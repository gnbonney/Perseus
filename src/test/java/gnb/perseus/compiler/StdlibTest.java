package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    public void automatic_stdlib_textoutput_test() throws Exception {
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-textoutput-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_textoutput_client.alg",
                "gnb/perseus/programs",
                "StdlibTextOutputClient",
                clientDir);
        PerseusCompiler.assemble(clientJasmin, clientDir);

        assertTrue(Files.exists(clientDir.resolve("perseus/io/TextOutput.class")),
                "Normal compilation should provision TextOutput automatically");

        String output = runClass(clientDir, "gnb.perseus.programs.StdlibTextOutputClient");
        assertEquals("42 3.5 Hello o", output.trim(),
                "Builtin output names should work through the automatically provisioned TextOutput unit");
    }

    @Test
    public void stdlib_textoutput_helper_reduction_test() throws Exception {
        Path outDir = Files.createTempDirectory(BUILD_DIR, "stdlib-textoutput-jasmin");
        Path jasminFile = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/TextOutput.alg",
                "perseus/io",
                "TextOutput",
                outDir,
                java.util.List.of(outDir),
                false);
        String jasminSource = Files.readString(jasminFile);

        assertTrue(jasminSource.contains("invokestatic gnb/perseus/runtime/Channels/outString(ILjava/lang/String;)V"),
                "TextOutput should still use the shared dynamic channel write primitive");
        assertTrue(jasminSource.contains("invokestatic java/lang/Integer/toString(I)Ljava/lang/String;"),
                "Integer output should now format through ordinary Java interop in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Double/toString(D)Ljava/lang/String;"),
                "Real output should now format through ordinary Java interop in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/String/valueOf(Ljava/lang/Object;)Ljava/lang/String;"),
                "TextOutput should now render A-format descriptors directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Integer/parseInt(Ljava/lang/String;)I"),
                "TextOutput should now parse Aw and nX widths directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic gnb/perseus/runtime/TextFormatSupport/formatOne(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"),
                "TextOutput should delegate only the remaining hard single-value descriptors to TextFormatSupport");
        assertFalse(jasminSource.contains("Channels/outInteger"),
                "TextOutput should no longer depend on the dedicated Channels.outInteger helper");
        assertFalse(jasminSource.contains("Channels/outReal"),
                "TextOutput should no longer depend on the dedicated Channels.outReal helper");
        assertFalse(jasminSource.contains("Channels/outTerminator"),
                "TextOutput should no longer depend on the dedicated Channels.outTerminator helper");
        assertFalse(jasminSource.contains("TextFormatSupport/format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"),
                "TextOutput should no longer delegate whole-format parsing back to TextFormatSupport");
    }

    @Test
    public void automatic_stdlib_textinput_test() throws Exception {
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-textinput-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_textinput_client.alg",
                "gnb/perseus/programs",
                "StdlibTextInputClient",
                clientDir);
        PerseusCompiler.assemble(clientJasmin, clientDir);

        assertTrue(Files.exists(clientDir.resolve("perseus/io/TextInput.class")),
                "Normal compilation should provision TextInput automatically");

        String output = runClassWithInput(clientDir, "gnb.perseus.programs.StdlibTextInputClient", "12\n3.5\ne\n");
        assertEquals("12,3.5,1", output.trim(),
                "Builtin input names should work through the automatically provisioned TextInput unit");
    }

    @Test
    public void stdlib_textinput_helper_reduction_test() throws Exception {
        Path outDir = Files.createTempDirectory(BUILD_DIR, "stdlib-textinput-jasmin");
        Path jasminFile = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/TextInput.alg",
                "perseus/io",
                "TextInput",
                outDir,
                java.util.List.of(outDir),
                false);
        String jasminSource = Files.readString(jasminFile);

        assertTrue(jasminSource.contains("invokestatic gnb/perseus/runtime/Channels/ininteger(I)I"),
                "TextInput should still use the shared low-level integer read primitive");
        assertTrue(jasminSource.contains("invokestatic gnb/perseus/runtime/Channels/inreal(I)D"),
                "TextInput should still use the shared low-level real read primitive");
        assertTrue(jasminSource.contains("invokestatic gnb/perseus/runtime/Channels/inToken(I)Ljava/lang/String;"),
                "TextInput should now use the shared token read primitive for A-format descriptors");
        assertFalse(jasminSource.contains("Channels/informatKinds"),
                "TextInput should no longer depend on the old Channels.informatKinds helper");
        assertFalse(jasminSource.contains("Channels/informatValues"),
                "TextInput should no longer depend on the old Channels.informatValues helper");
        assertFalse(jasminSource.contains("Channels/informatValuesInto"),
                "TextInput should no longer depend on the old Channels.informatValuesInto helper");
    }

    @Test
    public void automatic_stdlib_faults_test() throws Exception {
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "stdlib-faults-client");
        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/stdlib/stdlib_fault_client.alg",
                "gnb/perseus/programs",
                "StdlibFaultClient",
                clientDir);
        PerseusCompiler.assemble(clientJasmin, clientDir);

        assertTrue(Files.exists(clientDir.resolve("perseus/runtime/Faults.class")),
                "Normal compilation should provision Faults automatically");
        assertFalse(Files.exists(clientDir.resolve("gnb/perseus/runtime/FaultSupport.class")),
                "Faults should no longer require the old Java bridge helper");

        String output = runClass(clientDir, "gnb.perseus.programs.StdlibFaultClient");
        assertEquals("1", output.trim(),
                "Builtin fault should work through the automatically provisioned Faults unit");
    }
}
