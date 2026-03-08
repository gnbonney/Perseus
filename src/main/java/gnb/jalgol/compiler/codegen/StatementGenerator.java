package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.CodeGenUtils;
import java.util.Stack;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Handles statement code generation logic.
 */
public class StatementGenerator implements GeneratorDelegate {
    private ContextManager context;
    private ExpressionGenerator exprGen;
    private ProcedureGenerator procGen;

    private final Stack<String> ifEndLabelStack = new Stack<>();
    private final Stack<String> ifElseLabelStack = new Stack<>();
    private String currentForLoopLabel;
    private String currentForEndLabel;

    private boolean isInProcedureDecl;
    private boolean inProcedureWalk;

    public StatementGenerator(ExpressionGenerator exprGen, ProcedureGenerator procGen) {
        this.exprGen = exprGen;
        this.procGen = procGen;
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
        this.exprGen.setContext(context);
    }

    public void setProcedureContext(boolean isInProcedureDecl, boolean inProcedureWalk) {
        this.isInProcedureDecl = isInProcedureDecl;
        this.inProcedureWalk = inProcedureWalk;
    }

    public void enterIfStatement(AlgolParser.IfStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        AlgolParser.ExprContext cond = ctx.expr();
        boolean hasElse = ctx.statement().size() > 1;
        String endLabel = CodeGenUtils.generateUniqueLabel("endif");
        ifEndLabelStack.push(endLabel);

        String thenLabel = CodeGenUtils.generateUniqueLabel("then");
        String falseTarget;
        if (hasElse) {
            String elseLabel = CodeGenUtils.generateUniqueLabel("else");
            ifElseLabelStack.push(elseLabel);
            falseTarget = elseLabel;
        } else {
            ifElseLabelStack.push(""); // sentinel
            falseTarget = endLabel;
        }

        if (cond instanceof AlgolParser.RelExprContext rel) {
            activeOutput.append(exprGen.generateExpr(rel.expr(0)));
            activeOutput.append(exprGen.generateExpr(rel.expr(1)));
            String op = rel.op.getText();
            String cmpInstr = switch (op) {
                case "<"  -> "if_icmplt";
                case "<=" -> "if_icmple";
                case ">"  -> "if_icmpgt";
                case ">=" -> "if_icmpge";
                case "="  -> "if_icmpeq";
                case "<>" -> "if_icmpne";
                default   -> "if_icmpne";
            };
            activeOutput.append(cmpInstr).append(" ").append(thenLabel).append("\n");
        } else {
            activeOutput.append(exprGen.generateExpr(cond));
            activeOutput.append("ifne ").append(thenLabel).append("\n");
        }
        activeOutput.append("goto ").append(falseTarget).append("\n");
        activeOutput.append(thenLabel).append(":\n");
    }

    public void exitIfStatement(AlgolParser.IfStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String endLabel = ifEndLabelStack.pop();
        ifElseLabelStack.pop();
        activeOutput.append(endLabel).append(":\n");
    }

    public void exitAssignment(AlgolParser.AssignmentContext ctx, StringBuilder activeOutput, String packageName, String className) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        List<AlgolParser.LvalueContext> lvalues = ctx.lvalue();

        String currentProcName = context.getCurrentProcName();
        String currentProcReturnType = context.getCurrentProcReturnType();
        int procRetvalSlot = context.getProcRetvalSlot();
        Map<String, String> currentSymbolTable = context.getSymbolTable();
        Map<String, int[]> currentArrayBounds = context.getArrayBounds();

