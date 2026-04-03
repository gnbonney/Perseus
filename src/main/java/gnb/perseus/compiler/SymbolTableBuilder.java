package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First-pass listener: builds symbol table metadata for main-scope variables,
 * procedures, arrays, labels, and the first slice of Perseus classes.
 */
public class SymbolTableBuilder extends PerseusBaseListener {
    private static final Map<String, String> BUILTIN_JAVA_EXCEPTION_TYPES = Map.ofEntries(
            Map.entry("Exception", "java.lang.Exception"),
            Map.entry("RuntimeException", "java.lang.RuntimeException"),
            Map.entry("IOException", "java.io.IOException"),
            Map.entry("FileNotFoundException", "java.io.FileNotFoundException"),
            Map.entry("NumberFormatException", "java.lang.NumberFormatException"),
            Map.entry("IllegalArgumentException", "java.lang.IllegalArgumentException"),
            Map.entry("IllegalStateException", "java.lang.IllegalStateException"),
            Map.entry("ArrayIndexOutOfBoundsException", "java.lang.ArrayIndexOutOfBoundsException"),
            Map.entry("IndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException"),
            Map.entry("ClassCastException", "java.lang.ClassCastException"),
            Map.entry("ArithmeticException", "java.lang.ArithmeticException"),
            Map.entry("NullPointerException", "java.lang.NullPointerException"));

    private final Map<String, String> symbolTable = new LinkedHashMap<>();
    private final Map<String, String> mainSymbolTable = new LinkedHashMap<>();
    private final Set<String> labels = new LinkedHashSet<>();
    private final Map<String, int[]> arrayBounds = new LinkedHashMap<>();
    private final Map<String, List<int[]>> arrayBoundPairs = new LinkedHashMap<>();
    private final Map<String, ProcInfo> procedures = new LinkedHashMap<>();
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, String> externalJavaClasses = new LinkedHashMap<>();
    private final Map<String, ExternalValueInfo> externalJavaStaticValues = new LinkedHashMap<>();
    private final Map<String, PerseusParser.SwitchDeclContext> switchDeclarations = new LinkedHashMap<>();
    private String namespaceName;
    private final Deque<ProcInfo> procStack = new ArrayDeque<>();
    private final Deque<ClassInfo> classStack = new ArrayDeque<>();
    private final Deque<MethodInfo> methodStack = new ArrayDeque<>();

    public SymbolTableBuilder() {
        for (Map.Entry<String, String> entry : BUILTIN_JAVA_EXCEPTION_TYPES.entrySet()) {
            registerExternalJavaClass(entry.getKey(), entry.getValue());
        }
    }

    private ProcInfo currentProc() {
        return procStack.isEmpty() ? null : procStack.peek();
    }

    private ClassInfo currentClass() {
        return classStack.isEmpty() ? null : classStack.peek();
    }

    private MethodInfo currentMethod() {
        return methodStack.isEmpty() ? null : methodStack.peek();
    }

    public static class ProcInfo {
        public final String returnType;
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Set<String> arrayParams = new LinkedHashSet<>();
        public final Map<String, String> localVars = new LinkedHashMap<>();
        public final Set<String> ownVars = new LinkedHashSet<>();
        public final Set<String> ownArrays = new LinkedHashSet<>();
        public final List<String> nestedProcedures = new ArrayList<>();
        public boolean external;
        public String externalKind;
        public String externalTargetClass;
        public String externalTargetMethod;

        public ProcInfo(String returnType) {
            this.returnType = returnType;
        }
    }

    public static class ExternalValueInfo {
        public final String localName;
        public final String type;
        public final String ownerClass;
        public final String targetMember;

        public ExternalValueInfo(String localName, String type, String ownerClass, String targetMember) {
            this.localName = localName;
            this.type = type;
            this.ownerClass = ownerClass;
            this.targetMember = targetMember;
        }
    }

    public static class MethodInfo {
        public final String returnType;
        public final PerseusParser.ProcedureDeclContext parseContext;
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Map<String, String> localVars = new LinkedHashMap<>();

