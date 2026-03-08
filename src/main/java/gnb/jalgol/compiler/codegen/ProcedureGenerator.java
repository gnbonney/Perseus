package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.SymbolTableBuilder;
import gnb.jalgol.compiler.SymbolTableBuilder.ProcInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;
import java.util.stream.Collectors;

public class ProcedureGenerator {
    private final ContextManager context;
    private final ExpressionGenerator exprGen;

    public ProcedureGenerator(ContextManager context, ExpressionGenerator exprGen) {
        this.context = context;
        this.exprGen = exprGen;
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

        for (String vn : varToField.keySet()) {
            sb.append(".field public ").append(vn).append(" [Ljava/lang/Object;\n");
        }

        sb.append("\n.method public <init>(");
        for (int i = 0; i < varToField.size(); i++) sb.append("[Ljava/lang/Object;");
        sb.append(")V\n");
        sb.append("aload_0\ninvokespecial java/lang/Object/<init>()V\n");
        int fi = 0;
        for (String vn : varToField.keySet()) {
            sb.append("aload_0\naload ").append(fi + 1).append("\nputfield ")
              .append(thunkClassName).append("/").append(vn).append(" [Ljava/lang/Object;\n");
            fi++;
        }
        sb.append("return\n.end method\n\n");

        sb.append(".method public eval()Ljava/lang/Object;\n");
        sb.append(".limit stack 10\n.limit locals 10\n");

        context.pushOutput(new StringBuilder());
        Map<String, String> oldSym = context.getSymbolTable();
        Map<String, Integer> oldIdx = context.getLocalIndex();
        Map<String, String> thunkSym = context.getMainSymbolTable() != null ? new HashMap<>(context.getMainSymbolTable()) : new HashMap<>();
        Map<String, Integer> thunkIdx = new HashMap<>();
        context.setSymbolTable(thunkSym);
        context.setLocalIndex(thunkIdx);

        for (String vn : varToField.keySet()) {
            String type = context.getMainSymbolTable() != null ? context.getMainSymbolTable().get(vn) : null;
            sb.append("aload_0\ngetfield ").append(thunkClassName).append("/").append(vn).append(" [Ljava/lang/Object;\n");
            sb.append("iconst_0\naaload\n");
            if ("real".equals(type)) {
                sb.append("checkcast java/lang/Double\ninvokevirtual java/lang/Double/doubleValue()D\n");
            } else if ("integer".equals(type) || "boolean".equals(type)) {
                sb.append("checkcast java/lang/Integer\ninvokevirtual java/lang/Integer/intValue()I\n");
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

        for (Map.Entry<String, Integer> e : varToBoxSlot.entrySet()) {
            String vn = e.getKey();
            int slot = e.getValue();
            String varType = context.getSymbolTable().get(vn);
            if (varType == null && context.getMainSymbolTable() != null) varType = context.getMainSymbolTable().get(vn);
            sb.append("iconst_1\nanewarray java/lang/Object\nastore ").append(slot).append("\n");
            sb.append(exprGen.generateLoadVar(vn));
            if ("real".equals(varType)) {
                sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
            } else if ("integer".equals(varType) || "boolean".equals(varType)) {
                sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
            }
            sb.append("aload ").append(slot).append("\nswap\niconst_0\nswap\naastore\n");
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
                    sb.append("aload ").append(varToBoxSlot.get(vn)).append("\n");
                }
                String ctorDesc = varToField.keySet().stream().map(vn -> "[Ljava/lang/Object;").collect(Collectors.joining("", "(", ")V"));
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
        ProcInfo procInfo = context.getProcedures().get(procName);
        context.saveMainContext();
        context.setSymbolTable(new HashMap<>(context.getMainSymbolTable() != null ? context.getMainSymbolTable() : new HashMap<>()));
        context.setLocalIndex(new HashMap<>());
        context.setNextLocalIndex(0);

        StringBuilder sb = new StringBuilder();
        String paramDesc = procInfo.paramNames.stream()
                .map(p -> procInfo.valueParams.contains(p) ? (procInfo.paramTypes.getOrDefault(p, "integer").equals("real") ? "D" : "I") : "Lgnb/jalgol/runtime/Thunk;")
                .collect(Collectors.joining());
        String retDesc = "void".equals(procInfo.returnType) ? "V" : (procInfo.returnType.equals("real") ? "D" : (procInfo.returnType.equals("string") ? "Ljava/lang/String;" : "I"));

        sb.append("\n.method public static ").append(procName).append("(").append(paramDesc).append(")").append(retDesc).append("\n");
        sb.append(".limit stack 50\n.limit locals 50\n");

        for (String p : procInfo.paramNames) {
            allocateNewLocal(p);
        }

        context.pushOutput(sb);
    }

    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        String procName = ctx.identifier().getText();
        ProcInfo procInfo = context.getProcedures().get(procName);
        StringBuilder sb = context.getActiveOutput();

        if (procInfo != null && !"void".equals(procInfo.returnType)) {
            Integer slot = context.getLocalIndex().get(procName);
            if (slot != null) {
                switch (procInfo.returnType) {
                    case "real"   -> sb.append("dload ").append(slot).append("\ndreturn\n");
                    case "string" -> sb.append("aload ").append(slot).append("\nareturn\n");
                    default       -> sb.append("iload ").append(slot).append("\nireturn\n");
                }
            }
        } else {
            sb.append("return\n");
        }
        sb.append(".end method\n");

        context.addProcedureMethod(sb.toString());
        context.popOutput();
        context.restoreMainContext();
    }
}
