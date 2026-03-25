package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.HashMap;
import java.util.Map;

/**
 * Pass 1.5: Type inference for expressions.
 * Walks the parse tree and annotates each ExprContext with its resolved type ("integer" or "real").
 * Uses the symbol table to look up variable types.
 */
public class TypeInferencer extends PerseusBaseListener {
    private final String sourceName;
    private final Map<String, String> symbolTable;
    private final Map<PerseusParser.ExprContext, String> exprTypes = new HashMap<>();

    public TypeInferencer(String sourceName, Map<String, String> symbolTable) {
        this.sourceName = sourceName;
        this.symbolTable = symbolTable;
    }

    public Map<PerseusParser.ExprContext, String> getExprTypes() {
        return exprTypes;
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
        if ("/".equals(op)) {
            resultType = "real"; // / always real
        } else {
            // deferred type means runtime-dispatch numeric type, so treat as real in arithmetic
            if ("deferred".equals(leftType) || "deferred".equals(rightType)) {
                resultType = "real";
            } else {
                resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
            }
        }
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitAddSubExpr(PerseusParser.AddSubExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String resultType;
        if ("deferred".equals(leftType) || "deferred".equals(rightType)) {
            resultType = "real";
        } else {
            resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        }
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitAndExpr(PerseusParser.AndExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        if (!"boolean".equals(leftType) || !"boolean".equals(rightType)) {
            throw error(ctx, "PERS2003", "& operator requires boolean operands");
        }
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitOrExpr(PerseusParser.OrExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        if (!"boolean".equals(leftType) || !"boolean".equals(rightType)) {
            throw error(ctx, "PERS2004", "or operator requires boolean operands");
        }
        exprTypes.put(ctx, "boolean");
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
        
        // Check for environmental constants
        if ("maxreal".equals(varName) || "minreal".equals(varName) || "epsilon".equals(varName)) {
            exprTypes.put(ctx, "real");
            return;
        } else if ("maxint".equals(varName)) {
            exprTypes.put(ctx, "integer");
            return;
        }
        
        // Regular variable lookup
        String type = symbolTable.get(varName);
        if (type == null) {
            throw error(ctx, "PERS2001", "Undeclared variable: " + varName);
        }
        // call-by-name parameters are stored as "thunk:base" in the symbol table;
        // for type inference we only care about the underlying base type.
        if (type.startsWith("thunk:")) {
            type = type.substring("thunk:".length());
        }
        // Algol allows parameters without an explicit type (deferred typing).
        // Keep deferred as 'deferred' in expression typing so codegen can do
        // runtime dispatch instead of hard-casting to integer.
        exprTypes.put(ctx, type);
    }

    @Override
    public void exitArrayAccessExpr(PerseusParser.ArrayAccessExprContext ctx) {
        String arrName = ctx.identifier().getText();
        String arrType = symbolTable.get(arrName);
        if (arrType == null) throw error(ctx, "PERS2002", "Undeclared array: " + arrName);
        // "integer[]" → "integer",  "real[]" → "real"
        String elemType = arrType.endsWith("[]") ? arrType.substring(0, arrType.length() - 2) : arrType;
        exprTypes.put(ctx, elemType);
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
    public void exitStringLiteralExpr(PerseusParser.StringLiteralExprContext ctx) {
        exprTypes.put(ctx, "string");
    }

    @Override
    public void exitUnaryMinusExpr(PerseusParser.UnaryMinusExprContext ctx) {
        String innerType = exprTypes.get(ctx.expr());
        exprTypes.put(ctx, innerType == null ? "integer" : innerType);
    }

    @Override
    public void exitParenExpr(PerseusParser.ParenExprContext ctx) {
        String innerType = exprTypes.get(ctx.expr());
        exprTypes.put(ctx, innerType);
    }

    @Override
    public void exitProcCallExpr(PerseusParser.ProcCallExprContext ctx) {
        String procName = ctx.identifier().getText();
        
        // Check for built-in math functions first
        String builtinType = getBuiltinFunctionType(procName);
        if (builtinType != null) {
            exprTypes.put(ctx, builtinType);
            return;
        }
        
        // Otherwise, look up user-defined procedure
        String procType = symbolTable.get(procName);
        if (procType != null && procType.startsWith("procedure:")) {
            exprTypes.put(ctx, procType.substring("procedure:".length()));
        }
    }
    
    @Override
    public void exitFalseLiteralExpr(PerseusParser.FalseLiteralExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitIfExpr(PerseusParser.IfExprContext ctx) {
        // Result type is determined by the then-branch
        String thenType = exprTypes.get(ctx.expr(1));
        exprTypes.put(ctx, thenType != null ? thenType : "integer");
    }

    /**
     * Returns the return type of a built-in math function, or null if not recognized.
     */
    private String getBuiltinFunctionType(String name) {
        return switch (name) {
            case "sqrt", "abs", "sin", "cos", "arctan", "ln", "exp" -> "real";
            case "iabs", "sign", "entier" -> "integer";
            case "length" -> "integer";
            case "substring", "concat" -> "string";
            default -> null;
        };
    }

    private DiagnosticException error(PerseusParser.ExprContext ctx, String code, String message) {
        return new DiagnosticException(CompilerDiagnostic.error(code, ctx.getStart(), sourceName, message));
    }
}
