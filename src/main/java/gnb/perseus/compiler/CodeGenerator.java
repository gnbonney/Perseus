// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.AlgolBaseListener;
import gnb.perseus.compiler.antlr.AlgolParser;
import gnb.perseus.compiler.antlr.AlgolParser.ExprContext;
import gnb.perseus.compiler.codegen.BuiltinFunctionGenerator;
import gnb.perseus.compiler.codegen.ProcedureGenerator;
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
    // Switch declarations from SymbolTableBuilder (name -> parse context)
    private final Map<String, AlgolParser.SwitchDeclContext> switchDeclarations;

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
    private final Deque<Map<String, Integer>> savedEnvParamSlotsStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedEnvLocalSlotsStack = new LinkedList<>();
    private final Deque<Integer>              savedEnvRetSaveSlotStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedNestedSelfThunkSlotsStack = new LinkedList<>();
    // Tracks whether the current procedure declaration is actually a procedure-variable declaration
    // (i.e., a `procedure p;` with no executable body) so we can skip generating a method for it.
    private final Deque<Boolean>              skipProcedureDeclStack = new LinkedList<>();

    // Maps expression contexts to their inferred types ("integer" or "real")
    private final Map<AlgolParser.ExprContext, String> exprTypes;

    // --- Output buffers ---
    private final StringBuilder classHeader = new StringBuilder();
    private final StringBuilder mainCode    = new StringBuilder();
    private final List<String>  procMethods = new ArrayList<>();
    private StringBuilder activeOutput;   // points to mainCode or top of procBufferStack
    private final Deque<StringBuilder> procBufferStack = new ArrayDeque<>();

    // Tracks whether any top-level (main) executable statements were emitted
    // so we can optionally auto-invoke a default entry procedure if none were.
    private boolean mainHadExecutableStatements = false;

    // --- Procedure return-value tracking ---
    private String currentProcName = null;
    private String currentProcReturnType = null;
    private int    procRetvalSlot = -1;
    private Map<String, Integer> currentEnvParamSlots = new LinkedHashMap<>();
    private Map<String, Integer> currentEnvLocalSlots = new LinkedHashMap<>();
    private int currentEnvRetSaveSlot = -1;
    private Map<String, Integer> currentNestedSelfThunkSlots = new LinkedHashMap<>();

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

    // Delegate for generating built-in math and string function calls
    private final BuiltinFunctionGenerator builtinGen;
    // Delegate for generating procedure reference and procedure-variable call code
    private final ProcedureGenerator procGen;

    public CodeGenerator(String source, String packageName, String className,
                         Map<String, String> symbolTable, Map<String, Integer> localIndex, int numLocals,
                         Map<AlgolParser.ExprContext, String> exprTypes, Map<String, int[]> arrayBounds,
                         Map<String, SymbolTableBuilder.ProcInfo> procedures,
                         Map<String, AlgolParser.SwitchDeclContext> switchDeclarations,
                         Map<String, Integer> procVarSlots) {
        this.source = source;
        this.packageName = packageName;
        this.className = className;
        this.exprTypes = exprTypes;
        this.procedures = procedures;
        this.switchDeclarations = switchDeclarations != null ? switchDeclarations : Map.of();
        this.procVarSlots = procVarSlots != null ? procVarSlots : Map.of();
        this.currentSymbolTable = symbolTable;
        this.currentLocalIndex  = localIndex;
        this.currentNumLocals   = numLocals;
        this.currentArrayBounds = arrayBounds;
        this.builtinGen = new BuiltinFunctionGenerator(exprTypes);
        this.builtinGen.setExprCodeGen(e -> generateExpr(e));
        this.procGen = new ProcedureGenerator(
            packageName, className,
            this::nextProcRefId,
            (name, content) -> procRefClassDefinitions.add(Map.entry(name, content)),
            this::lookupProcVarSlot,
            exprTypes,
            e -> generateExpr(e));
        this.procGen.setCurrentProcNameSupplier(() -> this.currentProcName);
        this.procGen.setProceduresSupplier(() -> this.procedures);
    }

    private int nextProcRefId() { return procRefCounter++; }

    private Integer lookupProcVarSlot(String name) {
        Integer idx = currentLocalIndex.get(name);
        if (idx == null && mainLocalIndex != null) idx = mainLocalIndex.get(name);
        if (idx == null) {
            Integer marked = procVarSlots.get(name);
            // A negative marker indicates the variable is a procedure variable but
            // does not have a dedicated local slot (stored in static field instead).
            if (marked != null && marked >= 0) {
                idx = marked;
            }
        }
        return idx;
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
        mainHadExecutableStatements = false;

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

        // Emit static fields for all procedure-typed variables (even in nested scopes) so
        // they can be referenced via getstatic/putstatic from generated call-by-name and
        // procedure-variable code.
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String varName = symEntry.getKey();
            String varType = symEntry.getValue();
            if (varType != null && varType.startsWith("procedure:")) {
                String desc = switch (varType.substring("procedure:".length())) {
                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                };
                classHeader.append(".field public static ")
                           .append(staticFieldName(varName, varType)).append(" ").append(desc).append("\n");
            }
        }

        // Add static Scanner field for input procedures (used for System.in reading)
        classHeader.append(".field public static __scanner Ljava/util/Scanner;\n");

        // Environment bridge fields for nested-procedure access to outer parameters.
        if (useEnvBridge()) for (Map.Entry<String, SymbolTableBuilder.ProcInfo> pe : procedures.entrySet()) {
            String pName = pe.getKey();
            if (!useEnvBridge(pName)) continue;
            SymbolTableBuilder.ProcInfo pInfo = pe.getValue();
            for (String p : pInfo.paramNames) {
                String pDesc;
                if (!pInfo.valueParams.contains(p)) {
                    // Call-by-name parameters are represented as Thunk instances.
                    pDesc = "Lgnb/perseus/compiler/Thunk;";
                } else {
                    String pType = getFormalBaseType(pInfo, p);
                    if ("real".equals(pType)) pDesc = "D";
                    else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                    else if (pType.startsWith("procedure:")) {
                        pDesc = switch (pType.substring("procedure:".length())) {
                            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                            default -> "Lgnb/perseus/compiler/VoidProcedure;";
                        };
                    } else {
                        pDesc = "I";
                    }
                }
                classHeader.append(".field public static ")
                           .append(envThunkFieldName(pName, p))
                           .append(" ").append(pDesc).append("\n");
            }
            if (procedureNeedsLocalBridge(pInfo)) for (Map.Entry<String, String> local : pInfo.localVars.entrySet()) {
                String localName = local.getKey();
                String localType = local.getValue();
                if (pInfo.ownVars.contains(localName) || localType == null || localType.endsWith("[]") || localType.startsWith("procedure:")) continue;
                classHeader.append(".field public static ")
                           .append(envThunkFieldName(pName, localName))
                           .append(" ").append(scalarTypeToJvmDesc(localType)).append("\n");
            }
            if (!"void".equals(pInfo.returnType)) {
                String rDesc = "real".equals(pInfo.returnType) ? "D" : "string".equals(pInfo.returnType) ? "Ljava/lang/String;" : "I";
                classHeader.append(".field public static ")
                           .append(envReturnFieldName(pName))
                           .append(" ").append(rDesc).append("\n");
            }
            classHeader.append(".field public static ")
                       .append(selfThunkFieldName(pName))
                       .append(" Lgnb/perseus/compiler/Thunk;\n");
        }

        classHeader.append("\n")
                   .append(".method public <init>()V\n")
                   .append(".limit stack 64\n") // TODO: calculate required stack
                   .append(".limit locals 64\n") // TODO: calculate required locals
                   .append("aload_0\n")
                   .append("invokespecial java/lang/Object/<init>()V\n")
                   .append("return\n")
                   .append(".end method\n\n");

        // Main method header
        mainCode.append(".method public static main([Ljava/lang/String;)V\n")
                .append(".limit stack 64\n") // TODO: calculate required stack
                .append(".limit locals 64\n"); // TODO: calculate required locals

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

        // Initialize procedure variables to self-referential ProcRef objects.
        // This ensures that a call to the procedure before any assignment uses the
        // declared procedure implementation (Algol's bindable procedure semantics).
        for (Map.Entry<String, String> symEntry : currentSymbolTable.entrySet()) {
            String varName = symEntry.getKey();
            String type = symEntry.getValue();
            if (type != null && type.startsWith("procedure:") && procedures.containsKey(varName)) {
                mainCode.append(generateProcedureReference(varName, procedures.get(varName)));
                mainCode.append("putstatic ")
                        .append(packageName).append("/").append(className)
                        .append("/").append(staticFieldName(varName, type)).append(" ")
                        .append(getProcedureInterfaceDescriptor(type)).append("\n");
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
        // If the program contains no top-level executable statements, implicitly
        // invoke the last zero-arg procedure defined (common in some Algol test cases).
        if (!mainHadExecutableStatements) {
            String entryProc = null;
            for (Map.Entry<String, SymbolTableBuilder.ProcInfo> e : procedures.entrySet()) {
                if (e.getValue().paramNames.isEmpty()) {
                    entryProc = e.getKey();
                }
            }
            if (entryProc != null) {
                activeOutput.append(generateUserProcedureInvocation(entryProc, List.of(), true));
            }
        }
        activeOutput.append("return\n").append(".end method\n");
    }

    // -------------------------------------------------------------------------
    // Procedure declaration enter/exit: switch output buffer and local context
    // -------------------------------------------------------------------------

    @Override
    public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        String procName = ctx.identifier().getText();
        SymbolTableBuilder.ProcInfo info = procedures.get(procName);

        // Detect procedure-variable declarations (procedure p; with no executable body).
        // In this case, the next statement is another procedure declaration, and we
        // should not generate a method for this symbol.
        boolean isProcVarDecl = ctx.statement() != null && ctx.statement().procedureDecl() != null;
        String stmtText = ctx.statement() != null ? ctx.statement().getText() : "<none>";
        skipProcedureDeclStack.push(isProcVarDecl);
        if (isProcVarDecl) {
            return;
        }

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
        savedEnvParamSlotsStack.push(currentEnvParamSlots);
        savedEnvLocalSlotsStack.push(currentEnvLocalSlots);
        savedEnvRetSaveSlotStack.push(currentEnvRetSaveSlot);
        savedNestedSelfThunkSlotsStack.push(currentNestedSelfThunkSlots);

        // Make the current scope the new "outer" scope
        mainSymbolTable   = currentSymbolTable;
        mainLocalIndex    = currentLocalIndex;
        mainNumLocals     = currentNumLocals;
        mainArrayBounds   = currentArrayBounds;

        // Build procedure-local context
        currentProcName       = procName;
        currentProcReturnType = info.returnType;
        currentEnvParamSlots  = new LinkedHashMap<>();
        currentEnvLocalSlots  = new LinkedHashMap<>();
        currentEnvRetSaveSlot = -1;
        currentNestedSelfThunkSlots = new LinkedHashMap<>();

        Map<String, String>  procST = new LinkedHashMap<>();
        Map<String, Integer> procLI = new LinkedHashMap<>();
        int nextSlot = 0;

        // Parameters occupy the first slots
        for (String paramName : info.paramNames) {
            String baseType = getFormalBaseType(info, paramName);
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
            if (info.ownVars.contains(varName)) {
                // own locals persist across re-entry, so represent them as class statics
                // rather than per-activation JVM locals.
                continue;
            }
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
                    return "Lgnb/perseus/compiler/Thunk;";
                }
                String type = getFormalBaseType(info, p);
                if ("real".equals(type)) return "D";
                if ("string".equals(type)) return "Ljava/lang/String;";
                if (type.startsWith("procedure:")) {
                    return switch (type.substring("procedure:".length())) {
                        case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                        case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                        case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                        default -> "Lgnb/perseus/compiler/VoidProcedure;";
                    };
                }
                // boolean and integer both treated as I
                return "I";
            })
            .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";

        activeOutput.append(".method public static ").append(procName)
                    .append("(").append(paramDesc).append(")").append(retDesc).append("\n")
                    .append(".limit stack 64\n") // TODO: calculate required stack
                    .append(".limit locals 64\n"); // TODO: calculate required locals

        // Publish parameters into static env fields so nested procedures can access them.
        // Save previous env-field values in extra locals so recursion restores outer activation state.
        if (useEnvBridge(procName)) for (String p : info.paramNames) {
            Integer pSlot = procLI.get(p);
            if (pSlot != null) {
                String pType = getFormalBaseType(info, p);
                if (info.valueParams.contains(p)) {
                    // Only value parameters use static env fields
                    if ("real".equals(pType)) {
                        int saveSlot = currentNumLocals;
                        currentNumLocals += 2;
                        currentEnvParamSlots.put(p, saveSlot);
                        activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" D\n");
                        emitStore("dstore", saveSlot);
                        activeOutput.append("dload ").append(pSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" D\n");
                    } else if ("string".equals(pType)) {
                        int saveSlot = currentNumLocals;
                        currentNumLocals += 1;
                        currentEnvParamSlots.put(p, saveSlot);
                        activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" Ljava/lang/String;\n");
                        emitStore("astore", saveSlot);
                        activeOutput.append("aload ").append(pSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" Ljava/lang/String;\n");
                    } else if (pType.startsWith("procedure:")) {
                        String pDesc = switch (pType.substring("procedure:".length())) {
                            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                            default -> "Lgnb/perseus/compiler/VoidProcedure;";
                        };
                        int saveSlot = currentNumLocals;
                        currentNumLocals += 1;
                        currentEnvParamSlots.put(p, saveSlot);
                        activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ").append(pDesc).append("\n");
                        emitStore("astore", saveSlot);
                        activeOutput.append("aload ").append(pSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ").append(pDesc).append("\n");
                    } else {
                        int saveSlot = currentNumLocals;
                        currentNumLocals += 1;
                        currentEnvParamSlots.put(p, saveSlot);
                        activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" I\n");
                        emitStore("istore", saveSlot);
                        activeOutput.append("iload ").append(pSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" I\n");
                    }
                } else {
                    // Call-by-name parameters are passed as Thunk objects; publish them for nested procs as well.
                    int saveSlot = currentNumLocals;
                    currentNumLocals += 1;
                    currentEnvParamSlots.put(p, saveSlot);
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envThunkFieldName(procName, p)).append(" Lgnb/perseus/compiler/Thunk;\n");
                    emitStore("astore", saveSlot);
                    activeOutput.append("aload ").append(pSlot).append("\n");
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envThunkFieldName(procName, p)).append(" Lgnb/perseus/compiler/Thunk;\n");
                }
            }
        }
        if (useEnvBridge(procName) && !"void".equals(info.returnType) && procRetvalSlot >= 0) {
            String rDesc = "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";
            currentEnvRetSaveSlot = currentNumLocals;
            currentNumLocals += "real".equals(info.returnType) ? 2 : 1;
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            if ("real".equals(info.returnType)) emitStore("dstore", currentEnvRetSaveSlot);
            else if ("string".equals(info.returnType)) emitStore("astore", currentEnvRetSaveSlot);
            else emitStore("istore", currentEnvRetSaveSlot);
        }
        if (useEnvBridge(procName) && procedureNeedsLocalBridge(info)) {
            for (Map.Entry<String, String> local : info.localVars.entrySet()) {
                String localName = local.getKey();
                String localType = local.getValue();
                if (info.ownVars.contains(localName) || localType == null || localType.endsWith("[]") || localType.startsWith("procedure:")) continue;
                int saveSlot = currentNumLocals;
                currentNumLocals += "real".equals(localType) ? 2 : 1;
                currentEnvLocalSlots.put(localName, saveSlot);
                activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                            .append("/").append(envThunkFieldName(procName, localName)).append(" ")
                            .append(scalarTypeToJvmDesc(localType)).append("\n");
                if ("real".equals(localType)) emitStore("dstore", saveSlot);
                else if ("string".equals(localType)) emitStore("astore", saveSlot);
                else emitStore("istore", saveSlot);
            }
        }
        if (useEnvBridge(procName) && info != null) {
            for (String nestedProcName : info.nestedProcedures) {
                int saveSlot = currentNumLocals;
                currentNumLocals += 1;
                currentNestedSelfThunkSlots.put(nestedProcName, saveSlot);
                activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                            .append("/").append(selfThunkFieldName(nestedProcName)).append(" Lgnb/perseus/compiler/Thunk;\n");
                emitStore("astore", saveSlot);
                activeOutput.append("aconst_null\n");
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(selfThunkFieldName(nestedProcName)).append(" Lgnb/perseus/compiler/Thunk;\n");
            }
        }
        // TODO: Once we properly calculate limits everywhere, replace Math.max(currentNumLocals, 64) with exact calculation.
        ensureLocalLimit(Math.max(currentNumLocals, 64));

        // Initialize local variables (not parameters) and the retval slot
        for (Map.Entry<String, Integer> e : procLI.entrySet()) {
            if (info.paramNames.contains(e.getKey())) continue; // params set by caller
            if (e.getKey().equals(procName)) continue; // self-ref slot initialized below
            String varType = procST.get(e.getKey());
            int slot = e.getValue();
            if ("real".equals(varType)) {
                activeOutput.append("dconst_0\n"); emitStore("dstore", slot);
            } else if ("string".equals(varType)) {
                activeOutput.append("ldc \"\"\n"); emitStore("astore", slot);
            } else if (varType != null && varType.startsWith("procedure:")) {
                activeOutput.append("aconst_null\n"); emitStore("astore", slot);
            } else {
                activeOutput.append("iconst_0\n"); emitStore("istore", slot);
            }
        }
        // Initialize self-reference slot to null
        if (selfRefSlot >= 0) {
            activeOutput.append("aconst_null\n"); emitStore("astore", selfRefSlot);
        }
        // Initialize retval slot (only for typed functions)
        if (procRetvalSlot >= 0) {
            if ("real".equals(info.returnType)) {
                activeOutput.append("dconst_0\n"); emitStore("dstore", procRetvalSlot);
            } else {
                activeOutput.append("iconst_0\n"); emitStore("istore", procRetvalSlot);
            }
            if (useEnvBridge(procName)) {
                String rDesc = "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";
                if ("real".equals(info.returnType)) {
                    activeOutput.append("dload ").append(procRetvalSlot).append("\n");
                } else if ("string".equals(info.returnType)) {
                    activeOutput.append("aload ").append(procRetvalSlot).append("\n");
                } else {
                    activeOutput.append("iload ").append(procRetvalSlot).append("\n");
                }
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            }
        }
        if (useEnvBridge(procName) && procedureNeedsLocalBridge(info)) {
            for (Map.Entry<String, String> local : info.localVars.entrySet()) {
                String localName = local.getKey();
                String localType = local.getValue();
                if (info.ownVars.contains(localName) || localType == null || localType.endsWith("[]") || localType.startsWith("procedure:")) continue;
                Integer slot = procLI.get(localName);
                if (slot == null) continue;
                if ("real".equals(localType)) {
                    activeOutput.append("dload ").append(slot).append("\n");
                } else if ("string".equals(localType)) {
                    activeOutput.append("aload ").append(slot).append("\n");
                } else {
                    activeOutput.append("iload ").append(slot).append("\n");
                }
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(envThunkFieldName(procName, localName)).append(" ")
                            .append(scalarTypeToJvmDesc(localType)).append("\n");
            }
        }
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        boolean skip = skipProcedureDeclStack.pop();
        if (skip) {
            return;
        }

        String procName = ctx.identifier().getText();
        SymbolTableBuilder.ProcInfo info = procedures.get(procName);

        // Restore env fields for this activation before returning.
        if (useEnvBridge(procName) && info != null) {
            for (String p : info.paramNames) {
                Integer saveSlot = currentEnvParamSlots.get(p);
                if (saveSlot == null) continue;
                String pType = getFormalBaseType(info, p);
                if (info.valueParams.contains(p)) {
                    if ("real".equals(pType)) {
                        activeOutput.append("dload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" D\n");
                    } else if ("string".equals(pType)) {
                        activeOutput.append("aload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" Ljava/lang/String;\n");
                    } else if (pType.startsWith("procedure:")) {
                        String pDesc = switch (pType.substring("procedure:".length())) {
                            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                            default -> "Lgnb/perseus/compiler/VoidProcedure;";
                        };
                        activeOutput.append("aload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ").append(pDesc).append("\n");
                    } else {
                        activeOutput.append("iload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" I\n");
                    }
                } else {
                    // Restore call-by-name thunk parameter env field
                    activeOutput.append("aload ").append(saveSlot).append("\n");
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envThunkFieldName(procName, p)).append(" Lgnb/perseus/compiler/Thunk;\n");
                }
            }
        }

        if (useEnvBridge(procName) && info != null && !"void".equals(info.returnType) && procRetvalSlot >= 0) {
            String rDesc = "real".equals(info.returnType) ? "D" : "string".equals(info.returnType) ? "Ljava/lang/String;" : "I";
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            if ("real".equals(info.returnType)) emitStore("dstore", procRetvalSlot);
            else if ("string".equals(info.returnType)) emitStore("astore", procRetvalSlot);
            else emitStore("istore", procRetvalSlot);

            if (currentEnvRetSaveSlot >= 0) {
                if ("real".equals(info.returnType)) {
                    activeOutput.append("dload ").append(currentEnvRetSaveSlot).append("\n");
                } else if ("string".equals(info.returnType)) {
                    activeOutput.append("aload ").append(currentEnvRetSaveSlot).append("\n");
                } else {
                    activeOutput.append("iload ").append(currentEnvRetSaveSlot).append("\n");
                }
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            }
        }
        if (useEnvBridge(procName) && procedureNeedsLocalBridge(info)) {
            for (Map.Entry<String, String> local : info.localVars.entrySet()) {
                String localName = local.getKey();
                String localType = local.getValue();
                Integer saveSlot = currentEnvLocalSlots.get(localName);
                if (saveSlot == null || info.ownVars.contains(localName) || localType == null || localType.endsWith("[]") || localType.startsWith("procedure:")) continue;
                if ("real".equals(localType)) {
                    activeOutput.append("dload ").append(saveSlot).append("\n");
                } else if ("string".equals(localType)) {
                    activeOutput.append("aload ").append(saveSlot).append("\n");
                } else {
                    activeOutput.append("iload ").append(saveSlot).append("\n");
                }
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(envThunkFieldName(procName, localName)).append(" ")
                            .append(scalarTypeToJvmDesc(localType)).append("\n");
            }
        }
        if (useEnvBridge(procName) && info != null) {
            for (String nestedProcName : info.nestedProcedures) {
                Integer saveSlot = currentNestedSelfThunkSlots.get(nestedProcName);
                if (saveSlot == null) continue;
                activeOutput.append("aload ").append(saveSlot).append("\n");
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(selfThunkFieldName(nestedProcName)).append(" Lgnb/perseus/compiler/Thunk;\n");
            }
        }
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
        currentEnvParamSlots  = savedEnvParamSlotsStack.pop();
        currentEnvLocalSlots  = savedEnvLocalSlotsStack.pop();
        currentEnvRetSaveSlot = savedEnvRetSaveSlotStack.pop();
        currentNestedSelfThunkSlots = savedNestedSelfThunkSlotsStack.pop();
    }

    // -------------------------------------------------------------------------
    // Assignments
    // -------------------------------------------------------------------------

    @Override
    public void exitAssignment(AlgolParser.AssignmentContext ctx) {
        if (currentProcName == null) mainHadExecutableStatements = true;
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

            // String scalar character mutation: s[i] := replacement
            // Rebuilds the string using StringBuilder: prefix + replacement + suffix
            if ("string".equals(elemType)) {
                activeOutput
                    .append("new java/lang/StringBuilder\n")
                    .append("dup\n")
                    .append("invokespecial java/lang/StringBuilder/<init>()V\n")
                    // prefix: s.substring(0, i-1)
                    .append(generateLoadVar(arrName))
                    .append("iconst_0\n")
                    .append(generateExpr(lv.expr()))
                    .append("iconst_1\n").append("isub\n")
                    .append("invokevirtual java/lang/String/substring(II)Ljava/lang/String;\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    // replacement (RHS expression must be a string)
                    .append(generateExpr(ctx.expr()))
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    // suffix: s.substring(i)
                    .append(generateLoadVar(arrName))
                    .append(generateExpr(lv.expr()))
                    .append("invokevirtual java/lang/String/substring(I)Ljava/lang/String;\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;\n");
                // store result back to s (local or static)
                Integer strIdx = currentLocalIndex.get(arrName);
                if (strIdx != null) {
                    activeOutput.append("astore ").append(strIdx).append("\n");
                } else {
                    String sType = currentSymbolTable.getOrDefault(arrName,
                            mainSymbolTable != null ? mainSymbolTable.get(arrName) : null);
                    if (sType != null) {
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(arrName).append(" Ljava/lang/String;\n");
                    } else {
                        activeOutput.append("; ERROR: undefined variable ").append(arrName).append("\n");
                    }
                }
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
        boolean rhsIsProcedureRef = isProcedureReferenceExpr(ctx.expr());

        // Determine storage type: real if any destination is real, string if any destination is string
        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String returnTargetType = getProcedureReturnTargetType(lvName, rhsIsProcedureRef);
            if (returnTargetType != null) return "real".equals(returnTargetType);
            String vt = currentSymbolTable.get(lvName);
            if (vt == null && mainSymbolTable != null) {
                vt = mainSymbolTable.get(lvName);
            }
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            return "real".equals(vt);
        });
        boolean anyString = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String returnTargetType = getProcedureReturnTargetType(lvName, rhsIsProcedureRef);
            if (returnTargetType != null) return "string".equals(returnTargetType);
            String vt = currentSymbolTable.get(lvName);
            if (vt == null && mainSymbolTable != null) {
                vt = mainSymbolTable.get(lvName);
            }
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            return "string".equals(vt);
        });
        // A typed procedure name can denote either a procedure reference or the
        // procedure's implicit return variable. When the RHS is a procedure
        // reference, keep the existing procedure-variable behavior. Otherwise,
        // treat assignments to the current/enclosing typed procedure names as
        // return-value writes.
        boolean anyProcedure = lvalues.stream()
            .anyMatch(lv -> isProcedureVariableTarget(lv.identifier().getText(), rhsIsProcedureRef));
        String storeType = anyProcedure ? "procedure" : anyReal ? "real" : anyString ? "string" : "integer";

        // Generate expression and widen if needed
        activeOutput.append(generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            activeOutput.append("i2d\n");
        }

        // If multiple destinations, store the computed value into a temp slot so we can
        // reload it safely (avoids dup/dup2 stack-splitting issues).
        boolean useTemp = lvalues.size() > 1;
        int tempSlot = -1;
        if (useTemp) {
            tempSlot = allocateNewLocal("tmp");
            if ("real".equals(storeType)) {
                emitStore("dstore", tempSlot);
            } else if ("string".equals(storeType) || "procedure".equals(storeType)) {
                emitStore("astore", tempSlot);
            } else {
                emitStore("istore", tempSlot);
            }
        }

        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();
            if (useTemp) {
                if ("real".equals(storeType)) {
                    activeOutput.append("dload ").append(tempSlot).append("\n");
                } else if ("string".equals(storeType) || "procedure".equals(storeType)) {
                    activeOutput.append("aload ").append(tempSlot).append("\n");
                } else {
                    activeOutput.append("iload ").append(tempSlot).append("\n");
                }
            }

            // Procedure return value assignment - but ONLY if this procedure actually has a return value slot.
            // For void procedures (including those used as procedure variables), assignment to the
            // procedure name should be treated as a procedure-variable assignment, not a return value.
            if (name.equals(currentProcName) && isProcedureReturnTarget(name, rhsIsProcedureRef)) {
                if ("real".equals(currentProcReturnType)) {
                    emitStore("dstore", procRetvalSlot);
                    if (useEnvBridge(currentProcName)) {
                        activeOutput.append("dload ").append(procRetvalSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envReturnFieldName(currentProcName)).append(" D\n");
                    }
                } else if ("string".equals(currentProcReturnType)) {
                    emitStore("astore", procRetvalSlot);
                    if (useEnvBridge(currentProcName)) {
                        activeOutput.append("aload ").append(procRetvalSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envReturnFieldName(currentProcName)).append(" Ljava/lang/String;\n");
                    }
                } else {
                    emitStore("istore", procRetvalSlot);
                    if (useEnvBridge(currentProcName)) {
                        activeOutput.append("iload ").append(procRetvalSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envReturnFieldName(currentProcName)).append(" I\n");
                    }
                }
                continue;
            }

            if (isEnclosingProcedureReturnTarget(name, rhsIsProcedureRef)) {
                SymbolTableBuilder.ProcInfo outerProc = getEnclosingProcedureInfo(name);
                if (outerProc == null) {
                    activeOutput.append("; ERROR: missing enclosing procedure return target ").append(name).append("\n");
                    continue;
                }
                if ("real".equals(outerProc.returnType)) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" D\n");
                } else if ("string".equals(outerProc.returnType)) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" Ljava/lang/String;\n");
                } else {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" I\n");
                }
                continue;
            }

            Integer idx = currentLocalIndex.get(name);
            if (idx == null && mainLocalIndex != null) idx = mainLocalIndex.get(name);
            String varType = currentSymbolTable.get(name);
            if (varType == null && mainSymbolTable != null) varType = mainSymbolTable.get(name);
            System.out.println("DEBUG: assignment target '" + name + "' resolvedIdx=" + idx + " varType=" + varType + " currentProc=" + currentProcName);
            if (varType == null && mainSymbolTable != null) {
                varType = mainSymbolTable.get(name);
            }
            boolean isCallByNameParam = false;
            if (currentProcName != null) {
                SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                if (currInfo != null && currInfo.paramNames.contains(name) && !currInfo.valueParams.contains(name)) {
                    isCallByNameParam = true;
                }
            }
            boolean isThunk = (varType != null && varType.startsWith("thunk:")) || isCallByNameParam;
            boolean isProcVar = varType != null && varType.startsWith("procedure:");
                // If this variable resolves to an outer-scope local (present in mainLocalIndex
                // but not in currentLocalIndex), access it via class static field instead.
                if (idx != null && !currentLocalIndex.containsKey(name)) {
                    if (varType != null && varType.startsWith("procedure:")) {
                        String pdesc = switch (varType.substring("procedure:".length())) {
                            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                            default -> "Lgnb/perseus/compiler/VoidProcedure;";
                        };
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        String targetName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                        if (targetName.equals(name) && varType != null && varType.startsWith("procedure:")) {
                            targetName = staticFieldName(name, varType);
                        }
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(pdesc).append("\n");
                    } else if (varType != null && varType.endsWith("[]")) {
                        String ad = arrayTypeToJvmDesc(varType);
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        String targetName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(ad).append("\n");
                    } else {
                        String sd = scalarTypeToJvmDesc(varType != null ? varType : "integer");
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        String targetName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(sd).append("\n");
                    }
                    continue;
                }
            if (idx == null && !isThunk && !isProcVar) {
                // Check if this is a static scalar
                if (varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:") && !varType.startsWith("thunk:")) {
                    // Static scalar: emit putstatic (use env bridge name when available)
                    String jvmDesc = scalarTypeToJvmDesc(varType);
                    String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                    String targetName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(targetName).append(" ").append(jvmDesc).append("\n");
                    continue;
                }
                // Check main symbol table for outer scope static scalars
                if (mainSymbolTable != null) {
                    String mainType = mainSymbolTable.get(name);
                    if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                        // Static scalar from outer scope: emit putstatic (use env bridge name when available)
                        String jvmDesc = scalarTypeToJvmDesc(mainType);
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        String targetName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(jvmDesc).append("\n");
                        continue;
                    }
                }
                activeOutput.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }


            if (isThunk) {
                if ("thunk:deferred".equals(varType)) {
                    // Preserve original actual type (integer vs real) when setting deferred call-by-name args.
                    int deferredTempSlot = currentNumLocals;
                    currentNumLocals += 2;
                    activeOutput.append("dstore ").append(deferredTempSlot).append("\n");

                    if (idx != null) {
                        activeOutput.append("aload ").append(idx).append("\n");
                    } else {
                        activeOutput.append(generateLoadThunkRef(name));
                    }
                    activeOutput.append("dup\n");
                    activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                    activeOutput.append("dup\n");
                    activeOutput.append("instanceof java/lang/Double\n");
                    String realLabel = generateUniqueLabel("deferred_real");
                    String endLabel = generateUniqueLabel("deferred_end");
                    activeOutput.append("ifeq ").append(realLabel).append("\n");
                    // real path
                    activeOutput.append("pop\n");
                    activeOutput.append("dload ").append(deferredTempSlot).append("\n");
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    activeOutput.append("goto ").append(endLabel).append("\n");
                    activeOutput.append(realLabel).append(":\n");
                    activeOutput.append("pop\n");
                    activeOutput.append("dload ").append(deferredTempSlot).append("\n");
                    activeOutput.append("d2i\n");
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    activeOutput.append(endLabel).append(":\n");
                    activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
                    continue;
                }

                // assignment to a name parameter: call thunk.set(boxedValue)
                // stack has the primitive/reference value; box it, then swap the thunk ref in
                if ("real".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else if (!"string".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                // push thunk ref (local if available, otherwise via env bridge), swap so order is: thunk, boxed_value
                if (idx != null) {
                    activeOutput.append("aload ").append(idx).append("\n");
                } else {
                    activeOutput.append(generateLoadThunkRef(name));
                }
                activeOutput.append("swap\n");
                activeOutput.append("invokeinterface ")
                            .append("gnb/perseus/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
                continue;
            }

            // normal local variable storage
            if ("integer".equals(varType) || "boolean".equals(varType)) {
                emitStore("istore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("iload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" I\n");
                    }
                }
            } else if ("real".equals(varType)) {
                emitStore("dstore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("dload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" D\n");
                    }
                }
            } else if ("string".equals(varType)) {
                emitStore("astore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("aload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" Ljava/lang/String;\n");
                    }
                }
            } else if (isProcVar) {
                // Procedure variables are stored in a static field so they are shared across activations.
                String pdesc = switch (varType.substring("procedure:".length())) {
                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                };
                boolean storeToLocal = currentLocalIndex.containsKey(name);

                if (storeToLocal) {
                    // Store in local slot first so we can also store the same value to the static field.
                    emitStore("astore", idx);
                    // Reload for storing into the static field.
                    activeOutput.append("aload ").append(idx).append("\n");
                }

                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(staticFieldName(name, varType)).append(" ").append(pdesc).append("\n");
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
        if (currentProcName == null) mainHadExecutableStatements = true;
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
            // Check if it's a call through a procedure variable (local or outer scope).
            String varType = currentSymbolTable.get(name);
            // Fall back to main scope if not found in current scope
            if (varType == null && mainSymbolTable != null) varType = mainSymbolTable.get(name);
            SymbolTableBuilder.ProcInfo declaredProc = procedures.get(name);
            boolean preferDirectProcedureCall = declaredProc != null && !"void".equals(declaredProc.returnType);
            boolean isProcVar = varType != null && varType.startsWith("procedure:")
                    && (currentLocalIndex.containsKey(name) || procVarSlots.containsKey(name));
            if (isProcVar && !preferDirectProcedureCall) {
                // Call through a procedure variable (local slot or static field)
                activeOutput.append(generateProcedureVariableCall(name, varType, args));
                // Procedure-variable calls return a value (unless void); in statement position,
                // the return value should be discarded to keep the stack balanced.
                String procRet = varType.substring("procedure:".length());
                if (!"void".equals(procRet)) {
                    if ("real".equals(procRet)) {
                        activeOutput.append("pop2\n");
                    } else {
                        activeOutput.append("pop\n");
                    }
                }
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
    public void exitSwitchDecl(AlgolParser.SwitchDeclContext ctx) {
        // Switch declarations are metadata for designational goto codegen.
    }

    @Override
    public void exitGotoStatement(AlgolParser.GotoStatementContext ctx) {
        emitGotoDesignationalExpr(ctx.designationalExpr());
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
                    activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
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
                    activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
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
        activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
    }

    /** Load the current value out of a thunk (unboxed onto the JVM stack). */
    private void appendLoadThunkValue(int varIndex, String baseType) {
        activeOutput.append("aload ").append(varIndex).append("\n");
        activeOutput.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
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

    private void emitGotoDesignationalExpr(AlgolParser.DesignationalExprContext ctx) {
        if (ctx instanceof AlgolParser.DirectDesignationalExprContext simpleCtx) {
            emitGotoSimpleDesignationalExpr(simpleCtx.simpleDesignationalExpr());
            return;
        }
        if (ctx instanceof AlgolParser.IfDesignationalExprContext ifCtx) {
            String falseLabel = generateUniqueLabel("switch_else");
            activeOutput.append(generateExpr(ifCtx.expr()));
            activeOutput.append("ifeq ").append(falseLabel).append("\n");
            emitGotoSimpleDesignationalExpr(ifCtx.simpleDesignationalExpr());
            activeOutput.append(falseLabel).append(":\n");
            emitGotoDesignationalExpr(ifCtx.designationalExpr());
            return;
        }
        activeOutput.append("; ERROR: unsupported designational expression\n");
    }

    private void emitGotoSimpleDesignationalExpr(AlgolParser.SimpleDesignationalExprContext ctx) {
        if (ctx instanceof AlgolParser.LabelDesignatorExprContext labelCtx) {
            activeOutput.append("goto ").append(labelCtx.identifier().getText()).append("\n");
            return;
        }
        if (ctx instanceof AlgolParser.ParenDesignatorExprContext parenCtx) {
            emitGotoDesignationalExpr(parenCtx.designationalExpr());
            return;
        }
        if (ctx instanceof AlgolParser.SwitchDesignatorExprContext switchCtx) {
            emitGotoSwitchDesignator(switchCtx.identifier().getText(), switchCtx.expr());
            return;
        }
        activeOutput.append("; ERROR: unsupported simple designational expression\n");
    }

    private void emitGotoSwitchDesignator(String switchName, AlgolParser.ExprContext indexExpr) {
        AlgolParser.SwitchDeclContext switchDecl = switchDeclarations.get(switchName);
        if (switchDecl == null) {
            activeOutput.append("; ERROR: unknown switch ").append(switchName).append("\n");
            return;
        }

        String indexType = exprTypes.getOrDefault(indexExpr, "integer");
        int indexSlot = allocateNewLocal("switchIndex");
        activeOutput.append(generateExpr(indexExpr));
        if ("real".equals(indexType)) {
            activeOutput.append("d2i\n");
        }
        emitStore("istore", indexSlot);

        for (int caseIndex = 0; caseIndex < switchDecl.designationalExpr().size(); caseIndex++) {
            String nextLabel = generateUniqueLabel("switch_next");
            activeOutput.append("iload ").append(indexSlot).append("\n");
            activeOutput.append("ldc ").append(caseIndex + 1).append("\n");
            activeOutput.append("if_icmpne ").append(nextLabel).append("\n");
            emitGotoDesignationalExpr(switchDecl.designationalExpr(caseIndex));
            activeOutput.append(nextLabel).append(":\n");
        }

        emitUndefinedSwitchTrap(switchName);
    }

    private void emitUndefinedSwitchTrap(String switchName) {
        activeOutput.append("new java/lang/RuntimeException\n");
        activeOutput.append("dup\n");
        activeOutput.append("ldc \"Undefined switch designator: ").append(switchName).append("\"\n");
        activeOutput.append("invokespecial java/lang/RuntimeException/<init>(Ljava/lang/String;)V\n");
        activeOutput.append("athrow\n");
    }

    /** Returns the JVM array descriptor for a Perseus array type. */
    
    // ---------- helpers for thunk / variable box support ----------

    /**
     * Allocate a fresh local variable slot with a generated name hint
     * and update locals limit accordingly.
     */
    private int allocateNewLocal(String hint) {
        int slot = currentNumLocals;
        String name = hint + slot;
        currentLocalIndex.put(name, slot);
        // Reserve two slots for real (double) or boxed values to avoid slot overlap
        int incr = (hint != null && (hint.contains("box") || hint.contains("real"))) ? 2 : 1;
        currentNumLocals += incr;
        // Debug trace to help diagnose locals-limit updates
        System.out.println("DEBUG: allocateNewLocal called in proc=" + currentProcName + " hint=" + hint + " assigned=" + slot + " newLimit=" + currentNumLocals);
        if (currentNumLocals > 64) {
            throw new IllegalStateException("Exceeded hardcoded .limit locals 64: " + currentNumLocals + " locals allocated in " + currentProcName);
        }
        if (activeOutput != null) {
            int li = activeOutput.lastIndexOf(".limit locals ");
            System.out.println("DEBUG: activeOutput has .limit locals at index " + li);
        } else {
            System.out.println("DEBUG: activeOutput is null; procBufferStack.size=" + procBufferStack.size());
        }
        // TODO: Once we properly calculate limits everywhere, replace Math.max(currentNumLocals, 64) with exact calculation.
        ensureLocalLimit(Math.max(currentNumLocals, 64));
        return slot;
    }

    /**
     * Ensure that the current method's .limit locals directive is at least the
     * given value.  Scans the activeOutput or classHeader/procBufferStack for the
     * directive and updates it.
     */
    private void ensureLocalLimit(int required) {
        // Prefer updating the active output buffer's last .limit locals occurrence.
        StringBuilder buf = activeOutput != null ? activeOutput : (procBufferStack.isEmpty() ? mainCode : procBufferStack.peek());
        String search = ".limit locals ";
        int idx = buf.lastIndexOf(search);
        if (idx >= 0) {
            int end = buf.indexOf("\n", idx);
            if (end > idx) {
                buf.replace(idx + search.length(), end, Integer.toString(required));
                return;
            }
        }
        // Fallback: try proc buffer top
        if (!procBufferStack.isEmpty()) {
            StringBuilder alt = procBufferStack.peek();
            int altIdx = alt.lastIndexOf(search);
            if (altIdx >= 0) {
                int end = alt.indexOf("\n", altIdx);
                if (end > altIdx) {
                    alt.replace(altIdx + search.length(), end, Integer.toString(required));
                    return;
                }
            }
        }
        // Final fallback: update mainCode
        int mainIdx = mainCode.lastIndexOf(search);
        if (mainIdx >= 0) {
            int end = mainCode.indexOf("\n", mainIdx);
            if (end > mainIdx) {
                mainCode.replace(mainIdx + search.length(), end, Integer.toString(required));
            }
        }
    }

    /** Append a store instruction to the given buffer, logging if the slot is outside currentNumLocals. */
    private void emitStore(StringBuilder target, String instr, int slot) {
        if (target == null) target = activeOutput;
        System.out.println("DEBUG: emitStore proc=" + currentProcName + " instr=" + instr + " slot=" + slot + " currentNumLocals=" + currentNumLocals);
        target.append(instr).append(" ").append(slot).append("\n");
    }

    private void emitStore(String instr, int slot) {
        emitStore(activeOutput, instr, slot);
    }

    /**
     * Collect all simple variable names occurring in an expression tree.
     */
    private Set<String> collectVarNames(ExprContext ctx) {
        Set<String> names = new LinkedHashSet<>();
        if (ctx instanceof AlgolParser.VarExprContext ve) {
            String varName = ve.identifier().getText();
            // If the expression refers to the current procedure (recursive call), it should
            // be treated as a call rather than a captured variable.
            if (varName.equals(currentProcName) && procedures.containsKey(varName)) {
                return names;
            }
            names.add(varName);
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
        } else if (ctx instanceof AlgolParser.OrExprContext oe) {
            names.addAll(collectVarNames(oe.expr(0)));
            names.addAll(collectVarNames(oe.expr(1)));
        } else if (ctx instanceof AlgolParser.NotExprContext ne) {
            names.addAll(collectVarNames(ne.expr()));
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
        if (idx == null && mainLocalIndex != null) {
            idx = mainLocalIndex.get(name);
        }
        if (type == null && mainSymbolTable != null) {
            type = mainSymbolTable.get(name);
        }
        // If variable is not declared locally, check whether it is an env-bridge parameter
        // of an enclosing procedure (nested scope access).  In that case we can load it
        // from the corresponding static __env_<outer>_<name> field.
        if (type == null) {
            for (String outerProc : savedProcNameStack) {
                if (outerProc == null) continue;
                SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                if (outerInfo == null) continue;
                if (!useEnvBridge(outerProc)) continue;
                if (!outerInfo.paramNames.contains(name)) continue;

                String baseType = getFormalBaseType(outerInfo, name);
                if (!outerInfo.valueParams.contains(name)) {
                    // call-by-name parameter (thunk)
                    type = "thunk:" + baseType;
                } else {
                    type = baseType;
                }
                // Force idx to null so we load from static env field below.
                idx = null;
                break;
            }
        }
        if (type != null && type.startsWith("thunk:")) {
            String baseType = type.substring("thunk:".length());
            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadThunkRef(name));
            sb.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
            if ("real".equals(baseType)) {
                sb.append("checkcast java/lang/Double\n");
                sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
            } else if ("deferred".equals(baseType)) {
                return sb.toString();
            } else if ("string".equals(baseType)) {
                sb.append("checkcast java/lang/String\n");
            } else {
                sb.append("checkcast java/lang/Integer\n");
                sb.append("invokevirtual java/lang/Integer/intValue()I\n");
            }
            return sb.toString();
        }
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
        // If the idx came from an outer scope (mainLocalIndex) and we're not generating
        // code for that outer method, access via static field rather than local slot.
        if (!currentLocalIndex.containsKey(name)) {
            if (type != null && type.startsWith("procedure:")) {
                String desc = switch (type.substring("procedure:".length())) {
                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                };
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String fieldName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + desc + "\n";
            }
            if (type != null && !type.endsWith("[]") && !type.startsWith("thunk:")) {
                String jvmDesc = scalarTypeToJvmDesc(type);
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String fieldName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
            }
            if (type != null && type.endsWith("[]")) {
                String jvmDesc = arrayTypeToJvmDesc(type);
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String fieldName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
            }
        }
        if (type != null && type.startsWith("thunk:")) {
            // thunk formal inside caller? not expected here
            type = type.substring("thunk:".length());
        }
        if (useEnvBridge(currentProcName) && currentProcName != null) {
            SymbolTableBuilder.ProcInfo cp = procedures.get(currentProcName);
            if (cp != null && cp.paramNames.contains(name) && cp.valueParams.contains(name)) {
                String pType = getFormalBaseType(cp, name);
                String desc;
                if ("real".equals(pType)) desc = "D";
                else if ("string".equals(pType)) desc = "Ljava/lang/String;";
                else if (pType.startsWith("procedure:")) {
                    desc = switch (pType.substring("procedure:".length())) {
                        case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                        case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                        case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                        default -> "Lgnb/perseus/compiler/VoidProcedure;";
                    };
                } else desc = "I";
                return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentProcName, name) + " " + desc + "\n";
            }
        }
        if (isCurrentProcedureBridgedLocal(name)) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentProcName, name)
                + " " + scalarTypeToJvmDesc(type) + "\n";
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
        cls.append(".implements gnb/perseus/compiler/Thunk\n\n");

        // When a call-by-name actual is a procedure identifier, capture the defining
        // outer activation environment in thunk instance fields so recursive re-entry
        // uses closure-like state instead of whichever static env is current at get().
        if (actual instanceof AlgolParser.VarExprContext ve
                && procedures.containsKey(ve.identifier().getText())
                && (currentSymbolTable == null
                    || !currentSymbolTable.containsKey(ve.identifier().getText())
                    || ve.identifier().getText().equals(currentProcName))) {
            String pName = ve.identifier().getText();
            String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
            boolean captureOuterEnv = currentProcName != null && currentProcName.equals(pName)
                && outerProc != null && useEnvBridge(outerProc);

            if (captureOuterEnv) {
                SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);

                if (outerInfo != null) {
                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }
                        cls.append(".field public cap_").append(p).append(" ").append(pDesc).append("\n");
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        String rDesc = "real".equals(outerInfo.returnType) ? "D"
                            : "string".equals(outerInfo.returnType) ? "Ljava/lang/String;" : "I";
                        cls.append(".field public cap_ret ").append(rDesc).append("\n");
                    }
                    cls.append("\n");

                    cls.append(".method public <init>()V\n");
                    cls.append(".limit stack 64\n"); // TODO: calculate required stack
                    cls.append(".limit locals 64\n"); // TODO: calculate required locals
                    cls.append("aload_0\n");
                    cls.append("invokespecial java/lang/Object/<init>()V\n");
                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }

                        cls.append("aload_0\n");
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envThunkFieldName(outerProc, p)).append(" ").append(pDesc).append("\n");

                        // For primitive env fields (int/double) we can simply capture the value.
                        // For reference env fields we keep existing null-safe default thunk logic.
                        if ("I".equals(pDesc) || "D".equals(pDesc)) {
                            cls.append("putfield ").append(internalName).append("/cap_").append(p).append(" ").append(pDesc).append("\n");
                        } else {
                            cls.append("dup\n");
                            cls.append("ifnonnull cap_nonnull_").append(p).append("\n");
                            cls.append("pop\n");
                            cls.append("new gnb/perseus/compiler/DefaultThunk\n");
                            cls.append("dup\n");
                            cls.append("invokespecial gnb/perseus/compiler/DefaultThunk/<init>()V\n");
                            cls.append("cap_nonnull_").append(p).append(":\n");
                            cls.append("putfield ").append(internalName).append("/cap_").append(p).append(" ").append(pDesc).append("\n");
                        }
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        String rDesc = "real".equals(outerInfo.returnType) ? "D"
                            : "string".equals(outerInfo.returnType) ? "Ljava/lang/String;" : "I";
                        cls.append("aload_0\n");
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(rDesc).append("\n");
                        cls.append("putfield ").append(internalName).append("/cap_ret ").append(rDesc).append("\n");
                    }
                    cls.append("return\n");
                    cls.append(".end method\n\n");

                    cls.append(".method public get()Ljava/lang/Object;\n");
                    cls.append(".limit stack 64\n"); // TODO: calculate required stack
                    cls.append(".limit locals 64\n"); // TODO: calculate required locals
                    int localSlot = 1;
                    Map<String, Integer> savedSlot = new LinkedHashMap<>();
                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }
                        savedSlot.put(p, localSlot);
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envThunkFieldName(outerProc, p)).append(" ").append(pDesc).append("\n");
                        if ("D".equals(pDesc)) {
                            cls.append("dstore ").append(localSlot).append("\n");
                            localSlot += 2;
                        } else if ("I".equals(pDesc)) {
                            cls.append("istore ").append(localSlot).append("\n");
                            localSlot += 1;
                        } else {
                            cls.append("astore ").append(localSlot).append("\n");
                            localSlot += 1;
                        }
                    }
                    int retSaveSlot = -1;
                    String retDesc = null;
                    if (!"void".equals(outerInfo.returnType)) {
                        retDesc = "real".equals(outerInfo.returnType) ? "D"
                            : "string".equals(outerInfo.returnType) ? "Ljava/lang/String;" : "I";
                        retSaveSlot = localSlot;
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(retDesc).append("\n");
                        if ("D".equals(retDesc)) {
                            cls.append("dstore ").append(retSaveSlot).append("\n");
                            localSlot += 2;
                        } else if ("I".equals(retDesc)) {
                            cls.append("istore ").append(retSaveSlot).append("\n");
                            localSlot += 1;
                        } else {
                            cls.append("astore ").append(retSaveSlot).append("\n");
                            localSlot += 1;
                        }
                    }

                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }
                        cls.append("aload_0\n");
                        cls.append("getfield ").append(internalName).append("/cap_").append(p).append(" ").append(pDesc).append("\n");
                        cls.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envThunkFieldName(outerProc, p)).append(" ").append(pDesc).append("\n");
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        cls.append("aload_0\n");
                        cls.append("getfield ").append(internalName).append("/cap_ret ").append(retDesc).append("\n");
                        cls.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(retDesc).append("\n");
                    }

                    SymbolTableBuilder.ProcInfo pInfo = procedures.get(pName);
                    String pRet = pInfo != null ? pInfo.returnType : "integer";
                    String pRetDesc = "real".equals(pRet) ? "D" : "string".equals(pRet) ? "Ljava/lang/String;" : "I";
                    int selfSaveSlot = localSlot;
                    cls.append("getstatic ").append(packageName).append("/").append(className)
                       .append("/").append(selfThunkFieldName(pName)).append(" Lgnb/perseus/compiler/Thunk;\n");
                    cls.append("astore ").append(selfSaveSlot).append("\n");
                    localSlot += 1;
                    cls.append("aload_0\n");
                    cls.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(selfThunkFieldName(pName)).append(" Lgnb/perseus/compiler/Thunk;\n");
                    int callRetSlot = localSlot;
                    cls.append("invokestatic ").append(packageName).append("/").append(className)
                       .append("/").append(pName).append("()").append(pRetDesc).append("\n");
                    if ("D".equals(pRetDesc)) {
                        cls.append("dstore ").append(callRetSlot).append("\n");
                        localSlot += 2;
                    } else if ("I".equals(pRetDesc)) {
                        cls.append("istore ").append(callRetSlot).append("\n");
                        localSlot += 1;
                    } else {
                        cls.append("astore ").append(callRetSlot).append("\n");
                        localSlot += 1;
                    }

                    cls.append("aload ").append(selfSaveSlot).append("\n");
                    cls.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(selfThunkFieldName(pName)).append(" Lgnb/perseus/compiler/Thunk;\n");

                    // Do not rewrite the captured bridged parameters here. A re-entrant
                    // call on the same thunk can already have advanced the activation
                    // further than the current suspended env snapshot.
                    if (!"void".equals(outerInfo.returnType)) {
                        cls.append("aload_0\n");
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(retDesc).append("\n");
                        cls.append("putfield ").append(internalName).append("/cap_ret ").append(retDesc).append("\n");
                    }

                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }
                        int slot = savedSlot.get(p);
                        if ("D".equals(pDesc)) {
                            cls.append("dload ").append(slot).append("\n");
                        } else if ("I".equals(pDesc)) {
                            cls.append("iload ").append(slot).append("\n");
                        } else {
                            cls.append("aload ").append(slot).append("\n");
                        }
                        cls.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envThunkFieldName(outerProc, p)).append(" ").append(pDesc).append("\n");
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        if ("D".equals(retDesc)) {
                            cls.append("dload ").append(retSaveSlot).append("\n");
                        } else if ("I".equals(retDesc)) {
                            cls.append("iload ").append(retSaveSlot).append("\n");
                        } else {
                            cls.append("aload ").append(retSaveSlot).append("\n");
                        }
                        cls.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(retDesc).append("\n");
                    }

                    if ("real".equals(baseType)) {
                        if ("D".equals(pRetDesc)) cls.append("dload ").append(callRetSlot).append("\n");
                        else if ("I".equals(pRetDesc)) cls.append("iload ").append(callRetSlot).append("\n");
                        else cls.append("aload ").append(callRetSlot).append("\n");
                        if ("I".equals(pRetDesc)) cls.append("i2d\n");
                        cls.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    } else if ("integer".equals(baseType) || "boolean".equals(baseType)) {
                        if ("D".equals(pRetDesc)) cls.append("dload ").append(callRetSlot).append("\n");
                        else if ("I".equals(pRetDesc)) cls.append("iload ").append(callRetSlot).append("\n");
                        else cls.append("aload ").append(callRetSlot).append("\n");
                        if ("D".equals(pRetDesc)) cls.append("d2i\n");
                        cls.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    } else if ("deferred".equals(baseType)) {
                        if ("D".equals(pRetDesc)) cls.append("dload ").append(callRetSlot).append("\n");
                        else if ("I".equals(pRetDesc)) cls.append("iload ").append(callRetSlot).append("\n");
                        else cls.append("aload ").append(callRetSlot).append("\n");
                        if ("D".equals(pRetDesc)) {
                            cls.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                        } else if ("I".equals(pRetDesc)) {
                            cls.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                        }
                    } else if ("string".equals(baseType) && "Ljava/lang/String;".equals(pRetDesc)) {
                        cls.append("aload ").append(callRetSlot).append("\n");
                    }

                    cls.append("areturn\n");
                    cls.append(".end method\n\n");

                    cls.append(".method public sync()V\n");
                    cls.append(".limit stack 64\n"); // TODO: calculate required stack
                    cls.append(".limit locals 64\n"); // TODO: calculate required locals
                    for (String p : outerInfo.paramNames) {
                        String pDesc;
                        if (!outerInfo.valueParams.contains(p)) {
                            pDesc = "Lgnb/perseus/compiler/Thunk;";
                        } else {
                            String pType = getFormalBaseType(outerInfo, p);
                            if ("real".equals(pType)) pDesc = "D";
                            else if ("string".equals(pType)) pDesc = "Ljava/lang/String;";
                            else if (pType.startsWith("procedure:")) {
                                pDesc = switch (pType.substring("procedure:".length())) {
                                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                                };
                            } else pDesc = "I";
                        }
                        cls.append("aload_0\n");
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envThunkFieldName(outerProc, p)).append(" ").append(pDesc).append("\n");
                        cls.append("putfield ").append(internalName).append("/cap_").append(p).append(" ").append(pDesc).append("\n");
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        cls.append("aload_0\n");
                        cls.append("getstatic ").append(packageName).append("/").append(className)
                           .append("/").append(envReturnFieldName(outerProc)).append(" ").append(retDesc).append("\n");
                        cls.append("putfield ").append(internalName).append("/cap_ret ").append(retDesc).append("\n");
                    }
                    cls.append("return\n");
                    cls.append(".end method\n\n");

                    cls.append(".method public set(Ljava/lang/Object;)V\n");
                    cls.append(".limit stack 64\n"); // TODO: calculate required stack
                    cls.append(".limit locals 64\n"); // TODO: calculate required locals
                    cls.append("return\n");
                    cls.append(".end method\n\n");

                    thunkClassDefinitions.add(Map.entry(thunkLocalName, cls.toString()));
                    return internalName;
                }
            }
        }

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
        cls.append(".limit stack 64\n"); // TODO: calculate required stack
        cls.append(".limit locals 64\n"); // TODO: calculate required locals
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
        cls.append(".limit stack 64\n"); // TODO: calculate required stack
        cls.append(".limit locals 64\n"); // TODO: calculate required locals
        if (actual != null) {
            // generate expression code inside thunk, using mapping from vars to field indexes
            String actualExprType;
            if (actual instanceof AlgolParser.VarExprContext ve && procedures.containsKey(ve.identifier().getText())) {
                String pName = ve.identifier().getText();
                SymbolTableBuilder.ProcInfo pInfo = procedures.get(pName);
                String pRet = pInfo != null ? pInfo.returnType : "integer";
                String pRetDesc = "real".equals(pRet) ? "D" : "string".equals(pRet) ? "Ljava/lang/String;" : "I";
                cls.append("invokestatic ").append(packageName).append("/").append(className)
                   .append("/").append(pName).append("()").append(pRetDesc).append("\n");
                actualExprType = pRet;
            } else {
                String savedProcName = currentProcName;
                String savedProcRetType = currentProcReturnType;
                int savedRetSlot = procRetvalSlot;
                currentProcName = null;
                currentProcReturnType = null;
                procRetvalSlot = -1;
                cls.append(generateExpr(actual, varToField));
                currentProcName = savedProcName;
                currentProcReturnType = savedProcRetType;
                procRetvalSlot = savedRetSlot;
                actualExprType = getExprBaseType(actual);
            }
            // result on stack is primitive or reference; box it if necessary
            switch (baseType) {
                case "real":
                    // coerce integer expression to double if needed before boxing
                    if ("integer".equals(actualExprType)) cls.append("i2d\n");
                    cls.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    break;
                case "deferred":
                    if ("real".equals(actualExprType)) {
                        cls.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    } else if ("integer".equals(actualExprType) || "boolean".equals(actualExprType)) {
                        cls.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    }
                    break;
                case "integer":
                case "boolean":
                    if ("real".equals(actualExprType)) cls.append("d2i\n");
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
        cls.append(".method public sync()V\n");
        cls.append(".limit stack 64\n"); // TODO: calculate required stack size
        cls.append(".limit locals 64\n"); // TODO: calculate required locals
        cls.append("return\n");
        cls.append(".end method\n\n");
        // generate set(Object) method
        cls.append(".method public set(Ljava/lang/Object;)V\n");
        cls.append(".limit stack 64\n"); // TODO: calculate required stack size
        cls.append(".limit locals 64\n"); // TODO: calculate required locals
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
            AlgolParser.ArgContext arg = argList.get(ai);
            if (!isValue) {
                if (arg.expr() != null) {
                    Set<String> names = collectVarNames(arg.expr());
                    for (String vn : names) {
                        // If the variable is already a thunk (call-by-name), we don't need to box and restore it.
                        String vType = lookupVarType(vn);
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        boolean outerIsCallByNameParam = false;
                        if (outerProc != null) {
                            SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                            if (outerInfo != null && outerInfo.paramNames.contains(vn) && !outerInfo.valueParams.contains(vn)) {
                                outerIsCallByNameParam = true;
                            }
                        }
                        boolean isThunkVar = (vType != null && vType.startsWith("thunk:")) || outerIsCallByNameParam;
                        if (isThunkVar) continue;

                        if (varToBoxSlot.containsKey(vn)) continue;
                        int boxSlot = allocateNewLocal("box");
                        varToBoxSlot.put(vn, boxSlot);
                        // initialize box array [Ljava/lang/Object; and store current value at index 0
                        sb.append("iconst_1\n");
                        sb.append("anewarray java/lang/Object\n");
                        emitStore(sb, "astore", boxSlot);

                        sb.append("aload ").append(boxSlot).append("\n");
                        sb.append("iconst_0\n");
                        if ("real".equals(vType)) {
                            sb.append(generateLoadVar(vn));
                            sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                        } else if ("string".equals(vType)) {
                            sb.append(generateLoadVar(vn));
                        } else {
                            sb.append(generateLoadVar(vn));
                            sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                        }
                        sb.append("aastore\n");
                        varsToRestore.add(vn);
                    }
                }
            }
            if (isValue) {
                if (arg.expr() != null) {
                    sb.append(generateExpr(arg.expr()));
                    String paramType = getFormalBaseType(info, paramName);
                    String argType = exprTypes.getOrDefault(arg.expr(), "integer");
                    if ("real".equals(paramType) && "integer".equals(argType)) {
                        sb.append("i2d\n");
                    }
                } else {
                    sb.append(arg.getText()).append("\n");
                }
            } else {
                ExprContext actual = arg.expr();
                if (actual instanceof AlgolParser.VarExprContext ve) {
                    String vn = ve.identifier().getText();
                    String vType = lookupVarType(vn);
                    String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                    boolean outerIsCallByNameParam = false;
                    if (outerProc != null) {
                        SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                        if (outerInfo != null && outerInfo.paramNames.contains(vn) && !outerInfo.valueParams.contains(vn)) {
                            outerIsCallByNameParam = true;
                        }
                    }
                    if ((vType != null && vType.startsWith("thunk:")) || outerIsCallByNameParam) {
                        sb.append(generateLoadThunkRef(vn));
                        continue;
                    }
                    if (procedures.containsKey(vn) && currentProcName != null && currentProcName.equals(vn)) {
                        // Recursive call to this procedure: cache the thunk in a local variable
                        // so each activation has its own captured environment.
                        // When reusing the currently-active thunk object, refresh it first so the
                        // recursive self-reference sees the activation's latest bridged state.
                        String selfThunkLocal = "__selfThunk_" + vn;
                        Integer selfThunkSlot = currentLocalIndex.get(selfThunkLocal);
                        if (selfThunkSlot == null) {
                            selfThunkSlot = allocateNewLocal(selfThunkLocal);
                            currentLocalIndex.put(selfThunkLocal, selfThunkSlot);
                            sb.append("getstatic ").append(packageName).append("/").append(className)
                              .append("/").append(selfThunkFieldName(vn))
                              .append(" Lgnb/perseus/compiler/Thunk;\n");
                            sb.append("astore ").append(selfThunkSlot).append("\n");
                        }
                        String useExisting = "selfthunk_use_" + thunkCounter + "_" + ai;
                        String done = "selfthunk_done_" + thunkCounter + "_" + ai;

                        sb.append("aload ").append(selfThunkSlot).append("\n");
                        sb.append("dup\n");
                        sb.append("ifnonnull ").append(useExisting).append("\n");
                        sb.append("pop\n");

                        Set<String> names = collectVarNames(actual);
                        Map<String,Integer> varToField = new LinkedHashMap<>();
                        int fi = 0;
                        for (String vnn : names) {
                            if (varToBoxSlot.containsKey(vnn)) {
                                varToField.put(vnn, fi++);
                            }
                        }
                        String baseType = getFormalBaseType(info, paramName);
                        String thunkClass = createThunkClass(varToField, actual, baseType);
                        sb.append("new ").append(thunkClass).append("\n");
                        sb.append("dup\n");
                        for (String vnn : varToField.keySet()) {
                            int boxSlot = varToBoxSlot.get(vnn);
                            sb.append("aload ").append(boxSlot).append("\n");
                        }
                        String ctorDesc = varToField.keySet().stream()
                                            .map(vnn -> "[Ljava/lang/Object;")
                                            .collect(Collectors.joining("", "(", ")V"));
                        sb.append("invokespecial ").append(thunkClass)
                          .append("/<init>").append(ctorDesc).append("\n");
                        sb.append("checkcast gnb/perseus/compiler/Thunk\n");
                        sb.append("dup\n");
                        sb.append("astore ").append(selfThunkSlot).append("\n");
                        sb.append("goto ").append(done).append("\n");
                        sb.append(useExisting).append(":\n");
                        sb.append("dup\n");
                        sb.append("invokeinterface gnb/perseus/compiler/Thunk/sync()V 1\n");
                        sb.append(done).append(":\n");
                        continue;
                    }
                }
                Set<String> names = actual != null ? collectVarNames(actual) : Set.of();
                Map<String,Integer> varToField = new LinkedHashMap<>();
                int fi = 0;
                for (String vn : names) {
                    if (varToBoxSlot.containsKey(vn)) {
                        varToField.put(vn, fi++);
                    }
                }
                String baseType = getFormalBaseType(info, paramName);
                if ("deferred".equals(baseType)) {
                    baseType = getExprBaseType(actual);
                }
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
                    return "Lgnb/perseus/compiler/Thunk;";
                }
                String type = getFormalBaseType(info, p);
                if ("real".equals(type)) return "D";
                if ("string".equals(type)) return "Ljava/lang/String;";
                if (type.startsWith("procedure:")) {
                    return switch (type.substring("procedure:".length())) {
                        case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                        case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                        case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                        default -> "Lgnb/perseus/compiler/VoidProcedure;";
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
            if (varSlot == null && mainLocalIndex != null) varSlot = mainLocalIndex.get(vn);
            String varType = lookupVarType(vn);
            sb.append("aload ").append(boxSlot).append("\n");
            sb.append("iconst_0\n");
            sb.append("aaload\n");
            if (varType != null && varType.startsWith("thunk:")) {
                sb.append("checkcast gnb/perseus/compiler/Thunk\n");
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String targetField = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, vn) : vn;
                if (varSlot != null) {
                    if (!currentLocalIndex.containsKey(vn)) {
                        sb.append("putstatic ").append(packageName).append("/").append(className)
                          .append("/").append(targetField).append(" Lgnb/perseus/compiler/Thunk;\n");
                    } else {
                        emitStore(sb, "astore", varSlot);
                    }
                } else {
                    // Static thunk
                    sb.append("putstatic ").append(packageName).append("/").append(className)
                       .append("/").append(targetField).append(" Lgnb/perseus/compiler/Thunk;\n");
                }
                continue;
            }
            if ("real".equals(varType)) {
                sb.append("checkcast java/lang/Double\n");
                sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String targetField = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, vn) : vn;
                    if (varSlot != null) {
                        if (!currentLocalIndex.containsKey(vn)) {
                            // Outer-scope scalar: write to env bridge
                            sb.append("putstatic ").append(packageName).append("/").append(className)
                              .append("/").append(targetField).append(" D\n");
                        } else {
                            emitStore(sb, "dstore", varSlot);
                        }
                    } else {
                        // Static scalar
                        sb.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(targetField).append(" D\n");
                    }
            } else if ("string".equals(varType)) {
                sb.append("checkcast java/lang/String\n");
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String targetField = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, vn) : vn;
                    if (varSlot != null) {
                        if (!currentLocalIndex.containsKey(vn)) {
                            sb.append("putstatic ").append(packageName).append("/").append(className)
                              .append("/").append(targetField).append(" Ljava/lang/String;\n");
                        } else {
                            emitStore(sb, "astore", varSlot);
                        }
                    } else {
                        // Static scalar
                        sb.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(targetField).append(" Ljava/lang/String;\n");
                    }
            } else {
                sb.append("checkcast java/lang/Integer\n");
                sb.append("invokevirtual java/lang/Integer/intValue()I\n");
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String targetField = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, vn) : vn;
                    if (varSlot != null) {
                        if (!currentLocalIndex.containsKey(vn)) {
                            sb.append("putstatic ").append(packageName).append("/").append(className)
                              .append("/").append(targetField).append(" I\n");
                        } else {
                            emitStore(sb, "istore", varSlot);
                        }
                    } else {
                        // Static scalar
                        sb.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(targetField).append(" I\n");
                    }
            }
        }

        return sb.toString();

    }

    // Clean retyped: Checks if the given expression is a procedure reference
    private boolean isProcedureReferenceExpr(ExprContext expr) {
        if (!(expr instanceof AlgolParser.VarExprContext ve)) {
            return false;
        }
        String name = ve.identifier().getText();
        if (procedures.containsKey(name)) {
            return true;
        }
        String type = lookupVarType(name);
        return type != null && type.startsWith("procedure:");
    }

    private static String arrayTypeToJvmDesc(String arrayType) {
        return CodeGenUtils.arrayTypeToJvmDesc(arrayType);
    }

    private static String scalarTypeToJvmDesc(String scalarType) {
        return CodeGenUtils.scalarTypeToJvmDesc(scalarType);
    }

    /**
     * Looks up a variable's type in the current scope, falling back to main scope.
     *
     * Also supports nested procedure environments: if a variable is not declared in
     * the current or main scope but is a parameter of an enclosing procedure, it
     * is accessed via the environment bridge static fields (e.g. __env_<outer>_<param>).
     */
    private String lookupVarType(String name) {
        String type = currentSymbolTable.get(name);
        if (type == null && mainSymbolTable != null) type = mainSymbolTable.get(name);
        if (type != null) return type;

        // If not found in the local or main symbol tables, check for env-bridge
        // parameters from enclosing procedures (nested scopes).
        for (String outerProc : savedProcNameStack) {
            if (outerProc == null) continue;
            SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
            if (outerInfo == null) continue;
            if (outerInfo.paramNames.contains(name)) {
                // Determine the base type of this parameter
                String baseType = getFormalBaseType(outerInfo, name);
                if (!outerInfo.valueParams.contains(name)) {
                    // Call-by-name parameter: represents a Thunk
                    return "thunk:" + baseType;
                }
                // Value parameter: use actual base type (integer/real/string/procedure)
                return baseType;
            }
        }
        return null;
    }

    private SymbolTableBuilder.ProcInfo getEnclosingProcedureInfo(String name) {
        for (String outerProc : savedProcNameStack) {
            if (outerProc == null || !outerProc.equals(name)) continue;
            return procedures.get(outerProc);
        }
        return null;
    }

    private boolean isProcedureReturnTarget(String name, boolean rhsIsProcedureRef) {
        if (rhsIsProcedureRef) return false;
        String returnType = getProcedureReturnTargetType(name, false);
        return returnType != null && !"void".equals(returnType);
    }

    private boolean isEnclosingProcedureReturnTarget(String name, boolean rhsIsProcedureRef) {
        if (rhsIsProcedureRef || name == null || name.equals(currentProcName)) return false;
        SymbolTableBuilder.ProcInfo outerInfo = getEnclosingProcedureInfo(name);
        return outerInfo != null && !"void".equals(outerInfo.returnType);
    }

    private String getProcedureReturnTargetType(String name, boolean rhsIsProcedureRef) {
        if (rhsIsProcedureRef || name == null) return null;
        if (name.equals(currentProcName) && procRetvalSlot >= 0) {
            return currentProcReturnType;
        }
        SymbolTableBuilder.ProcInfo outerInfo = getEnclosingProcedureInfo(name);
        if (outerInfo != null && !"void".equals(outerInfo.returnType)) {
            return outerInfo.returnType;
        }
        return null;
    }

    private boolean isProcedureVariableTarget(String name, boolean rhsIsProcedureRef) {
        if (isProcedureReturnTarget(name, rhsIsProcedureRef)) return false;
        String type = currentSymbolTable.get(name);
        if (type == null && mainSymbolTable != null) {
            type = mainSymbolTable.get(name);
        }
        return type != null && type.startsWith("procedure:");
    }

    private String envThunkFieldName(String procName, String paramName) {
        return "__env_" + procName + "_" + paramName;
    }

    private String envReturnFieldName(String procName) {
        return "__env_ret_" + procName;
    }

    private String selfThunkFieldName(String procName) {
        return "__selfThunk_" + procName;
    }

    private String staticFieldName(String name, String varType) {
        // Jasmin treats some identifiers (e.g. "outer") as reserved tokens, so we
        // avoid emitting fields with those names by using a stable prefix for
        // procedure-valued variables.
        if (varType != null && varType.startsWith("procedure:")) {
            return "__proc_" + name;
        }
        return name;
    }

    private String getFormalBaseType(SymbolTableBuilder.ProcInfo info, String paramName) {
        if (info == null) {
            return "integer";
        }
        String baseType = info.paramTypes.get(paramName);
        if (baseType != null) {
            return baseType;
        }
        return info.valueParams.contains(paramName) ? "integer" : "deferred";
    }

    private String getExprBaseType(ExprContext expr) {
        if (expr == null) {
            return "integer";
        }
        if (expr instanceof AlgolParser.VarExprContext ve) {
            String name = ve.identifier().getText();
            SymbolTableBuilder.ProcInfo procInfo = procedures.get(name);
            if (procInfo != null) {
                return procInfo.returnType;
            }
        }
        String exprType = exprTypes.get(expr);
        if (exprType == null) {
            return "integer";
        }
        if (exprType.startsWith("thunk:")) {
            return exprType.substring("thunk:".length());
        }
        return exprType;
    }

    private String dynamicUnboxDeferredValue(String targetType) {
        String doubleLabel = generateUniqueLabel("deferred_double");
        String endLabel = generateUniqueLabel("deferred_end");
        if ("string".equals(targetType)) {
            return "checkcast java/lang/String\n";
        }
        if ("integer".equals(targetType) || "boolean".equals(targetType)) {
            return "dup\n"
                + "instanceof java/lang/Double\n"
                + "ifne " + doubleLabel + "\n"
                + "checkcast java/lang/Integer\n"
                + "invokevirtual java/lang/Integer/intValue()I\n"
                + "goto " + endLabel + "\n"
                + doubleLabel + ":\n"
                + "checkcast java/lang/Double\n"
                + "invokevirtual java/lang/Double/doubleValue()D\n"
                + "d2i\n"
                + endLabel + ":\n";
        }
        return "dup\n"
            + "instanceof java/lang/Double\n"
            + "ifne " + doubleLabel + "\n"
            + "checkcast java/lang/Integer\n"
            + "invokevirtual java/lang/Integer/intValue()I\n"
            + "i2d\n"
            + "goto " + endLabel + "\n"
            + doubleLabel + ":\n"
            + "checkcast java/lang/Double\n"
            + "invokevirtual java/lang/Double/doubleValue()D\n"
            + endLabel + ":\n";
    }

    private boolean useEnvBridge() {
        return true;
    }

    private boolean useEnvBridge(String procName) {
        return procName != null && procedures.containsKey(procName);
    }

    private boolean procedureNeedsLocalBridge(SymbolTableBuilder.ProcInfo info) {
        return info != null && !info.nestedProcedures.isEmpty();
    }

    private boolean isCurrentProcedureBridgedLocal(String name) {
        if (currentProcName == null || name == null || !useEnvBridge(currentProcName)) return false;
        SymbolTableBuilder.ProcInfo info = procedures.get(currentProcName);
        if (!procedureNeedsLocalBridge(info)) return false;
        String localType = info.localVars.get(name);
        return localType != null
            && !info.ownVars.contains(name)
            && !localType.endsWith("[]")
            && !localType.startsWith("procedure:");
    }

    private String generateLoadThunkRef(String name) {
        Integer idx = currentLocalIndex.get(name);
        if (idx != null) {
            return "aload " + idx + "\n";
        }
        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
        if (useEnvBridge(outerProc) && outerProc != null) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(outerProc, name)
                + " Lgnb/perseus/compiler/Thunk;\n";
        }
        String type = lookupVarType(name);
        if (type != null && type.startsWith("procedure:")) {
            String pDesc = switch (type.substring("procedure:".length())) {
                case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                default -> "Lgnb/perseus/compiler/VoidProcedure;";
            };
            return "getstatic " + packageName + "/" + className + "/" + staticFieldName(name, type) + " "
                + pDesc + "\n";
        }
        if (mainSymbolTable != null && mainSymbolTable.containsKey(name)) {
            return "getstatic " + packageName + "/" + className + "/" + name + " Lgnb/perseus/compiler/Thunk;\n";
        }
        return "; ERROR: cannot resolve thunk reference " + name + "\n";
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
            String leftType  = exprTypes.getOrDefault(e.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(e.expr(1), "integer");
            String type = exprTypes.getOrDefault(ctx, "integer");
            if (leftType.startsWith("thunk:")) {
                leftType = leftType.substring("thunk:".length());
            }
            if (rightType.startsWith("thunk:")) {
                rightType = rightType.substring("thunk:".length());
            }
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
            String leftType  = exprTypes.getOrDefault(e.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(e.expr(1), "integer");
            String type = exprTypes.getOrDefault(ctx, "integer");
            if (leftType.startsWith("thunk:")) {
                leftType = leftType.substring("thunk:".length());
            }
            if (rightType.startsWith("thunk:")) {
                rightType = rightType.substring("thunk:".length());
            }
            if ("real".equals(type) && "integer".equals(leftType))  left  += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("+".equals(op) ? "dadd" : "dsub") :
                ("+".equals(op) ? "iadd" : "isub");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AndExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "iand\n";
        } else if (ctx instanceof AlgolParser.OrExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "ior\n";
        } else if (ctx instanceof AlgolParser.NotExprContext e) {
            return generateExpr(e.expr(), varToFieldIndex) + "iconst_1\nixor\n";
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
            if (type == null && mainSymbolTable != null) {
                type = mainSymbolTable.get(name);
            }

            // If not found locally, check for env bridge parameters from enclosing procedures
            if (type == null && idx == null) {
                for (String outerProc : savedProcNameStack) {
                    if (outerProc == null) continue;
                    SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                    if (outerInfo == null) continue;
                    if (!useEnvBridge(outerProc)) continue;
                    if (!outerInfo.paramNames.contains(name)) continue;

                    String baseType = getFormalBaseType(outerInfo, name);
                    if (!outerInfo.valueParams.contains(name)) {
                        type = "thunk:" + baseType;
                    } else {
                        type = baseType;
                    }
                    break;
                }
            }

            // support call-by-name thunk parameters (local or env bridge) before idx==null early return
            if (type != null && type.startsWith("thunk:")) {
                String baseType = type.substring("thunk:".length());
                StringBuilder sb2 = new StringBuilder();
                if (idx != null) {
                    sb2.append("aload ").append(idx).append("\n");
                } else {
                    sb2.append(generateLoadThunkRef(name));
                }
                sb2.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                switch (baseType) {
                    case "real" -> {
                        sb2.append("checkcast java/lang/Double\n");
                        sb2.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    }
                    case "deferred" -> {
                        String inferredType = exprTypes.getOrDefault(ctx, "real");
                        sb2.append(dynamicUnboxDeferredValue(inferredType));
                    }
                    case "string" -> sb2.append("checkcast java/lang/String\n");
                    default -> {
                        sb2.append("checkcast java/lang/Integer\n");
                        sb2.append("invokevirtual java/lang/Integer/intValue()I\n");
                    }
                }
                return sb2.toString();
            }

            if (idx == null) {
                // Check if this is a static scalar (no local slot, but exists in symbol table)
                if (type != null && !type.endsWith("[]") && !type.startsWith("procedure:") && !type.startsWith("thunk:")) {
                    // Static scalar: emit getstatic
                    String jvmDesc = scalarTypeToJvmDesc(type);
                    String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                    String fieldName = (useEnvBridge(outerProc) && mainSymbolTable != null && mainSymbolTable.containsKey(name) && outerProc != null)
                        ? envThunkFieldName(outerProc, name) : name;
                    return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
                }
                // Check main symbol table for outer scope static scalars
                if (mainSymbolTable != null) {
                    String mainType = mainSymbolTable.get(name);
                    if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                        // Static scalar from outer scope: emit getstatic
                        String jvmDesc = scalarTypeToJvmDesc(mainType);
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        String fieldName = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, name) : name;
                        return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
                    }
                }
                return "; ERROR: undeclared variable " + name + "\n";
            }
            if (useEnvBridge(currentProcName) && currentProcName != null) {
                SymbolTableBuilder.ProcInfo cp = procedures.get(currentProcName);
                if (cp != null && cp.paramNames.contains(name) && cp.valueParams.contains(name)) {
                    String pType = getFormalBaseType(cp, name);
                    String desc;
                    if ("real".equals(pType)) desc = "D";
                    else if ("string".equals(pType)) desc = "Ljava/lang/String;";
                    else if (pType.startsWith("procedure:")) {
                        desc = switch (pType.substring("procedure:".length())) {
                            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                            default -> "Lgnb/perseus/compiler/VoidProcedure;";
                        };
                    } else desc = "I";
                    return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentProcName, name) + " " + desc + "\n";
                }
            }
            if (isCurrentProcedureBridgedLocal(name)) {
                return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentProcName, name)
                    + " " + scalarTypeToJvmDesc(type) + "\n";
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

            // String scalar character access: s[i] -> s.substring(i-1, i)
            if ("string".equals(elemType)) {
                StringBuilder sb = new StringBuilder();
                sb.append(generateLoadVar(arrName));                         // load s
                sb.append(generateExpr(e.expr(), varToFieldIndex));          // load i
                sb.append("dup\n");                                          // i, i
                sb.append("iconst_1\n").append("isub\n");                    // i-1, i  (beginIndex)
                sb.append("swap\n");                                         // i-1, i  → correct order for (II)
                sb.append("invokevirtual java/lang/String/substring(II)Ljava/lang/String;\n");
                return sb.toString();
            }

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

            // Check if this name refers to a procedure variable
            String varType = lookupVarType(procName);
            boolean isProcVar = varType != null && varType.startsWith("procedure:");
            SymbolTableBuilder.ProcInfo declaredProc = procedures.get(procName);
            boolean preferDirectProcedureCall = declaredProc != null && !"void".equals(declaredProc.returnType);

            // If this is a declared procedure, invoke its body.
            // If the name is also a procedure variable, calls should route through the
            // current binding (procedure variable) so assignment to the variable affects
            // subsequent calls.
            String callCode;
            if (declaredProc != null) {
                if (isProcVar && !preferDirectProcedureCall) {
                    callCode = generateProcedureVariableCall(procName, varType, e.argList().arg());
                } else {
                    callCode = generateUserProcedureInvocation(procName, e.argList().arg(), false);
                }
            } else if (isProcVar) {
                callCode = generateProcedureVariableCall(procName, varType, e.argList().arg());
            } else {
                // Fall back to ordinary procedure invocation (should not usually happen)
                callCode = generateUserProcedureInvocation(procName, e.argList().arg(), false);
            }

            // For Algol procedure expressions like `make(1)` where `make` is a procedure
            // variable, the call is implemented as a `void` invoke and stores the result in
            // the procedure variable (e.g. __proc_make). In expression context we need to
            // preserve the original binding, return the new value, and restore the old one.
            if (declaredProc != null && isProcVar) {
                String procReturnType = declaredProc.returnType;
                if ("void".equals(procReturnType)) {
                    int saveSlot = allocateNewLocal("procVar");
                    String currentBinding = generateProcedureVariableLoad(procName, varType);
                    StringBuilder exprCode = new StringBuilder();
                    exprCode.append(currentBinding);
                    exprCode.append("astore ").append(saveSlot).append("\n");
                    exprCode.append(callCode);
                    exprCode.append(generateProcedureVariableLoad(procName, varType));

                    Integer localSlot = currentLocalIndex.get(procName);
                    if (localSlot != null) {
                        exprCode.append("aload ").append(saveSlot).append("\n");
                        exprCode.append("astore ").append(localSlot).append("\n");
                    } else {
                        exprCode.append("aload ").append(saveSlot).append("\n");
                        exprCode.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(staticFieldName(procName, varType)).append(" ")
                                .append(getProcedureInterfaceDescriptor(varType)).append("\n");
                    }
                    return exprCode.toString();
                }
            }
            return callCode;
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
     * Delegates to {@link BuiltinFunctionGenerator} which handles math builtins
     * (sqrt, abs, sin, …) and string builtins (length, concat, substring) separately.
     * Returns null if the function name is not a recognized built-in.
     */
    private String generateBuiltinMathFunction(String funcName, AlgolParser.ProcCallExprContext ctx) {
        return builtinGen.generate(funcName, ctx);
    }

    /**
     * Generates code to create a procedure reference object for the given procedure.
     * Creates a synthetic class that implements the appropriate procedure interface
     * and delegates calls to the actual procedure method.
     */
    /**
     * Creates a synthetic ProcRef class and returns JVM instantiation bytecode.
     * Delegates to ProcedureGenerator.
     */
    private String generateProcedureReference(String procName, SymbolTableBuilder.ProcInfo procInfo) {
        return procGen.generateProcedureReference(procName, procInfo);
    }
    
    private String getReturnTypeDescriptor(String returnType) {
        return CodeGenUtils.getReturnTypeDescriptor(returnType);
    }

    private String getReturnInstruction(String returnType) {
        return CodeGenUtils.getReturnInstruction(returnType);
    }

    private String getProcedureInterfaceDescriptor(String procType) {
        String returnType = procType.substring("procedure:".length());
        return switch (returnType) {
            case "void" -> "Lgnb/perseus/compiler/VoidProcedure;";
            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
            default -> throw new RuntimeException("Unknown procedure return type: " + returnType);
        };
    }

    /**
     * Generates code to call a procedure through a procedure variable.
     * Delegates to ProcedureGenerator when the variable is stored in a local slot.
     * Falls back to a static field lookup if the variable is stored in the outer scope.
     */
    private String generateProcedureVariableCall(String varName, String varType, List<AlgolParser.ArgContext> args) {
        // Prefer calling through a local slot if this procedure variable is stored locally.
        Integer slot = currentLocalIndex.get(varName);
        if (slot != null) {
            // Use local slot (allows procedure-variable parameters and local procedure variables)
            return procGen.generateProcedureVariableCall(varName, varType, args);
        }
        // Otherwise, the procedure variable is stored in a static field.
        return generateProcedureVariableCallViaStaticField(varName, varType, args);
    }

    private String generateProcedureVariableLoad(String varName, String varType) {
        String returnType = varType.substring("procedure:".length());
        String desc = switch (returnType) {
            case "void" -> "Lgnb/perseus/compiler/VoidProcedure;";
            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
            case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
            default -> throw new RuntimeException("Unknown procedure return type: " + returnType);
        };
        Integer slot = currentLocalIndex.get(varName);
        if (slot != null) {
            return "aload " + slot + "\n";
        }
        // Fallback to static field if we don't have a local slot
        return "getstatic " + packageName + "/" + className + "/" + staticFieldName(varName, varType) + " " + desc + "\n";
    }

    private String generateProcedureVariableCallViaStaticField(String varName, String varType, List<AlgolParser.ArgContext> args) {
        // Load the procedure reference from the static field instead of a local slot.
        String load = generateProcedureVariableLoad(varName, varType);
        // Build the argument array and invoke the method on the procedure interface.
        String returnType = varType.substring("procedure:".length());
        String interfaceName = switch (returnType) {
            case "void" -> "VoidProcedure";
            case "real" -> "RealProcedure";
            case "integer" -> "IntegerProcedure";
            case "string" -> "StringProcedure";
            default -> throw new RuntimeException("Unknown procedure return type: " + returnType);
        };
        StringBuilder sb = new StringBuilder();
        sb.append(load);
        sb.append("checkcast gnb/perseus/compiler/").append(interfaceName).append("\n");

        int argCount = args.size();
        SymbolTableBuilder.ProcInfo targetInfo = procedures.get(varName);

        if (argCount == 0) {
            sb.append("iconst_0\nanewarray java/lang/Object\n");
        } else {
            sb.append("ldc ").append(argCount).append("\nanewarray java/lang/Object\n");
            for (int i = 0; i < argCount; i++) {
                AlgolParser.ExprContext argExpr = args.get(i).expr();

                boolean isByName = false;
                String paramBaseType = "integer";
                if (targetInfo != null && i < targetInfo.paramNames.size()) {
                    String paramName = targetInfo.paramNames.get(i);
                    isByName = !targetInfo.valueParams.contains(paramName);
                    paramBaseType = getFormalBaseType(targetInfo, paramName);
                }

                sb.append("dup\n");
                sb.append("ldc ").append(i).append("\n");

                if (isByName) {
                    // Pass a Thunk for by-name parameters. If the source expression is already
                    // a thunk variable, use it directly; otherwise create a thunk wrapper.
                    if (argExpr instanceof AlgolParser.VarExprContext argVar) {
                        String vn = argVar.identifier().getText();
                        String vnType = lookupVarType(vn);
                        if (vnType != null && vnType.startsWith("thunk:")) {
                            sb.append(generateLoadThunkRef(vn));
                        } else {
                            String thunkType = "deferred".equals(paramBaseType) ? getExprBaseType(argExpr) : paramBaseType;
                            String thunkClass = createThunkClass(new LinkedHashMap<>(), argExpr, thunkType);
                            sb.append("new ").append(thunkClass).append("\n");
                            sb.append("dup\n");
                            sb.append("invokespecial ").append(thunkClass).append("/<init>()V\n");
                        }
                    } else {
                        String thunkType = "deferred".equals(paramBaseType) ? getExprBaseType(argExpr) : paramBaseType;
                        String thunkClass = createThunkClass(new LinkedHashMap<>(), argExpr, thunkType);
                        sb.append("new ").append(thunkClass).append("\n");
                        sb.append("dup\n");
                        sb.append("invokespecial ").append(thunkClass).append("/<init>()V\n");
                    }
                } else {
                    String argType = exprTypes.getOrDefault(argExpr, "integer");
                    String baseType = argType.startsWith("thunk:") ? argType.substring("thunk:".length()) : argType;
                    sb.append(generateExpr(argExpr));
                    if ("real".equals(baseType)) {
                        sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    } else if ("integer".equals(baseType) || "boolean".equals(baseType)) {
                        sb.append("invokestatic java/lang/Integer.valueOf(I)Ljava/lang/Integer;\n");
                    }
                }

                sb.append("aastore\n");
            }
        }

        sb.append("invokeinterface gnb/perseus/compiler/")
          .append(interfaceName)
          .append("/invoke([Ljava/lang/Object;)")
          .append(CodeGenUtils.getReturnTypeDescriptor(returnType))
          .append(" 2\n");
        return sb.toString();
    }
}



