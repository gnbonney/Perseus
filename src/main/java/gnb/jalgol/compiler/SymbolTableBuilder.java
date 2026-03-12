package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    // Stack of currently-open procedure declarations (supports nested procedures like manboy)
    private final Deque<ProcInfo> procStack = new ArrayDeque<>();

    /** Convenience accessor: returns the innermost open procedure, or null if at top level. */
    private ProcInfo currentProc() { return procStack.isEmpty() ? null : procStack.peek(); }

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

        ProcInfo newProc = new ProcInfo(returnType);
        procedures.put(name, newProc);
        procStack.push(newProc);

        // Collect parameter names from formal-parameter-list
        if (ctx.paramList() != null) {
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                newProc.paramNames.add(id.getText());
            }
        }
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        // Add parameters to symbol table for type inference
        ProcInfo proc = procStack.peek();
        if (proc != null) {
            for (String param : proc.paramNames) {
                String baseType = proc.paramTypes.get(param);
                if (baseType == null) baseType = "integer"; // default for unspecified numeric params
                // procedure-type params are value params (passed as ProcRef); others depend on valueParams set
                if (baseType.startsWith("procedure:")) {
                    symbolTable.put(param, baseType);
                } else {
                    String type = proc.valueParams.contains(param) ? baseType : "thunk:" + baseType;
                    symbolTable.put(param, type);
                }
            }
        }
        procStack.pop();
    }

    @Override
    public void enterValueSpec(AlgolParser.ValueSpecContext ctx) {
        ProcInfo proc = currentProc();
        if (proc != null) {
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                proc.valueParams.add(id.getText());
            }
        }
    }

    @Override
    public void enterParamSpec(AlgolParser.ParamSpecContext ctx) {
        ProcInfo proc = currentProc();
        if (proc != null) {
            // Determine the type from the paramSpecType alternative
            AlgolParser.ParamSpecTypeContext typeCtx = ctx.paramSpecType();
            String actualBaseType;
            boolean isProcType = false;
            if (typeCtx instanceof AlgolParser.RealProcedureParamTypeContext) {
                actualBaseType = "procedure:real"; isProcType = true;
            } else if (typeCtx instanceof AlgolParser.IntegerProcedureParamTypeContext) {
                actualBaseType = "procedure:integer"; isProcType = true;
            } else if (typeCtx instanceof AlgolParser.StringProcedureParamTypeContext) {
                actualBaseType = "procedure:string"; isProcType = true;
            } else if (typeCtx instanceof AlgolParser.VoidProcedureParamTypeContext) {
                actualBaseType = "procedure:void"; isProcType = true;
            } else if (typeCtx instanceof AlgolParser.RealParamTypeContext) {
                actualBaseType = "real";
            } else if (typeCtx instanceof AlgolParser.IntegerParamTypeContext) {
                actualBaseType = "integer";
            } else if (typeCtx instanceof AlgolParser.StringParamTypeContext) {
                actualBaseType = "string";
            } else if (typeCtx instanceof AlgolParser.BooleanParamTypeContext) {
                actualBaseType = "boolean";
            } else {
                actualBaseType = "integer"; // fallback
            }
            for (AlgolParser.IdentifierContext id : ctx.paramList().identifier()) {
                String paramName = id.getText();
                proc.paramTypes.put(paramName, actualBaseType);
                if (isProcType) {
                    // Procedure parameters are passed as ProcRef objects (by value), not as thunks
                    proc.valueParams.add(paramName);
                }
                // Add to global symbol table so TypeInferencer can resolve types of param uses
                symbolTable.put(paramName, actualBaseType);
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
            ProcInfo proc = currentProc();
            System.out.println("DEBUG: Declaring variable " + name + " with type " + type + " in " + (proc == null ? "main" : proc.paramNames.contains(name) ? "params" : "locals"));
            symbolTable.put(name, type); // always add to full table for TypeInferencer
            if (proc == null) {
                mainSymbolTable.put(name, type); // main scope only
            } else {
                proc.localVars.put(name, type);
            }
        }
    }

    @Override
    public void enterArrayDecl(AlgolParser.ArrayDeclContext ctx) {
        String elemType;
        if (ctx.INTEGER() != null) elemType = "integer";
        else if (ctx.REAL() != null) elemType = "real";
        else if (ctx.STRING() != null) elemType = "string";
        else if (ctx.BOOLEAN() != null) elemType = "boolean";
        else elemType = "real"; // bare 'array' defaults to real per Algol 60
        String arrType = elemType + "[]";
        String name = ctx.identifier().getText();
        int lower = Integer.parseInt(ctx.signedInt(0).getText());
        int upper = Integer.parseInt(ctx.signedInt(1).getText());
        symbolTable.put(name, arrType);
        mainSymbolTable.put(name, arrType);
        arrayBounds.put(name, new int[]{lower, upper});
    }

    @Override
    public void enterLabel(AlgolParser.LabelContext ctx) {
        labels.add(ctx.identifier().getText());
    }
}
