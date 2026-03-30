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
                "test/algol/classes/class_counter.alg", "gnb/perseus/programs", "ClassCounterTest", BUILD_DIR);
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
                "test/algol/classes/class_point.alg", "gnb/perseus/programs", "ClassPointTest", BUILD_DIR);
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
                "test/algol/classes/class_two_counters.alg", "gnb/perseus/programs", "ClassTwoCountersTest", BUILD_DIR);
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

    @Test
    public void class_prefix_point_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_prefix_point.alg",
                "gnb/perseus/programs",
                "ClassPrefixPointTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassPrefixPointTest");
        assertEquals("7.211102550927978", output.trim(),
                "Prefix inheritance should preserve inherited fields and procedures");
    }

    @Test
    public void class_dynamic_dispatch_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_dynamic_dispatch.alg",
                "gnb/perseus/programs",
                "ClassDynamicDispatchTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassDynamicDispatchTest");
        assertEquals("dog", output.trim(),
                "Dynamic dispatch should select the overriding procedure from the runtime class");
    }

    @Test
    public void class_external_java_stringbuilder_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_external_java_stringbuilder.alg",
                "gnb/perseus/programs",
                "ClassExternalJavaStringBuilderTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassExternalJavaStringBuilderTest");
        assertEquals("Hello World", output.trim(),
                "External Java class declarations should support object creation and method calls");
    }

    @Test
    public void class_extends_external_java_random_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_extends_external_java_random.alg",
                "gnb/perseus/programs",
                "ClassExtendsExternalJavaRandomTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassExtendsExternalJavaRandomTest").trim();
        assertTrue(output.matches("[1-6]"),
                "A Perseus subclass of java.util.Random should be able to call inherited Java methods");
    }

    @Test
    public void class_extends_external_java_abstract_outputstream_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_extends_external_java_abstract_outputstream.alg",
                "gnb/perseus/programs",
                "ClassExtendsExternalJavaAbstractOutputStreamTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassExtendsExternalJavaAbstractOutputStreamTest");
        assertEquals("2", output.trim(),
                "A Perseus subclass of an abstract Java class should be able to satisfy required methods");
    }

    @Test
    public void class_implements_external_java_runnable_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_implements_external_java_runnable.alg",
                "gnb/perseus/programs",
                "ClassImplementsExternalJavaRunnableTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassImplementsExternalJavaRunnableTest");
        assertEquals("hello from thread", output.trim(),
                "A Perseus class should be able to satisfy a Java interface and be passed to Java code");
    }

    @Test
    public void class_implements_external_java_two_interfaces_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/classes/class_implements_external_java_two_interfaces.alg",
                "gnb/perseus/programs",
                "ClassImplementsExternalJavaTwoInterfacesTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ClassImplementsExternalJavaTwoInterfacesTest");
        assertEquals("hello closed", output.trim(),
                "A Perseus class should be able to satisfy multiple Java interfaces and be used through each");
    }
}
