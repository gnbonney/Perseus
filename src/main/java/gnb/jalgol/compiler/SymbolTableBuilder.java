package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * First-pass listener: builds symbol table (variables, types, scopes).
 * Uses LinkedHashMap to preserve declaration order for stable local variable slot assignment.
 */
public class SymbolTableBuilder extends AlgolBaseListener {
    // Ordered symbol table: name → type (e.g. "real"). Declaration order is preserved.
    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    // Set of label names (for forward reference checking)
    private final Set<String> labels = new LinkedHashSet<>();
    // Array bounds: name → [lowerBound, upperBound]
    private final Map<String, int[]> arrayBounds = new LinkedHashMap<>();

    public Map<String, String> getSymbolTable() {
        return symbolTable;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public Map<String, int[]> getArrayBounds() {
        return arrayBounds;
    }

    @Override
    public void enterVarDecl(AlgolParser.VarDeclContext ctx) {
        String type = ctx.getStart().getText(); // 'real', 'integer', or 'boolean'
        for (AlgolParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            symbolTable.put(idCtx.getText(), type);
        }
    }

    @Override
    public void enterArrayDecl(AlgolParser.ArrayDeclContext ctx) {
        String elemType = ctx.getStart().getText(); // 'integer' or 'real'
        String arrType = elemType + "[]";
        String name = ctx.identifier().getText();
        int lower = Integer.parseInt(ctx.unsignedInt(0).getText());
        int upper = Integer.parseInt(ctx.unsignedInt(1).getText());
        symbolTable.put(name, arrType);
        arrayBounds.put(name, new int[]{lower, upper});
    }

    @Override
    public void enterLabel(AlgolParser.LabelContext ctx) {
        labels.add(ctx.identifier().getText());
    }
}
