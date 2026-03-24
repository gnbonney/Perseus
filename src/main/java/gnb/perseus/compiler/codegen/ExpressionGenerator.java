package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.antlr.AlgolParser;
import gnb.perseus.compiler.antlr.AlgolParser.ExprContext;
import gnb.perseus.compiler.SymbolTableBuilder;
import gnb.perseus.compiler.CodeGenUtils;
import java.util.Map;
import java.util.List;

/**
 * Handles expression code generation logic.
 */
public class ExpressionGenerator implements GeneratorDelegate {
    private ContextManager context;
    private ProcedureGenerator procGen;

    public ExpressionGenerator() {
    }

    public void setProcedureGenerator(ProcedureGenerator procGen) {
        this.procGen = procGen;
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
    }

    public String generateExpr(AlgolParser.ExprContext ctx) {
        if (ctx == null) return "";
        if (ctx instanceof AlgolParser.IntLiteralExprContext) {
            return "ldc " + ctx.getText() + "\n";
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext) {
            return "ldc2_w " + ctx.getText() + "d\n";
        } else if (ctx instanceof AlgolParser.VarExprContext varCtx) {
            String name = varCtx.identifier().getText();
            System.out.println("DEBUG: ExpressionGenerator generating for VarExpr: " + name);
            // Check if it's a procedure name being used as a value or a variable call
            String type = context.getSymbolTable().get(name);
            if (type == null && context.getMainSymbolTable() != null) type = context.getMainSymbolTable().get(name);
            // If still no type, check procedures map (procedure name used as a value)
            if (type == null && context.getProcedures().containsKey(name)) {
                SymbolTableBuilder.ProcInfo pInfo = context.getProcedures().get(name);
                if (pInfo != null && procGen != null) {
                    return procGen.generateProcedureReference(name, pInfo);
                }
            }
            System.out.println("DEBUG:   Type for " + name + " is " + type);

            if (type != null && type.startsWith("procedure:")) {
                Integer idx = context.getLocalIndex().get(name);
                System.out.println("DEBUG:   Local index for " + name + " is " + idx);
                if (idx != null) {
                    // It's a procedure variable.
                    // If this VarExpr is NOT the identifier of a ProcedureCallContext, it's a reference.
                    boolean isCall = false;
                    org.antlr.v4.runtime.tree.ParseTree p = varCtx.getParent();
                    while (p != null) {
                        if (p instanceof AlgolParser.ProcedureCallContext call) {
                            if (call.identifier().getText().equals(name)) {
                                isCall = true;
                                break;
                            }
                        }
                        p = p.getParent();
                    }
                    if (!isCall) {
                        System.out.println("DEBUG:   Generating aload " + idx + " for procedure variable " + name);
                        // Return the reference object stored in the variable
                        return "aload " + idx + "\n";
                    } else {
                        System.out.println("DEBUG:   VarExpr is part of a call to " + name);
                        // It's a call through a variable. But wait, ProcedureCall handles this.
                        // Actually, if it's an Expression (function call), it might be here.
                        // For 13A, we'll let ProcedureCall/exitProcedureCall handle it for statements.
                        // For expressions...
                    }
                } else {
                    System.out.println("DEBUG:   Generating static reference for procedure " + name);
                    // Not a local variable, must be a direct reference to a static procedure
                    SymbolTableBuilder.ProcInfo info = context.getProcedures().get(name);
                    if (info != null && procGen != null) {
                        return procGen.generateProcedureReference(name, info);
                    }
                }
            }
            return generateLoadVar(name);
        } else if (ctx instanceof AlgolParser.AddSubExprContext binCtx) {
            String op = binCtx.op.getText();
            return generateExpr(binCtx.expr(0)) + generateExpr(binCtx.expr(1)) + ("+".equals(op) ? "iadd\n" : "isub\n");
        } else if (ctx instanceof AlgolParser.MulDivExprContext mulCtx) {
            String op = mulCtx.op.getText();
            return generateExpr(mulCtx.expr(0)) + generateExpr(mulCtx.expr(1)) + ("*".equals(op) ? "imul\n" : "idiv\n");
        } else if (ctx instanceof AlgolParser.ParenExprContext parCtx) {
            return generateExpr(parCtx.expr());
        } else if (ctx instanceof AlgolParser.TrueLiteralExprContext) {
            return "iconst_1\n";
        } else if (ctx instanceof AlgolParser.FalseLiteralExprContext) {
            return "iconst_0\n";
        } else if (ctx instanceof AlgolParser.StringLiteralExprContext strCtx) {
            return "ldc " + strCtx.getText() + "\n";
        }
        return "; expr logic missing\n";
    }

