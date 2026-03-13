package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.CodeGenUtils;
import gnb.jalgol.compiler.SymbolTableBuilder;
import gnb.jalgol.compiler.SymbolTableBuilder.ProcInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

public class ProcedureGenerator implements GeneratorDelegate {
    private ContextManager context;
    private ExpressionGenerator exprGen;

    // --- Callback fields for CodeGenerator-delegated construction ---
    private String packageName;
    private String className;
    private IntSupplier nextProcRefId;
    private BiConsumer<String, String> storeProcRefClass;
    private Function<String, Integer> lookupLocalSlot;
    private Map<AlgolParser.ExprContext, String> exprTypes;
    private Function<AlgolParser.ExprContext, String> generateExprFn;

    public ProcedureGenerator(ContextManager context, ExpressionGenerator exprGen) {
        this.context = context;
        this.exprGen = exprGen;
        // Wire callbacks so generateProcedureReference/generateProcedureVariableCall work with this path too
        this.packageName       = context.getPackageName();
        this.className         = context.getClassName();
        this.nextProcRefId     = () -> { int id = context.getProcRefId(); context.incrementProcRefId(); return id; };
        this.storeProcRefClass = (name, content) -> context.addProcRefClass(name, content);
        this.lookupLocalSlot   = varName -> context.getLocalIndex().get(varName);
        this.exprTypes         = context.getExprTypes();
        this.generateExprFn    = e -> exprGen.generateExpr(e);
    }

    public ProcedureGenerator(ExpressionGenerator exprGen) {
        this.exprGen = exprGen;
    }

    public ProcedureGenerator(String packageName, String className,
                               IntSupplier nextProcRefId,
                               BiConsumer<String, String> storeProcRefClass,
                               Function<String, Integer> lookupLocalSlot,
                               Map<AlgolParser.ExprContext, String> exprTypes,
                               Function<AlgolParser.ExprContext, String> generateExprFn) {
        this.packageName      = packageName;
        this.className        = className;
        this.nextProcRefId    = nextProcRefId;
        this.storeProcRefClass = storeProcRefClass;
        this.lookupLocalSlot  = lookupLocalSlot;
        this.exprTypes        = exprTypes;
        this.generateExprFn   = generateExprFn;
    }

    private String generateExpr(AlgolParser.ExprContext ctx) {
        return exprGen.generateExpr(ctx);
    }

    private int allocateNewLocal(String hint) {
        int slot = context.getNextLocalIndex();
        context.getLocalIndex().put(hint, slot);
        context.setNextLocalIndex(slot + (hint.contains("box") || hint.contains("real") ? 2 : 1));
        return slot;
    }

    private Set<String> collectVarNames(ParseTree tree) {
        Set<String> names = new LinkedHashSet<>();
        if (tree instanceof AlgolParser.VarExprContext) {
            names.add(((AlgolParser.VarExprContext) tree).identifier().getText());
        } else {
            for (int i = 0; i < tree.getChildCount(); i++) {
                names.addAll(collectVarNames(tree.getChild(i)));
            }
        }
        return names;
    }

