package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.lang.reflect.Field;
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
    private final Map<String, SymbolTableBuilder.ProcInfo> procedures;
    private final Map<String, SymbolTableBuilder.ClassInfo> classes;
    private final Map<PerseusParser.ExprContext, String> exprTypes = new HashMap<>();
    private final Deque<SymbolTableBuilder.ClassInfo> classStack = new ArrayDeque<>();
    private final Deque<SymbolTableBuilder.ProcInfo> procStack = new ArrayDeque<>();
    private final Deque<SymbolTableBuilder.MethodInfo> methodStack = new ArrayDeque<>();
    private final Deque<Map<String, String>> exceptionBindingStack = new ArrayDeque<>();
    private final Deque<Map<String, String>> lambdaBindingStack = new ArrayDeque<>();
    private final Deque<Map<String, String>> anonymousLocalBindingStack = new ArrayDeque<>();

    public TypeInferencer(String sourceName, Map<String, String> symbolTable,
            Map<String, SymbolTableBuilder.ProcInfo> procedures,
            Map<String, SymbolTableBuilder.ClassInfo> classes) {
        this.sourceName = sourceName;
        this.symbolTable = symbolTable;
        this.procedures = procedures != null ? procedures : Map.of();
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
            return;
        }
        SymbolTableBuilder.ProcInfo proc = procedures.get(ctx.identifier().getText());
        if (proc != null) {
            procStack.push(proc);
        }
    }

    @Override
    public void exitProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (!methodStack.isEmpty()) {
            methodStack.pop();
            return;
        }
        if (!procStack.isEmpty()) {
            procStack.pop();
        }
    }

    @Override
    public void enterExceptionHandler(PerseusParser.ExceptionHandlerContext ctx) {
        if (ctx.identifier() == null) {
            exceptionBindingStack.push(Map.of());
            return;
        }
        String boundName = ctx.identifier().getText();
        String boundType = ExceptionTypeResolver.toReferenceType(ctx.exceptionPattern());
        exceptionBindingStack.push(Map.of(boundName, boundType));
    }

    @Override
    public void exitExceptionHandler(PerseusParser.ExceptionHandlerContext ctx) {
        if (!exceptionBindingStack.isEmpty()) {
            exceptionBindingStack.pop();
        }
    }

    @Override
    public void exitSignalStatement(PerseusParser.SignalStatementContext ctx) {
        String exprType = exprTypes.get(ctx.expr());
        if (!isThrowableReferenceType(exprType)) {
            throw error(ctx, "PERS2011", "signal requires an exception object reference");
        }
    }

    @Override
    public void exitRelExpr(PerseusParser.RelExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        if (isReferenceComparison(leftType, rightType)) {
            String op = ctx.op.getText();
            if (!"=".equals(op) && !"<>".equals(op)) {
                throw error(ctx, "PERS2012", "reference comparisons only support = and <>");
            }
        } else if ("null".equals(leftType) || "null".equals(rightType)) {
            throw error(ctx, "PERS2012", "null comparisons require an object reference on the other side");
        }
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
        if (arrType.startsWith("vector:")) {
            exprTypes.put(ctx, arrType.substring("vector:".length()));
            return;
        }
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
    public void exitNullLiteralExpr(PerseusParser.NullLiteralExprContext ctx) {
        exprTypes.put(ctx, "null");
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
    public void enterAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        Map<String, String> bindings = new HashMap<>();
        if (ctx.lambdaParamList() != null) {
            for (PerseusParser.LambdaParamContext param : ctx.lambdaParamList().lambdaParam()) {
                bindings.put(param.identifier().getText(), mapLambdaType(param.lambdaParamType()));
            }
        }
        lambdaBindingStack.push(bindings);
        anonymousLocalBindingStack.push(new HashMap<>());
    }

    @Override
    public void exitAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        String declaredReturnType = mapLambdaReturnType(ctx.lambdaReturnType());
        String bodyType = anonymousProcedureBodyType(ctx);
        if (!isLambdaBodyCompatible(declaredReturnType, bodyType)) {
            throw error(ctx, "PERS2013",
                    "Anonymous procedure body type " + bodyType + " does not match declared return type " + declaredReturnType);
        }
        exprTypes.put(ctx, "procedure:" + declaredReturnType);
        if (!lambdaBindingStack.isEmpty()) {
            lambdaBindingStack.pop();
        }
        if (!anonymousLocalBindingStack.isEmpty()) {
            anonymousLocalBindingStack.pop();
        }
    }

    @Override
    public void enterVarDecl(PerseusParser.VarDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = mapVarDeclType(ctx);
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), type);
        }
    }

    @Override
    public void enterRefDecl(PerseusParser.RefDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "ref:" + ctx.identifier().getText();
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), type);
        }
    }

    @Override
    public void enterVectorDecl(PerseusParser.VectorDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "vector:" + mapVectorElementType(ctx.vectorElementType());
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), type);
        }
    }

    @Override
    public void exitProcCallExpr(PerseusParser.ProcCallExprContext ctx) {
        String procName = ctx.identifier().getText();
        java.util.List<String> argTypes = getArgTypes(ctx.argList());
        String procType = lookupType(procName);
        if (procType != null && procType.startsWith("procedure:")) {
            exprTypes.put(ctx, procType.substring("procedure:".length()));
            return;
        }

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
            JavaInteropResolver.MethodResolution resolution = findJavaMethodInHierarchy(currentClass, procName, argTypes);
            if (resolution.method() != null) {
                exprTypes.put(ctx, mapJavaType(resolution.method().getReturnType()));
                return;
            }
            if (resolution.diagnostic() != null) {
                throw error(ctx, "PERS2010", resolution.diagnostic());
            }
        }
    }

    @Override
    public void exitIfExpr(PerseusParser.IfExprContext ctx) {
        exprTypes.put(ctx, exprTypes.getOrDefault(ctx.expr(1), "integer"));
    }

    @Override
    public void exitNewObjectExpr(PerseusParser.NewObjectExprContext ctx) {
        String className = ctx.identifier().getText();
        SymbolTableBuilder.ClassInfo cls = classes.get(className);
        if (cls != null && cls.externalJava) {
            JavaInteropResolver.ConstructorResolution resolution =
                    JavaInteropResolver.resolveConstructor(
                            cls.externalJavaQualifiedName,
                            getArgTypes(ctx.argList()),
                            this::scoreReferenceCompatibility);
            if (resolution.constructor() == null) {
                throw error(ctx, "PERS2011", resolution.diagnostic());
            }
        }
        exprTypes.put(ctx, "ref:" + className);
    }

    @Override
    public void exitMemberCallExpr(PerseusParser.MemberCallExprContext ctx) {
        String memberType = resolveMemberAccessType(
                ctx.identifier(0).getText(),
                ctx.identifier(1).getText(),
                ctx.argList() != null,
                getArgTypes(ctx.argList()),
                ctx);
        exprTypes.put(ctx, memberType);
    }

    @Override
    public void exitMemberCall(PerseusParser.MemberCallContext ctx) {
        resolveMemberAccessType(
                ctx.identifier(0).getText(),
                ctx.identifier(1).getText(),
                ctx.argList() != null,
                getArgTypes(ctx.argList()),
                null);
    }

    private String resolveMemberAccessType(String receiverName, String memberName, boolean explicitCall,
            java.util.List<String> argTypes, org.antlr.v4.runtime.ParserRuleContext ctxForError) {
        String receiverType = lookupType(receiverName);
        if (receiverType == null) {
            if (ctxForError != null) {
                throw error(ctxForError, "PERS2008", "Member call requires a typed receiver: " + receiverName + "." + memberName);
            }
            throw new DiagnosticException(CompilerDiagnostic.error("PERS2008", fileFromSourceName(), 1, 1,
                    "Member call requires a typed receiver: " + receiverName + "." + memberName));
        }
        if ("string".equals(receiverType)) {
            JavaInteropResolver.MethodResolution resolution = findJavaMethod("java.lang.String", memberName, argTypes);
            if (resolution.method() != null) {
                return mapJavaType(resolution.method().getReturnType());
            }
            raiseMemberError(ctxForError, "PERS2010", resolution.diagnostic());
        }
        if (receiverType.startsWith("vector:")) {
            if ("append".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector append requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "vector append requires exactly one argument");
                }
                String elementType = receiverType.substring("vector:".length());
                if (!isVectorElementCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "vector append argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("size".equals(memberName)) {
                if (explicitCall && !argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "vector size() does not take arguments");
                }
                return "integer";
            }
            raiseMemberError(ctxForError, "PERS2010", "Unknown vector member: " + memberName);
        }
        if (!receiverType.startsWith("ref:")) {
            raiseMemberError(ctxForError, "PERS2008", "Member call requires an object reference: " + receiverName + "." + memberName);
        }
        String className = receiverType.substring("ref:".length());
        SymbolTableBuilder.ClassInfo cls = classes.get(className);
        if (cls == null) {
            raiseMemberError(ctxForError, "PERS2009", "Unknown class: " + className);
        }
        SymbolTableBuilder.MethodInfo method = findMethodInHierarchy(cls, memberName);
        if (method != null) {
            return method.returnType;
        }
        if (!explicitCall) {
            Field javaField = findJavaFieldInHierarchy(cls, memberName);
            if (javaField != null) {
                return mapJavaType(javaField.getType());
            }
        }
        JavaInteropResolver.MethodResolution resolution = findJavaMethodInHierarchy(cls, memberName, argTypes);
        if (resolution.method() != null) {
            return mapJavaType(resolution.method().getReturnType());
        }
        raiseMemberError(ctxForError, "PERS2010", resolution.diagnostic());
        return "integer";
    }

    private void raiseMemberError(org.antlr.v4.runtime.ParserRuleContext ctxForError, String code, String message) {
        if (ctxForError != null) {
            throw error(ctxForError, code, message);
        }
        throw new DiagnosticException(CompilerDiagnostic.error(code, fileFromSourceName(), 1, 1, message));
    }

    private String fileFromSourceName() {
        return sourceName != null ? sourceName : "<input>";
    }

    private void requireBooleanOperands(PerseusParser.ExprContext left, PerseusParser.ExprContext right,
            PerseusParser.ExprContext whole, String code, String message) {
        if (!"boolean".equals(exprTypes.get(left)) || !"boolean".equals(exprTypes.get(right))) {
            throw error(whole, code, message);
        }
        exprTypes.put(whole, "boolean");
    }

    private String lookupType(String name) {
        for (Map<String, String> bindings : lambdaBindingStack) {
            String type = bindings.get(name);
            if (type != null) return type;
        }

        for (Map<String, String> bindings : anonymousLocalBindingStack) {
            String type = bindings.get(name);
            if (type != null) return type;
        }

        for (Map<String, String> bindings : exceptionBindingStack) {
            String type = bindings.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.MethodInfo method = methodStack.peek();
        if (method != null) {
            String type = method.localVars.get(name);
            if (type != null) return type;
            type = method.paramTypes.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.ProcInfo proc = procStack.peek();
        if (proc != null) {
            String type = proc.localVars.get(name);
            if (type != null) return type;
            type = proc.paramTypes.get(name);
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

    private String mapLambdaType(PerseusParser.LambdaParamTypeContext typeCtx) {
        if (typeCtx == null) return "integer";
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
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
        return "integer";
    }

    private String mapVarDeclType(PerseusParser.VarDeclContext ctx) {
        if (ctx.REAL() != null) return "real";
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.PROCEDURE() != null) return "procedure:void";
        return "integer";
    }

    private String mapVectorElementType(PerseusParser.VectorElementTypeContext ctx) {
        if (ctx.REAL() != null) return "real";
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.refType() != null) return "ref:" + ctx.refType().identifier().getText();
        return "integer";
    }

    private boolean isVectorElementCompatible(String elementType, String valueType) {
        if (elementType == null || valueType == null) {
            return false;
        }
        if (elementType.equals(valueType)) {
            return true;
        }
        if ("real".equals(elementType) && "integer".equals(valueType)) {
            return true;
        }
        return elementType.startsWith("ref:") && "null".equals(valueType);
    }

    private String anonymousProcedureBodyType(PerseusParser.AnonymousProcedureExprContext ctx) {
        PerseusParser.AnonymousProcedureBodyContext body = ctx.anonymousProcedureBody();
        if (body instanceof PerseusParser.AnonymousExprProcedureBodyContext exprBody) {
            return exprTypes.getOrDefault(exprBody.expr(), "integer");
        }
        if (body instanceof PerseusParser.AnonymousBlockProcedureBodyContext blockBody) {
            return anonymousProcedureCompoundType(blockBody.anonymousProcedureCompound());
        }
        if (body instanceof PerseusParser.AnonymousBraceProcedureBodyContext braceBody) {
            return anonymousProcedureCompoundType(braceBody.anonymousProcedureCompound());
        }
        return "void";
    }

    private String anonymousProcedureCompoundType(PerseusParser.AnonymousProcedureCompoundContext ctx) {
        if (ctx instanceof PerseusParser.AnonymousStatementExprProcedureCompoundContext stmtExpr) {
            return exprTypes.getOrDefault(stmtExpr.expr(), "integer");
        }
        if (ctx instanceof PerseusParser.AnonymousExprProcedureCompoundContext exprOnly) {
            return exprTypes.getOrDefault(exprOnly.expr(), "integer");
        }
        return "void";
    }

    private boolean isLambdaBodyCompatible(String declaredReturnType, String bodyType) {
        if (declaredReturnType == null || bodyType == null) {
            return false;
        }
        if (declaredReturnType.equals(bodyType)) {
            return true;
        }
        if ("void".equals(declaredReturnType) && "void".equals(bodyType)) {
            return true;
        }
        if ("real".equals(declaredReturnType) && "integer".equals(bodyType)) {
            return true;
        }
        if (declaredReturnType.startsWith("ref:") && "null".equals(bodyType)) {
            return true;
        }
        return false;
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

    private java.util.List<String> getArgTypes(PerseusParser.ArgListContext argList) {
        if (argList == null) {
            return java.util.List.of();
        }
        java.util.ArrayList<String> argTypes = new java.util.ArrayList<>();
        for (PerseusParser.ArgContext arg : argList.arg()) {
            argTypes.add(exprTypes.getOrDefault(arg.expr(), "integer"));
        }
        return argTypes;
    }

    private JavaInteropResolver.MethodResolution findJavaMethod(String qualifiedName, String memberName, java.util.List<String> argTypes) {
        return JavaInteropResolver.resolveMethod(qualifiedName, memberName, argTypes, this::scoreReferenceCompatibility);
    }

    private JavaInteropResolver.MethodResolution findJavaMethodInHierarchy(SymbolTableBuilder.ClassInfo cls, String memberName, java.util.List<String> argTypes) {
        SymbolTableBuilder.ClassInfo current = cls;
        JavaInteropResolver.MethodResolution lastFailure = null;
        while (current != null) {
            if (current.externalJava && current.externalJavaQualifiedName != null) {
                JavaInteropResolver.MethodResolution resolution = findJavaMethod(current.externalJavaQualifiedName, memberName, argTypes);
                if (resolution.method() != null) {
                    return resolution;
                }
                lastFailure = resolution;
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return lastFailure != null
                ? lastFailure
                : new JavaInteropResolver.MethodResolution(null, "Unknown class member: " + cls.name + "." + memberName);
    }

    private Field findJavaField(String qualifiedName, String memberName) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            return owner.getField(memberName);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
        }
        return null;
    }

    private Field findJavaFieldInHierarchy(SymbolTableBuilder.ClassInfo cls, String memberName) {
        SymbolTableBuilder.ClassInfo current = cls;
        while (current != null) {
            if (current.externalJava && current.externalJavaQualifiedName != null) {
                Field field = findJavaField(current.externalJavaQualifiedName, memberName);
                if (field != null) {
                    return field;
                }
            }
            current = current.parentName != null ? classes.get(current.parentName) : null;
        }
        return null;
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
            case "outstring", "outreal", "outinteger", "outchar", "outterminator" -> "void";
            default -> null;
        };
    }

    private DiagnosticException error(PerseusParser.ExprContext ctx, String code, String message) {
        return new DiagnosticException(CompilerDiagnostic.error(code, ctx.getStart(), sourceName, message));
    }

    private DiagnosticException error(org.antlr.v4.runtime.ParserRuleContext ctx, String code, String message) {
        return new DiagnosticException(CompilerDiagnostic.error(code, ctx.getStart(), sourceName, message));
    }

    private boolean isThrowableReferenceType(String type) {
        if (type == null || !type.startsWith("ref:")) {
            return false;
        }
        String className = type.substring("ref:".length());
        return isThrowableClassName(className, new java.util.HashSet<>());
    }

    private boolean isReferenceComparison(String leftType, String rightType) {
        return isReferenceLike(leftType) && isReferenceLike(rightType);
    }

    private boolean isReferenceLike(String type) {
        return "null".equals(type) || (type != null && type.startsWith("ref:"));
    }

    private boolean isThrowableClassName(String className, java.util.Set<String> seen) {
        if (!seen.add(className)) {
            return false;
        }
        SymbolTableBuilder.ClassInfo cls = classes.get(className);
        if (cls == null) {
            return false;
        }
        if (cls.externalJava && cls.externalJavaQualifiedName != null) {
            try {
                return Throwable.class.isAssignableFrom(Class.forName(cls.externalJavaQualifiedName));
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        if (cls.parentName != null) {
            return isThrowableClassName(cls.parentName, seen);
        }
        return false;
    }
}
