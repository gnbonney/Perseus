package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.HashMap;
import java.util.Map;

/**
 * Pass 1.5: Type inference for expressions.
 * Walks the parse tree and annotates each ExprContext with its resolved type ("integer" or "real").
 * Uses the symbol table to look up variable types.
 */
public class TypeInferencer extends AlgolBaseListener {
    private final Map<String, String> symbolTable;
    private final Map<AlgolParser.ExprContext, String> exprTypes = new HashMap<>();

    public TypeInferencer(Map<String, String> symbolTable) {
        this.symbolTable = symbolTable;
    }

    public Map<AlgolParser.ExprContext, String> getExprTypes() {
        return exprTypes;
    }

    @Override
    public void exitRelExpr(AlgolParser.RelExprContext ctx) {
        // Relational expressions always yield Boolean, but for now we don't store it
        // since if statements handle it directly
    }

    @Override
    public void exitMulDivExpr(AlgolParser.MulDivExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String op = ctx.op.getText();
        String resultType;
        if ("/".equals(op)) {
            resultType = "real"; // / always real
        } else { // *
            resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        }
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitAddSubExpr(AlgolParser.AddSubExprContext ctx) {
        String leftType = exprTypes.get(ctx.expr(0));
        String rightType = exprTypes.get(ctx.expr(1));
        String resultType = ("integer".equals(leftType) && "integer".equals(rightType)) ? "integer" : "real";
        exprTypes.put(ctx, resultType);
    }

    @Override
    public void exitVarExpr(AlgolParser.VarExprContext ctx) {
        String varName = ctx.identifier().getText();
        String type = symbolTable.get(varName);
        if (type == null) {
            throw new RuntimeException("Undeclared variable: " + varName);
        }
        exprTypes.put(ctx, type);
    }

    @Override
    public void exitRealLiteralExpr(AlgolParser.RealLiteralExprContext ctx) {
        exprTypes.put(ctx, "real");
    }

    @Override
    public void exitIntLiteralExpr(AlgolParser.IntLiteralExprContext ctx) {
        exprTypes.put(ctx, "integer");
    }

    @Override
    public void exitTrueLiteralExpr(AlgolParser.TrueLiteralExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitFalseLiteralExpr(AlgolParser.FalseLiteralExprContext ctx) {
        exprTypes.put(ctx, "boolean");
    }

    @Override
    public void exitParenExpr(AlgolParser.ParenExprContext ctx) {
        String innerType = exprTypes.get(ctx.expr());
        exprTypes.put(ctx, innerType);
    }
}