    public String generateLoadVar(String name) {
        Integer idx = context.getLocalIndex().get(name);
        String type = context.getSymbolTable().get(name);
        if (idx == null && context.getMainLocalIndex() != null) {
            idx = context.getMainLocalIndex().get(name);
        }
        if (type == null && context.getMainSymbolTable() != null) {
            type = context.getMainSymbolTable().get(name);
        }
        // If this is a thunk (call-by-name) parameter, load the thunk and eval it
        if (type != null && type.startsWith("thunk:")) {
            String baseType = type.substring("thunk:".length());
            String cast = baseType.equals("real") ? "java/lang/Double" : (baseType.equals("string") ? "java/lang/String" : "java/lang/Integer");
            String valMethod = baseType.equals("real") ? "doubleValue()D" : (baseType.equals("string") ? "" : "intValue()I");
            StringBuilder sb = new StringBuilder();
            sb.append("aload ").append(idx).append("\n")
              .append("invokeinterface gnb/perseus/runtime/Thunk/eval()Ljava/lang/Object; 1\n")
              .append("checkcast ").append(cast).append("\n");
            if (!valMethod.isEmpty()) sb.append("invokevirtual ").append(cast).append("/").append(valMethod).append("\n");
            return sb.toString();
        }
        if (idx == null) {
            // Check if this is a static scalar or array in main
            if (type != null && !type.endsWith("[]") && !type.startsWith("procedure:") && !type.startsWith("thunk:")) {
                String jvmDesc = CodeGenUtils.scalarTypeToJvmDesc(type);
                return "getstatic " + context.getPackageName() + "/" + context.getClassName() + "/" + name + " " + jvmDesc + "\n";
            }
            if (context.getMainSymbolTable() != null) {
                String mainType = context.getMainSymbolTable().get(name);
                if (mainType != null && !mainType.endsWith("[]") && !mainType.startsWith("procedure:") && !mainType.startsWith("thunk:")) {
                    String jvmDesc = CodeGenUtils.scalarTypeToJvmDesc(mainType);
                    return "getstatic " + context.getPackageName() + "/" + context.getClassName() + "/" + name + " " + jvmDesc + "\n";
                }
            }
            return "; ERROR: unknown variable " + name + "\n";
        }
        // If idx came from outer scope (mainLocalIndex) and current local map doesn't contain it,
        // access via static field instead of local slot.
        if (!context.getLocalIndex().containsKey(name)) {
            if (type != null && type.startsWith("procedure:")) {
                String desc = switch (type.substring("procedure:".length())) {
                    case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
                    case "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
                    case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
                    default -> "Lgnb/perseus/compiler/VoidProcedure;";
                };
                return "getstatic " + context.getPackageName() + "/" + context.getClassName() + "/" + name + " " + desc + "\n";
            }
            if (type != null && !type.endsWith("[]") && !type.startsWith("thunk:")) {
                String jvmDesc = CodeGenUtils.scalarTypeToJvmDesc(type);
                return "getstatic " + context.getPackageName() + "/" + context.getClassName() + "/" + name + " " + jvmDesc + "\n";
            }
            if (type != null && type.endsWith("[]")) {
                String jvmDesc = CodeGenUtils.arrayTypeToJvmDesc(type);
                return "getstatic " + context.getPackageName() + "/" + context.getClassName() + "/" + name + " " + jvmDesc + "\n";
            }
        }
        if (type != null && type.startsWith("thunk:")) {
            type = type.substring("thunk:".length());
        }
        if ("integer".equals(type) || "boolean".equals(type)) {
            return "iload " + idx + "\n";
        } else if ("real".equals(type)) {
            return "dload " + idx + "\n";
        } else if ("string".equals(type)) {
            return "aload " + idx + "\n";
        } else {
            return "; ERROR: unknown var type " + type + "\n";
        }
    }

    public String generateStoreVar(String name) {
        Integer idx = context.getLocalIndex().get(name);
        String type = context.getSymbolTable().get(name);
        if (idx == null) return "; ERROR: unknown variable " + name + "\n";
        if ("real".equals(type)) return "dstore " + idx + "\n";
        return "istore " + idx + "\n";
    }
}
