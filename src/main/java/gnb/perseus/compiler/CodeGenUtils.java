package gnb.perseus.compiler;

import java.util.concurrent.atomic.AtomicInteger;

public class CodeGenUtils {
    private static final AtomicInteger labelCounter = new AtomicInteger(0);

    public static String generateUniqueLabel(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    public static String arrayTypeToJvmDesc(String type) {
        return arrayTypeToJvmDesc(type != null ? Type.parse(type) : null);
    }

    public static String arrayTypeToJvmDesc(Type type) {
        if (type == null) return "[I";
        if (!type.isArray()) return "[I";
        Type elementType = type.elementType();
        if (elementType != null && elementType.isRef()) return "[Ljava/lang/Object;";
        return switch (elementType != null ? elementType.toLegacyString() : "") {
            case "boolean" -> "[Z";
            case "integer" -> "[I";
            case "real"    -> "[D";
            case "string"  -> "[Ljava/lang/String;";
            default -> "[I";
        };
    }

    public static String scalarTypeToJvmDesc(String type) {
        return scalarTypeToJvmDesc(type != null ? Type.parse(type) : null);
    }

    public static String scalarTypeToJvmDesc(Type type) {
        if (type == null) return "I";
        if (type.isRef()) return "Ljava/lang/Object;";
        if (type.isVector()) return "Ljava/util/List;";
        if (type.isMap()) return "Ljava/util/Map;";
        if (type.isSet()) return "Ljava/util/Set;";
        if (type.isProcedure()) return getProcedureInterfaceDescriptor(type);
        return switch (type.toLegacyString()) {
            case "real", "deferred" -> "D";
            case "string" -> "Ljava/lang/String;";
            default -> "I";
        };
    }

    public static String getReturnTypeDescriptor(String type) {
        return getReturnTypeDescriptor(type != null ? Type.parse(type) : null);
    }

    public static String getReturnTypeDescriptor(Type type) {
        if (type == null) return "V";
        if (type.isRef()) return "Ljava/lang/Object;";
        if (type.isVector()) return "Ljava/util/List;";
        if (type.isMap()) return "Ljava/util/Map;";
        if (type.isSet()) return "Ljava/util/Set;";
        if (type.isProcedure()) return getProcedureInterfaceDescriptor(type);
        return switch (type.toLegacyString()) {
            case "void" -> "V";
            case "real", "deferred" -> "D";
            case "string" -> "Ljava/lang/String;";
            case "boolean", "integer" -> "I";
            default -> "I";
        };
    }

    public static String getReturnInstruction(String type) {
        return getReturnInstruction(type != null ? Type.parse(type) : null);
    }

    public static String getReturnInstruction(Type type) {
        if (type == null) return "return";
        if (type.isRef() || type.isVector() || type.isMap() || type.isSet() || type.isProcedure()) return "areturn";
        return switch (type.toLegacyString()) {
            case "void" -> "return";
            case "real", "deferred" -> "dreturn";
            case "string" -> "areturn";
            default -> "ireturn";
        };
    }

    public static String getProcedureInterfaceDescriptor(String procType) {
        return getProcedureInterfaceDescriptor(procType != null ? Type.parse(procType) : null);
    }

    public static String getProcedureInterfaceDescriptor(Type procType) {
        if (procType == null || !procType.isProcedure()) {
            return "Lgnb/perseus/compiler/VoidProcedure;";
        }
        Type returnType = procType.elementType();
        if (returnType != null && (returnType.isRef() || returnType.isProcedure())) {
            return "Lgnb/perseus/compiler/ReferenceProcedure;";
        }
        return switch (returnType != null ? returnType.toLegacyString() : "void") {
            case "void" -> "Lgnb/perseus/compiler/VoidProcedure;";
            case "real" -> "Lgnb/perseus/compiler/RealProcedure;";
            case "boolean", "integer" -> "Lgnb/perseus/compiler/IntegerProcedure;";
            case "string" -> "Lgnb/perseus/compiler/StringProcedure;";
            default -> throw new RuntimeException("Unknown procedure return type: " + returnType);
        };
    }
}