        public MethodInfo(String returnType, PerseusParser.ProcedureDeclContext parseContext) {
            this.returnType = returnType;
            this.parseContext = parseContext;
        }
    }

    public static class ClassInfo {
        public final String name;
        public final PerseusParser.ClassDeclContext parseContext;
        public final String parentName;
        public final boolean externalJava;
        public final String externalJavaQualifiedName;
        public final List<String> interfaces = new ArrayList<>();
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, MethodInfo> methods = new LinkedHashMap<>();

        public ClassInfo(String name, PerseusParser.ClassDeclContext parseContext, String parentName,
                boolean externalJava, String externalJavaQualifiedName) {
            this.name = name;
            this.parseContext = parseContext;
            this.parentName = parentName;
            this.externalJava = externalJava;
            this.externalJavaQualifiedName = externalJavaQualifiedName;
        }
    }

    public Map<String, String> getSymbolTable() {
        return symbolTable;
    }

    public Map<String, String> getMainSymbolTable() {
        return mainSymbolTable;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public Map<String, int[]> getArrayBounds() {
        return arrayBounds;
    }

    public Map<String, List<int[]>> getArrayBoundPairs() {
        return arrayBoundPairs;
    }

    public Map<String, ProcInfo> getProcedures() {
        return procedures;
    }

    public Map<String, ClassInfo> getClasses() {
        return classes;
    }

    public Map<String, String> getExternalJavaClasses() {
        return externalJavaClasses;
    }

    public Map<String, ExternalValueInfo> getExternalJavaStaticValues() {
        return externalJavaStaticValues;
    }

    public Map<String, PerseusParser.SwitchDeclContext> getSwitchDeclarations() {
        return switchDeclarations;
    }

    public static Map<String, String> getBuiltInJavaExceptionTypes() {
        return BUILTIN_JAVA_EXCEPTION_TYPES;
    }

    public static String resolveBuiltInJavaExceptionQualifiedName(String simpleName) {
        return BUILTIN_JAVA_EXCEPTION_TYPES.get(simpleName);
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    @Override
    public void enterNamespaceDecl(PerseusParser.NamespaceDeclContext ctx) {
        namespaceName = ctx.qualifiedName().getText();
    }

    @Override
    public void enterClassDecl(PerseusParser.ClassDeclContext ctx) {
        String parentName = ctx.parentClass != null ? ctx.parentClass.getText() : null;
        String name = ctx.className.getText();
        ClassInfo cls = new ClassInfo(name, ctx, parentName, false, null);
        if (parentName != null) {
            ClassInfo parent = classes.get(parentName);
            if (parent != null) {
                cls.paramTypes.putAll(parent.paramTypes);
                cls.valueParams.addAll(parent.valueParams);
            }
        }
        if (ctx.interfaceList() != null) {
            for (PerseusParser.IdentifierContext id : ctx.interfaceList().identifier()) {
                cls.interfaces.add(id.getText());
            }
        }
        if (ctx.paramList() != null) {
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                cls.paramNames.add(id.getText());
            }
        }
        classes.put(name, cls);
        classStack.push(cls);
    }

    @Override
    public void exitClassDecl(PerseusParser.ClassDeclContext ctx) {
        classStack.pop();
    }

    @Override
    public void enterProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (currentClass() != null) {
            String returnType = getDeclaredReturnType(ctx.getStart().getText());
            String name = ctx.identifier().getText();
            MethodInfo method = new MethodInfo(returnType, ctx);
            if (ctx.paramList() != null) {
                for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                    method.paramNames.add(id.getText());
                }
            }
            currentClass().methods.put(name, method);
            methodStack.push(method);
            return;
        }

        String returnType = getDeclaredReturnType(ctx.getStart().getText());
        String name = ctx.identifier().getText();
        symbolTable.put(name, "procedure:" + returnType);

        ProcInfo outerProc = currentProc();
        if (outerProc != null) {
            outerProc.nestedProcedures.add(name);
        }

