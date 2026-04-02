package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gnb.tools.PerseusStdlibBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class StdlibTest extends CompilerTest {

    @Test
    public void compiled_stdlib_jar_test() throws Exception {
        Path stdlibClasses = Files.createTempDirectory(BUILD_DIR, "stdlib-classes");
        Path stdlibJar = BUILD_DIR.resolve("perseus-stdlib-test.jar");

        PerseusStdlibBuilder.build(
                Path.of("src/main/perseus/stdlib"),
                stdlibClasses,
                stdlibJar);

        assertTrue(Files.exists(stdlibClasses.resolve("perseus/lang/MathEnv.class")),
                "The stdlib builder should compile MathEnv under its namespace");
        assertTrue(Files.exists(stdlibJar),
                "The stdlib builder should package the compiled classes into a jar");
    }

    @Test
    public void automatic_stdlib_builtin_test() throws Exception {
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_math_client.alg",
                "gnb/perseus/programs",
                "StdlibMathClient",
                clientDir);
        PerseusCompiler.assemble(clientJasmin, clientDir);

        assertTrue(Files.exists(clientDir.resolve("perseus/lang/MathEnv.class")),
                "Normal compilation should provision MathEnv automatically");

        String output = runClass(clientDir, "gnb.perseus.programs.StdlibMathClient");
        assertEquals("-1,2.5,42,3.0,0.0,1.0,0.0,1.0,3,-3", output.trim(),
                "Builtin math names should work through the automatically provisioned standard environment");
    }

    @Test
    public void automatic_stdlib_strings_test() throws Exception {
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-strings-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_strings_client.alg",
                "gnb/perseus/programs",
                "StdlibStringsClient",
                clientDir);
        PerseusCompiler.assemble(clientJasmin, clientDir);

        assertTrue(Files.exists(clientDir.resolve("perseus/text/Strings.class")),
                "Normal compilation should provision Strings automatically");

        String output = runClass(clientDir, "gnb.perseus.programs.StdlibStringsClient");
        assertEquals("13,world,world!", output.trim(),
                "Builtin string names should work through the automatically provisioned standard environment");
    }
}
