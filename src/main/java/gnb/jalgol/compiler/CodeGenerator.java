// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import java.util.List;
import java.util.Map;

/**
 * Second-pass listener: emits Jasmin code using the pre-computed symbol table, local variable map, and expression types.
 * Handles both integer and real arithmetic.
 */
public class CodeGenerator extends AlgolBaseListener {
    private final String source;
    private final String packageName;
    private final String className;
    // Maps variable name → type ("integer" or "real")
    private final Map<String, String> symbolTable;
    // Maps variable name → JVM local variable slot index (doubles take 2 slots, ints take 1)
    private final Map<String, Integer> localIndex;
    // Total number of local variable slots needed (.limit locals)
    private final int numLocals;
    // Maps expression contexts to their inferred types ("integer" or "real")
    private final Map<AlgolParser.ExprContext, String> exprTypes;
    private final StringBuilder output = new StringBuilder();

    // For for loops
    private String currentForLoopLabel;
    private String currentForEndLabel;

    public CodeGenerator(String source, String packageName, String className,
                         Map<String, String> symbolTable, Map<String, Integer> localIndex, int numLocals,
                         Map<AlgolParser.ExprContext, String> exprTypes) {
        this.source = source;
        this.packageName = packageName;
        this.className = className;
        this.symbolTable = symbolTable;
        this.localIndex = localIndex;
        this.numLocals = numLocals;
        this.exprTypes = exprTypes;
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
        // Initialize all variables to 0
        for (Map.Entry<String, Integer> entry : localIndex.entrySet()) {
            String varName = entry.getKey();
            int index = entry.getValue();
            String type = symbolTable.get(varName);
            if ("integer".equals(type)) {
                output.append("iconst_0\n");
                output.append("istore ").append(index).append("\n");
            } else { // real
                output.append("dconst_0\n");
                output.append("dstore ").append(index).append("\n");
            }
        }
    }

    @Override
    public void exitProgram(AlgolParser.ProgramContext ctx) {
        output.append("return\n")
              .append(".end method\n");
    }

