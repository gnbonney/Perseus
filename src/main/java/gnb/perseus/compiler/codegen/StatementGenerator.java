package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.antlr.PerseusParser;
import gnb.perseus.compiler.CodeGenUtils;
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

    public StatementGenerator(ExpressionGenerator exprGen) {
        this.exprGen = exprGen;
    }

    public void setProcedureGenerator(ProcedureGenerator procGen) {
        this.procGen = procGen;
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
        if (this.exprGen != null) this.exprGen.setContext(context);
    }

    public void setProcedureContext(boolean isInProcedureDecl, boolean inProcedureWalk) {
        this.isInProcedureDecl = isInProcedureDecl;
        this.inProcedureWalk = inProcedureWalk;
    }

    public void enterIfStatement(PerseusParser.IfStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        PerseusParser.ExprContext cond = ctx.expr();
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

        if (cond instanceof PerseusParser.RelExprContext rel) {
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

    public void exitIfStatement(PerseusParser.IfStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String endLabel = ifEndLabelStack.pop();
        ifElseLabelStack.pop();
        activeOutput.append(endLabel).append(":\n");
    }

    public String generateAssignment(PerseusParser.AssignmentContext ctx, String packageName, String className) {
        if (isInProcedureDecl && !inProcedureWalk) return "";
        List<PerseusParser.LvalueContext> lvalues = ctx.lvalue();
        StringBuilder activeOutput = new StringBuilder();

        String currentProcName = context.getCurrentProcName();
        String currentProcReturnType = context.getCurrentProcReturnType();
        int procRetvalSlot = context.getProcRetvalSlot();
        Map<String, String> currentSymbolTable = context.getSymbolTable();
        Map<String, int[]> currentArrayBounds = context.getArrayBounds();

        if (lvalues.size() == 1 && !lvalues.get(0).expr().isEmpty()) {
            PerseusParser.LvalueContext lv = lvalues.get(0);
            String arrName = lv.identifier().getText();
            String elemType = currentSymbolTable.get(arrName);
            if (elemType == null && context.getMainSymbolTable() != null) elemType = context.getMainSymbolTable().get(arrName);

            if (elemType == null) {
                activeOutput.append("; ERROR: undeclared array ").append(arrName).append("\n");
                return activeOutput.toString();
            }
            int[] bounds = currentArrayBounds.get(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = CodeGenUtils.arrayTypeToJvmDesc(elemType);
            activeOutput.append("getstatic ").append(packageName).append("/")
                        .append(className).append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            activeOutput.append(exprGen.generateExpr(lv.expr(0)));
            if (lower != 0) {
                activeOutput.append("ldc ").append(lower).append("\n");
                activeOutput.append("isub\n");
            }
            activeOutput.append(exprGen.generateExpr(ctx.expr()));
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "boolean[]".equals(elemType) ? "bastore\n" : "string[]".equals(elemType) ? "aastore\n" : "iastore\n");
            return activeOutput.toString();
        }

        String exprType = context.getExprTypes().getOrDefault(ctx.expr(), "integer");

        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = context.getSymbolTable().get(lvName);
            if (vt == null && context.getMainSymbolTable() != null) vt = context.getMainSymbolTable().get(lvName);
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
            if (idx == null && context.getMainLocalIndex() != null) idx = context.getMainLocalIndex().get(name);
            if (idx == null && context.getProcVarSlots() != null) idx = context.getProcVarSlots().get(name);
            String varType = context.getSymbolTable().get(name);
            if (varType == null && context.getMainSymbolTable() != null) varType = context.getMainSymbolTable().get(name);

            if (idx == null) {
                activeOutput.append("; ERROR: unknown variable ").append(name).append("\n");
                continue;
            }
            if (i < lvalues.size() - 1) activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            if ("real".equals(varType)) activeOutput.append("dstore ").append(idx).append("\n");
            else if (varType != null && varType.startsWith("procedure:")) activeOutput.append("astore ").append(idx).append("\n");
            else activeOutput.append("istore ").append(idx).append("\n");
        }
        return activeOutput.toString();
    }

    public void enterForStatement(PerseusParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String varName = ctx.identifier().getText();
        Integer varIndex = context.getLocalIndex().get(varName);
        if (varIndex == null) return;

        currentForLoopLabel = CodeGenUtils.generateUniqueLabel("loop");
        currentForEndLabel = CodeGenUtils.generateUniqueLabel("endfor");

        // Only handle the first for-element for simple step/until support in this delegate
        if (!ctx.forList().forElement().isEmpty()) {
            PerseusParser.ForElementContext elem = ctx.forList().forElement().get(0);
            if (elem instanceof PerseusParser.StepUntilElementContext e) {
                activeOutput.append(exprGen.generateExpr(e.expr(0)));
                activeOutput.append("istore ").append(varIndex).append("\n");
                activeOutput.append(currentForLoopLabel).append(":\n");
                activeOutput.append("iload ").append(varIndex).append("\n");
                activeOutput.append(exprGen.generateExpr(e.expr(2)));
                activeOutput.append("if_icmpgt ").append(currentForEndLabel).append("\n");
            }
        }
    }

    public void exitForStatement(PerseusParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        if (!ctx.forList().forElement().isEmpty()) {
            PerseusParser.ForElementContext elem = ctx.forList().forElement().get(0);
            if (elem instanceof PerseusParser.StepUntilElementContext e) {
                String varName = ctx.identifier().getText();
                Integer varIndex = context.getLocalIndex().get(varName);
                if (varIndex == null) return;
                activeOutput.append("iload ").append(varIndex).append("\n");
                activeOutput.append(exprGen.generateExpr(e.expr(1)));
                activeOutput.append("iadd\nistore ").append(varIndex).append("\n");
                activeOutput.append("goto ").append(currentForLoopLabel).append("\n");
            }
        }
        activeOutput.append(currentForEndLabel).append(":\n");
    }

    public String exitProcedureCall(PerseusParser.ProcedureCallContext ctx) {
        if (isInProcedureDecl && !inProcedureWalk) return "";
        String name = ctx.identifier().getText();
        List<PerseusParser.ArgContext> args = ctx.argList() != null ? ctx.argList().arg() : new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        
        // Handle built-ins first (Simplified for now)
        if ("outstring".equals(name) || "outreal".equals(name) || "outinteger".equals(name)) {
            sb.append(exprGen.generateExpr(args.get(0).expr()))
                        .append(exprGen.generateExpr(args.get(args.size()-1).expr()))
                        .append("invokestatic perseus/io/TextOutput/")
                        .append(name)
                        .append("(")
                        .append(name.equals("outstring") ? "ILjava/lang/String;" : name.equals("outreal") ? "ID" : "II")
                        .append(")V\n");
        } else {
            String varType = context.getSymbolTable().get(name);
            if (varType != null && varType.startsWith("procedure:")) {
                sb.append(procGen.generateProcedureVariableCall(name, varType, args));
            } else {
                sb.append(procGen.generateProcedureCall(name, args, true));
            }
        }
        return sb.toString();
    }
}

