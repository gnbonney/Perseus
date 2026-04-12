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
    private final Map<String, Type> symbolTable;
    private final Map<String, SymbolTableBuilder.ProcInfo> procedures;
    private final Map<String, SymbolTableBuilder.ClassInfo> classes;
    private final Map<PerseusParser.ExprContext, Type> exprTypes = new HashMap<>();
    private final Deque<SymbolTableBuilder.ClassInfo> classStack = new ArrayDeque<>();
    private final Deque<SymbolTableBuilder.ProcInfo> procStack = new ArrayDeque<>();
    private final Deque<SymbolTableBuilder.MethodInfo> methodStack = new ArrayDeque<>();
    private final Deque<Map<String, Type>> exceptionBindingStack = new ArrayDeque<>();
    private final Deque<Map<String, Type>> lambdaBindingStack = new ArrayDeque<>();
    private final Deque<Map<String, Type>> anonymousLocalBindingStack = new ArrayDeque<>();

    public TypeInferencer(String sourceName, Map<String, Type> symbolTable,
            Map<String, SymbolTableBuilder.ProcInfo> procedures,
            Map<String, SymbolTableBuilder.ClassInfo> classes) {
        this.sourceName = sourceName;
        this.symbolTable = symbolTable;
        this.procedures = procedures != null ? procedures : Map.of();
        this.classes = classes != null ? classes : Map.of();
    }

    public Map<PerseusParser.ExprContext, Type> getExprTypes() {
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
        exceptionBindingStack.push(Map.of(boundName, typed(boundType)));
    }

    @Override
    public void exitExceptionHandler(PerseusParser.ExceptionHandlerContext ctx) {
        if (!exceptionBindingStack.isEmpty()) {
            exceptionBindingStack.pop();
        }
    }

    @Override
    public void exitSignalStatement(PerseusParser.SignalStatementContext ctx) {
        String exprType = legacy(exprTypes.get(ctx.expr()));
        if (!isThrowableReferenceType(exprType)) {
            throw error(ctx, "PERS2011", "signal requires an exception object reference");
        }
    }

    @Override
    public void exitRelExpr(PerseusParser.RelExprContext ctx) {
        String leftType = legacy(exprTypes.get(ctx.expr(0)));
        String rightType = legacy(exprTypes.get(ctx.expr(1)));
        if (isReferenceComparison(leftType, rightType)) {
            String op = ctx.op.getText();
            if (!"=".equals(op) && !"<>".equals(op)) {
                throw error(ctx, "PERS2012", "reference comparisons only support = and <>");
            }
        } else if ("null".equals(leftType) || "null".equals(rightType)) {
            throw error(ctx, "PERS2012", "null comparisons require an object reference on the other side");
        }
        exprTypes.put(ctx, Type.BOOLEAN);
    }

    @Override
    public void exitMulDivExpr(PerseusParser.MulDivExprContext ctx) {
        String leftType = legacy(exprTypes.get(ctx.expr(0)));
        String rightType = legacy(exprTypes.get(ctx.expr(1)));
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
        exprTypes.put(ctx, typed(resultType));
    }

    @Override
    public void exitPowExpr(PerseusParser.PowExprContext ctx) {
        String leftType = legacy(exprTypes.get(ctx.expr(0)));
        String rightType = legacy(exprTypes.get(ctx.expr(1)));
        String resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        exprTypes.put(ctx, typed(resultType));
    }

    @Override
    public void exitAddSubExpr(PerseusParser.AddSubExprContext ctx) {
        String leftType = legacy(exprTypes.get(ctx.expr(0)));
        String rightType = legacy(exprTypes.get(ctx.expr(1)));
        String resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        exprTypes.put(ctx, typed(resultType));
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
        String operandType = legacy(exprTypes.get(ctx.expr()));
        if (!"boolean".equals(operandType)) {
            throw error(ctx, "PERS2005", "not operator requires boolean operand");
        }
        exprTypes.put(ctx, Type.BOOLEAN);
    }

    @Override
    public void exitVarExpr(PerseusParser.VarExprContext ctx) {
        String varName = ctx.identifier().getText();
        if ("maxreal".equals(varName) || "minreal".equals(varName) || "epsilon".equals(varName)) {
            exprTypes.put(ctx, Type.REAL);
            return;
        }
        if ("maxint".equals(varName)) {
            exprTypes.put(ctx, Type.INTEGER);
            return;
        }

        Type type = lookupType(varName);
        if (type == null) {
            throw error(ctx, "PERS2001", "Undeclared variable: " + varName);
        }
        exprTypes.put(ctx, type.unwrapThunk());
    }

    @Override
    public void exitArrayAccessExpr(PerseusParser.ArrayAccessExprContext ctx) {
        String arrName = ctx.identifier().getText();
        String arrType = legacy(lookupType(arrName));
        if (arrType == null) throw error(ctx, "PERS2002", "Undeclared array: " + arrName);
        if (arrType.startsWith("vector:")) {
            exprTypes.put(ctx, typed(arrType.substring("vector:".length())));
            return;
        }
        if (arrType.startsWith("map:")) {
            if (ctx.expr().size() != 1) {
                throw error(ctx, "PERS2010", "map access requires exactly one subscript");
            }
            String keyType = mapKeyType(arrType);
            String actualKeyType = legacy(exprTypes.get(ctx.expr(0)));
            if (!isMapTypeCompatible(keyType, actualKeyType)) {
                throw error(ctx, "PERS2010",
                        "map key type " + actualKeyType + " is incompatible with declared key type " + keyType);
            }
            exprTypes.put(ctx, typed(mapValueType(arrType)));
            return;
        }
        if (arrType.startsWith("set:")) {
            throw error(ctx, "PERS2010", "set values do not support indexed access");
        }
        exprTypes.put(ctx, typed(arrType.endsWith("[]") ? arrType.substring(0, arrType.length() - 2) : arrType));
    }

    @Override
    public void exitRealLiteralExpr(PerseusParser.RealLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.REAL);
    }

    @Override
    public void exitIntLiteralExpr(PerseusParser.IntLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.INTEGER);
    }

    @Override
    public void exitTrueLiteralExpr(PerseusParser.TrueLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.BOOLEAN);
    }

    @Override
    public void exitFalseLiteralExpr(PerseusParser.FalseLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.BOOLEAN);
    }

    @Override
    public void exitNullLiteralExpr(PerseusParser.NullLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.NULL);
    }

    @Override
    public void exitStringLiteralExpr(PerseusParser.StringLiteralExprContext ctx) {
        exprTypes.put(ctx, Type.STRING);
    }

    @Override
    public void exitUnaryMinusExpr(PerseusParser.UnaryMinusExprContext ctx) {
        exprTypes.put(ctx, exprTypes.getOrDefault(ctx.expr(), Type.INTEGER));
    }

    @Override
    public void exitParenExpr(PerseusParser.ParenExprContext ctx) {
        exprTypes.put(ctx, exprTypes.get(ctx.expr()));
    }

    @Override
    public void exitVectorLiteralExpr(PerseusParser.VectorLiteralExprContext ctx) {
        String literalType = inferVectorLiteralType(ctx);
        exprTypes.put(ctx, typed(literalType));
    }

    @Override
    public void exitSetLiteralExpr(PerseusParser.SetLiteralExprContext ctx) {
        String literalType = inferSetLiteralType(ctx);
        exprTypes.put(ctx, typed(literalType));
    }

    @Override
    public void exitMapLiteralExpr(PerseusParser.MapLiteralExprContext ctx) {
        String literalType = inferMapLiteralType(ctx);
        exprTypes.put(ctx, typed(literalType));
    }

    @Override
    public void enterAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        Map<String, Type> bindings = new HashMap<>();
        if (ctx.lambdaParamList() != null) {
            for (PerseusParser.LambdaParamContext param : ctx.lambdaParamList().lambdaParam()) {
                bindings.put(param.identifier().getText(), typed(mapLambdaType(param.lambdaParamType())));
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
        exprTypes.put(ctx, typed("procedure:" + declaredReturnType));
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
            anonymousLocalBindingStack.peek().put(id.getText(), typed(type));
        }
    }

    @Override
    public void exitAssignment(PerseusParser.AssignmentContext ctx) {
        String rhsType = legacy(exprTypes.get(ctx.expr()));
        for (PerseusParser.LvalueContext lvalue : ctx.lvalue()) {
            if (!lvalue.expr().isEmpty()) {
                String indexedType = legacy(lookupType(lvalue.identifier().getText()));
                if (indexedType != null && indexedType.startsWith("map:")) {
                    if (lvalue.expr().size() != 1) {
                        throw error(ctx, "PERS2010", "map assignment requires exactly one subscript");
                    }
                    String keyType = mapKeyType(indexedType);
                    String actualKeyType = legacy(exprTypes.get(lvalue.expr(0)));
                    if (!isMapTypeCompatible(keyType, actualKeyType)) {
                        throw error(ctx, "PERS2010",
                                "map key type " + actualKeyType + " is incompatible with declared key type " + keyType);
                    }
                    String valueType = mapValueType(indexedType);
                    if (!isMapTypeCompatible(valueType, rhsType)) {
                        throw error(ctx, "PERS2010",
                                "map value type " + rhsType + " is incompatible with declared value type " + valueType);
                    }
                }
                continue;
            }
            if (rhsType == null || !rhsType.startsWith("vector:")) {
                if (rhsType != null && rhsType.startsWith("set:")) {
                    String lhsType = legacy(lookupType(lvalue.identifier().getText()));
                    if (lhsType != null && lhsType.startsWith("set:") && !lhsType.equals(rhsType)) {
                        throw error(ctx, "PERS2010",
                                "Set assignment type " + rhsType + " is incompatible with destination type " + lhsType);
                    }
                } else if (rhsType != null && rhsType.startsWith("map:")) {
                    String lhsType = legacy(lookupType(lvalue.identifier().getText()));
                    if (lhsType != null && lhsType.startsWith("map:") && !lhsType.equals(rhsType)) {
                        throw error(ctx, "PERS2010",
                                "Map assignment type " + rhsType + " is incompatible with destination type " + lhsType);
                    }
                }
                continue;
            }
            String lhsType = legacy(lookupType(lvalue.identifier().getText()));
            if (lhsType != null && lhsType.startsWith("vector:") && !lhsType.equals(rhsType)) {
                throw error(ctx, "PERS2010",
                        "Vector assignment type " + rhsType + " is incompatible with destination type " + lhsType);
            }
        }
    }

    @Override
    public void enterRefDecl(PerseusParser.RefDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "ref:" + ctx.identifier().getText();
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), typed(type));
        }
    }

    @Override
    public void enterVectorDecl(PerseusParser.VectorDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "vector:" + mapVectorElementType(ctx.vectorElementType());
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), typed(type));
        }
    }

    @Override
    public void enterMapDecl(PerseusParser.MapDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "map:" + mapMapType(ctx.mapKeyType()) + "=>" + mapMapType(ctx.mapValueType());
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), typed(type));
        }
    }

    @Override
    public void enterSetDecl(PerseusParser.SetDeclContext ctx) {
        if (anonymousLocalBindingStack.isEmpty()) {
            return;
        }
        String type = "set:" + mapSetElementType(ctx.setElementType());
        for (PerseusParser.IdentifierContext id : ctx.varList().identifier()) {
            anonymousLocalBindingStack.peek().put(id.getText(), typed(type));
        }
    }

    @Override
    public void exitProcCallExpr(PerseusParser.ProcCallExprContext ctx) {
        String procName = ctx.identifier().getText();
        java.util.List<String> argTypes = getArgTypes(ctx.argList());
        String procType = legacy(lookupType(procName));
        if (procType != null && procType.startsWith("procedure:")) {
            exprTypes.put(ctx, typed(procType.substring("procedure:".length())));
            return;
        }

        String builtinType = getBuiltinFunctionType(procName);
        if (builtinType != null) {
            exprTypes.put(ctx, typed(builtinType));
            return;
        }

        SymbolTableBuilder.ClassInfo currentClass = classStack.peek();
        if (currentClass != null) {
            SymbolTableBuilder.MethodInfo method = findMethodInHierarchy(currentClass, procName);
            if (method != null) {
                exprTypes.put(ctx, method.returnTypeInfo);
                return;
            }
            JavaInteropResolver.MethodResolution resolution = findJavaMethodInHierarchy(currentClass, procName, argTypes);
            if (resolution.method() != null) {
                exprTypes.put(ctx, typed(mapJavaType(resolution.method().getReturnType())));
                return;
            }
            if (resolution.diagnostic() != null) {
                throw error(ctx, "PERS2010", resolution.diagnostic());
            }
        }
    }

    @Override
    public void exitIfExpr(PerseusParser.IfExprContext ctx) {
        exprTypes.put(ctx, exprTypes.getOrDefault(ctx.expr(1), Type.INTEGER));
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
        exprTypes.put(ctx, typed("ref:" + className));
    }

    @Override
    public void exitMemberCallExpr(PerseusParser.MemberCallExprContext ctx) {
        String memberType = resolveMemberAccessType(
                ctx.identifier(0).getText(),
                ctx.identifier(1).getText(),
                hasExplicitMemberCallSyntax(ctx),
                getArgTypes(ctx.argList()),
                ctx);
        exprTypes.put(ctx, typed(memberType));
    }

    @Override
    public void exitMemberCall(PerseusParser.MemberCallContext ctx) {
        resolveMemberAccessType(
                ctx.identifier(0).getText(),
                ctx.identifier(1).getText(),
                hasExplicitMemberCallSyntax(ctx),
                getArgTypes(ctx.argList()),
                null);
    }

    private String resolveMemberAccessType(String receiverName, String memberName, boolean explicitCall,
            java.util.List<String> argTypes, org.antlr.v4.runtime.ParserRuleContext ctxForError) {
        String receiverType = legacy(lookupType(receiverName));
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
            String elementType = receiverType.substring("vector:".length());
            if ("append".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector append requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "vector append requires exactly one argument");
                }
                if (!isVectorElementCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "vector append argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("insert".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector insert requires call syntax");
                }
                if (argTypes.size() != 2) {
                    raiseMemberError(ctxForError, "PERS2010", "vector insert requires exactly two arguments");
                }
                if (!"integer".equals(argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010", "vector insert index must be integer");
                }
                if (!isVectorElementCompatible(elementType, argTypes.get(1))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "vector insert argument type " + argTypes.get(1) + " is incompatible with element type " + elementType);
                }
                return "void";
            }
            if ("remove".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector remove requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "vector remove requires exactly one argument");
                }
                if (!"integer".equals(argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010", "vector remove index must be integer");
                }
                return elementType;
            }
            if ("contains".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector contains requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "vector contains requires exactly one argument");
                }
                if (!isVectorElementCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "vector contains argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("clear".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "vector clear requires call syntax");
                }
                if (!argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "vector clear does not take arguments");
                }
                return "void";
            }
            if ("size".equals(memberName)) {
                if (explicitCall && !argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "vector size() does not take arguments");
                }
                return "integer";
            }
            raiseMemberError(ctxForError, "PERS2010", "Unknown vector member: " + memberName);
        }
        if (receiverType.startsWith("map:")) {
            String keyType = mapKeyType(receiverType);
            String valueType = mapValueType(receiverType);
            if ("contains".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "map contains requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "map contains requires exactly one argument");
                }
                if (!isMapTypeCompatible(keyType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "map contains argument type " + argTypes.get(0) + " is incompatible with key type " + keyType);
                }
                return "boolean";
            }
            if ("remove".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "map remove requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "map remove requires exactly one argument");
                }
                if (!isMapTypeCompatible(keyType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "map remove argument type " + argTypes.get(0) + " is incompatible with key type " + keyType);
                }
                return valueType;
            }
            if ("clear".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "map clear requires call syntax");
                }
                if (!argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "map clear does not take arguments");
                }
                return "void";
            }
            if ("size".equals(memberName)) {
                if (explicitCall && !argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "map size() does not take arguments");
                }
                return "integer";
            }
            if ("keys".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "map keys requires call syntax");
                }
                if (!argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "map keys() does not take arguments");
                }
                return "set:" + keyType;
            }
            if ("values".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "map values requires call syntax");
                }
                if (!argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "map values() does not take arguments");
                }
                return "iterable:" + valueType;
            }
            raiseMemberError(ctxForError, "PERS2010", "Unknown map member: " + memberName);
        }
        if (receiverType.startsWith("set:")) {
            String elementType = receiverType.substring("set:".length());
            if ("insert".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "set insert requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "set insert requires exactly one argument");
                }
                if (!isMapTypeCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "set insert argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("contains".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "set contains requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "set contains requires exactly one argument");
                }
                if (!isMapTypeCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "set contains argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("remove".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "set remove requires call syntax");
                }
                if (argTypes.size() != 1) {
                    raiseMemberError(ctxForError, "PERS2010", "set remove requires exactly one argument");
                }
                if (!isMapTypeCompatible(elementType, argTypes.get(0))) {
                    raiseMemberError(ctxForError, "PERS2010",
                            "set remove argument type " + argTypes.get(0) + " is incompatible with element type " + elementType);
                }
                return "boolean";
            }
            if ("clear".equals(memberName)) {
                if (!explicitCall) {
                    raiseMemberError(ctxForError, "PERS2010", "set clear requires call syntax");
                }
                if (!argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "set clear does not take arguments");
                }
                return "void";
            }
            if ("size".equals(memberName)) {
                if (explicitCall && !argTypes.isEmpty()) {
                    raiseMemberError(ctxForError, "PERS2010", "set size() does not take arguments");
                }
                return "integer";
            }
            raiseMemberError(ctxForError, "PERS2010", "Unknown set member: " + memberName);
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
            return legacy(method.returnTypeInfo);
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
        if (!"boolean".equals(legacy(exprTypes.get(left))) || !"boolean".equals(legacy(exprTypes.get(right)))) {
            throw error(whole, code, message);
        }
        exprTypes.put(whole, Type.BOOLEAN);
    }

    private Type lookupType(String name) {
        for (Map<String, Type> bindings : lambdaBindingStack) {
            Type type = bindings.get(name);
            if (type != null) return type;
        }

        for (Map<String, Type> bindings : anonymousLocalBindingStack) {
            Type type = bindings.get(name);
            if (type != null) return type;
        }

        for (Map<String, Type> bindings : exceptionBindingStack) {
            Type type = bindings.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.MethodInfo method = methodStack.peek();
        if (method != null) {
            Type type = method.typedLocalVars.get(name);
            if (type != null) return type;
            type = method.typedParamTypes.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.ProcInfo proc = procStack.peek();
        if (proc != null) {
            Type type = proc.typedLocalVars.get(name);
            if (type != null) return type;
            type = proc.typedParamTypes.get(name);
            if (type != null) return type;
        }

        SymbolTableBuilder.ClassInfo cls = classStack.peek();
        if (cls != null) {
            Type type = cls.typedFields.get(name);
            if (type != null) return type;
            type = cls.typedParamTypes.get(name);
            if (type != null) return type;
        }

        return symbolTable.get(name);
    }

    private static String legacy(Type type) {
        return type != null ? type.toLegacyString() : null;
    }

    private static Type typed(String legacyType) {
        return Type.parse(legacyType);
    }

    private boolean hasExplicitMemberCallSyntax(org.antlr.v4.runtime.ParserRuleContext ctx) {
        return ctx != null && ctx.getChildCount() > 3;
    }

    private String mapLambdaType(PerseusParser.LambdaParamTypeContext typeCtx) {
        if (typeCtx == null) return "integer";
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
        if (typeCtx.vectorType() != null) return "vector:" + mapVectorElementType(typeCtx.vectorType().vectorElementType());
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
        if (typeCtx.vectorType() != null) return "vector:" + mapVectorElementType(typeCtx.vectorType().vectorElementType());
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

    private boolean isMapTypeCompatible(String declaredType, String actualType) {
        if (declaredType == null || actualType == null) {
            return false;
        }
        if (declaredType.equals(actualType)) {
            return true;
        }
        if ("real".equals(declaredType) && "integer".equals(actualType)) {
            return true;
        }
        return declaredType.startsWith("ref:") && "null".equals(actualType);
    }

    private String mapKeyType(String mapType) {
        int sep = mapType.indexOf("=>");
        return sep >= 0 ? mapType.substring("map:".length(), sep) : "integer";
    }

    private String mapValueType(String mapType) {
        int sep = mapType.indexOf("=>");
        return sep >= 0 ? mapType.substring(sep + 2) : "integer";
    }

    private String mapMapType(org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (ctx instanceof PerseusParser.MapKeyTypeContext keyCtx) {
            if (keyCtx.REAL() != null) return "real";
            if (keyCtx.INTEGER() != null) return "integer";
            if (keyCtx.STRING() != null) return "string";
            if (keyCtx.BOOLEAN() != null) return "boolean";
            if (keyCtx.refType() != null) return "ref:" + keyCtx.refType().identifier().getText();
        }
        if (ctx instanceof PerseusParser.MapValueTypeContext valueCtx) {
            if (valueCtx.REAL() != null) return "real";
            if (valueCtx.INTEGER() != null) return "integer";
            if (valueCtx.STRING() != null) return "string";
            if (valueCtx.BOOLEAN() != null) return "boolean";
            if (valueCtx.refType() != null) return "ref:" + valueCtx.refType().identifier().getText();
        }
        return "integer";
    }

    private String mapSetElementType(PerseusParser.SetElementTypeContext ctx) {
        if (ctx.REAL() != null) return "real";
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.refType() != null) return "ref:" + ctx.refType().identifier().getText();
        return "integer";
    }

    private String inferVectorLiteralType(PerseusParser.VectorLiteralExprContext ctx) {
        String current = null;
        for (PerseusParser.ExprContext expr : ctx.expr()) {
            String next = legacy(exprTypes.get(expr));
            if (next == null) {
                throw error(ctx, "PERS2010", "Unable to infer vector literal element type");
            }
            if (next.startsWith("vector:") || next.startsWith("procedure:") || "void".equals(next)) {
                throw error(ctx, "PERS2010", "Vector literals do not yet support elements of type " + next);
            }
            current = mergeVectorLiteralElementType(current, next, ctx);
        }
        if (current == null || "null".equals(current)) {
            throw error(ctx, "PERS2010", "Vector literals require at least one non-null element type");
        }
        return "vector:" + current;
    }

    private String inferSetLiteralType(PerseusParser.SetLiteralExprContext ctx) {
        String current = null;
        for (PerseusParser.ExprContext expr : ctx.expr()) {
            String next = legacy(exprTypes.get(expr));
            if (next == null) {
                throw error(ctx, "PERS2010", "Unable to infer set literal element type");
            }
            if (next.startsWith("vector:") || next.startsWith("map:") || next.startsWith("set:")
                    || next.startsWith("procedure:") || "void".equals(next)) {
                throw error(ctx, "PERS2010", "Set literals do not yet support elements of type " + next);
            }
            current = mergeSetLiteralElementType(current, next, ctx);
        }
        if (current == null || "null".equals(current)) {
            throw error(ctx, "PERS2010", "Set literals require at least one non-null element type");
        }
        return "set:" + current;
    }

    private String inferMapLiteralType(PerseusParser.MapLiteralExprContext ctx) {
        String currentKey = null;
        String currentValue = null;
        for (PerseusParser.MapLiteralEntryContext entry : ctx.mapLiteralEntry()) {
            String nextKey = legacy(exprTypes.get(entry.expr(0)));
            String nextValue = legacy(exprTypes.get(entry.expr(1)));
            if (nextKey == null || nextValue == null) {
                throw error(ctx, "PERS2010", "Unable to infer map literal entry types");
            }
            if (nextKey.startsWith("vector:") || nextKey.startsWith("map:") || nextKey.startsWith("set:")
                    || nextKey.startsWith("procedure:") || "void".equals(nextKey)) {
                throw error(ctx, "PERS2010", "Map literals do not yet support keys of type " + nextKey);
            }
            if (nextValue.startsWith("vector:") || nextValue.startsWith("map:") || nextValue.startsWith("set:")
                    || nextValue.startsWith("procedure:") || "void".equals(nextValue)) {
                throw error(ctx, "PERS2010", "Map literals do not yet support values of type " + nextValue);
            }
            currentKey = mergeMapLiteralComponentType(currentKey, nextKey, ctx, "key");
            currentValue = mergeMapLiteralComponentType(currentValue, nextValue, ctx, "value");
        }
        if (currentKey == null || "null".equals(currentKey)) {
            throw error(ctx, "PERS2010", "Map literals require at least one non-null key type");
        }
        if (currentValue == null || "null".equals(currentValue)) {
            throw error(ctx, "PERS2010", "Map literals require at least one non-null value type");
        }
        return "map:" + currentKey + "=>" + currentValue;
    }

    private String mergeSetLiteralElementType(String current, String next, PerseusParser.SetLiteralExprContext ctx) {
        if (current == null) {
            return next;
        }
        if (current.equals(next)) {
            return current;
        }
        if (("real".equals(current) && "integer".equals(next))
                || ("integer".equals(current) && "real".equals(next))) {
            return "real";
        }
        if (current.startsWith("ref:") && "null".equals(next)) {
            return current;
        }
        if ("null".equals(current) && next.startsWith("ref:")) {
            return next;
        }
        throw error(ctx, "PERS2010",
                "Set literal element type " + next + " is incompatible with earlier element type " + current);
    }

    private String mergeMapLiteralComponentType(String current, String next, PerseusParser.MapLiteralExprContext ctx, String role) {
        if (current == null) {
            return next;
        }
        if (current.equals(next)) {
            return current;
        }
        if (("real".equals(current) && "integer".equals(next))
                || ("integer".equals(current) && "real".equals(next))) {
            return "real";
        }
        if (current.startsWith("ref:") && "null".equals(next)) {
            return current;
        }
        if ("null".equals(current) && next.startsWith("ref:")) {
            return next;
        }
        throw error(ctx, "PERS2010",
                "Map literal " + role + " type " + next + " is incompatible with earlier " + role + " type " + current);
    }

    private String mergeVectorLiteralElementType(String current, String next, PerseusParser.VectorLiteralExprContext ctx) {
        if (current == null) {
            return next;
        }
        if (current.equals(next)) {
            return current;
        }
        if (("real".equals(current) && "integer".equals(next))
                || ("integer".equals(current) && "real".equals(next))) {
            return "real";
        }
        if (current.startsWith("ref:") && "null".equals(next)) {
            return current;
        }
        if ("null".equals(current) && next.startsWith("ref:")) {
            return next;
        }
        throw error(ctx, "PERS2010",
                "Vector literal element type " + next + " is incompatible with earlier element type " + current);
    }

    private String anonymousProcedureBodyType(PerseusParser.AnonymousProcedureExprContext ctx) {
        PerseusParser.AnonymousProcedureBodyContext body = ctx.anonymousProcedureBody();
        if (body instanceof PerseusParser.AnonymousExprProcedureBodyContext exprBody) {
            return legacy(exprTypes.getOrDefault(exprBody.expr(), Type.INTEGER));
        }
        if (body instanceof PerseusParser.AnonymousBlockProcedureBodyContext blockBody) {
            return anonymousProcedureCompoundType(blockBody.anonymousProcedureCompound());
        }
        return "void";
    }

    private String anonymousProcedureCompoundType(PerseusParser.AnonymousProcedureCompoundContext ctx) {
        if (ctx instanceof PerseusParser.AnonymousStatementExprProcedureCompoundContext stmtExpr) {
            return legacy(exprTypes.getOrDefault(stmtExpr.expr(), Type.INTEGER));
        }
        if (ctx instanceof PerseusParser.AnonymousExprProcedureCompoundContext exprOnly) {
            return legacy(exprTypes.getOrDefault(exprOnly.expr(), Type.INTEGER));
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
            argTypes.add(legacy(exprTypes.getOrDefault(arg.expr(), Type.INTEGER)));
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
