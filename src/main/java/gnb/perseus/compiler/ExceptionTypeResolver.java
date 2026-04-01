package gnb.perseus.compiler;

import gnb.perseus.compiler.antlr.PerseusParser;

/**
 * Shared resolution helpers for Perseus exception patterns.
 */
public final class ExceptionTypeResolver {
    private ExceptionTypeResolver() {
    }

    public static String toQualifiedJavaName(PerseusParser.ExceptionPatternContext ctx) {
        if (ctx == null) return "java.lang.RuntimeException";
        if (ctx.qualifiedName() != null) {
            return ctx.qualifiedName().getText();
        }
        if (ctx.identifier() != null) {
            String identifier = ctx.identifier().getText();
            String builtIn = SymbolTableBuilder.resolveBuiltInJavaExceptionQualifiedName(identifier);
            if (builtIn != null) {
                return builtIn;
            }
            return "java.lang.RuntimeException";
        }
        return "java.lang.RuntimeException";
    }

    public static String toInternalJavaName(PerseusParser.ExceptionPatternContext ctx) {
        return toQualifiedJavaName(ctx).replace('.', '/');
    }

    public static String toSimpleClassName(PerseusParser.ExceptionPatternContext ctx) {
        return simpleClassName(toQualifiedJavaName(ctx));
    }

    public static String toReferenceType(PerseusParser.ExceptionPatternContext ctx) {
        return "ref:" + toSimpleClassName(ctx);
    }

    public static String simpleClassName(String qualifiedName) {
        int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
    }
}