        ProcInfo newProc = new ProcInfo(returnType);
        procedures.put(name, newProc);
        procStack.push(newProc);

        if (ctx.paramList() != null) {
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                newProc.paramNames.add(id.getText());
            }
        }
    }

    @Override
    public void enterExternalProcedureDecl(PerseusParser.ExternalProcedureDeclContext ctx) {
        String returnType;
        if (ctx.INTEGER() != null) returnType = "integer";
        else if (ctx.REAL() != null) returnType = "real";
        else if (ctx.STRING() != null) returnType = "string";
        else returnType = "void";
        String declaredName = ctx.identifier(0).getText();
        String name = ctx.identifier().size() > 1 ? ctx.identifier(1).getText() : declaredName;

        symbolTable.put(name, "procedure:" + returnType);

        ProcInfo proc = new ProcInfo(returnType);
        proc.external = true;
        proc.externalTargetMethod = declaredName;
        if (ctx.externalProcSpec() instanceof PerseusParser.ExternalPerseusSpecContext perseusSpec) {
            proc.externalKind = "perseus";
            proc.externalTargetClass = perseusSpec.qualifiedName().getText();
        } else if (ctx.externalProcSpec() instanceof PerseusParser.ExternalAlgolSpecContext algolSpec) {
            proc.externalKind = "perseus";
            proc.externalTargetClass = algolSpec.qualifiedName().getText();
        } else if (ctx.externalProcSpec() instanceof PerseusParser.ExternalJavaStaticSpecContext javaSpec) {
            proc.externalKind = "java-static";
            proc.externalTargetClass = javaSpec.qualifiedName().getText();
        }

        if (ctx.externalFormalList() != null) {
            for (PerseusParser.ExternalFormalGroupContext group : ctx.externalFormalList().externalFormalGroup()) {
                String actualBaseType = mapExternalParamType(group.externalParamSpecType());
                boolean isArrayType = actualBaseType.endsWith("[]");
                for (PerseusParser.IdentifierContext id : group.identifier()) {
                    String paramName = id.getText();
                    proc.paramNames.add(paramName);
                    proc.paramTypes.put(paramName, actualBaseType);
                    proc.valueParams.add(paramName);
                    if (isArrayType) {
                        proc.arrayParams.add(paramName);
                    }
                }
            }
        }

        procedures.put(name, proc);
    }

    @Override
    public void enterExternalClassDecl(PerseusParser.ExternalClassDeclContext ctx) {
        String qualifiedName = ctx.qualifiedName().getText();
        String simpleName = ctx.qualifiedName().qualifiedNamePart(
                ctx.qualifiedName().qualifiedNamePart().size() - 1).getText();
        registerExternalJavaClass(simpleName, qualifiedName);
    }

    private void registerExternalJavaClass(String simpleName, String qualifiedName) {
        externalJavaClasses.put(simpleName, qualifiedName);
        classes.put(simpleName, new ClassInfo(simpleName, null, null, true, qualifiedName));
    }

    @Override
    public void exitProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
        if (currentMethod() != null) {
            methodStack.pop();
            return;
        }

        ProcInfo proc = procStack.peek();
        if (proc != null) {
            for (String param : proc.paramNames) {
                String baseType = proc.paramTypes.get(param);
                if (baseType == null) baseType = "deferred";
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
    public void enterValueSpec(PerseusParser.ValueSpecContext ctx) {
        MethodInfo method = currentMethod();
        if (method != null) {
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                method.valueParams.add(id.getText());
            }
            return;
        }

        ClassInfo cls = currentClass();
        if (cls != null) {
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                cls.valueParams.add(id.getText());
            }
            return;
        }

        ProcInfo proc = currentProc();
        if (proc != null) {
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                proc.valueParams.add(id.getText());
            }
        }
    }

    @Override
    public void enterParamSpec(PerseusParser.ParamSpecContext ctx) {
        MethodInfo method = currentMethod();
        if (method != null) {
            String actualBaseType = mapParamSpecType(ctx.paramSpecType());
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                String paramName = id.getText();
                method.paramTypes.put(paramName, actualBaseType);
                method.valueParams.add(paramName);
            }
            return;
        }

        ClassInfo cls = currentClass();
        if (cls != null) {
            String actualBaseType = mapParamSpecType(ctx.paramSpecType());
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                String paramName = id.getText();
                cls.paramTypes.put(paramName, actualBaseType);
                cls.valueParams.add(paramName);
            }
            return;
        }

        ProcInfo proc = currentProc();
        if (proc != null) {
            PerseusParser.ParamSpecTypeContext typeCtx = ctx.paramSpecType();
            String actualBaseType = mapParamSpecType(typeCtx);
            boolean isProcType = actualBaseType.startsWith("procedure:");
            boolean isArrayType = actualBaseType.endsWith("[]");
            for (PerseusParser.IdentifierContext id : ctx.paramList().identifier()) {
                String paramName = id.getText();
                proc.paramTypes.put(paramName, actualBaseType);
                if (isProcType) {
                    proc.valueParams.add(paramName);
                }
                if (isArrayType) {
                    proc.valueParams.add(paramName);
                    proc.arrayParams.add(paramName);
                }
                symbolTable.put(paramName, actualBaseType);
            }
        }
    }

    @Override
    public void enterVarDecl(PerseusParser.VarDeclContext ctx) {
        boolean isProcedure = ctx.PROCEDURE() != null;
        boolean isOwn = ctx.OWN() != null;
        String type;
        if (isProcedure) {
            if (ctx.REAL() != null) type = "procedure:real";
            else if (ctx.INTEGER() != null) type = "procedure:integer";
            else if (ctx.STRING() != null) type = "procedure:string";
            else type = "procedure:void";
        } else {
            if (ctx.REAL() != null) type = "real";
            else if (ctx.INTEGER() != null) type = "integer";
            else if (ctx.BOOLEAN() != null) type = "boolean";
            else if (ctx.STRING() != null) type = "string";
            else type = "integer";
        }

        for (PerseusParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();

            MethodInfo method = currentMethod();
            if (method != null) {
                method.localVars.put(name, type);
                symbolTable.put(name, type);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, type);
                continue;
            }

            ProcInfo proc = currentProc();
            symbolTable.put(name, type);
            if (proc == null) {
                mainSymbolTable.put(name, type);
            } else {
                proc.localVars.put(name, type);
                if (isOwn) {
                    proc.ownVars.add(name);
                    mainSymbolTable.put(name, type);
                }
            }
        }
    }

    @Override
    public void enterRefDecl(PerseusParser.RefDeclContext ctx) {
        String refType = "ref:" + ctx.identifier().getText();
        for (PerseusParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();

            MethodInfo method = currentMethod();
            if (method != null) {
                method.localVars.put(name, refType);
                symbolTable.put(name, refType);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, refType);
                continue;
            }

            symbolTable.put(name, refType);
            mainSymbolTable.put(name, refType);
        }
    }

    @Override
    public void enterExternalValueDecl(PerseusParser.ExternalValueDeclContext ctx) {
        String ownerClass = ctx.qualifiedName().getText();
        String refType = "ref:" + ctx.identifier(0).getText();
        String targetMember = ctx.identifier(1).getText();
        String localName = ctx.identifier(2).getText();
        symbolTable.put(localName, refType);
        mainSymbolTable.put(localName, refType);
        externalJavaStaticValues.put(localName, new ExternalValueInfo(localName, refType, ownerClass, targetMember));
    }

    @Override
    public void enterArrayDecl(PerseusParser.ArrayDeclContext ctx) {
        boolean isOwn = ctx.OWN() != null;
        String elemType;
        if (ctx.INTEGER() != null) elemType = "integer";
        else if (ctx.REAL() != null) elemType = "real";
        else if (ctx.STRING() != null) elemType = "string";
        else if (ctx.BOOLEAN() != null) elemType = "boolean";
        else elemType = "real";
        String arrType = elemType + "[]";
        String name = ctx.identifier().getText();
        List<int[]> bounds = new ArrayList<>();
        for (PerseusParser.BoundPairContext pairCtx : ctx.boundPair()) {
            int lower = Integer.parseInt(pairCtx.signedInt(0).getText());
            int upper = Integer.parseInt(pairCtx.signedInt(1).getText());
            bounds.add(new int[]{lower, upper});
        }
        symbolTable.put(name, arrType);
        mainSymbolTable.put(name, arrType);
        if (!bounds.isEmpty()) {
            arrayBounds.put(name, bounds.get(0));
            arrayBoundPairs.put(name, bounds);
        }
        ProcInfo proc = currentProc();
        if (proc != null && isOwn) {
            proc.ownArrays.add(name);
        }
    }

    @Override
    public void enterSwitchDecl(PerseusParser.SwitchDeclContext ctx) {
        String name = ctx.identifier().getText();
        symbolTable.put(name, "switch");
        switchDeclarations.put(name, ctx);
    }

    @Override
    public void enterLabel(PerseusParser.LabelContext ctx) {
        labels.add(ctx.getStart().getText());
    }

    private String getDeclaredReturnType(String firstToken) {
        if ("integer".equals(firstToken)) return "integer";
        if ("real".equals(firstToken)) return "real";
        if ("string".equals(firstToken)) return "string";
        return "void";
    }

    private String mapExternalParamType(PerseusParser.ExternalParamSpecTypeContext typeCtx) {
        if (typeCtx instanceof PerseusParser.ExternalRealArrayParamTypeContext) return "real[]";
        if (typeCtx instanceof PerseusParser.ExternalIntegerArrayParamTypeContext) return "integer[]";
        if (typeCtx instanceof PerseusParser.ExternalStringArrayParamTypeContext) return "string[]";
        if (typeCtx instanceof PerseusParser.ExternalBooleanArrayParamTypeContext) return "boolean[]";
        if (typeCtx instanceof PerseusParser.ExternalDefaultArrayParamTypeContext) return "real[]";
        if (typeCtx instanceof PerseusParser.ExternalRealParamTypeContext) return "real";
        if (typeCtx instanceof PerseusParser.ExternalIntegerParamTypeContext) return "integer";
        if (typeCtx instanceof PerseusParser.ExternalStringParamTypeContext) return "string";
        if (typeCtx instanceof PerseusParser.ExternalBooleanParamTypeContext) return "boolean";
        return "integer";
    }

    private String mapParamSpecType(PerseusParser.ParamSpecTypeContext typeCtx) {
        if (typeCtx instanceof PerseusParser.RealProcedureParamTypeContext) return "procedure:real";
        if (typeCtx instanceof PerseusParser.IntegerProcedureParamTypeContext) return "procedure:integer";
        if (typeCtx instanceof PerseusParser.StringProcedureParamTypeContext) return "procedure:string";
        if (typeCtx instanceof PerseusParser.VoidProcedureParamTypeContext) return "procedure:void";
        if (typeCtx instanceof PerseusParser.RealArrayParamTypeContext) return "real[]";
        if (typeCtx instanceof PerseusParser.IntegerArrayParamTypeContext) return "integer[]";
        if (typeCtx instanceof PerseusParser.StringArrayParamTypeContext) return "string[]";
        if (typeCtx instanceof PerseusParser.BooleanArrayParamTypeContext) return "boolean[]";
        if (typeCtx instanceof PerseusParser.DefaultArrayParamTypeContext) return "real[]";
        if (typeCtx instanceof PerseusParser.RealParamTypeContext) return "real";
        if (typeCtx instanceof PerseusParser.IntegerParamTypeContext) return "integer";
        if (typeCtx instanceof PerseusParser.StringParamTypeContext) return "string";
        if (typeCtx instanceof PerseusParser.BooleanParamTypeContext) return "boolean";
        return "integer";
    }
}
