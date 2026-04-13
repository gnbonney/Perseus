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
            Map.entry("EOFException", "java.io.EOFException"),
            Map.entry("NumberFormatException", "java.lang.NumberFormatException"),
            Map.entry("IllegalArgumentException", "java.lang.IllegalArgumentException"),
            Map.entry("IllegalStateException", "java.lang.IllegalStateException"),
            Map.entry("ArrayIndexOutOfBoundsException", "java.lang.ArrayIndexOutOfBoundsException"),
            Map.entry("IndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException"),
            Map.entry("ClassCastException", "java.lang.ClassCastException"),
            Map.entry("ArithmeticException", "java.lang.ArithmeticException"),
            Map.entry("NullPointerException", "java.lang.NullPointerException"));

    private final Map<String, Type> symbolTable = new LinkedHashMap<>();
    private final Map<String, Type> mainSymbolTable = new LinkedHashMap<>();
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
        public final Type returnTypeInfo;
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Map<String, Type> typedParamTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Set<String> arrayParams = new LinkedHashSet<>();
        public final Map<String, String> localVars = new LinkedHashMap<>();
        public final Map<String, Type> typedLocalVars = new LinkedHashMap<>();
        public final Set<String> ownVars = new LinkedHashSet<>();
        public final Set<String> ownArrays = new LinkedHashSet<>();
        public final List<String> nestedProcedures = new ArrayList<>();
        public boolean containsAnonymousProcedures;
        public boolean external;
        public String externalKind;
        public String externalTargetClass;
        public String externalTargetMethod;

        public ProcInfo(String returnType) {
            this.returnType = returnType;
            this.returnTypeInfo = Type.parse(returnType);
        }
    }

    public static class ExternalValueInfo {
        public final String localName;
        public final String type;
        public final Type typeInfo;
        public final String ownerClass;
        public final String targetMember;

        public ExternalValueInfo(String localName, String type, String ownerClass, String targetMember) {
            this.localName = localName;
            this.type = type;
            this.typeInfo = Type.parse(type);
            this.ownerClass = ownerClass;
            this.targetMember = targetMember;
        }
    }

    public static class MethodInfo {
        public final String returnType;
        public final Type returnTypeInfo;
        public final PerseusParser.ProcedureDeclContext parseContext;
        public final List<String> paramNames = new ArrayList<>();
        public final Map<String, String> paramTypes = new LinkedHashMap<>();
        public final Map<String, Type> typedParamTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Map<String, String> localVars = new LinkedHashMap<>();
        public final Map<String, Type> typedLocalVars = new LinkedHashMap<>();

        public MethodInfo(String returnType, PerseusParser.ProcedureDeclContext parseContext) {
            this.returnType = returnType;
            this.returnTypeInfo = Type.parse(returnType);
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
        public final Map<String, Type> typedParamTypes = new LinkedHashMap<>();
        public final Set<String> valueParams = new LinkedHashSet<>();
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, Type> typedFields = new LinkedHashMap<>();
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

    public Map<String, Type> getSymbolTable() {
        return symbolTable;
    }

    public Map<String, Type> getMainSymbolTable() {
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
                cls.typedParamTypes.putAll(parent.typedParamTypes);
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
            String returnType = getDeclaredProcedureReturnType(ctx);
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

        String returnType = getDeclaredProcedureReturnType(ctx);
        String name = ctx.identifier().getText();
        recordSymbol(name, "procedure:" + returnType);

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
        String returnType = getDeclaredExternalProcedureReturnType(ctx);
        String declaredName = ctx.identifier(0).getText();
        String name = ctx.identifier().size() > 1 ? ctx.identifier(1).getText() : declaredName;

        recordSymbol(name, "procedure:" + returnType);

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
                    proc.typedParamTypes.put(paramName, Type.parse(actualBaseType));
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
                    recordSymbol(param, baseType);
                } else {
                    String type = proc.valueParams.contains(param) ? baseType : "thunk:" + baseType;
                    recordSymbol(param, type);
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
    public void enterAnonymousProcedureExpr(PerseusParser.AnonymousProcedureExprContext ctx) {
        ProcInfo proc = currentProc();
        if (proc != null) {
            proc.containsAnonymousProcedures = true;
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
                method.typedParamTypes.put(paramName, Type.parse(actualBaseType));
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
                cls.typedParamTypes.put(paramName, Type.parse(actualBaseType));
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
                proc.typedParamTypes.put(paramName, Type.parse(actualBaseType));
                if (isProcType) {
                    proc.valueParams.add(paramName);
                }
                if (isArrayType) {
                    proc.valueParams.add(paramName);
                    proc.arrayParams.add(paramName);
                }
                recordSymbol(paramName, actualBaseType);
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
                method.typedLocalVars.put(name, Type.parse(type));
                recordSymbol(name, type);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, type);
                cls.typedFields.put(name, Type.parse(type));
                continue;
            }

            ProcInfo proc = currentProc();
            recordSymbol(name, type);
            if (proc == null) {
                recordMainSymbol(name, type);
            } else {
                proc.localVars.put(name, type);
                proc.typedLocalVars.put(name, Type.parse(type));
                if (isOwn) {
                    proc.ownVars.add(name);
                    recordMainSymbol(name, type);
                }
            }
        }
    }

    @Override
    public void enterVectorDecl(PerseusParser.VectorDeclContext ctx) {
        String elementType = mapVectorElementType(ctx.vectorElementType());
        String vectorType = "vector:" + elementType;
        for (PerseusParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();

            MethodInfo method = currentMethod();
            if (method != null) {
                method.localVars.put(name, vectorType);
                method.typedLocalVars.put(name, Type.parse(vectorType));
                recordSymbol(name, vectorType);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, vectorType);
                cls.typedFields.put(name, Type.parse(vectorType));
                continue;
            }

            ProcInfo proc = currentProc();
            recordSymbol(name, vectorType);
            if (proc == null) {
                recordMainSymbol(name, vectorType);
            } else {
                proc.localVars.put(name, vectorType);
                proc.typedLocalVars.put(name, Type.parse(vectorType));
            }
        }
    }

    @Override
    public void enterMapDecl(PerseusParser.MapDeclContext ctx) {
        String keyType = mapMapType(ctx.mapKeyType());
        String valueType = mapMapType(ctx.mapValueType());
        String mapType = "map:" + keyType + "=>" + valueType;
        for (PerseusParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();

            MethodInfo method = currentMethod();
            if (method != null) {
                method.localVars.put(name, mapType);
                method.typedLocalVars.put(name, Type.parse(mapType));
                recordSymbol(name, mapType);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, mapType);
                cls.typedFields.put(name, Type.parse(mapType));
                continue;
            }

            ProcInfo proc = currentProc();
            recordSymbol(name, mapType);
            if (proc == null) {
                recordMainSymbol(name, mapType);
            } else {
                proc.localVars.put(name, mapType);
                proc.typedLocalVars.put(name, Type.parse(mapType));
            }
        }
    }

    @Override
    public void enterSetDecl(PerseusParser.SetDeclContext ctx) {
        String elementType = mapSetElementType(ctx.setElementType());
        String setType = "set:" + elementType;
        for (PerseusParser.IdentifierContext idCtx : ctx.varList().identifier()) {
            String name = idCtx.getText();

            MethodInfo method = currentMethod();
            if (method != null) {
                method.localVars.put(name, setType);
                method.typedLocalVars.put(name, Type.parse(setType));
                recordSymbol(name, setType);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, setType);
                cls.typedFields.put(name, Type.parse(setType));
                continue;
            }

            ProcInfo proc = currentProc();
            recordSymbol(name, setType);
            if (proc == null) {
                recordMainSymbol(name, setType);
            } else {
                proc.localVars.put(name, setType);
                proc.typedLocalVars.put(name, Type.parse(setType));
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
                method.typedLocalVars.put(name, Type.parse(refType));
                recordSymbol(name, refType);
                continue;
            }

            ClassInfo cls = currentClass();
            if (cls != null) {
                cls.fields.put(name, refType);
                cls.typedFields.put(name, Type.parse(refType));
                continue;
            }

            recordSymbol(name, refType);
            recordMainSymbol(name, refType);
        }
    }

    @Override
    public void enterExternalValueDecl(PerseusParser.ExternalValueDeclContext ctx) {
        String ownerClass = ctx.qualifiedName().getText();
        String declaredType;
        if (ctx.externalValueType().REF() != null) {
            declaredType = "ref:" + ctx.externalValueType().identifier().getText();
        } else if (ctx.externalValueType().REAL() != null) {
            declaredType = "real";
        } else if (ctx.externalValueType().INTEGER() != null) {
            declaredType = "integer";
        } else if (ctx.externalValueType().BOOLEAN() != null) {
            declaredType = "boolean";
        } else if (ctx.externalValueType().STRING() != null) {
            declaredType = "string";
        } else {
            declaredType = "integer";
        }
        String targetMember = ctx.externalJavaIdentifier().getText();
        String localName = ctx.identifier().getText();
        recordSymbol(localName, declaredType);
        recordMainSymbol(localName, declaredType);
        externalJavaStaticValues.put(localName, new ExternalValueInfo(localName, declaredType, ownerClass, targetMember));
    }

    @Override
    public void enterArrayDecl(PerseusParser.ArrayDeclContext ctx) {
        boolean isOwn = ctx.OWN() != null;
        String elemType;
        if (ctx.INTEGER() != null) elemType = "integer";
        else if (ctx.REAL() != null) elemType = "real";
        else if (ctx.STRING() != null) elemType = "string";
        else if (ctx.BOOLEAN() != null) elemType = "boolean";
        else if (ctx.refType() != null) elemType = "ref:" + ctx.refType().identifier().getText();
        else elemType = "real";
        String arrType = elemType + "[]";
        String name = ctx.identifier().getText();
        List<int[]> bounds = new ArrayList<>();
        for (PerseusParser.BoundPairContext pairCtx : ctx.boundPair()) {
            int lower = Integer.parseInt(pairCtx.signedInt(0).getText());
            int upper = Integer.parseInt(pairCtx.signedInt(1).getText());
            bounds.add(new int[]{lower, upper});
        }
        recordSymbol(name, arrType);
        recordMainSymbol(name, arrType);
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
        symbolTable.put(name, Type.SWITCH);
        switchDeclarations.put(name, ctx);
    }

    @Override
    public void enterLabel(PerseusParser.LabelContext ctx) {
        labels.add(ctx.getStart().getText());
    }

    private void recordSymbol(String name, String legacyType) {
        symbolTable.put(name, Type.parse(legacyType));
    }

    private void recordMainSymbol(String name, String legacyType) {
        mainSymbolTable.put(name, Type.parse(legacyType));
    }

    private String getDeclaredProcedureReturnType(PerseusParser.ProcedureDeclContext ctx) {
        PerseusParser.ProcedureReturnTypeContext typeCtx = ctx.procedureReturnType();
        if (typeCtx == null) return "void";
        if (typeCtx instanceof PerseusParser.RealScalarProcedureReturnTypeContext) return "real";
        if (typeCtx instanceof PerseusParser.IntegerScalarProcedureReturnTypeContext) return "integer";
        if (typeCtx instanceof PerseusParser.StringScalarProcedureReturnTypeContext) return "string";
        if (typeCtx instanceof PerseusParser.BooleanScalarProcedureReturnTypeContext) return "boolean";
        if (typeCtx instanceof PerseusParser.RefScalarProcedureReturnTypeContext refCtx) {
            return "ref:" + refCtx.refType().identifier().getText();
        }
        if (typeCtx instanceof PerseusParser.VectorScalarProcedureReturnTypeContext vectorCtx) {
            return "vector:" + mapVectorElementType(vectorCtx.vectorType().vectorElementType());
        }
        if (typeCtx instanceof PerseusParser.RealProcedureProcedureReturnTypeContext) return "procedure:real";
        if (typeCtx instanceof PerseusParser.IntegerProcedureProcedureReturnTypeContext) return "procedure:integer";
        if (typeCtx instanceof PerseusParser.StringProcedureProcedureReturnTypeContext) return "procedure:string";
        if (typeCtx instanceof PerseusParser.BooleanProcedureProcedureReturnTypeContext) return "procedure:boolean";
        if (typeCtx instanceof PerseusParser.RefProcedureProcedureReturnTypeContext refProcCtx) {
            return "procedure:ref:" + refProcCtx.refType().identifier().getText();
        }
        if (typeCtx instanceof PerseusParser.VectorProcedureProcedureReturnTypeContext vectorProcCtx) {
            return "procedure:vector:" + mapVectorElementType(vectorProcCtx.vectorType().vectorElementType());
        }
        if (typeCtx instanceof PerseusParser.VoidProcedureProcedureReturnTypeContext) return "procedure:void";
        return "void";
    }

    private String getDeclaredExternalProcedureReturnType(PerseusParser.ExternalProcedureDeclContext ctx) {
        if (ctx.INTEGER() != null) return "integer";
        if (ctx.REAL() != null) return "real";
        if (ctx.STRING() != null) return "string";
        if (ctx.BOOLEAN() != null) return "boolean";
        if (ctx.refType() != null) return "ref:" + ctx.refType().identifier().getText();
        if (ctx.vectorType() != null) return "vector:" + mapVectorElementType(ctx.vectorType().vectorElementType());
        return "void";
    }

    private String mapExternalParamType(PerseusParser.ExternalParamSpecTypeContext typeCtx) {
        if (typeCtx instanceof PerseusParser.ExternalVectorParamTypeContext vectorParamCtx) {
            return "vector:" + mapVectorElementType(vectorParamCtx.vectorType().vectorElementType());
        }
        if (typeCtx instanceof PerseusParser.ExternalRefArrayParamTypeContext refArrayParamCtx) {
            return "ref:" + refArrayParamCtx.refType().identifier().getText() + "[]";
        }
        if (typeCtx instanceof PerseusParser.ExternalRefParamTypeContext refParamCtx) {
            return "ref:" + refParamCtx.refType().identifier().getText();
        }
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
        if (typeCtx instanceof PerseusParser.BooleanProcedureParamTypeContext) return "procedure:boolean";
        if (typeCtx instanceof PerseusParser.RefProcedureParamTypeContext refProcCtx) {
            return "procedure:ref:" + refProcCtx.refType().identifier().getText();
        }
        if (typeCtx instanceof PerseusParser.VectorProcedureParamTypeContext vectorProcCtx) {
            return "procedure:vector:" + mapVectorElementType(vectorProcCtx.vectorType().vectorElementType());
        }
        if (typeCtx instanceof PerseusParser.VoidProcedureParamTypeContext) return "procedure:void";
        if (typeCtx instanceof PerseusParser.RefParamTypeContext refParamCtx) {
            return "ref:" + refParamCtx.refType().identifier().getText();
        }
        if (typeCtx instanceof PerseusParser.VectorParamTypeContext vectorParamCtx) {
            return "vector:" + mapVectorElementType(vectorParamCtx.vectorType().vectorElementType());
        }
        if (typeCtx instanceof PerseusParser.RefArrayParamTypeContext refArrayCtx) {
            return "ref:" + refArrayCtx.refType().identifier().getText() + "[]";
        }
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

    private String mapVectorElementType(PerseusParser.VectorElementTypeContext typeCtx) {
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
        return "integer";
    }

    private String mapMapType(org.antlr.v4.runtime.ParserRuleContext typeCtx) {
        if (typeCtx instanceof PerseusParser.MapKeyTypeContext keyCtx) {
            if (keyCtx.REAL() != null) return "real";
            if (keyCtx.INTEGER() != null) return "integer";
            if (keyCtx.BOOLEAN() != null) return "boolean";
            if (keyCtx.STRING() != null) return "string";
            if (keyCtx.refType() != null) return "ref:" + keyCtx.refType().identifier().getText();
        }
        if (typeCtx instanceof PerseusParser.MapValueTypeContext valueCtx) {
            if (valueCtx.REAL() != null) return "real";
            if (valueCtx.INTEGER() != null) return "integer";
            if (valueCtx.BOOLEAN() != null) return "boolean";
            if (valueCtx.STRING() != null) return "string";
            if (valueCtx.refType() != null) return "ref:" + valueCtx.refType().identifier().getText();
        }
        return "integer";
    }

    private String mapSetElementType(PerseusParser.SetElementTypeContext typeCtx) {
        if (typeCtx.REAL() != null) return "real";
        if (typeCtx.INTEGER() != null) return "integer";
        if (typeCtx.BOOLEAN() != null) return "boolean";
        if (typeCtx.STRING() != null) return "string";
        if (typeCtx.refType() != null) return "ref:" + typeCtx.refType().identifier().getText();
        return "integer";
    }
}
