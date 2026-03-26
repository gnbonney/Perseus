package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import gnb.perseus.postprocess.FixLimits;
import java.util.List;

import org.junit.jupiter.api.Test;

public class DiagnosticsTest extends CompilerTest {

   @Test
    public void syntax_error_diagnostic_test() throws Exception {
        Path tempFile = BUILD_DIR.resolve("syntax_error_test.alg");
        Files.createDirectories(BUILD_DIR);
        Files.writeString(tempFile, "begin x := end");

        try {
            PerseusCompiler.compileToFile(
                    tempFile.toString(), "gnb/perseus/programs", "SyntaxErrorTest", BUILD_DIR);
            fail("Expected compilation to fail with a syntax diagnostic");
        } catch (CompilationFailedException e) {
            assertFalse(e.getDiagnostics().isEmpty(), "Expected at least one diagnostic");
            CompilerDiagnostic diagnostic = e.getDiagnostics().get(0);
            assertEquals("PERS1001", diagnostic.code(), "Expected syntax error code");
            assertEquals(1, diagnostic.line(), "Expected syntax error on line 1");
            assertTrue(diagnostic.column() > 0, "Expected a positive column number");
            assertTrue(diagnostic.file().endsWith("syntax_error_test.alg"),
                    "Expected diagnostic to report the source file");
        }
    }

    @Test
    public void semantic_error_diagnostic_test() throws Exception {
        Path tempFile = BUILD_DIR.resolve("semantic_error_test.alg");
        Files.createDirectories(BUILD_DIR);
        Files.writeString(tempFile, "begin integer x; x := y end");

        try {
            PerseusCompiler.compileToFile(
                    tempFile.toString(), "gnb/perseus/programs", "SemanticErrorTest", BUILD_DIR);
            fail("Expected compilation to fail with a semantic diagnostic");
        } catch (CompilationFailedException e) {
            assertEquals(1, e.getDiagnostics().size(), "Expected exactly one diagnostic");
            CompilerDiagnostic diagnostic = e.getDiagnostics().get(0);
            assertEquals("PERS2001", diagnostic.code(), "Expected undeclared variable code");
            assertEquals(1, diagnostic.line(), "Expected semantic error on line 1");
            assertTrue(diagnostic.column() > 0, "Expected a positive column number");
            assertTrue(diagnostic.message().contains("Undeclared variable: y"),
                    "Expected the diagnostic message to mention the missing variable");
        }
    } 
}
