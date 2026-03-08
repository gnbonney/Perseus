package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ExprContext;
import gnb.jalgol.compiler.SymbolTableBuilder;
import gnb.jalgol.compiler.CodeGenUtils;
import java.util.Map;
import java.util.List;

/**
 * Handles expression code generation logic.
 */
public class ExpressionGenerator implements GeneratorDelegate {
    private ContextManager context;
    private final Map<ExprContext, String> exprTypes;
    private final Map<String, SymbolTableBuilder.ProcInfo> procedures;
    private final Map<String, int[]> mainArrayBounds;
    private final Map<String, int[]> currentArrayBounds;
    private String currentProcName;
    private String currentProcReturnType;
    private int procRetvalSlot;
    private int thunkCounter;

    public ExpressionGenerator(
            Map<ExprContext, String> exprTypes, 
            Map<String, SymbolTableBuilder.ProcInfo> procedures,
            Map<String, int[]> currentArrayBounds,
            Map<String, int[]> mainArrayBounds) {
        this.exprTypes = exprTypes;
        this.procedures = procedures;
        this.currentArrayBounds = currentArrayBounds;
        this.mainArrayBounds = mainArrayBounds;
    }

    @Override
    public void setContext(ContextManager context) {
        this.context = context;
    }

    public void setProcContext(String name, String returnType, int retvalSlot) {
        this.currentProcName = name;
        this.currentProcReturnType = returnType;
        this.procRetvalSlot = retvalSlot;
    }

    public void setThunkCounter(int counter) {
        this.thunkCounter = counter;
    }

    public String generateExpr(ExprContext ctx) {
        return generateExpr(ctx, null);
    }

