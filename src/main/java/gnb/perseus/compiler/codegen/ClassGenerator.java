package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.CodeGenUtils;
import gnb.perseus.compiler.SymbolTableBuilder;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates JVM classes for the current Perseus class feature slice.
 */
public class ClassGenerator {
    private final String source;
    private final String packageName;
    private final Map<PerseusParser.ExprContext, String> exprTypes;
    private final Map<String, SymbolTableBuilder.ClassInfo> classes;

    public ClassGenerator(String source, String packageName, Map<PerseusParser.ExprContext, String> exprTypes,
            Map<String, SymbolTableBuilder.ClassInfo> classes) {
        this.source = source;
        this.packageName = packageName;
        this.exprTypes = exprTypes;
        this.classes = classes != null ? classes : Map.of();
    }

    public String generateClassJasmin(SymbolTableBuilder.ClassInfo cls) {
        StringBuilder sb = new StringBuilder();
        String internalName = packageName + "/" + cls.name;
        sb.append(".source ").append(source).append("\n");
        sb.append(".class public ").append(internalName).append("\n");
        sb.append(".super ").append(getSuperInternalName(cls)).append("\n\n");

        for (String paramName : ownParamNames(cls)) {
            String type = cls.paramTypes.getOrDefault(paramName, "integer");
            sb.append(".field protected ").append(paramName).append(" ")
              .append(CodeGenUtils.scalarTypeToJvmDesc(type)).append("\n");
        }
        for (Map.Entry<String, String> field : cls.fields.entrySet()) {
            sb.append(".field protected ").append(field.getKey()).append(" ")
              .append(CodeGenUtils.scalarTypeToJvmDesc(field.getValue())).append("\n");
        }
        sb.append("\n");

        sb.append(generateClassConstructor(cls));
        for (Map.Entry<String, SymbolTableBuilder.MethodInfo> methodEntry : cls.methods.entrySet()) {
            sb.append(generateClassMethod(cls, methodEntry.getKey(), methodEntry.getValue()));
        }
        return sb.toString();
    }

    private String generateClassConstructor(SymbolTableBuilder.ClassInfo cls) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> paramSlots = new LinkedHashMap<>();
        int nextSlot = 1;
        StringBuilder desc = new StringBuilder("(");
        for (String paramName : cls.paramNames) {
            String type = cls.paramTypes.getOrDefault(paramName, "integer");
            paramSlots.put(paramName, nextSlot);
            desc.append(CodeGenUtils.getReturnTypeDescriptor(type));
            nextSlot += "real".equals(type) ? 2 : 1;
        }
        desc.append(")V");

        sb.append(".method public <init>").append(desc).append("\n")
          .append(".limit stack 64\n")
          .append(".limit locals 64\n")
          .append("aload_0\n");

        SymbolTableBuilder.ClassInfo parent = parentClass(cls);
        if (parent != null) {
            StringBuilder superDesc = new StringBuilder("(");
            for (String paramName : parent.paramNames) {
                String type = cls.paramTypes.getOrDefault(paramName, "integer");
                int slot = paramSlots.get(paramName);
                if ("real".equals(type)) {
                    sb.append("dload ").append(slot).append("\n");
                } else if ("string".equals(type) || type.startsWith("ref:")) {
                    sb.append("aload ").append(slot).append("\n");
                } else {
                    sb.append("iload ").append(slot).append("\n");
                }
                superDesc.append(CodeGenUtils.getReturnTypeDescriptor(type));
            }
            superDesc.append(")V");
            sb.append("invokespecial ").append(getSuperInternalName(cls))
              .append("/<init>").append(superDesc).append("\n");
        } else {
            sb.append("invokespecial java/lang/Object/<init>()V\n");
        }

        for (Map.Entry<String, String> field : cls.fields.entrySet()) {
            String type = field.getValue();
            sb.append("aload_0\n");
            if ("real".equals(type)) {
                sb.append("dconst_0\n");
            } else if ("string".equals(type)) {
                sb.append("ldc \"\"\n");
            } else if (type != null && type.startsWith("ref:")) {
                sb.append("aconst_null\n");
            } else {
                sb.append("iconst_0\n");
            }
            sb.append("putfield ").append(packageName).append("/").append(cls.name).append("/")
              .append(field.getKey()).append(" ").append(CodeGenUtils.scalarTypeToJvmDesc(type)).append("\n");
        }

