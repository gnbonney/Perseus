package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import gnb.perseus.postprocess.FixLimits;
import java.util.List;

import org.junit.jupiter.api.Test;

public class StringsAndIOTest extends CompilerTest {

    @Test
	public void math_functions_test() throws Exception {
		// Test all math functions from Milestone 11A
		String algolSrc = """
begin
	real x, y;
	integer i;
	
	comment Test sqrt;
	x := sqrt(4.0);
	outreal(1, x);
	outstring(1, "\\n");
	
	comment Test abs with negative;
	x := abs(0.0 - 3.5);
	outreal(1, x);
	outstring(1, "\\n");
	
	comment Test iabs with negative integer;
	i := iabs(0 - 42);
	outinteger(1, i);
	outstring(1, "\\n");
	
	comment Test sign;
	i := sign(5.0);
	outinteger(1, i);
	outstring(1, " ");
	i := sign(0.0 - 3.0);
	outinteger(1, i);
	outstring(1, " ");
	i := sign(0.0);
	outinteger(1, i);
	outstring(1, "\\n");
	
	comment Test entier (floor);
	i := entier(3.7);
	outinteger(1, i);
	outstring(1, " ");
	i := entier(0.0 - 2.3);
	outinteger(1, i);
	outstring(1, "\\n");
	
	comment Test sin, cos (using known values);
	x := sin(0.0);
	outreal(1, x);
	outstring(1, "\\n");
	
	comment Test ln and exp (inverse functions);
	x := ln(exp(1.0));
	outreal(1, x);
	outstring(1, "\\n");
	
	comment Test arctan;
	x := arctan(0.0);
	outreal(1, x);
	outstring(1, "\\n")
end
""";

		// Write source to a temporary file
		Path tempFile = BUILD_DIR.resolve("math_test_temp.alg");
		Files.createDirectories(BUILD_DIR);
		Files.writeString(tempFile, algolSrc);

		Path jasminFile = PerseusCompiler.compileToFile(
				tempFile.toString(), "gnb/perseus/programs", "MathFunctionsTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		
		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/sqrt"),
				"Should call MathEnv.sqrt");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/abs"),
				"Should call MathEnv.abs");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/sin"),
				"Should call MathEnv.sin");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/ln"),
				"Should call MathEnv.ln");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/exp"),
				"Should call MathEnv.exp");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/arctan"),
				"Should call MathEnv.arctan");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/entier"),
				"Should call MathEnv.entier");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/iabs"),
				"Should call MathEnv.iabs");
		assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/sign"),
				"Should call MathEnv.sign");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run and check output
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.MathFunctionsTest");
		System.out.println("math_functions output: [" + output + "]");
		
		// Check specific values
		assertTrue(output.contains("2.0"), "sqrt(4) should be 2.0");
		assertTrue(output.contains("3.5"), "abs(-3.5) should be 3.5");
		assertTrue(output.contains("42"), "iabs(-42) should be 42");
		assertTrue(output.contains("1 -1 0"), "sign tests should produce 1 -1 0");
		assertTrue(output.contains("3 -3"), "entier tests should produce 3 -3");
		assertTrue(output.contains("0.0"), "sin(0) and arctan(0) should be 0.0");
		// ln(exp(1)) should be very close to 1.0
		assertTrue(output.contains("1.0"), "ln(exp(1)) should be approximately 1.0");
	}
    
    @Test
    public void output_procedures_test() throws Exception {
        // Test outchar and outterminator procedures
        String algolSrc = """
begin
    outinteger(1, 42);
    outterminator(1);
    outchar(1, "World", 0);
    stop
end
""";
        
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/output_test.alg", "gnb/perseus/programs",
                "OutputTestM11B", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        
        // Check compilation succeeded - no ERROR in output
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outinteger(II)V"),
                "Should call TextOutput.outinteger");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outterminator(I)V"),
                "Should call TextOutput.outterminator");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outreal(ID)V"),
                "Should call TextOutput.outreal");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outchar(ILjava/lang/String;I)V"),
                "Should call TextOutput.outchar");
    }

    @Test
    public void channel_aware_stream_selection_test() throws Exception {
        // Test channel-aware stream selection for output procedures
        // Channel 0 -> System.err, Channel 1 -> System.out
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/channel_test.alg", "gnb/perseus/programs",
                "ChannelTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        
        System.out.println("=== CHANNEL TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CHANNEL TEST ===");
        
        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");
        
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outstring(ILjava/lang/String;)V"),
                "Channel-aware outstring calls should route through TextOutput");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outinteger(II)V"),
                "Channel-aware outinteger calls should route through TextOutput");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outreal(ID)V"),
                "Channel-aware outreal calls should route through TextOutput");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outchar(ILjava/lang/String;I)V"),
                "Channel-aware outchar calls should route through TextOutput");
        assertTrue(jasminSource.contains("invokestatic perseus/io/TextOutput/outterminator(I)V"),
                "Channel-aware outterminator calls should route through TextOutput");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        ProcessResult result = runClassCapture(BUILD_DIR, "gnb.perseus.programs.ChannelTest");
        assertEquals(0, result.exitCode(), "channel_test should run successfully");
        assertEquals("INFO: This goes to stdout992.71Y ", result.stdout(),
                "Channel 1 output should be written to stdout");
        assertEquals("ERROR: This goes to stderr423.14A ", result.stderr(),
                "Channel 0 output should be written to stderr");
    }

    @Test
    public void string_test() throws Exception {
        // Test string variable support from Milestone 11C.2
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/string_test.alg", "gnb/perseus/programs",
                "StringTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== STRING TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END STRING TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify string variable initialization
        assertTrue(jasminSource.contains("ldc \"\"") && jasminSource.contains("putstatic"),
                "Should initialize string variables to empty string with putstatic");

        // Verify string literal loading
        assertTrue(jasminSource.contains("ldc \"Hello, World!\""),
                "Should load string literals");

        // Verify string variable storage
        assertTrue(jasminSource.contains("putstatic"),
                "Should generate putstatic for string variable assignment");

        // Verify string variable loading
        assertTrue(jasminSource.contains("getstatic"),
                "Should generate getstatic for string variable access");

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run and check output
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.StringTest");
        System.out.println("string test output: [" + output + "]");
        assertEquals("Hello, World! ", output, "Should output the string with space terminator");
    }

    @Test
    public void instring_test() throws Exception {
        // Test instring procedure from Milestone 11C.3
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/instring_test.alg", "gnb/perseus/programs",
                "InstringTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== INSTRING TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END INSTRING TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify instring call generation
        assertTrue(jasminSource.contains("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;"),
                "Should call Scanner.nextLine() for instring");
        assertTrue(jasminSource.contains("putstatic"),
                "Should store result in string variable with putstatic");

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run with input and check output
        String output = runClassWithInput(BUILD_DIR, "gnb.perseus.programs.InstringTest", "Test Input String\n");
        System.out.println("instring test output: [" + output + "]");
        assertEquals("Test Input String", output.trim(), "Should read and output the input string");
    }

    @Test
    public void stop_fault_test() throws Exception {
        // Test stop and fault procedures from Milestone 11D
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/stop_fault_test.alg", "gnb/perseus/programs",
                "StopFaultTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== STOP FAULT TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END STOP FAULT TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify stop call generation
        assertTrue(jasminSource.contains("iconst_0"),
                "Should load exit code 0 for stop");
        assertTrue(jasminSource.contains("invokestatic java/lang/System/exit(I)V"),
                "Should call System.exit() for stop");

        // Verify fault call generation
        assertTrue(jasminSource.contains("getstatic java/lang/System/err Ljava/io/PrintStream;"),
                "Should get System.err for fault");
        assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V"),
                "Should call println for fault message");
        assertTrue(jasminSource.contains("iconst_1"),
                "Should load exit code 1 for fault");

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Note: We don't run the program since stop and fault exit the JVM
        // Just verify that compilation and Jasmin generation work correctly
    }

    @Test
    public void constants_test() throws Exception {
        // Test environmental constants from Milestone 11E
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/constants_test.alg", "gnb/perseus/programs",
                "ConstantsTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== CONSTANTS TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CONSTANTS TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify constant loading
        assertTrue(jasminSource.contains("ldc2_w " + Double.MAX_VALUE),
                "Should load Double.MAX_VALUE for maxreal");
        assertTrue(jasminSource.contains("ldc2_w " + Double.MIN_VALUE),
                "Should load Double.MIN_VALUE for minreal");
        assertTrue(jasminSource.contains("ldc2_w " + Double.MIN_NORMAL),
                "Should load machine epsilon for epsilon");
        assertTrue(jasminSource.contains("ldc " + Integer.MAX_VALUE),
                "Should load Integer.MAX_VALUE for maxint");

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run and check output
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ConstantsTest");
        System.out.println("constants test output: [" + output + "]");
        
        // Verify the output contains the expected values
        assertTrue(output.contains("maxreal = 1.7976931348623157E308"), "Should output maxreal value");
        assertTrue(output.contains("minreal = 4.9E-324"), "Should output minreal value");
        assertTrue(output.contains("epsilon = 2.2250738585072014E-308"), "Should output epsilon value");
        assertTrue(output.contains("maxint = 2147483647"), "Should output maxint value");
    }

     @Test
    public void sqrt_negative_test() throws Exception {
        // Test that sqrt of negative number returns NaN (documented choice) from Milestone 11F
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/sqrt_negative_test.alg", "gnb/perseus/programs",
                "SqrtNegativeTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== SQRT NEGATIVE TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END SQRT NEGATIVE TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        // Verify sqrt call generation
        assertTrue(jasminSource.contains("invokestatic perseus/lang/MathEnv/sqrt(D)D"),
                "Should call MathEnv.sqrt");

        // Assemble and run
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.SqrtNegativeTest");
        System.out.println("sqrt negative test output: [" + output + "]");

        // Verify output contains NaN (Java's Math.sqrt returns NaN for negative inputs)
        assertTrue(output.contains("NaN"), "sqrt of negative number should return NaN");
    }

    @Test
    public void string_output_test() throws Exception {
	// Milestone 18: string variable declaration, assignment, length, substring, concat, s[i] access, s[i] := mutation
	Path jasminFile = PerseusCompiler.compileToFile(
		"test/algol/io/string_output.alg", "gnb/perseus/programs", "StringOutput", BUILD_DIR);
	String jasminSource = Files.readString(jasminFile);

	System.out.println("=== STRING OUTPUT JASMIN ===");
	System.out.println(jasminSource);
	System.out.println("=== END STRING OUTPUT ===");

	// Check compilation succeeded
	assertFalse(jasminSource.startsWith("ERROR"),
		"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

	// Should use Strings.length() for builtin length(s)
	assertTrue(jasminSource.contains("invokestatic perseus/text/Strings/length(Ljava/lang/String;)I"),
		"Should use Strings.length() for length(s)");

	// Should use Strings.substring() for builtin substring(), and String.substring() for character access.
	assertTrue(jasminSource.contains("invokestatic perseus/text/Strings/substring(Ljava/lang/String;II)Ljava/lang/String;"),
		"Should use Strings.substring() for substring()");
	assertTrue(jasminSource.contains("invokevirtual java/lang/String/substring(II)Ljava/lang/String;"),
		"Should use String.substring(II) for character access");

	// Should use StringBuilder for s[i] := mutation
	assertTrue(jasminSource.contains("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;"),
		"Should use StringBuilder.toString() for character mutation");

	// Should use Strings.concat for concat()
	assertTrue(jasminSource.contains("invokestatic perseus/text/Strings/concat(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
		"Should use Strings.concat() for concat()");

	// Assemble to .class
	PerseusCompiler.assemble(jasminFile, BUILD_DIR);

	// Run and check output
	String output = runClass(BUILD_DIR, "gnb.perseus.programs.StringOutput");
	System.out.println("string_output output: [" + output + "]");
	assertEquals("Hello, world! 13 H world Hello, World! Hello, World!!!!", output.trim(),
		"Should output all string operations correctly");
    }

    @Test
    public void file_channel_output_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/file_channel_output.alg", "gnb/perseus/programs",
                "FileChannelOutput", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== FILE CHANNEL OUTPUT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END FILE CHANNEL OUTPUT ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.FileChannelOutput");
        assertEquals("Hello, file channel!", output.trim(),
                "File channel support should write to a file, read it back, and print the recovered line");
    }

    @Test
    public void string_channel_output_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/string_channel_output.alg", "gnb/perseus/programs",
                "StringChannelOutput", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== STRING CHANNEL OUTPUT JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END STRING CHANNEL OUTPUT ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.StringChannelOutput");
        assertEquals("[alpha 42]", output.trim(),
                "String channel support should accumulate output into the target string variable");
    }

    @Test
    public void file_channel_io_exception_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/io/file_channel_io_exception.alg", "gnb/perseus/programs",
                "FileChannelIOException", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== FILE CHANNEL IOEXCEPTION JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END FILE CHANNEL IOEXCEPTION ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.FileChannelIOException");
        assertEquals("1", output.trim(),
                "Opening an invalid file channel should raise IOException and recover through the handler");
    }
}