    public String createThunkClass(Map<String, Integer> varToField, AlgolParser.ExprContext actual, String baseType) {
        String thunkClassName = "Thunk" + context.getThunkId();
        context.incrementThunkId();
        StringBuilder sb = new StringBuilder();
        sb.append(".class public ").append(thunkClassName).append("\n");
        sb.append(".super java/lang/Object\n");
        sb.append(".implements gnb/jalgol/runtime/Thunk\n\n");

        // Emit per-instance fields for each closed-over variable (mutable one-element boxes)
        Map<String, String> varTypes = new LinkedHashMap<>();
        for (String vn : varToField.keySet()) {
            String type = context.getMainSymbolTable() != null ? context.getMainSymbolTable().get(vn) : null;
            varTypes.put(vn, type);
            sb.append(".field private ").append(vn).append(" [Ljava/lang/Object;\n");
        }

        // Constructor: accept each closed-over variable as parameter (Object[] box per var)
        sb.append("\n.method public <init>(");
        for (String vn : varToField.keySet()) {
            sb.append("[Ljava/lang/Object;");
        }
        sb.append(")V\n");
        sb.append("aload_0\ninvokespecial java/lang/Object/<init>()V\n");
        int fi = 1;
        for (String vn : varToField.keySet()) {
            sb.append("aload_0\n");
            sb.append("aload ").append(fi).append("\n");
            fi++;
            sb.append("putfield ").append(thunkClassName).append("/").append(vn).append(" [Ljava/lang/Object;\n");
        }
        sb.append("return\n.end method\n\n");

        // eval() method: use instance fields
        sb.append(".method public eval()Ljava/lang/Object;\n");
        sb.append(".limit stack 10\n.limit locals 10\n");

        context.pushOutput(new StringBuilder());
        Map<String, String> oldSym = context.getSymbolTable();
        Map<String, Integer> oldIdx = context.getLocalIndex();
        Map<String, String> thunkSym = context.getMainSymbolTable() != null ? new HashMap<>(context.getMainSymbolTable()) : new HashMap<>();
        Map<String, Integer> thunkIdx = new HashMap<>();
        context.setSymbolTable(thunkSym);
        context.setLocalIndex(thunkIdx);

        // Load each closed-over variable from instance field (box) into local
        int localSlot = 1;
        for (String vn : varToField.keySet()) {
            String type = varTypes.get(vn);
            sb.append("aload_0\ngetfield ").append(thunkClassName).append("/").append(vn).append(" [Ljava/lang/Object;\n");
            sb.append("iconst_0\naaload\n");
            if ("real".equals(type)) {
                sb.append("checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n");
                sb.append("dstore ").append(localSlot).append("\n");
                thunkIdx.put(vn, localSlot);
                localSlot += 2;
            } else if ("string".equals(type)) {
                sb.append("checkcast java/lang/String\n");
                sb.append("astore ").append(localSlot).append("\n");
                thunkIdx.put(vn, localSlot);
                localSlot++;
            } else {
                sb.append("checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n");
                sb.append("istore ").append(localSlot).append("\n");
                thunkIdx.put(vn, localSlot);
                localSlot++;
            }
        }

        sb.append(generateExpr(actual));
        if ("real".equals(baseType)) {
            sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
        } else if ("integer".equals(baseType) || "boolean".equals(baseType)) {
            sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
        }
        sb.append("areturn\n.end method\n");

        context.setSymbolTable(oldSym);
        context.setLocalIndex(oldIdx);
        context.popOutput();

        context.addThunkClass(sb.toString());
        return thunkClassName;
    }