    public String generateExpr(ExprContext ctx, Map<String, Integer> varToFieldIndex) {
        if (ctx instanceof AlgolParser.RelExprContext e) {
            String left = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            String op = e.op.getText();
            String trueLabel = CodeGenUtils.generateUniqueLabel("rel_true");
            String endLabel = CodeGenUtils.generateUniqueLabel("rel_end");
            String cmpOp = switch (op) {
                case "<" -> "lt";
                case "<=" -> "le";
                case ">" -> "gt";
                case ">=" -> "ge";
                case "=" -> "eq";
                case "<>" -> "ne";
                default -> throw new RuntimeException("Unknown rel op " + op);
            };
            return left + right + "if_icmp" + cmpOp + " " + trueLabel + "\n" +
                "iconst_0\n" +
                "goto " + endLabel + "\n" +
                trueLabel + ":\n" +
                "iconst_1\n" +
                endLabel + ":\n";
        } else if (ctx instanceof AlgolParser.MulDivExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            String leftType  = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            if ("real".equals(type) && "integer".equals(leftType))  left  += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("*".equals(op) ? "dmul" : "ddiv") :
                ("*".equals(op) ? "imul" : "idiv");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AddSubExprContext e) {
            String left  = generateExpr(e.expr(0), varToFieldIndex);
            String right = generateExpr(e.expr(1), varToFieldIndex);
            String leftType  = exprTypes.get(e.expr(0));
            String rightType = exprTypes.get(e.expr(1));
            String type = exprTypes.get(ctx);
            if ("real".equals(type) && "integer".equals(leftType))  left  += "i2d\n";
            if ("real".equals(type) && "integer".equals(rightType)) right += "i2d\n";
            String op = e.op.getText();
            String instr = "real".equals(type) ?
                ("+".equals(op) ? "dadd" : "dsub") :
                ("+".equals(op) ? "iadd" : "isub");
            return left + right + instr + "\n";
        } else if (ctx instanceof AlgolParser.AndExprContext e) {
            return generateExpr(e.expr(0), varToFieldIndex) + generateExpr(e.expr(1), varToFieldIndex) + "iand\n";
        } else if (ctx instanceof AlgolParser.VarExprContext e) {
            String name = e.identifier().getText();
            Map<String, String> currentSymbolTable = context.getSymbolTable();
            String packageName = context.getPackageName();
            String className = context.getClassName();

            if (varToFieldIndex != null && varToFieldIndex.containsKey(name)) {
                int fieldIdx = varToFieldIndex.get(name);
                StringBuilder sb = new StringBuilder();
                sb.append("aload_0\n");
                sb.append("getfield ").append(packageName).append("/")
                  .append(className).append("").append((thunkCounter-1))
                  .append("/box").append(fieldIdx).append(" [Ljava/lang/Object;\n");
                sb.append("iconst_0\n");
                sb.append("aaload\n");
                
                String type = currentSymbolTable.get(name);
                String baseType = "integer";
                if (type != null && type.startsWith("thunk:")) {
                    baseType = type.substring("thunk:".length());
                } else if (type != null) {
                    baseType = type;
                }
                switch (baseType) {
                    case "real":
                        sb.append("checkcast java/lang/Double\n");
                        sb.append("invokevirtual java/lang/Double/doubleValue()D\n");
                        break;
                    case "string":
                        sb.append("checkcast java/lang/String\n");
                        break;
                    default: 
                        sb.append("checkcast java/lang/Integer\n");
                        sb.append("invokevirtual java/lang/Integer/intValue()I\n");
                        break;
                }
                return sb.toString();
            }

            if ("maxreal".equals(name)) return "ldc2_w " + Double.MAX_VALUE + "\n";
            if ("minreal".equals(name)) return "ldc2_w " + Double.MIN_VALUE + "\n";
            if ("maxint".equals(name)) return "ldc " + Integer.MAX_VALUE + "\n";
            if ("epsilon".equals(name)) return "ldc2_w " + Double.MIN_NORMAL + "\n";

            if (name.equals(currentProcName)) {
                if ("real".equals(currentProcReturnType)) return "dload " + procRetvalSlot + "\n";
                else if ("string".equals(currentProcReturnType)) return "aload " + procRetvalSlot + "\n";
                else return "iload " + procRetvalSlot + "\n";
            }

            Integer idx = context.getLocalIndex().get(name);
            if (idx == null) return "; ERROR: undeclared variable " + name + "\n";
            String type = currentSymbolTable.get(name);
            if (type != null && type.startsWith("thunk:")) {
                String baseType = type.substring("thunk:".length());
                StringBuilder sb2 = new StringBuilder();
                sb2.append("aload ").append(idx).append("\n");
                sb2.append("invokeinterface gnb/jalgol/compiler/Thunk/get()Ljava/lang/Object; 1\n");
                switch (baseType) {
                    case "real" -> {
                        sb2.append("checkcast java/lang/Double\n");
                        sb2.append("invokevirtual java/lang/Double/doubleValue()D\n");
                    }
                    case "string" -> sb2.append("checkcast java/lang/String\n");
                    default -> {
                        sb2.append("checkcast java/lang/Integer\n");
                        sb2.append("invokevirtual java/lang/Integer/intValue()I\n");
                    }
                }
                return sb2.toString();
            }
            if ("integer".equals(type) || "boolean".equals(type)) return "iload " + idx + "\n";
            else if ("real".equals(type)) return "dload " + idx + "\n";
            else if ("string".equals(type)) return "aload " + idx + "\n";
            else return "; ERROR: unknown variable type " + type + "\n";
        } else if (ctx instanceof AlgolParser.ArrayAccessExprContext e) {
            String arrName = e.identifier().getText();
            String elemType = lookupVarType(arrName, context.getSymbolTable(), null);
            if (elemType == null) return "; ERROR: undeclared array " + arrName + "\n";
            int[] bounds = lookupArrayBounds(arrName);
            int lower = bounds != null ? bounds[0] : 0;
            String jvmDesc = CodeGenUtils.arrayTypeToJvmDesc(elemType);
            StringBuilder sb = new StringBuilder();
            sb.append("getstatic ").append(context.getPackageName()).append("/").append(context.getClassName())
              .append("/").append(arrName).append(" ").append(jvmDesc).append("\n");
            sb.append(generateExpr(e.expr(), varToFieldIndex));
            if (lower != 0) {
                sb.append("ldc ").append(lower).append("\n");
                sb.append("isub\n");
            }
            sb.append("real[]".equals(elemType) ? "daload\n" : "boolean[]".equals(elemType) ? "baload\n" : "string[]".equals(elemType) ? "aaload\n" : "iaload\n");
            return sb.toString();
        } else if (ctx instanceof AlgolParser.ProcCallExprContext e) {
            String procName = e.identifier().getText();
            String builtinCode = generateBuiltinMathFunction(procName, e);
            if (builtinCode != null) return builtinCode;
            return "; Procedure call logic to be migrated\n";
        } else if (ctx instanceof AlgolParser.RealLiteralExprContext e) {
            return "ldc2_w " + e.realLiteral().getText() + "\n";
        } else if (ctx instanceof AlgolParser.IntLiteralExprContext e) {
            return "ldc " + e.unsignedInt().getText() + "\n";
        } else if (ctx instanceof AlgolParser.StringLiteralExprContext e) {
            return "ldc " + e.string().getText() + "\n";
        } else if (ctx instanceof AlgolParser.TrueLiteralExprContext) {
            return "iconst_1\n";
        } else if (ctx instanceof AlgolParser.FalseLiteralExprContext) {
            return "iconst_0\n";
        } else if (ctx instanceof AlgolParser.UnaryMinusExprContext e) {
            String type = exprTypes.getOrDefault(ctx, "integer");
            String inner = generateExpr(e.expr(), varToFieldIndex);
            return inner + ("real".equals(type) ? "dneg\n" : "ineg\n");
        } else if (ctx instanceof AlgolParser.ParenExprContext e) {
            return generateExpr(e.expr(), varToFieldIndex);
        }
        return "; unknown expr type\n";
    }

