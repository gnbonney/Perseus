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

        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_math_client.alg",
                "gnb/perseus/programs",
                "StdlibMathClient",
                clientDir,
                java.util.List.of(clientDir, stdlibJar));
        PerseusCompiler.assemble(clientJasmin, clientDir);

        String output = runClassWithClasspath(clientDir, "gnb.perseus.programs.StdlibMathClient", stdlibJar);
        assertEquals("-1,2.5", output.trim(),
                "A client should be able to call a compiled stdlib unit packaged as a jar");
    }
}