    public String generateProcedureCall(String name, List<AlgolParser.ArgContext> args, boolean isStatement) {
        ProcInfo info = context.getProcedures().get(name);
        if (info == null) {
            return "; unknown procedure: " + name + "\n";
        }

        StringBuilder sb = new StringBuilder();
        Map<String, Integer> varToBoxSlot = new LinkedHashMap<>();

        for (int ai = 0; ai < args.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            if (!info.valueParams.contains(paramName)) {
                AlgolParser.ArgContext arg = args.get(ai);
                if (arg.expr() != null) {
                    for (String vn : collectVarNames(arg.expr())) {
                        if (!varToBoxSlot.containsKey(vn)) {
                            varToBoxSlot.put(vn, allocateNewLocal("__box_" + vn));
                        }
                    }
                }
            }
        }

        // For each closed-over variable, load its value directly for thunk constructor
        for (Map.Entry<String, Integer> e : varToBoxSlot.entrySet()) {
            String vn = e.getKey();
            int slot = e.getValue();
            String varType = context.getSymbolTable().get(vn);
            if (varType == null && context.getMainSymbolTable() != null) varType = context.getMainSymbolTable().get(vn);
            // create one-element Object[] and store boxed value at index 0
            sb.append("iconst_1\n");
            sb.append("anewarray java/lang/Object\n");
            sb.append("astore ").append(slot).append("\n");
            sb.append("aload ").append(slot).append("\n");
            sb.append("iconst_0\n");
            if ("real".equals(varType)) {
                sb.append(exprGen.generateLoadVar(vn));
                sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
            } else if ("string".equals(varType)) {
                sb.append(exprGen.generateLoadVar(vn));
            } else {
                sb.append(exprGen.generateLoadVar(vn));
                sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
            }
            sb.append("aastore\n");
        }

        for (int ai = 0; ai < args.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            boolean isValue = info.valueParams.contains(paramName);
            AlgolParser.ArgContext arg = args.get(ai);
            if (isValue) {
                if (arg.expr() != null) {
                    sb.append(generateExpr(arg.expr()));
                    String paramType = info.paramTypes.getOrDefault(paramName, "integer");
                    String argType = context.getExprTypes().getOrDefault(arg.expr(), "integer");
                    if ("real".equals(paramType) && "integer".equals(argType)) sb.append("i2d\n");
                }
            } else {
                AlgolParser.ExprContext actual = arg.expr();
                Set<String> names = actual != null ? collectVarNames(actual) : Set.of();
                Map<String, Integer> varToField = new LinkedHashMap<>();
                int fi = 0;
                for (String vn : names) varToField.put(vn, fi++);
                String baseType = info.paramTypes.getOrDefault(paramName, "integer");
                String thunkClass = createThunkClass(varToField, actual, baseType);
                sb.append("new ").append(thunkClass).append("\ndup\n");
                for (String vn : varToField.keySet()) {
                    int slot = varToBoxSlot.get(vn);
                    sb.append("aload ").append(slot).append("\n");
                }
                String ctorDesc = varToField.keySet().stream()
                    .map(vn -> "[Ljava/lang/Object;")
                    .collect(Collectors.joining("", "(", ")V"));
                sb.append("invokespecial ").append(thunkClass).append("/<init>").append(ctorDesc).append("\n");
            }
        }

        String paramDesc = info.paramNames.stream()
                .map(p -> info.valueParams.contains(p) ? (info.paramTypes.getOrDefault(p, "integer").equals("real") ? "D" : "I") : "Lgnb/jalgol/runtime/Thunk;")
                .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : (info.returnType.equals("real") ? "D" : (info.returnType.equals("string") ? "Ljava/lang/String;" : "I"));
        
        sb.append("invokestatic ").append(context.getPackageName()).append("/").append(context.getClassName())
          .append("/").append(name).append("(").append(paramDesc).append(")").append(retDesc).append("\n");

        if (isStatement && !"void".equals(info.returnType)) {
            sb.append(info.returnType.equals("real") ? "pop2\n" : "pop\n");
        }

        for (int ai = 0; ai < args.size() && ai < info.paramNames.size(); ai++) {
            String paramName = info.paramNames.get(ai);
            if (!info.valueParams.contains(paramName)) {
                AlgolParser.ArgContext arg = args.get(ai);
                if (arg.expr() instanceof AlgolParser.VarExprContext) {
                    String vn = ((AlgolParser.VarExprContext) arg.expr()).identifier().getText();
                    String varType = context.getSymbolTable().get(vn);
                    if (varType == null && context.getMainSymbolTable() != null) varType = context.getMainSymbolTable().get(vn);
                    int boxSlot = varToBoxSlot.get(vn);
                    sb.append("aload ").append(boxSlot).append("\niconst_0\naaload\n");
                    if ("real".equals(varType)) {
                        sb.append("checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n")
                          .append(exprGen.generateStoreVar(vn));
                    } else if ("integer".equals(varType) || "boolean".equals(varType)) {
                        sb.append("checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n")
                          .append(exprGen.generateStoreVar(vn));
                    }
                }
            }
        }
        return sb.toString();
    }

    public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        String procName = ctx.identifier().getText();
        ProcInfo info = context.getProcedures().get(procName);
        
        context.saveMainContext();
        
        Map<String, String> procST = new LinkedHashMap<>();
        Map<String, Integer> procLI = new LinkedHashMap<>();
        int nextSlot = 0;

