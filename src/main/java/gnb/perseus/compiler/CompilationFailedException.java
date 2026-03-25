package gnb.perseus.compiler;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CompilationFailedException extends IOException {
    private final List<CompilerDiagnostic> diagnostics;

    public CompilationFailedException(List<CompilerDiagnostic> diagnostics) {
        super(buildMessage(diagnostics));
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public List<CompilerDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    private static String buildMessage(List<CompilerDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "Compilation failed.";
        }
        return diagnostics.stream()
                .map(CompilerDiagnostic::format)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
