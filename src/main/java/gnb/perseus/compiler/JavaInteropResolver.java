package gnb.perseus.compiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Shared Java interop resolution for overloaded methods and constructors.
 */
public final class JavaInteropResolver {
    private JavaInteropResolver() {
    }

    public static Method findBestMethod(String qualifiedName, String methodName, List<String> argTypes) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            Method best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Method method : owner.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != argTypes.size()) {
                    continue;
                }
                int score = scoreJavaCallable(method.getParameterTypes(), argTypes);
                if (score >= 0 && score < bestScore) {
                    best = method;
                    bestScore = score;
                }
            }
            return best;
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    public static Constructor<?> findBestConstructor(String qualifiedName, List<String> argTypes) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            Constructor<?> best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Constructor<?> ctor : owner.getConstructors()) {
                if (ctor.getParameterCount() != argTypes.size()) {
                    continue;
                }
                int score = scoreJavaCallable(ctor.getParameterTypes(), argTypes);
                if (score >= 0 && score < bestScore) {
                    best = ctor;
                    bestScore = score;
                }
            }
            return best;
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static int scoreJavaCallable(Class<?>[] parameterTypes, List<String> argTypes) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int paramScore = scoreJavaArgument(parameterTypes[i], argTypes.get(i));
            if (paramScore < 0) {
                return -1;
            }
            score += paramScore;
        }
        return score;
    }

    private static int scoreJavaArgument(Class<?> parameterType, String argType) {
        if (argType == null) {
            return !parameterType.isPrimitive() ? 20 : -1;
        }
        if (argType.startsWith("thunk:")) {
            argType = argType.substring("thunk:".length());
        }
        return switch (argType) {
            case "string" -> {
                if (parameterType == String.class) yield 0;
                if (!parameterType.isPrimitive() && parameterType.isAssignableFrom(String.class)) yield 5;
                yield -1;
            }
            case "integer" -> {
                if (parameterType == int.class || parameterType == Integer.class) yield 0;
                if (parameterType == double.class || parameterType == Double.class) yield 1;
                if (parameterType == long.class || parameterType == Long.class) yield 2;
                if (parameterType == float.class || parameterType == Float.class) yield 3;
                if (!parameterType.isPrimitive() && Number.class.isAssignableFrom(parameterType)) yield 5;
                if (!parameterType.isPrimitive() && parameterType.isAssignableFrom(Integer.class)) yield 6;
                yield -1;
            }
            case "real", "deferred" -> {
                if (parameterType == double.class || parameterType == Double.class) yield 0;
                if (parameterType == float.class || parameterType == Float.class) yield 1;
                if (!parameterType.isPrimitive() && Number.class.isAssignableFrom(parameterType)) yield 5;
                if (!parameterType.isPrimitive() && parameterType.isAssignableFrom(Double.class)) yield 6;
                yield -1;
            }
            case "boolean" -> {
                if (parameterType == boolean.class || parameterType == Boolean.class) yield 0;
                if (!parameterType.isPrimitive() && parameterType.isAssignableFrom(Boolean.class)) yield 6;
                yield -1;
            }
            default -> {
                if (argType.startsWith("ref:")) {
                    String simpleName = argType.substring("ref:".length());
                    if (parameterType.getSimpleName().equals(simpleName)) yield 0;
                    if (!parameterType.isPrimitive() && parameterType == Object.class) yield 8;
                    if (!parameterType.isPrimitive()) yield 10;
                }
                yield !parameterType.isPrimitive() ? 12 : -1;
            }
        };
    }
}
