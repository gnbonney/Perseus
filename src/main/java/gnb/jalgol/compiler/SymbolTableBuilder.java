package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First-pass listener: builds symbol table (variables, types, scopes).
 * Uses LinkedHashMap to preserve declaration order for stable local variable slot assignment.
 */
public class SymbolTableBuilder extends AlgolBaseListener {
    // Ordered symbol table: name → type (e.g. "real"). Declaration order is preserved.
    private final Map<String, String> symbolTable = new LinkedHashMap<>();

    public Map<String, String> getSymbolTable() {
        return symbolTable;
    }

    @Override
    public void enterVarDecl(AlgolParser.VarDeclContext ctx) {
        String type = ctx.getStart().getText(); // 'real' (only type for milestone 2)
        for (AlgolParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            symbolTable.put(idCtx.getText(), type);
        }
    }
}