        for (String paramName : info.paramNames) {
            String baseType = info.paramTypes.getOrDefault(paramName, "integer");
            String paramType = info.valueParams.contains(paramName) ? baseType : "thunk:" + baseType;
            procST.put(paramName, paramType);
            procLI.put(paramName, nextSlot);
            nextSlot += paramType.startsWith("thunk:") ? 1 : ("real".equals(paramType) ? 2 : 1);
        }
        for (Map.Entry<String, String> local : info.localVars.entrySet()) {
            procST.put(local.getKey(), local.getValue());
            procLI.put(local.getKey(), nextSlot);
            nextSlot += "real".equals(local.getValue()) ? 2 : 1;
        }
        int retvalSlot = -1;
        if (!"void".equals(info.returnType)) {
            retvalSlot = nextSlot;
            nextSlot += "real".equals(info.returnType) ? 2 : 1;
        }

        context.setSymbolTable(procST);
        context.setLocalIndex(procLI);
        context.setNextLocalIndex(nextSlot);
        context.setProcedureContext(procName, info.returnType, retvalSlot);

        StringBuilder sb = new StringBuilder();
        String paramDesc = info.paramNames.stream()
                .map(p -> info.valueParams.contains(p) ? (info.paramTypes.getOrDefault(p, "integer").equals("real") ? "D" : "I") : "Lgnb/jalgol/runtime/Thunk;")
                .collect(Collectors.joining());
        String retDesc = "void".equals(info.returnType) ? "V" : (info.returnType.equals("real") ? "D" : (info.returnType.equals("string") ? "Ljava/lang/String;" : "I"));

        sb.append("\n.method public static ").append(procName).append("(").append(paramDesc).append(")").append(retDesc).append("\n");
        sb.append(".limit stack 16\n.limit locals ").append(nextSlot).append("\n");

        for (Map.Entry<String, Integer> e : procLI.entrySet()) {
            if (info.paramNames.contains(e.getKey())) continue;
            String varType = procST.get(e.getKey());
            int slot = e.getValue();
            if ("real".equals(varType)) sb.append("dconst_0\ndstore ").append(slot).append("\n");
            else if ("string".equals(varType)) sb.append("ldc \"\"\nastore ").append(slot).append("\n");
            else sb.append("iconst_0\nistore ").append(slot).append("\n");
        }
        if (retvalSlot >= 0) {
            if ("real".equals(info.returnType)) sb.append("dconst_0\ndstore ").append(retvalSlot).append("\n");
            else sb.append("iconst_0\nistore ").append(retvalSlot).append("\n");
        }