    @Override
    public void exitAssignment(AlgolParser.AssignmentContext ctx) {
        String exprType = exprTypes.getOrDefault(ctx.expr(), "real");
        java.util.List<AlgolParser.IdentifierContext> dests = ctx.identifier();

        // Determine common storage type: real if any dest is real
        boolean anyReal = dests.stream()
            .map(d -> symbolTable.getOrDefault(d.getText(), "real"))
            .anyMatch("real"::equals);
        String storeType = anyReal ? "real" : "integer";

        // Generate expression, widen to real if needed
        output.append(generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            output.append("i2d\n");
        }

        // Store to each destination; dup before all but the last
        for (int i = 0; i < dests.size(); i++) {
            String name = dests.get(i).getText();
            Integer idx = localIndex.get(name);
            if (idx == null) {
                output.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }
            String varType = symbolTable.get(name);
            if (i < dests.size() - 1) {
                output.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            }
            // Coerce real → integer if this destination is integer but others forced real
            if ("integer".equals(varType) && "real".equals(storeType)) {
                output.append("d2i\n");
            }
            output.append("integer".equals(varType) ? "istore " : "dstore ").append(idx).append("\n");
        }
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
        } else if ("outreal".equals(name)) {
            // outreal(channel, expr) — channel ignored, print double
            // invokevirtual requires object ref on stack first, then args
            output.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                  .append(generateExpr(args.get(1).expr())) // the expr in arg
                  .append("invokevirtual java/io/PrintStream/print(D)V\n");
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

    @Override
    public void enterIfStatement(AlgolParser.IfStatementContext ctx) {
        AlgolParser.ExprContext cond = ctx.expr();
        if (cond instanceof AlgolParser.RelExprContext rel) {
            // Generate left and right operands
            output.append(generateExpr(rel.expr(0)));
            output.append(generateExpr(rel.expr(1)));
            String op = rel.op.getText();
            String cmpInstr = switch (op) {
                case "<" -> "if_icmplt";
                case "<=" -> "if_icmple";
                case ">" -> "if_icmpgt";
                case ">=" -> "if_icmpge";
                case "=" -> "if_icmpeq";
                case "<>" -> "if_icmpne";
                default -> "if_icmpne";
            };
            String thenLabel = generateUniqueLabel("then");
            output.append(cmpInstr).append(" ").append(thenLabel).append("\n");
            // Skip then statement
            String endLabel = generateUniqueLabel("endif");
            output.append("goto ").append(endLabel).append("\n");
            output.append(thenLabel).append(":\n");
            // Then statement will be emitted here
            // After then, jump to end
            // But since then may be goto, we add goto end after the statement in exit
        } else {
            output.append("; non-rel if condition TODO\n");
        }
    }

    @Override
    public void exitIfStatement(AlgolParser.IfStatementContext ctx) {
        // Emit the end label (generated in enter)
        output.append("endif_" + (labelCounter - 1) + ":\n");
    }

    @Override
    public void enterForStatement(AlgolParser.ForStatementContext ctx) {
        String varName = ctx.identifier().getText();
        int varIndex = localIndex.get(varName);
        String varType = symbolTable.get(varName);

        // Generate start expression and store to var
        output.append(generateExpr(ctx.expr(0))); // start
        if ("real".equals(varType)) {
            output.append("dstore ").append(varIndex).append("\n");
        } else {
            output.append("istore ").append(varIndex).append("\n");
        }

        currentForLoopLabel = generateUniqueLabel("loop");
        currentForEndLabel = generateUniqueLabel("endfor");

        output.append(currentForLoopLabel).append(":\n");

        // Compare var against until: exit loop if var > until (assumes positive step)
        // TODO: handle negative step (check var < until when step < 0)
        if ("real".equals(varType)) {
            output.append("dload ").append(varIndex).append("\n");
            output.append(generateExpr(ctx.expr(2))); // until
            output.append("dcmpg\n");
            output.append("ifgt ").append(currentForEndLabel).append("\n");
        } else {
            output.append("iload ").append(varIndex).append("\n");
            output.append(generateExpr(ctx.expr(2))); // until
            output.append("if_icmpgt ").append(currentForEndLabel).append("\n");
        }
        // Statement will be emitted here
    }

    @Override
    public void exitForStatement(AlgolParser.ForStatementContext ctx) {
        String varName = ctx.identifier().getText();
        int varIndex = localIndex.get(varName);
        String varType = symbolTable.get(varName);

        // After statement, compute step and add to var
        output.append(generateExpr(ctx.expr(1))); // step
        if ("real".equals(varType)) {
            output.append("dload ").append(varIndex).append("\n");
            output.append("dadd\n");
            output.append("dstore ").append(varIndex).append("\n");
        } else {
            output.append("iload ").append(varIndex).append("\n");
            output.append("iadd\n");
            output.append("istore ").append(varIndex).append("\n");
        }

        output.append("goto ").append(currentForLoopLabel).append("\n");
        output.append(currentForEndLabel).append(":\n");
    }

    private String generateUniqueLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    private int labelCounter = 0;

    /**
     * Recursively generates Jasmin instructions for an expression.
     * Uses inferred types to select appropriate JVM instructions.
     */
    private String generateExpr(ExprContext ctx) {
        if (ctx instanceof AlgolParser.RelExprContext e) {
            // TODO: implement relational expressions
            return "; relational expr TODO\n";
        } else if (ctx instanceof AlgolParser.MulDivExprContext e) {
            String left = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String leftType = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            // Widen integers to real if needed
            if ("real".equals(type) && "integer".equals(leftType)) left += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("*".equals(op) ? "dmul" : "ddiv") :
                ("*".equals(op) ? "imul" : "idiv");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AddSubExprContext e) {
            String left = generateExpr(e.expr(0));
            String right = generateExpr(e.expr(1));
            String leftType = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            // Widen integers to real if needed
            if ("real".equals(type) && "integer".equals(leftType)) left += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("+".equals(op) ? "dadd" : "dsub") :
                ("+".equals(op) ? "iadd" : "isub");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();
            Integer idx = localIndex.get(name);
            if (idx == null) return "; ERROR: undeclared variable " + name + "\n";
            String type = symbolTable.get(name);
            return "integer".equals(type) ? "iload " + idx + "\n" : "dload " + idx + "\n";
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext e) {
            // e.g. 0.6 → ldc2_w 0.6
            String val = e.realLiteral().getText();
            return "ldc2_w " + val + "\n";
        } else if (ctx instanceof AlgolParser.IntLiteralExprContext e) {
            // Integer literal
            String val = e.unsignedInt().getText();
            return "ldc " + val + "\n";
        } else if (ctx instanceof AlgolParser.ParenExprContext e) {
            return generateExpr(e.expr());
        }
        return "; unknown expr type\n";
    }
}
