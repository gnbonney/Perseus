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
                   .append(".super java/lang/Object\n\n")
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

        // Initialize all main variables
        for (Map.Entry<String, Integer> entry : currentLocalIndex.entrySet()) {
            String varName = entry.getKey();
            int index = entry.getValue();
            String type = currentSymbolTable.get(varName);
            if ("integer".equals(type) || "boolean".equals(type)) {
                mainCode.append("iconst_0\n").append("istore ").append(index).append("\n");
            } else if ("integer[]".equals(type)) {
                int[] bounds = currentArrayBounds.get(varName);
                int size = bounds[1] - bounds[0] + 1;
                mainCode.append("ldc ").append(size).append("\n")
                        .append("newarray int\n")
                        .append("astore ").append(index).append("\n");
            } else if ("real[]".equals(type)) {
                int[] bounds = currentArrayBounds.get(varName);
                int size = bounds[1] - bounds[0] + 1;
                mainCode.append("ldc ").append(size).append("\n")
                        .append("newarray double\n")
                        .append("astore ").append(index).append("\n");
            } else { // real
                mainCode.append("dconst_0\n").append("dstore ").append(index).append("\n");
            }
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
        // Retval slot at end
        procRetvalSlot = nextSlot;
        nextSlot += "real".equals(info.returnType) ? 2 : 1;
        int procNumLocals = nextSlot;

        currentSymbolTable   = procST;
        currentLocalIndex    = procLI;
        currentNumLocals     = procNumLocals;
        currentArrayBounds   = new LinkedHashMap<>();

        // Build JVM method descriptor
        String paramDesc = info.paramNames.stream()
            .map(p -> "real".equals(info.paramTypes.getOrDefault(p, "integer")) ? "D" : "I")
            .collect(Collectors.joining());
        String retDesc = "real".equals(info.returnType) ? "D" : "I";

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
        // Initialize retval slot
        if ("real".equals(info.returnType)) {
            activeOutput.append("dconst_0\n").append("dstore ").append(procRetvalSlot).append("\n");
        } else {
            activeOutput.append("iconst_0\n").append("istore ").append(procRetvalSlot).append("\n");
        }
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        // Load retval and return
        if ("real".equals(currentProcReturnType)) {
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
            int arrSlot = currentLocalIndex.get(arrName);
            int[] bounds = currentArrayBounds.get(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            activeOutput.append("aload ").append(arrSlot).append("\n");
            activeOutput.append(generateExpr(lv.expr())); // subscript
            if (lower != 0) {
                activeOutput.append("ldc ").append(lower).append("\n");
                activeOutput.append("isub\n");
            }
            activeOutput.append(generateExpr(ctx.expr())); // value
            String elemType = currentSymbolTable.getOrDefault(arrName, "integer[]");
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "iastore\n");
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

    @Override
    public void exitProcedureCall(AlgolParser.ProcedureCallContext ctx) {
        String name = ctx.identifier().getText();
        List<AlgolParser.ArgContext> args = ctx.argList().arg();
        if ("outstring".equals(name)) {
            String str = args.get(1).getText();
            activeOutput.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                        .append("ldc ").append(str).append("\n")
                        .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        } else if ("outreal".equals(name)) {
            activeOutput.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(D)V\n");
        } else if ("outinteger".equals(name)) {
            activeOutput.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(I)V\n");
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

        activeOutput.append(generateExpr(ctx.expr(0))); // start
        if ("real".equals(varType)) {
            activeOutput.append("dstore ").append(varIndex).append("\n");
        } else {
            activeOutput.append("istore ").append(varIndex).append("\n");
        }

        currentForLoopLabel = generateUniqueLabel("loop");
        currentForEndLabel  = generateUniqueLabel("endfor");

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
    }

    @Override
    public void exitForStatement(AlgolParser.ForStatementContext ctx) {
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

    // -------------------------------------------------------------------------
    // Expression code generation
    // -------------------------------------------------------------------------

    private String generateExpr(ExprContext ctx) {
        if (ctx instanceof AlgolParser.RelExprContext e) {
            return "; relational expr TODO\n";
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
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();
            Integer idx = currentLocalIndex.get(name);
            if (idx == null) return "; ERROR: undeclared variable " + name + "\n";
            String type = currentSymbolTable.get(name);
            return ("integer".equals(type) || "boolean".equals(type)) ? "iload " + idx + "\n" : "dload " + idx + "\n";
        } else if (ctx instanceof AlgolParser.ArrayAccessExprContext e) {
            String arrName = e.identifier().getText();
            Integer arrSlot = currentLocalIndex.get(arrName);
            if (arrSlot == null) return "; ERROR: undeclared array " + arrName + "\n";
            int[] bounds = currentArrayBounds.get(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String elemType = currentSymbolTable.getOrDefault(arrName, "integer[]");
            StringBuilder sb = new StringBuilder();
            sb.append("aload ").append(arrSlot).append("\n");
            sb.append(generateExpr(e.expr()));
            if (lower != 0) {
                sb.append("ldc ").append(lower).append("\n");
                sb.append("isub\n");
            }
            sb.append("real[]".equals(elemType) ? "daload\n" : "iaload\n");
            return sb.toString();
        } else if (ctx instanceof AlgolParser.ProcCallExprContext e) {
            String procName = e.identifier().getText();
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
}

