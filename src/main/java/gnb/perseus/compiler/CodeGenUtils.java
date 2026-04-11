package gnb.perseus.compiler;

import java.util.concurrent.atomic.AtomicInteger;

public class CodeGenUtils {
    private static final AtomicInteger labelCounter = new AtomicInteger(0);

    public static String generateUniqueLabel(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    public static String arrayTypeToJvmDesc(String type) {
        if (type == null) return "[I";
        if (type.startsWith("ref:") && type.endsWith("[]")) return "[Ljava/lang/Object;";
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
        if (type.startsWith("ref:")) return "Ljava/lang/Object;";
        if (type.startsWith("vector:")) return "Ljava/util/List;";
        if (type.startsWith("map:")) return "Ljava/util/Map;";
        if (type.startsWith("procedure:")) return getProcedureInterfaceDescriptor(type);
        return switch (type) {
            case "real"   -> "D";
            case "deferred" -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
        };
    }

    public static String getReturnTypeDescriptor(String type) {
        if (type == null) return "V";
        if (type.startsWith("ref:")) return "Ljava/lang/Object;";
        if (type.startsWith("vector:")) return "Ljava/util/List;";
        if (type.startsWith("map:")) return "Ljava/util/Map;";
        if (type.startsWith("procedure:")) return getProcedureInterfaceDescriptor(type);
        return switch (type) {
            case "void"              -> "V";
            case "real"              -> "D";
            case "deferred"          -> "D";
            case "string"            -> "Ljava/lang/String;";
            case "boolean", "integer" -> "I";
            default -> "I";
        };
    }

    public static String getReturnInstruction(String type) {
        if (type == null) return "return";
        if (type.startsWith("ref:")) return "areturn";
        if (type.startsWith("vector:")) return "areturn";
        if (type.startsWith("map:")) return "areturn";
        if (type.startsWith("procedure:")) return "areturn";
        return switch (type) {
            case "void"   -> "return";
            case "real"   -> "dreturn";
            case "deferred" -> "dreturn";
            case "string" -> "areturn";
            default -> "ireturn";
        };
    }

    public static String getProcedureInterfaceDescriptor(String procType) {
        if (procType == null || !procType.startsWith("procedure:")) {
            return "Lgnb/perseus/compiler/VoidProcedure;";
        }
        String returnType = procType.substring("procedure:".length());
        if (returnType.startsWith("ref:") || returnType.startsWith("procedure:")) {
            return "Lgnb/perseus/compiler/ReferenceProcedure;";
        }
        return switch (returnType) {
            case "void" -> "Lgnb/perseus/compiler/VoidProcedure;";
            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
            case "boolean", "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
            default -> throw new RuntimeException("Unknown procedure return type: " + returnType);
        };
    }
}
