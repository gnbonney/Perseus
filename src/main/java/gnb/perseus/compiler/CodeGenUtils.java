package gnb.perseus.compiler;

import java.util.concurrent.atomic.AtomicInteger;

public class CodeGenUtils {
    private static final AtomicInteger labelCounter = new AtomicInteger(0);

    public static String generateUniqueLabel(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    public static String arrayTypeToJvmDesc(String type) {
        if (type == null) return "[I";
        return switch (type) {
            case "boolean[]" -> "[Z";
            case "integer[]" -> "[I";
            case "real[]"    -> "[D";
            case "string[]"  -> "[Ljava/lang/String;";
            default -> "[I";
        };
    }

    public static String scalarTypeToJvmDesc(String type) {
        if (type == null) return "I";
        return switch (type) {
            case "real"   -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
        };
    }

    public static String getReturnTypeDescriptor(String type) {
        if (type == null) return "V";
        return switch (type) {
            case "void"              -> "V";
            case "real"              -> "D";
            case "string"            -> "Ljava/lang/String;";
            case "boolean", "integer" -> "I";
            default -> "I";
        };
    }

    public static String getReturnInstruction(String type) {
        if (type == null) return "ireturn";
        return switch (type) {
            case "real"   -> "dreturn";
            case "string" -> "areturn";
            default -> "ireturn";
        };
    }
}
