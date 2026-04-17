// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusParser;
import gnb.perseus.compiler.antlr.PerseusParser.ExprContext;
import gnb.perseus.compiler.codegen.BuiltinFunctionGenerator;
import gnb.perseus.compiler.codegen.ChannelIOGenerator;
import gnb.perseus.compiler.codegen.ClassGenerator;
import gnb.perseus.compiler.codegen.ExceptionGenerator;
import gnb.perseus.compiler.codegen.ProcedureGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

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
public class CodeGenerator extends PerseusBaseListener {
    private final String source;
    private final String packageName;
    private final String classPackageName;
    private final String className;
    // Procedure definitions from SymbolTableBuilder (name → ProcInfo)
    private final Map<String, SymbolTableBuilder.ProcInfo> procedures;
    private final Map<String, SymbolTableBuilder.ClassInfo> classes;
    // Switch declarations from SymbolTableBuilder (name -> parse context)
    private final Map<String, PerseusParser.SwitchDeclContext> switchDeclarations;

    // --- Thunk helper data ---
    // counter for generating unique thunk class names
    private int thunkCounter = 0;
    // collects (shortClassName, jasminSource) for generated thunk classes (each needs its own .j file)
    private final List<Map.Entry<String,String>> thunkClassDefinitions = new ArrayList<>();

    // --- Procedure reference helper data ---
    // counter for generating unique procedure reference class names
    private int procRefCounter = 0;
    private int anonymousProcedureCounter = 0;
    // collects (shortClassName, jasminSource) for generated procedure reference classes
    private final List<Map.Entry<String,String>> procRefClassDefinitions = new ArrayList<>();
    private final List<Map.Entry<String,String>> generatedClassDefinitions = new ArrayList<>();
    private final Map<PerseusParser.AnonymousProcedureExprContext, String> anonymousProcedureNames = new IdentityHashMap<>();
    private int anonymousBodySuppressionDepth = 0;
    private boolean emittingAnonymousBody = false;

    // --- Current context (swapped when entering/exiting procedures) ---
    private Map<String, String> currentSymbolTable;
    private Map<String, Type> currentSymbolTableTypes;
    private Map<String, Integer> currentLocalIndex;
    private int currentNumLocals;
    private Map<String, int[]> currentArrayBounds;
    private Map<String, List<int[]>> currentArrayBoundPairs;
    private final Map<String, String> rootMainSymbolTable;
    private final Map<String, Type> rootMainSymbolTableTypes;
    private final Map<String, Integer> rootMainLocalIndex;

    // --- Saved outer context (one entry per active nested procedure level) ---
    private Map<String, String>  mainSymbolTable;
    private Map<String, Type>    mainSymbolTableTypes;
    private Map<String, Integer> mainLocalIndex;
    private int                  mainNumLocals;
    private Map<String, int[]>   mainArrayBounds;
    private Map<String, List<int[]>> mainArrayBoundPairs;
    private final Deque<Map<String, String>>  savedOuterSTStack     = new LinkedList<>();
    private final Deque<Map<String, Type>>    savedOuterTypedSTStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedOuterLIStack     = new LinkedList<>();
    private final Deque<Integer>              savedOuterNLStack     = new LinkedList<>();
    private final Deque<Map<String, int[]>>   savedOuterABStack     = new LinkedList<>();
    private final Deque<Map<String, List<int[]>>> savedOuterABPairsStack = new LinkedList<>();
    private final Deque<String>               savedProcNameStack    = new LinkedList<>();
    private final Deque<String>               savedProcRetTypeStack = new LinkedList<>();
    private final Deque<Integer>              savedProcRetSlotStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedEnvParamSlotsStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedEnvLocalSlotsStack = new LinkedList<>();
    private final Deque<Integer>              savedEnvRetSaveSlotStack = new LinkedList<>();
    private final Deque<Map<String, Integer>> savedNestedSelfThunkSlotsStack = new LinkedList<>();
    private final Deque<Map<String, int[]>>   savedArrayParamBoundSlotsStack = new LinkedList<>();
    // Tracks whether the current procedure declaration is actually a procedure-variable declaration
    // (i.e., a `procedure p;` with no executable body) so we can skip generating a method for it.
    private final Deque<Boolean>              skipProcedureDeclStack = new LinkedList<>();
    private int skippedClassDepth = 0;

    // Maps expression contexts to their inferred types ("integer" or "real")
    private final Map<PerseusParser.ExprContext, String> exprTypes;
    private final Map<PerseusParser.ExprContext, Type> exprTypesInfo;

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
    private String currentClosureOwnerProcName = null;
    private String currentProcReturnType = null;
    private int    procRetvalSlot = -1;
    private Map<String, Integer> currentEnvParamSlots = new LinkedHashMap<>();
    private Map<String, Integer> currentEnvLocalSlots = new LinkedHashMap<>();
    private int currentEnvRetSaveSlot = -1;
    private Map<String, Integer> currentNestedSelfThunkSlots = new LinkedHashMap<>();
    private Map<String, int[]> currentArrayParamBoundSlots = new LinkedHashMap<>();

    // For for loops
    private String currentForLoopLabel;
    private String currentForEndLabel;
    private final Deque<String> loopBackEdgeLabelStack = new ArrayDeque<>();
    private final Deque<String> loopContinueLabelStack = new ArrayDeque<>();
    private final Deque<String> loopBreakLabelStack = new ArrayDeque<>();
    private final Deque<String> repeatBodyLabelStack = new ArrayDeque<>();

    // Stack for capturing for-loop body code (enables multi-element for-list by body-inline duplication)
    private final Deque<StringBuilder> forBodyStack = new ArrayDeque<>();

    // Stacks for if/then/else label management (supports nesting)
    private final Deque<String> ifEndLabelStack  = new ArrayDeque<>();
    private final Deque<String> ifElseLabelStack = new ArrayDeque<>();
    private final Deque<ExceptionBindingState> exceptionBindingStateStack = new ArrayDeque<>();
    private final ExceptionGenerator exceptionGen;
    private final ChannelIOGenerator channelGen;

    // Map of procedure variable names to their main-method JVM slot indices
    private final Map<String, Integer> procVarSlots;

    // Delegate for generating built-in math and string function calls
    private final BuiltinFunctionGenerator builtinGen;
    private final ClassGenerator classGen;
    private final Map<String, String> externalJavaClasses;
    private final Map<String, SymbolTableBuilder.ExternalValueInfo> externalJavaStaticValues;
    // Delegate for generating procedure reference and procedure-variable call code
    private final ProcedureGenerator procGen;

    private static final class ExceptionBindingState {
        final String name;
        final Integer priorLocalSlot;
        final String priorType;
        final Type priorTypeInfo;
        final int priorNumLocals;

        ExceptionBindingState(String name, Integer priorLocalSlot, String priorType, Type priorTypeInfo, int priorNumLocals) {
            this.name = name;
            this.priorLocalSlot = priorLocalSlot;
            this.priorType = priorType;
            this.priorTypeInfo = priorTypeInfo;
            this.priorNumLocals = priorNumLocals;
        }
    }

    private static final ExceptionBindingState NO_EXCEPTION_BINDING =
            new ExceptionBindingState("", null, null, null, -1);

    public CodeGenerator(String source, String packageName, String classPackageName, String className,
                         Map<String, String> symbolTable, Map<String, Type> typedSymbolTable,
                         Map<String, Integer> localIndex, int numLocals,
                         Map<PerseusParser.ExprContext, String> exprTypes,
                         Map<PerseusParser.ExprContext, Type> typedExprTypes, Map<String, int[]> arrayBounds,
                         Map<String, List<int[]>> arrayBoundPairs,
                         Map<String, SymbolTableBuilder.ProcInfo> procedures,
                         Map<String, SymbolTableBuilder.ClassInfo> classes,
                         Map<String, PerseusParser.SwitchDeclContext> switchDeclarations,
                         Map<String, Integer> procVarSlots,
                         Map<String, String> externalJavaClasses,
                         Map<String, SymbolTableBuilder.ExternalValueInfo> externalJavaStaticValues) {
        this.source = source;
        this.packageName = packageName;
        this.classPackageName = classPackageName != null && !classPackageName.isBlank() ? classPackageName : packageName;
        this.className = className;
        this.exprTypes = exprTypes;
        this.exprTypesInfo = typedExprTypes;
        this.procedures = procedures;
        this.classes = classes != null ? classes : Map.of();
        this.switchDeclarations = switchDeclarations != null ? switchDeclarations : Map.of();
        this.procVarSlots = procVarSlots != null ? procVarSlots : Map.of();
        this.externalJavaClasses = externalJavaClasses != null ? externalJavaClasses : Map.of();
        this.externalJavaStaticValues = externalJavaStaticValues != null ? externalJavaStaticValues : Map.of();
        this.rootMainSymbolTable = symbolTable;
        this.rootMainSymbolTableTypes = typedSymbolTable;
        this.rootMainLocalIndex = localIndex;
        this.currentSymbolTable = symbolTable;
        this.currentSymbolTableTypes = typedSymbolTable;
        this.currentLocalIndex  = localIndex;
        this.currentNumLocals   = numLocals;
        this.currentArrayBounds = arrayBounds;
        this.currentArrayBoundPairs = arrayBoundPairs;
        this.builtinGen = new BuiltinFunctionGenerator(exprTypes);
        this.classGen = new ClassGenerator(source, this.classPackageName, exprTypes, this.classes);
        this.builtinGen.setExprCodeGen(e -> generateExpr(e));
        this.procGen = new ProcedureGenerator(
            packageName, className,
            this::nextProcRefId,
            (name, content) -> procRefClassDefinitions.add(Map.entry(name, content)),
            this::lookupProcVarSlot,
            exprTypes,
            e -> generateExpr(e));
        this.procGen.setCurrentProcNameSupplier(
            () -> this.currentClosureOwnerProcName != null ? this.currentClosureOwnerProcName : this.currentProcName);
        this.procGen.setProceduresSupplier(() -> this.procedures);
        this.exceptionGen = new ExceptionGenerator(this::generateUniqueLabel);
        this.channelGen = new ChannelIOGenerator(packageName, className);
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

    public Map<String,String> getGeneratedClassOutputs() {
        Map<String,String> result = new LinkedHashMap<>();
        for (Map.Entry<String,String> e : generatedClassDefinitions) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    private boolean skippingClassSubtree() {
        return skippedClassDepth > 0;
    }

    private boolean skippingCodegen() {
        return skippingClassSubtree() || (anonymousBodySuppressionDepth > 0 && !emittingAnonymousBody);
    }

    @Override
    public void enterClassDecl(PerseusParser.ClassDeclContext ctx) {
        skippedClassDepth++;
    }

    @Override
    public void exitClassDecl(PerseusParser.ClassDeclContext ctx) {
        String classDeclName = ctx.className.getText();
        SymbolTableBuilder.ClassInfo cls = classes.get(classDeclName);
        if (cls != null && !cls.externalJava) {
            generatedClassDefinitions.add(Map.entry(companionClassFileName(classDeclName), classGen.generateClassJasmin(cls)));
        }
        skippedClassDepth = Math.max(0, skippedClassDepth - 1);
    }

    @Override
    public void enterAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        PerseusParser.AnonymousProcedureBodyContext body = ctx.anonymousProcedureBody();
        if (body instanceof PerseusParser.AnonymousBlockProcedureBodyContext) {
            anonymousBodySuppressionDepth++;
        }
    }

    @Override
    public void exitAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        PerseusParser.AnonymousProcedureBodyContext body = ctx.anonymousProcedureBody();
        if (body instanceof PerseusParser.AnonymousBlockProcedureBodyContext) {
            anonymousBodySuppressionDepth = Math.max(0, anonymousBodySuppressionDepth - 1);
        }
    }

    private String companionClassFileName(String simpleClassName) {
        return className + "$Class$" + simpleClassName;
    }

    private Type lookupSymbolTypeInfo(String name) {
        Type type = currentSymbolTableTypes != null ? currentSymbolTableTypes.get(name) : null;
        if (type == null && mainSymbolTableTypes != null) {
            type = mainSymbolTableTypes.get(name);
        }
        if (type == null && rootMainSymbolTableTypes != null) {
            type = rootMainSymbolTableTypes.get(name);
        }
        return type;
    }

    private String lookupSymbolType(String name) {
        Type typed = lookupSymbolTypeInfo(name);
        if (typed != null) {
            return typed.toLegacyString();
        }
        String type = currentSymbolTable != null ? currentSymbolTable.get(name) : null;
        if (type == null && mainSymbolTable != null) {
            type = mainSymbolTable.get(name);
        }
        if (type == null && rootMainSymbolTable != null) {
            type = rootMainSymbolTable.get(name);
        }
        return type;
    }

    private Type exprTypeInfo(PerseusParser.ExprContext expr, Type defaultType) {
        if (expr == null) {
            return defaultType;
        }
        Type type = exprTypesInfo != null ? exprTypesInfo.get(expr) : null;
        return type != null ? type : defaultType;
    }

    private String exprTypeTag(PerseusParser.ExprContext expr, String defaultType) {
        Type typed = exprTypeInfo(expr, defaultType != null ? Type.parse(defaultType) : null);
        if (typed != null) {
            return typed.toLegacyString();
        }
        return exprTypes.getOrDefault(expr, defaultType);
    }

    private Type procedureReturnTargetTypeInfo(String name, boolean rhsIsProcedureRef) {
        String legacyType = getProcedureReturnTargetType(name, rhsIsProcedureRef);
        return legacyType != null ? Type.parse(legacyType) : null;
    }

    private boolean usesIntegerStorage(Type type) {
        Type effective = type != null ? type.unwrapThunk() : null;
        return Type.INTEGER.equals(effective) || Type.BOOLEAN.equals(effective);
    }

    private boolean usesRealStorage(Type type) {
        Type effective = type != null ? type.unwrapThunk() : null;
        return Type.REAL.equals(effective) || Type.DEFERRED.equals(effective);
    }

    private boolean usesObjectStorage(Type type) {
        Type effective = type != null ? type.unwrapThunk() : null;
        return isObjectType(effective);
    }

    private void emitTypedStore(Type type, int slot) {
        if (usesRealStorage(type)) {
            emitStore("dstore", slot);
        } else if (usesObjectStorage(type)) {
            emitStore("astore", slot);
        } else {
            emitStore("istore", slot);
        }
    }

    private void emitTypedLoad(Type type, int slot) {
        if (usesRealStorage(type)) {
            activeOutput.append("dload ").append(slot).append("\n");
        } else if (usesObjectStorage(type)) {
            activeOutput.append("aload ").append(slot).append("\n");
        } else {
            activeOutput.append("iload ").append(slot).append("\n");
        }
    }

    @Override
    public void enterProgram(PerseusParser.ProgramContext ctx) {
        mainHadExecutableStatements = false;

        // Class header and <init>
        classHeader.append(".source ").append(source).append("\n")
                   .append(".class public ").append(packageName).append("/").append(className).append("\n")
                   .append(".super java/lang/Object\n\n");

        // Emit static field declarations for arrays (must appear BEFORE methods in Jasmin)
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String arrName = symEntry.getKey();
            Type arrType = symEntry.getValue();
            if (arrType != null && arrType.isArray()) {
                classHeader.append(".field public static ").append(arrName)
                           .append(" ").append(arrayTypeToJvmDesc(arrType)).append("\n");
            }
        }

