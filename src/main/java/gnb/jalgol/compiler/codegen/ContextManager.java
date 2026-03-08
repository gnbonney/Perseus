package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.Map;
import java.util.Set;

/**
 * Manages the shared state and common utilities for the code generation process.
 * This class serves as the central point for state management that was previously
 * scattered throughout CodeGenerator.
 */
public class ContextManager {
    private final String packageName;
    private final String className;
    private final Map<String, String> symbolTable;
    private final Map<String, Integer> localIndex;
    private final Map<AlgolParser.ExprContext, String> exprTypes;
    private final Map<String, int[]> arrayBounds;
    private int numLocals;

    public ContextManager(String packageName, String className, Map<String, String> symbolTable, Map<String, Integer> localIndex, Map<AlgolParser.ExprContext, String> exprTypes, Map<String, int[]> arrayBounds) {
        this.packageName = packageName;
        this.className = className;
        this.symbolTable = symbolTable;
        this.localIndex = localIndex;
        this.exprTypes = exprTypes;
        this.arrayBounds = arrayBounds;
        this.numLocals = localIndex.size();
    }

    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
    public Map<String, String> getSymbolTable() { return symbolTable; }
    public Map<String, Integer> getLocalIndex() { return localIndex; }
    public Map<AlgolParser.ExprContext, String> getExprTypes() { return exprTypes; }
    public Map<String, int[]> getArrayBounds() { return arrayBounds; }

    public void setNumLocals(int numLocals) {
        this.numLocals = numLocals;
    }

    public int allocateNewLocal(String hint) {
        int slot = numLocals++;
        localIndex.put(hint + slot, slot);
        return slot;
    }

    public int getNumLocals() {
        return numLocals;
    }
}
