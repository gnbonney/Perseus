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
    public void exception_bounds_error_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_bounds_error.alg",
                "gnb/perseus/programs",
                "ExceptionBoundsError",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== EXCEPTION BOUNDS ERROR JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END EXCEPTION BOUNDS ERROR ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/ArrayIndexOutOfBoundsException"),
                "BoundsError should currently lower to a JVM array-bounds catch");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionBoundsError");
        assertEquals("99", output.trim(),
                "BoundsError handler should recover after an out-of-range array access");
    }

    @Test
    public void exception_fault_error_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/exceptions/exception_fault_error.alg",
                "gnb/perseus/programs",
                "ExceptionFaultError",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== EXCEPTION FAULT ERROR JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END EXCEPTION FAULT ERROR ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains(".catch java/lang/RuntimeException"),
                "FaultError should currently lower to a catchable runtime exception");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExceptionFaultError");
        assertEquals("1", output.trim(),
                "fault(...) inside an exception block should recover through FaultError handling");
    }
}
