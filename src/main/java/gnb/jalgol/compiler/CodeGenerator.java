// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // --- Thunk helper data ---
    // counter for generating unique thunk class names
    private int thunkCounter = 0;
    // collects (shortClassName, jasminSource) for generated thunk classes (each needs its own .j file)
    private final List<Map.Entry<String,String>> thunkClassDefinitions = new ArrayList<>();

    // --- Procedure reference helper data ---
    // counter for generating unique procedure reference class names
    private int procRefCounter = 0;
    // collects (shortClassName, jasminSource) for generated procedure reference classes
    private final List<Map.Entry<String,String>> procRefClassDefinitions = new ArrayList<>();

    // --- Current context (swapped when entering/exiting procedures) ---
    private Map<String, String> currentSymbolTable;
    private Map<String, Integer> currentLocalIndex;
    private int currentNumLocals;
    private Map<String, int[]> currentArrayBounds;

    // --- Saved outer context (one entry per active nested procedure level) ---
    private Map<String, String>  mainSymbolTable;
    private Map<String, Integer> mainLocalIndex;
    private int                  mainNumLocals;
    private Map<String, int[]>   mainArrayBounds;
    private final Deque<Map<String, String>>  savedOuterSTStack     = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedOuterLIStack     = new LinkedList<>();
    private final Deque<Integer>              savedOuterNLStack     = new LinkedList<>();
    private final Deque<Map<String, int[]>>   savedOuterABStack     = new LinkedList<>();
    private final Deque<String>               savedProcNameStack    = new LinkedList<>();
    private final Deque<String>               savedProcRetTypeStack = new LinkedList<>();
    private final Deque<Integer>              savedProcRetSlotStack = new LinkedList<>();

    // Maps expression contexts to their inferred types ("integer" or "real")
    private final Map<AlgolParser.ExprContext, String> exprTypes;

    // --- Output buffers ---
    private final StringBuilder classHeader = new StringBuilder();
    private final StringBuilder mainCode    = new StringBuilder();
    private final List<String>  procMethods = new ArrayList<>();
    private StringBuilder activeOutput;   // points to mainCode or top of procBufferStack
    private final Deque<StringBuilder> procBufferStack = new ArrayDeque<>();

    // --- Procedure return-value tracking ---
    private String currentProcName = null;
    private String currentProcReturnType = null;
    private int    procRetvalSlot = -1;

    // For for loops
    private String currentForLoopLabel;
    private String currentForEndLabel;

    // Stack for capturing for-loop body code (enables multi-element for-list by body-inline duplication)
    private final Deque<StringBuilder> forBodyStack = new ArrayDeque<>();

    // Stacks for if/then/else label management (supports nesting)
    private final Deque<String> ifEndLabelStack  = new ArrayDeque<>();
    private final Deque<String> ifElseLabelStack = new ArrayDeque<>();

    // Map of procedure variable names to their main-method JVM slot indices
    private final Map<String, Integer> procVarSlots;

    public CodeGenerator(String source, String packageName, String className,
                         Map<String, String> symbolTable, Map<String, Integer> localIndex, int numLocals,
                         Map<AlgolParser.ExprContext, String> exprTypes, Map<String, int[]> arrayBounds,
                         Map<String, SymbolTableBuilder.ProcInfo> procedures, Map<String, Integer> procVarSlots) {
        this.source = source;
        this.packageName = packageName;
        this.className = className;
        this.exprTypes = exprTypes;
        this.procedures = procedures;
        this.procVarSlots = procVarSlots != null ? procVarSlots : Map.of();
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

    /**
     * Returns the generated thunk class definitions as a map from short class name
     * (e.g. "JenTest$Thunk0") to the full Jasmin source for that class.
     * Each entry must be written to its own .j file and assembled separately.
     */
    public Map<String,String> getThunkClassOutputs() {
        Map<String,String> result = new LinkedHashMap<>();
        for (Map.Entry<String,String> e : thunkClassDefinitions) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    /**
     * Returns a map of (shortClassName → jasminSource) for all generated procedure reference classes.
     * Each entry must be written to its own .j file and assembled separately.
     */
    public Map<String,String> getProcRefClassOutputs() {
        Map<String,String> result = new LinkedHashMap<>();
        for (Map.Entry<String,String> e : procRefClassDefinitions) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
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

        // Emit static field declarations for scalars (must appear BEFORE methods in Jasmin)
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String varName = symEntry.getKey();
            String varType = symEntry.getValue();
            if (!varType.endsWith("[]") && !varType.startsWith("procedure:")) {
                classHeader.append(".field public static ").append(varName)
                           .append(" ").append(scalarTypeToJvmDesc(varType)).append("\n");
            }
        }

        // Add static Scanner field for input procedures (used for System.in reading)
        classHeader.append(".field public static __scanner Ljava/util/Scanner;\n");

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

        // Initialize scalars as static fields (putstatic)
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String varName = symEntry.getKey();
            String type = symEntry.getValue();
            if (!type.endsWith("[]") && !type.startsWith("procedure:")) {
                // Scalar variable: initialize via putstatic
                if ("integer".equals(type) || "boolean".equals(type)) {
                    mainCode.append("iconst_0\n");
                } else if ("real".equals(type)) {
                    mainCode.append("dconst_0\n");
                } else if ("string".equals(type)) {
                    mainCode.append("ldc \"\"\n");
                }
                mainCode.append("putstatic ").append(packageName).append("/").append(className)
                        .append("/").append(varName).append(" ").append(scalarTypeToJvmDesc(type)).append("\n");
            }
        }

        // Initialize procedure variables from localIndex (they stay as locals)
        for (Map.Entry<String, Integer> entry : currentLocalIndex.entrySet()) {
            String varName = entry.getKey();
            int index = entry.getValue();
            String type = currentSymbolTable.get(varName);
            if (type != null && type.startsWith("procedure:")) {
                mainCode.append("aconst_null\n").append("astore ").append(index).append("\n");
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
            String elemType = "real[]".equals(type) ? "double" : "boolean[]".equals(type) ? "boolean" : "string[]".equals(type) ? "java/lang/String" : "int";
            String newarrayInstr = "string[]".equals(type) ? "anewarray" : "newarray";
            mainCode.append("ldc ").append(size).append("\n")
                    .append(newarrayInstr).append(" ").append(elemType).append("\n")
                    .append("putstatic ").append(packageName).append("/").append(className)
                    .append("/").append(varName).append(" ").append(arrayTypeToJvmDesc(type)).append("\n");
        }

        // Initialize Scanner for input procedures
        mainCode.append("new java/util/Scanner\n")
                .append("dup\n")
                .append("getstatic java/lang/System/in Ljava/io/InputStream;\n")
                .append("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n")
                .append("putstatic ").append(packageName).append("/").append(className)
                .append("/__scanner Ljava/util/Scanner;\n");

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
        StringBuilder newBuf = new StringBuilder();
        procBufferStack.push(newBuf);
        activeOutput = newBuf;

        // Save outer context before making current context the new "outer" (supports nesting)
        savedOuterSTStack.push(mainSymbolTable);
        savedOuterLIStack.push(mainLocalIndex);
        savedOuterNLStack.push(mainNumLocals);
        savedOuterABStack.push(mainArrayBounds);
        savedProcNameStack.push(currentProcName);
        savedProcRetTypeStack.push(currentProcReturnType);
        savedProcRetSlotStack.push(procRetvalSlot);

        // Make the current scope the new "outer" scope
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
            String baseType = info.paramTypes.getOrDefault(paramName, "integer");
            String paramType;
            if (info.valueParams.contains(paramName)) {
                paramType = baseType;
            } else {
                // call-by-name parameter is represented internally as a thunk object
                paramType = "thunk:" + baseType;
            }
            procST.put(paramName, paramType);
            procLI.put(paramName, nextSlot);
            // thunks and procedure refs are object references so occupy 1 slot, real still 2
            nextSlot += (paramType.startsWith("thunk:") || paramType.startsWith("procedure:")) ? 1 : ("real".equals(paramType) ? 2 : 1);
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
        // Self-reference slot: if this procedure is used as a procedure variable,
        // reserve an extra slot so the body can store/load a ProcRef for itself.
        int selfRefSlot = -1;
        if (procVarSlots.containsKey(procName)) {
            selfRefSlot = nextSlot;
            nextSlot++;
            procST.put(procName, "procedure:" + info.returnType);
            procLI.put(procName, selfRefSlot);
        }
        int procNumLocals = nextSlot;

        currentSymbolTable   = procST;
        currentLocalIndex    = procLI;
        currentNumLocals     = procNumLocals;
        currentArrayBounds   = new LinkedHashMap<>();

        // Build JVM method descriptor
        String paramDesc = info.paramNames.stream()
            .map(p -> {
                if (!info.valueParams.contains(p)) {
                    // call-by-name parameter passed as Thunk
                    return "Lgnb/jalgol/compiler/Thunk;";
                }
                String type = info.paramTypes.getOrDefault(p, "integer");
                if ("real".equals(type)) return "D";
                if ("string".equals(type)) return "Ljava/lang/String;";
                if (type.startsWith("procedure:")) {
                    return switch (type.substring("procedure:".length())) {
                        case "real" -> "Lgnb/jalgol/compiler/RealProcedure;";
                        case "integer" -> "Lgnb/jalgol/compiler/IntegerProcedure;";
                        case "string" -> "Lgnb/jalgol/compiler/StringProcedure;";
                        default -> "Lgnb/jalgol/compiler/VoidProcedure;";
                    };
                }
                // boolean and integer both treated as I
                return "I";
            })
            .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";

        activeOutput.append(".method public static ").append(procName)
                    .append("(").append(paramDesc).append(")").append(retDesc).append("\n")
                    .append(".limit stack 16\n")
                    .append(".limit locals ").append(procNumLocals).append("\n");

        // Initialize local variables (not parameters) and the retval slot
        for (Map.Entry<String, Integer> e : procLI.entrySet()) {
            if (info.paramNames.contains(e.getKey())) continue; // params set by caller
            if (e.getKey().equals(procName)) continue; // self-ref slot initialized below
            String varType = procST.get(e.getKey());
            int slot = e.getValue();
            if ("real".equals(varType)) {
                activeOutput.append("dconst_0\n").append("dstore ").append(slot).append("\n");
            } else if ("string".equals(varType)) {
                activeOutput.append("ldc \"\"\n").append("astore ").append(slot).append("\n");
            } else if (varType != null && varType.startsWith("procedure:")) {
                activeOutput.append("aconst_null\n").append("astore ").append(slot).append("\n");
            } else {
                activeOutput.append("iconst_0\n").append("istore ").append(slot).append("\n");
            }
        }
        // Initialize self-reference slot to null
        if (selfRefSlot >= 0) {
            activeOutput.append("aconst_null\n").append("astore ").append(selfRefSlot).append("\n");
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
        } else if ("string".equals(currentProcReturnType)) {
            activeOutput.append("aload ").append(procRetvalSlot).append("\n")
                        .append("areturn\n");
        } else {
            activeOutput.append("iload ").append(procRetvalSlot).append("\n")
                        .append("ireturn\n");
        }
        activeOutput.append(".end method\n\n");

        procMethods.add(procBufferStack.pop().toString());

        // Restore context (supports nested procedures via saved stacks)
        currentSymbolTable    = mainSymbolTable;
        currentLocalIndex     = mainLocalIndex;
        currentNumLocals      = mainNumLocals;
        currentArrayBounds    = mainArrayBounds;
        activeOutput          = procBufferStack.isEmpty() ? mainCode : procBufferStack.peek();
        mainSymbolTable       = savedOuterSTStack.pop();
        mainLocalIndex        = savedOuterLIStack.pop();
        mainNumLocals         = savedOuterNLStack.pop();
        mainArrayBounds       = savedOuterABStack.pop();
        currentProcName       = savedProcNameStack.pop();
        currentProcReturnType = savedProcRetTypeStack.pop();
        procRetvalSlot        = savedProcRetSlotStack.pop();
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
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "boolean[]".equals(elemType) ? "bastore\n" : "string[]".equals(elemType) ? "aastore\n" : "iastore\n");
            return;
        }

        // Scalar (possibly chained) assignment
        String exprType = exprTypes.getOrDefault(ctx.expr(), "integer");

        // Determine storage type: real if any destination is real, string if any destination is string
        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = currentSymbolTable.get(lvName);
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            if (lvName.equals(currentProcName)) return "real".equals(currentProcReturnType);
            return "real".equals(vt);
        });
        boolean anyString = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = currentSymbolTable.get(lvName);
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            if (lvName.equals(currentProcName)) return "string".equals(currentProcReturnType);
            return "string".equals(vt);
        });
        boolean anyProcedure = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = currentSymbolTable.get(lvName);
            return vt != null && vt.startsWith("procedure:");
        });
        String storeType = anyProcedure ? "procedure" : anyReal ? "real" : anyString ? "string" : "integer";

        // Generate expression and widen if needed
        activeOutput.append(generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            activeOutput.append("i2d\n");
        }

        // Store to each destination; dup before all but the last
        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();

            // Procedure return value assignment - but ONLY if the RHS is not a procedure reference
            // (if RHS is a procedure ref, fall through to the normal variable assignment below,
            //  so that `P := hello` inside procedure P stores into P's self-reference slot)
            if (name.equals(currentProcName) && !"procedure".equals(storeType)) {
                if (i < lvalues.size() - 1) {
                    activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                }
                if ("real".equals(currentProcReturnType)) {
                    activeOutput.append("dstore ").append(procRetvalSlot).append("\n");
                } else if ("string".equals(currentProcReturnType)) {
                    activeOutput.append("astore ").append(procRetvalSlot).append("\n");
                } else {
                    activeOutput.append("istore ").append(procRetvalSlot).append("\n");
                }
                continue;
            }

            Integer idx = currentLocalIndex.get(name);
            String varType = currentSymbolTable.get(name);
            boolean isThunk = varType != null && varType.startsWith("thunk:");
            boolean isProcVar = varType != null && varType.startsWith("procedure:");
            if (idx == null && !isThunk && !isProcVar) {
                // Check if this is a static scalar
                if (varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Dup before each putstatic except the last
                    if (i < lvalues.size() - 1) {
                        activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                    }
                    // Static scalar: emit putstatic
                    String jvmDesc = scalarTypeToJvmDesc(varType);
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(name).append(" ").append(jvmDesc).append("\n");
                    continue;
                }
                // Check main symbol table for outer scope static scalars
                if (mainSymbolTable != null) {
                    String mainType = mainSymbolTable.get(name);
                    if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                        // Dup before each putstatic except the last
                        if (i < lvalues.size() - 1) {
                            activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                        }
                        // Static scalar from outer scope: emit putstatic
                        String jvmDesc = scalarTypeToJvmDesc(mainType);
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(name).append(" ").append(jvmDesc).append("\n");
                        continue;
                    }
                }
                activeOutput.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }

            if (i < lvalues.size() - 1) {
                activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            }

            if (isThunk) {
                // assignment to a name parameter: call thunk.set(boxedValue)
                // stack has the primitive/reference value; box it, then swap the thunk ref in
                if ("real".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else if (!"string".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                // push thunk ref, swap so order is: thunk, boxed_value
                activeOutput.append("aload ").append(idx).append("\n");
                activeOutput.append("swap\n");
                activeOutput.append("invokeinterface ")
                            .append("gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
                continue;
            }

            // normal local variable storage
            if ("integer".equals(varType) || "boolean".equals(varType)) {
                activeOutput.append("istore ").append(idx).append("\n");
            } else if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(idx).append("\n");
            } else if ("string".equals(varType)) {
                activeOutput.append("astore ").append(idx).append("\n");
            } else if (isProcVar) {
                activeOutput.append("astore ").append(idx).append("\n");
            }
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
        System.out.println("Processing procedure call: " + name);
        List<AlgolParser.ArgContext> args = ctx.argList() != null ? ctx.argList().arg() : List.of();
        if ("outstring".equals(name)) {
            AlgolParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
            String stream = getChannelStream(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(args.size() - 1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        } else if ("outreal".equals(name)) {
            AlgolParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
            String stream = getChannelStream(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(args.size() - 1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(D)V\n");
        } else if ("outinteger".equals(name)) {
            AlgolParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
            String stream = getChannelStream(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(args.size() - 1).expr()))
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
            String stream = getChannelStream(args.size() > 0 ? args.get(0) : null);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                        .append("ldc \" \"\n")
                        .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        } else if ("ininteger".equals(name)) {
            // ininteger(channel, var) - reads an integer from System.in and stores in var
            AlgolParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof AlgolParser.VarExprContext) {
                String varName = ((AlgolParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                String varType = currentSymbolTable.get(varName);
                if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Static scalar
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextInt()I\n")
                                .append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" I\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextInt()I\n")
                                .append("istore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: ininteger requires a variable as second argument\n");
            }
        } else if ("inreal".equals(name)) {
            // inreal(channel, var) - reads a real from System.in and stores in var
            AlgolParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof AlgolParser.VarExprContext) {
                String varName = ((AlgolParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                String varType = currentSymbolTable.get(varName);
                if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Static scalar
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextDouble()D\n")
                                .append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" D\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextDouble()D\n")
                                .append("dstore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: inreal requires a variable as second argument\n");
            }
        } else if ("inchar".equals(name)) {
            // inchar(channel, str, var) - reads one character and finds its position in str
            String str = args.get(1).getText();
            AlgolParser.ExprContext varExpr = args.get(2).expr();
            if (varExpr instanceof AlgolParser.VarExprContext) {
                String varName = ((AlgolParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                String varType = currentSymbolTable.get(varName);
                if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Static scalar
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/next()Ljava/lang/String;\n")
                                .append("iconst_0\n")
                                .append("invokevirtual java/lang/String/charAt(I)C\n")
                                .append("ldc ").append(str).append("\n")
                                .append("swap\n")
                                .append("invokevirtual java/lang/String/indexOf(I)I\n")
                                .append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" I\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    // Read next token from scanner
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/next()Ljava/lang/String;\n")
                                .append("iconst_0\n")
                                .append("invokevirtual java/lang/String/charAt(I)C\n")
                                .append("ldc ").append(str).append("\n")
                                .append("swap\n")
                                .append("invokevirtual java/lang/String/indexOf(I)I\n")
                                .append("istore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: inchar requires a variable as third argument\n");
            }
        } else if ("instring".equals(name)) {
            // instring(channel, var) - reads a string from System.in and stores in var
            AlgolParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof AlgolParser.VarExprContext) {
                String varName = ((AlgolParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                String varType = currentSymbolTable.get(varName);
                if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Static scalar
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n")
                                .append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" Ljava/lang/String;\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/__scanner Ljava/util/Scanner;\n")
                                .append("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n")
                                .append("astore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: instring requires a variable as second argument\n");
            }
        } else if ("stop".equals(name)) {
            // stop - terminates the program normally
            activeOutput.append("iconst_0\n")
                        .append("invokestatic java/lang/System/exit(I)V\n");
        } else if ("fault".equals(name)) {
            // fault(str, r) - prints error message to System.err then exits with code 1
            activeOutput.append("getstatic java/lang/System/err Ljava/io/PrintStream;\n")
                        .append(generateExpr(args.get(0).expr()))
                        .append("invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V\n")
                        .append("iconst_1\n")
                        .append("invokestatic java/lang/System/exit(I)V\n");
        } else {
            // Check if it's a call through a procedure variable (local or param).
            // Only route through slot when we are INSIDE a procedure (currentProcName != null),
            // because in the outer (main) scope a procedure-variable slot may be uninitialized
            // and the call should be a plain invokestatic.
            String varType = currentSymbolTable.get(name);
            Integer varIdx = currentLocalIndex.get(name);
            boolean isInProc = currentProcName != null;
            if (isInProc && varType != null && varType.startsWith("procedure:") && varIdx != null) {
                // Call through a procedure variable slot
                activeOutput.append(generateProcedureVariableCall(name, varType, args));
            } else {
                // User-defined procedure call (statement form)
                activeOutput.append(generateUserProcedureInvocation(name, args, true));
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
    public void enterBlock(AlgolParser.BlockContext ctx) {
        // Blocks are just containers for statements - no special handling needed
    }

    @Override
    public void exitBlock(AlgolParser.BlockContext ctx) {
        // Blocks are just containers for statements - no special handling needed
    }

    @Override
    public void enterCompoundStatement(AlgolParser.CompoundStatementContext ctx) {
        // Compound statements are just containers for statements - no special handling needed
    }

    @Override
    public void exitCompoundStatement(AlgolParser.CompoundStatementContext ctx) {
        // Compound statements are just containers for statements - no special handling needed
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
            String leftCode  = generateExpr(rel.expr(0));
            String rightCode = generateExpr(rel.expr(1));
            String leftType  = exprTypes.getOrDefault(rel.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(rel.expr(1), "integer");
            String op = rel.op.getText();
            activeOutput.append(leftCode);
            activeOutput.append(rightCode);
            if ("real".equals(leftType) || "real".equals(rightType)) {
                // Real comparison: dcmpg result -1/0/1, then branch
                activeOutput.append("dcmpg\n");
                String cmpInstr = switch (op) {
                    case "<"  -> "iflt";
                    case "<=" -> "ifle";
                    case ">"  -> "ifgt";
                    case ">=" -> "ifge";
                    case "="  -> "ifeq";
                    case "<>" -> "ifne";
                    default   -> "ifne";
                };
                activeOutput.append(cmpInstr).append(" ").append(thenLabel).append("\n");
            } else {
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
            }
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
        // Redirect all body code to a capture buffer; exitForStatement will
        // reconstruct the complete for-list structure with inline body duplication.
        forBodyStack.push(activeOutput);
        activeOutput = new StringBuilder();
    }

    @Override
    public void exitForStatement(AlgolParser.ForStatementContext ctx) {
        String bodyCode = activeOutput.toString();
        activeOutput = forBodyStack.pop();

        String varName = ctx.identifier().getText();
        Integer varIndex = currentLocalIndex.get(varName);
        String varType = currentSymbolTable.get(varName);
        if (varType == null && mainSymbolTable != null) varType = mainSymbolTable.get(varName);
        boolean isStaticScalar = varIndex == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:");
        if (varIndex == null && !isStaticScalar) {
            activeOutput.append("; ERROR: for-loop variable ").append(varName).append(" undeclared\n");
            return;
        }
        boolean varIsThunk = varType != null && varType.startsWith("thunk:");
        String baseVarType = varIsThunk ? varType.substring("thunk:".length()) : varType;

        // Helper for variable store/load in for-loops
        String varStoreInstr = isStaticScalar ? 
            ("real".equals(varType) ? "putstatic " + packageName + "/" + className + "/" + varName + " D\n" : "putstatic " + packageName + "/" + className + "/" + varName + " I\n") :
            ("real".equals(varType) ? "dstore " + varIndex + "\n" : "istore " + varIndex + "\n");
        String varLoadInstr = isStaticScalar ?
            ("real".equals(varType) ? "getstatic " + packageName + "/" + className + "/" + varName + " D\n" : "getstatic " + packageName + "/" + className + "/" + varName + " I\n") :
            ("real".equals(varType) ? "dload " + varIndex + "\n" : "iload " + varIndex + "\n");

        String afterAllLabel = generateUniqueLabel("endfor");

        for (AlgolParser.ForElementContext elem : ctx.forList().forElement()) {
            if (elem instanceof AlgolParser.StepUntilElementContext e) {
                String loopLabel = generateUniqueLabel("loop");
                // init
                activeOutput.append(generateExpr(e.expr(0)));
                if (varIsThunk) {
                    appendBoxAndSetThunk(varIndex, baseVarType);
                } else {
                    activeOutput.append(varStoreInstr);
                }
                activeOutput.append(loopLabel).append(":\n");
                // check condition: var > limit → exit
                if (varIsThunk) {
                    appendLoadThunkValue(varIndex, baseVarType);
                    activeOutput.append(generateExpr(e.expr(2)));
                    if ("real".equals(baseVarType)) {
                        activeOutput.append("dcmpg\nifgt ").append(afterAllLabel).append("\n");
                    } else {
                        activeOutput.append("if_icmpgt ").append(afterAllLabel).append("\n");
                    }
                } else {
                    activeOutput.append(varLoadInstr);
                    activeOutput.append(generateExpr(e.expr(2)));
                    if ("real".equals(varType)) {
                        activeOutput.append("dcmpg\nifgt ").append(afterAllLabel).append("\n");
                    } else {
                        activeOutput.append("if_icmpgt ").append(afterAllLabel).append("\n");
                    }
                }
                // body
                activeOutput.append(bodyCode);
                // increment
                if (varIsThunk) {
                    activeOutput.append("aload ").append(varIndex).append("\n");
                    activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                    if ("real".equals(baseVarType)) {
                        activeOutput.append("checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n");
                        activeOutput.append(generateExpr(e.expr(1)));
                        activeOutput.append("dadd\ninvokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    } else {
                        activeOutput.append("checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n");
                        activeOutput.append(generateExpr(e.expr(1)));
                        activeOutput.append("iadd\ninvokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    }
                    activeOutput.append("aload ").append(varIndex).append("\nswap\n");
                    activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
                } else {
                    activeOutput.append(varLoadInstr);
                    activeOutput.append(generateExpr(e.expr(1)));
                    if ("real".equals(varType)) {
                        activeOutput.append("dadd\n");
                    } else {
                        activeOutput.append("iadd\n");
                    }
                    activeOutput.append(varStoreInstr);
                }
                activeOutput.append("goto ").append(loopLabel).append("\n");

            } else if (elem instanceof AlgolParser.WhileElementContext e) {
                // while semantics: re-evaluate expr AND condition each iteration
                String loopLabel = generateUniqueLabel("loop");
                activeOutput.append(loopLabel).append(":\n");
                activeOutput.append(generateExpr(e.expr(0)));
                if (varIsThunk) {
                    appendBoxAndSetThunk(varIndex, baseVarType);
                } else {
                    activeOutput.append(varStoreInstr);
                }
                activeOutput.append(generateExpr(e.expr(1))); // while condition → 0 or 1
                activeOutput.append("ifeq ").append(afterAllLabel).append("\n");
                activeOutput.append(bodyCode);
                activeOutput.append("goto ").append(loopLabel).append("\n");

            } else {
                // SimpleElement: execute exactly once
                AlgolParser.SimpleElementContext e = (AlgolParser.SimpleElementContext) elem;
                activeOutput.append(generateExpr(e.expr()));
                if (varIsThunk) {
                    appendBoxAndSetThunk(varIndex, baseVarType);
                } else {
                    activeOutput.append(varStoreInstr);
                }
                activeOutput.append(bodyCode);
            }
        }
        activeOutput.append(afterAllLabel).append(":\n");
    }

    /** Box the top-of-stack value and set it into a thunk via thunk.set(). */
    private void appendBoxAndSetThunk(int varIndex, String baseType) {
        if ("real".equals(baseType)) {
            activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
        } else {
            activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
        }
        activeOutput.append("aload ").append(varIndex).append("\nswap\n");
        activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
    }

    /** Load the current value out of a thunk (unboxed onto the JVM stack). */
    private void appendLoadThunkValue(int varIndex, String baseType) {
        activeOutput.append("aload ").append(varIndex).append("\n");
        activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
        if ("real".equals(baseType)) {
            activeOutput.append("checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n");
        } else {
            activeOutput.append("checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n");
        }
    }

    // -------------------------------------------------------------------------
    // Unique label counter
    // -------------------------------------------------------------------------

    private int labelCounter = 0;

    private String generateUniqueLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    /** Returns the JVM array descriptor for a JAlgol array type. */
    
    // ---------- helpers for thunk / variable box support ----------

    /**
     * Allocate a fresh local variable slot with a generated name hint
     * and update locals limit accordingly.
     */
    private int allocateNewLocal(String hint) {
        int slot = currentNumLocals;
        String name = hint + slot;
        currentLocalIndex.put(name, slot);
        currentNumLocals += 1;
        ensureLocalLimit(currentNumLocals);
        return slot;
    }

    /**
     * Ensure that the current method's .limit locals directive is at least the
     * given value.  Scans the activeOutput or classHeader/procBufferStack for the
     * directive and updates it.
     */
    private void ensureLocalLimit(int required) {
        StringBuilder buf = procBufferStack.isEmpty() ? mainCode : procBufferStack.peek();
        String search = ".limit locals ";
        int idx = buf.indexOf(search);
        if (idx >= 0) {
            int end = buf.indexOf("\n", idx);
            if (end > idx) {
                buf.replace(idx + search.length(), end, Integer.toString(required));
            }
        }
    }

    /**
     * Collect all simple variable names occurring in an expression tree.
     */
    private Set<String> collectVarNames(ExprContext ctx) {
        Set<String> names = new LinkedHashSet<>();
        if (ctx instanceof AlgolParser.VarExprContext ve) {
            names.add(ve.identifier().getText());
        } else if (ctx instanceof AlgolParser.ArrayAccessExprContext ae) {
            names.add(ae.identifier().getText());
            names.addAll(collectVarNames(ae.expr()));
        } else if (ctx instanceof AlgolParser.ProcCallExprContext pc) {
            for (AlgolParser.ArgContext a : pc.argList().arg()) {
                if (a.expr() != null) names.addAll(collectVarNames(a.expr()));
            }
        } else if (ctx instanceof AlgolParser.RelExprContext re) {
            names.addAll(collectVarNames(re.expr(0)));
            names.addAll(collectVarNames(re.expr(1)));
        } else if (ctx instanceof AlgolParser.MulDivExprContext me) {
            names.addAll(collectVarNames(me.expr(0)));
            names.addAll(collectVarNames(me.expr(1)));
        } else if (ctx instanceof AlgolParser.AddSubExprContext ae) {
            names.addAll(collectVarNames(ae.expr(0)));
            names.addAll(collectVarNames(ae.expr(1)));
        } else if (ctx instanceof AlgolParser.AndExprContext ae) {
            names.addAll(collectVarNames(ae.expr(0)));
            names.addAll(collectVarNames(ae.expr(1)));
        } else if (ctx instanceof AlgolParser.UnaryMinusExprContext ue) {
            names.addAll(collectVarNames(ue.expr()));
        } else if (ctx instanceof AlgolParser.ParenExprContext pe) {
            names.addAll(collectVarNames(pe.expr()));
        }
        // other cases (literals, true/false) add nothing
        return names;
    }

    /**
     * Generate code to load the current value of a named variable (ignores thunk
     * state).  Used when initializing boxes.
     */
    private String generateLoadVar(String name) {
        Integer idx = currentLocalIndex.get(name);
        String type = currentSymbolTable.get(name);
        if (idx == null) {
            // Check if this is a static scalar
            if (type != null && !type.endsWith("[]") && !type.startsWith("procedure:") && !type.startsWith("thunk:")) {
                String jvmDesc = scalarTypeToJvmDesc(type);
                return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
            }
            // Check main symbol table for outer scope static scalars
            if (mainSymbolTable != null) {
                String mainType = mainSymbolTable.get(name);
                if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                    String jvmDesc = scalarTypeToJvmDesc(mainType);
                    return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
                }
            }
            return "; ERROR: undeclared variable " + name + "\n";
        }
        if (type != null && type.startsWith("thunk:")) {
            // thunk formal inside caller? not expected here
            type = type.substring("thunk:".length());
        }
        if ("integer".equals(type) || "boolean".equals(type)) {
            return "iload " + idx + "\n";
        } else if ("real".equals(type)) {
            return "dload " + idx + "\n";
        } else if ("string".equals(type)) {
            return "aload " + idx + "\n";
        } else {
            return "; ERROR: unknown var type " + type + "\n";
        }
    }

    /**
     * Build a thunk class definition for one call-by-name parameter.
     * varToField maps variable names referenced by this argument to a field index
     * within the thunk class.  actual may be null (in case of string literal arg)
     * and baseType gives the underlying Algol type ("integer", "real", "string").
     * Returns the internal class name (e.g. "pkg/Cls$Thunk0").
     */
    private String createThunkClass(Map<String,Integer> varToField, ExprContext actual, String baseType) {
        String thunkLocalName = className + "$Thunk" + thunkCounter++;
        String internalName = packageName + "/" + thunkLocalName;
        StringBuilder cls = new StringBuilder();
        cls.append(".class public ").append(internalName).append("\n");
        cls.append(".super java/lang/Object\n");
        cls.append(".implements gnb/jalgol/compiler/Thunk\n\n");
        // fields for each referenced variable box
        for (int i = 0; i < varToField.size(); i++) {
            cls.append(".field public box").append(i).append(" [Ljava/lang/Object;\n");
        }
        cls.append("\n");
        // constructor
        StringBuilder ctorDesc = new StringBuilder("(");
        for (int i = 0; i < varToField.size(); i++) {
            ctorDesc.append("[Ljava/lang/Object;");
        }
        ctorDesc.append(")V");
        cls.append(".method public <init>").append(ctorDesc).append("\n");
        cls.append(".limit stack 10\n");
        cls.append(".limit locals ").append(varToField.size() + 1).append("\n");
        cls.append("aload_0\n");
        cls.append("invokespecial java/lang/Object/<init>()V\n");
        // store constructor args into fields
        for (int i = 0; i < varToField.size(); i++) {
            cls.append("aload_0\n");
            // load parameter i+1 (slot 1..)
            if (i + 1 <= 3) {
                cls.append("aload_").append(i+1).append("\n");
            } else {
                cls.append("aload ").append(i+1).append("\n");
            }
            cls.append("putfield ").append(internalName).append("/box").append(i)
               .append(" [Ljava/lang/Object;\n");
        }
        cls.append("return\n");
        cls.append(".end method\n\n");
        // generate get() method
        cls.append(".method public get()Ljava/lang/Object;\n");
        cls.append(".limit stack 10\n");
        cls.append(".limit locals 1\n");
        if (actual != null) {
            // generate expression code inside thunk, using mapping from vars to field indexes
            cls.append(generateExpr(actual, varToField));
            // result on stack is primitive or reference; box it if necessary
            String actualExprType = exprTypes.getOrDefault(actual, "integer");
            switch (baseType) {
                case "real":
                    // coerce integer expression to double if needed before boxing
                    if ("integer".equals(actualExprType)) cls.append("i2d\n");
                    cls.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    break;
                case "integer":
                case "boolean":
                    cls.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    break;
                default:
                    // string already reference, nothing to box
                    break;
            }
        } else {
            // no expression (shouldn't happen) just return null
            cls.append("aconst_null\n");
        }
        cls.append("areturn\n");
        cls.append(".end method\n\n");
        // generate set(Object) method
        cls.append(".method public set(Ljava/lang/Object;)V\n");
        cls.append(".limit stack 10\n");
        cls.append(".limit locals 2\n");
        // for simplicity always store value into first box field if any
        if (!varToField.isEmpty()) {
            // store into first box regardless; actual semantics for expr not needed
            cls.append("aload_0\n");
            cls.append("getfield ").append(internalName).append("/box0 [Ljava/lang/Object;\n");
            cls.append("iconst_0\n");
            cls.append("aload_1\n");
            cls.append("aastore\n");
        }
        cls.append("return\n");
        cls.append(".end method\n\n");

        thunkClassDefinitions.add(Map.entry(thunkLocalName, cls.toString()));
        return internalName;
    }

    // -------------------------------------------------------------
    // End thunk/helper methods
    // -------------------------------------------------------------

    /**
     * Generate Jasmin code for a user-defined procedure call, handling both
     * standard value parameters and call-by-name (thunk) parameters.
     *
     * @param name the procedure name
     * @param args the argument contexts from the call site
     * @param isStatement whether the call appears in statement position; if true,
     *                    the return value will be popped when non-void.
     * @return a string containing the Jasmin instructions for the call (including
     *         any temporary box initialization and variable restoration).
     */
    private String generateUserProcedureInvocation(String name, List<AlgolParser.ArgContext> args, boolean isStatement) {
        SymbolTableBuilder.ProcInfo info = procedures.get(name);
        if (info == null) {
            return "; unknown procedure: " + name + "\n";
        }

        StringBuilder sb = new StringBuilder();
        // Determine which parameters are call-by-name and collect variable
        // boxes needed for all of them.
        Map<String,Integer> varToBoxSlot = new LinkedHashMap<>();
        Set<String> varsToRestore = new LinkedHashSet<>();

        // first pass: discover variables referenced by each name argument
        List<AlgolParser.ArgContext> argList = args;
        for (int ai = 0; ai < argList.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            boolean isValue = info.valueParams.contains(paramName);
            if (!isValue) {
                AlgolParser.ArgContext arg = argList.get(ai);
                if (arg.expr() != null) {
                    Set<String> names = collectVarNames(arg.expr());
                    for (String vn : names) {
                        if (!varToBoxSlot.containsKey(vn)) {
                            int slot = allocateNewLocal("__box_" + vn);
                            varToBoxSlot.put(vn, slot);
                        }
                    }
                    if (arg.expr() instanceof AlgolParser.VarExprContext) {
                        varsToRestore.add(((AlgolParser.VarExprContext)arg.expr()).identifier().getText());
                    }
                }
            }
        }

        // allocate and initialize boxes in caller
        for (Map.Entry<String,Integer> e : varToBoxSlot.entrySet()) {
            String vn = e.getKey();
            int slot = e.getValue();
            String varType = currentSymbolTable.get(vn);
            sb.append("iconst_1\n");
            sb.append("anewarray java/lang/Object\n");
            sb.append("astore ").append(slot).append("\n");
            sb.append(generateLoadVar(vn));
            if ("real".equals(varType)) {
                sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
            } else if ("integer".equals(varType) || "boolean".equals(varType)) {
                sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
            }
            sb.append("aload ").append(slot).append("\n");
            sb.append("swap\n");
            sb.append("iconst_0\n");
            sb.append("swap\n");
            sb.append("aastore\n");
        }

        // second pass: push arguments, creating thunks as needed
        for (int ai = 0; ai < argList.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            boolean isValue = info.valueParams.contains(paramName);
            AlgolParser.ArgContext arg = argList.get(ai);
            if (isValue) {
                if (arg.expr() != null) {
                    sb.append(generateExpr(arg.expr()));
                    String paramType = info.paramTypes.getOrDefault(paramName, "integer");
                    String argType = exprTypes.getOrDefault(arg.expr(), "integer");
                    if ("real".equals(paramType) && "integer".equals(argType)) {
                        sb.append("i2d\n");
                    }
                } else {
                    sb.append(arg.getText()).append("\n");
                }
            } else {
                ExprContext actual = arg.expr();
                Set<String> names = actual != null ? collectVarNames(actual) : Set.of();
                Map<String,Integer> varToField = new LinkedHashMap<>();
                int fi = 0;
                for (String vn : names) {
                    varToField.put(vn, fi++);
                }
                String baseType = info.paramTypes.getOrDefault(paramName, "integer");
                String thunkClass = createThunkClass(varToField, actual, baseType);
                sb.append("new ").append(thunkClass).append("\n");
                sb.append("dup\n");
                for (String vn : varToField.keySet()) {
                    int boxSlot = varToBoxSlot.get(vn);
                    sb.append("aload ").append(boxSlot).append("\n");
                }
                String ctorDesc = varToField.keySet().stream()
                                    .map(vn -> "[Ljava/lang/Object;")
                                    .collect(Collectors.joining("", "(", ")V"));
                sb.append("invokespecial ").append(thunkClass)
                            .append("/<init>").append(ctorDesc).append("\n");
            }
        }

        // perform invocation
        String paramDesc = info.paramNames.stream()
            .map(p -> {
                if (!info.valueParams.contains(p)) {
                    return "Lgnb/jalgol/compiler/Thunk;";
                }
                String type = info.paramTypes.getOrDefault(p, "integer");
                if ("real".equals(type)) return "D";
                if ("string".equals(type)) return "Ljava/lang/String;";
                if (type.startsWith("procedure:")) {
                    return switch (type.substring("procedure:".length())) {
                        case "real" -> "Lgnb/jalgol/compiler/RealProcedure;";
                        case "integer" -> "Lgnb/jalgol/compiler/IntegerProcedure;";
                        case "string" -> "Lgnb/jalgol/compiler/StringProcedure;";
                        default -> "Lgnb/jalgol/compiler/VoidProcedure;";
                    };
                }
                return "I";
            })
            .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";
        sb.append("invokestatic ").append(packageName).append("/")
                    .append(className).append("/").append(name)
                    .append("(").append(paramDesc).append(")").append(retDesc).append("\n");
        if (isStatement && !"V".equals(retDesc)) {
            if ("D".equals(retDesc)) sb.append("pop2\n");
            else sb.append("pop\n");
        }

        // restore caller variables
        for (String vn : varsToRestore) {
            int boxSlot = varToBoxSlot.get(vn);
            Integer varSlot = currentLocalIndex.get(vn);
            String varType = currentSymbolTable.get(vn);
            sb.append("aload ").append(boxSlot).append("\n");
            sb.append("iconst_0\n");
            sb.append("aaload\n");
            if ("real".equals(varType)) {
                sb.append("checkcast java/lang/Double\n");
                sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
                if (varSlot != null) {
                    sb.append("dstore ").append(varSlot).append("\n");
                } else {
                    // Static scalar
                    sb.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(vn).append(" D\n");
                }
            } else if ("string".equals(varType)) {
                sb.append("checkcast java/lang/String\n");
                if (varSlot != null) {
                    sb.append("astore ").append(varSlot).append("\n");
                } else {
                    // Static scalar
                    sb.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(vn).append(" Ljava/lang/String;\n");
                }
            } else {
                sb.append("checkcast java/lang/Integer\n");
                sb.append("invokevirtual java/lang/Integer/intValue()I\n");
                if (varSlot != null) {
                    sb.append("istore ").append(varSlot).append("\n");
                } else {
                    // Static scalar
                    sb.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(vn).append(" I\n");
                }
            }
        }

        return sb.toString();
    }
    private static String arrayTypeToJvmDesc(String arrayType) {
        return switch (arrayType) {
            case "boolean[]" -> "[Z";
            case "integer[]" -> "[I";
            case "real[]"    -> "[D";
            case "string[]"  -> "[Ljava/lang/String;";
            default -> "[I";
        };
    }

    private static String scalarTypeToJvmDesc(String scalarType) {
        return switch (scalarType) {
            case "boolean", "integer" -> "I";
            case "real" -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
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

    /**
     * Generate code for an expression. When varToFieldIndex is non-null, it maps
     * variable names to a field index inside a thunk object; the generated code
     * will load from thunk fields rather than normal local variables.
     */
    private String generateExpr(ExprContext ctx) {
        return generateExpr(ctx, null);
    }

    private String generateExpr(ExprContext ctx, Map<String,Integer> varToFieldIndex) {
        if (ctx instanceof AlgolParser.RelExprContext e) {
            String leftCode  = generateExpr(e.expr(0), varToFieldIndex);
            String rightCode = generateExpr(e.expr(1), varToFieldIndex);
            String leftType  = exprTypes.getOrDefault(e.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(e.expr(1), "integer");
            String op = e.op.getText();
            String trueLabel = generateUniqueLabel("rel_true");
            String endLabel  = generateUniqueLabel("rel_end");
            if ("real".equals(leftType) || "real".equals(rightType)) {
                // Real comparison: coerce to double, use dcmpg + branch
                if ("integer".equals(leftType))  leftCode  += "i2d\n";
                if ("integer".equals(rightType)) rightCode += "i2d\n";
                String cmpInstr = switch (op) {
                    case "<"  -> "iflt";
                    case "<="  -> "ifle";
                    case ">"  -> "ifgt";
                    case ">="  -> "ifge";
                    case "="  -> "ifeq";
                    case "<>" -> "ifne";
                    default   -> throw new RuntimeException("Unknown rel op " + op);
                };
                return leftCode + rightCode + "dcmpg\n" + cmpInstr + " " + trueLabel + "\n" +
                    "iconst_0\ngoto " + endLabel + "\n" +
                    trueLabel + ":\niconst_1\n" +
                    endLabel + ":\n";
            } else {
                // Integer comparison
                String cmpOp = switch (op) {
                    case "<"  -> "lt";
                    case "<="  -> "le";
                    case ">"  -> "gt";
                    case ">="  -> "ge";
                    case "="  -> "eq";
                    case "<>" -> "ne";
                    default   -> throw new RuntimeException("Unknown rel op " + op);
                };
                return leftCode + rightCode + "if_icmp" + cmpOp + " " + trueLabel + "\n" +
                    "iconst_0\ngoto " + endLabel + "\n" +
                    trueLabel + ":\niconst_1\n" +
                    endLabel + ":\n";
            }
        } else if (ctx instanceof AlgolParser.IfExprContext e) {
            // if-then-else as expression (mandatory else branch)
            String condCode  = generateExpr(e.expr(0), varToFieldIndex);
            String thenCode  = generateExpr(e.expr(1), varToFieldIndex);
            String elseCode  = generateExpr(e.expr(2), varToFieldIndex);
            String resultType = exprTypes.getOrDefault(e, "integer");
            String thenType  = exprTypes.getOrDefault(e.expr(1), "integer");
            String elseType  = exprTypes.getOrDefault(e.expr(2), "integer");
            if ("real".equals(resultType) && "integer".equals(thenType)) thenCode += "i2d\n";
            if ("real".equals(resultType) && "integer".equals(elseType)) elseCode += "i2d\n";
            String elseLabel = generateUniqueLabel("ifexpr_else");
            String endLabel  = generateUniqueLabel("ifexpr_end");
            return condCode +
                "ifeq " + elseLabel + "\n" +
                thenCode +
                "goto " + endLabel + "\n" +
                elseLabel + ":\n" +
                elseCode +
                endLabel + ":\n";
        } else if (ctx instanceof AlgolParser.MulDivExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
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
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
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
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "iand\n";
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();

            // If we're generating code inside a thunk, some variables may be
            // stored in thunk fields instead of caller locals.
            if (varToFieldIndex != null && varToFieldIndex.containsKey(name)) {
                int fieldIdx = varToFieldIndex.get(name);
                StringBuilder sb = new StringBuilder();
                // load boxes field for this variable
                sb.append("aload_0\n");
                sb.append("getfield ").append(packageName).append("/")
                  .append(className).append("$Thunk").append((thunkCounter-1))
                  .append("/box").append(fieldIdx).append(" [Ljava/lang/Object;\n");
                sb.append("iconst_0\n");
                sb.append("aaload\n");
                // now unbox based on expected type in currentSymbolTable? but inside thunk
                // we want primitive for expression evaluation.  Determine base type from
                // info? We'll look up underlying type from currentSymbolTable if it
                // contains a thunk entry.  Otherwise default integer.
                String type = currentSymbolTable.get(name);
                String baseType = "integer";
                if (type != null && type.startsWith("thunk:")) {
                    baseType = type.substring("thunk:".length());
                } else if (type != null) {
                    baseType = type;
                }
                switch (baseType) {
                    case "real":
                        sb.append("checkcast java/lang/Double\n");
                        sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
                        break;
                    case "string":
                        sb.append("checkcast java/lang/String\n");
                        break;
                    default: // integer/boolean
                        sb.append("checkcast java/lang/Integer\n");
                        sb.append("invokevirtual java/lang/Integer/intValue()I\n");
                        break;
                }
                return sb.toString();
            }

            // Check for environmental constants first
            if ("maxreal".equals(name)) {
                return "ldc2_w " + Double.MAX_VALUE + "\n";
            } else if ("minreal".equals(name)) {
                return "ldc2_w " + Double.MIN_VALUE + "\n";
            } else if ("maxint".equals(name)) {
                return "ldc " + Integer.MAX_VALUE + "\n";
            } else if ("epsilon".equals(name)) {
                // Machine epsilon - using Double.MIN_NORMAL as a reasonable approximation
                return "ldc2_w " + Double.MIN_NORMAL + "\n";
            }

            // Check if this is the current procedure's return value OR a procedure self-reference variable
            if (name.equals(currentProcName)) {
                // If the procedure name has a local slot as a procedure variable, load from that slot
                Integer selfSlot = currentLocalIndex.get(name);
                String selfType = currentSymbolTable.get(name);
                if (selfSlot != null && selfType != null && selfType.startsWith("procedure:")) {
                    // Procedure variable used in expression context: call through the variable
                    return generateProcedureVariableCall(name, selfType, List.of());
                }
                // Otherwise it's a return-value reference
                if ("real".equals(currentProcReturnType)) {
                    return "dload " + procRetvalSlot + "\n";
                } else if ("string".equals(currentProcReturnType)) {
                    return "aload " + procRetvalSlot + "\n";
                } else {
                    return "iload " + procRetvalSlot + "\n";
                }
            }

            // Check if it's a procedure variable slot (local) - load the reference
            Integer localIdx = currentLocalIndex.get(name);
            if (localIdx != null) {
                String localType = currentSymbolTable.get(name);
                if (localType != null && localType.startsWith("procedure:")) {
                    // Load the ProcRef object from the slot
                    return "aload " + localIdx + "\n";
                }
            }

            // Check if this is a procedure reference (procedure used as a value - generate a ProcRef wrapper)
            SymbolTableBuilder.ProcInfo procInfo = procedures.get(name);
            if (procInfo != null) {
                return generateProcedureReference(name, procInfo);
            }

            // Regular variable lookup
            Integer idx = currentLocalIndex.get(name);
            String type = currentSymbolTable.get(name);
            if (idx == null) {
                // Check if this is a static scalar (no local slot, but exists in symbol table)
                if (type != null && !type.endsWith("[]") && !type.startsWith("procedure:") && !type.startsWith("thunk:")) {
                    // Static scalar: emit getstatic
                    String jvmDesc = scalarTypeToJvmDesc(type);
                    return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
                }
                // Check main symbol table for outer scope static scalars
                if (mainSymbolTable != null) {
                    String mainType = mainSymbolTable.get(name);
                    if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                        // Static scalar from outer scope: emit getstatic
                        String jvmDesc = scalarTypeToJvmDesc(mainType);
                        return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
                    }
                }
                return "; ERROR: undeclared variable " + name + "\n";
            }
            // support call-by-name thunk parameters
            if (type != null && type.startsWith("thunk:")) {
                String baseType = type.substring("thunk:".length());
                StringBuilder sb2 = new StringBuilder();
                sb2.append("aload ").append(idx).append("\n");
                sb2.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                switch (baseType) {
                    case "real" -> {
                        sb2.append("checkcast java/lang/Double\n");
                        sb2.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    }
                    case "string" -> sb2.append("checkcast java/lang/String\n");
                    default -> {
                        sb2.append("checkcast java/lang/Integer\n");
                        sb2.append("invokevirtual java/lang/Integer/intValue()I\n");
                    }
                }
                return sb2.toString();
            }
            if ("integer".equals(type) || "boolean".equals(type)) {
                return "iload " + idx + "\n";
            } else if ("real".equals(type)) {
                return "dload " + idx + "\n";
            } else if ("string".equals(type)) {
                return "aload " + idx + "\n";
            } else if (type != null && type.startsWith("procedure:")) {
                // Procedure variable: call through the stored reference
                return generateProcedureVariableCall(name, type, List.of());
            } else {
                return "; ERROR: unknown variable type " + type + "\n";
            }
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
            sb.append(generateExpr(e.expr(), varToFieldIndex));
            if (lower != 0) {
                sb.append("ldc ").append(lower).append("\n");
                sb.append("isub\n");
            }
            sb.append("real[]".equals(elemType) ? "daload\n" : "boolean[]".equals(elemType) ? "baload\n" : "string[]".equals(elemType) ? "aaload\n" : "iaload\n");
            return sb.toString();
        } else if (ctx instanceof AlgolParser.ProcCallExprContext e) {
            String procName = e.identifier().getText();
            
            // Check for built-in math functions first
            String builtinCode = generateBuiltinMathFunction(procName, e);
            if (builtinCode != null) {
                return builtinCode;
            }
            
            // Check if this is a procedure variable call
            String varType = currentSymbolTable.get(procName);
            if (varType != null && varType.startsWith("procedure:")) {
                return generateProcedureVariableCall(procName, varType, e.argList().arg());
            }
            
            // Delegate user-defined procedures (handles call-by-name internally)
            return generateUserProcedureInvocation(procName, e.argList().arg(), false);
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext e) {
            return "ldc2_w " + e.realLiteral().getText() + "d\n";
        } else if (ctx instanceof AlgolParser.IntLiteralExprContext e) {
            return "ldc " + e.unsignedInt().getText() + "\n";
        } else if (ctx instanceof AlgolParser.StringLiteralExprContext e) {
            return "ldc " + e.string().getText() + "\n";
        } else if (ctx instanceof AlgolParser.TrueLiteralExprContext) {
            return "iconst_1\n";
        } else if (ctx instanceof AlgolParser.FalseLiteralExprContext) {
            return "iconst_0\n";
        } else if (ctx instanceof AlgolParser.UnaryMinusExprContext e) {
            String type = exprTypes.getOrDefault(ctx, "integer");
            String inner = generateExpr(e.expr(), varToFieldIndex);
            if ("real".equals(type)) {
                return inner + "dneg\n";
            } else {
                return inner + "ineg\n";
            }
        } else if (ctx instanceof AlgolParser.ParenExprContext e) {
            return generateExpr(e.expr(), varToFieldIndex);
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

    /**
     * Generates code to create a procedure reference object for the given procedure.
     * Creates a synthetic class that implements the appropriate procedure interface
     * and delegates calls to the actual procedure method.
     */
    private String generateProcedureReference(String procName, SymbolTableBuilder.ProcInfo procInfo) {
        String returnType = procInfo.returnType;
        
        // Determine the interface to implement
        String interfaceName;
        switch (returnType) {
            case "void": interfaceName = "VoidProcedure"; break;
            case "real": interfaceName = "RealProcedure"; break;
            case "integer": interfaceName = "IntegerProcedure"; break;
            case "string": interfaceName = "StringProcedure"; break;
            default: throw new RuntimeException("Unknown procedure return type: " + returnType);
        }
        
        // Generate unique class name for this procedure reference
        String procRefClassName = className + "$ProcRef" + procRefCounter++;
        String fullClassName = packageName + "/" + procRefClassName;
        
        // Build the Jasmin source for the procedure reference class
        StringBuilder jasmin = new StringBuilder();
        jasmin.append(".source ").append(procRefClassName).append(".j\n");
        jasmin.append(".class public ").append(fullClassName).append("\n");
        jasmin.append(".super java/lang/Object\n");
        jasmin.append(".implements gnb/jalgol/compiler/").append(interfaceName).append("\n\n");
        
        // Constructor
        jasmin.append(".method public <init>()V\n");
        jasmin.append("    .limit stack 1\n");
        jasmin.append("    .limit locals 1\n");
        jasmin.append("    aload_0\n");
        jasmin.append("    invokenonvirtual java/lang/Object/<init>()V\n");
        jasmin.append("    return\n");
        jasmin.append(".end method\n\n");
        
        // Implement the interface method — unbox args from Object[] and call the static procedure
        jasmin.append(".method public invoke([").append("Ljava/lang/Object;").append(")").append(getReturnTypeDescriptor(returnType)).append("\n");
        jasmin.append("    .limit stack 20\n");
        jasmin.append("    .limit locals 20\n");

        // Unbox each argument from the Object[] (slot 1) and push onto JVM stack
        List<String> paramNames = procInfo.paramNames;
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            String paramType = procInfo.paramTypes.getOrDefault(paramName, "integer");
            jasmin.append("    aload_1\n");          // load Object[] args
            jasmin.append("    ldc ").append(i).append("\n");
            jasmin.append("    aaload\n");             // load Object from array
            // Use java.lang.Number to handle both Integer and Double boxing
            jasmin.append("    checkcast java/lang/Number\n");
            if ("real".equals(paramType)) {
                jasmin.append("    invokevirtual java/lang/Number/doubleValue()D\n");
            } else {
                jasmin.append("    invokevirtual java/lang/Number/intValue()I\n");
            }
        }

        // Call the actual procedure
        String paramDesc = procInfo.paramNames.stream()
            .map(p -> {
                if (procInfo.valueParams.contains(p)) {
                    String type = procInfo.paramTypes.getOrDefault(p, "integer");
                    return switch (type) {
                        case "real" -> "D";
                        case "string" -> "Ljava/lang/String;";
                        default -> "I";
                    };
                } else {
                    return "Lgnb/jalgol/compiler/Thunk;";
                }
            })
            .collect(Collectors.joining());
        String retDesc = switch (returnType) {
            case "void" -> "V";
            case "real" -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
        };
        jasmin.append("    invokestatic ").append(packageName).append("/").append(className)
              .append("/").append(procName).append("(").append(paramDesc).append(")").append(retDesc).append("\n");
        
        if (!"void".equals(returnType)) {
            jasmin.append("    ").append(getReturnInstruction(returnType)).append("\n");
        } else {
            jasmin.append("    return\n");
        }
        
        jasmin.append(".end method\n");
        
        // Store the generated class
        procRefClassDefinitions.add(Map.entry(procRefClassName, jasmin.toString()));
        
        // Generate instantiation code
        StringBuilder instantiation = new StringBuilder();
        instantiation.append("new ").append(fullClassName).append("\n");
        instantiation.append("dup\n");
        instantiation.append("invokenonvirtual ").append(fullClassName).append("/<init>()V\n");
        
        return instantiation.toString();
    }
    
    private String getReturnTypeDescriptor(String returnType) {
        switch (returnType) {
            case "void": return "V";
            case "real": return "D";
            case "string": return "Ljava/lang/String;";
            default: return "I";
        }
    }
    
    private String getReturnInstruction(String returnType) {
        switch (returnType) {
            case "real": return "dreturn";
            case "string": return "areturn";
            default: return "ireturn";
        }
    }

    /**
     * Generates code to call a procedure through a procedure variable.
     * Arguments are boxed into an Object[] and passed via the ProcRef interface.
     */
    private String generateProcedureVariableCall(String varName, String varType, List<AlgolParser.ArgContext> args) {
        String returnType = varType.substring("procedure:".length());
        String interfaceName;
        switch (returnType) {
            case "void": interfaceName = "VoidProcedure"; break;
            case "real": interfaceName = "RealProcedure"; break;
            case "integer": interfaceName = "IntegerProcedure"; break;
            case "string": interfaceName = "StringProcedure"; break;
            default: throw new RuntimeException("Unknown procedure return type: " + returnType);
        }

        StringBuilder sb = new StringBuilder();

        // Load the procedure reference object
        Integer idx = currentLocalIndex.get(varName);
        if (idx == null && mainLocalIndex != null) idx = mainLocalIndex.get(varName);
        if (idx == null) idx = procVarSlots.get(varName);
        if (idx == null) {
            return "; ERROR: undeclared procedure variable " + varName + "\n";
        }
        sb.append("aload ").append(idx).append("\n");
        sb.append("checkcast gnb/jalgol/compiler/").append(interfaceName).append("\n");

        // Build Object[] with actual arguments (boxed)
        int argCount = args.size();
        if (argCount == 0) {
            sb.append("iconst_0\nanewarray java/lang/Object\n");
        } else {
            sb.append("ldc ").append(argCount).append("\n");
            sb.append("anewarray java/lang/Object\n");
            for (int i = 0; i < argCount; i++) {
                AlgolParser.ExprContext argExpr = args.get(i).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append("dup\n");
                sb.append("ldc ").append(i).append("\n");
                sb.append(generateExpr(argExpr));
                if ("real".equals(argType)) {
                    sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else {
                    sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                sb.append("aastore\n");
            }
        }

        sb.append("invokeinterface gnb/jalgol/compiler/").append(interfaceName)
          .append("/invoke([Ljava/lang/Object;)").append(getReturnTypeDescriptor(returnType)).append(" 2\n");

        return sb.toString();
    }
}

