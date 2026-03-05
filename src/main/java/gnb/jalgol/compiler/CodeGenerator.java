// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Second-pass listener: emits Jasmin code using the pre-computed symbol table, local variable map, and expression types.
 * Handles both integer and real arithmetic, arrays, if/then/else, for loops, and procedure declarations.
 *
 * Output is split into three regions assembled in getOutput():
 *   classHeader  — .source / .class / .super / <init>
 *   procMethods  — one complete ".method ... .end method" per declared procedure
 *   mainCode     — main([Ljava/lang/String;)V method
 *
 * When walking a procedureDecl subtree, activeOutput is redirected to a temporary procBuffer.
 * On exit the completed method is appended to procMethods and activeOutput is restored.
 */
public class CodeGenerator extends AlgolBaseListener {
    private final String source;
    private final String packageName;
    private final String className;
    // Procedure definitions from SymbolTableBuilder (name → ProcInfo)
    private final Map<String, SymbolTableBuilder.ProcInfo> procedures;

    // --- Current context (swapped when entering/exiting procedures) ---
    private Map<String, String> currentSymbolTable;
    private Map<String, Integer> currentLocalIndex;
    private int currentNumLocals;
    private Map<String, int[]> currentArrayBounds;

    // --- Saved main context (restored after a procedure is emitted) ---
    private Map<String, String> mainSymbolTable;
    private Map<String, Integer> mainLocalIndex;
    private int mainNumLocals;
    private Map<String, int[]> mainArrayBounds;

    // Maps expression contexts to their inferred types ("integer" or "real")
    private final Map<AlgolParser.ExprContext, String> exprTypes;

    // --- Output buffers ---
    private final StringBuilder classHeader = new StringBuilder();
    private final StringBuilder mainCode    = new StringBuilder();
    private final List<String>  procMethods = new ArrayList<>();
    private StringBuilder activeOutput;   // points to mainCode or current procBuffer
    private StringBuilder procBuffer;     // non-null while inside a procedureDecl

    // --- Procedure return-value tracking ---
    private String currentProcName = null;
    private String currentProcReturnType = null;
    private int    procRetvalSlot = -1;

    // For for loops
    private String currentForLoopLabel;
    private String currentForEndLabel;

    // Stacks for if/then/else label management (supports nesting)
    private final Deque<String> ifEndLabelStack  = new ArrayDeque<>();
    private final Deque<String> ifElseLabelStack = new ArrayDeque<>();

    public CodeGenerator(String source, String packageName, String className,
                         Map<String, String> symbolTable, Map<String, Integer> localIndex, int numLocals,
                         Map<AlgolParser.ExprContext, String> exprTypes, Map<String, int[]> arrayBounds,
                         Map<String, SymbolTableBuilder.ProcInfo> procedures) {
        this.source = source;
        this.packageName = packageName;
        this.className = className;
        this.exprTypes = exprTypes;
        this.procedures = procedures;
        this.currentSymbolTable = symbolTable;
        this.currentLocalIndex  = localIndex;
        this.currentNumLocals   = numLocals;
        this.currentArrayBounds = arrayBounds;
    }

    public String getOutput() {
        StringBuilder full = new StringBuilder();
        full.append(classHeader);
        for (String pm : procMethods) full.append(pm);
        full.append(mainCode);
        return full.toString();
    }

    @Override
    public void enterProgram(AlgolParser.ProgramContext ctx) {
        // Class header and <init>
        classHeader.append(".source ").append(source).append("\n")
                   .append(".class public ").append(packageName).append("/").append(className).append("\n")
                   .append(".super java/lang/Object\n\n");

        // Emit static field declarations for arrays (must appear BEFORE methods in Jasmin)
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String arrName = symEntry.getKey();
            String arrType = symEntry.getValue();
            if (arrType.endsWith("[]")) {
                classHeader.append(".field public static ").append(arrName)
                           .append(" ").append(arrayTypeToJvmDesc(arrType)).append("\n");
            }
        }

        classHeader.append("\n")
                   .append(".method public <init>()V\n")
                   .append(".limit stack 1\n")
                   .append(".limit locals 1\n")
                   .append("aload_0\n")
                   .append("invokespecial java/lang/Object/<init>()V\n")
                   .append("return\n")
                   .append(".end method\n\n");

        // Main method header
        mainCode.append(".method public static main([Ljava/lang/String;)V\n")
                .append(".limit stack 16\n")
                .append(".limit locals ").append(currentNumLocals).append("\n");

        // Initialize scalars from localIndex
        for (Map.Entry<String, Integer> entry : currentLocalIndex.entrySet()) {
            String varName = entry.getKey();
            int index = entry.getValue();
            String type = currentSymbolTable.get(varName);
            if ("integer".equals(type) || "boolean".equals(type)) {
                mainCode.append("iconst_0\n").append("istore ").append(index).append("\n");
            } else { // real
                mainCode.append("dconst_0\n").append("dstore ").append(index).append("\n");
            }
        }

        // Initialize arrays as static fields (newarray + putstatic)
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String varName = symEntry.getKey();
            String type = symEntry.getValue();
            if (!type.endsWith("[]")) continue;
            int[] bounds = currentArrayBounds.get(varName);
            if (bounds == null) continue;
            int size = bounds[1] - bounds[0] + 1;
            String elemType = "real[]".equals(type) ? "double" : "boolean[]".equals(type) ? "boolean" : "int";
            mainCode.append("ldc ").append(size).append("\n")
                    .append("newarray ").append(elemType).append("\n")
                    .append("putstatic ").append(packageName).append("/").append(className)
                    .append("/").append(varName).append(" ").append(arrayTypeToJvmDesc(type)).append("\n");
        }

        activeOutput = mainCode;
    }

    @Override
    public void exitProgram(AlgolParser.ProgramContext ctx) {
        activeOutput.append("return\n").append(".end method\n");
    }

    // -------------------------------------------------------------------------
    // Procedure declaration enter/exit: switch output buffer and local context
    // -------------------------------------------------------------------------

    @Override
    public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        String procName = ctx.identifier().getText();
        SymbolTableBuilder.ProcInfo info = procedures.get(procName);

        // Switch to a fresh procedure buffer
        procBuffer   = new StringBuilder();
        activeOutput = procBuffer;

        // Save main context
        mainSymbolTable   = currentSymbolTable;
        mainLocalIndex    = currentLocalIndex;
        mainNumLocals     = currentNumLocals;
        mainArrayBounds   = currentArrayBounds;

        // Build procedure-local context
        currentProcName       = procName;
        currentProcReturnType = info.returnType;

        Map<String, String>  procST = new LinkedHashMap<>();
        Map<String, Integer> procLI = new LinkedHashMap<>();
        int nextSlot = 0;

        // Parameters occupy the first slots
        for (String paramName : info.paramNames) {
            String paramType = info.paramTypes.getOrDefault(paramName, "integer");
            procST.put(paramName, paramType);
            procLI.put(paramName, nextSlot);
            nextSlot += "real".equals(paramType) ? 2 : 1;
        }
        // Then locals
        for (Map.Entry<String, String> local : info.localVars.entrySet()) {
            String varName = local.getKey();
            String varType = local.getValue();
            procST.put(varName, varType);
            procLI.put(varName, nextSlot);
            nextSlot += "real".equals(varType) ? 2 : 1;
        }
        // Retval slot at end (only for typed function procedures, not void)
        if (!"void".equals(info.returnType)) {
            procRetvalSlot = nextSlot;
            nextSlot += "real".equals(info.returnType) ? 2 : 1;
        } else {
            procRetvalSlot = -1;
        }
        int procNumLocals = nextSlot;

        currentSymbolTable   = procST;
        currentLocalIndex    = procLI;
        currentNumLocals     = procNumLocals;
        currentArrayBounds   = new LinkedHashMap<>();

        // Build JVM method descriptor
        String paramDesc = info.paramNames.stream()
            .map(p -> "real".equals(info.paramTypes.getOrDefault(p, "integer")) ? "D" : "I")
            .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : "real".equals(info.returnType) ? "D" : "I";

        activeOutput.append(".method public static ").append(procName)
                    .append("(").append(paramDesc).append(")").append(retDesc).append("\n")
                    .append(".limit stack 16\n")
                    .append(".limit locals ").append(procNumLocals).append("\n");

        // Initialize local variables (not parameters) and the retval slot
        for (Map.Entry<String, Integer> e : procLI.entrySet()) {
            if (info.paramNames.contains(e.getKey())) continue; // params set by caller
            String varType = procST.get(e.getKey());
            int slot = e.getValue();
            if ("real".equals(varType)) {
                activeOutput.append("dconst_0\n").append("dstore ").append(slot).append("\n");
            } else {
                activeOutput.append("iconst_0\n").append("istore ").append(slot).append("\n");
            }
        }
        // Initialize retval slot (only for typed functions)
        if (procRetvalSlot >= 0) {
            if ("real".equals(info.returnType)) {
                activeOutput.append("dconst_0\n").append("dstore ").append(procRetvalSlot).append("\n");
            } else {
                activeOutput.append("iconst_0\n").append("istore ").append(procRetvalSlot).append("\n");
            }
        }
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        // Emit return instruction based on return type
        if ("void".equals(currentProcReturnType)) {
            activeOutput.append("return\n");
        } else if ("real".equals(currentProcReturnType)) {
            activeOutput.append("dload ").append(procRetvalSlot).append("\n")
                        .append("dreturn\n");
        } else {
            activeOutput.append("iload ").append(procRetvalSlot).append("\n")
                        .append("ireturn\n");
        }
        activeOutput.append(".end method\n\n");

        procMethods.add(procBuffer.toString());

        // Restore main context
        currentSymbolTable   = mainSymbolTable;
        currentLocalIndex    = mainLocalIndex;
        currentNumLocals     = mainNumLocals;
        currentArrayBounds   = mainArrayBounds;
        activeOutput         = mainCode;
        currentProcName      = null;
        currentProcReturnType = null;
        procRetvalSlot       = -1;
        procBuffer           = null;
    }

    // -------------------------------------------------------------------------
    // Assignments
    // -------------------------------------------------------------------------

    @Override
    public void exitAssignment(AlgolParser.AssignmentContext ctx) {
        List<AlgolParser.LvalueContext> lvalues = ctx.lvalue();

        // Array element assignment (single dest with subscript)
        if (lvalues.size() == 1 && lvalues.get(0).expr() != null) {
            AlgolParser.LvalueContext lv = lvalues.get(0);
            String arrName = lv.identifier().getText();
            String elemType = lookupVarType(arrName);
            if (elemType == null) {
                activeOutput.append("; ERROR: undeclared array ").append(arrName).append("\n");
                return;
            }
            int[] bounds = lookupArrayBounds(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = arrayTypeToJvmDesc(elemType);
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            activeOutput.append(generateExpr(lv.expr())); // subscript
            if (lower != 0) {
                activeOutput.append("ldc ").append(lower).append("\n");
                activeOutput.append("isub\n");
            }
            activeOutput.append(generateExpr(ctx.expr())); // value
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "boolean[]".equals(elemType) ? "bastore\n" : "iastore\n");
            return;
        }

        // Scalar (possibly chained) assignment
        String exprType = exprTypes.getOrDefault(ctx.expr(), "integer");

        // Determine storage type: real if any destination is real (treats procedure return as its return type)
        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            if (lvName.equals(currentProcName)) return "real".equals(currentProcReturnType);
            return "real".equals(currentSymbolTable.getOrDefault(lvName, "real"));
        });
        String storeType = anyReal ? "real" : "integer";

        // Generate expression and widen if needed
        activeOutput.append(generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            activeOutput.append("i2d\n");
        }

        // Store to each destination; dup before all but the last
        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();

            // Procedure return value assignment
            if (name.equals(currentProcName)) {
                if (i < lvalues.size() - 1) {
                    activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                }
                if ("real".equals(currentProcReturnType)) {
                    activeOutput.append("dstore ").append(procRetvalSlot).append("\n");
                } else {
                    activeOutput.append("istore ").append(procRetvalSlot).append("\n");
                }
                continue;
            }

            Integer idx = currentLocalIndex.get(name);
            if (idx == null) {
                activeOutput.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }
            String varType = currentSymbolTable.get(name);
            if (i < lvalues.size() - 1) {
                activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            }
            if ("integer".equals(varType) && "real".equals(storeType)) {
                activeOutput.append("d2i\n");
            }
            activeOutput.append(("integer".equals(varType) || "boolean".equals(varType)) ? "istore " : "dstore ")
                        .append(idx).append("\n");
        }
    }

    // -------------------------------------------------------------------------
    // Procedure calls (statement form: outstring, outreal, outinteger, etc.)
    // -------------------------------------------------------------------------

    /**
     * Resolves the channel parameter to the appropriate PrintStream field reference.
     * Per Environmental-Block.md:
     *   - Channel 0 → System.err
     *   - Channel 1 → System.out
     *   - Any other value → System.out
     * The channel must be a compile-time constant integer literal. If it's not,
     * a warning comment is emitted and System.out is used as the default.
     */
    private String getChannelStream(AlgolParser.ArgContext channelArg) {
        if (channelArg == null || channelArg.expr() == null) {
            activeOutput.append("; WARNING: missing channel parameter, defaulting to System.out\n");
            return "java/lang/System/out";
        }
        
        // Try to evaluate as a constant integer literal
        AlgolParser.ExprContext expr = channelArg.expr();
        if (expr instanceof AlgolParser.IntLiteralExprContext) {
            AlgolParser.IntLiteralExprContext intExpr = (AlgolParser.IntLiteralExprContext) expr;
            String channelText = intExpr.unsignedInt().getText();
            try {
                int channelValue = Integer.parseInt(channelText);
                if (channelValue == 0) {
                    return "java/lang/System/err";
                } else {
                    return "java/lang/System/out";
                }
            } catch (NumberFormatException e) {
                activeOutput.append("; WARNING: invalid channel value, defaulting to System.out\n");
                return "java/lang/System/out";
            }
        } else {
            // Not a constant integer literal
            activeOutput.append("; WARNING: channel parameter is not a compile-time constant, defaulting to System.out\n");
            return "java/lang/System/out";
        }
    }

    @Override
    public void exitProcedureCall(AlgolParser.ProcedureCallContext ctx) {
        String name = ctx.identifier().getText();
        List<AlgolParser.ArgContext> args = ctx.argList().arg();
        if ("outstring".equals(name)) {
            String stream = getChannelStream(args.get(0));
            String str = args.get(1).getText();
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append("ldc ").append(str).append("\n")
                        .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        } else if ("outreal".equals(name)) {
            String stream = getChannelStream(args.get(0));
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(D)V\n");
        } else if ("outinteger".equals(name)) {
            String stream = getChannelStream(args.get(0));
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(I)V\n");
        } else if ("outchar".equals(name)) {
            // outchar(channel, str, position) - outputs character at position in string
            String stream = getChannelStream(args.get(0));
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append("ldc ").append(args.get(1).getText()).append("\n")
                        .append(generateExpr(args.get(2).expr()))
                        .append("invokevirtual java/lang/String/charAt(I)C\n")
                        .append("invokevirtual java/io/PrintStream/print(C)V\n");
        } else if ("outterminator".equals(name)) {
            // outterminator(channel) - outputs a space separator
            String stream = getChannelStream(args.get(0));
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append("ldc \" \"\n")
                        .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        } else {
            // User-defined procedure call (statement form)
            SymbolTableBuilder.ProcInfo info = procedures.get(name);
            if (info != null) {
                for (int ai = 0; ai < args.size(); ai++) {
                    AlgolParser.ArgContext arg = args.get(ai);
                    if (arg.expr() == null) continue; // skip string args
                    activeOutput.append(generateExpr(arg.expr()));
                    if (ai < info.paramNames.size()) {
                        String paramName = info.paramNames.get(ai);
                        String paramType = info.paramTypes.getOrDefault(paramName, "integer");
                        String argType = exprTypes.getOrDefault(arg.expr(), "integer");
                        if ("real".equals(paramType) && "integer".equals(argType)) {
                            activeOutput.append("i2d\n");
                        }
                    }
                }
                String paramDesc = info.paramNames.stream()
                    .map(p -> "real".equals(info.paramTypes.getOrDefault(p, "integer")) ? "D" : "I")
                    .collect(Collectors.joining());
                String retDesc = "void".equals(info.returnType) ? "V" : "real".equals(info.returnType) ? "D" : "I";
                activeOutput.append("invokestatic ").append(packageName).append("/").append(className)
                            .append("/").append(name)
                            .append("(").append(paramDesc).append(")").append(retDesc).append("\n");
                // Pop non-void return value (it's a statement, result discarded)
                if ("D".equals(retDesc)) activeOutput.append("pop2\n");
                else if (!"V".equals(retDesc)) activeOutput.append("pop\n");
            } else {
                activeOutput.append("; unknown procedure: ").append(name).append("\n");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Labels and goto
    // -------------------------------------------------------------------------

    @Override
    public void enterLabel(AlgolParser.LabelContext ctx) {
        String labelName = ctx.identifier().getText();
        activeOutput.append(labelName).append(":\n");
    }

    @Override
    public void exitGotoStatement(AlgolParser.GotoStatementContext ctx) {
        String labelName = ctx.identifier().getText();
        activeOutput.append("goto ").append(labelName).append("\n");
    }

    // -------------------------------------------------------------------------
    // if / then / else
    // -------------------------------------------------------------------------

    @Override
    public void enterStatement(AlgolParser.StatementContext ctx) {
        if (ctx.getParent() instanceof AlgolParser.IfStatementContext ifCtx
                && ifCtx.statement().size() > 1
                && ctx == ifCtx.statement(1)) {
            String endLabel  = ifEndLabelStack.peek();
            String elseLabel = ifElseLabelStack.peek();
            activeOutput.append("goto ").append(endLabel).append("\n");
            activeOutput.append(elseLabel).append(":\n");
        }
    }

    @Override
    public void enterIfStatement(AlgolParser.IfStatementContext ctx) {
        AlgolParser.ExprContext cond = ctx.expr();
        boolean hasElse = ctx.statement().size() > 1;
        String endLabel = generateUniqueLabel("endif");
        ifEndLabelStack.push(endLabel);

        String thenLabel    = generateUniqueLabel("then");
        String falseTarget;
        if (hasElse) {
            String elseLabel = generateUniqueLabel("else");
            ifElseLabelStack.push(elseLabel);
            falseTarget = elseLabel;
        } else {
            ifElseLabelStack.push(""); // sentinel
            falseTarget = endLabel;
        }

        if (cond instanceof AlgolParser.RelExprContext rel) {
            activeOutput.append(generateExpr(rel.expr(0)));
            activeOutput.append(generateExpr(rel.expr(1)));
            String op = rel.op.getText();
            String cmpInstr = switch (op) {
                case "<"  -> "if_icmplt";
                case "<=" -> "if_icmple";
                case ">"  -> "if_icmpgt";
                case ">=" -> "if_icmpge";
                case "="  -> "if_icmpeq";
                case "<>" -> "if_icmpne";
                default   -> "if_icmpne";
            };
            activeOutput.append(cmpInstr).append(" ").append(thenLabel).append("\n");
        } else {
            activeOutput.append(generateExpr(cond));
            activeOutput.append("ifne ").append(thenLabel).append("\n");
        }
        activeOutput.append("goto ").append(falseTarget).append("\n");
        activeOutput.append(thenLabel).append(":\n");
    }

    @Override
    public void exitIfStatement(AlgolParser.IfStatementContext ctx) {
        String endLabel = ifEndLabelStack.pop();
        ifElseLabelStack.pop();
        activeOutput.append(endLabel).append(":\n");
    }

    // -------------------------------------------------------------------------
    // for loops
    // -------------------------------------------------------------------------

    @Override
    public void enterForStatement(AlgolParser.ForStatementContext ctx) {
        String varName  = ctx.identifier().getText();
        int    varIndex = currentLocalIndex.get(varName);
        String varType  = currentSymbolTable.get(varName);

        currentForLoopLabel = generateUniqueLabel("loop");
        currentForEndLabel  = generateUniqueLabel("endfor");

        if (ctx.STEP() != null) {
            // step until: init before loop label, condition check at loop top
            activeOutput.append(generateExpr(ctx.expr(0))); // start
            if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(currentForLoopLabel).append(":\n");
            if ("real".equals(varType)) {
                activeOutput.append("dload ").append(varIndex).append("\n");
                activeOutput.append(generateExpr(ctx.expr(2))); // until
                activeOutput.append("dcmpg\n");
                activeOutput.append("ifgt ").append(currentForEndLabel).append("\n");
            } else {
                activeOutput.append("iload ").append(varIndex).append("\n");
                activeOutput.append(generateExpr(ctx.expr(2))); // until
                activeOutput.append("if_icmpgt ").append(currentForEndLabel).append("\n");
            }
        } else if (ctx.WHILE() != null) {
            // while: per Algol 60 semantics, V := E is re-evaluated before EACH iteration
            activeOutput.append(currentForLoopLabel).append(":\n");
            activeOutput.append(generateExpr(ctx.expr(0))); // initial value (re-assigned each iteration)
            if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(generateExpr(ctx.expr(1))); // while condition
            activeOutput.append("ifeq ").append(currentForEndLabel).append("\n");
        } else {
            // bare for (no step/while): just set variable, no loop
            activeOutput.append(generateExpr(ctx.expr(0)));
            if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(currentForLoopLabel).append(":\n");
        }
    }

    @Override
    public void exitForStatement(AlgolParser.ForStatementContext ctx) {
        if (ctx.STEP() != null) {
            String varName  = ctx.identifier().getText();
            int    varIndex = currentLocalIndex.get(varName);
            String varType  = currentSymbolTable.get(varName);

            activeOutput.append(generateExpr(ctx.expr(1))); // step
            if ("real".equals(varType)) {
                activeOutput.append("dload ").append(varIndex).append("\n");
                activeOutput.append("dadd\n");
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("iload ").append(varIndex).append("\n");
                activeOutput.append("iadd\n");
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
        }

        activeOutput.append("goto ").append(currentForLoopLabel).append("\n");
        activeOutput.append(currentForEndLabel).append(":\n");
    }

    // -------------------------------------------------------------------------
    // Unique label counter
    // -------------------------------------------------------------------------

    private int labelCounter = 0;

    private String generateUniqueLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    /** Returns the JVM array descriptor for a JAlgol array type. */
    private static String arrayTypeToJvmDesc(String arrayType) {
        return switch (arrayType) {
            case "boolean[]" -> "[Z";
            case "integer[]" -> "[I";
            case "real[]"    -> "[D";
            default -> "[I";
        };
    }

    /** Looks up variable type in the current scope, falling back to main-scope if inside a procedure. */
    private String lookupVarType(String name) {
        String type = currentSymbolTable.get(name);
        if (type == null && mainSymbolTable != null) type = mainSymbolTable.get(name);
        return type;
    }

    /** Looks up array bounds in the current scope, falling back to main-scope bounds if inside a procedure. */
    private int[] lookupArrayBounds(String name) {
        int[] bounds = currentArrayBounds.get(name);
        if (bounds == null && mainArrayBounds != null) bounds = mainArrayBounds.get(name);
        return bounds;
    }

    // -------------------------------------------------------------------------
    // Expression code generation
    // -------------------------------------------------------------------------

    private String generateExpr(ExprContext ctx) {
        if (ctx instanceof AlgolParser.RelExprContext e) {
            String left = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String op = e.op.getText();
            String trueLabel = generateUniqueLabel("rel_true");
            String endLabel = generateUniqueLabel("rel_end");
            String cmpOp = switch (op) {
                case "<" -> "lt";
                case "<=" -> "le";
                case ">" -> "gt";
                case ">=" -> "ge";
                case "=" -> "eq";
                case "<>" -> "ne";
                default -> throw new RuntimeException("Unknown rel op " + op);
            };
            return left + right + "if_icmp" + cmpOp + " " + trueLabel + "\n" +
                "iconst_0\n" +
                "goto " + endLabel + "\n" +
                trueLabel + ":\n" +
                "iconst_1\n" +
                endLabel + ":\n";
        } else if (ctx instanceof AlgolParser.MulDivExprContext e) {
            String left  = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String leftType  = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            if ("real".equals(type) && "integer".equals(leftType))  left  += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("*".equals(op) ? "dmul" : "ddiv") :
                ("*".equals(op) ? "imul" : "idiv");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AddSubExprContext e) {
            String left  = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String leftType  = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            if ("real".equals(type) && "integer".equals(leftType))  left  += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("+".equals(op) ? "dadd" : "dsub") :
                ("+".equals(op) ? "iadd" : "isub");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AndExprContext e) {
            return generateExpr(e.expr(0)) + generateExpr(e.expr(1)) + "iand\n";
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();
            Integer idx = currentLocalIndex.get(name);
            if (idx == null) return "; ERROR: undeclared variable " + name + "\n";
            String type = currentSymbolTable.get(name);
            return ("integer".equals(type) || "boolean".equals(type)) ? "iload " + idx + "\n" : "dload " + idx + "\n";
        } else if (ctx instanceof AlgolParser.ArrayAccessExprContext e) {
            String arrName = e.identifier().getText();
            String elemType = lookupVarType(arrName);
            if (elemType == null) return "; ERROR: undeclared array " + arrName + "\n";
            int[] bounds = lookupArrayBounds(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = arrayTypeToJvmDesc(elemType);
            StringBuilder sb = new StringBuilder();
            sb.append("getstatic ").append(packageName).append("/").append(className)
              .append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            sb.append(generateExpr(e.expr()));
            if (lower != 0) {
                sb.append("ldc ").append(lower).append("\n");
                sb.append("isub\n");
            }
            sb.append("real[]".equals(elemType) ? "daload\n" : "boolean[]".equals(elemType) ? "baload\n" : "iaload\n");
            return sb.toString();
        } else if (ctx instanceof AlgolParser.ProcCallExprContext e) {
            String procName = e.identifier().getText();
            
            // Check for built-in math functions first
            String builtinCode = generateBuiltinMathFunction(procName, e);
            if (builtinCode != null) {
                return builtinCode;
            }
            
            // Otherwise, handle user-defined procedure
            SymbolTableBuilder.ProcInfo info = procedures.get(procName);
            if (info == null) return "; ERROR: undeclared procedure " + procName + "\n";
            StringBuilder sb = new StringBuilder();
            List<AlgolParser.ArgContext> args = e.argList().arg();
            for (int i = 0; i < args.size(); i++) {
                AlgolParser.ArgContext arg = args.get(i);
                if (arg.expr() == null) continue; // skip string args
                sb.append(generateExpr(arg.expr()));
                // Widen integer arg to double if the corresponding param is real
                if (i < info.paramNames.size()) {
                    String paramName = info.paramNames.get(i);
                    String paramType = info.paramTypes.getOrDefault(paramName, "integer");
                    String argType   = exprTypes.getOrDefault(arg.expr(), "integer");
                    if ("real".equals(paramType) && "integer".equals(argType)) {
                        sb.append("i2d\n");
                    }
                }
            }
            String paramDesc = info.paramNames.stream()
                .map(p -> "real".equals(info.paramTypes.getOrDefault(p, "integer")) ? "D" : "I")
                .collect(Collectors.joining());
            String retDesc = "real".equals(info.returnType) ? "D" : "I";
            sb.append("invokestatic ").append(packageName).append("/").append(className)
              .append("/").append(procName)
              .append("(").append(paramDesc).append(")").append(retDesc).append("\n");
            return sb.toString();
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext e) {
            return "ldc2_w " + e.realLiteral().getText() + "\n";
        } else if (ctx instanceof AlgolParser.IntLiteralExprContext e) {
            return "ldc " + e.unsignedInt().getText() + "\n";
        } else if (ctx instanceof AlgolParser.TrueLiteralExprContext) {
            return "iconst_1\n";
        } else if (ctx instanceof AlgolParser.FalseLiteralExprContext) {
            return "iconst_0\n";
        } else if (ctx instanceof AlgolParser.ParenExprContext e) {
            return generateExpr(e.expr());
        }
        return "; unknown expr type\n";
    }

    /**
     * Generates code for built-in math functions from the environmental block.
     * Returns null if the function name is not a recognized built-in.
     */
    private String generateBuiltinMathFunction(String funcName, AlgolParser.ProcCallExprContext ctx) {
        if (ctx.argList() == null || ctx.argList().arg().isEmpty()) {
            return "; ERROR: " + funcName + " requires an argument\n";
        }
        
        AlgolParser.ExprContext argExpr = ctx.argList().arg().get(0).expr();
        if (argExpr == null) {
            return "; ERROR: " + funcName + " requires an expression argument\n";
        }
        
        StringBuilder sb = new StringBuilder();
        String argType = exprTypes.getOrDefault(argExpr, "integer");
        
        switch (funcName) {
            case "sqrt":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/sqrt(D)D\n");
                return sb.toString();
                
            case "abs":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/abs(D)D\n");
                return sb.toString();
                
            case "iabs":
                sb.append(generateExpr(argExpr));
                if ("real".equals(argType)) sb.append("d2i\n");
                sb.append("invokestatic java/lang/Math/abs(I)I\n");
                return sb.toString();
                
            case "sign":
                // sign(E) = E > 0 ? 1 : E < 0 ? -1 : 0
                // Generate inline code for efficiency
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/signum(D)D\n");
                sb.append("d2i\n");
                return sb.toString();
                
            case "entier":
                // entier(E) = (int)Math.floor(E)
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/floor(D)D\n");
                sb.append("d2i\n");
                return sb.toString();
                
            case "sin":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/sin(D)D\n");
                return sb.toString();
                
            case "cos":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/cos(D)D\n");
                return sb.toString();
                
            case "arctan":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/atan(D)D\n");
                return sb.toString();
                
            case "ln":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/log(D)D\n");
                return sb.toString();
                
            case "exp":
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/exp(D)D\n");
                return sb.toString();
                
            default:
                return null; // Not a built-in function
        }
    }
}

