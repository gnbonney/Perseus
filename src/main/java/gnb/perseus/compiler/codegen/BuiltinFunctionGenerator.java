package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates Jasmin instructions for built-in Algol functions.
 *
 * <p>Handles two distinct categories:
 * <ul>
 *   <li><b>Math builtins</b>: sqrt, abs, iabs, sign, entier, sin, cos, arctan, ln, exp —
 *       delegated to {@code java/lang/Math} static methods.</li>
 *   <li><b>String builtins</b>: length, concat, substring —
 *       delegated to {@code java/lang/String} instance methods.</li>
 * </ul>
 *
 * <p>Called from {@code CodeGenerator} via {@link #generate} before the user-defined
 * procedure lookup.  Returns {@code null} when the function name is not recognized,
 * which signals the caller to continue with its normal lookup chain.
 */
public class BuiltinFunctionGenerator {

    private final Map<PerseusParser.ExprContext, String> exprTypes;
    private Function<PerseusParser.ExprContext, String> exprCodeGen;

    public BuiltinFunctionGenerator(Map<PerseusParser.ExprContext, String> exprTypes) {
        this.exprTypes = exprTypes;
    }

    /**
     * Sets the callback used to generate Jasmin code for sub-expressions.
     * Must be called before any {@link #generate} invocation.
     */
    public void setExprCodeGen(Function<PerseusParser.ExprContext, String> fn) {
        this.exprCodeGen = fn;
    }

    /**
     * Returns Jasmin code for a recognized built-in function call, or {@code null}
     * if the function name is not a built-in.
     */
    public String generate(String funcName, PerseusParser.ProcCallExprContext ctx) {
        if (isMathBuiltin(funcName))   return generateMathBuiltin(funcName, ctx);
        if (isStringBuiltin(funcName)) return generateStringBuiltin(funcName, ctx);
        return null;
    }

    public static boolean isMathBuiltin(String name) {
        return switch (name) {
            case "sqrt", "abs", "iabs", "sign", "entier",
                 "sin", "cos", "arctan", "ln", "exp" -> true;
            default -> false;
        };
    }

    public static boolean isStringBuiltin(String name) {
        return switch (name) {
            case "length", "concat", "substring" -> true;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Math builtins — all delegate to java/lang/Math statics
    // -------------------------------------------------------------------------

    private String generateMathBuiltin(String funcName, PerseusParser.ProcCallExprContext ctx) {
        if (ctx.argList() == null || ctx.argList().arg().isEmpty())
            return "; ERROR: " + funcName + " requires an argument\n";
        PerseusParser.ExprContext argExpr = ctx.argList().arg().get(0).expr();
        if (argExpr == null)
            return "; ERROR: " + funcName + " requires an expression argument\n";

        StringBuilder sb = new StringBuilder();
        String argType = exprTypes.getOrDefault(argExpr, "integer");

        switch (funcName) {
            case "sqrt":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/sqrt(D)D\n");
                return sb.toString();

            case "abs":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/abs(D)D\n");
                return sb.toString();

            case "iabs":
                sb.append(exprCodeGen.apply(argExpr));
                if ("real".equals(argType)) sb.append("d2i\n");
                sb.append("invokestatic perseus/lang/MathEnv/iabs(I)I\n");
                return sb.toString();

            case "sign":
                // sign(E) = E > 0 ? 1 : E < 0 ? -1 : 0
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/sign(D)I\n");
                return sb.toString();

            case "entier":
                // entier(E) returns the floor of E as an integer value
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/entier(D)I\n");
                return sb.toString();

            case "sin":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/sin(D)D\n");
                return sb.toString();

            case "cos":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/cos(D)D\n");
                return sb.toString();

            case "arctan":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/arctan(D)D\n");
                return sb.toString();

            case "ln":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/ln(D)D\n");
                return sb.toString();

            case "exp":
                sb.append(exprCodeGen.apply(argExpr));
                if ("integer".equals(argType)) sb.append("i2d\n");
                sb.append("invokestatic perseus/lang/MathEnv/exp(D)D\n");
                return sb.toString();

            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // String builtins — all delegate to java/lang/String instance methods
    // -------------------------------------------------------------------------

    private String generateStringBuiltin(String funcName, PerseusParser.ProcCallExprContext ctx) {
        if (ctx.argList() == null || ctx.argList().arg().isEmpty())
            return "; ERROR: " + funcName + " requires an argument\n";
        PerseusParser.ExprContext argExpr = ctx.argList().arg().get(0).expr();
        if (argExpr == null)
            return "; ERROR: " + funcName + " requires an expression argument\n";

        StringBuilder sb = new StringBuilder();

        switch (funcName) {
            case "length":
                // length(s) → s.length()
                sb.append(exprCodeGen.apply(argExpr));
                sb.append("invokevirtual java/lang/String/length()I\n");
                return sb.toString();

            case "concat": {
                // concat(s1, s2) → s1.concat(s2)
                PerseusParser.ExprContext s2Expr = ctx.argList().arg().get(1).expr();
                sb.append(exprCodeGen.apply(argExpr));
                sb.append(exprCodeGen.apply(s2Expr));
                sb.append("invokevirtual java/lang/String/concat(Ljava/lang/String;)Ljava/lang/String;\n");
                return sb.toString();
            }

            case "substring": {
                // substring(s, start, end) — Algol 1-based inclusive; Java substring(start-1, end)
                // Example: substring(s, 8, 12) → s.substring(7, 12)
                PerseusParser.ExprContext startExpr = ctx.argList().arg().get(1).expr();
                PerseusParser.ExprContext endExpr   = ctx.argList().arg().get(2).expr();
                sb.append(exprCodeGen.apply(argExpr));    // push s
                sb.append(exprCodeGen.apply(startExpr));  // push start
                sb.append("iconst_1\n").append("isub\n"); // beginIndex = start - 1
                sb.append(exprCodeGen.apply(endExpr));    // endIndex = end (Java exclusive)
                sb.append("invokevirtual java/lang/String/substring(II)Ljava/lang/String;\n");
                return sb.toString();
            }

            default:
                return null;
        }
    }
}

