package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First-pass listener: builds symbol table (variables, types, scopes).
 * Uses LinkedHashMap to preserve declaration order for stable local variable slot assignment.
 */
public class SymbolTableBuilder extends AlgolBaseListener {
    // Ordered symbol table: name → type for ALL scopes (used by TypeInferencer)
    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    // Main-scope only symbol table: name → type (used for JVM slot assignment in main method)
    private final Map<String, String> mainSymbolTable = new LinkedHashMap<>();
    // Set of label names (for forward reference checking)
    private final Set<String> labels = new LinkedHashSet<>();
    // Array bounds: name → [lowerBound, upperBound]
    private final Map<String, int[]> arrayBounds = new LinkedHashMap<>();
    // Procedure definitions: name → ProcInfo
    private final Map<String, ProcInfo> procedures = new LinkedHashMap<>();
    private ProcInfo currentProc = null;

    /** Metadata for a declared procedure. */
    public static class ProcInfo {
        public final String returnType;
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Map<String, String> localVars = new LinkedHashMap<>();

        public ProcInfo(String returnType) {
            this.returnType = returnType;
        }
    }

    public Map<String, String> getSymbolTable() {
        return symbolTable;
    }

    /** Returns only main-scope variables (no procedure locals/params). Used for JVM slot assignment. */
    public Map<String, String> getMainSymbolTable() {
        return mainSymbolTable;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public Map<String, int[]> getArrayBounds() {
        return arrayBounds;
    }

    public Map<String, ProcInfo> getProcedures() {
        return procedures;
    }

    @Override
    public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        // First token is INTEGER/REAL/STRING (typed) or PROCEDURE (void)
        String firstToken = ctx.getStart().getText();
        String returnType;
        if ("integer".equals(firstToken)) returnType = "integer";
        else if ("real".equals(firstToken)) returnType = "real";
        else if ("string".equals(firstToken)) returnType = "string";
        else returnType = "void";
        String name = ctx.identifier().getText();

        // Add to global symbol table so TypeInferencer knows the return type
        symbolTable.put(name, "procedure:" + returnType);
        
        // Note: We used to add to mainSymbolTable here (which causes it to get a slot). 
        // We now handle procedure variables (slots) through a manual scan in AntlrAlgolListener.
        // mainSymbolTable.put(name, "procedure:" + returnType);

        currentProc = new ProcInfo(returnType);
        procedures.put(name, currentProc);

        // Collect parameter names from formal-parameter-list
        if (ctx.paramList() != null) {
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                currentProc.paramNames.add(id.getText());
            }
        }
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        // Add parameters to symbol table for type inference
        if (currentProc != null) {
            for (String param : currentProc.paramNames) {
                String baseType = currentProc.paramTypes.get(param);
                if (baseType == null) baseType = "real"; // default
                String type = currentProc.valueParams.contains(param) ? baseType : "thunk:" + baseType;
                symbolTable.put(param, type);
            }
        }
        currentProc = null;
    }

    @Override
    public void enterValueSpec(AlgolParser.ValueSpecContext ctx) {
        if (currentProc != null) {
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                currentProc.valueParams.add(id.getText());
            }
        }
    }

    @Override
    public void enterParamSpec(AlgolParser.ParamSpecContext ctx) {
        if (currentProc != null) {
            String type = ctx.getStart().getText(); // "integer", "real", "string", or "procedure"
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                String paramName = id.getText();
                String actualType = "procedure".equals(type) ? "procedure:real" : type;
                currentProc.paramTypes.put(paramName, actualType);
                // Add to global symbol table so TypeInferencer can resolve types of param uses
                symbolTable.put(paramName, actualType);
            }
        }
    }

    @Override
    public void enterVarDecl(AlgolParser.VarDeclContext ctx) {
        // Check if this is a procedure variable declaration
        boolean isProcedure = ctx.PROCEDURE() != null;
        String type;
        if (isProcedure) {
            // Check if there's a return type specified
            if (ctx.REAL() != null) type = "procedure:real";
            else if (ctx.INTEGER() != null) type = "procedure:integer";
            else if (ctx.STRING() != null) type = "procedure:string";
            else type = "procedure:void"; // untyped procedure variable
        } else {
            // Regular variable
            if (ctx.REAL() != null) type = "real";
            else if (ctx.INTEGER() != null) type = "integer";
            else if (ctx.BOOLEAN() != null) type = "boolean";
            else if (ctx.STRING() != null) type = "string";
            else type = "integer"; // default
        }
        
        for (AlgolParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();
            System.out.println("DEBUG: Declaring variable " + name + " with type " + type + " in " + (currentProc == null ? "main" : currentProc.paramNames.contains(name) ? "params" : "locals"));
            symbolTable.put(name, type); // always add to full table for TypeInferencer
            if (currentProc == null) {
                mainSymbolTable.put(name, type); // main scope only
            } else {
                currentProc.localVars.put(name, type);
            }
        }
    }

    @Override
    public void enterArrayDecl(AlgolParser.ArrayDeclContext ctx) {
        String elemType;
        if (ctx.INTEGER() != null) elemType = "integer";
        else if (ctx.REAL() != null) elemType = "real";
        else if (ctx.STRING() != null) elemType = "string";
        else elemType = "boolean";
        String arrType = elemType + "[]";
        String name = ctx.identifier().getText();
        int lower = Integer.parseInt(ctx.unsignedInt(0).getText());
        int upper = Integer.parseInt(ctx.unsignedInt(1).getText());
        symbolTable.put(name, arrType);
        mainSymbolTable.put(name, arrType);
        arrayBounds.put(name, new int[]{lower, upper});
    }

    @Override
    public void enterLabel(AlgolParser.LabelContext ctx) {
        labels.add(ctx.identifier().getText());
    }
}
