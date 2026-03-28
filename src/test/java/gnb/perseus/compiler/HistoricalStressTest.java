package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import gnb.perseus.postprocess.FixLimits;

public class HistoricalStressTest extends CompilerTest {

    @Test
	public void primes_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/historical/primes.alg", "gnb/perseus/programs", "PrimesTest", BUILD_DIR);
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
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � sieve prints primes below 1000, 10 per line
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.PrimesTest");
		System.err.println("primes output: [" + output + "]");
		// Check first few primes
		assertTrue(output.contains("2 3 5 7 11 13 17 19 23 29"),
				"Should contain first 10 primes");
		// Check last prime below 1000 is 997
		assertTrue(output.trim().endsWith("997"), "Should end with 997");
	}

	@Test
	public void pi_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/historical/pi.alg", "gnb/perseus/programs", "PiTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt"),
				"Should call Math.sqrt");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � pi.alg uses Archimedes method to approximate pi
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.PiTest");
		System.out.println("pi output: [" + output + "]");
		
		// Check that output contains approximations of pi improving over iterations
		assertTrue(output.contains("> pi >"), "Should contain pi approximation format");
		// Final approximation should be close to pi (3.14159...)
		assertTrue(output.contains("3.14"), "Should approximate pi starting with 3.14");
	}

	@Test
	public void pi2_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/historical/pi2.alg", "gnb/perseus/programs", "Pi2Test", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
				"Compilation should not produce an error: " + jasminSource);
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/sqrt"),
				"Should call Math.sqrt");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � pi2.alg uses Archimedes method with procedures accessing outer variables
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Pi2Test");
		System.out.println("pi2 output: [" + output + "]");
		
		// Check that output contains approximations of pi improving over iterations
		assertTrue(output.contains("> pi >"), "Should contain pi approximation format");
		// Final approximation should be close to pi (3.14159...)
		assertTrue(output.contains("3.14"), "Should approximate pi starting with 3.14");
	}

	@Test
    public void pi_programs_test() throws Exception {
        // Test that pi_simple.alg (Archimedes pi approximation) compiles and runs from Milestone 11F
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/historical/pi_simple.alg", "gnb/perseus/programs",
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
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.PiTest");

        // Verify output contains pi approximations (10 iterations of the Archimedes method)
        assertTrue(output.contains(" > pi > "), "Should output Archimedes bounds in '... > pi > ...' format");
        assertTrue(output.contains("3.14"), "Should approximate pi to at least 3.14...");
    }
    
	@Test
    public void manboy_test() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/historical/manboy.alg", "gnb/perseus/programs", "ManBoy", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		System.out.println("=== MANBOY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END MANBOY ===");

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run FixLimits to verify and fix the generated class family.
		try {
		    FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/ManBoy.class"));
		} catch (Exception e) {
		    throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
		}

		// Run and capture output with a timeout so a bad recursive/codegen path
		// fails fast instead of hanging the test suite indefinitely.
		String output = runClassWithTimeout(BUILD_DIR, "gnb.perseus.programs.ManBoy", 10_000);
		System.out.println("Man or Boy output: [" + output + "]");
		assertEquals("-67.0", output.trim(), "Man or Boy test should return -67.0");
    }

    @Test
    public void recursion_euler_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/historical/recursion_euler.alg", "gnb/perseus/programs", "RecursionEuler", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== RECURSION_EULER JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END RECURSION_EULER ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.RecursionEuler");
        System.out.println("RecursionEuler output: [" + output + "]");
        assertTrue(output.trim().length() > 0, "Should produce some output");
    }
}
