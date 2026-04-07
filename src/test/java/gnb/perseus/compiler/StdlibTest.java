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
        assertTrue(Files.exists(stdlibClasses.resolve("perseus/io/Channels.class")),
                "The stdlib builder should compile the stdlib-owned Channels unit");
        assertTrue(Files.exists(stdlibClasses.resolve("gnb/perseus/compiler/Thunk.class")),
                "The stdlib builder should provision the generic Thunk support class needed by stdlib-owned string channels");
        assertFalse(Files.exists(stdlibClasses.resolve("gnb/perseus/runtime/Channels.class")),
                "The stdlib builder should no longer copy the old runtime Channels helper into stdlib outputs");
        assertFalse(Files.exists(stdlibClasses.resolve("gnb/perseus/runtime/TextFormatSupport.class")),
                "The stdlib builder should no longer copy TextFormatSupport into stdlib outputs");
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
        compileStdlibChannels(outDir);
        Path jasminFile = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/TextOutput.alg",
                "perseus/io",
                "TextOutput",
                outDir,
                java.util.List.of(outDir),
                false);
        String jasminSource = Files.readString(jasminFile);

        assertTrue(jasminSource.contains("invokestatic perseus/io/Channels/outstring(ILjava/lang/String;)V"),
                "TextOutput should now use the stdlib-owned dynamic channel write primitive");
        assertTrue(jasminSource.contains("invokestatic java/lang/Integer/toString(I)Ljava/lang/String;"),
                "Integer output should now format through ordinary Java interop in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Double/toString(D)Ljava/lang/String;"),
                "Real output should now format through ordinary Java interop in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/String/valueOf(Ljava/lang/Object;)Ljava/lang/String;"),
                "TextOutput should now render A-format descriptors directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Integer/parseInt(Ljava/lang/String;)I"),
                "TextOutput should now parse descriptor widths directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Double/parseDouble(Ljava/lang/String;)D"),
                "TextOutput should now parse boxed numeric values directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Math/floor(D)D"),
                "TextOutput should now handle fixed and scientific rounding directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Math/abs(D)D"),
                "TextOutput should now normalize scientific values directly in compiled stdlib code");
        assertFalse(jasminSource.contains("Channels/outInteger"),
                "TextOutput should no longer depend on the dedicated Channels.outInteger helper");
        assertFalse(jasminSource.contains("Channels/outReal"),
                "TextOutput should no longer depend on the dedicated Channels.outReal helper");
        assertFalse(jasminSource.contains("Channels/outTerminator"),
                "TextOutput should no longer depend on the dedicated Channels.outTerminator helper");
        assertFalse(jasminSource.contains("TextFormatSupport"),
                "TextOutput should no longer depend on TextFormatSupport for any descriptor rendering");
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
        compileStdlibChannels(outDir);
        Path jasminFile = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/TextInput.alg",
                "perseus/io",
                "TextInput",
                outDir,
                java.util.List.of(outDir),
                false);
        String jasminSource = Files.readString(jasminFile);

        assertTrue(jasminSource.contains("invokestatic perseus/io/Channels/intoken(I)Ljava/lang/String;"),
                "TextInput should now use the stdlib-owned token read primitive at the runtime boundary");
        assertTrue(jasminSource.contains("invokestatic java/lang/Integer/parseInt(Ljava/lang/String;)I"),
                "TextInput should now parse integer input directly in compiled stdlib code");
        assertTrue(jasminSource.contains("invokestatic java/lang/Double/parseDouble(Ljava/lang/String;)D"),
                "TextInput should now parse real input directly in compiled stdlib code");
        assertFalse(jasminSource.contains("gnb/perseus/runtime/Channels"),
                "TextInput should no longer depend on the old runtime Channels helper");
        assertFalse(jasminSource.contains("Channels/ininteger"),
                "TextInput should no longer depend on the old Channels integer parsing helper");
        assertFalse(jasminSource.contains("Channels/inreal"),
                "TextInput should no longer depend on the old Channels real parsing helper");
        assertFalse(jasminSource.contains("Channels/inchar"),
                "TextInput should no longer depend on the old Channels character parsing helper");
        assertFalse(jasminSource.contains("Channels/informatKinds"),
                "TextInput should no longer depend on the old Channels.informatKinds helper");
        assertFalse(jasminSource.contains("Channels/informatValues"),
                "TextInput should no longer depend on the old Channels.informatValues helper");
        assertFalse(jasminSource.contains("Channels/informatValuesInto"),
                "TextInput should no longer depend on the old Channels.informatValuesInto helper");
    }

    @Test
    public void stdlib_channels_string_channel_ownership_test() throws Exception {
        Path outDir = Files.createTempDirectory(BUILD_DIR, "stdlib-channels-jasmin");
        Path jasminFile = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/Channels.alg",
                "perseus/io",
                "Channels",
                outDir,
                java.util.List.of(outDir),
                false);
        String jasminSource = Files.readString(jasminFile);

        assertTrue(jasminSource.contains("invokeinterface gnb/perseus/compiler/Thunk/get()Ljava/lang/Object; 1"),
                "Channels should now read string-channel contents through the generic thunk target");
        assertTrue(jasminSource.contains("invokeinterface gnb/perseus/compiler/Thunk/set(Ljava/lang/Object;)V 2"),
                "Channels should now update string-channel contents through the generic thunk target");
        assertTrue(jasminSource.contains("putstatic perseus/io/Channels/stringchannels Ljava/lang/Object;"),
                "Channels should now own stdlib-side string-channel state");
        assertFalse(jasminSource.contains("gnb/perseus/runtime/Channels"),
                "Compiled Channels stdlib code should no longer depend on the removed runtime Channels helper");
    }

    private static void compileStdlibChannels(Path outDir) throws Exception {
        Path channelsJasmin = PerseusCompiler.compileToFileInternal(
                "src/main/perseus/stdlib/perseus/io/Channels.alg",
                "perseus/io",
                "Channels",
                outDir,
                java.util.List.of(outDir),
                false);
        PerseusCompiler.assemble(channelsJasmin, outDir);
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
