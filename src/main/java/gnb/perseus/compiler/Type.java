package gnb.perseus.compiler;

import java.util.Objects;

/**
 * Structured internal type representation for the compiler.
 *
 * <p>This first slice is deliberately compatible with the compiler's older
 * string tags so subsystems can migrate incrementally while sharing one real
 * type model internally.
 */
public final class Type {
    public enum Kind {
        VOID,
        INTEGER,
        REAL,
        STRING,
        BOOLEAN,
        DEFERRED,
        NULL,
        SWITCH,
        REF,
        ARRAY,
        VECTOR,
        MAP,
        SET,
        PROCEDURE,
        ITERABLE,
        THUNK
    }

    public static final Type VOID = new Type(Kind.VOID, null, null, null, null);
    public static final Type INTEGER = new Type(Kind.INTEGER, null, null, null, null);
    public static final Type REAL = new Type(Kind.REAL, null, null, null, null);
    public static final Type STRING = new Type(Kind.STRING, null, null, null, null);
    public static final Type BOOLEAN = new Type(Kind.BOOLEAN, null, null, null, null);
    public static final Type DEFERRED = new Type(Kind.DEFERRED, null, null, null, null);
    public static final Type NULL = new Type(Kind.NULL, null, null, null, null);
    public static final Type SWITCH = new Type(Kind.SWITCH, null, null, null, null);

    private final Kind kind;
    private final String name;
    private final Type elementType;
    private final Type keyType;
    private final Type valueType;

    private Type(Kind kind, String name, Type elementType, Type keyType, Type valueType) {
        this.kind = kind;
        this.name = name;
        this.elementType = elementType;
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public static Type ref(String simpleName) {
        return new Type(Kind.REF, simpleName, null, null, null);
    }

    public static Type array(Type elementType) {
        return new Type(Kind.ARRAY, null, Objects.requireNonNull(elementType), null, null);
    }

    public static Type vector(Type elementType) {
        return new Type(Kind.VECTOR, null, Objects.requireNonNull(elementType), null, null);
    }

    public static Type set(Type elementType) {
        return new Type(Kind.SET, null, Objects.requireNonNull(elementType), null, null);
    }

    public static Type iterable(Type elementType) {
        return new Type(Kind.ITERABLE, null, Objects.requireNonNull(elementType), null, null);
    }

    public static Type procedure(Type returnType) {
        return new Type(Kind.PROCEDURE, null, Objects.requireNonNull(returnType), null, null);
    }

    public static Type thunk(Type baseType) {
        return new Type(Kind.THUNK, null, Objects.requireNonNull(baseType), null, null);
    }

    public static Type map(Type keyType, Type valueType) {
        return new Type(Kind.MAP, null, null, Objects.requireNonNull(keyType), Objects.requireNonNull(valueType));
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public Type elementType() {
        return elementType;
    }

    public Type keyType() {
        return keyType;
    }

    public Type valueType() {
        return valueType;
    }

    public boolean isScalar() {
        return switch (kind) {
            case INTEGER, REAL, STRING, BOOLEAN, DEFERRED -> true;
            default -> false;
        };
    }

    public boolean isRef() {
        return kind == Kind.REF;
    }

    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    public boolean isVector() {
        return kind == Kind.VECTOR;
    }

    public boolean isMap() {
        return kind == Kind.MAP;
    }

    public boolean isSet() {
        return kind == Kind.SET;
    }

    public boolean isProcedure() {
        return kind == Kind.PROCEDURE;
    }

    public boolean isIterable() {
        return kind == Kind.ITERABLE;
    }

    public boolean isThunk() {
        return kind == Kind.THUNK;
    }

    public boolean isNull() {
        return kind == Kind.NULL;
    }

    public Type unwrapThunk() {
        return kind == Kind.THUNK ? elementType : this;
    }

    public String toLegacyString() {
        return switch (kind) {
            case VOID -> "void";
            case INTEGER -> "integer";
            case REAL -> "real";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case DEFERRED -> "deferred";
            case NULL -> "null";
            case SWITCH -> "switch";
            case REF -> "ref:" + name;
            case ARRAY -> elementType.toLegacyString() + "[]";
            case VECTOR -> "vector:" + elementType.toLegacyString();
            case MAP -> "map:" + keyType.toLegacyString() + "=>" + valueType.toLegacyString();
            case SET -> "set:" + elementType.toLegacyString();
            case PROCEDURE -> "procedure:" + elementType.toLegacyString();
            case ITERABLE -> "iterable:" + elementType.toLegacyString();
            case THUNK -> "thunk:" + elementType.toLegacyString();
        };
    }

    public static Type parse(String legacy) {
        if (legacy == null) {
            return null;
        }
        if (legacy.startsWith("thunk:")) {
            return thunk(parse(legacy.substring("thunk:".length())));
        }
        if (legacy.startsWith("procedure:")) {
            return procedure(parse(legacy.substring("procedure:".length())));
        }
        if (legacy.startsWith("map:")) {
            int sep = legacy.indexOf("=>");
            if (sep < 0) {
                throw new IllegalArgumentException("Invalid map type tag: " + legacy);
            }
            return map(parse(legacy.substring("map:".length(), sep)), parse(legacy.substring(sep + 2)));
        }
        if (legacy.startsWith("vector:")) {
            return vector(parse(legacy.substring("vector:".length())));
        }
        if (legacy.startsWith("set:")) {
            return set(parse(legacy.substring("set:".length())));
        }
        if (legacy.startsWith("iterable:")) {
            return iterable(parse(legacy.substring("iterable:".length())));
        }
        if (legacy.startsWith("ref:") && legacy.endsWith("[]")) {
            return array(ref(legacy.substring("ref:".length(), legacy.length() - 2)));
        }
        if (legacy.endsWith("[]")) {
            return array(parse(legacy.substring(0, legacy.length() - 2)));
        }
        if (legacy.startsWith("ref:")) {
            return ref(legacy.substring("ref:".length()));
        }
        return switch (legacy) {
            case "void" -> VOID;
            case "integer" -> INTEGER;
            case "real" -> REAL;
            case "string" -> STRING;
            case "boolean" -> BOOLEAN;
            case "deferred" -> DEFERRED;
            case "null" -> NULL;
            case "switch" -> SWITCH;
            default -> throw new IllegalArgumentException("Unknown legacy type tag: " + legacy);
        };
    }

    @Override
    public String toString() {
        return toLegacyString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type type)) return false;
        return kind == type.kind
                && Objects.equals(name, type.name)
                && Objects.equals(elementType, type.elementType)
                && Objects.equals(keyType, type.keyType)
                && Objects.equals(valueType, type.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, elementType, keyType, valueType);
    }
}
