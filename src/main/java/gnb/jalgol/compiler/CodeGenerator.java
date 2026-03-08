package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.codegen.ContextManager;
import gnb.jalgol.compiler.codegen.ExpressionGenerator;
import gnb.jalgol.compiler.codegen.ProcedureGenerator;
import gnb.jalgol.compiler.codegen.StatementGenerator;
import gnb.jalgol.compiler.SymbolTableBuilder.ProcInfo;
import java.util.Map;

public class CodeGenerator extends AlgolBaseListener {
    private final ContextManager context;
    private final ExpressionGenerator exprGen;
    private final StatementGenerator stmtGen;
    private final ProcedureGenerator procGen;

    private final String sourceFile;
    private final String packageName;
    private final String className;

    public CodeGenerator(String sourceFile, String packageName, String className,
                         Map<String, String> symbolTable, Map<String, Integer> localIndex,
                         int numLocals, Map<AlgolParser.ExprContext, String> exprTypes,
                         Map<String, int[]> arrayBounds, Map<String, ProcInfo> procedures) {
        this.sourceFile = sourceFile;
        this.packageName = packageName;
        this.className = className;
        
        this.context = new ContextManager(sourceFile, packageName, className, procedures, exprTypes);
        this.context.setSymbolTable(symbolTable);
        this.context.setLocalIndex(localIndex);
        this.context.setNextLocalIndex(numLocals);
        this.context.setArrayBounds(arrayBounds);
        this.context.saveMainContext();
        
        this.exprGen = new ExpressionGenerator();
        this.procGen = new ProcedureGenerator(context, exprGen);
        this.stmtGen = new StatementGenerator(exprGen, procGen);
        
        this.exprGen.setContext(context);
        this.stmtGen.setContext(context);
    }

    public String getJasminCode() {
        return context.finalizeAssembly();
    }

    public String getOutput() {
        return getJasminCode();
    }

    public Map<String, String> getThunkClassOutputs() {
        return context.getThunkClasses();
    }

    public Map<String, String> getProcRefClassOutputs() {
        return java.util.Map.of();
    }

    private void syncContext() {
        stmtGen.setProcedureContext(context.isInProcedureDecl(), context.isInProcedureWalk());
    }

    @Override
    public void enterProgram(AlgolParser.ProgramContext ctx) {
        context.getActiveOutput().append(".class public ").append(packageName).append("/").append(className).append("\n")
                .append(".super java/lang/Object\n\n")
                .append(".field public static __scanner Ljava/util/Scanner;\n\n")
                .append(".method public static main([Ljava/lang/String;)V\n")
                .append(".limit stack 50\n.limit locals 50\n")
                .append("new java/util/Scanner\ndup\ngetstatic java/lang/System/in Ljava/io/InputStream;\n")
                .append("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n")
                .append("putstatic ").append(packageName).append("/").append(className).append("/__scanner Ljava/util/Scanner;\n");
    }

    @Override
    public void exitProgram(AlgolParser.ProgramContext ctx) {
        context.getActiveOutput().append("return\n.end method\n");
    }

    @Override
    public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        context.setInProcedureDecl(true);
        context.setInProcedureWalk(false);
        procGen.enterProcedureDecl(ctx);
        syncContext();
    }

    @Override
    public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
        procGen.exitProcedureDecl(ctx);
        context.setInProcedureDecl(false);
        syncContext();
    }

    @Override
    public void enterIfStatement(AlgolParser.IfStatementContext ctx) {
        stmtGen.enterIfStatement(ctx, context.getActiveOutput());
    }

    @Override
    public void exitIfStatement(AlgolParser.IfStatementContext ctx) {
        stmtGen.exitIfStatement(ctx, context.getActiveOutput());
    }

    @Override
    public void exitAssignment(AlgolParser.AssignmentContext ctx) {
        stmtGen.exitAssignment(ctx, context.getActiveOutput(), packageName, className);
    }

    @Override
    public void enterForStatement(AlgolParser.ForStatementContext ctx) {
        stmtGen.enterForStatement(ctx, context.getActiveOutput());
    }

    @Override
    public void exitForStatement(AlgolParser.ForStatementContext ctx) {
        stmtGen.exitForStatement(ctx, context.getActiveOutput());
    }

    @Override
    public void exitProcedureCall(AlgolParser.ProcedureCallContext ctx) {
        stmtGen.exitProcedureCall(ctx, context.getActiveOutput());
    }
}