    private String generateBuiltinMathFunction(String funcName, AlgolParser.ProcCallExprContext ctx) {
        if (ctx.argList() == null || ctx.argList().arg().isEmpty()) return "; ERROR: " + funcName + " requires an argument\n";
        AlgolParser.ExprContext argExpr = ctx.argList().arg().get(0).expr();
        if (argExpr == null) return "; ERROR: " + funcName + " requires an expression argument\n";
        
        StringBuilder sb = new StringBuilder();
        String argType = exprTypes.getOrDefault(argExpr, "integer");
        
        switch (funcName) {
            case "sqrt", "abs", "sin", "cos", "arctan", "ln", "exp" -> {
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                String method = funcName.equals("arctan") ? "atan" : funcName.equals("ln") ? "log" : funcName;
                sb.append("invokestatic java/lang/Math/").append(method).append("(D)D\n");
                return sb.toString();
            }
            case "iabs" -> {
                sb.append(generateExpr(argExpr));
                if ("real".equals(argType)) sb.append("d2i\n");
                sb.append("invokestatic java/lang/Math/abs(I)I\n");
                return sb.toString();
            }
            case "sign" -> {
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/signum(D)D\n");
                sb.append("d2i\n");
                return sb.toString();
            }
            case "entier" -> {
                sb.append(generateExpr(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic java/lang/Math/floor(D)D\n");
                sb.append("d2i\n");
                return sb.toString();
            }
            default -> { return null; }
        }
    }

    private int[] lookupArrayBounds(String name) {
        int[] bounds = currentArrayBounds.get(name);
        if (bounds == null && mainArrayBounds != null) bounds = mainArrayBounds.get(name);
        return bounds;
    }

    public static String lookupVarType(String name, Map<String, String> currentSymbolTable, Map<String, String> mainSymbolTable) {
        String type = currentSymbolTable.get(name);
        if (type == null && mainSymbolTable != null) type = mainSymbolTable.get(name);
        return type;
    }
}
