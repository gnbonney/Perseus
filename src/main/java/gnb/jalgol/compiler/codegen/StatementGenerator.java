package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.CodeGenUtils;
import java.util.Stack;

/**
 * Handles statement code generation logic.
 */
public class StatementGenerator implements GeneratorDelegate {
    private ContextManager context;
    private ExpressionGenerator exprGen;
    
    private final Stack<String> ifEndLabelStack = new Stack<>();
    private final Stack<String> ifElseLabelStack = new Stack<>();
    private String currentForLoopLabel;
    private String currentForEndLabel;
    
    private boolean isInProcedureDecl;
    private boolean inProcedureWalk;

    public StatementGenerator(ExpressionGenerator exprGen) {
        this.exprGen = exprGen;
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
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

    public void exitAssignment(AlgolParser.AssignmentContext ctx, StringBuilder activeOutput, String packageName, String className, String currentProcName, String currentProcReturnType, int procRetvalSlot) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        java.util.List<AlgolParser.LvalueContext> lvalues = ctx.lvalue();

        // Array element assignment (single dest with subscript)
        if (lvalues.size() == 1 && lvalues.get(0).expr() != null) {
            AlgolParser.LvalueContext lv = lvalues.get(0);
            String arrName = lv.identifier().getText();
            String elemType = context.getSymbolTable().get(arrName);
            if (elemType == null) {
                activeOutput.append("; ERROR: undeclared array ").append(arrName).append("\n");
                return;
            }
            int[] bounds = context.getArrayBounds().get(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = CodeGenUtils.arrayTypeToJvmDesc(elemType);
            activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                        .append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            activeOutput.append(exprGen.generateExpr(lv.expr())); // subscript
            if (lower != 0) {
                activeOutput.append("ldc ").append(lower).append("\n");
                activeOutput.append("isub\n");
            }
            activeOutput.append(exprGen.generateExpr(ctx.expr())); // value
            activeOutput.append("real[]".equals(elemType) ? "dastore\n" : "boolean[]".equals(elemType) ? "bastore\n" : "string[]".equals(elemType) ? "aastore\n" : "iastore\n");
            return;
        }

        // Scalar (possibly chained) assignment
        String exprType = context.getExprTypes().getOrDefault(ctx.expr(), "integer");

        // Determine storage type: real if any destination is real, string if any destination is string
        boolean anyReal = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = context.getSymbolTable().get(lvName);
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            if (lvName.equals(currentProcName)) return "real".equals(currentProcReturnType);
            return "real".equals(vt);
        });
        boolean anyString = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = context.getSymbolTable().get(lvName);
            if (vt != null && vt.startsWith("thunk:")) vt = vt.substring("thunk:".length());
            if (lvName.equals(currentProcName)) return "string".equals(currentProcReturnType);
            return "string".equals(vt);
        });
        boolean anyProcedure = lvalues.stream().anyMatch(lv -> {
            String lvName = lv.identifier().getText();
            String vt = context.getSymbolTable().get(lvName);
            return vt != null && vt.startsWith("procedure:");
        });
        String storeType = anyProcedure ? "procedure" : anyReal ? "real" : anyString ? "string" : "integer";

        // Generate expression and widen if needed
        activeOutput.append(exprGen.generateExpr(ctx.expr()));
        if ("real".equals(storeType) && "integer".equals(exprType)) {
            activeOutput.append("i2d\n");
        }

        // Store to each destination; dup before all but the last
        for (int i = 0; i < lvalues.size(); i++) {
            String name = lvalues.get(i).identifier().getText();

            // Procedure return value assignment
            if (name.equals(currentProcName)) {
                if (i < lvalues.size() - 1) {
                    activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
                }
                if ("real".equals(currentProcReturnType)) {
                    activeOutput.append("dstore ").append(procRetvalSlot).append("\n");
                } else if ("string".equals(currentProcReturnType)) {
                    activeOutput.append("astore ").append(procRetvalSlot).append("\n");
                } else {
                    activeOutput.append("istore ").append(procRetvalSlot).append("\n");
                }
                continue;
            }

            Integer idx = context.getLocalIndex().get(name);
            String varType = context.getSymbolTable().get(name);
            boolean isThunk = varType != null && varType.startsWith("thunk:");
            if (idx == null && !isThunk) {
                activeOutput.append("; ERROR: undeclared variable ").append(name).append("\n");
                continue;
            }

            if (i < lvalues.size() - 1) {
                activeOutput.append("real".equals(storeType) ? "dup2\n" : "dup\n");
            }

            if (isThunk) {
                // assignment to a name parameter: call thunk.set(boxedValue)
                // stack has the primitive/reference value; box it, then swap the thunk ref in
                if ("real".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else if (!"string".equals(storeType)) {
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                // push thunk ref, swap so order is: thunk, boxed_value
                activeOutput.append("aload ").append(idx).append("\n");
                activeOutput.append("swap\n");
                activeOutput.append("invokeinterface ")
                            .append("gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
                continue;
            }

            // normal local variable storage
            if ("integer".equals(varType) || "boolean".equals(varType)) {
                activeOutput.append("istore ").append(idx).append("\n");
            } else if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(idx).append("\n");
            } else if ("string".equals(varType)) {
                activeOutput.append("astore ").append(idx).append("\n");
            } else if (varType != null && varType.startsWith("procedure:")) {
                activeOutput.append("astore ").append(idx).append("\n");
            }
        }
    }

    public void enterForStatement(AlgolParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        String varName  = ctx.identifier().getText();
        Integer varIndex = context.getLocalIndex().get(varName);
        String varType  = context.getSymbolTable().get(varName);
        boolean varIsThunk = varType != null && varType.startsWith("thunk:");
        String baseVarType = varIsThunk ? varType.substring("thunk:".length()) : varType;

        currentForLoopLabel = CodeGenUtils.generateUniqueLabel("loop");
        currentForEndLabel  = CodeGenUtils.generateUniqueLabel("endfor");

        if (ctx.STEP() != null) {
            activeOutput.append(exprGen.generateExpr(ctx.expr(0))); // start
            if (varIsThunk) {
                if ("real".equals(baseVarType)) {
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else {
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                activeOutput.append("aload ").append(varIndex).append("\n");
                activeOutput.append("swap\n");
                activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
            } else if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(currentForLoopLabel).append(":\n");
            if (varIsThunk) {
                activeOutput.append("aload ").append(varIndex).append("\n");
                activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                if ("real".equals(baseVarType)) {
                    activeOutput.append("checkcast java/lang/Double\n");
                    activeOutput.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    activeOutput.append(exprGen.generateExpr(ctx.expr(2))); // until
                    activeOutput.append("dcmpg\n");
                    activeOutput.append("ifgt ").append(currentForEndLabel).append("\n");
                } else {
                    activeOutput.append("checkcast java/lang/Integer\n");
                    activeOutput.append("invokevirtual java/lang/Integer/intValue()I\n");
                    activeOutput.append(exprGen.generateExpr(ctx.expr(2))); // until
                    activeOutput.append("if_icmpgt ").append(currentForEndLabel).append("\n");
                }
            } else if ("real".equals(varType)) {
                activeOutput.append("dload ").append(varIndex).append("\n");
                activeOutput.append(exprGen.generateExpr(ctx.expr(2))); // until
                activeOutput.append("dcmpg\n");
                activeOutput.append("ifgt ").append(currentForEndLabel).append("\n");
            } else {
                activeOutput.append("iload ").append(varIndex).append("\n");
                activeOutput.append(exprGen.generateExpr(ctx.expr(2))); // until
                activeOutput.append("if_icmpgt ").append(currentForEndLabel).append("\n");
            }
        } else if (ctx.WHILE() != null) {
            activeOutput.append(currentForLoopLabel).append(":\n");
            activeOutput.append(exprGen.generateExpr(ctx.expr(0))); 
            if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(exprGen.generateExpr(ctx.expr(1))); // while condition
            activeOutput.append("ifeq ").append(currentForEndLabel).append("\n");
        } else {
            activeOutput.append(exprGen.generateExpr(ctx.expr(0)));
            if ("real".equals(varType)) {
                activeOutput.append("dstore ").append(varIndex).append("\n");
            } else {
                activeOutput.append("istore ").append(varIndex).append("\n");
            }
            activeOutput.append(currentForLoopLabel).append(":\n");
        }
    }

    public void exitForStatement(AlgolParser.ForStatementContext ctx, StringBuilder activeOutput) {
        if (isInProcedureDecl && !inProcedureWalk) return;
        if (ctx.STEP() != null) {
            String varName  = ctx.identifier().getText();
            int    varIndex = context.getLocalIndex().get(varName);
            String varType  = context.getSymbolTable().get(varName);
            boolean varIsThunk = varType != null && varType.startsWith("thunk:");
            String baseVarType = varIsThunk ? varType.substring("thunk:".length()) : varType;

            if (varIsThunk) {
                activeOutput.append("aload ").append(varIndex).append("\n");
                activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                if ("real".equals(baseVarType)) {
                    activeOutput.append("checkcast java/lang/Double\n");
                    activeOutput.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    activeOutput.append(exprGen.generateExpr(ctx.expr(1))); // step
                    activeOutput.append("dadd\n");
                    activeOutput.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
                } else {
                    activeOutput.append("checkcast java/lang/Integer\n");
                    activeOutput.append("invokevirtual java/lang/Integer/intValue()I\n");
                    activeOutput.append(exprGen.generateExpr(ctx.expr(1))); // step
                    activeOutput.append("iadd\n");
                    activeOutput.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
                }
                activeOutput.append("aload ").append(varIndex).append("\n");
                activeOutput.append("swap\n");
                activeOutput.append("invokeinterface gnb/jalgol/compiler/Thunk/set(Ljava/lang/Object;)V 2\n");
            } else {
                activeOutput.append(exprGen.generateExpr(ctx.expr(1))); // step
                if ("real".equals(varType)) {
                    activeOutput.append("dload ").append(varIndex).append("\n");
                    activeOutput.append("dadd\n");
                    activeOutput.append("dstore ").append(varIndex).append("\n");
                } else {
                    activeOutput.append("iload ").append(varIndex).append("\n");
                    activeOutput.append("iadd\n");
                    activeOutput.append("istore ").append(varIndex).append("\n");
                }
            }
        }
        activeOutput.append("goto ").append(currentForLoopLabel).append("\n");
        activeOutput.append(currentForEndLabel).append(":\n");
    }
}