        // Emit static field declarations for scalars (must appear BEFORE methods in Jasmin)
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String varName = symEntry.getKey();
            Type varType = symEntry.getValue();
            if (varType != null && !varType.isArray() && !varType.isProcedure()) {
                classHeader.append(".field public static ").append(varName)
                           .append(" ").append(scalarTypeToJvmDesc(varType)).append("\n");
            }
        }

        // Emit static fields for all procedure-typed variables (even in nested scopes) so
        // they can be referenced via getstatic/putstatic from generated call-by-name and
        // procedure-variable code.
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String varName = symEntry.getKey();
            Type varType = symEntry.getValue();
            if (varType != null && varType.isProcedure()) {
                String desc = getProcedureInterfaceDescriptor(varType);
                classHeader.append(".field public static ")
                           .append(staticFieldName(varName, varType.toLegacyString())).append(" ").append(desc).append("\n");
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
                    if (pType.endsWith("[]")) continue;
                    if ("real".equals(pType) || "deferred".equals(pType)) pDesc = "D";
                    else if (isObjectType(pType)) pDesc = CodeGenUtils.getReturnTypeDescriptor(pType);
                    else if (pType.startsWith("procedure:")) {
                        pDesc = getProcedureInterfaceDescriptor(pType);
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
                String rDesc = CodeGenUtils.getReturnTypeDescriptor(pInfo.returnType);
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
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String varName = symEntry.getKey();
            Type type = symEntry.getValue();
            if (type != null && !type.isArray() && !type.isProcedure()) {
                // Scalar variable: initialize via putstatic
                if (type.isVector()) {
                    mainCode.append("new java/util/ArrayList\n")
                            .append("dup\n")
                            .append("invokespecial java/util/ArrayList/<init>()V\n");
                } else if (type.isMap()) {
                    mainCode.append("new java/util/LinkedHashMap\n")
                            .append("dup\n")
                            .append("invokespecial java/util/LinkedHashMap/<init>()V\n");
                } else if (type.isSet()) {
                    mainCode.append("new java/util/LinkedHashSet\n")
                            .append("dup\n")
                            .append("invokespecial java/util/LinkedHashSet/<init>()V\n");
                } else if (Type.INTEGER.equals(type) || Type.BOOLEAN.equals(type)) {
                    mainCode.append("iconst_0\n");
                } else if (Type.REAL.equals(type)) {
                    mainCode.append("dconst_0\n");
                } else if (Type.STRING.equals(type)) {
                    mainCode.append("ldc \"\"\n");
                } else if (type.isRef()) {
                    mainCode.append("aconst_null\n");
                }
                mainCode.append("putstatic ").append(packageName).append("/").append(className)
                        .append("/").append(varName).append(" ").append(scalarTypeToJvmDesc(type)).append("\n");
            }
        }

        // Initialize procedure variables to self-referential ProcRef objects.
        // This ensures that a call to the procedure before any assignment uses the
        // declared procedure implementation (Algol's bindable procedure semantics).
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String varName = symEntry.getKey();
            Type type = symEntry.getValue();
            if (type != null && type.isProcedure() && procedures.containsKey(varName)) {
                mainCode.append(generateProcedureReference(varName, procedures.get(varName)));
                mainCode.append("putstatic ")
                        .append(packageName).append("/").append(className)
                        .append("/").append(staticFieldName(varName, type.toLegacyString())).append(" ")
                        .append(getProcedureInterfaceDescriptor(type)).append("\n");
            }
        }

        // Initialize arrays as static fields (newarray + putstatic)
        for (Map.Entry<String, Type> symEntry : currentSymbolTableTypes.entrySet()) {
            String varName = symEntry.getKey();
            Type type = symEntry.getValue();
            if (type == null || !type.isArray()) continue;
            List<int[]> bounds = lookupDeclaredArrayBoundPairs(varName);
            if (bounds == null || bounds.isEmpty()) continue;
            int size = computeFlattenedArraySize(bounds);
            boolean isRefArray = type.elementType() != null && type.elementType().isRef();
            String elemType = isRefArray ? "java/lang/Object"
                    : Type.REAL.equals(type.elementType()) ? "double"
                    : Type.BOOLEAN.equals(type.elementType()) ? "boolean"
                    : Type.STRING.equals(type.elementType()) ? "java/lang/String"
                    : "int";
            String newarrayInstr = (Type.STRING.equals(type.elementType()) || isRefArray) ? "anewarray" : "newarray";
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
    public void exitProgram(PerseusParser.ProgramContext ctx) {
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
    public void enterProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (skippingCodegen()) {
            skipProcedureDeclStack.push(true);
            return;
        }
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
        savedOuterTypedSTStack.push(mainSymbolTableTypes);
        savedOuterLIStack.push(mainLocalIndex);
        savedOuterNLStack.push(mainNumLocals);
        savedOuterABStack.push(mainArrayBounds);
        savedOuterABPairsStack.push(mainArrayBoundPairs);
        savedProcNameStack.push(currentProcName);
        savedProcRetTypeStack.push(currentProcReturnType);
        savedProcRetSlotStack.push(procRetvalSlot);
        savedEnvParamSlotsStack.push(currentEnvParamSlots);
        savedEnvLocalSlotsStack.push(currentEnvLocalSlots);
        savedEnvRetSaveSlotStack.push(currentEnvRetSaveSlot);
        savedNestedSelfThunkSlotsStack.push(currentNestedSelfThunkSlots);
        savedArrayParamBoundSlotsStack.push(currentArrayParamBoundSlots);

        // Make the current scope the new "outer" scope
        mainSymbolTable   = currentSymbolTable;
        mainSymbolTableTypes = currentSymbolTableTypes;
        mainLocalIndex    = currentLocalIndex;
        mainNumLocals     = currentNumLocals;
        mainArrayBounds   = currentArrayBounds;
        mainArrayBoundPairs = currentArrayBoundPairs;

        // Build procedure-local context
        currentProcName       = procName;
        currentProcReturnType = info.returnType;
        currentEnvParamSlots  = new LinkedHashMap<>();
        currentEnvLocalSlots  = new LinkedHashMap<>();
        currentEnvRetSaveSlot = -1;
        currentNestedSelfThunkSlots = new LinkedHashMap<>();
        currentArrayParamBoundSlots = new LinkedHashMap<>();

        Map<String, String>  procST = new LinkedHashMap<>();
        Map<String, Type>    procSTTypes = new LinkedHashMap<>();
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
            procSTTypes.put(paramName, Type.parse(paramType));
            procLI.put(paramName, nextSlot);
            if (paramType.endsWith("[]")) {
                int lowerSlot = nextSlot + 1;
                int upperSlot = nextSlot + 2;
                currentArrayParamBoundSlots.put(paramName, new int[]{lowerSlot, upperSlot});
                nextSlot += 3;
            } else {
                // thunks and procedure refs are object references so occupy 1 slot, real still 2
                nextSlot += (paramType.startsWith("thunk:") || paramType.startsWith("procedure:")) ? 1 : (("real".equals(paramType) || "deferred".equals(paramType)) ? 2 : 1);
            }
        }
        // Then locals
        for (Map.Entry<String, String> local : info.localVars.entrySet()) {
            String varName = local.getKey();
            String varType = local.getValue();
            procST.put(varName, varType);
            procSTTypes.put(varName, info.typedLocalVars.getOrDefault(varName, Type.parse(varType)));
            if (info.ownVars.contains(varName)) {
                // own locals persist across re-entry, so represent them as class statics
                // rather than per-activation JVM locals.
                continue;
            }
            procLI.put(varName, nextSlot);
            nextSlot += ("real".equals(varType) || "deferred".equals(varType)) ? 2 : 1;
        }
        // Retval slot at end (only for typed function procedures, not void)
        if (!"void".equals(info.returnType)) {
            procRetvalSlot = nextSlot;
            nextSlot += ("real".equals(info.returnType) || "deferred".equals(info.returnType)) ? 2 : 1;
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
            procSTTypes.put(procName, Type.procedure(info.returnTypeInfo));
            procLI.put(procName, selfRefSlot);
        }
        int procNumLocals = nextSlot;

        currentSymbolTable   = procST;
        currentSymbolTableTypes = procSTTypes;
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
                if (type.endsWith("[]")) return arrayTypeToJvmDesc(type) + "II";
                if (type.startsWith("procedure:")) return getProcedureInterfaceDescriptor(type);
                return CodeGenUtils.getReturnTypeDescriptor(type);
            })
            .collect(Collectors.joining());
        String retDesc = CodeGenUtils.getReturnTypeDescriptor(info.returnType);

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
                if (pType.endsWith("[]")) continue;
                if (info.valueParams.contains(p)) {
                    // Only value parameters use static env fields
                    if ("real".equals(pType) || "deferred".equals(pType)) {
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
                        String pDesc = getProcedureInterfaceDescriptor(pType);
                        int saveSlot = currentNumLocals;
                        currentNumLocals += 1;
                        currentEnvParamSlots.put(p, saveSlot);
                        activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ").append(pDesc).append("\n");
                        emitStore("astore", saveSlot);
                        activeOutput.append("aload ").append(pSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ").append(pDesc).append("\n");
                    } else if (isObjectType(pType)) {
                        String pDesc = CodeGenUtils.getReturnTypeDescriptor(pType);
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
            String rDesc = CodeGenUtils.getReturnTypeDescriptor(info.returnType);
            currentEnvRetSaveSlot = currentNumLocals;
            currentNumLocals += ("real".equals(info.returnType) || "deferred".equals(info.returnType)) ? 2 : 1;
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            if ("real".equals(info.returnType) || "deferred".equals(info.returnType)) emitStore("dstore", currentEnvRetSaveSlot);
            else if (isObjectType(info.returnType)) emitStore("astore", currentEnvRetSaveSlot);
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
                else if (isObjectType(localType)) emitStore("astore", saveSlot);
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
            Type varTypeInfo = procSTTypes.get(e.getKey());
            String varType = varTypeInfo != null ? varTypeInfo.toLegacyString() : procST.get(e.getKey());
            int slot = e.getValue();
            if (Type.REAL.equals(varTypeInfo) || Type.DEFERRED.equals(varTypeInfo)) {
                activeOutput.append("dconst_0\n"); emitStore("dstore", slot);
            } else if (varTypeInfo != null && varTypeInfo.isVector()) {
                activeOutput.append("new java/util/ArrayList\n");
                activeOutput.append("dup\n");
                activeOutput.append("invokespecial java/util/ArrayList/<init>()V\n");
                emitStore("astore", slot);
            } else if (varTypeInfo != null && varTypeInfo.isMap()) {
                activeOutput.append("new java/util/LinkedHashMap\n");
                activeOutput.append("dup\n");
                activeOutput.append("invokespecial java/util/LinkedHashMap/<init>()V\n");
                emitStore("astore", slot);
            } else if (varTypeInfo != null && varTypeInfo.isSet()) {
                activeOutput.append("new java/util/LinkedHashSet\n");
                activeOutput.append("dup\n");
                activeOutput.append("invokespecial java/util/LinkedHashSet/<init>()V\n");
                emitStore("astore", slot);
            } else if (Type.STRING.equals(varTypeInfo)) {
                activeOutput.append("ldc \"\"\n"); emitStore("astore", slot);
            } else if (varTypeInfo != null && varTypeInfo.isRef()) {
                activeOutput.append("aconst_null\n"); emitStore("astore", slot);
            } else if (varTypeInfo != null && varTypeInfo.isProcedure()) {
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
            if ("real".equals(info.returnType) || "deferred".equals(info.returnType)) {
                activeOutput.append("dconst_0\n"); emitStore("dstore", procRetvalSlot);
            } else if ("string".equals(info.returnType)) {
                activeOutput.append("ldc \"\"\n"); emitStore("astore", procRetvalSlot);
            } else if (isObjectType(info.returnType)) {
                activeOutput.append("aconst_null\n"); emitStore("astore", procRetvalSlot);
            } else {
                activeOutput.append("iconst_0\n"); emitStore("istore", procRetvalSlot);
            }
            if (useEnvBridge(procName)) {
                String rDesc = CodeGenUtils.getReturnTypeDescriptor(info.returnType);
                if ("real".equals(info.returnType) || "deferred".equals(info.returnType)) {
                    activeOutput.append("dload ").append(procRetvalSlot).append("\n");
                } else if (isObjectType(info.returnType)) {
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
                } else if (isObjectType(localType)) {
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
    public void exitProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
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
                if (pType.endsWith("[]")) continue;
                if (info.valueParams.contains(p)) {
                    if ("real".equals(pType) || "deferred".equals(pType)) {
                        activeOutput.append("dload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" D\n");
                    } else if (isObjectType(pType)) {
                        activeOutput.append("aload ").append(saveSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(procName, p)).append(" ")
                                    .append(CodeGenUtils.getReturnTypeDescriptor(pType)).append("\n");
                    } else if (pType.startsWith("procedure:")) {
                        String pDesc = getProcedureInterfaceDescriptor(pType);
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
            String rDesc = CodeGenUtils.getReturnTypeDescriptor(info.returnType);
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(envReturnFieldName(procName)).append(" ").append(rDesc).append("\n");
            if ("real".equals(info.returnType) || "deferred".equals(info.returnType)) emitStore("dstore", procRetvalSlot);
            else if (isObjectType(info.returnType)) emitStore("astore", procRetvalSlot);
            else emitStore("istore", procRetvalSlot);

            if (currentEnvRetSaveSlot >= 0) {
                if ("real".equals(info.returnType) || "deferred".equals(info.returnType)) {
                    activeOutput.append("dload ").append(currentEnvRetSaveSlot).append("\n");
                } else if (isObjectType(info.returnType)) {
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
                } else if (isObjectType(localType)) {
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
        } else if ("real".equals(currentProcReturnType) || "deferred".equals(currentProcReturnType)) {
            activeOutput.append("dload ").append(procRetvalSlot).append("\n")
                        .append("dreturn\n");
        } else if (isObjectType(currentProcReturnType)) {
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
        currentSymbolTableTypes = mainSymbolTableTypes;
        currentLocalIndex     = mainLocalIndex;
        currentNumLocals      = mainNumLocals;
        currentArrayBounds    = mainArrayBounds;
        currentArrayBoundPairs = mainArrayBoundPairs;
        activeOutput          = procBufferStack.isEmpty() ? mainCode : procBufferStack.peek();
        mainSymbolTable       = savedOuterSTStack.pop();
        mainSymbolTableTypes  = savedOuterTypedSTStack.pop();
        mainLocalIndex        = savedOuterLIStack.pop();
        mainNumLocals         = savedOuterNLStack.pop();
        mainArrayBounds       = savedOuterABStack.pop();
        mainArrayBoundPairs   = savedOuterABPairsStack.pop();
        currentProcName       = savedProcNameStack.pop();
        currentProcReturnType = savedProcRetTypeStack.pop();
        procRetvalSlot        = savedProcRetSlotStack.pop();
        currentEnvParamSlots  = savedEnvParamSlotsStack.pop();
        currentEnvLocalSlots  = savedEnvLocalSlotsStack.pop();
        currentEnvRetSaveSlot = savedEnvRetSaveSlotStack.pop();
        currentNestedSelfThunkSlots = savedNestedSelfThunkSlotsStack.pop();
        currentArrayParamBoundSlots = savedArrayParamBoundSlotsStack.pop();
    }

    // -------------------------------------------------------------------------
    // Assignments
    // -------------------------------------------------------------------------

    @Override
    public void exitAssignment(PerseusParser.AssignmentContext ctx) {
        if (skippingCodegen()) return;
        if (currentProcName == null) mainHadExecutableStatements = true;
        List<PerseusParser.LvalueContext> lvalues = ctx.lvalue();
        Type rhsTypeInfo = exprTypeInfo(ctx.expr(), Type.INTEGER);

        // Array element assignment (single dest with subscript)
        if (lvalues.size() == 1 && !lvalues.get(0).expr().isEmpty()) {
            PerseusParser.LvalueContext lv = lvalues.get(0);
            String arrName = lv.identifier().getText();
            Type targetTypeInfo = lookupVarTypeInfo(arrName);
            if (targetTypeInfo == null) {
                activeOutput.append("; ERROR: undeclared array ").append(arrName).append("\n");
                return;
            }

            if (targetTypeInfo.isVector()) {
                Type vectorElementType = targetTypeInfo.elementType();
                if (lv.expr().size() != 1) {
                    activeOutput.append("; ERROR: vector indexing currently requires exactly one subscript\n");
                    return;
                }
                activeOutput.append(generateLoadVar(arrName));
                activeOutput.append(generateExpr(lv.expr(0)));
                activeOutput.append(generateExpr(ctx.expr()));
                activeOutput.append(boxVectorElementValue(vectorElementType.toLegacyString(), rhsTypeInfo.toLegacyString()));
                activeOutput.append("invokeinterface java/util/List/set(ILjava/lang/Object;)Ljava/lang/Object; 3\n");
                activeOutput.append("pop\n");
                return;
            }
            if (targetTypeInfo.isMap()) {
                if (lv.expr().size() != 1) {
                    activeOutput.append("; ERROR: map assignment currently requires exactly one subscript\n");
                    return;
                }
                activeOutput.append(generateLoadVar(arrName));
                activeOutput.append(generateExpr(lv.expr(0)));
                activeOutput.append(boxMapComponentValue(targetTypeInfo.keyType().toLegacyString(),
                        exprTypeInfo(lv.expr(0), Type.INTEGER).toLegacyString()));
                activeOutput.append(generateExpr(ctx.expr()));
                activeOutput.append(boxMapComponentValue(targetTypeInfo.valueType().toLegacyString(), rhsTypeInfo.toLegacyString()));
                activeOutput.append("invokeinterface java/util/Map/put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; 3\n");
                activeOutput.append("pop\n");
                return;
            }

            // String scalar character mutation: s[i] := replacement
            // Rebuilds the string using StringBuilder: prefix + replacement + suffix
            if (Type.STRING.equals(targetTypeInfo) && lv.expr().size() == 1) {
                activeOutput
                    .append("new java/lang/StringBuilder\n")
                    .append("dup\n")
                    .append("invokespecial java/lang/StringBuilder/<init>()V\n")
                    // prefix: s.substring(0, i-1)
                    .append(generateLoadVar(arrName))
                    .append("iconst_0\n")
                    .append(generateExpr(lv.expr(0)))
                    .append("iconst_1\n").append("isub\n")
                    .append("invokevirtual java/lang/String/substring(II)Ljava/lang/String;\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    // replacement (RHS expression must be a string)
                    .append(generateExpr(ctx.expr()))
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    // suffix: s.substring(i)
                    .append(generateLoadVar(arrName))
                    .append(generateExpr(lv.expr(0)))
                    .append("invokevirtual java/lang/String/substring(I)Ljava/lang/String;\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;\n");
                // store result back to s (local or static)
                Integer strIdx = currentLocalIndex.get(arrName);
                if (strIdx != null) {
                    activeOutput.append("astore ").append(strIdx).append("\n");
                } else {
                    if (lookupSymbolTypeInfo(arrName) != null) {
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(arrName).append(" Ljava/lang/String;\n");
                    } else {
                        activeOutput.append("; ERROR: undefined variable ").append(arrName).append("\n");
                    }
                }
                return;
            }
            activeOutput.append(generateLoadVar(arrName));
            activeOutput.append(generateArrayElementIndex(arrName, lv.expr(), null));
            activeOutput.append(generateExpr(ctx.expr())); // value
            Type elementTypeInfo = targetTypeInfo.isArray() ? targetTypeInfo.elementType() : null;
            if (Type.REAL.equals(elementTypeInfo) && Type.INTEGER.equals(rhsTypeInfo)) {
                activeOutput.append("i2d\n");
            }
            boolean refArray = elementTypeInfo != null && elementTypeInfo.isRef();
            if (refArray) {
                if (Type.INTEGER.equals(rhsTypeInfo)) {
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                } else if (Type.REAL.equals(rhsTypeInfo)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else if (Type.BOOLEAN.equals(rhsTypeInfo)) {
                    activeOutput.append("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n");
                }
            }
            activeOutput.append(Type.REAL.equals(elementTypeInfo) ? "dastore\n"
                    : Type.BOOLEAN.equals(elementTypeInfo) ? "bastore\n"
                    : (usesObjectStorage(elementTypeInfo) || refArray) ? "aastore\n"
                    : "iastore\n");
            return;
        }

        // Scalar (possibly chained) assignment
        boolean rhsIsProcedureRef = isProcedureReferenceExpr(ctx.expr());

        // Determine storage type: real if any destination is real, string if any destination is string
        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            Type returnTargetType = procedureReturnTargetTypeInfo(lvName, rhsIsProcedureRef);
            if (returnTargetType != null) return Type.REAL.equals(returnTargetType);
            Type vt = lookupVarTypeInfo(lvName);
            vt = vt != null ? vt.unwrapThunk() : null;
            return Type.REAL.equals(vt);
        });
        boolean anyString = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            Type returnTargetType = procedureReturnTargetTypeInfo(lvName, rhsIsProcedureRef);
            if (returnTargetType != null) return Type.STRING.equals(returnTargetType);
            Type vt = lookupVarTypeInfo(lvName);
            vt = vt != null ? vt.unwrapThunk() : null;
            return Type.STRING.equals(vt);
        });
        boolean anyRef = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            Type vt = lookupVarTypeInfo(lvName);
            vt = vt != null ? vt.unwrapThunk() : null;
            return usesObjectStorage(vt) && !isProcedureVariableTarget(lvName, rhsIsProcedureRef);
        });
        // A typed procedure name can denote either a procedure reference or the
        // procedure's implicit return variable. When the RHS is a procedure
        // reference, keep the existing procedure-variable behavior. Otherwise,
        // treat assignments to the current/enclosing typed procedure names as
        // return-value writes.
        boolean anyProcedure = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            if (isProcedureVariableTarget(lvName, rhsIsProcedureRef)) {
                return true;
            }
            Type returnTargetType = procedureReturnTargetTypeInfo(lvName, rhsIsProcedureRef);
            return returnTargetType != null && returnTargetType.isProcedure();
        });
        Type storeTypeInfo = anyProcedure ? Type.procedure(Type.VOID)
                : anyReal ? Type.REAL
                : anyString ? Type.STRING
                : anyRef ? Type.ref("java/lang/Object")
                : Type.INTEGER;

        // Generate expression and widen if needed
        activeOutput.append(generateExpr(ctx.expr()));
        if (Type.REAL.equals(storeTypeInfo) && Type.INTEGER.equals(rhsTypeInfo)) {
            activeOutput.append("i2d\n");
        }

        // If multiple destinations, store the computed value into a temp slot so we can
        // reload it safely (avoids dup/dup2 stack-splitting issues).
        boolean useTemp = lvalues.size() > 1;
        int tempSlot = -1;
        if (useTemp) {
            tempSlot = allocateNewLocal("tmp");
            emitTypedStore(storeTypeInfo, tempSlot);
        }

        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();
            if (useTemp) {
                emitTypedLoad(storeTypeInfo, tempSlot);
            }

            // Procedure return value assignment - but ONLY if this procedure actually has a return value slot.
            // For void procedures (including those used as procedure variables), assignment to the
            // procedure name should be treated as a procedure-variable assignment, not a return value.
            if (name.equals(currentProcName) && isProcedureReturnTarget(name, rhsIsProcedureRef)) {
                Type currentProcReturnTypeInfo = currentProcReturnType != null ? Type.parse(currentProcReturnType) : null;
                if (usesRealStorage(currentProcReturnTypeInfo)) {
                    emitStore("dstore", procRetvalSlot);
                    if (useEnvBridge(currentProcName)) {
                        activeOutput.append("dload ").append(procRetvalSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envReturnFieldName(currentProcName)).append(" D\n");
                    }
                } else if (usesObjectStorage(currentProcReturnTypeInfo)) {
                    emitStore("astore", procRetvalSlot);
                    if (useEnvBridge(currentProcName)) {
                        activeOutput.append("aload ").append(procRetvalSlot).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envReturnFieldName(currentProcName)).append(" ")
                                    .append(CodeGenUtils.getReturnTypeDescriptor(currentProcReturnTypeInfo)).append("\n");
                    }
                } else {
                    if (Type.REAL.equals(rhsTypeInfo)) {
                        activeOutput.append("d2i\n");
                    }
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
                Type outerReturnTypeInfo = outerProc.returnTypeInfo != null ? outerProc.returnTypeInfo : Type.parse(outerProc.returnType);
                if (usesRealStorage(outerReturnTypeInfo)) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" D\n");
                } else if (usesObjectStorage(outerReturnTypeInfo)) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" ")
                                .append(CodeGenUtils.getReturnTypeDescriptor(outerReturnTypeInfo)).append("\n");
                } else {
                    if (Type.REAL.equals(rhsTypeInfo)) {
                        activeOutput.append("d2i\n");
                    }
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(envReturnFieldName(name)).append(" I\n");
                }
                continue;
            }

            Integer idx = currentLocalIndex.get(name);
            if (idx == null && mainLocalIndex != null) idx = mainLocalIndex.get(name);
            Type varTypeInfo = currentSymbolTableTypes != null ? currentSymbolTableTypes.get(name) : null;
            boolean resolvedFromRootMain = false;
            if (varTypeInfo == null && mainSymbolTableTypes != null) {
                varTypeInfo = mainSymbolTableTypes.get(name);
            }
            if (idx == null && rootMainLocalIndex != null && rootMainLocalIndex.containsKey(name)) {
                idx = rootMainLocalIndex.get(name);
                resolvedFromRootMain = true;
            }
            if (varTypeInfo == null && rootMainSymbolTableTypes != null) {
                varTypeInfo = rootMainSymbolTableTypes.get(name);
                if (varTypeInfo != null) {
                    resolvedFromRootMain = true;
                }
            }
            String varType = varTypeInfo != null ? varTypeInfo.toLegacyString() : null;
            System.out.println("DEBUG: assignment target '" + name + "' resolvedIdx=" + idx + " varType=" + varType + " currentProc=" + currentProcName);
            boolean isCallByNameParam = false;
            if (currentProcName != null) {
                SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                if (currInfo != null && currInfo.paramNames.contains(name) && !currInfo.valueParams.contains(name)) {
                    isCallByNameParam = true;
                }
            }
            boolean isThunk = (varTypeInfo != null && varTypeInfo.isThunk()) || isCallByNameParam;
            boolean isProcVar = varTypeInfo != null && varTypeInfo.isProcedure();
                // If this variable resolves to an outer-scope local (present in mainLocalIndex
                // but not in currentLocalIndex), access it via class static field instead.
                if (idx != null && !currentLocalIndex.containsKey(name)) {
                    if (varTypeInfo != null && varTypeInfo.isProcedure()) {
                        String pdesc = getProcedureInterfaceDescriptor(varTypeInfo);
                        String targetName = resolveOuterFieldName(name, varType, resolvedFromRootMain);
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(pdesc).append("\n");
                    } else if (varTypeInfo != null && varTypeInfo.isArray()) {
                        String ad = arrayTypeToJvmDesc(varTypeInfo);
                        String targetName = resolveOuterFieldName(name, varType, resolvedFromRootMain);
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(ad).append("\n");
                    } else {
                        String sd = scalarTypeToJvmDesc(varTypeInfo != null ? varTypeInfo : Type.INTEGER);
                        String targetName = resolveOuterFieldName(name, varType, resolvedFromRootMain);
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(sd).append("\n");
                    }
                    continue;
                }
            if (idx == null && !isThunk && !isProcVar) {
                // Check if this is a static scalar
                if (varTypeInfo != null && !varTypeInfo.isArray() && !varTypeInfo.isProcedure() && !varTypeInfo.isThunk()) {
                    // Static scalar: emit putstatic (use env bridge name when available)
                    String jvmDesc = scalarTypeToJvmDesc(varTypeInfo);
                    String targetName = resolveOuterFieldName(name, varType, resolvedFromRootMain);
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(targetName).append(" ").append(jvmDesc).append("\n");
                    continue;
                }
                // Check main symbol table for outer scope static scalars
                if (mainSymbolTableTypes != null) {
                    Type mainType = mainSymbolTableTypes.get(name);
                    if (mainType != null && !mainType.isArray() && !mainType.isProcedure() && !mainType.isThunk()) {
                        // Static scalar from outer scope: emit putstatic (use env bridge name when available)
                        String jvmDesc = scalarTypeToJvmDesc(mainType);
                        String targetName = resolveOuterFieldName(name, mainType.toLegacyString(), resolvedFromRootMain);
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(targetName).append(" ").append(jvmDesc).append("\n");
                        continue;
                    }
                }
                activeOutput.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }


            if (isThunk) {
                if (varTypeInfo != null && varTypeInfo.isThunk() && Type.DEFERRED.equals(varTypeInfo.elementType())) {
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
                if (Type.REAL.equals(storeTypeInfo)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else if (!usesObjectStorage(storeTypeInfo)) {
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
            if (usesIntegerStorage(varTypeInfo)) {
                emitStore("istore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("iload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" I\n");
                    }
                }
            } else if (usesRealStorage(varTypeInfo)) {
                emitStore("dstore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("dload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" D\n");
                    }
                }
            } else if (isProcVar) {
                // Procedure variables are stored in a static field so they are shared across activations.
                String pdesc = getProcedureInterfaceDescriptor(varTypeInfo);
                boolean storeToLocal = currentLocalIndex.containsKey(name);

                if (storeToLocal) {
                    // Store in local slot first so we can also store the same value to the static field.
                    emitStore("astore", idx);
                    // Reload for storing into the static field.
                    activeOutput.append("aload ").append(idx).append("\n");
                }

                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(staticFieldName(name, varType)).append(" ").append(pdesc).append("\n");
            } else if (usesObjectStorage(varTypeInfo)) {
                emitStore("astore", idx);
                if (currentProcName != null && currentLocalIndex.containsKey(name)) {
                    SymbolTableBuilder.ProcInfo currInfo = procedures.get(currentProcName);
                    if (procedureNeedsLocalBridge(currInfo) && currInfo.localVars.containsKey(name) && !currInfo.ownVars.contains(name)) {
                        activeOutput.append("aload ").append(idx).append("\n");
                        activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                    .append("/").append(envThunkFieldName(currentProcName, name)).append(" ")
                                    .append(scalarTypeToJvmDesc(varTypeInfo)).append("\n");
                    }
                }
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
    private String getChannelStream(PerseusParser.ArgContext channelArg) {
        if (channelArg == null || channelArg.expr() == null) {
            activeOutput.append("; WARNING: missing channel parameter, defaulting to System.out\n");
            return "java/lang/System/out";
        }
        
        // Try to evaluate as a constant integer literal
        PerseusParser.ExprContext expr = channelArg.expr();
        if (expr instanceof PerseusParser.IntLiteralExprContext) {
            PerseusParser.IntLiteralExprContext intExpr = (PerseusParser.IntLiteralExprContext) expr;
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
    public void exitProcedureCall(PerseusParser.ProcedureCallContext ctx) {
        if (skippingCodegen()) return;
        if (currentProcName == null && !emittingAnonymousBody) mainHadExecutableStatements = true;
        String name = ctx.identifier().getText();
        System.out.println("Processing procedure call: " + name);
        List<PerseusParser.ArgContext> args = ctx.argList() != null ? ctx.argList().arg() : List.of();
        if (channelGen.tryEmitProcedureCall(name, args, activeOutput, currentLocalIndex, currentSymbolTable,
                mainSymbolTable, this::generateExpr, e -> exprTypes.getOrDefault(e, "integer"), this::allocateNewLocal, this::getChannelStream,
                this::lookupVarType, this::staticFieldName, this::generateOpenStringTargetThunk)) {
            return;
        } else if ("outchar".equals(name)) {
            if (args.size() > 0 && args.get(0).expr() != null) {
                activeOutput.append(generateExpr(args.get(0).expr()));
            } else {
                activeOutput.append("iconst_1\n");
            }
            activeOutput.append(generateExpr(args.get(1).expr()))
                        .append(generateExpr(args.get(2).expr()))
                        .append("invokestatic perseus/io/TextOutput/outchar(ILjava/lang/String;I)V\n");
        } else if ("ininteger".equals(name)) {
            PerseusParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof PerseusParser.VarExprContext) {
                String varName = ((PerseusParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                Type varTypeInfo = lookupVarTypeInfo(varName);
                activeOutput.append(generateExpr(args.get(0).expr()))
                            .append("invokestatic perseus/io/TextInput/ininteger(I)I\n");
                if (varSlot == null && varTypeInfo != null && !varTypeInfo.isArray() && !varTypeInfo.isProcedure() && !varTypeInfo.isThunk()) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" I\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("istore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: ininteger requires a variable as second argument\n");
            }
        } else if ("inreal".equals(name)) {
            PerseusParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof PerseusParser.VarExprContext) {
                String varName = ((PerseusParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                Type varTypeInfo = lookupVarTypeInfo(varName);
                activeOutput.append(generateExpr(args.get(0).expr()))
                            .append("invokestatic perseus/io/TextInput/inreal(I)D\n");
                if (varSlot == null && varTypeInfo != null && !varTypeInfo.isArray() && !varTypeInfo.isProcedure() && !varTypeInfo.isThunk()) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" D\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("dstore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: inreal requires a variable as second argument\n");
            }
        } else if ("inchar".equals(name)) {
            PerseusParser.ExprContext varExpr = args.get(2).expr();
            if (varExpr instanceof PerseusParser.VarExprContext) {
                String varName = ((PerseusParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                Type varTypeInfo = lookupVarTypeInfo(varName);
                activeOutput.append(generateExpr(args.get(0).expr()))
                            .append(generateExpr(args.get(1).expr()))
                            .append("invokestatic perseus/io/TextInput/inchar(ILjava/lang/String;)I\n");
                if (varSlot == null && varTypeInfo != null && !varTypeInfo.isArray() && !varTypeInfo.isProcedure() && !varTypeInfo.isThunk()) {
                    activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                                .append("/").append(varName).append(" I\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("istore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: inchar requires a variable as third argument\n");
            }
        } else if ("stop".equals(name)) {
            // stop - terminates the program normally
            activeOutput.append("iconst_0\n")
                        .append("invokestatic java/lang/System/exit(I)V\n");
        } else if ("fault".equals(name)) {
            activeOutput.append(generateExpr(args.get(0).expr()));
            activeOutput.append(generateExpr(args.get(1).expr()));
            if (Type.INTEGER.equals(exprTypeInfo(args.get(1).expr(), Type.INTEGER))) {
                activeOutput.append("i2d\n");
            }
            activeOutput.append("invokestatic perseus/runtime/Faults/fault(Ljava/lang/String;D)V\n");
        } else {
            // Check if it's a call through a procedure variable (local or outer scope).
            Type varTypeInfo = lookupVarTypeInfo(name);
            String varType = varTypeInfo != null ? varTypeInfo.toLegacyString() : null;
            SymbolTableBuilder.ProcInfo declaredProc = procedures.get(name);
            boolean preferDirectProcedureCall = declaredProc != null && !"void".equals(declaredProc.returnType);
            boolean isProcVar = varTypeInfo != null && varTypeInfo.isProcedure()
                    && (currentLocalIndex.containsKey(name) || procVarSlots.containsKey(name));
            if (isProcVar && !preferDirectProcedureCall) {
                // Call through a procedure variable (local slot or static field)
                activeOutput.append(generateProcedureVariableCall(name, varTypeInfo, args));
                // Procedure-variable calls return a value (unless void); in statement position,
                // the return value should be discarded to keep the stack balanced.
                Type procRet = varTypeInfo.elementType();
                if (!Type.VOID.equals(procRet)) {
                    if (Type.REAL.equals(procRet)) {
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

    @Override
    public void exitMemberCall(PerseusParser.MemberCallContext ctx) {
        if (skippingCodegen()) return;
        if (currentProcName == null && !emittingAnonymousBody) mainHadExecutableStatements = true;
        activeOutput.append(generateMemberInvocation(
                ctx.identifier(0).getText(),
                ctx.identifier(1).getText(),
                ctx.argList() != null ? ctx.argList().arg() : List.of(),
                ctx.argList() != null,
                true));
    }

    // -------------------------------------------------------------------------
    // Labels and goto
    // -------------------------------------------------------------------------

    @Override
    public void enterLabel(PerseusParser.LabelContext ctx) {
        if (skippingCodegen()) return;
        String labelName = ctx.getStart().getText();
        activeOutput.append(normalizeStatementLabel(labelName)).append(":\n");
    }

    @Override
    public void exitSwitchDecl(PerseusParser.SwitchDeclContext ctx) {
        // Switch declarations are metadata for designational goto codegen.
    }

    @Override
    public void exitGotoStatement(PerseusParser.GotoStatementContext ctx) {
        if (skippingCodegen()) return;
        emitGotoDesignationalExpr(ctx.designationalExpr());
    }

    @Override
    public void exitSignalStatement(PerseusParser.SignalStatementContext ctx) {
        if (skippingCodegen()) return;
        activeOutput.append(generateExpr(ctx.expr()));
        activeOutput.append("athrow\n");
    }

    @Override
    public void enterWhileStatement(PerseusParser.WhileStatementContext ctx) {
        if (skippingCodegen()) return;
        String condLabel = generateUniqueLabel("whilecond");
        String endLabel = generateUniqueLabel("endwhile");
        loopBackEdgeLabelStack.push(condLabel);
        loopContinueLabelStack.push(condLabel);
        loopBreakLabelStack.push(endLabel);
        activeOutput.append(condLabel).append(":\n");
        activeOutput.append(generateExpr(ctx.expr()));
        activeOutput.append("ifeq ").append(endLabel).append("\n");
    }

    @Override
    public void exitWhileStatement(PerseusParser.WhileStatementContext ctx) {
        if (skippingCodegen()) return;
        if (loopBackEdgeLabelStack.isEmpty() || loopContinueLabelStack.isEmpty() || loopBreakLabelStack.isEmpty()) {
            activeOutput.append("; ERROR: while-label stack underflow\n");
            return;
        }
        String backEdgeLabel = loopBackEdgeLabelStack.pop();
        loopContinueLabelStack.pop();
        String endLabel = loopBreakLabelStack.pop();
        activeOutput.append("goto ").append(backEdgeLabel).append("\n");
        activeOutput.append(endLabel).append(":\n");
    }

    @Override
    public void enterRepeatStatement(PerseusParser.RepeatStatementContext ctx) {
        if (skippingCodegen()) return;
        String bodyLabel = generateUniqueLabel("repeat");
        String condLabel = generateUniqueLabel("repeatcond");
        String endLabel = generateUniqueLabel("endrepeat");
        repeatBodyLabelStack.push(bodyLabel);
        loopBackEdgeLabelStack.push(bodyLabel);
        loopContinueLabelStack.push(condLabel);
        loopBreakLabelStack.push(endLabel);
        activeOutput.append(bodyLabel).append(":\n");
    }

    @Override
    public void exitRepeatStatement(PerseusParser.RepeatStatementContext ctx) {
        if (skippingCodegen()) return;
        if (repeatBodyLabelStack.isEmpty() || loopBackEdgeLabelStack.isEmpty()
                || loopContinueLabelStack.isEmpty() || loopBreakLabelStack.isEmpty()) {
            activeOutput.append("; ERROR: repeat-label stack underflow\n");
            return;
        }
        String bodyLabel = repeatBodyLabelStack.pop();
        loopBackEdgeLabelStack.pop();
        String condLabel = loopContinueLabelStack.pop();
        String endLabel = loopBreakLabelStack.pop();
        activeOutput.append(condLabel).append(":\n");
        activeOutput.append(generateExpr(ctx.expr()));
        activeOutput.append("ifeq ").append(bodyLabel).append("\n");
        activeOutput.append(endLabel).append(":\n");
    }

    @Override
    public void exitBreakStatement(PerseusParser.BreakStatementContext ctx) {
        if (skippingCodegen()) return;
        if (loopBreakLabelStack.isEmpty()) {
            activeOutput.append("; ERROR: break used outside loop\n");
            return;
        }
        activeOutput.append("goto ").append(loopBreakLabelStack.peek()).append("\n");
    }

    @Override
    public void exitContinueStatement(PerseusParser.ContinueStatementContext ctx) {
        if (skippingCodegen()) return;
        if (loopContinueLabelStack.isEmpty()) {
            activeOutput.append("; ERROR: continue used outside loop\n");
            return;
        }
        activeOutput.append("goto ").append(loopContinueLabelStack.peek()).append("\n");
    }

    // -------------------------------------------------------------------------
    // if / then / else
    // -------------------------------------------------------------------------

    @Override
    public void enterStatement(PerseusParser.StatementContext ctx) {
        if (skippingCodegen()) return;
        if (ctx.getParent() instanceof PerseusParser.IfStatementContext ifCtx
                && ifCtx.statement().size() > 1
                && ctx == ifCtx.statement(1)) {
            String endLabel  = ifEndLabelStack.peek();
            String elseLabel = ifElseLabelStack.peek();
            activeOutput.append("goto ").append(endLabel).append("\n");
            activeOutput.append(elseLabel).append(":\n");
        }
    }

    @Override
    public void enterBlock(PerseusParser.BlockContext ctx) {
        if (skippingCodegen()) return;
        exceptionGen.enterBlock(ctx, activeOutput);
    }

    @Override
    public void exitBlock(PerseusParser.BlockContext ctx) {
        if (skippingCodegen()) return;
        exceptionGen.exitBlock(ctx, activeOutput);
    }

    @Override
    public void enterCompoundStatement(PerseusParser.CompoundStatementContext ctx) {
        // Compound statements are just containers for statements - no special handling needed
    }

    @Override
    public void exitCompoundStatement(PerseusParser.CompoundStatementContext ctx) {
        if (skippingCodegen()) return;
        exceptionGen.exitCompoundStatement(ctx, activeOutput);
    }

    @Override
    public void enterExceptionHandler(PerseusParser.ExceptionHandlerContext ctx) {
        if (skippingCodegen()) return;
        exceptionGen.enterExceptionHandler(ctx, activeOutput);
        if (ctx.identifier() == null) {
            exceptionBindingStateStack.push(NO_EXCEPTION_BINDING);
            activeOutput.append("pop\n");
            return;
        }

        String boundName = ctx.identifier().getText();
        int priorNumLocals = currentNumLocals;
        Integer priorLocalSlot = currentLocalIndex.get(boundName);
        String priorType = currentSymbolTable.get(boundName);
        Type priorTypeInfo = currentSymbolTableTypes != null ? currentSymbolTableTypes.get(boundName) : null;
        int boundSlot = allocateNewLocal("exception");
        currentLocalIndex.put(boundName, boundSlot);
        Type boundTypeInfo = Type.parse(exceptionPatternRefType(ctx.exceptionPattern()));
        currentSymbolTable.put(boundName, boundTypeInfo.toLegacyString());
        if (currentSymbolTableTypes != null) {
            currentSymbolTableTypes.put(boundName, boundTypeInfo);
        }
        exceptionBindingStateStack.push(new ExceptionBindingState(
                boundName, priorLocalSlot, priorType, priorTypeInfo, priorNumLocals));
        activeOutput.append("astore ").append(boundSlot).append("\n");
    }

    @Override
    public void exitExceptionHandler(PerseusParser.ExceptionHandlerContext ctx) {
        if (skippingCodegen()) return;
        exceptionGen.exitExceptionHandler(ctx, activeOutput);
        if (exceptionBindingStateStack.isEmpty()) return;
        ExceptionBindingState state = exceptionBindingStateStack.pop();
        if (state == NO_EXCEPTION_BINDING) return;

        currentNumLocals = state.priorNumLocals;
        ensureLocalLimit(Math.max(currentNumLocals, 64));
        currentLocalIndex.remove(state.name);
        if (state.priorLocalSlot != null) {
            currentLocalIndex.put(state.name, state.priorLocalSlot);
        }
        currentSymbolTable.remove(state.name);
        if (currentSymbolTableTypes != null) {
            currentSymbolTableTypes.remove(state.name);
        }
        if (state.priorType != null) {
            currentSymbolTable.put(state.name, state.priorType);
        }
        if (state.priorTypeInfo != null && currentSymbolTableTypes != null) {
            currentSymbolTableTypes.put(state.name, state.priorTypeInfo);
        }
    }

    @Override
    public void enterIfStatement(PerseusParser.IfStatementContext ctx) {
        if (skippingCodegen()) return;
        PerseusParser.ExprContext cond = ctx.expr();
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

        if (cond instanceof PerseusParser.RelExprContext rel) {
            String leftCode  = generateExpr(rel.expr(0));
            String rightCode = generateExpr(rel.expr(1));
            String leftType  = exprTypes.getOrDefault(rel.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(rel.expr(1), "integer");
            String op = rel.op.getText();
            activeOutput.append(leftCode);
            activeOutput.append(rightCode);
            if (isReferenceComparison(leftType, rightType)) {
                String cmpInstr = "=".equals(op) ? "if_acmpeq" : "if_acmpne";
                activeOutput.append(cmpInstr).append(" ").append(thenLabel).append("\n");
            } else if ("real".equals(leftType) || "real".equals(rightType)) {
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
    public void exitIfStatement(PerseusParser.IfStatementContext ctx) {
        if (skippingCodegen()) return;
        String endLabel = ifEndLabelStack.pop();
        ifElseLabelStack.pop();
        activeOutput.append(endLabel).append(":\n");
    }

    // -------------------------------------------------------------------------
    // for loops
    // -------------------------------------------------------------------------

    @Override
    public void enterForStatement(PerseusParser.ForStatementContext ctx) {
        if (skippingCodegen()) return;
        // Redirect all body code to a capture buffer; exitForStatement will
        // reconstruct the complete for-list structure with inline body duplication.
        forBodyStack.push(activeOutput);
        activeOutput = new StringBuilder();
    }

    @Override
    public void exitForStatement(PerseusParser.ForStatementContext ctx) {
        if (skippingCodegen()) return;
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
        if (ctx.forClause() instanceof PerseusParser.InArrayForClauseContext inClause) {
            emitForInIterableLoop(inClause, bodyCode, varName, varIndex, varType, isStaticScalar, afterAllLabel);
            activeOutput.append(afterAllLabel).append(":\n");
            return;
        }

        PerseusParser.TraditionalForClauseContext traditionalClause =
                (PerseusParser.TraditionalForClauseContext) ctx.forClause();
        for (PerseusParser.ForElementContext elem : traditionalClause.forList().forElement()) {
            if (elem instanceof PerseusParser.StepUntilElementContext e) {
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

            } else if (elem instanceof PerseusParser.WhileElementContext e) {
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
                PerseusParser.SimpleElementContext e = (PerseusParser.SimpleElementContext) elem;
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

    private void emitForInIterableLoop(PerseusParser.InArrayForClauseContext inClause, String bodyCode,
            String varName, Integer varIndex, String varType, boolean isStaticScalar, String afterAllLabel) {
        String iterableType = inferIterableType(inClause.expr());
        boolean javaIterable = isJavaIterableReferenceType(iterableType);
        boolean typedIterable = iterableType != null && iterableType.startsWith("iterable:");
        boolean setIterable = iterableType != null && iterableType.startsWith("set:");
        if (iterableType == null || (!iterableType.endsWith("[]") && !iterableType.startsWith("vector:") && !setIterable && !typedIterable && !javaIterable)) {
            activeOutput.append("; ERROR: for ... in ... do currently requires an array, vector, set, typed iterable, or Java Iterable value\n");
            return;
        }

        String elementType = javaIterable
                ? effectiveIterationVariableType(varType)
                : typedIterable
                ? iterableType.substring("iterable:".length())
                : setIterable
                ? iterableType.substring("set:".length())
                : iterableType.startsWith("vector:")
                ? iterableType.substring("vector:".length())
                : iterableType.substring(0, iterableType.length() - 2);
        if (!javaIterable && !isCompatibleIterationVariableType(varType, elementType)) {
            activeOutput.append("; ERROR: iteration variable ").append(varName)
                    .append(" has incompatible type ").append(varType)
                    .append(" for iterable element type ").append(elementType).append("\n");
            return;
        }

        int iterableSlot = allocateNewLocal("iterable");
        String loopLabel = generateUniqueLabel("loop");

        activeOutput.append(generateExpr(inClause.expr()));
        activeOutput.append("astore ").append(iterableSlot).append("\n");

        if (javaIterable || setIterable || typedIterable) {
            int iteratorSlot = allocateNewLocal("iterator");
            activeOutput.append("aload ").append(iterableSlot).append("\n");
            activeOutput.append("checkcast java/lang/Iterable\n");
            activeOutput.append("invokeinterface java/lang/Iterable/iterator()Ljava/util/Iterator; 1\n");
            activeOutput.append("astore ").append(iteratorSlot).append("\n");
            activeOutput.append(loopLabel).append(":\n");
            activeOutput.append("aload ").append(iteratorSlot).append("\n");
            activeOutput.append("invokeinterface java/util/Iterator/hasNext()Z 1\n");
            activeOutput.append("ifeq ").append(afterAllLabel).append("\n");
            activeOutput.append("aload ").append(iteratorSlot).append("\n");
            activeOutput.append("invokeinterface java/util/Iterator/next()Ljava/lang/Object; 1\n");
            activeOutput.append(unboxJavaIterableElementValue(elementType));
            activeOutput.append(storeIterationVariableValue(varName, varType, varIndex, isStaticScalar, elementType));
            activeOutput.append(bodyCode);
            activeOutput.append("goto ").append(loopLabel).append("\n");
            return;
        }

        int indexSlot = allocateNewLocal("iterIndex");
        activeOutput.append("ldc 0\n");
        activeOutput.append("istore ").append(indexSlot).append("\n");
        activeOutput.append(loopLabel).append(":\n");
        activeOutput.append("iload ").append(indexSlot).append("\n");
        activeOutput.append("aload ").append(iterableSlot).append("\n");
        if (iterableType.startsWith("vector:")) {
            activeOutput.append("invokeinterface java/util/List/size()I 1\n");
        } else {
            activeOutput.append("arraylength\n");
        }
        activeOutput.append("if_icmpge ").append(afterAllLabel).append("\n");
        activeOutput.append("aload ").append(iterableSlot).append("\n");
        activeOutput.append("iload ").append(indexSlot).append("\n");
        if (iterableType.startsWith("vector:")) {
            activeOutput.append("invokeinterface java/util/List/get(I)Ljava/lang/Object; 2\n");
            activeOutput.append(unboxVectorElementValue(elementType));
        } else {
            activeOutput.append(loadArrayElementInstruction(iterableType));
        }
        activeOutput.append(storeIterationVariableValue(varName, varType, varIndex, isStaticScalar, elementType));
        activeOutput.append(bodyCode);
        activeOutput.append("iload ").append(indexSlot).append("\n");
        activeOutput.append("ldc 1\n");
        activeOutput.append("iadd\n");
        activeOutput.append("istore ").append(indexSlot).append("\n");
        activeOutput.append("goto ").append(loopLabel).append("\n");
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

    private String inferIterableType(ExprContext expr) {
        String exprType = exprTypes.get(expr);
        if (exprType != null) {
            return exprType;
        }
        if (expr instanceof PerseusParser.VarExprContext varExpr) {
            return lookupVarType(varExpr.identifier().getText());
        }
        return null;
    }

    private boolean isCompatibleIterationVariableType(String variableType, String elementType) {
        if (variableType == null || elementType == null) return false;
        if (variableType.startsWith("thunk:")) {
            variableType = variableType.substring("thunk:".length());
        }
        return variableType.equals(elementType);
    }

    private String loadArrayElementInstruction(String arrayType) {
        boolean refArray = arrayType != null && arrayType.startsWith("ref:") && arrayType.endsWith("[]");
        return "real[]".equals(arrayType) ? "daload\n"
                : "boolean[]".equals(arrayType) ? "baload\n"
                : ("string[]".equals(arrayType) || refArray) ? "aaload\n"
                : "iaload\n";
    }

    private String boxVectorElementValue(String elementType, String valueType) {
        StringBuilder sb = new StringBuilder();
        if ("real".equals(elementType) && "integer".equals(valueType)) {
            sb.append("i2d\n");
            valueType = "real";
        }
        if ("real".equals(elementType) || "real".equals(valueType)) {
            sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
        } else if ("integer".equals(elementType) || "boolean".equals(elementType)
                || "integer".equals(valueType) || "boolean".equals(valueType)) {
            sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
        }
        return sb.toString();
    }

    private String unboxVectorElementValue(String elementType) {
        if ("real".equals(elementType)) {
            return "checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n";
        }
        if ("integer".equals(elementType) || "boolean".equals(elementType)) {
            return "checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n";
        }
        if ("string".equals(elementType)) {
            return "checkcast java/lang/String\n";
        }
        if (elementType != null && (elementType.startsWith("ref:") || elementType.startsWith("vector:") || elementType.startsWith("procedure:"))) {
            return "";
        }
        return "";
    }

    private String boxMapComponentValue(String declaredType, String actualType) {
        StringBuilder sb = new StringBuilder();
        if ("real".equals(declaredType) && "integer".equals(actualType)) {
            sb.append("i2d\n");
            actualType = "real";
        }
        if ("real".equals(declaredType) || "real".equals(actualType)) {
            sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
        } else if ("integer".equals(declaredType) || "boolean".equals(declaredType)
                || "integer".equals(actualType) || "boolean".equals(actualType)) {
            sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
        }
        return sb.toString();
    }

    private String unboxMapComponentValue(String declaredType) {
        if ("real".equals(declaredType)) {
            return "checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n";
        }
        if ("integer".equals(declaredType) || "boolean".equals(declaredType)) {
            return "checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n";
        }
        if ("string".equals(declaredType)) {
            return "checkcast java/lang/String\n";
        }
        return "";
    }

    private String mapKeyType(String mapType) {
        int sep = mapType.indexOf("=>");
        return sep >= 0 ? mapType.substring("map:".length(), sep) : "integer";
    }

    private String mapValueType(String mapType) {
        int sep = mapType.indexOf("=>");
        return sep >= 0 ? mapType.substring(sep + 2) : "integer";
    }

    private String unboxJavaIterableElementValue(String expectedType) {
        if ("real".equals(expectedType)) {
            return "checkcast java/lang/Number\ninvokevirtual java/lang/Number/doubleValue()D\n";
        }
        if ("integer".equals(expectedType)) {
            return "checkcast java/lang/Number\ninvokevirtual java/lang/Number/intValue()I\n";
        }
        if ("boolean".equals(expectedType)) {
            return "checkcast java/lang/Boolean\ninvokevirtual java/lang/Boolean/booleanValue()Z\n";
        }
        if ("string".equals(expectedType)) {
            return "checkcast java/lang/String\n";
        }
        return "";
    }

    private String storeIterationVariableValue(String varName, String varType, Integer varIndex,
            boolean isStaticScalar, String elementType) {
        String effectiveType = effectiveIterationVariableType(varType);
        if ("real".equals(effectiveType)) {
            if (isStaticScalar) {
                return "putstatic " + packageName + "/" + className + "/" + varName + " D\n";
            }
            return "dstore " + varIndex + "\n";
        }
        if ("integer".equals(effectiveType) || "boolean".equals(effectiveType)) {
            if (isStaticScalar) {
                return "putstatic " + packageName + "/" + className + "/" + varName + " I\n";
            }
            return "istore " + varIndex + "\n";
        }
        String desc = scalarTypeToJvmDesc(effectiveType != null ? effectiveType : elementType);
        if (isStaticScalar) {
            return "putstatic " + packageName + "/" + className + "/" + varName + " " + desc + "\n";
        }
        return "astore " + varIndex + "\n";
    }

    private String effectiveIterationVariableType(String varType) {
        return varType != null && varType.startsWith("thunk:") ? varType.substring("thunk:".length()) : varType;
    }

    private boolean isJavaIterableReferenceType(String type) {
        if (type == null || !type.startsWith("ref:")) {
            return false;
        }
        String simpleName = type.substring("ref:".length());
        SymbolTableBuilder.ClassInfo cls = classes.get(simpleName);
        if (cls == null) {
            return false;
        }
        return matchesExternalJavaType(cls, java.lang.Iterable.class, new LinkedHashSet<>());
    }

    private boolean matchesExternalJavaType(SymbolTableBuilder.ClassInfo cls, Class<?> targetType, Set<String> seen) {
        if (cls == null || !seen.add(cls.name)) {
            return false;
        }
        if (cls.externalJava && cls.externalJavaQualifiedName != null) {
            try {
                Class<?> actualClass = Class.forName(cls.externalJavaQualifiedName);
                if (targetType.isAssignableFrom(actualClass)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        for (String ifaceName : cls.interfaces) {
            SymbolTableBuilder.ClassInfo iface = classes.get(ifaceName);
            if (matchesExternalJavaType(iface, targetType, seen)) {
                return true;
            }
        }
        if (cls.parentName != null) {
            SymbolTableBuilder.ClassInfo parent = classes.get(cls.parentName);
            if (matchesExternalJavaType(parent, targetType, seen)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Unique label counter
    // -------------------------------------------------------------------------

    private int labelCounter = 0;

    private String generateUniqueLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    private String normalizeStatementLabel(String rawLabel) {
        return rawLabel.chars().allMatch(Character::isDigit) ? "L" + rawLabel : rawLabel;
    }

    private void emitGotoDesignationalExpr(PerseusParser.DesignationalExprContext ctx) {
        if (ctx instanceof PerseusParser.DirectDesignationalExprContext simpleCtx) {
            emitGotoSimpleDesignationalExpr(simpleCtx.simpleDesignationalExpr());
            return;
        }
        if (ctx instanceof PerseusParser.IfDesignationalExprContext ifCtx) {
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

    private void emitGotoSimpleDesignationalExpr(PerseusParser.SimpleDesignationalExprContext ctx) {
        if (ctx instanceof PerseusParser.LabelDesignatorExprContext labelCtx) {
            activeOutput.append("goto ").append(normalizeStatementLabel(labelCtx.identifier().getText())).append("\n");
            return;
        }
        if (ctx instanceof PerseusParser.NumericLabelDesignatorExprContext numericLabelCtx) {
            activeOutput.append("goto ").append(normalizeStatementLabel(numericLabelCtx.unsignedInt().getText())).append("\n");
            return;
        }
        if (ctx instanceof PerseusParser.ParenDesignatorExprContext parenCtx) {
            emitGotoDesignationalExpr(parenCtx.designationalExpr());
            return;
        }
        if (ctx instanceof PerseusParser.SwitchDesignatorExprContext switchCtx) {
            emitGotoSwitchDesignator(switchCtx.identifier().getText(), switchCtx.expr());
            return;
        }
        activeOutput.append("; ERROR: unsupported simple designational expression\n");
    }

    private void emitGotoSwitchDesignator(String switchName, PerseusParser.ExprContext indexExpr) {
        PerseusParser.SwitchDeclContext switchDecl = switchDeclarations.get(switchName);
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
        if (ctx instanceof PerseusParser.VarExprContext ve) {
            String varName = ve.identifier().getText();
            // If the expression refers to the current procedure (recursive call), it should
            // be treated as a call rather than a captured variable.
            if (varName.equals(currentProcName) && procedures.containsKey(varName)) {
                return names;
            }
            names.add(varName);
        } else if (ctx instanceof PerseusParser.ArrayAccessExprContext ae) {
            names.add(ae.identifier().getText());
            for (PerseusParser.ExprContext subscript : ae.expr()) {
                names.addAll(collectVarNames(subscript));
            }
        } else if (ctx instanceof PerseusParser.ProcCallExprContext pc) {
            if (pc.argList() != null) {
                for (PerseusParser.ArgContext a : pc.argList().arg()) {
                    if (a.expr() != null) names.addAll(collectVarNames(a.expr()));
                }
            }
        } else if (ctx instanceof PerseusParser.RelExprContext re) {
            names.addAll(collectVarNames(re.expr(0)));
            names.addAll(collectVarNames(re.expr(1)));
        } else if (ctx instanceof PerseusParser.MulDivExprContext me) {
            names.addAll(collectVarNames(me.expr(0)));
            names.addAll(collectVarNames(me.expr(1)));
        } else if (ctx instanceof PerseusParser.AddSubExprContext ae) {
            names.addAll(collectVarNames(ae.expr(0)));
            names.addAll(collectVarNames(ae.expr(1)));
        } else if (ctx instanceof PerseusParser.AndExprContext ae) {
            names.addAll(collectVarNames(ae.expr(0)));
            names.addAll(collectVarNames(ae.expr(1)));
        } else if (ctx instanceof PerseusParser.OrExprContext oe) {
            names.addAll(collectVarNames(oe.expr(0)));
            names.addAll(collectVarNames(oe.expr(1)));
        } else if (ctx instanceof PerseusParser.AnonymousProcedureExprContext) {
            return names;
        } else if (ctx instanceof PerseusParser.NotExprContext ne) {
            names.addAll(collectVarNames(ne.expr()));
        } else if (ctx instanceof PerseusParser.UnaryMinusExprContext ue) {
            names.addAll(collectVarNames(ue.expr()));
        } else if (ctx instanceof PerseusParser.ParenExprContext pe) {
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
        SymbolTableBuilder.ExternalValueInfo externalValue = externalJavaStaticValues.get(name);
        boolean resolvedFromRootMain = false;
        if (idx == null && mainLocalIndex != null) {
            idx = mainLocalIndex.get(name);
        }
        if (type == null && mainSymbolTable != null) {
            type = mainSymbolTable.get(name);
        }
        if (idx == null && rootMainLocalIndex != null && rootMainLocalIndex.containsKey(name)) {
            idx = rootMainLocalIndex.get(name);
            resolvedFromRootMain = true;
        }
        if (type == null && rootMainSymbolTable != null) {
            type = rootMainSymbolTable.get(name);
            if (type != null) {
                resolvedFromRootMain = true;
            }
        }
        if (type == null) {
            type = capturedClosureType(name);
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
        String closureOwnerLoad = generateClosureOwnerEnvLoad(name, type);
        if (closureOwnerLoad != null) {
            return closureOwnerLoad;
        }
        if (idx == null) {
            if (externalValue != null) {
                return "getstatic " + externalValue.ownerClass.replace('.', '/') + "/" + externalValue.targetMember
                        + " " + externalValueJvmDesc(externalValue.type) + "\n";
            }
            // Check if this is a static scalar
            if (type != null && !type.endsWith("[]") && !type.startsWith("procedure:") && !type.startsWith("thunk:")) {
                String jvmDesc = scalarTypeToJvmDesc(type);
                return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
            }
            if (type != null && type.endsWith("[]")) {
                String jvmDesc = arrayTypeToJvmDesc(type);
                return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
            }
            // Check main symbol table for outer scope static scalars
            if (mainSymbolTable != null) {
                String mainType = mainSymbolTable.get(name);
                if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                    String jvmDesc = scalarTypeToJvmDesc(mainType);
                    return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
                } else if (mainType != null && mainType.endsWith("[]")) {
                    String jvmDesc = arrayTypeToJvmDesc(mainType);
                    return "getstatic " + packageName + "/" + className + "/" + name + " " + jvmDesc + "\n";
                }
            }
            return "; ERROR: undeclared variable " + name + "\n";
        }
        // If the idx came from an outer scope (mainLocalIndex) and we're not generating
        // code for that outer method, access via static field rather than local slot.
        if (!currentLocalIndex.containsKey(name)) {
            if (type != null && type.startsWith("procedure:")) {
                String desc = getProcedureInterfaceDescriptor(type);
                String fieldName = resolveOuterFieldName(name, type, resolvedFromRootMain);
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + desc + "\n";
            }
            if (type != null && !type.endsWith("[]") && !type.startsWith("thunk:")) {
                String jvmDesc = scalarTypeToJvmDesc(type);
                String fieldName = resolveOuterFieldName(name, type, resolvedFromRootMain);
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
            }
            if (type != null && type.endsWith("[]")) {
                String jvmDesc = arrayTypeToJvmDesc(type);
                String fieldName = resolveOuterFieldName(name, type, resolvedFromRootMain);
                return "getstatic " + packageName + "/" + className + "/" + fieldName + " " + jvmDesc + "\n";
            }
        }
        if (type != null && type.startsWith("thunk:")) {
            // thunk formal inside caller? not expected here
            type = type.substring("thunk:".length());
        }
        String envProcName = currentClosureOwnerProcName != null ? currentClosureOwnerProcName : currentProcName;
        if ((idx == null || !currentLocalIndex.containsKey(name)) && useEnvBridge(envProcName) && envProcName != null) {
            SymbolTableBuilder.ProcInfo cp = procedures.get(envProcName);
            if (cp != null && cp.paramNames.contains(name) && cp.valueParams.contains(name)) {
                String pType = getFormalBaseType(cp, name);
                if (pType.endsWith("[]")) {
                    return "aload " + idx + "\n";
                }
                String desc;
                if ("real".equals(pType) || "deferred".equals(pType)) desc = "D";
                else if ("string".equals(pType)) desc = "Ljava/lang/String;";
                else if (pType.startsWith("procedure:")) {
                    desc = getProcedureInterfaceDescriptor(pType);
                } else if (isObjectType(pType)) desc = CodeGenUtils.getReturnTypeDescriptor(pType);
                else if ("deferred".equals(pType)) desc = "D";
                else desc = "I";
                return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(envProcName, name) + " " + desc + "\n";
            }
        }
        if (isCurrentProcedureBridgedLocal(name)) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(envProcName, name)
                + " " + scalarTypeToJvmDesc(type) + "\n";
        }
        if ("integer".equals(type) || "boolean".equals(type)) {
            return "iload " + idx + "\n";
        } else if ("real".equals(type) || "deferred".equals(type)) {
            return "dload " + idx + "\n";
        } else if ("string".equals(type) || (type != null && (type.startsWith("ref:") || type.startsWith("vector:")))) {
            return "aload " + idx + "\n";
        } else if (type != null && type.endsWith("[]")) {
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
        if (actual instanceof PerseusParser.VarExprContext ve
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
                                pDesc = getProcedureInterfaceDescriptor(pType);
                            } else pDesc = "I";
                        }
                        cls.append(".field public cap_").append(p).append(" ").append(pDesc).append("\n");
                    }
                    if (!"void".equals(outerInfo.returnType)) {
                        String rDesc = CodeGenUtils.getReturnTypeDescriptor(outerInfo.returnType);
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
                                pDesc = getProcedureInterfaceDescriptor(pType);
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
                        String rDesc = CodeGenUtils.getReturnTypeDescriptor(outerInfo.returnType);
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
                                pDesc = getProcedureInterfaceDescriptor(pType);
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
                        retDesc = CodeGenUtils.getReturnTypeDescriptor(outerInfo.returnType);
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
                                pDesc = getProcedureInterfaceDescriptor(pType);
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
                    String pRetDesc = CodeGenUtils.getReturnTypeDescriptor(pRet);
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
                                pDesc = getProcedureInterfaceDescriptor(pType);
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
                                pDesc = CodeGenUtils.getProcedureInterfaceDescriptor(pType);
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
            if (actual instanceof PerseusParser.VarExprContext ve && procedures.containsKey(ve.identifier().getText())) {
                String pName = ve.identifier().getText();
                SymbolTableBuilder.ProcInfo pInfo = procedures.get(pName);
                String pRet = pInfo != null ? pInfo.returnType : "integer";
                String pRetDesc = CodeGenUtils.getReturnTypeDescriptor(pRet);
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
    private String generateUserProcedureInvocation(String name, List<PerseusParser.ArgContext> args, boolean isStatement) {
        SymbolTableBuilder.ProcInfo info = procedures.get(name);
        if (info == null) {
            return "; unknown procedure: " + name + "\n";
        }
        if (info.external) {
            return generateExternalProcedureInvocation(name, info, args, isStatement);
        }

        StringBuilder sb = new StringBuilder();
        // Determine which parameters are call-by-name and collect variable
        // boxes needed for all of them.
        Map<String,Integer> varToBoxSlot = new LinkedHashMap<>();
        Set<String> varsToRestore = new LinkedHashSet<>();

        // first pass: discover variables referenced by each name argument
        List<PerseusParser.ArgContext> argList = args;
        for (int ai = 0; ai < argList.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            boolean isValue = info.valueParams.contains(paramName);
            PerseusParser.ArgContext arg = argList.get(ai);
            if (!isValue) {
                if (arg.expr() != null) {
                    Set<String> names = collectVarNames(arg.expr());
                    for (String vn : names) {
                        // If the variable is already a thunk (call-by-name), we don't need to box and restore it.
                        Type vTypeInfo = lookupVarTypeInfo(vn);
                        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                        boolean outerIsCallByNameParam = false;
                        if (outerProc != null) {
                            SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                            if (outerInfo != null && outerInfo.paramNames.contains(vn) && !outerInfo.valueParams.contains(vn)) {
                                outerIsCallByNameParam = true;
                            }
                        }
                        boolean isThunkVar = (vTypeInfo != null && vTypeInfo.isThunk()) || outerIsCallByNameParam;
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
                        if (Type.REAL.equals(vTypeInfo)) {
                            sb.append(generateLoadVar(vn));
                            sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                        } else if (Type.STRING.equals(vTypeInfo) || usesObjectStorage(vTypeInfo)) {
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
                if (getFormalBaseTypeInfo(info, paramName).isArray()) {
                    if (arg.expr() instanceof PerseusParser.VarExprContext ve) {
                        String arrayName = ve.identifier().getText();
                        Type actualType = lookupVarTypeInfo(arrayName);
                        if (actualType != null && actualType.isArray()) {
                            List<int[]> actualBounds = lookupDeclaredArrayBoundPairs(arrayName);
                            if (actualBounds != null && actualBounds.size() > 1) {
                                sb.append("; ERROR: multidimensional array arguments are not supported for ")
                                  .append(paramName).append("\n");
                            } else {
                                sb.append(generateLoadVar(arrayName));
                                sb.append(generatePushArrayBounds(arrayName));
                            }
                        } else {
                            sb.append("; ERROR: expected array argument for ").append(paramName).append("\n");
                        }
                    } else {
                        sb.append("; ERROR: array argument must be an array identifier for ").append(paramName).append("\n");
                    }
                    continue;
                }
                if (arg.expr() != null) {
                    sb.append(generateExpr(arg.expr()));
                    Type paramType = getFormalBaseTypeInfo(info, paramName);
                    Type argType = exprTypeInfo(arg.expr(), Type.INTEGER).unwrapThunk();
                    if ((Type.REAL.equals(paramType) || Type.DEFERRED.equals(paramType)) && Type.INTEGER.equals(argType)) {
                        sb.append("i2d\n");
                    }
                } else {
                    sb.append(arg.getText()).append("\n");
                }
            } else {
                ExprContext actual = arg.expr();
                if (actual instanceof PerseusParser.VarExprContext ve) {
                    String vn = ve.identifier().getText();
                    Type vTypeInfo = lookupVarTypeInfo(vn);
                    String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                    boolean outerIsCallByNameParam = false;
                    if (outerProc != null) {
                        SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
                        if (outerInfo != null && outerInfo.paramNames.contains(vn) && !outerInfo.valueParams.contains(vn)) {
                            outerIsCallByNameParam = true;
                        }
                    }
                    if ((vTypeInfo != null && vTypeInfo.isThunk()) || outerIsCallByNameParam) {
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
                        Type baseType = getFormalBaseTypeInfo(info, paramName);
                        String thunkClass = createThunkClass(varToField, actual, baseType.toLegacyString());
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
                Type baseType = getFormalBaseTypeInfo(info, paramName);
                if (Type.DEFERRED.equals(baseType)) {
                    baseType = getExprBaseTypeInfo(actual);
                }
                String thunkClass = createThunkClass(varToField, actual, baseType.toLegacyString());
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
                Type type = getFormalBaseTypeInfo(info, p);
                if (type.isArray()) return arrayTypeToJvmDesc(type) + "II";
                if (usesRealStorage(type)) return "D";
                if (type.isProcedure()) return getProcedureInterfaceDescriptor(type);
                if (usesObjectStorage(type)) return CodeGenUtils.getReturnTypeDescriptor(type);
                return "I";
            })
            .collect(Collectors.joining());
        String retDesc = CodeGenUtils.getReturnTypeDescriptor(info.returnType);
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
            Type varTypeInfo = lookupVarTypeInfo(vn);
            sb.append("aload ").append(boxSlot).append("\n");
            sb.append("iconst_0\n");
            sb.append("aaload\n");
            if (varTypeInfo != null && varTypeInfo.isThunk()) {
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
            if (Type.REAL.equals(varTypeInfo)) {
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
            } else if (Type.STRING.equals(varTypeInfo)) {
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
            } else if (usesObjectStorage(varTypeInfo)) {
                String desc = typeStoreDescriptor(varTypeInfo);
                if (desc != null) {
                    sb.append("checkcast ").append(desc.substring(1, desc.length() - 1)).append("\n");
                }
                String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
                String targetField = (useEnvBridge(outerProc) && outerProc != null) ? envThunkFieldName(outerProc, vn) : vn;
                    if (varSlot != null) {
                        if (!currentLocalIndex.containsKey(vn)) {
                            sb.append("putstatic ").append(packageName).append("/").append(className)
                              .append("/").append(targetField).append(" ").append(desc).append("\n");
                        } else {
                            emitStore(sb, "astore", varSlot);
                        }
                    } else {
                        sb.append("putstatic ").append(packageName).append("/").append(className)
                           .append("/").append(targetField).append(" ").append(desc).append("\n");
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
        if (expr instanceof PerseusParser.AnonymousProcedureExprContext) {
            return true;
        }
        if (!(expr instanceof PerseusParser.VarExprContext ve)) {
            Type exprType = exprTypeInfo(expr, null);
            return exprType != null && exprType.isProcedure();
        }
        String name = ve.identifier().getText();
        if (procedures.containsKey(name)) {
            return true;
        }
        Type type = lookupVarTypeInfo(name);
        return type != null && type.isProcedure();
    }

    private static String arrayTypeToJvmDesc(String arrayType) {
        return CodeGenUtils.arrayTypeToJvmDesc(arrayType);
    }

    private static String arrayTypeToJvmDesc(Type arrayType) {
        return CodeGenUtils.arrayTypeToJvmDesc(arrayType);
    }

    private static String scalarTypeToJvmDesc(String scalarType) {
        return CodeGenUtils.scalarTypeToJvmDesc(scalarType);
    }

    private static String scalarTypeToJvmDesc(Type scalarType) {
        return CodeGenUtils.scalarTypeToJvmDesc(scalarType);
    }

    private String externalValueJvmDesc(String type) {
        return externalValueJvmDesc(type != null ? Type.parse(type) : null);
    }

    private String externalValueJvmDesc(Type type) {
        if (type != null && type.isRef()) {
            String simpleName = type.name();
            String qualified = externalJavaClasses.get(simpleName);
            if (qualified != null) {
                return "L" + qualified.replace('.', '/') + ";";
            }
            return "Ljava/lang/Object;";
        }
        return scalarTypeToJvmDesc(type);
    }

    private String externalTypeToJvmDesc(String type, String externalKind) {
        return externalTypeToJvmDesc(type != null ? Type.parse(type) : null, externalKind);
    }

    private String externalTypeToJvmDesc(Type type, String externalKind) {
        if (type != null && type.isVector()) {
            return "Ljava/util/List;";
        }
        if (type != null && type.isArray()) {
            if ("java-static".equals(externalKind)) {
                return arrayTypeToJvmDesc(type);
            }
            return arrayTypeToJvmDesc(type) + "II";
        }
        if (type != null && type.isRef()) {
            String simpleName = type.name();
            String qualified = externalJavaClasses.get(simpleName);
            if (qualified != null) {
                return "L" + qualified.replace('.', '/') + ";";
            }
            return "Ljava/lang/Object;";
        }
        if ("java-static".equals(externalKind)) {
            return switch (type != null ? type.toLegacyString() : "integer") {
                case "void" -> "V";
                case "real" -> "D";
                case "string" -> "Ljava/lang/String;";
                case "boolean" -> "Z";
                default -> "I";
            };
        }
        return CodeGenUtils.getReturnTypeDescriptor(type);
    }

    /**
     * Looks up a variable's type in the current scope, falling back to main scope.
     *
     * Also supports nested procedure environments: if a variable is not declared in
     * the current or main scope but is a parameter of an enclosing procedure, it
     * is accessed via the environment bridge static fields (e.g. __env_<outer>_<param>).
     */
    private String lookupVarType(String name) {
        Type typeInfo = lookupVarTypeInfo(name);
        return typeInfo != null ? typeInfo.toLegacyString() : null;
    }

    private Type lookupVarTypeInfo(String name) {
        Type type = currentSymbolTableTypes != null ? currentSymbolTableTypes.get(name) : null;
        if (type == null && mainSymbolTableTypes != null) {
            type = mainSymbolTableTypes.get(name);
        }
        if (type == null && rootMainSymbolTableTypes != null) {
            type = rootMainSymbolTableTypes.get(name);
        }
        if (type == null) {
            type = capturedClosureTypeInfo(name);
        }
        if (type != null) return type;

        // If not found in the local or main symbol tables, check for env-bridge
        // parameters from enclosing procedures (nested scopes).
        for (String outerProc : savedProcNameStack) {
            if (outerProc == null) continue;
            SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
            if (outerInfo == null) continue;
            if (outerInfo.paramNames.contains(name)) {
                Type baseType = getFormalBaseTypeInfo(outerInfo, name);
                if (!outerInfo.valueParams.contains(name)) {
                    return Type.thunk(baseType);
                }
                return baseType;
            }
        }
        return null;
    }

    private Type getFormalBaseTypeInfo(SymbolTableBuilder.ProcInfo info, String paramName) {
        if (info == null) {
            return Type.INTEGER;
        }
        Type baseType = info.typedParamTypes.get(paramName);
        if (baseType != null) {
            return baseType;
        }
        return Type.DEFERRED;
    }

    private String lookupVarTypeLegacyFallback(String name) {
        String type = currentSymbolTable.get(name);
        if (type == null && mainSymbolTable != null) type = mainSymbolTable.get(name);
        if (type == null && rootMainSymbolTable != null) type = rootMainSymbolTable.get(name);
        if (type == null) {
            type = capturedClosureType(name);
        }
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

    private SymbolTableBuilder.ProcInfo getClosureOwnerInfo() {
        if (currentClosureOwnerProcName == null) {
            return null;
        }
        return procedures.get(currentClosureOwnerProcName);
    }

    private String capturedClosureType(String name) {
        Type typeInfo = capturedClosureTypeInfo(name);
        return typeInfo != null ? typeInfo.toLegacyString() : null;
    }

    private Type capturedClosureTypeInfo(String name) {
        SymbolTableBuilder.ProcInfo ownerInfo = getClosureOwnerInfo();
        if (ownerInfo == null || name == null) {
            return null;
        }
        if (ownerInfo.paramNames.contains(name)) {
            Type baseType = getFormalBaseTypeInfo(ownerInfo, name);
            return ownerInfo.valueParams.contains(name) ? baseType : Type.thunk(baseType);
        }
        Type localType = ownerInfo.typedLocalVars.get(name);
        if (localType != null && !ownerInfo.ownVars.contains(name)
                && !localType.isArray() && !localType.isProcedure()) {
            return localType;
        }
        if (ownerInfo.nestedProcedures.contains(name)) {
            SymbolTableBuilder.ProcInfo nestedInfo = procedures.get(name);
            if (nestedInfo != null) {
                return Type.procedure(nestedInfo.returnTypeInfo);
            }
        }
        return null;
    }

    private String generateClosureOwnerEnvLoad(String name, String type) {
        SymbolTableBuilder.ProcInfo ownerInfo = getClosureOwnerInfo();
        if (ownerInfo == null || currentClosureOwnerProcName == null || name == null || type == null) {
            return null;
        }
        if (ownerInfo.paramNames.contains(name) && ownerInfo.valueParams.contains(name)) {
            String pType = getFormalBaseType(ownerInfo, name);
            if (pType.endsWith("[]")) {
                return null;
            }
            String desc;
            if ("real".equals(pType) || "deferred".equals(pType)) desc = "D";
            else if ("string".equals(pType)) desc = "Ljava/lang/String;";
            else if (pType.startsWith("procedure:")) desc = getProcedureInterfaceDescriptor(pType);
            else if (isObjectType(pType)) desc = CodeGenUtils.getReturnTypeDescriptor(pType);
            else desc = "I";
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentClosureOwnerProcName, name)
                + " " + desc + "\n";
        }
        String localType = ownerInfo.localVars.get(name);
        if (localType != null && !ownerInfo.ownVars.contains(name)
                && !localType.endsWith("[]") && !localType.startsWith("procedure:")) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentClosureOwnerProcName, name)
                + " " + scalarTypeToJvmDesc(localType) + "\n";
        }
        return null;
    }

    private String nearestCapturedOuterProcName() {
        if (currentClosureOwnerProcName != null) {
            return currentClosureOwnerProcName;
        }
        return savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
    }

    private String exceptionPatternRefType(PerseusParser.ExceptionPatternContext ctx) {
        return ExceptionTypeResolver.toReferenceType(ctx);
    }

    private SymbolTableBuilder.MethodInfo findClassMethodInHierarchy(SymbolTableBuilder.ClassInfo cls, String methodName) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            SymbolTableBuilder.MethodInfo method = current.methods.get(methodName);
            if (method != null) {
                return method;
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return null;
    }

    private String findMethodOwner(SymbolTableBuilder.ClassInfo cls, String methodName) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            if (current.methods.containsKey(methodName)) {
                return current.name;
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return null;
    }

    private String ownerInternalName(SymbolTableBuilder.ClassInfo cls) {
        if (cls == null) {
            return classPackageName + "/UnknownClass";
        }
        if (cls.externalJava && cls.externalJavaQualifiedName != null) {
            return cls.externalJavaQualifiedName.replace('.', '/');
        }
        return classPackageName + "/" + cls.name;
    }

    private Method findJavaMethod(String qualifiedName, String methodName, List<PerseusParser.ArgContext> args) {
        return JavaInteropResolver.findBestMethod(
                qualifiedName, methodName, getArgTypes(args), this::scoreReferenceCompatibility);
    }

    private Field findJavaField(String qualifiedName, String fieldName) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            return owner.getField(fieldName);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
        }
        return null;
    }

    private Constructor<?> findJavaConstructor(String qualifiedName, List<PerseusParser.ArgContext> args) {
        return JavaInteropResolver.findBestConstructor(
                qualifiedName, getArgTypes(args), this::scoreReferenceCompatibility);
    }

    private List<String> getArgTypes(List<PerseusParser.ArgContext> args) {
        ArrayList<String> argTypes = new ArrayList<>();
        for (PerseusParser.ArgContext arg : args) {
            argTypes.add(exprTypes.getOrDefault(arg.expr(), "integer"));
        }
        return argTypes;
    }

    private int scoreReferenceCompatibility(Class<?> parameterType, String refTypeSimpleName) {
        SymbolTableBuilder.ClassInfo cls = classes.get(refTypeSimpleName);
        if (cls == null) {
            return -1;
        }
        if (matchesJavaReferenceType(cls, parameterType)) {
            return 2;
        }
        return -1;
    }

    private boolean matchesJavaReferenceType(SymbolTableBuilder.ClassInfo cls, Class<?> parameterType) {
        if (cls.externalJava && cls.externalJavaQualifiedName != null) {
            try {
                Class<?> actualClass = Class.forName(cls.externalJavaQualifiedName);
                if (parameterType.isAssignableFrom(actualClass)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        for (String ifaceName : cls.interfaces) {
            SymbolTableBuilder.ClassInfo iface = classes.get(ifaceName);
            if (iface != null && iface.externalJava && iface.externalJavaQualifiedName != null) {
                try {
                    Class<?> ifaceClass = Class.forName(iface.externalJavaQualifiedName);
                    if (parameterType.isAssignableFrom(ifaceClass)) {
                        return true;
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        if (cls.parentName != null) {
            SymbolTableBuilder.ClassInfo parent = classes.get(cls.parentName);
            if (parent != null && matchesJavaReferenceType(parent, parameterType)) {
                return true;
            }
        }
        return false;
    }

    private String toJvmDescriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private boolean isProcedureReturnTarget(String name, boolean rhsIsProcedureRef) {
        String returnType = getProcedureReturnTargetType(name, rhsIsProcedureRef);
        return returnType != null && !"void".equals(returnType);
    }

    private boolean isEnclosingProcedureReturnTarget(String name, boolean rhsIsProcedureRef) {
        if (rhsIsProcedureRef || name == null || name.equals(currentProcName)) return false;
        SymbolTableBuilder.ProcInfo outerInfo = getEnclosingProcedureInfo(name);
        return outerInfo != null && !"void".equals(outerInfo.returnType);
    }

    private String getProcedureReturnTargetType(String name, boolean rhsIsProcedureRef) {
        if (name == null) return null;
        if (name.equals(currentProcName) && procRetvalSlot >= 0) {
            if (rhsIsProcedureRef && !currentProcReturnType.startsWith("procedure:")) {
                return null;
            }
            return currentProcReturnType;
        }
        SymbolTableBuilder.ProcInfo outerInfo = getEnclosingProcedureInfo(name);
        if (outerInfo != null && !"void".equals(outerInfo.returnType)
                && (!rhsIsProcedureRef || outerInfo.returnType.startsWith("procedure:"))) {
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

    private String generateOpenStringTargetThunk(PerseusParser.ArgContext arg) {
        if (arg == null || !(arg.expr() instanceof PerseusParser.VarExprContext varExpr)) {
            return null;
        }

        String name = varExpr.identifier().getText();
        String type = lookupVarType(name);
        if (!"string".equals(type) && !"thunk:string".equals(type)) {
            return null;
        }

        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
        if (outerProc != null) {
            SymbolTableBuilder.ProcInfo outerInfo = procedures.get(outerProc);
            if (outerInfo != null && outerInfo.paramNames.contains(name) && !outerInfo.valueParams.contains(name)) {
                return generateLoadThunkRef(name);
            }
        }

        Integer slot = currentLocalIndex.get(name);
        if (slot != null) {
            return null;
        }

        String thunkLocalName = className + "$Thunk" + thunkCounter++;
        String internalName = packageName + "/" + thunkLocalName;
        String fieldName = staticFieldName(name, "string");

        StringBuilder cls = new StringBuilder();
        cls.append(".class public ").append(internalName).append("\n");
        cls.append(".super java/lang/Object\n");
        cls.append(".implements gnb/perseus/compiler/Thunk\n\n");

        cls.append(".method public <init>()V\n");
        cls.append(".limit stack 1\n");
        cls.append(".limit locals 1\n");
        cls.append("aload_0\n");
        cls.append("invokespecial java/lang/Object/<init>()V\n");
        cls.append("return\n");
        cls.append(".end method\n\n");

        cls.append(".method public get()Ljava/lang/Object;\n");
        cls.append(".limit stack 1\n");
        cls.append(".limit locals 1\n");
        cls.append("getstatic ").append(packageName).append("/").append(className)
                .append("/").append(fieldName).append(" Ljava/lang/String;\n");
        cls.append("areturn\n");
        cls.append(".end method\n\n");

        cls.append(".method public sync()V\n");
        cls.append(".limit stack 0\n");
        cls.append(".limit locals 1\n");
        cls.append("return\n");
        cls.append(".end method\n\n");

        cls.append(".method public set(Ljava/lang/Object;)V\n");
        cls.append(".limit stack 1\n");
        cls.append(".limit locals 2\n");
        cls.append("aload_1\n");
        cls.append("checkcast java/lang/String\n");
        cls.append("putstatic ").append(packageName).append("/").append(className)
                .append("/").append(fieldName).append(" Ljava/lang/String;\n");
        cls.append("return\n");
        cls.append(".end method\n\n");

        thunkClassDefinitions.add(Map.entry(thunkLocalName, cls.toString()));
        return "new " + internalName + "\n"
                + "dup\n"
                + "invokespecial " + internalName + "/<init>()V\n";
    }

    private String resolveOuterFieldName(String name, String varType, boolean resolvedFromRootMain) {
        if (resolvedFromRootMain) {
            return staticFieldName(name, varType);
        }
        String outerProc = savedProcNameStack.isEmpty() ? null : savedProcNameStack.peek();
        if (outerProc == null || !useEnvBridge(outerProc)) {
            return staticFieldName(name, varType);
        }
        if (varType != null && varType.startsWith("procedure:")) {
            return envThunkFieldName(outerProc, name);
        }
        return envThunkFieldName(outerProc, name);
    }

    private String getFormalBaseType(SymbolTableBuilder.ProcInfo info, String paramName) {
        if (info == null) {
            return "integer";
        }
        String baseType = info.paramTypes.get(paramName);
        if (baseType != null) {
            return baseType;
        }
        return "deferred";
    }

    private boolean isCurrentArrayParameter(String name) {
        return currentArrayParamBoundSlots.containsKey(name);
    }

    private String generateLoadArrayLowerBound(String name) {
        int[] boundSlots = currentArrayParamBoundSlots.get(name);
        if (boundSlots != null) {
            return "iload " + boundSlots[0] + "\n";
        }
        int[] bounds = lookupArrayBounds(name);
        if (bounds != null && bounds[0] != 0) {
            return "ldc " + bounds[0] + "\n";
        }
        return "";
    }

    private String generatePushArrayBounds(String name) {
        int[] boundSlots = currentArrayParamBoundSlots.get(name);
        if (boundSlots != null) {
            return "iload " + boundSlots[0] + "\n"
                + "iload " + boundSlots[1] + "\n";
        }
        int[] bounds = lookupArrayBounds(name);
        int lower = bounds != null ? bounds[0] : 0;
        int upper = bounds != null ? bounds[1] : 0;
        return "ldc " + lower + "\n"
            + "ldc " + upper + "\n";
    }

    private List<int[]> lookupDeclaredArrayBoundPairs(String name) {
        List<int[]> bounds = currentArrayBoundPairs != null ? currentArrayBoundPairs.get(name) : null;
        if (bounds == null && mainArrayBoundPairs != null) {
            bounds = mainArrayBoundPairs.get(name);
        }
        return bounds;
    }

    private int computeFlattenedArraySize(List<int[]> bounds) {
        int size = 1;
        for (int[] boundPair : bounds) {
            size *= (boundPair[1] - boundPair[0] + 1);
        }
        return size;
    }

    private String generateNormalizedSubscript(ExprContext expr, int[] boundPair, Map<String, Integer> varToFieldIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append(generateExpr(expr, varToFieldIndex));
        if ("real".equals(exprTypes.getOrDefault(expr, "integer"))) {
            sb.append("d2i\n");
        }
        if (boundPair != null && boundPair[0] != 0) {
            sb.append("ldc ").append(boundPair[0]).append("\n");
            sb.append("isub\n");
        }
        return sb.toString();
    }

    private String generateMemberInvocation(String receiverName, String memberName,
            List<PerseusParser.ArgContext> args, boolean explicitCall, boolean isStatement) {
        String receiverType = lookupVarType(receiverName);
        if (receiverType == null) {
            return "; ERROR: member call requires typed receiver " + receiverName + "\n";
        }
        if (receiverType.startsWith("vector:")) {
            String elementType = receiverType.substring("vector:".length());
            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadVar(receiverName));
            if ("append".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: vector append requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxVectorElementValue(elementType, argType));
                sb.append("invokeinterface java/util/List/add(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("insert".equals(memberName)) {
                if (args.size() != 2) {
                    return "; ERROR: vector insert requires exactly two arguments\n";
                }
                PerseusParser.ExprContext indexExpr = args.get(0).expr();
                PerseusParser.ExprContext valueExpr = args.get(1).expr();
                String valueType = exprTypes.getOrDefault(valueExpr, "integer");
                sb.append(generateExpr(indexExpr));
                sb.append(generateExpr(valueExpr));
                sb.append(boxVectorElementValue(elementType, valueType));
                sb.append("invokeinterface java/util/List/add(ILjava/lang/Object;)V 3\n");
                return sb.toString();
            }
            if ("remove".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: vector remove requires exactly one argument\n";
                }
                PerseusParser.ExprContext indexExpr = args.get(0).expr();
                sb.append(generateExpr(indexExpr));
                sb.append("invokeinterface java/util/List/remove(I)Ljava/lang/Object; 2\n");
                sb.append(unboxVectorElementValue(elementType));
                if (isStatement) {
                    if ("real".equals(elementType)) {
                        sb.append("pop2\n");
                    } else if (!"void".equals(elementType)) {
                        sb.append("pop\n");
                    }
                }
                return sb.toString();
            }
            if ("contains".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: vector contains requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxVectorElementValue(elementType, argType));
                sb.append("invokeinterface java/util/List/contains(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("clear".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: vector clear does not take arguments\n";
                }
                sb.append("invokeinterface java/util/List/clear()V 1\n");
                return sb.toString();
            }
            if ("size".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: vector size does not take arguments\n";
                }
                sb.append("invokeinterface java/util/List/size()I 1\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            return "; ERROR: unknown vector member " + memberName + "\n";
        }
        if (receiverType.startsWith("map:")) {
            String keyType = mapKeyType(receiverType);
            String valueType = mapValueType(receiverType);
            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadVar(receiverName));
            if ("contains".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: map contains requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxMapComponentValue(keyType, argType));
                sb.append("invokeinterface java/util/Map/containsKey(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("remove".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: map remove requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxMapComponentValue(keyType, argType));
                sb.append("invokeinterface java/util/Map/remove(Ljava/lang/Object;)Ljava/lang/Object; 2\n");
                sb.append(unboxMapComponentValue(valueType));
                if (isStatement) {
                    if ("real".equals(valueType)) {
                        sb.append("pop2\n");
                    } else {
                        sb.append("pop\n");
                    }
                }
                return sb.toString();
            }
            if ("clear".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: map clear does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Map/clear()V 1\n");
                return sb.toString();
            }
            if ("size".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: map size does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Map/size()I 1\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("keys".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: map keys() does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Map/keySet()Ljava/util/Set; 1\n");
                return sb.toString();
            }
            if ("values".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: map values() does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Map/values()Ljava/util/Collection; 1\n");
                return sb.toString();
            }
            return "; ERROR: unknown map member " + memberName + "\n";
        }
        if (receiverType.startsWith("set:")) {
            String elementType = receiverType.substring("set:".length());
            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadVar(receiverName));
            if ("insert".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: set insert requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxMapComponentValue(elementType, argType));
                sb.append("invokeinterface java/util/Set/add(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("contains".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: set contains requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxMapComponentValue(elementType, argType));
                sb.append("invokeinterface java/util/Set/contains(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("remove".equals(memberName)) {
                if (args.size() != 1) {
                    return "; ERROR: set remove requires exactly one argument\n";
                }
                PerseusParser.ExprContext argExpr = args.get(0).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                sb.append(boxMapComponentValue(elementType, argType));
                sb.append("invokeinterface java/util/Set/remove(Ljava/lang/Object;)Z 2\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            if ("clear".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: set clear does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Set/clear()V 1\n");
                return sb.toString();
            }
            if ("size".equals(memberName)) {
                if (!args.isEmpty()) {
                    return "; ERROR: set size does not take arguments\n";
                }
                sb.append("invokeinterface java/util/Set/size()I 1\n");
                if (isStatement) {
                    sb.append("pop\n");
                }
                return sb.toString();
            }
            return "; ERROR: unknown set member " + memberName + "\n";
        }
        if ("string".equals(receiverType)) {
            Method javaMethod = findJavaMethod("java.lang.String", memberName, args);
            if (javaMethod == null) {
                return "; ERROR: unknown string member java.lang.String." + memberName + "\n";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadVar(receiverName));
            StringBuilder desc = new StringBuilder("(");
            Class<?>[] parameterTypes = javaMethod.getParameterTypes();
            for (int i = 0; i < args.size(); i++) {
                PerseusParser.ExprContext argExpr = args.get(i).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                if (parameterTypes[i] == double.class && "integer".equals(argType)) {
                    sb.append("i2d\n");
                }
                desc.append(toJvmDescriptor(parameterTypes[i]));
            }
            desc.append(")").append(toJvmDescriptor(javaMethod.getReturnType()));
            sb.append("invokevirtual java/lang/String/").append(memberName).append(desc).append("\n");
            if (isStatement && javaMethod.getReturnType() != void.class) {
                sb.append(javaMethod.getReturnType() == double.class ? "pop2\n" : "pop\n");
            }
            return sb.toString();
        }
        if (!receiverType.startsWith("ref:")) {
            return "; ERROR: member call requires object reference " + receiverName + "\n";
        }
        String objectClass = receiverType.substring("ref:".length());
        SymbolTableBuilder.ClassInfo cls = classes.get(objectClass);
        if (cls == null) {
            return "; ERROR: unknown class " + objectClass + "\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(generateLoadVar(receiverName));
        sb.append("checkcast ").append(ownerInternalName(cls)).append("\n");

        if (cls.externalJava) {
            if (!explicitCall) {
                Field javaField = findJavaField(cls.externalJavaQualifiedName, memberName);
                if (javaField != null) {
                    sb.append("getfield ").append(javaField.getDeclaringClass().getName().replace('.', '/'))
                      .append("/").append(memberName).append(" ").append(toJvmDescriptor(javaField.getType())).append("\n");
                    if (isStatement) {
                        sb.append(javaField.getType() == double.class || javaField.getType() == long.class ? "pop2\n" : "pop\n");
                    }
                    return sb.toString();
                }
            }
            Method javaMethod = findJavaMethod(cls.externalJavaQualifiedName, memberName, args);
            if (javaMethod == null) {
                return "; ERROR: unknown external java class member " + objectClass + "." + memberName + "\n";
            }
            StringBuilder desc = new StringBuilder("(");
            Class<?>[] parameterTypes = javaMethod.getParameterTypes();
            for (int i = 0; i < args.size(); i++) {
                PerseusParser.ExprContext argExpr = args.get(i).expr();
                String argType = exprTypes.getOrDefault(argExpr, "integer");
                sb.append(generateExpr(argExpr));
                if (parameterTypes[i] == double.class && "integer".equals(argType)) {
                    sb.append("i2d\n");
                }
                desc.append(toJvmDescriptor(parameterTypes[i]));
            }
            desc.append(")").append(toJvmDescriptor(javaMethod.getReturnType()));
            if (javaMethod.getDeclaringClass().isInterface()) {
                sb.append("invokeinterface ").append(javaMethod.getDeclaringClass().getName().replace('.', '/'))
                  .append("/").append(memberName).append(desc).append(" ").append(args.size() + 1).append("\n");
            } else {
                sb.append("invokevirtual ").append(javaMethod.getDeclaringClass().getName().replace('.', '/'))
                  .append("/").append(memberName).append(desc).append("\n");
            }
            if (isStatement && javaMethod.getReturnType() != void.class) {
                sb.append(javaMethod.getReturnType() == double.class ? "pop2\n" : "pop\n");
            }
            return sb.toString();
        }

        SymbolTableBuilder.MethodInfo method = findClassMethodInHierarchy(cls, memberName);
        String ownerName = findMethodOwner(cls, memberName);
        if (method == null || ownerName == null) {
            return "; ERROR: unknown class member " + objectClass + "." + memberName + "\n";
        }
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < args.size(); i++) {
            PerseusParser.ExprContext argExpr = args.get(i).expr();
            String argType = exprTypes.getOrDefault(argExpr, "integer");
            String paramName = i < method.paramNames.size() ? method.paramNames.get(i) : null;
            String paramType = paramName != null ? method.paramTypes.getOrDefault(paramName, "integer") : argType;
            sb.append(generateExpr(argExpr));
            if ("real".equals(paramType) && "integer".equals(argType)) {
                sb.append("i2d\n");
            }
            desc.append(CodeGenUtils.getReturnTypeDescriptor(paramType));
        }
        desc.append(")").append(CodeGenUtils.getReturnTypeDescriptor(method.returnType));
        sb.append("invokevirtual ").append(classPackageName).append("/").append(ownerName)
          .append("/").append(memberName).append(desc).append("\n");
        if (isStatement && !"void".equals(method.returnType)) {
            sb.append("real".equals(method.returnType) ? "pop2\n" : "pop\n");
        }
        return sb.toString();
    }

    private String generateExternalProcedureInvocation(String name, SymbolTableBuilder.ProcInfo info,
            List<PerseusParser.ArgContext> args, boolean isStatement) {
        StringBuilder sb = new StringBuilder();
        for (int ai = 0; ai < args.size() && ai < info.paramNames.size(); ai++) {
            PerseusParser.ArgContext arg = args.get(ai);
            String paramType = getFormalBaseType(info, info.paramNames.get(ai));
            if (arg.expr() != null) {
                sb.append(generateExpr(arg.expr()));
                if (paramType.endsWith("[]")) {
                    if (!"java-static".equals(info.externalKind) && arg.expr() instanceof PerseusParser.VarExprContext varExpr) {
                        sb.append(generatePushArrayBounds(varExpr.identifier().getText()));
                    } else if (!"java-static".equals(info.externalKind)) {
                        sb.append("; ERROR: unsupported external array argument form for ")
                          .append(info.paramNames.get(ai)).append("\n");
                    }
                }
                String argType = getExprBaseType(arg.expr());
                if ("real".equals(paramType) && "integer".equals(argType)) {
                    sb.append("i2d\n");
                }
            } else {
                sb.append("; ERROR: unsupported external argument form for ")
                  .append(info.paramNames.get(ai)).append("\n");
            }
        }

        String paramDesc = info.paramNames.stream()
                .map(paramName -> externalTypeToJvmDesc(getFormalBaseType(info, paramName), info.externalKind))
                .collect(Collectors.joining());
        String retDesc = externalTypeToJvmDesc(info.returnType, info.externalKind);
        String targetMethodName = info.externalTargetMethod != null ? info.externalTargetMethod : name;
        sb.append("invokestatic ").append(info.externalTargetClass.replace('.', '/'))
                .append("/").append(targetMethodName)
                .append("(").append(paramDesc).append(")").append(retDesc).append("\n");
        if (isStatement && !"V".equals(retDesc)) {
            if ("D".equals(retDesc)) sb.append("pop2\n");
            else sb.append("pop\n");
        }
        return sb.toString();
    }

    private String generateArrayElementIndex(String name, List<? extends ExprContext> subscripts, Map<String, Integer> varToFieldIndex) {
        if (isCurrentArrayParameter(name)) {
            if (subscripts.size() != 1) {
                return "; ERROR: multidimensional array parameters are not supported yet\n";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(generateExpr(subscripts.get(0), varToFieldIndex));
            if ("real".equals(exprTypes.getOrDefault(subscripts.get(0), "integer"))) {
                sb.append("d2i\n");
            }
            String lowerLoad = generateLoadArrayLowerBound(name);
            if (!lowerLoad.isEmpty()) {
                sb.append(lowerLoad);
                sb.append("isub\n");
            }
            return sb.toString();
        }

        List<int[]> bounds = lookupDeclaredArrayBoundPairs(name);
        if (bounds == null || bounds.isEmpty()) {
            return subscripts.isEmpty() ? "" : generateExpr(subscripts.get(0), varToFieldIndex);
        }
        if (bounds.size() != subscripts.size()) {
            return "; ERROR: wrong number of subscripts for array " + name + "\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(generateNormalizedSubscript(subscripts.get(0), bounds.get(0), varToFieldIndex));
        for (int i = 1; i < subscripts.size(); i++) {
            int extent = bounds.get(i)[1] - bounds.get(i)[0] + 1;
            sb.append("ldc ").append(extent).append("\n");
            sb.append("imul\n");
            sb.append(generateNormalizedSubscript(subscripts.get(i), bounds.get(i), varToFieldIndex));
            sb.append("iadd\n");
        }
        return sb.toString();
    }

    private String getExprBaseType(ExprContext expr) {
        Type baseType = getExprBaseTypeInfo(expr);
        return baseType != null ? baseType.toLegacyString() : "integer";
    }

    private Type getExprBaseTypeInfo(ExprContext expr) {
        if (expr == null) {
            return Type.INTEGER;
        }
        if (expr instanceof PerseusParser.VarExprContext ve) {
            String name = ve.identifier().getText();
            Type varType = lookupVarTypeInfo(name);
            if (varType != null) {
                return varType.unwrapThunk();
            }
            SymbolTableBuilder.ProcInfo procInfo = procedures.get(name);
            if (procInfo != null) {
                return procInfo.returnTypeInfo;
            }
        }
        return exprTypeInfo(expr, Type.INTEGER).unwrapThunk();
    }

    private String typeStoreDescriptor(Type type) {
        if (type == null) {
            return null;
        }
        if (type.isArray()) {
            return arrayTypeToJvmDesc(type);
        }
        if (type.isProcedure()) {
            return getProcedureInterfaceDescriptor(type);
        }
        if (type.isThunk()) {
            return "Lgnb/perseus/compiler/Thunk;";
        }
        return scalarTypeToJvmDesc(type);
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
        return info != null && (!info.nestedProcedures.isEmpty() || info.containsAnonymousProcedures);
    }

    private boolean isCurrentProcedureBridgedLocal(String name) {
        String procName = currentClosureOwnerProcName != null ? currentClosureOwnerProcName : currentProcName;
        if (procName == null || name == null || !useEnvBridge(procName)) return false;
        SymbolTableBuilder.ProcInfo info = procedures.get(procName);
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
        SymbolTableBuilder.ProcInfo closureOwnerInfo = getClosureOwnerInfo();
        if (closureOwnerInfo != null && closureOwnerInfo.paramNames.contains(name)
                && !closureOwnerInfo.valueParams.contains(name)) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(currentClosureOwnerProcName, name)
                + " Lgnb/perseus/compiler/Thunk;\n";
        }
        String outerProc = nearestCapturedOuterProcName();
        if (useEnvBridge(outerProc) && outerProc != null) {
            return "getstatic " + packageName + "/" + className + "/" + envThunkFieldName(outerProc, name)
                + " Lgnb/perseus/compiler/Thunk;\n";
        }
        Type typeInfo = lookupVarTypeInfo(name);
        if (typeInfo != null && typeInfo.isProcedure()) {
            String type = typeInfo.toLegacyString();
            String pDesc = getProcedureInterfaceDescriptor(typeInfo);
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
        if (isCurrentArrayParameter(name)) {
            return new int[]{0, 0};
        }
        int[] bounds = currentArrayBounds.get(name);
        if (bounds == null && mainArrayBounds != null) bounds = mainArrayBounds.get(name);
        return bounds;
    }

    private boolean isReferenceComparison(String leftType, String rightType) {
        return isReferenceLike(leftType) && isReferenceLike(rightType);
    }

    private boolean isReferenceLike(String type) {
        return isReferenceLike(type != null ? Type.parse(type) : null);
    }

    private boolean isReferenceLike(Type type) {
        return type != null && (type.isNull() || type.isRef());
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
        if (ctx instanceof PerseusParser.RelExprContext e) {
            String leftCode  = generateExpr(e.expr(0), varToFieldIndex);
            String rightCode = generateExpr(e.expr(1), varToFieldIndex);
            Type leftTypeInfo  = exprTypeInfo(e.expr(0), Type.INTEGER).unwrapThunk();
            Type rightTypeInfo = exprTypeInfo(e.expr(1), Type.INTEGER).unwrapThunk();
            String leftType  = leftTypeInfo.toLegacyString();
            String rightType = rightTypeInfo.toLegacyString();
            String op = e.op.getText();
            String trueLabel = generateUniqueLabel("rel_true");
            String endLabel  = generateUniqueLabel("rel_end");
            if (isReferenceLike(leftTypeInfo) && isReferenceLike(rightTypeInfo)) {
                String cmpInstr = switch (op) {
                    case "=" -> "if_acmpeq";
                    case "<>" -> "if_acmpne";
                    default -> throw new RuntimeException("Unknown reference rel op " + op);
                };
                return leftCode + rightCode + cmpInstr + " " + trueLabel + "\n" +
                    "iconst_0\ngoto " + endLabel + "\n" +
                    trueLabel + ":\niconst_1\n" +
                    endLabel + ":\n";
            } else if (Type.REAL.equals(leftTypeInfo) || Type.REAL.equals(rightTypeInfo)) {
                // Real comparison: coerce to double, use dcmpg + branch
                if (Type.INTEGER.equals(leftTypeInfo))  leftCode  += "i2d\n";
                if (Type.INTEGER.equals(rightTypeInfo)) rightCode += "i2d\n";
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
        } else if (ctx instanceof PerseusParser.IfExprContext e) {
            // if-then-else as expression (mandatory else branch)
            String condCode  = generateExpr(e.expr(0), varToFieldIndex);
            String thenCode  = generateExpr(e.expr(1), varToFieldIndex);
            String elseCode  = generateExpr(e.expr(2), varToFieldIndex);
            Type resultType = exprTypeInfo(e, Type.INTEGER);
            Type thenType  = exprTypeInfo(e.expr(1), Type.INTEGER);
            Type elseType  = exprTypeInfo(e.expr(2), Type.INTEGER);
            if (Type.REAL.equals(resultType) && Type.INTEGER.equals(thenType)) thenCode += "i2d\n";
            if (Type.REAL.equals(resultType) && Type.INTEGER.equals(elseType)) elseCode += "i2d\n";
            String elseLabel = generateUniqueLabel("ifexpr_else");
            String endLabel  = generateUniqueLabel("ifexpr_end");
            return condCode +
                "ifeq " + elseLabel + "\n" +
                thenCode +
                "goto " + endLabel + "\n" +
                elseLabel + ":\n" +
                elseCode +
                endLabel + ":\n";
        } else if (ctx instanceof PerseusParser.PowExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            Type leftType  = exprTypeInfo(e.expr(0), Type.INTEGER).unwrapThunk();
            Type rightType = exprTypeInfo(e.expr(1), Type.INTEGER).unwrapThunk();
            Type type = exprTypeInfo(ctx, Type.INTEGER);
            if (Type.INTEGER.equals(leftType))  left  += "i2d\n";
            if (Type.INTEGER.equals(rightType)) right += "i2d\n";
            StringBuilder sb = new StringBuilder();
            sb.append(left);
            sb.append(right);
            sb.append("invokestatic java/lang/Math/pow(DD)D\n");
            if (Type.INTEGER.equals(type)) {
                sb.append("d2i\n");
            }
            return sb.toString();
        } else if (ctx instanceof PerseusParser.MulDivExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            Type leftType  = exprTypeInfo(e.expr(0), Type.INTEGER).unwrapThunk();
            Type rightType = exprTypeInfo(e.expr(1), Type.INTEGER).unwrapThunk();
            Type type = exprTypeInfo(ctx, Type.INTEGER);
            if (Type.REAL.equals(type) && Type.INTEGER.equals(leftType))  left  += "i2d\n";
            if (Type.REAL.equals(type) && Type.INTEGER.equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = Type.REAL.equals(type) ?
                ("*".equals(op) ? "dmul" : "ddiv") :
                ("*".equals(op) ? "imul" : "idiv");
            return left + right + instr + "\n";
        } else if (ctx instanceof PerseusParser.AddSubExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            Type leftType  = exprTypeInfo(e.expr(0), Type.INTEGER).unwrapThunk();
            Type rightType = exprTypeInfo(e.expr(1), Type.INTEGER).unwrapThunk();
            Type type = exprTypeInfo(ctx, Type.INTEGER);
            if (Type.REAL.equals(type) && Type.INTEGER.equals(leftType))  left  += "i2d\n";
            if (Type.REAL.equals(type) && Type.INTEGER.equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = Type.REAL.equals(type) ?
                ("+".equals(op) ? "dadd" : "dsub") :
                ("+".equals(op) ? "iadd" : "isub");
            return left + right + instr + "\n";
        } else if (ctx instanceof PerseusParser.AndExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "iand\n";
        } else if (ctx instanceof PerseusParser.OrExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "ior\n";
        } else if (ctx instanceof PerseusParser.ImpExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex)
                + "iconst_1\nixor\n"
                + generateExpr(e.expr(1), varToFieldIndex)
                + "ior\n";
        } else if (ctx instanceof PerseusParser.EqvExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex)
                + generateExpr(e.expr(1), varToFieldIndex)
                + "ixor\n"
                + "iconst_1\nixor\n";
        } else if (ctx instanceof PerseusParser.NotExprContext e) {
            return generateExpr(e.expr(), varToFieldIndex) + "iconst_1\nixor\n";
        } else if (ctx instanceof PerseusParser.NewObjectExprContext e) {
            String objectClass = e.identifier().getText();
            SymbolTableBuilder.ClassInfo cls = classes.get(objectClass);
            StringBuilder sb = new StringBuilder();
            sb.append("new ").append(ownerInternalName(cls)).append("\n");
            sb.append("dup\n");
            List<PerseusParser.ArgContext> args = e.argList() != null ? e.argList().arg() : List.of();
            StringBuilder ctorDesc = new StringBuilder("(");
            if (cls != null && cls.externalJava) {
                Constructor<?> ctor = findJavaConstructor(cls.externalJavaQualifiedName, args);
                if (ctor == null) {
                    return "; ERROR: no matching external java constructor for " + objectClass + "\n";
                }
                Class<?>[] parameterTypes = ctor.getParameterTypes();
                for (int i = 0; i < args.size(); i++) {
                    PerseusParser.ExprContext argExpr = args.get(i).expr();
                    String argType = exprTypes.getOrDefault(argExpr, "integer");
                    sb.append(generateExpr(argExpr, varToFieldIndex));
                    if (parameterTypes[i] == double.class && "integer".equals(argType)) {
                        sb.append("i2d\n");
                    }
                    ctorDesc.append(toJvmDescriptor(parameterTypes[i]));
                }
            } else {
                for (int i = 0; i < args.size(); i++) {
                    PerseusParser.ExprContext argExpr = args.get(i).expr();
                    String argType = exprTypes.getOrDefault(argExpr, "integer");
                    String paramName = cls != null && i < cls.paramNames.size() ? cls.paramNames.get(i) : null;
                    String paramType = paramName != null ? cls.paramTypes.getOrDefault(paramName, "integer") : argType;
                    sb.append(generateExpr(argExpr, varToFieldIndex));
                    if ("real".equals(paramType) && "integer".equals(argType)) {
                        sb.append("i2d\n");
                    }
                    ctorDesc.append(CodeGenUtils.getReturnTypeDescriptor(paramType));
                }
            }
            ctorDesc.append(")V");
            sb.append("invokespecial ").append(ownerInternalName(cls))
              .append("/<init>").append(ctorDesc).append("\n");
            return sb.toString();
        } else if (ctx instanceof PerseusParser.MemberCallExprContext e) {
            return generateMemberInvocation(
                    e.identifier(0).getText(),
                    e.identifier(1).getText(),
                    e.argList() != null ? e.argList().arg() : List.of(),
                    e.argList() != null,
                    false);
        } else if (ctx instanceof PerseusParser.AnonymousProcedureExprContext e) {
            return generateAnonymousProcedureReference(e);
        } else if (ctx instanceof PerseusParser.VectorLiteralExprContext e) {
            String literalType = exprTypes.getOrDefault(e, "vector:integer");
            String elementType = literalType.startsWith("vector:")
                    ? literalType.substring("vector:".length())
                    : "integer";
            StringBuilder sb = new StringBuilder();
            sb.append("new java/util/ArrayList\n");
            sb.append("dup\n");
            sb.append("invokespecial java/util/ArrayList/<init>()V\n");
            for (PerseusParser.ExprContext elementExpr : e.expr()) {
                String elementExprType = exprTypes.getOrDefault(elementExpr, "integer");
                sb.append("dup\n");
                sb.append(generateExpr(elementExpr, varToFieldIndex));
                sb.append(boxVectorElementValue(elementType, elementExprType));
                sb.append("invokeinterface java/util/List/add(Ljava/lang/Object;)Z 2\n");
                sb.append("pop\n");
            }
            return sb.toString();
        } else if (ctx instanceof PerseusParser.SetLiteralExprContext e) {
            String literalType = exprTypes.getOrDefault(e, "set:integer");
            String elementType = literalType.startsWith("set:")
                    ? literalType.substring("set:".length())
                    : "integer";
            StringBuilder sb = new StringBuilder();
            sb.append("new java/util/LinkedHashSet\n");
            sb.append("dup\n");
            sb.append("invokespecial java/util/LinkedHashSet/<init>()V\n");
            for (PerseusParser.ExprContext elementExpr : e.expr()) {
                String elementExprType = exprTypes.getOrDefault(elementExpr, "integer");
                sb.append("dup\n");
                sb.append(generateExpr(elementExpr, varToFieldIndex));
                sb.append(boxMapComponentValue(elementType, elementExprType));
                sb.append("invokeinterface java/util/Set/add(Ljava/lang/Object;)Z 2\n");
                sb.append("pop\n");
            }
            return sb.toString();
        } else if (ctx instanceof PerseusParser.MapLiteralExprContext e) {
            String literalType = exprTypes.getOrDefault(e, "map:string=>integer");
            String keyType = mapKeyType(literalType);
            String valueType = mapValueType(literalType);
            StringBuilder sb = new StringBuilder();
            sb.append("new java/util/LinkedHashMap\n");
            sb.append("dup\n");
            sb.append("invokespecial java/util/LinkedHashMap/<init>()V\n");
            for (PerseusParser.MapLiteralEntryContext entry : e.mapLiteralEntry()) {
                String keyExprType = exprTypes.getOrDefault(entry.expr(0), "integer");
                String valueExprType = exprTypes.getOrDefault(entry.expr(1), "integer");
                sb.append("dup\n");
                sb.append(generateExpr(entry.expr(0), varToFieldIndex));
                sb.append(boxMapComponentValue(keyType, keyExprType));
                sb.append(generateExpr(entry.expr(1), varToFieldIndex));
                sb.append(boxMapComponentValue(valueType, valueExprType));
                sb.append("invokeinterface java/util/Map/put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; 3\n");
                sb.append("pop\n");
            }
            return sb.toString();
        } else if (ctx instanceof PerseusParser.VarExprContext e) {
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
                return "invokestatic perseus/lang/MathEnv/maxreal()D\n";
            } else if ("minreal".equals(name)) {
                return "invokestatic perseus/lang/MathEnv/minreal()D\n";
            } else if ("maxint".equals(name)) {
                return "invokestatic perseus/lang/MathEnv/maxint()I\n";
            } else if ("epsilon".equals(name)) {
                return "invokestatic perseus/lang/MathEnv/epsilon()D\n";
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
                if ("real".equals(currentProcReturnType) || "deferred".equals(currentProcReturnType)) {
                    return "dload " + procRetvalSlot + "\n";
                } else if (isObjectType(currentProcReturnType)) {
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

            SymbolTableBuilder.ExternalValueInfo externalValue = externalJavaStaticValues.get(name);
            if (externalValue != null) {
                return "getstatic " + externalValue.ownerClass.replace('.', '/') + "/" + externalValue.targetMember
                        + " " + externalValueJvmDesc(externalValue.type) + "\n";
            }

            String lookupType = lookupVarType(name);
            if (lookupType != null && lookupType.startsWith("thunk:")) {
                String baseType = lookupType.substring("thunk:".length());
                StringBuilder sb = new StringBuilder();
                Integer idx = currentLocalIndex.get(name);
                if (idx != null) {
                    sb.append("aload ").append(idx).append("\n");
                } else {
                    sb.append(generateLoadThunkRef(name));
                }
                sb.append("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                switch (baseType) {
                    case "real" -> {
                        sb.append("checkcast java/lang/Double\n");
                        sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    }
                    case "deferred" -> {
                        String inferredType = exprTypes.getOrDefault(ctx, "real");
                        sb.append(dynamicUnboxDeferredValue(inferredType));
                    }
                    case "string" -> sb.append("checkcast java/lang/String\n");
                    default -> {
                        sb.append("checkcast java/lang/Integer\n");
                        sb.append("invokevirtual java/lang/Integer/intValue()I\n");
                    }
                }
                return sb.toString();
            }

            return generateLoadVar(name);
        } else if (ctx instanceof PerseusParser.ArrayAccessExprContext e) {
            String arrName = e.identifier().getText();
            String elemType = lookupVarType(arrName);
            if (elemType == null) return "; ERROR: undeclared array " + arrName + "\n";

            if (elemType.startsWith("vector:")) {
                if (e.expr().size() != 1) {
                    return "; ERROR: vector indexing currently requires exactly one subscript\n";
                }
                String vectorElementType = elemType.substring("vector:".length());
                StringBuilder sb = new StringBuilder();
                sb.append(generateLoadVar(arrName));
                sb.append(generateExpr(e.expr(0), varToFieldIndex));
                sb.append("invokeinterface java/util/List/get(I)Ljava/lang/Object; 2\n");
                sb.append(unboxVectorElementValue(vectorElementType));
                return sb.toString();
            }
            if (elemType.startsWith("map:")) {
                if (e.expr().size() != 1) {
                    return "; ERROR: map access currently requires exactly one subscript\n";
                }
                StringBuilder sb = new StringBuilder();
                sb.append(generateLoadVar(arrName));
                sb.append(generateExpr(e.expr(0), varToFieldIndex));
                sb.append(boxMapComponentValue(mapKeyType(elemType), exprTypes.getOrDefault(e.expr(0), "integer")));
                sb.append("invokeinterface java/util/Map/get(Ljava/lang/Object;)Ljava/lang/Object; 2\n");
                sb.append(unboxMapComponentValue(mapValueType(elemType)));
                return sb.toString();
            }

            // String scalar character access: s[i] -> s.substring(i-1, i)
            if ("string".equals(elemType) && e.expr().size() == 1) {
                StringBuilder sb = new StringBuilder();
                sb.append(generateLoadVar(arrName));                         // load s
                sb.append(generateExpr(e.expr(0), varToFieldIndex));         // load i
                sb.append("dup\n");                                          // i, i
                sb.append("iconst_1\n").append("isub\n");                    // i-1, i  (beginIndex)
                sb.append("swap\n");                                         // i-1, i  → correct order for (II)
                sb.append("invokevirtual java/lang/String/substring(II)Ljava/lang/String;\n");
                return sb.toString();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(generateLoadVar(arrName));
            sb.append(generateArrayElementIndex(arrName, e.expr(), varToFieldIndex));
            boolean refArray = elemType != null && elemType.startsWith("ref:") && elemType.endsWith("[]");
            sb.append("real[]".equals(elemType) ? "daload\n"
                    : "boolean[]".equals(elemType) ? "baload\n"
                    : ("string[]".equals(elemType) || refArray) ? "aaload\n"
                    : "iaload\n");
            return sb.toString();
        } else if (ctx instanceof PerseusParser.ProcCallExprContext e) {
            String procName = e.identifier().getText();
            List<PerseusParser.ArgContext> callArgs = e.argList() != null ? e.argList().arg() : List.of();
            String builtinProcedureCode = generateBuiltinProcedureExpr(procName, callArgs);
            if (builtinProcedureCode != null) {
                return builtinProcedureCode;
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
                    callCode = generateProcedureVariableCall(procName, varType, callArgs);
                } else {
                    callCode = generateUserProcedureInvocation(procName, callArgs, false);
                }
            } else if (isProcVar) {
                callCode = generateProcedureVariableCall(procName, varType, callArgs);
            } else {
                String builtinCode = generateBuiltinMathFunction(procName, e);
                if (builtinCode != null) {
                    return builtinCode;
                }
                // Fall back to ordinary procedure invocation (should not usually happen)
                callCode = generateUserProcedureInvocation(procName, callArgs, false);
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
        } else if (ctx instanceof PerseusParser.RealLiteralExprContext e) {
            return "ldc2_w " + e.realLiteral().getText() + "d\n";
        } else if (ctx instanceof PerseusParser.IntLiteralExprContext e) {
            return "ldc " + e.unsignedInt().getText() + "\n";
        } else if (ctx instanceof PerseusParser.StringLiteralExprContext e) {
            return "ldc " + e.string().getText() + "\n";
        } else if (ctx instanceof PerseusParser.NullLiteralExprContext) {
            return "aconst_null\n";
        } else if (ctx instanceof PerseusParser.TrueLiteralExprContext) {
            return "iconst_1\n";
        } else if (ctx instanceof PerseusParser.FalseLiteralExprContext) {
            return "iconst_0\n";
        } else if (ctx instanceof PerseusParser.UnaryMinusExprContext e) {
            String type = exprTypes.getOrDefault(ctx, "integer");
            String inner = generateExpr(e.expr(), varToFieldIndex);
            if ("real".equals(type)) {
                return inner + "dneg\n";
            } else {
                return inner + "ineg\n";
            }
        } else if (ctx instanceof PerseusParser.ParenExprContext e) {
            return generateExpr(e.expr(), varToFieldIndex);
        }
        return "; unknown expr type\n";
    }

    /**
     * Delegates to {@link BuiltinFunctionGenerator} which handles math builtins
     * (sqrt, abs, sin, …) and string builtins (length, concat, substring) separately.
     * Returns null if the function name is not a recognized built-in.
     */
    private String generateBuiltinMathFunction(String funcName, PerseusParser.ProcCallExprContext ctx) {
        return builtinGen.generate(funcName, ctx);
    }

    private String generateBuiltinProcedureExpr(String name, List<PerseusParser.ArgContext> args) {
        StringBuilder sb = new StringBuilder();
        if (channelGen.tryEmitProcedureCall(name, args, sb, currentLocalIndex, currentSymbolTable,
                mainSymbolTable, this::generateExpr, e -> exprTypes.getOrDefault(e, "integer"),
                this::allocateNewLocal, this::getChannelStream, this::lookupVarType,
                this::staticFieldName, this::generateOpenStringTargetThunk)) {
            return sb.toString();
        }
        if ("outchar".equals(name)) {
            if (args.size() > 0 && args.get(0).expr() != null) {
                sb.append(generateExpr(args.get(0).expr()));
            } else {
                sb.append("iconst_1\n");
            }
            sb.append(generateExpr(args.get(1).expr()))
              .append(generateExpr(args.get(2).expr()))
              .append("invokestatic perseus/io/TextOutput/outchar(ILjava/lang/String;I)V\n");
            return sb.toString();
        }
        if ("stop".equals(name)) {
            sb.append("iconst_0\n")
              .append("invokestatic java/lang/System/exit(I)V\n");
            return sb.toString();
        }
        return null;
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

    private String mapLambdaParamType(PerseusParser.LambdaParamTypeContext typeCtx) {
        if (typeCtx == null) return "integer";
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
        if (typeCtx.vectorType() != null) return "vector:" + mapAnonymousVectorElementType(typeCtx.vectorType().vectorElementType());
        return "integer";
    }

    private String mapLambdaReturnType(PerseusParser.LambdaReturnTypeContext typeCtx) {
        if (typeCtx == null) return "integer";
        if (typeCtx.VOID() != null) return "void";
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
        if (typeCtx.vectorType() != null) return "vector:" + mapAnonymousVectorElementType(typeCtx.vectorType().vectorElementType());
        return "integer";
    }

    private String lambdaParameterDescriptor(String type) {
        if (type == null) return "I";
        if ("real".equals(type)) return "D";
        if ("integer".equals(type) || "boolean".equals(type)) return "I";
        if (isObjectType(type)) return CodeGenUtils.getReturnTypeDescriptor(type);
        throw new RuntimeException("Unsupported anonymous procedure parameter type: " + type);
    }

    private String mapAnonymousLocalVarType(PerseusParser.VarDeclContext ctx) {
        if (ctx.REAL() != null) return "real";
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.PROCEDURE() != null) return "procedure:void";
        return "integer";
    }

    private String mapAnonymousVectorElementType(PerseusParser.VectorElementTypeContext ctx) {
        if (ctx.REAL() != null) return "real";
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.refType() != null) return "ref:" + ctx.refType().identifier().getText();
        return "integer";
    }

    private record AnonymousLocalDecl(String name, String type) {}

    private List<AnonymousLocalDecl> collectAnonymousBodyLocals(PerseusParser.AnonymousProcedureCompoundContext compoundCtx) {
        List<AnonymousLocalDecl> locals = new ArrayList<>();
        if (compoundCtx == null) {
            return locals;
        }
        List<PerseusParser.StatementContext> statements = List.of();
        if (compoundCtx instanceof PerseusParser.AnonymousStatementExprProcedureCompoundContext stmtExpr) {
            statements = stmtExpr.statement();
        } else if (compoundCtx instanceof PerseusParser.AnonymousStatementProcedureCompoundContext stmtsOnly) {
            statements = stmtsOnly.statement();
        }
        for (PerseusParser.StatementContext stmt : statements) {
            if (stmt.varDecl() != null) {
                String type = mapAnonymousLocalVarType(stmt.varDecl());
                if (type.startsWith("procedure:")) {
                    throw new RuntimeException("Anonymous procedure block bodies do not yet support local procedure declarations");
                }
                for (PerseusParser.IdentifierContext id : stmt.varDecl().varList().identifier()) {
                    locals.add(new AnonymousLocalDecl(id.getText(), type));
                }
            } else if (stmt.vectorDecl() != null) {
                String type = "vector:" + mapAnonymousVectorElementType(stmt.vectorDecl().vectorElementType());
                for (PerseusParser.IdentifierContext id : stmt.vectorDecl().varList().identifier()) {
                    locals.add(new AnonymousLocalDecl(id.getText(), type));
                }
            } else if (stmt.refDecl() != null) {
                String type = "ref:" + stmt.refDecl().identifier().getText();
                for (PerseusParser.IdentifierContext id : stmt.refDecl().varList().identifier()) {
                    locals.add(new AnonymousLocalDecl(id.getText(), type));
                }
            } else if (stmt.arrayDecl() != null || stmt.procedureDecl() != null || stmt.classDecl() != null
                    || stmt.externalClassDecl() != null || stmt.externalProcedureDecl() != null
                    || stmt.externalValueDecl() != null || stmt.switchDecl() != null) {
                throw new RuntimeException("Anonymous procedure block bodies do not yet support this kind of declaration");
            }
        }
        return locals;
    }

    private void initializeAnonymousLocal(String name, String type) {
        int slot = currentLocalIndex.get(name);
        if ("real".equals(type) || "deferred".equals(type)) {
            activeOutput.append("dconst_0\n");
            emitStore("dstore", slot);
        } else if (type != null && type.startsWith("vector:")) {
            activeOutput.append("new java/util/ArrayList\n");
            activeOutput.append("dup\n");
            activeOutput.append("invokespecial java/util/ArrayList/<init>()V\n");
            emitStore("astore", slot);
        } else if (type != null && type.startsWith("map:")) {
            activeOutput.append("new java/util/LinkedHashMap\n");
            activeOutput.append("dup\n");
            activeOutput.append("invokespecial java/util/LinkedHashMap/<init>()V\n");
            emitStore("astore", slot);
        } else if ("string".equals(type)) {
            activeOutput.append("ldc \"\"\n");
            emitStore("astore", slot);
        } else if (type != null && type.startsWith("ref:")) {
            activeOutput.append("aconst_null\n");
            emitStore("astore", slot);
        } else {
            activeOutput.append("iconst_0\n");
            emitStore("istore", slot);
        }
    }

    private PerseusParser.ExprContext anonymousBodyResultExpr(PerseusParser.AnonymousProcedureBodyContext bodyCtx) {
        if (bodyCtx instanceof PerseusParser.AnonymousExprProcedureBodyContext exprBody) {
            return exprBody.expr();
        }
        PerseusParser.AnonymousProcedureCompoundContext compound = null;
        if (bodyCtx instanceof PerseusParser.AnonymousBlockProcedureBodyContext blockBody) {
            compound = blockBody.anonymousProcedureCompound();
        }
        if (compound instanceof PerseusParser.AnonymousStatementExprProcedureCompoundContext stmtExpr) {
            return stmtExpr.expr();
        }
        if (compound instanceof PerseusParser.AnonymousExprProcedureCompoundContext exprOnly) {
            return exprOnly.expr();
        }
        return null;
    }

    private void emitAnonymousProcedureBody(PerseusParser.AnonymousProcedureBodyContext bodyCtx, SymbolTableBuilder.ProcInfo procInfo) {
        if (bodyCtx instanceof PerseusParser.AnonymousExprProcedureBodyContext exprBody) {
            activeOutput.append(generateExpr(exprBody.expr()));
            return;
        }

        PerseusParser.AnonymousProcedureCompoundContext compound = null;
        if (bodyCtx instanceof PerseusParser.AnonymousBlockProcedureBodyContext blockBody) {
            compound = blockBody.anonymousProcedureCompound();
        }
        if (compound == null) {
            return;
        }

        for (AnonymousLocalDecl local : collectAnonymousBodyLocals(compound)) {
            if (currentSymbolTable.containsKey(local.name())) {
                throw new RuntimeException("Duplicate anonymous procedure local: " + local.name());
            }
            int slot = currentNumLocals;
            currentSymbolTable.put(local.name(), local.type());
            currentLocalIndex.put(local.name(), slot);
            currentNumLocals += "real".equals(local.type()) ? 2 : 1;
            initializeAnonymousLocal(local.name(), local.type());
        }
        ensureLocalLimit(Math.max(currentNumLocals, 64));

        boolean savedEmittingAnonymousBody = emittingAnonymousBody;
        emittingAnonymousBody = true;
        try {
            ParseTreeWalker.DEFAULT.walk(this, compound);
        } finally {
            emittingAnonymousBody = savedEmittingAnonymousBody;
        }

        PerseusParser.ExprContext resultExpr = anonymousBodyResultExpr(bodyCtx);
        if (resultExpr != null) {
            activeOutput.append(generateExpr(resultExpr));
        } else if (!"void".equals(procInfo.returnType)) {
            throw new RuntimeException("Non-void anonymous procedure block bodies must end with a result expression");
        }
    }

    private String generateAnonymousProcedureReference(PerseusParser.AnonymousProcedureExprContext ctx) {
        String existing = anonymousProcedureNames.get(ctx);
        if (existing != null) {
            return generateProcedureReference(existing, procedures.get(existing));
        }

        String procName = "__anonproc" + anonymousProcedureCounter++;
        anonymousProcedureNames.put(ctx, procName);

        SymbolTableBuilder.ProcInfo procInfo = new SymbolTableBuilder.ProcInfo(mapLambdaReturnType(ctx.lambdaReturnType()));
        if (ctx.lambdaParamList() != null) {
            for (PerseusParser.LambdaParamContext param : ctx.lambdaParamList().lambdaParam()) {
                String paramName = param.identifier().getText();
                String paramType = mapLambdaParamType(param.lambdaParamType());
                procInfo.paramNames.add(paramName);
                procInfo.paramTypes.put(paramName, paramType);
                procInfo.valueParams.add(paramName);
            }
        }
        procedures.put(procName, procInfo);

        Map<String, String> savedSymbolTable = currentSymbolTable;
        Map<String, Type> savedSymbolTableTypes = currentSymbolTableTypes;
        Map<String, Integer> savedLocalIndex = currentLocalIndex;
        int savedNumLocals = currentNumLocals;
        String savedProcName = currentProcName;
        String savedClosureOwnerProcName = currentClosureOwnerProcName;
        String savedProcReturnType = currentProcReturnType;
        int savedProcRetvalSlot = procRetvalSlot;
        StringBuilder savedActiveOutput = activeOutput;

        StringBuilder method = new StringBuilder();
        activeOutput = method;
        currentSymbolTable = new LinkedHashMap<>();
        currentSymbolTableTypes = new LinkedHashMap<>();
        currentLocalIndex = new LinkedHashMap<>();
        currentNumLocals = 0;
        currentProcName = null;
        currentClosureOwnerProcName = savedProcName != null ? savedProcName : savedClosureOwnerProcName;
        currentProcReturnType = procInfo.returnType;
        procRetvalSlot = -1;

        StringBuilder paramDesc = new StringBuilder();
        for (String paramName : procInfo.paramNames) {
            String paramType = procInfo.paramTypes.get(paramName);
            currentSymbolTable.put(paramName, paramType);
            currentLocalIndex.put(paramName, currentNumLocals);
            paramDesc.append(lambdaParameterDescriptor(paramType));
            currentNumLocals += "real".equals(paramType) ? 2 : 1;
        }

        method.append(".method public static ").append(procName)
              .append("(").append(paramDesc).append(")")
              .append(CodeGenUtils.getReturnTypeDescriptor(procInfo.returnType)).append("\n");
        method.append(".limit stack 64\n");
        method.append(".limit locals 64\n");
        emitAnonymousProcedureBody(ctx.anonymousProcedureBody(), procInfo);
        PerseusParser.ExprContext resultExpr = anonymousBodyResultExpr(ctx.anonymousProcedureBody());
        String bodyType = resultExpr != null ? exprTypes.getOrDefault(resultExpr, "integer") : "void";
        if ("real".equals(procInfo.returnType) && "integer".equals(bodyType)) {
            method.append("i2d\n");
        }
        method.append(getReturnInstruction(procInfo.returnType)).append("\n");
        method.append(".end method\n\n");

        activeOutput = savedActiveOutput;
        currentSymbolTable = savedSymbolTable;
        currentSymbolTableTypes = savedSymbolTableTypes;
        currentLocalIndex = savedLocalIndex;
        currentNumLocals = savedNumLocals;
        currentProcName = savedProcName;
        currentClosureOwnerProcName = savedClosureOwnerProcName;
        currentProcReturnType = savedProcReturnType;
        procRetvalSlot = savedProcRetvalSlot;

        procMethods.add(method.toString());
        return generateProcedureReference(procName, procInfo);
    }

    private static boolean isObjectType(String type) {
        return "string".equals(type)
                || (type != null && (type.startsWith("ref:") || type.startsWith("vector:") || type.startsWith("map:") || type.startsWith("iterable:") || type.startsWith("procedure:")));
    }

    private static boolean isObjectType(Type type) {
        return type != null && (Type.STRING.equals(type)
                || type.isRef()
                || type.isVector()
                || type.isMap()
                || type.isSet()
                || type.isIterable()
                || type.isProcedure());
    }

    private String getProcedureInterfaceDescriptor(String procType) {
        return CodeGenUtils.getProcedureInterfaceDescriptor(procType);
    }

    private String getProcedureInterfaceDescriptor(Type procType) {
        return CodeGenUtils.getProcedureInterfaceDescriptor(procType);
    }

    private String getProcedureInterfaceInvokeReturnDescriptor(String procType) {
        String interfaceDesc = getProcedureInterfaceDescriptor(procType);
        if ("Lgnb/perseus/compiler/ReferenceProcedure;".equals(interfaceDesc)) {
            return "Ljava/lang/Object;";
        }
        return CodeGenUtils.getReturnTypeDescriptor(procType.substring("procedure:".length()));
    }

    private String getProcedureInterfaceInvokeReturnDescriptor(Type procType) {
        String interfaceDesc = getProcedureInterfaceDescriptor(procType);
        if ("Lgnb/perseus/compiler/ReferenceProcedure;".equals(interfaceDesc)) {
            return "Ljava/lang/Object;";
        }
        return CodeGenUtils.getReturnTypeDescriptor(procType.elementType());
    }

    /**
     * Generates code to call a procedure through a procedure variable.
     * Delegates to ProcedureGenerator when the variable is stored in a local slot.
     * Falls back to a static field lookup if the variable is stored in the outer scope.
     */
    private String generateProcedureVariableCall(String varName, String varType, List<PerseusParser.ArgContext> args) {
        return generateProcedureVariableCall(varName, Type.parse(varType), args);
    }

    private String generateProcedureVariableCall(String varName, Type varType, List<PerseusParser.ArgContext> args) {
        // Prefer calling through a local slot if this procedure variable is stored locally.
        Integer slot = currentLocalIndex.get(varName);
        if (slot != null) {
            // Use local slot (allows procedure-variable parameters and local procedure variables)
            return procGen.generateProcedureVariableCall(varName, varType.toLegacyString(), args);
        }
        // Otherwise, the procedure variable is stored in a static field.
        return generateProcedureVariableCallViaStaticField(varName, varType, args);
    }

    private String generateProcedureVariableLoad(String varName, String varType) {
        return generateProcedureVariableLoad(varName, Type.parse(varType));
    }

    private String generateProcedureVariableLoad(String varName, Type varType) {
        String desc = getProcedureInterfaceDescriptor(varType);
        Integer slot = currentLocalIndex.get(varName);
        if (slot != null) {
            return "aload " + slot + "\n";
        }
        // Fallback to static field if we don't have a local slot
        return "getstatic " + packageName + "/" + className + "/" + staticFieldName(varName, varType.toLegacyString()) + " " + desc + "\n";
    }

    private String generateProcedureVariableCallViaStaticField(String varName, Type varType, List<PerseusParser.ArgContext> args) {
        // Load the procedure reference from the static field instead of a local slot.
        String load = generateProcedureVariableLoad(varName, varType);
        // Build the argument array and invoke the method on the procedure interface.
        Type returnType = varType.elementType();
        String interfaceName = getProcedureInterfaceDescriptor(varType)
                .replace("Lgnb/perseus/compiler/", "")
                .replace(";", "");
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
                PerseusParser.ExprContext argExpr = args.get(i).expr();

                boolean isByName = false;
                Type paramBaseType = Type.INTEGER;
                if (targetInfo != null && i < targetInfo.paramNames.size()) {
                    String paramName = targetInfo.paramNames.get(i);
                    isByName = !targetInfo.valueParams.contains(paramName);
                    paramBaseType = getFormalBaseTypeInfo(targetInfo, paramName);
                }

                sb.append("dup\n");
                sb.append("ldc ").append(i).append("\n");

                if (isByName) {
                    // Pass a Thunk for by-name parameters. If the source expression is already
                    // a thunk variable, use it directly; otherwise create a thunk wrapper.
                    if (argExpr instanceof PerseusParser.VarExprContext argVar) {
                        String vn = argVar.identifier().getText();
                        Type vnType = lookupVarTypeInfo(vn);
                        if (vnType != null && vnType.isThunk()) {
                            sb.append(generateLoadThunkRef(vn));
                        } else {
                            Type thunkType = Type.DEFERRED.equals(paramBaseType) ? getExprBaseTypeInfo(argExpr) : paramBaseType;
                            String thunkClass = createThunkClass(new LinkedHashMap<>(), argExpr, thunkType.toLegacyString());
                            sb.append("new ").append(thunkClass).append("\n");
                            sb.append("dup\n");
                            sb.append("invokespecial ").append(thunkClass).append("/<init>()V\n");
                        }
                    } else {
                        Type thunkType = Type.DEFERRED.equals(paramBaseType) ? getExprBaseTypeInfo(argExpr) : paramBaseType;
                        String thunkClass = createThunkClass(new LinkedHashMap<>(), argExpr, thunkType.toLegacyString());
                        sb.append("new ").append(thunkClass).append("\n");
                        sb.append("dup\n");
                        sb.append("invokespecial ").append(thunkClass).append("/<init>()V\n");
                    }
                } else {
                    Type baseType = exprTypeInfo(argExpr, Type.INTEGER).unwrapThunk();
                    sb.append(generateExpr(argExpr));
                    if (Type.REAL.equals(baseType)) {
                        sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                    } else if (usesIntegerStorage(baseType)) {
                        sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                    }
                }

                sb.append("aastore\n");
            }
        }

        sb.append("invokeinterface gnb/perseus/compiler/")
          .append(interfaceName)
          .append("/invoke([Ljava/lang/Object;)")
          .append(getProcedureInterfaceInvokeReturnDescriptor(varType))
          .append(" 2\n");
        if (returnType.isProcedure()) {
            String nestedDesc = getProcedureInterfaceDescriptor(returnType);
            sb.append("checkcast ")
              .append(nestedDesc.substring(1, nestedDesc.length() - 1))
              .append("\n");
        }
        return sb.toString();
    }

}