        context.pushOutput(sb);
    }

    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        String procName = ctx.identifier().getText();
        
        StringBuilder sb = context.getActiveOutput();
        String retType = context.getCurrentProcReturnType();
        int slot = context.getProcRetvalSlot();

        if ("void".equals(retType)) sb.append("return\n");
        else if ("real".equals(retType)) sb.append("dload ").append(slot).append("\ndreturn\n");
        else if ("string".equals(retType)) sb.append("aload ").append(slot).append("\nareturn\n");
        else sb.append("iload ").append(slot).append("\nireturn\n");

        sb.append(".end method\n");

        context.addProcedureMethod(sb.toString());
        context.popOutput();
        context.restoreMainContext();
        context.setProcedureContext(null, null, -1);
    }

    /**
     * Generates code to call a procedure through a procedure variable.
     * Uses callback-based state injection (CodeGenerator delegation pattern).
     */
    public String generateProcedureVariableCall(String varName, String varType, List<AlgolParser.ArgContext> args) {
        String returnType = varType.substring("procedure:".length());
        String interfaceName;
        switch (returnType) {
            case "void":    interfaceName = "VoidProcedure";    break;
            case "real":    interfaceName = "RealProcedure";    break;
            case "integer": interfaceName = "IntegerProcedure"; break;
            case "string":  interfaceName = "StringProcedure";  break;
            default: throw new RuntimeException("Unknown procedure return type: " + returnType);
        }

        StringBuilder sb = new StringBuilder();

        // Load the procedure reference object
        Integer idx = lookupLocalSlot.apply(varName);
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
                sb.append(generateExprFn.apply(argExpr));
                if ("real".equals(argType)) {
                    sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else {
                    sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                sb.append("aastore\n");
            }
        }

        sb.append("invokeinterface gnb/jalgol/compiler/").append(interfaceName)
          .append("/invoke([Ljava/lang/Object;)").append(CodeGenUtils.getReturnTypeDescriptor(returnType)).append(" 2\n");

        return sb.toString();
    }

    private String getReturnTypeDescriptor(String returnType) {
        return CodeGenUtils.getReturnTypeDescriptor(returnType);
    }

    public List<Map.Entry<String, String>> getThunkClassDefinitions() {
        return context.getThunkClasses().entrySet().stream().collect(Collectors.toList());
    }

    public List<Map.Entry<String, String>> getProcRefClassDefinitions() {
        return context.getProcRefClasses().entrySet().stream().collect(Collectors.toList());
    }

    /**
     * Generates a ProcRef class and returns JVM instantiation bytecode.
     * Uses callback-based state injection (CodeGenerator delegation pattern).
     */
    public String generateProcedureReference(String procName, SymbolTableBuilder.ProcInfo procInfo) {
        String returnType = procInfo.returnType;

        String interfaceName;
        switch (returnType) {
            case "void":    interfaceName = "VoidProcedure";    break;
            case "real":    interfaceName = "RealProcedure";    break;
            case "integer": interfaceName = "IntegerProcedure"; break;
            case "string":  interfaceName = "StringProcedure";  break;
            default: throw new RuntimeException("Unknown procedure return type: " + returnType);
        }

        int refId = nextProcRefId.getAsInt();
        String procRefClassName = className + "$ProcRef" + refId;
        String fullClassName = packageName + "/" + procRefClassName;

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

        // invoke() method — unbox args from Object[] and call the static procedure
        jasmin.append(".method public invoke([Ljava/lang/Object;)").append(CodeGenUtils.getReturnTypeDescriptor(returnType)).append("\n");
        jasmin.append("    .limit stack 20\n");
        jasmin.append("    .limit locals 20\n");

        List<String> paramNames = procInfo.paramNames;
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            String paramType = procInfo.paramTypes.getOrDefault(paramName, "integer");
            jasmin.append("    aload_1\n");
            jasmin.append("    ldc ").append(i).append("\n");
            jasmin.append("    aaload\n");
            jasmin.append("    checkcast java/lang/Number\n");
            if ("real".equals(paramType)) {
                jasmin.append("    invokevirtual java/lang/Number/doubleValue()D\n");
            } else {
                jasmin.append("    invokevirtual java/lang/Number/intValue()I\n");
            }
        }

        String paramDesc = procInfo.paramNames.stream()
            .map(p -> {
                if (procInfo.valueParams.contains(p)) {
                    String type = procInfo.paramTypes.getOrDefault(p, "integer");
                    return switch (type) {
                        case "real"   -> "D";
                        case "string" -> "Ljava/lang/String;";
                        default -> "I";
                    };
                } else {
                    return "Lgnb/jalgol/compiler/Thunk;";
                }
            })
            .collect(Collectors.joining());
        String retDesc = switch (returnType) {
            case "void"   -> "V";
            case "real"   -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
        };
        jasmin.append("    invokestatic ").append(packageName).append("/").append(className)
              .append("/").append(procName).append("(").append(paramDesc).append(")").append(retDesc).append("\n");

        if (!"void".equals(returnType)) {
            jasmin.append("    ").append(CodeGenUtils.getReturnInstruction(returnType)).append("\n");
        } else {
            jasmin.append("    return\n");
        }

        jasmin.append(".end method\n");

        storeProcRefClass.accept(procRefClassName, jasmin.toString());

        StringBuilder instantiation = new StringBuilder();
        instantiation.append("new ").append(fullClassName).append("\n");
        instantiation.append("dup\n");
        instantiation.append("invokenonvirtual ").append(fullClassName).append("/<init>()V\n");
        return instantiation.toString();
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
        if (this.exprGen != null) this.exprGen.setContext(context);
    }
}
