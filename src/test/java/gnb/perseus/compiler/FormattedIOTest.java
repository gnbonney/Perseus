package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class FormattedIOTest extends CompilerTest {

    @Test
    public void outformat_basic_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/outformat_basic.alg",
                "gnb/perseus/programs",
                "OutformatBasic",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== OUTFORMAT BASIC JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END OUTFORMAT BASIC ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");
        assertFalse(jasminSource.contains("gnb/perseus/runtime/TextFormatSupport/format"),
                "Outformat should no longer emit a direct compiler-side call to TextFormatSupport");
        assertEquals(1, countOccurrences(jasminSource, "invokestatic perseus/io/TextOutput/formatvalues(Ljava/lang/String;[Ljava/lang/Object;III)Ljava/lang/String;"),
                "Outformat should route formatted rendering through the compiled TextOutput stdlib unit");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.OutformatBasic");
        assertEquals("   42     3.14      Algol", output,
                "outformat should produce width-aware formatted stdout output");
    }

    @Test
    public void outformat_string_channel_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/outformat_string_channel.alg",
                "gnb/perseus/programs",
                "OutformatStringChannel",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== OUTFORMAT STRING CHANNEL JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END OUTFORMAT STRING CHANNEL ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.OutformatStringChannel");
        assertEquals("[  42   3.14     Hi]", output.trim(),
                "outformat should support sprintf-style formatting through openstring");
    }

    @Test
    public void outformat_file_channel_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/outformat_file_channel.alg",
                "gnb/perseus/programs",
                "OutformatFileChannel",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== OUTFORMAT FILE CHANNEL JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END OUTFORMAT FILE CHANNEL ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.OutformatFileChannel");
        assertEquals("  27 Perseus", output,
                "outformat should support formatted output through file-backed channels");
    }

    @Test
    public void outformat_extended_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/outformat_extended.alg",
                "gnb/perseus/programs",
                "OutformatExtended",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== OUTFORMAT EXTENDED JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END OUTFORMAT EXTENDED ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.OutformatExtended");
        assertEquals("  007    1.23e+03\nok   true", output,
                "outformat should support zero-padded integer, scientific, logical, spacing, and line-break descriptors");
    }

    @Test
    public void outformat_dynamic_format_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/outformat_dynamic_format.alg",
                "gnb/perseus/programs",
                "OutformatDynamicFormat",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not require a string-literal format expression");
        assertEquals(1, countOccurrences(jasminSource, "invokestatic perseus/io/TextOutput/formatvalues(Ljava/lang/String;[Ljava/lang/Object;III)Ljava/lang/String;"),
                "Dynamic outformat should still route through the compiled TextOutput stdlib unit");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.OutformatDynamicFormat");
        assertEquals("   42     3.14", output,
                "outformat should accept a runtime string expression for the format");
    }

    @Test
    public void informat_basic_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/informat_basic.alg",
                "gnb/perseus/programs",
                "InformatBasic",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== INFORMAT BASIC JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END INFORMAT BASIC ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");
        assertFalse(jasminSource.contains("gnb/perseus/runtime/Channels/informatValues"),
                "Informat should no longer emit a direct compiler-side call to Channels.informatValues");
        assertEquals(1, countOccurrences(jasminSource, "invokestatic perseus/io/TextInput/informatvalues(ILjava/lang/String;[Ljava/lang/Object;II)V"),
                "Informat should route value loading through the compiled TextInput stdlib unit");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClassWithInput(BUILD_DIR, "gnb.perseus.programs.InformatBasic", " 42  3.1 Hello\n");
        assertEquals("42 3.1 Hello", output.trim(),
                "informat should parse formatted input into integer, real, and string variables");
    }

    @Test
    public void informat_file_channel_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/informat_file_channel.alg",
                "gnb/perseus/programs",
                "InformatFileChannel",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== INFORMAT FILE CHANNEL JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END INFORMAT FILE CHANNEL ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.InformatFileChannel");
        assertEquals("Hello:42:3.1", output.trim(),
                "informat should parse through file channels via shared runtime support");
    }

    @Test
    public void mixed_string_channel_format_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/mixed_string_channel_format.alg",
                "gnb/perseus/programs",
                "MixedStringChannelFormat",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== MIXED STRING CHANNEL FORMAT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END MIXED STRING CHANNEL FORMAT ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.MixedStringChannelFormat");
        assertEquals("A=  7   2.5;9", output.trim(),
                "String channels should support mixed formatted and unformatted output in one buffer");
    }

    @Test
    public void mixed_file_channel_format_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/mixed_file_channel_format.alg",
                "gnb/perseus/programs",
                "MixedFileChannelFormat",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== MIXED FILE CHANNEL FORMAT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END MIXED FILE CHANNEL FORMAT ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.MixedFileChannelFormat");
        assertEquals("N=  5   xy!", output.trim(),
                "File channels should support mixed formatted and unformatted output in one stream");
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
