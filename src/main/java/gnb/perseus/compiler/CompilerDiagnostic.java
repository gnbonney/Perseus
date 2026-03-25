package gnb.perseus.compiler;

import org.antlr.v4.runtime.Token;

public record CompilerDiagnostic(
        String code,
        Severity severity,
        String file,
        int line,
        int column,
        String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public CompilerDiagnostic {
        if (file == null || file.isBlank()) {
            file = "<input>";
        }
        if (message == null) {
            message = "";
        }
    }

    public static CompilerDiagnostic error(String code, String file, int line, int column, String message) {
        return new CompilerDiagnostic(code, Severity.ERROR, file, line, column, message);
    }

    public static CompilerDiagnostic error(String code, Token token, String file, String message) {
        if (token == null) {
            return error(code, file, 0, 0, message);
        }
        return error(code, file, token.getLine(), token.getCharPositionInLine() + 1, message);
    }

    public String format() {
        return "%s %s %s:%d:%d %s".formatted(severity.name(), code, file, line, column, message);
    }
}
