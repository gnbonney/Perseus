package gnb.perseus.compiler;

public class DiagnosticException extends RuntimeException {
    private final CompilerDiagnostic diagnostic;

    public DiagnosticException(CompilerDiagnostic diagnostic) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
    }

    public CompilerDiagnostic getDiagnostic() {
        return diagnostic;
    }
}
