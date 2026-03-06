// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @author Greg Bonney
 *
 */
public class AntlrAlgolListenerTest {

	private static final Path BUILD_DIR = Paths.get("build/test-algol");

	@Test
	public void hello() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/hello.alg", "gnb/jalgol/programs", "Hello", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println(jasminSource);

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Hello"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V"),
				"Output should emit outstring call");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run and capture output
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Hello");
		System.out.println("Program output: [" + output + "]");
		assertEquals("Hello World", output.trim());
	}

	@Test
	public void primer1() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primer1.alg", "gnb/jalgol/programs", "Primer1", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println(jasminSource);

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Primer1"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("dstore"),
				"Output should contain dstore instructions for real variable assignment");
		assertTrue(jasminSource.contains("ddiv"),
				"Output should contain ddiv for real division");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — primer1 has no output, just verify it executes without error
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Primer1");
		assertEquals("", output.trim(), "primer1 should produce no output");
	}

	@Test
	public void primer2() throws Exception {
		// Compile Algol source to Jasmin (infinite loop — run briefly to confirm no crash)
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primer2.alg", "gnb/jalgol/programs", "Primer2", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER2 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER2 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Primer2"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("AA:"),
				"Output should contain the label AA");
		assertTrue(jasminSource.contains("goto AA"),
				"Output should contain goto AA");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run briefly (infinite loop) — should not crash before timeout
		String output = runClassWithTimeout(BUILD_DIR, "gnb.jalgol.programs.Primer2", 2000);
		// For infinite loop, output should be empty and process killed by timeout
		assertEquals("", output.trim(), "Infinite loop should produce no output before timeout");
	}

	@Test
	public void primer3() throws Exception {
		// Compile Algol source to Jasmin (loops 1000 times then stops)
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primer3.alg", "gnb/jalgol/programs", "Primer3", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER3 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER3 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Primer3"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("AA:"),
				"Output should contain the label AA");
		assertTrue(jasminSource.contains("if_icmplt"),
				"Output should contain integer comparison");
		assertTrue(jasminSource.contains("istore"),
				"Output should contain istore for integer variable");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — should terminate after 1000 iterations
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Primer3");
		assertEquals("", output.trim(), "primer3 should produce no output");
	}

	@Test
	public void primer5() throws Exception {
		// Compile Algol source to Jasmin (approximation of e via Taylor series)
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primer5.alg", "gnb/jalgol/programs", "Primer5", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER5 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER5 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Primer5"),
				"Output should declare the correct class");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — should print an approximation of e
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Primer5");
		System.out.println("primer5 output: [" + output + "]");
		assertTrue(output.trim().startsWith("2.718"), "primer5 should output an approximation of e (≈ 2.718...)");
	}

	@Test
	public void boolean_test() throws Exception {
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/boolean.alg", "gnb/jalgol/programs", "Boolean", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== BOOLEAN JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END BOOLEAN ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — should print "true"
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Boolean");
		System.out.println("boolean output: [" + output + "]");
		assertEquals("true", output.trim());
	}

	@Test
	public void array_test() throws Exception {
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/array.alg", "gnb/jalgol/programs", "ArrayTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== ARRAY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END ARRAY ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — nArr[5]=5, nArr[3]=0 (uninitialized)
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.ArrayTest");
		System.out.println("array output: [" + output + "]");
		assertEquals("50", output.trim());
	}

	@Test
	public void primer4() throws Exception {
		// Compile Algol source to Jasmin (for loop with 1000 iterations)
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primer4.alg", "gnb/jalgol/programs", "Primer4", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER4 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER4 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/jalgol/programs/Primer4"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("loop_"),
				"Output should contain loop labels");
		assertTrue(jasminSource.contains("endfor_"),
				"Output should contain endfor labels");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — should terminate after 1000 iterations, output final x and y
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.Primer4");
		assertFalse(output.trim().isEmpty(), "primer4 should produce output");
		// TODO: assert correct values, approximately 0.1545 and -0.988
	}

	@Test
	public void oneton_test() throws Exception {
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/oneton.alg", "gnb/jalgol/programs", "OnetonTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== ONETON JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END ONETON ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains(".method public static oneton(I)I"),
				"Should declare integer procedure oneton");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — loop prints 1..12 each followed by newline, then M=oneton(12)=24
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.OnetonTest");
		System.out.println("oneton output: [" + output + "]");
		String expected = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n24";
		assertEquals(expected, output.trim());
	}

	@Test
	public void primes_test() throws Exception {
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/primes.alg", "gnb/jalgol/programs", "PrimesTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.err.println("=== PRIMES JASMIN ===");
		System.err.println(jasminSource);
		System.err.println("=== END PRIMES ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		if (jasminSource.startsWith("ERROR")) {
			throw new RuntimeException("Compilation error: " + jasminSource);
		}
		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("newarray boolean"),
				"Should allocate boolean array for sieve");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — sieve prints primes below 1000, 10 per line
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.PrimesTest");
		System.err.println("primes output: [" + output + "]");
		// Check first few primes
		assertTrue(output.contains("2 3 5 7 11 13 17 19 23 29"),
				"Should contain first 10 primes");
		// Check last prime below 1000 is 997
		assertTrue(output.trim().endsWith("997"), "Should end with 997");
	}

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

		Path jasminFile = AntlrAlgolListener.compileToFile(
				tempFile.toString(), "gnb/jalgol/programs", "MathFunctionsTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		
		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt"),
				"Should call Math.sqrt");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/abs"),
				"Should call Math.abs");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/sin"),
				"Should call Math.sin");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/log"),
				"Should call Math.log for ln");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/exp"),
				"Should call Math.exp");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/atan"),
				"Should call Math.atan for arctan");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/floor"),
				"Should call Math.floor for entier");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/signum"),
				"Should call Math.signum for sign");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run and check output
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.MathFunctionsTest");
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
	public void pi_test() throws Exception {
		Path jasminFile = AntlrAlgolListener.compileToFile(
				"test/algol/pi.alg", "gnb/jalgol/programs", "PiTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt"),
				"Should call Math.sqrt");

		// Assemble to .class
		AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

		// Run — pi.alg uses Archimedes method to approximate pi
		String output = runClass(BUILD_DIR, "gnb.jalgol.programs.PiTest");
		System.out.println("pi output: [" + output + "]");
		
		// Check that output contains approximations of pi improving over iterations
		assertTrue(output.contains("> pi >"), "Should contain pi approximation format");
		// Final approximation should be close to pi (3.14159...)
		assertTrue(output.contains("3.14"), "Should approximate pi starting with 3.14");
	}

	private static String runClass(Path classDir, String className) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClass: " + cmd);
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin
		String output = new String(p.getInputStream().readAllBytes());
		int exitCode = p.waitFor();
		System.out.println("runClass: exit=" + exitCode + " output=[" + output + "]");
		return output;
	}

	private static String runClassWithTimeout(Path classDir, String className, long timeoutMs) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClassWithTimeout: " + cmd + " timeout=" + timeoutMs + "ms");
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin
		// Wait with timeout BEFORE reading output — readAllBytes() blocks until the process exits,
		// so it must not be called before we've killed the process if it runs forever.
		boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		if (!finished) {
			p.destroyForcibly();
			p.waitFor(); // ensure the process is fully dead before we read its output stream
			System.out.println("runClassWithTimeout: process killed after timeout");
		} else {
			System.out.println("runClassWithTimeout: process finished early, exit=" + p.exitValue());
		}
		String output = new String(p.getInputStream().readAllBytes());
		return output;
	}

	private static String runClassWithInput(Path classDir, String className, String input) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClassWithInput: " + cmd + " input=[" + input.replace("\n", "\\n") + "]");
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		// Write input to subprocess stdin
		try (java.io.OutputStream os = p.getOutputStream()) {
			os.write(input.getBytes());
		}
		String output = new String(p.getInputStream().readAllBytes());
		int exitCode = p.waitFor();
		System.out.println("runClassWithInput: exit=" + exitCode + " output=[" + output + "]");
		return output;
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
        
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/output_test.alg", "gnb/jalgol/programs",
                "OutputTestM11B", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        
        // Check compilation succeeded - no ERROR in output
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        
        // Verify outchar generates String.charAt invocation
        assertTrue(jasminSource.contains("invokevirtual java/lang/String/charAt(I)C"),
                "Should generate String.charAt for outchar");
        
        // Verify outterminator and outinteger generate PrintStream.print
        assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/print(I)V"),
                "Should generate PrintStream.print(I) for outinteger");
        assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V"),
                "Should generate PrintStream.print(String) for outterminator");
    }

    @Test
    public void channel_aware_stream_selection_test() throws Exception {
        // Test channel-aware stream selection for output procedures
        // Channel 0 -> System.err, Channel 1 -> System.out
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/channel_test.alg", "gnb/jalgol/programs",
                "ChannelTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        
        System.out.println("=== CHANNEL TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END CHANNEL TEST ===");
        
        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");
        
        // Verify channel 0 generates System.err references
        assertTrue(jasminSource.contains("getstatic java/lang/System/err Ljava/io/PrintStream;"),
                "Channel 0 should use System.err");
        
        // Verify channel 1 generates System.out references
        assertTrue(jasminSource.contains("getstatic java/lang/System/out Ljava/io/PrintStream;"),
                "Channel 1 should use System.out");
        
        // Count occurrences to ensure proper stream selection
        int errCount = countOccurrences(jasminSource, "getstatic java/lang/System/err");
        int outCount = countOccurrences(jasminSource, "getstatic java/lang/System/out");
        
        // We have 5 channel 0 calls and 5 channel 1 calls in the test
        assertEquals(5, errCount, "Should have 5 System.err references for channel 0");
        assertEquals(5, outCount, "Should have 5 System.out references for channel 1");
    }
    
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    @Test
    public void string_test() throws Exception {
        // Test string variable support from Milestone 11C.2
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/string_test.alg", "gnb/jalgol/programs",
                "StringTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== STRING TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END STRING TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify string variable initialization
        assertTrue(jasminSource.contains("ldc \"\"\nastore"),
                "Should initialize string variables to empty string");

        // Verify string literal loading
        assertTrue(jasminSource.contains("ldc \"Hello, World!\""),
                "Should load string literals");

        // Verify string variable storage
        assertTrue(jasminSource.contains("astore"),
                "Should generate astore for string variable assignment");

        // Verify string variable loading
        assertTrue(jasminSource.contains("aload"),
                "Should generate aload for string variable access");

        // Assemble to .class
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

        // Run and check output
        String output = runClass(BUILD_DIR, "gnb.jalgol.programs.StringTest");
        System.out.println("string test output: [" + output + "]");
        assertEquals("Hello, World! ", output, "Should output the string with space terminator");
    }

    @Test
    public void instring_test() throws Exception {
        // Test instring procedure from Milestone 11C.3
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/instring_test.alg", "gnb/jalgol/programs",
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
        assertTrue(jasminSource.contains("astore"),
                "Should store result in string variable");

        // Assemble to .class
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

        // Run with input and check output
        String output = runClassWithInput(BUILD_DIR, "gnb.jalgol.programs.InstringTest", "Test Input String\n");
        System.out.println("instring test output: [" + output + "]");
        assertEquals("Test Input String", output.trim(), "Should read and output the input string");
    }

    @Test
    public void stop_fault_test() throws Exception {
        // Test stop and fault procedures from Milestone 11D
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/stop_fault_test.alg", "gnb/jalgol/programs",
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
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

        // Note: We don't run the program since stop and fault exit the JVM
        // Just verify that compilation and Jasmin generation work correctly
    }

    @Test
    public void constants_test() throws Exception {
        // Test environmental constants from Milestone 11E
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/constants_test.alg", "gnb/jalgol/programs",
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
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);

        // Run and check output
        String output = runClass(BUILD_DIR, "gnb.jalgol.programs.ConstantsTest");
        System.out.println("constants test output: [" + output + "]");
        
        // Verify the output contains the expected values
        assertTrue(output.contains("maxreal = 1.7976931348623157E308"), "Should output maxreal value");
        assertTrue(output.contains("minreal = 4.9E-324"), "Should output minreal value");
        assertTrue(output.contains("epsilon = 2.2250738585072014E-308"), "Should output epsilon value");
        assertTrue(output.contains("maxint = 2147483647"), "Should output maxint value");
    }

    @Test
    public void pi_programs_test() throws Exception {
        // Test that pi_simple.alg (Archimedes pi approximation) compiles and runs from Milestone 11F
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/pi_simple.alg", "gnb/jalgol/programs",
                "PiTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "pi_simple.alg should compile without error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Verify key code patterns are present
        assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt"),
                "Should call Math.sqrt for the Archimedes b step");
        assertTrue(jasminSource.contains(".method public static archimedesa"),
                "Should define archimedesa procedure as a static method");
        assertTrue(jasminSource.contains(".method public static archimedesb"),
                "Should define archimedesb procedure as a static method");
        assertTrue(jasminSource.contains(".method public static main"),
                "Should have main method");

        // Assemble and run
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.jalgol.programs.PiTest");

        // Verify output contains pi approximations (10 iterations of the Archimedes method)
        assertTrue(output.contains(" > pi > "), "Should output Archimedes bounds in '... > pi > ...' format");
        assertTrue(output.contains("3.14"), "Should approximate pi to at least 3.14...");
    }

    @Test
    public void sqrt_negative_test() throws Exception {
        // Test that sqrt of negative number returns NaN (documented choice) from Milestone 11F
        Path jasminFile = AntlrAlgolListener.compileToFile(
                "test/algol/sqrt_negative_test.alg", "gnb/jalgol/programs",
                "SqrtNegativeTest", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== SQRT NEGATIVE TEST JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END SQRT NEGATIVE TEST ===");

        // Check compilation succeeded
        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error");

        // Verify sqrt call generation
        assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt(D)D"),
                "Should call Math.sqrt");

        // Assemble and run
        AntlrAlgolListener.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.jalgol.programs.SqrtNegativeTest");
        System.out.println("sqrt negative test output: [" + output + "]");

        // Verify output contains NaN (Java's Math.sqrt returns NaN for negative inputs)
        assertTrue(output.contains("NaN"), "sqrt of negative number should return NaN");
    }


} 
