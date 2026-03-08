package gnb.jalgol.compiler;

import java.util.concurrent.atomic.AtomicInteger;

public class CodeGenUtils {
    private static final AtomicInteger labelCounter = new AtomicInteger(0);

    public static String generateUniqueLabel(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    public static String arrayTypeToJvmDesc(String type) {
        if (type == null) return "I";
        return switch (type) {
            case "real[]" -> "[D";
            case "string[]" -> "[Ljava/lang/String;";
            case "boolean[]" -> "[B";
            default -> "[I";
        };
    }

    public static String getReturnTypeDescriptor(String type) {
        if (type == null) return "V";
        return switch (type) {
            case "void" -> "V";
            case "real" -> "D";
            case "string" -> "Ljava/lang/String;";
            case "boolean" -> "I";
            case "integer" -> "I";
            default -> "Ljava/lang/Object;";
        };
    }
}