        for (String paramName : ownParamNames(cls)) {
            String type = cls.paramTypes.getOrDefault(paramName, "integer");
            int slot = paramSlots.get(paramName);
            sb.append("aload_0\n");
            if ("real".equals(type)) {
                sb.append("dload ").append(slot).append("\n");
            } else if ("string".equals(type) || type.startsWith("ref:")) {
                sb.append("aload ").append(slot).append("\n");
            } else {
                sb.append("iload ").append(slot).append("\n");
            }
            sb.append("putfield ").append(packageName).append("/").append(cls.name).append("/")
              .append(paramName).append(" ").append(CodeGenUtils.scalarTypeToJvmDesc(type)).append("\n");
        }

        Map<String, Integer> noLocals = Map.of();
        PerseusParser.BlockContext block = cls.parseContext != null ? cls.parseContext.block() : null;
        if (block != null && block.compoundStatement() != null) {
            for (PerseusParser.StatementContext stmt : block.compoundStatement().statement()) {
                if (stmt.procedureDecl() != null) continue;
                sb.append(generateClassStatement(cls, null, stmt, noLocals, -1));
            }
        }

        sb.append("return\n")
          .append(".end method\n\n");
        return sb.toString();
    }

    private String generateClassMethod(SymbolTableBuilder.ClassInfo cls, String methodName, SymbolTableBuilder.MethodInfo method) {
        Map<String, Integer> localSlots = new LinkedHashMap<>();
        int nextSlot = 1;
        StringBuilder desc = new StringBuilder("(");
        for (String paramName : method.paramNames) {
            String type = method.paramTypes.getOrDefault(paramName, "integer");
            localSlots.put(paramName, nextSlot);
            desc.append(CodeGenUtils.getReturnTypeDescriptor(type));
            nextSlot += "real".equals(type) ? 2 : 1;
        }
        for (Map.Entry<String, String> local : method.localVars.entrySet()) {
            localSlots.put(local.getKey(), nextSlot);
            nextSlot += "real".equals(local.getValue()) ? 2 : 1;
        }
        int returnSlot = -1;
        if (!"void".equals(method.returnType)) {
            returnSlot = nextSlot;
            nextSlot += "real".equals(method.returnType) ? 2 : 1;
        }
        desc.append(")").append(CodeGenUtils.getReturnTypeDescriptor(method.returnType));

        StringBuilder sb = new StringBuilder();
        sb.append(".method public ").append(methodName).append(desc).append("\n")
          .append(".limit stack 64\n")
          .append(".limit locals 64\n");

        for (Map.Entry<String, String> local : method.localVars.entrySet()) {
            int slot = localSlots.get(local.getKey());
            String type = local.getValue();
            if ("real".equals(type)) {
                sb.append("dconst_0\n").append("dstore ").append(slot).append("\n");
            } else if ("string".equals(type)) {
                sb.append("ldc \"\"\n").append("astore ").append(slot).append("\n");
            } else if (type != null && type.startsWith("ref:")) {
                sb.append("aconst_null\n").append("astore ").append(slot).append("\n");
            } else {
                sb.append("iconst_0\n").append("istore ").append(slot).append("\n");
            }
        }
        if (returnSlot >= 0) {
            if ("real".equals(method.returnType)) {
                sb.append("dconst_0\n").append("dstore ").append(returnSlot).append("\n");
            } else if ("string".equals(method.returnType) || method.returnType.startsWith("ref:")) {
                sb.append("aconst_null\n").append("astore ").append(returnSlot).append("\n");
            } else {
                sb.append("iconst_0\n").append("istore ").append(returnSlot).append("\n");
            }
        }

        sb.append(generateClassStatement(cls, method, method.parseContext != null ? method.parseContext.statement() : null, localSlots, returnSlot));

        if ("void".equals(method.returnType)) {
            sb.append("return\n");
        } else if ("real".equals(method.returnType)) {
            sb.append("dload ").append(returnSlot).append("\n").append("dreturn\n");
        } else if ("string".equals(method.returnType) || method.returnType.startsWith("ref:")) {
            sb.append("aload ").append(returnSlot).append("\n").append("areturn\n");
        } else {
            sb.append("iload ").append(returnSlot).append("\n").append("ireturn\n");
        }
        sb.append(".end method\n\n");
        return sb.toString();
    }

    private String generateClassStatement(SymbolTableBuilder.ClassInfo cls, SymbolTableBuilder.MethodInfo method,
            PerseusParser.StatementContext stmt, Map<String, Integer> localSlots, int returnSlot) {
        if (stmt == null) return "";
        if (stmt.block() != null && stmt.block().compoundStatement() != null) {
            StringBuilder sb = new StringBuilder();
            for (PerseusParser.StatementContext inner : stmt.block().compoundStatement().statement()) {
                if (inner.procedureDecl() != null) continue;
                sb.append(generateClassStatement(cls, method, inner, localSlots, returnSlot));
            }
            return sb.toString();
        }
        if (stmt.assignment() != null) {
            return generateClassAssignment(cls, method, stmt.assignment(), localSlots, returnSlot);
        }
        return "";
    }

    private String generateClassAssignment(SymbolTableBuilder.ClassInfo cls, SymbolTableBuilder.MethodInfo method,
            PerseusParser.AssignmentContext assignment, Map<String, Integer> localSlots, int returnSlot) {
        if (assignment.lvalue().isEmpty()) return "";
        PerseusParser.LvalueContext target = assignment.lvalue(0);
        String targetName = target.identifier().getText();
        String valueType = resolveClassType(cls, method, targetName);
        String exprType = exprTypes.getOrDefault(assignment.expr(), "integer");
        StringBuilder valueCode = new StringBuilder();
        valueCode.append(generateClassExpr(cls, method, assignment.expr(), localSlots, returnSlot));
        if ("real".equals(valueType) && "integer".equals(exprType)) {
            valueCode.append("i2d\n");
        }

        if (method != null && targetName.equals(findMethodName(cls, method)) && returnSlot >= 0) {
            StringBuilder sb = new StringBuilder(valueCode);
            if ("real".equals(method.returnType)) {
                sb.append("dstore ").append(returnSlot).append("\n");
            } else if ("string".equals(method.returnType) || method.returnType.startsWith("ref:")) {
                sb.append("astore ").append(returnSlot).append("\n");
            } else {
                sb.append("istore ").append(returnSlot).append("\n");
            }
            return sb.toString();
        }

        Integer localSlot = localSlots.get(targetName);
        if (localSlot != null) {
            StringBuilder sb = new StringBuilder(valueCode);
            if ("real".equals(valueType)) {
                sb.append("dstore ").append(localSlot).append("\n");
            } else if ("string".equals(valueType) || (valueType != null && valueType.startsWith("ref:"))) {
                sb.append("astore ").append(localSlot).append("\n");
            } else {
                sb.append("istore ").append(localSlot).append("\n");
            }
            return sb.toString();
        }

        return new StringBuilder()
                .append("aload_0\n")
                .append(valueCode)
                .append("putfield ").append(packageName).append("/").append(findFieldOwner(cls, targetName)).append("/")
                .append(targetName).append(" ").append(CodeGenUtils.scalarTypeToJvmDesc(valueType)).append("\n")
                .toString();
    }

    private String generateClassExpr(SymbolTableBuilder.ClassInfo cls, SymbolTableBuilder.MethodInfo method,
            PerseusParser.ExprContext expr, Map<String, Integer> localSlots, int returnSlot) {
        if (expr instanceof PerseusParser.RealLiteralExprContext e) {
            return "ldc2_w " + e.realLiteral().getText() + "d\n";
        }
        if (expr instanceof PerseusParser.IntLiteralExprContext e) {
            return "ldc " + e.unsignedInt().getText() + "\n";
        }
        if (expr instanceof PerseusParser.StringLiteralExprContext e) {
            return "ldc " + e.string().getText() + "\n";
        }
        if (expr instanceof PerseusParser.VarExprContext e) {
            String name = e.identifier().getText();
            if (method != null && name.equals(findMethodName(cls, method)) && returnSlot >= 0) {
                if ("real".equals(method.returnType)) return "dload " + returnSlot + "\n";
                if ("string".equals(method.returnType) || method.returnType.startsWith("ref:")) return "aload " + returnSlot + "\n";
                return "iload " + returnSlot + "\n";
            }
            Integer localSlot = localSlots.get(name);
            String type = resolveClassType(cls, method, name);
            if (localSlot != null) {
                if ("real".equals(type)) return "dload " + localSlot + "\n";
                if ("string".equals(type) || (type != null && type.startsWith("ref:"))) return "aload " + localSlot + "\n";
                return "iload " + localSlot + "\n";
            }
            return "aload_0\ngetfield " + packageName + "/" + findFieldOwner(cls, name) + "/" + name + " "
                    + CodeGenUtils.scalarTypeToJvmDesc(type) + "\n";
        }
        if (expr instanceof PerseusParser.ParenExprContext e) {
            return generateClassExpr(cls, method, e.expr(), localSlots, returnSlot);
        }
        if (expr instanceof PerseusParser.UnaryMinusExprContext e) {
            String type = exprTypes.getOrDefault(expr, "integer");
            return generateClassExpr(cls, method, e.expr(), localSlots, returnSlot)
                    + ("real".equals(type) ? "dneg\n" : "ineg\n");
        }
        if (expr instanceof PerseusParser.AddSubExprContext e) {
            String leftType = exprTypes.getOrDefault(e.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(e.expr(1), "integer");
            String resultType = exprTypes.getOrDefault(expr, "integer");
            String left = generateClassExpr(cls, method, e.expr(0), localSlots, returnSlot);
            String right = generateClassExpr(cls, method, e.expr(1), localSlots, returnSlot);
            if ("real".equals(resultType) && "integer".equals(leftType)) left += "i2d\n";
            if ("real".equals(resultType) && "integer".equals(rightType)) right += "i2d\n";
            return left + right + ("real".equals(resultType)
                    ? ("+".equals(e.op.getText()) ? "dadd\n" : "dsub\n")
                    : ("+".equals(e.op.getText()) ? "iadd\n" : "isub\n"));
        }
        if (expr instanceof PerseusParser.MulDivExprContext e) {
            String leftType = exprTypes.getOrDefault(e.expr(0), "integer");
            String rightType = exprTypes.getOrDefault(e.expr(1), "integer");
            String resultType = exprTypes.getOrDefault(expr, "integer");
            String left = generateClassExpr(cls, method, e.expr(0), localSlots, returnSlot);
            String right = generateClassExpr(cls, method, e.expr(1), localSlots, returnSlot);
            if ("real".equals(resultType) && "integer".equals(leftType)) left += "i2d\n";
            if ("real".equals(resultType) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            return left + right + ("real".equals(resultType)
                    ? ("*".equals(op) ? "dmul\n" : "ddiv\n")
                    : ("*".equals(op) ? "imul\n" : "idiv\n"));
        }
        if (expr instanceof PerseusParser.ProcCallExprContext e) {
            String name = e.identifier().getText();
            List<PerseusParser.ArgContext> args = e.argList() != null ? e.argList().arg() : List.of();
            if ("sqrt".equals(name) && args.size() == 1) {
                String argType = exprTypes.getOrDefault(args.get(0).expr(), "integer");
                String argCode = generateClassExpr(cls, method, args.get(0).expr(), localSlots, returnSlot);
                if ("integer".equals(argType)) {
                    argCode += "i2d\n";
                }
                return argCode + "invokestatic java/lang/Math/sqrt(D)D\n";
            }
        }
        return "";
    }

    private String resolveClassType(SymbolTableBuilder.ClassInfo cls, SymbolTableBuilder.MethodInfo method, String name) {
        if (method != null) {
            String type = method.localVars.get(name);
            if (type != null) return type;
            type = method.paramTypes.get(name);
            if (type != null) return type;
        }
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            String type = current.fields.get(name);
            if (type != null) return type;
            type = current.paramTypes.get(name);
            if (type != null) return type;
            current = parentClass(current);
        }
        return "integer";
    }

    private String findMethodName(SymbolTableBuilder.ClassInfo cls, SymbolTableBuilder.MethodInfo method) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            for (Map.Entry<String, SymbolTableBuilder.MethodInfo> entry : current.methods.entrySet()) {
                if (entry.getValue() == method) {
                    return entry.getKey();
                }
            }
            current = parentClass(current);
        }
        return null;
    }

    private SymbolTableBuilder.ClassInfo parentClass(SymbolTableBuilder.ClassInfo cls) {
        if (cls == null || cls.parentName == null) {
            return null;
        }
        return classes.get(cls.parentName);
    }

    private String getSuperInternalName(SymbolTableBuilder.ClassInfo cls) {
        SymbolTableBuilder.ClassInfo parent = parentClass(cls);
        if (parent == null) {
            return "java/lang/Object";
        }
        if (parent.externalJava) {
            return parent.externalJavaQualifiedName.replace('.', '/');
        }
        return packageName + "/" + parent.name;
    }

    private List<String> ownParamNames(SymbolTableBuilder.ClassInfo cls) {
        SymbolTableBuilder.ClassInfo parent = parentClass(cls);
        if (parent == null) {
            return cls.paramNames;
        }
        return cls.paramNames.subList(parent.paramNames.size(), cls.paramNames.size());
    }

    private String findFieldOwner(SymbolTableBuilder.ClassInfo cls, String fieldName) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            if (current.fields.containsKey(fieldName) || current.paramTypes.containsKey(fieldName)) {
                return current.name;
            }
            current = parentClass(current);
        }
        return cls.name;
    }
}
