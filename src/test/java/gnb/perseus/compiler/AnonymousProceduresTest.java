package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class AnonymousProceduresTest extends CompilerTest {

    @Test
    public void anonymous_proc_numerical_sum_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/procedures/anonymous_proc_numerical_sum.alg",
                "gnb/perseus/programs",
                "AnonymousProcNumericalSum",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("__anonproc"),
                "Numerical anonymous procedures should lower to synthetic static helper methods");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcNumericalSum");
        String[] lines = output.trim().split("\\R");
        assertEquals(2, lines.length, "Expected two lines of numerical output");
        assertEquals(2.283333333333333, Double.parseDouble(lines[0]), 1e-12);
        assertEquals(30.0, Double.parseDouble(lines[1]), 1e-12);
    }

    @Test
    public void anonymous_proc_array_transform_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/procedures/anonymous_proc_array_transform.alg",
                "gnb/perseus/programs",
                "AnonymousProcArrayTransform",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("__anonproc"),
                "Array-transform anonymous procedures should lower to synthetic static helper methods");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcArrayTransform");
        String[] lines = output.trim().split("\\R");
        assertEquals(4, lines.length, "Expected four transformed values");
        assertEquals(4.0, Double.parseDouble(lines[0]), 1e-12);
        assertEquals(9.0, Double.parseDouble(lines[1]), 1e-12);
        assertEquals(16.0, Double.parseDouble(lines[2]), 1e-12);
        assertEquals(25.0, Double.parseDouble(lines[3]), 1e-12);
    }
}
