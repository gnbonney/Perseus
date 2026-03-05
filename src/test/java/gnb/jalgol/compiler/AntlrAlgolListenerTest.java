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

}
