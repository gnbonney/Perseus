package gnb.perseus.compiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared Java interop resolution for overloaded methods and constructors.
 */
public final class JavaInteropResolver {
    @FunctionalInterface
    public interface ReferenceCompatibility {
        int score(Class<?> parameterType, String refTypeSimpleName);
    }

    public record MethodResolution(Method method, String diagnostic) {}
    public record ConstructorResolution(Constructor<?> constructor, String diagnostic) {}

    private JavaInteropResolver() {
    }

    public static Method findBestMethod(String qualifiedName, String methodName, List<String> argTypes) {
        return resolveMethod(qualifiedName, methodName, argTypes).method();
    }

    public static Method findBestMethod(String qualifiedName, String methodName, List<String> argTypes,
            ReferenceCompatibility compatibility) {
        return resolveMethod(qualifiedName, methodName, argTypes, compatibility).method();
    }

    public static MethodResolution resolveMethod(String qualifiedName, String methodName, List<String> argTypes) {
        return resolveMethod(qualifiedName, methodName, argTypes, null);
    }

    public static MethodResolution resolveMethod(String qualifiedName, String methodName, List<String> argTypes,
            ReferenceCompatibility compatibility) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            Method best = null;
            int bestScore = Integer.MAX_VALUE;
            ArrayList<Method> bestMethods = new ArrayList<>();
            boolean sawSameName = false;
            for (Method method : owner.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (!method.getName().equals(methodName) || method.getParameterCount() != argTypes.size()) {
                    if (method.getName().equals(methodName)) {
                        sawSameName = true;
                    }
                    continue;
                }
                sawSameName = true;
                int score = scoreJavaCallable(method.getParameterTypes(), argTypes, compatibility);
                if (score >= 0 && score < bestScore) {
                    best = method;
                    bestScore = score;
                    bestMethods.clear();
                    bestMethods.add(method);
                } else if (score >= 0 && score == bestScore) {
                    bestMethods.add(method);
                }
            }
            if (bestMethods.size() > 1) {
                return new MethodResolution(null,
                        "Ambiguous Java overload for " + qualifiedName + "." + methodName
                                + formatArgTypes(argTypes) + "; candidates: " + formatMethodCandidates(bestMethods));
            }
            if (best != null) {
                return new MethodResolution(best, null);
            }
            if (sawSameName) {
                return new MethodResolution(null,
                        "No matching Java overload for " + qualifiedName + "." + methodName
                                + formatArgTypes(argTypes));
            }
        } catch (ClassNotFoundException ignored) {
        }
        return new MethodResolution(null,
                "Unknown Java member: " + qualifiedName + "." + methodName + formatArgTypes(argTypes));
    }

    public static Constructor<?> findBestConstructor(String qualifiedName, List<String> argTypes) {
        return resolveConstructor(qualifiedName, argTypes).constructor();
    }

    public static Constructor<?> findBestConstructor(String qualifiedName, List<String> argTypes,
            ReferenceCompatibility compatibility) {
        return resolveConstructor(qualifiedName, argTypes, compatibility).constructor();
    }

    public static ConstructorResolution resolveConstructor(String qualifiedName, List<String> argTypes) {
        return resolveConstructor(qualifiedName, argTypes, null);
    }

    public static ConstructorResolution resolveConstructor(String qualifiedName, List<String> argTypes,
            ReferenceCompatibility compatibility) {
        try {
            Class<?> owner = Class.forName(qualifiedName);
            Constructor<?> best = null;
            int bestScore = Integer.MAX_VALUE;
            ArrayList<Constructor<?>> bestCtors = new ArrayList<>();
            for (Constructor<?> ctor : owner.getConstructors()) {
                if (ctor.getParameterCount() != argTypes.size()) {
                    continue;
                }
                int score = scoreJavaCallable(ctor.getParameterTypes(), argTypes, compatibility);
                if (score >= 0 && score < bestScore) {
                    best = ctor;
                    bestScore = score;
                    bestCtors.clear();
                    bestCtors.add(ctor);
                } else if (score >= 0 && score == bestScore) {
                    bestCtors.add(ctor);
                }
            }
            if (bestCtors.size() > 1) {
                return new ConstructorResolution(null,
                        "Ambiguous Java constructor overload for " + qualifiedName
                                + formatArgTypes(argTypes) + "; candidates: " + formatConstructorCandidates(bestCtors));
            }
            if (best != null) {
                return new ConstructorResolution(best, null);
            }
        } catch (ClassNotFoundException ignored) {
        }
        return new ConstructorResolution(null,
                "No matching Java constructor for " + qualifiedName + formatArgTypes(argTypes));
    }

    private static int scoreJavaCallable(Class<?>[] parameterTypes, List<String> argTypes,
            ReferenceCompatibility compatibility) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int paramScore = scoreJavaArgument(parameterTypes[i], argTypes.get(i), compatibility);
            if (paramScore < 0) {
                return -1;
            }
            score += paramScore;
        }
        return score;
    }

    private static int scoreJavaArgument(Class<?> parameterType, String argType,
            ReferenceCompatibility compatibility) {
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
                    if (compatibility != null) {
                        int compatibilityScore = compatibility.score(parameterType, simpleName);
                        if (compatibilityScore >= 0) yield compatibilityScore;
                    }
                    if (!parameterType.isPrimitive() && parameterType == Object.class) yield 8;
                    yield -1;
                }
                yield !parameterType.isPrimitive() ? 12 : -1;
            }
        };
    }

    private static String formatArgTypes(List<String> argTypes) {
        return "(" + argTypes.stream().map(JavaInteropResolver::displayType).collect(Collectors.joining(", ")) + ")";
    }

    private static String displayType(String type) {
        if (type == null) {
            return "unknown";
        }
        if (type.startsWith("thunk:")) {
            return displayType(type.substring("thunk:".length()));
        }
        return type;
    }

    private static String formatMethodCandidates(List<Method> methods) {
        return methods.stream()
                .map(m -> m.getDeclaringClass().getName() + "." + m.getName()
                        + formatJavaTypes(m.getParameterTypes()))
                .collect(Collectors.joining("; "));
    }

    private static String formatConstructorCandidates(List<Constructor<?>> ctors) {
        return ctors.stream()
                .map(c -> c.getDeclaringClass().getName() + formatJavaTypes(c.getParameterTypes()))
                .collect(Collectors.joining("; "));
    }

    private static String formatJavaTypes(Class<?>[] parameterTypes) {
        return "(" + java.util.Arrays.stream(parameterTypes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ")) + ")";
    }
}
