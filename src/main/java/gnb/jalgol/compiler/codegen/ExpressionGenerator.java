package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import gnb.jalgol.compiler.SymbolTableBuilder;
import gnb.jalgol.compiler.CodeGenUtils;
import java.util.Map;
import java.util.List;

/**
 * Handles expression code generation logic.
 */
public class ExpressionGenerator implements GeneratorDelegate {
    private ContextManager context;

    public ExpressionGenerator() {
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
    }

    public String generateExpr(AlgolParser.ExprContext ctx) {
        if (ctx instanceof AlgolParser.IntLiteralExprContext) {
            return "ldc " + ctx.getText() + "\n";
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext) {
            return "ldc2_w " + ctx.getText() + "\n";
        } else if (ctx instanceof AlgolParser.VarExprContext varCtx) {
            return generateLoadVar(varCtx.identifier().getText());
        } else if (ctx instanceof AlgolParser.AddSubExprContext binCtx) {
            String op = binCtx.op.getText();
            return generateExpr(binCtx.expr(0)) + generateExpr(binCtx.expr(1)) + (op.equals("+") ? "iadd\n" : "isub\n");
        }
        // Minimal implementation for build verification
        return "; expr logic missing\n";
    }

    public String generateLoadVar(String name) {
        Integer idx = context.getLocalIndex().get(name);
        String type = context.getSymbolTable().get(name);
        if (idx == null && context.getMainSymbolTable() != null) {
            idx = context.getMainSymbolTable().get(name) != null ? null : null; // Logic placeholder
        }
        if (idx == null) return "; ERROR: unknown variable " + name + "\n";
        if ("real".equals(type)) return "dload " + idx + "\n";
        return "iload " + idx + "\n";
    }

    public String generateStoreVar(String name) {
        Integer idx = context.getLocalIndex().get(name);
        String type = context.getSymbolTable().get(name);
        if (idx == null) return "; ERROR: unknown variable " + name + "\n";
        if ("real".equals(type)) return "dstore " + idx + "\n";
        return "istore " + idx + "\n";
    }
}
