package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ClassesTest extends CompilerTest {

    @Test
    public void class_counter_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/class_counter.alg", "gnb/perseus/programs", "ClassCounterTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== CLASS COUNTER JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CLASS COUNTER ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains("Counter"),
                "Generated output should reflect the class-based Counter sample");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassCounterTest");
        System.out.println("class_counter output: [" + output + "]");
        assertEquals("10", output.trim(),
                "Counter objects should preserve mutable instance state across method calls");
    }

    @Test
    public void class_point_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/class_point.alg", "gnb/perseus/programs", "ClassPointTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== CLASS POINT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CLASS POINT ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains("Point"),
                "Generated output should reflect the class-based Point sample");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassPointTest");
        System.out.println("class_point output: [" + output + "]");
        assertEquals("5.0 7.211102550927978", output.trim(),
                "Point objects should support real-valued fields, mutation, and repeated method calls");
    }

    @Test
    public void class_two_counters_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/class_two_counters.alg", "gnb/perseus/programs", "ClassTwoCountersTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== CLASS TWO COUNTERS JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CLASS TWO COUNTERS ===");

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
        assertTrue(jasminSource.contains("Counter"),
                "Generated output should reflect the multi-instance Counter sample");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassTwoCountersTest");
        System.out.println("class_two_counters output: [" + output + "]");
        assertEquals("5 17", output.trim(),
                "Distinct objects should keep distinct instance state");
    }
}
