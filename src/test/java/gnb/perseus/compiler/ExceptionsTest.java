package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ExceptionsTest extends CompilerTest {

    @Test
    public void exception_java_number_format_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_java_number_format.alg",
                "gnb/perseus/programs",
                "ExceptionJavaNumberFormat",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== EXCEPTION JAVA NUMBER FORMAT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END EXCEPTION JAVA NUMBER FORMAT ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/NumberFormatException"),
                "Exception block should lower to a JVM catch for NumberFormatException");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionJavaNumberFormat");
        assertEquals("0", output.trim(),
                "NumberFormatException should be caught and recovered to the fallback integer value");
    }

    @Test
    public void exception_named_number_format_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_named_number_format.alg",
                "gnb/perseus/programs",
                "ExceptionNamedNumberFormat",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/NumberFormatException"),
                "A common Java exception name should resolve without an explicit external java class declaration");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionNamedNumberFormat");
        assertEquals("0", output.trim(),
                "A named Java exception pattern should catch NumberFormatException without java(...) syntax");
    }

    @Test
    public void exception_java_number_format_as_ex_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_java_number_format_as_ex.alg",
                "gnb/perseus/programs",
                "ExceptionJavaNumberFormatAsEx",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/NumberFormatException"),
                "The handler should still lower to a JVM catch for NumberFormatException");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionJavaNumberFormatAsEx");
        assertEquals("For input string: \"not-a-number\"", output.trim(),
                "An as-bound exception value should be usable inside the handler");
    }

    @Test
    public void exception_common_java_shortcuts_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_common_java_shortcuts.alg",
                "gnb/perseus/programs",
                "ExceptionCommonJavaShortcuts",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/NumberFormatException"),
                "Named NumberFormatException should lower to the corresponding JVM catch");
        assertTrue(jasminSource.contains(".catch java/lang/ArithmeticException"),
                "Named ArithmeticException should lower to the corresponding JVM catch");
        assertTrue(jasminSource.contains(".catch java/lang/IllegalArgumentException"),
                "Named IllegalArgumentException should lower to the corresponding JVM catch");
        assertTrue(jasminSource.contains(".catch java/lang/Exception"),
                "Named Exception should lower to the corresponding JVM catch");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionCommonJavaShortcuts");
        assertEquals("1111", output.trim(),
                "Common Java exception shortcuts should work across several representative exception types");
    }

    @Test
    public void exception_array_index_out_of_bounds_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_array_index_out_of_bounds.alg",
                "gnb/perseus/programs",
                "ExceptionArrayIndexOutOfBounds",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== EXCEPTION ARRAY INDEX OUT OF BOUNDS JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END EXCEPTION ARRAY INDEX OUT OF BOUNDS ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/ArrayIndexOutOfBoundsException"),
                "ArrayIndexOutOfBoundsException should lower to the corresponding JVM catch");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionArrayIndexOutOfBounds");
        assertEquals("99", output.trim(),
                "ArrayIndexOutOfBoundsException should recover after an out-of-range array access");
    }

    @Test
    public void exception_runtime_exception_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_runtime_exception.alg",
                "gnb/perseus/programs",
                "ExceptionRuntimeException",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== EXCEPTION RUNTIME EXCEPTION JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END EXCEPTION RUNTIME EXCEPTION ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/RuntimeException"),
                "RuntimeException should currently lower to a catchable runtime exception");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionRuntimeException");
        assertEquals("1", output.trim(),
                "fault(...) inside an exception block should recover through RuntimeException handling");
    }

    @Test
    public void exception_nested_blocks_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_nested_blocks.alg",
                "gnb/perseus/programs",
                "ExceptionNestedBlocks",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/NumberFormatException"),
                "Nested exception blocks should still lower to JVM catch regions");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionNestedBlocks");
        assertEquals("11", output.trim(),
                "The inner exception block should handle its own failure without falling through to the outer handler");
    }

    @Test
    public void signal_runtime_exception_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/signal_runtime_exception.alg",
                "gnb/perseus/programs",
                "SignalRuntimeException",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains("athrow"),
                "signal should lower to JVM exception throwing");
        assertTrue(jasminSource.contains(".catch java/lang/RuntimeException"),
                "signaled RuntimeException should be catchable through the existing exception model");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.SignalRuntimeException");
        assertEquals("1", output.trim(),
                "signal new RuntimeException(...) should integrate with block exception handling");
    }
}
