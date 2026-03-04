// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		String output = new String(p.getInputStream().readAllBytes());
		boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		if (!finished) {
			p.destroyForcibly();
			System.out.println("runClassWithTimeout: process killed after timeout");
		} else {
			System.out.println("runClassWithTimeout: process finished early, exit=" + p.exitValue());
		}
		return output;
	}

}
