package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Pass 1.5: infer expression types for code generation.
 */
public class TypeInferencer extends PerseusBaseListener {
    private final String sourceName;
    private final Map<String, String> symbolTable;
    private final Map<String, SymbolTableBuilder.ClassInfo> classes;
    private final Map<PerseusParser.ExprContext, String> exprTypes = new HashMap<>();
    private final Deque<SymbolTableBuilder.ClassInfo> classStack = new ArrayDeque<>();
    private final Deque<SymbolTableBuilder.MethodInfo> methodStack = new ArrayDeque<>();

    public TypeInferencer(String sourceName, Map<String, String> symbolTable,
            Map<String, SymbolTableBuilder.ClassInfo> classes) {
        this.sourceName = sourceName;
        this.symbolTable = symbolTable;
        this.classes = classes != null ? classes : Map.of();
    }

    public Map<PerseusParser.ExprContext, String> getExprTypes() {
        return exprTypes;
    }

    @Override
    public void enterClassDecl(PerseusParser.ClassDeclContext ctx) {
        String className = ctx.className.getText();
        SymbolTableBuilder.ClassInfo cls = classes.get(className);
        if (cls != null) {
            classStack.push(cls);
        }
    }

    @Override
    public void exitClassDecl(PerseusParser.ClassDeclContext ctx) {
        if (!classStack.isEmpty()) {
            classStack.pop();
        }
    }