        if (lvalues.size() == 1 && lvalues.get(0).expr() != null) {
            AlgolParser.LvalueContext lv = lvalues.get(0);
            String arrName = lv.identifier().getText();
            String elemType = currentSymbolTable.get(arrName);
            if (elemType == null) {
                activeOutput.append("; ERROR: undeclared array ").append(arrName).append("\n");
                return;
            }
            int[] bounds = currentArrayBounds.get(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = CodeGenUtils.arrayTypeToJvmDesc(elemType);
            activeOutput.append("getstatic ").append(packageName).append("/")
                        .append(className).append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            activeOutput.append(exprGen.generateExpr(lv.expr()));
            if (lower != 0) {
                activeOutput.append("ldc ").append(lower).append("\n");
                activeOutput.append("isub\n");
            }
            activeOutput.append(exprGen.generateExpr(ctx.expr()));
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "boolean[]".equals(elemType) ? "bastore\n" : "string[]".equals(elemType) ? "aastore\n" : "iastore\n");
            return;
        }

        String exprType = context.getExprTypes().getOrDefault(ctx.expr(), "integer");

        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = context.getSymbolTable().get(lvName);
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            if (lvName.equals(currentProcName)) return "real".equals(currentProcReturnType);
            return "real".equals(vt);
        });
        
        String storeType = anyReal ? "real" : "integer";

        activeOutput.append(exprGen.generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            activeOutput.append("i2d\n");
        }

        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();
            if (name.equals(currentProcName)) {
                if (i < lvalues.size() - 1) activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                activeOutput.append("real".equals(currentProcReturnType) ? "dstore " : "istore ").append(procRetvalSlot).append("\n");
                continue;
            }
            Integer idx = context.getLocalIndex().get(name);
            String varType = context.getSymbolTable().get(name);
            if (idx == null) continue;
            if (i < lvalues.size() - 1) activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            if ("real".equals(varType)) activeOutput.append("dstore ").append(idx).append("\n");
            else activeOutput.append("istore ").append(idx).append("\n");
        }
    }

    public void enterForStatement(AlgolParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String varName = ctx.identifier().getText();
        Integer varIndex = context.getLocalIndex().get(varName);

        currentForLoopLabel = CodeGenUtils.generateUniqueLabel("loop");
        currentForEndLabel = CodeGenUtils.generateUniqueLabel("endfor");

        if (ctx.STEP() != null) {
            activeOutput.append(exprGen.generateExpr(ctx.expr(0)));
            activeOutput.append("istore ").append(varIndex).append("\n");
            activeOutput.append(currentForLoopLabel).append(":\n");
            activeOutput.append("iload ").append(varIndex).append("\n");
            activeOutput.append(exprGen.generateExpr(ctx.expr(2)));
            activeOutput.append("if_icmpgt ").append(currentForEndLabel).append("\n");
        }
    }

    public void exitForStatement(AlgolParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        if (ctx.STEP() != null) {
            String varName = ctx.identifier().getText();
            Integer varIndex = context.getLocalIndex().get(varName);
            activeOutput.append("iload ").append(varIndex).append("\n");
            activeOutput.append(exprGen.generateExpr(ctx.expr(1)));
            activeOutput.append("iadd\nistore ").append(varIndex).append("\n");
            activeOutput.append("goto ").append(currentForLoopLabel).append("\n");
        }
        activeOutput.append(currentForEndLabel).append(":\n");
    }

    public void exitProcedureCall(AlgolParser.ProcedureCallContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String name = ctx.identifier().getText();
        List<AlgolParser.ArgContext> args = ctx.argList() != null ? ctx.argList().arg() : new ArrayList<>();
        
        // Handle built-ins first (Simplified for now)
        if ("outstring".equals(name) || "outreal".equals(name) || "outinteger".equals(name)) {
            activeOutput.append("getstatic java/lang/System/out Ljava/io/PrintStream;\n")
                        .append(exprGen.generateExpr(args.get(args.size()-1).expr()))
                        .append("invokevirtual java/io/PrintStream/print(")
                        .append(name.equals("outstring") ? "Ljava/lang/String;" : name.equals("outreal") ? "D" : "I")
                        .append(")V\n");
        } else {
            activeOutput.append(procGen.generateProcedureCall(name, args, true));
        }
    }
}
