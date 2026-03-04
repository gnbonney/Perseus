// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import java.util.List;
import java.util.Map;

/**
 * Second-pass listener: emits Jasmin code using the pre-computed symbol table and local variable map.
 * All numeric variables are treated as JVM double (type D) for Milestone 2.
 */
public class CodeGenerator extends AlgolBaseListener {
    private final String source;
    private final String packageName;
    private final String className;
    // Maps variable name → JVM local variable slot index (doubles occupy 2 slots each)
    private final Map<String, Integer> localIndex;
    // Total number of local variable slots needed (.limit locals)
    private final int numLocals;
    private final StringBuilder output = new StringBuilder();

    public CodeGenerator(String source, String packageName, String className,
                         Map<String, Integer> localIndex, int numLocals) {
        this.source = source;
        this.packageName = packageName;
        this.className = className;
        this.localIndex = localIndex;
        this.numLocals = numLocals;
    }

    public String getOutput() {
        return output.toString();
    }

    @Override
    public void enterProgram(AlgolParser.ProgramContext ctx) {
        output.append(".source ").append(source).append("\n")
              .append(".class public ").append(packageName).append("/").append(className).append("\n")
              .append(".super java/lang/Object\n\n")
              .append(".method public <init>()V\n")
              .append(".limit stack 1\n")
              .append(".limit locals 1\n")
              .append("aload_0\n")
              .append("invokespecial java/lang/Object/<init>()V\n")
              .append("return\n")
              .append(".end method\n\n")
              .append(".method public static main([Ljava/lang/String;)V\n")
              .append(".limit stack 16\n") // TODO: compute via static stack analysis
              .append(".limit locals ").append(numLocals).append("\n");
    }

    @Override
    public void exitProgram(AlgolParser.ProgramContext ctx) {
        output.append("return\n")
              .append(".end method\n");
    }

    @Override
    public void exitAssignment(AlgolParser.AssignmentContext ctx) {
        String name = ctx.identifier().getText();
        Integer idx = localIndex.get(name);
        if (idx == null) {
            output.append("; ERROR: undeclared variable ").append(name).append("\n");
            return;
        }
        output.append(generateExpr(ctx.expr()));
        output.append("dstore ").append(idx).append("\n");
    }

    @Override
    public void exitProcedureCall(AlgolParser.ProcedureCallContext ctx) {
        String name = ctx.identifier().getText();
        List<AlgolParser.ArgContext> args = ctx.argList().arg();
        if ("outstring".equals(name)) {
            // outstring(channel, string) — channel ignored, always write to System.out
            String str = args.get(1).getText();
            output.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                  .append("ldc ").append(str).append("\n")
                  .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        }
    }

    @Override
    public void enterLabel(AlgolParser.LabelContext ctx) {
        String labelName = ctx.identifier().getText();
        output.append(labelName).append(":\n");
    }

    @Override
    public void exitGotoStatement(AlgolParser.GotoStatementContext ctx) {
        String labelName = ctx.identifier().getText();
        output.append("goto ").append(labelName).append("\n");
    }

    /**
     * Recursively generates Jasmin instructions for an expression.
     * All arithmetic is performed in double precision (JVM type D).
     * Integer literals are coerced to double via i2d.
     */
    private String generateExpr(ExprContext ctx) {
        if (ctx instanceof AlgolParser.MulDivExprContext e) {
            String left = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String op = e.op.getText().equals("*") ? "dmul" : "ddiv";
            return left + right + op + "\n";
        } else if (ctx instanceof AlgolParser.AddSubExprContext e) {
            String left = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String op = e.op.getText().equals("+") ? "dadd" : "dsub";
            return left + right + op + "\n";
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();
            Integer idx = localIndex.get(name);
            if (idx == null) return "; ERROR: undeclared variable " + name + "\n";
            return "dload " + idx + "\n";
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext e) {
            // e.g. 0.6 → ldc2_w 0.6
            String val = e.realLiteral().getText();
            return "ldc2_w " + val + "\n";
        } else if (ctx instanceof AlgolParser.IntLiteralExprContext e) {
            // Integer literal in real context: push int constant, then widen to double
            String val = e.unsignedInt().getText();
            return "ldc " + val + "\ni2d\n";
        } else if (ctx instanceof AlgolParser.ParenExprContext e) {
            return generateExpr(e.expr());
        }
        return "; unknown expr type\n";
    }
}