    @Override
    public void enterProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (!classStack.isEmpty()) {
            SymbolTableBuilder.MethodInfo method = classStack.peek().methods.get(ctx.identifier().getText());
            if (method != null) {
                methodStack.push(method);
            }
        }
    }

    @Override
    public void exitProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (!methodStack.isEmpty()) {
            methodStack.pop();
        }
    }

    @Override
    public void exitRelExpr(PerseusParser.RelExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitMulDivExpr(PerseusParser.MulDivExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String op = ctx.op.getText();
        String resultType;
        if ("div".equals(op)) {
            resultType = "integer";
        } else if ("/".equals(op)) {
            resultType = "real";
        } else if ("deferred".equals(leftType) || "deferred".equals(rightType)) {
            resultType = "real";
        } else {
            resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        }
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitPowExpr(PerseusParser.PowExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitAddSubExpr(PerseusParser.AddSubExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitAndExpr(PerseusParser.AndExprContext ctx) {
        requireBooleanOperands(ctx.expr(0), ctx.expr(1), ctx, "PERS2003", "& operator requires boolean operands");
    }

    @Override
    public void exitOrExpr(PerseusParser.OrExprContext ctx) {
        requireBooleanOperands(ctx.expr(0), ctx.expr(1), ctx, "PERS2004", "or operator requires boolean operands");
    }

    @Override
    public void exitImpExpr(PerseusParser.ImpExprContext ctx) {
        requireBooleanOperands(ctx.expr(0), ctx.expr(1), ctx, "PERS2006", "imp operator requires boolean operands");
    }

    @Override
    public void exitEqvExpr(PerseusParser.EqvExprContext ctx) {
        requireBooleanOperands(ctx.expr(0), ctx.expr(1), ctx, "PERS2007", "eqv operator requires boolean operands");
    }

    @Override
    public void exitNotExpr(PerseusParser.NotExprContext ctx) {
        String operandType = exprTypes.get(ctx.expr());
        if (!"boolean".equals(operandType)) {
            throw error(ctx, "PERS2005", "not operator requires boolean operand");
        }
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitVarExpr(PerseusParser.VarExprContext ctx) {
        String varName = ctx.identifier().getText();
        if ("maxreal".equals(varName) || "minreal".equals(varName) || "epsilon".equals(varName)) {
            exprTypes.put(ctx, "real");
            return;
        }
        if ("maxint".equals(varName)) {
            exprTypes.put(ctx, "integer");
            return;
        }

        String type = lookupType(varName);
        if (type == null) {
            throw error(ctx, "PERS2001", "Undeclared variable: " + varName);
        }
        if (type.startsWith("thunk:")) {
            type = type.substring("thunk:".length());
        }
        exprTypes.put(ctx, type);
    }

    @Override
    public void exitArrayAccessExpr(PerseusParser.ArrayAccessExprContext ctx) {
        String arrName = ctx.identifier().getText();
        String arrType = lookupType(arrName);
        if (arrType == null) throw error(ctx, "PERS2002", "Undeclared array: " + arrName);
        exprTypes.put(ctx, arrType.endsWith("[]") ? arrType.substring(0, arrType.length() - 2) : arrType);
    }

    @Override
    public void exitRealLiteralExpr(PerseusParser.RealLiteralExprContext ctx) {
        exprTypes.put(ctx, "real");
    }

    @Override
    public void exitIntLiteralExpr(PerseusParser.IntLiteralExprContext ctx) {
        exprTypes.put(ctx, "integer");
    }

    @Override
    public void exitTrueLiteralExpr(PerseusParser.TrueLiteralExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitFalseLiteralExpr(PerseusParser.FalseLiteralExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitStringLiteralExpr(PerseusParser.StringLiteralExprContext ctx) {
        exprTypes.put(ctx, "string");
    }

    @Override
    public void exitUnaryMinusExpr(PerseusParser.UnaryMinusExprContext ctx) {
        exprTypes.put(ctx, exprTypes.getOrDefault(ctx.expr(), "integer"));
    }

    @Override
    public void exitParenExpr(PerseusParser.ParenExprContext ctx) {
        exprTypes.put(ctx, exprTypes.get(ctx.expr()));
    }

    @Override
    public void exitProcCallExpr(PerseusParser.ProcCallExprContext ctx) {
        String procName = ctx.identifier().getText();
        String builtinType = getBuiltinFunctionType(procName);
        if (builtinType != null) {
            exprTypes.put(ctx, builtinType);
            return;
        }

        SymbolTableBuilder.ClassInfo currentClass = classStack.peek();
        if (currentClass != null) {
            SymbolTableBuilder.MethodInfo method = findMethodInHierarchy(currentClass, procName);
            if (method != null) {
                exprTypes.put(ctx, method.returnType);
                return;
            }
            Method javaMethod = findJavaMethodInHierarchy(currentClass, procName,
                    ctx.argList() != null ? ctx.argList().arg().size() : 0);
            if (javaMethod != null) {
                exprTypes.put(ctx, mapJavaType(javaMethod.getReturnType()));
                return;
            }
        }

        String procType = lookupType(procName);
        if (procType != null && procType.startsWith("procedure:")) {
            exprTypes.put(ctx, procType.substring("procedure:".length()));
        }
    }

    @Override
    public void exitIfExpr(PerseusParser.IfExprContext ctx) {
        exprTypes.put(ctx, exprTypes.getOrDefault(ctx.expr(1), "integer"));
    }

    @Override
    public void exitNewObjectExpr(PerseusParser.NewObjectExprContext ctx) {
        exprTypes.put(ctx, "ref:" + ctx.identifier().getText());
    }

    @Override
    public void exitMemberCallExpr(PerseusParser.MemberCallExprContext ctx) {
        String receiverType = lookupType(ctx.identifier(0).getText());
        if (receiverType == null || !receiverType.startsWith("ref:")) {
            throw error(ctx, "PERS2008", "Member call requires an object reference: " + ctx.getText());
        }
        String className = receiverType.substring("ref:".length());
        SymbolTableBuilder.ClassInfo cls = classes.get(className);
        if (cls == null) {
            throw error(ctx, "PERS2009", "Unknown class: " + className);
        }
        String memberName = ctx.identifier(1).getText();
        SymbolTableBuilder.MethodInfo method = findMethodInHierarchy(cls, memberName);
        if (method != null) {
            exprTypes.put(ctx, method.returnType);
            return;
        }
        Method javaMethod = findJavaMethodInHierarchy(cls, memberName,
                ctx.argList() != null ? ctx.argList().arg().size() : 0);
        if (javaMethod != null) {
            exprTypes.put(ctx, mapJavaType(javaMethod.getReturnType()));
            return;
        }
        throw error(ctx, "PERS2010", "Unknown class member: " + className + "." + memberName);
    }

    private void requireBooleanOperands(PerseusParser.ExprContext left, PerseusParser.ExprContext right,
            PerseusParser.ExprContext whole, String code, String message) {
        if (!"boolean".equals(exprTypes.get(left)) || !"boolean".equals(exprTypes.get(right))) {
            throw error(whole, code, message);
        }
        exprTypes.put(whole, "boolean");
    }

    private String lookupType(String name) {
        SymbolTableBuilder.MethodInfo method = methodStack.peek();
        if (method != null) {
            String type = method.localVars.get(name);
            if (type != null) return type;
            type = method.paramTypes.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.ClassInfo cls = classStack.peek();
        if (cls != null) {
            String type = cls.fields.get(name);
            if (type != null) return type;
            type = cls.paramTypes.get(name);
            if (type != null) return type;
        }

        return symbolTable.get(name);
    }

    private SymbolTableBuilder.MethodInfo findMethodInHierarchy(SymbolTableBuilder.ClassInfo cls, String memberName) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            SymbolTableBuilder.MethodInfo method = current.methods.get(memberName);
            if (method != null) {
                return method;
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return null;
    }

    private Method findJavaMethod(String qualifiedName, String memberName, int argCount) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(memberName) && method.getParameterCount() == argCount) {
                    return method;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private Method findJavaMethodInHierarchy(SymbolTableBuilder.ClassInfo cls, String memberName, int argCount) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            if (current.externalJava && current.externalJavaQualifiedName != null) {
                Method method = findJavaMethod(current.externalJavaQualifiedName, memberName, argCount);
                if (method != null) {
                    return method;
                }
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return null;
    }

    private String mapJavaType(Class<?> type) {
        if (type == void.class) return "void";
        if (type == double.class || type == float.class) return "real";
        if (type == int.class || type == short.class || type == long.class || type == byte.class || type == char.class) return "integer";
        if (type == boolean.class) return "boolean";
        if (type == String.class) return "string";
        return "ref:" + type.getSimpleName();
    }

    private String getBuiltinFunctionType(String name) {
        return switch (name) {
            case "sqrt", "abs", "sin", "cos", "arctan", "ln", "exp" -> "real";
            case "iabs", "sign", "entier", "length" -> "integer";
            case "substring", "concat" -> "string";
            default -> null;
        };
    }

    private DiagnosticException error(PerseusParser.ExprContext ctx, String code, String message) {
        return new DiagnosticException(CompilerDiagnostic.error(code, ctx.getStart(), sourceName, message));
    }
}
