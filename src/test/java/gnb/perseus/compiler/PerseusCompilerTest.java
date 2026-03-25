// Copyright (c) 2017-2026 Greg Bonney

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

/**
 * @author Greg Bonney
 *
 */
public class PerseusCompilerTest {

	private static final Path BUILD_DIR = Paths.get("build/test-algol");

	@Test
	public void hello() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/hello.alg", "gnb/perseus/programs", "Hello", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println(jasminSource);

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Hello"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V"),
				"Output should emit outstring call");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run and capture output
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Hello");
		System.out.println("Program output: [" + output + "]");
		assertEquals("Hello World", output.trim());
	}

	@Test
	public void primer1() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primer1.alg", "gnb/perseus/programs", "Primer1", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println(jasminSource);

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Primer1"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("putstatic"),
				"Output should contain putstatic instructions for scalar variable assignment");
		assertTrue(jasminSource.contains("ddiv"),
				"Output should contain ddiv for real division");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � primer1 has no output, just verify it executes without error
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Primer1");
		assertEquals("", output.trim(), "primer1 should produce no output");
	}

	@Test
	public void primer2() throws Exception {
		// Compile Algol source to Jasmin (infinite loop � run briefly to confirm no crash)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primer2.alg", "gnb/perseus/programs", "Primer2", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER2 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER2 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Primer2"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("AA:"),
				"Output should contain the label AA");
		assertTrue(jasminSource.contains("goto AA"),
				"Output should contain goto AA");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run briefly (infinite loop) � should not crash before timeout
		// A timeout is the expected outcome for Primer2; an early exit should fail the test.
		String output = runClassExpectTimeout(BUILD_DIR, "gnb.perseus.programs.Primer2", 2000);
		// For infinite loop, output should be empty and process killed by timeout
		assertEquals("", output.trim(), "Infinite loop should produce no output before timeout");
	}

	@Test
	public void primer3() throws Exception {
		// Compile Algol source to Jasmin (loops 1000 times then stops)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primer3.alg", "gnb/perseus/programs", "Primer3", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER3 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER3 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Primer3"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("AA:"),
				"Output should contain the label AA");
		assertTrue(jasminSource.contains("if_icmplt"),
				"Output should contain integer comparison");
		assertTrue(jasminSource.contains("putstatic"),
				"Output should contain putstatic for variable assignment");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � should terminate after 1000 iterations
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Primer3");
		assertEquals("", output.trim(), "primer3 should produce no output");
	}

	@Test
	public void primer5() throws Exception {
		// Compile Algol source to Jasmin (approximation of e via Taylor series)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primer5.alg", "gnb/perseus/programs", "Primer5", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER5 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER5 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Primer5"),
				"Output should declare the correct class");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � should print an approximation of e
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Primer5");
		System.out.println("primer5 output: [" + output + "]");
		assertTrue(output.trim().startsWith("2.718"), "primer5 should output an approximation of e (� 2.718...)");
	}

	@Test
	public void boolean_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/boolean.alg", "gnb/perseus/programs", "Boolean", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== BOOLEAN JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END BOOLEAN ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � should print "true"
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Boolean");
		System.out.println("boolean output: [" + output + "]");
		assertEquals("true", output.trim());
	}

	@Test
	public void thunkIsolation_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/thunk_isolation.alg", "gnb/perseus/programs", "ThunkIsolation", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== THUNK ISOLATION JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END THUNK ISOLATION ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � should print "2"
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ThunkIsolation");
		System.out.println("thunk isolation output: [" + output + "]");
		assertEquals("2", output.trim());
	}

	@Test
	public void array_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/array.alg", "gnb/perseus/programs", "ArrayTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== ARRAY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END ARRAY ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � nArr[5]=5, nArr[3]=0 (uninitialized)
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ArrayTest");
		System.out.println("array output: [" + output + "]");
		assertEquals("50", output.trim());
	}

	@Test
	public void real_array_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/real_array.alg", "gnb/perseus/programs", "RealArrayTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== REAL ARRAY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END REAL ARRAY ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("newarray double"), "Should allocate a double array");
		assertTrue(jasminSource.contains("daload"), "Should use daload for real array element access");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � q[-7]=1.23, q[2]=4.56
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.RealArrayTest");
		System.out.println("real_array output: [" + output + "]");
		assertTrue(output.contains("1.23"), "Output should contain 1.23");
		assertTrue(output.contains("4.56"), "Output should contain 4.56");
	}

	@Test
	public void array_param_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/array_param.alg", "gnb/perseus/programs", "ArrayParamTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== ARRAY PARAM JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END ARRAY PARAM ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains(".method public static sum([DIIII)D"),
				"Formal real array parameters should carry array bounds alongside explicit integer parameters");
		assertTrue(jasminSource.contains(".method public static shift([DII)V"),
				"Void procedures with array parameters should also receive bounds");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ArrayParamTest");
		System.out.println("array_param output: [" + output + "]");
		assertTrue(output.contains("5"), "Array parameter procedure should respect the caller's bounds");
	}

	@Test
	public void multidimensional_array_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/matrix_trace.alg", "gnb/perseus/programs", "MatrixTraceTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== MULTIDIM ARRAY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END MULTIDIM ARRAY ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("ldc 9\nnewarray int"),
				"A two-dimensional 3x3 integer array should flatten to a single nine-element JVM array");
		assertTrue(jasminSource.contains("imul"),
				"Multidimensional array indexing should linearize subscripts with multiplication");
		assertTrue(jasminSource.contains("iaload"),
				"Integer multidimensional arrays should read through iaload");
		assertTrue(jasminSource.contains("iastore"),
				"Integer multidimensional arrays should write through iastore");
		assertTrue(jasminSource.contains(".method public static Spur(II)I"),
				"The regression should include a Spur procedure operating on a multidimensional matrix");
		assertTrue(jasminSource.contains(".method public static Transpose(II)V"),
				"The regression should include a Transpose procedure operating on a multidimensional matrix");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.MatrixTraceTest");
		System.out.println("multidimensional_array output: [" + output + "]");
		assertEquals("15\n4\n2\n15", output.trim(),
				"Spur and Transpose should preserve Algol-style multidimensional indexing with non-zero lower bounds");
	}

	@Test
	public void modified_division_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/modified_division.alg", "gnb/perseus/programs", "ModifiedDivisionTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== MODIFIED DIVISION JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END MODIFIED DIVISION ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ModifiedDivisionTest");
		System.out.println("modified_division output: [" + output + "]");
		assertEquals("2\n-2\n-2\n2", output.trim(),
				"div should follow the Modified Report's integer-division behavior");
	}

	@Test
	public void power_associativity_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/power_associativity.alg", "gnb/perseus/programs", "PowerAssociativityTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== POWER ASSOCIATIVITY JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END POWER ASSOCIATIVITY ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.PowerAssociativityTest");
		System.out.println("power_associativity output: [" + output + "]");
		assertEquals("64\n512\n8", output.trim(),
				"** and ^ should support the expected exponentiation behavior and associativity");
	}

	@Test
	public void boolean_imp_eqv_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/boolean_imp_eqv.alg", "gnb/perseus/programs", "BooleanImpEqvTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== BOOLEAN IMP EQV JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END BOOLEAN IMP EQV ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.BooleanImpEqvTest");
		System.out.println("boolean_imp_eqv output: [" + output + "]");
		assertEquals("imp-1\nimp-2\neqv-1\neqv-2\nprecedence-1\nprecedence-2", output.trim(),
				"imp and eqv should compile with the intended Boolean behavior and precedence");
	}

	@Test
	public void representation_synonyms_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/representation_synonyms.alg", "gnb/perseus/programs", "RepresentationSynonymsTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== REPRESENTATION SYNONYMS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END REPRESENTATION SYNONYMS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.RepresentationSynonymsTest");
		System.out.println("representation_synonyms output: [" + output + "]");
		assertEquals("imp-synonym\neqv-synonym\n16", output.trim(),
				"=>, ==, and ^ should work as the documented hardware-representation synonyms");
	}

	@Test
	public void brace_blocks_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/brace_blocks.alg", "gnb/perseus/programs", "BraceBlocksTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== BRACE BLOCKS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END BRACE BLOCKS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.BraceBlocksTest");
		System.out.println("brace_blocks output: [" + output + "]");
		assertEquals("brace-blocks", output.trim(),
				"Brace-delimited blocks should work anywhere begin/end blocks are accepted");
	}

	@Test
	public void primer4() throws Exception {
		// Compile Algol source to Jasmin (for loop with 1000 iterations)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primer4.alg", "gnb/perseus/programs", "Primer4", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PRIMER4 JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PRIMER4 ===");

		assertNotEquals("NO OUTPUT", jasminSource, "Compilation should succeed");
		assertTrue(jasminSource.contains(".class public gnb/perseus/programs/Primer4"),
				"Output should declare the correct class");
		assertTrue(jasminSource.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(jasminSource.contains("loop_"),
				"Output should contain loop labels");
		assertTrue(jasminSource.contains("endfor_"),
				"Output should contain endfor labels");

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � should terminate after 1000 iterations, output final x and y
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.Primer4");
		assertFalse(output.trim().isEmpty(), "primer4 should produce output");
		// TODO: assert correct values, approximately 0.1545 and -0.988
	}

	@Test
	public void oneton_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/oneton.alg", "gnb/perseus/programs", "OnetonTest", BUILD_DIR);
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
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run � loop prints 1..12 each followed by newline, then M=oneton(12)=24
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.OnetonTest");
		System.out.println("oneton output: [" + output + "]");
		String expected = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n24";
		assertEquals(expected, output.trim());
	}

	@Test
	public void primes_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/primes.alg", "gnb/perseus/programs", "PrimesTest", BUILD_DIR);
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
	public void jen_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/jen.alg", "gnb/perseus/programs", "JenTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not error: " + jasminSource);

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.JenTest");
		System.out.println("jen output: [" + output + "]");
		String[] lines = output.trim().split("\\R");
		assertTrue(lines.length >= 2, "Expected at least two lines of output");
		double v1 = Double.parseDouble(lines[0]);
		double v2 = Double.parseDouble(lines[1]);
		assertEquals(55.0, v1, 1e-9);
		assertEquals(110.0, v2, 1e-9);
	}

	@Test
	public void callByNameUpdateTest() throws Exception {
		String algolSrc = """
begin
    integer i;
    i := 5;
    real procedure inc(x);
        integer x;
    begin
        x := x + 1;
        inc := x
    end;
    outreal(1, inc(i));
    outstring(1, "\\n");
    outinteger(1, i);
end
""";
		Path temp = BUILD_DIR.resolve("cbn_test.alg");
		Files.createDirectories(BUILD_DIR);
		Files.writeString(temp, algolSrc);
		Path jas = PerseusCompiler.compileToFile(
			temp.toString(), "gnb/perseus/programs", "CbnTest", BUILD_DIR);
		String jasSrc = Files.readString(jas);
		assertFalse(jasSrc.startsWith("ERROR"), "Compilation should succeed");
		PerseusCompiler.assemble(jas, BUILD_DIR);
		String out = runClass(BUILD_DIR, "gnb.perseus.programs.CbnTest");
		System.out.println("cbn output: [" + out + "]");
		String[] parts = out.trim().split("\\R");
		assertEquals("6.0", parts[0]);
		assertEquals("6", parts[1]);
	}

	@Test
	public void pi_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/pi.alg", "gnb/perseus/programs", "PiTest", BUILD_DIR);
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
				"test/algol/pi2.alg", "gnb/perseus/programs", "Pi2Test", BUILD_DIR);
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

	private static String runClass(Path classDir, String className) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClass: " + cmd);
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(false);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin
		String stdout;
		String stderr;
		try (var out = p.getInputStream(); var err = p.getErrorStream()) {
			stdout = new String(out.readAllBytes());
			stderr = new String(err.readAllBytes());
		}
		int exitCode = p.waitFor();
		System.out.println("runClass: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
		assertEquals(0, exitCode, "Process failed for " + className + ": exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
		return stdout;
	}

	private record TimedRunResult(String stdout, String stderr, int exitCode, boolean timedOut) {}

	private static TimedRunResult runClassForAtMost(Path classDir, String className, long timeoutMs) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClassForAtMost: " + cmd + " timeout=" + timeoutMs + "ms");
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(false);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin
		boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		boolean timedOut = false;
		if (!finished) {
			timedOut = true;
			p.destroyForcibly();
			p.waitFor();
			System.out.println("runClassForAtMost: process killed after timeout");
		}
		String stdout;
		String stderr;
		try (var out = p.getInputStream(); var err = p.getErrorStream()) {
			stdout = new String(out.readAllBytes());
			stderr = new String(err.readAllBytes());
		}
		int exitCode = p.exitValue();
		System.out.println("runClassForAtMost: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "] timedOut=" + timedOut);
		return new TimedRunResult(stdout, stderr, exitCode, timedOut);
	}

	private static String runClassWithTimeout(Path classDir, String className, long timeoutMs) throws Exception {
		TimedRunResult result = runClassForAtMost(classDir, className, timeoutMs);
		if (result.timedOut()) {
			fail("Process timed out for " + className + " after " + timeoutMs + "ms"
					+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		}
		assertEquals(0, result.exitCode(),
				"Process failed for " + className + " with timeout: exit=" + result.exitCode()
						+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		return result.stdout();
	}

	private static String runClassExpectTimeout(Path classDir, String className, long timeoutMs) throws Exception {
		TimedRunResult result = runClassForAtMost(classDir, className, timeoutMs);
		if (!result.timedOut()) {
			fail("Expected " + className + " to time out after " + timeoutMs + "ms"
					+ " but exit=" + result.exitCode()
					+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		}
		return result.stdout();
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
        
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/output_test.alg", "gnb/perseus/programs",
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
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/channel_test.alg", "gnb/perseus/programs",
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
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/string_test.alg", "gnb/perseus/programs",
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
                "test/algol/instring_test.alg", "gnb/perseus/programs",
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
                "test/algol/stop_fault_test.alg", "gnb/perseus/programs",
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
                "test/algol/constants_test.alg", "gnb/perseus/programs",
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
    public void pi_programs_test() throws Exception {
        // Test that pi_simple.alg (Archimedes pi approximation) compiles and runs from Milestone 11F
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/pi_simple.alg", "gnb/perseus/programs",
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
    public void sqrt_negative_test() throws Exception {
        // Test that sqrt of negative number returns NaN (documented choice) from Milestone 11F
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/sqrt_negative_test.alg", "gnb/perseus/programs",
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
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.SqrtNegativeTest");
        System.out.println("sqrt negative test output: [" + output + "]");

        // Verify output contains NaN (Java's Math.sqrt returns NaN for negative inputs)
        assertTrue(output.contains("NaN"), "sqrt of negative number should return NaN");
    }

    @Test
    public void proc_var_test() throws Exception {
        // Compile Algol source to Jasmin
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/proc_var.alg", "gnb/perseus/programs", "ProcVar", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== PROC_VAR JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END PROC_VAR ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run and capture output
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ProcVar");
        System.out.println("Proc var output: [" + output + "]");
        assertEquals("Hello from procedureGoodbye from procedure", output.trim());
    }

    @Test
    public void proc_param_test() throws Exception {
        // Compile Algol source to Jasmin
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/proc_param.alg", "gnb/perseus/programs", "ProcParam", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== PROC_PARAM JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END PROC_PARAM ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run and capture output
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ProcParam");
        System.out.println("Proc param output: [" + output + "]");
        assertEquals("Hello", output.trim());
    }

    @Test
    public void proc_typed_simple_test() throws Exception {
        // Compile Algol source to Jasmin
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/proc_typed_simple.alg", "gnb/perseus/programs", "ProcTypedSimple", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== PROC_TYPED_SIMPLE JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END PROC_TYPED_SIMPLE ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        // Assemble to .class
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        // Run and capture output
        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ProcTypedSimple");
        System.out.println("Proc typed simple output: [" + output + "]");
        assertEquals("3.141590.0", output.trim());
    }

	@Test
	public void deferred_typing_test() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/deferred_typing_test.alg", "gnb/perseus/programs", "DeferredTypingTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		System.out.println("=== DEFERRED TYPING JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END DEFERRED TYPING ===");

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

		// Assemble to .class
		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		// Run FixLimits to verify and fix the generated class family.
		try {
			FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/DeferredTypingTest.class"));
		} catch (Exception e) {
			throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
		}

		// Run and capture output
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.DeferredTypingTest");
		System.out.println("Deferred typing output: [" + output + "]");
		assertTrue(output.contains("43"), "Expected output to contain 43");
		assertTrue(output.contains("4.14"), "Expected output to contain 4.14");
	}

    @Test
    public void deferred_name_params_mixed_type_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
            "test/algol/deferred_name_params_mixed_type.alg", "gnb/perseus/programs", "DeferredNameParamsMixedType", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== DEFERRED NAME PARAMS MIXED TYPE JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END DEFERRED NAME PARAMS MIXED TYPE ===");

        assertFalse(jasminSource.startsWith("ERROR"),
            "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.DeferredNameParamsMixedType");
        System.out.println("Deferred name params mixed type output: [" + output + "]");
        assertTrue(output.contains("11"), "Expected output to include 11");
        assertTrue(output.contains("3.5"), "Expected output to include 3.5");
    }

    @Test
    public void deferred_missing_formal_types_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
            "test/algol/deferred_missing_formal_types.alg", "gnb/perseus/programs", "DeferredMissingFormalTypes", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== DEFERRED MISSING FORMAL TYPES JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END DEFERRED MISSING FORMAL TYPES ===");

        assertFalse(jasminSource.startsWith("ERROR"),
            "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.DeferredMissingFormalTypes");
        System.out.println("Deferred missing formal types output: [" + output + "]");
        assertTrue(output.contains("6"), "Expected output to include 6");
        assertTrue(output.contains("2.2"), "Expected output to include 2.2");
    }

    @Test
    public void manboy_test() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/manboy.alg", "gnb/perseus/programs", "ManBoy", BUILD_DIR);
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
                "test/algol/recursion_euler.alg", "gnb/perseus/programs", "RecursionEuler", BUILD_DIR);
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

    @Test
    public void nested_scope_access_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/nested_scope_access.alg", "gnb/perseus/programs", "NestedScopeAccess", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== NESTED_SCOPE_ACCESS JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END NESTED_SCOPE_ACCESS ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.NestedScopeAccess");
        System.out.println("Nested scope access output: [" + output + "]");
        assertEquals("2", output.trim(), "Nested scope access test should output 2");
    }

    @Test
    public void nested_digits_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/nested_digits.alg", "gnb/perseus/programs", "NestedDigits", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== NESTED_DIGITS JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END NESTED_DIGITS ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        try {
            FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/NestedDigits.class"));
        } catch (Exception e) {
            throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
        }

        String output = runClassWithTimeout(BUILD_DIR, "gnb.perseus.programs.NestedDigits", 10_000);
        System.out.println("Nested digits output: [" + output + "]");
        assertEquals("3 1 4 8 3\n2 7 9 2", output.trim(),
                "Nested digits should preserve each outer activation while updating outer locals");
    }

    @Test
    public void thunk_closure_isolation_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/thunk_closure_isolation.alg", "gnb/perseus/programs", "ThunkClosureIsolation", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== THUNK_CLOSURE_ISOLATION JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END THUNK_CLOSURE_ISOLATION ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ThunkClosureIsolation");
        System.out.println("Thunk closure isolation output: [" + output + "]");
        assertEquals("1\n2", output.trim(), "Thunk closure isolation should output two independent values");
    }

    @Test
    public void boolean_operators_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/boolean_operators.alg", "gnb/perseus/programs",
                "BooleanOperators", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== BOOLEAN OPERATORS JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END BOOLEAN OPERATORS ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.BooleanOperators");
        System.out.println("boolean operators output: [" + output + "]");
        assertTrue(output.contains("or works"), "or operator should work");
        assertTrue(output.contains("not works"), "not operator should work");
        assertTrue(output.contains("Boolean logic test passed"), "combined boolean logic should pass");
    }


    @Test
    public void string_output_test() throws Exception {
	// Milestone 18: string variable declaration, assignment, length, substring, concat, s[i] access, s[i] := mutation
	Path jasminFile = PerseusCompiler.compileToFile(
		"test/algol/string_output.alg", "gnb/perseus/programs", "StringOutput", BUILD_DIR);
	String jasminSource = Files.readString(jasminFile);

	System.out.println("=== STRING OUTPUT JASMIN ===");
	System.out.println(jasminSource);
	System.out.println("=== END STRING OUTPUT ===");

	// Check compilation succeeded
	assertFalse(jasminSource.startsWith("ERROR"),
		"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

	// Should use String.length() for length(s)
	assertTrue(jasminSource.contains("invokevirtual java/lang/String/length()I"),
		"Should use String.length() for length(s)");

	// Should use String.substring for s[i] access and substring()
	assertTrue(jasminSource.contains("invokevirtual java/lang/String/substring(II)Ljava/lang/String;"),
		"Should use String.substring(II) for character access and substring()");

	// Should use StringBuilder for s[i] := mutation
	assertTrue(jasminSource.contains("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;"),
		"Should use StringBuilder.toString() for character mutation");

	// Should use String.concat for concat()
	assertTrue(jasminSource.contains("invokevirtual java/lang/String/concat(Ljava/lang/String;)Ljava/lang/String;"),
		"Should use String.concat() for concat()");

	// Assemble to .class
	PerseusCompiler.assemble(jasminFile, BUILD_DIR);

	// Run and check output
	String output = runClass(BUILD_DIR, "gnb.perseus.programs.StringOutput");
	System.out.println("string_output output: [" + output + "]");
	assertEquals("Hello, world! 13 H world Hello, World! Hello, World!!!!", output.trim(),
		"Should output all string operations correctly");
    }

    @Test
    public void own_variables_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/own_variables.alg", "gnb/perseus/programs", "OwnVariables", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== OWN VARIABLES JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END OWN VARIABLES ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains(".field public static counter I"),
                "own scalar should be emitted as a persistent static field");
        assertTrue(jasminSource.contains(".field public static history [I"),
                "own array should be emitted as a persistent static field");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        try {
            FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/OwnVariables.class"));
        } catch (Exception e) {
            throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
        }

        String output = runClassWithTimeout(BUILD_DIR, "gnb.perseus.programs.OwnVariables", 10_000);
        System.out.println("Own variables output: [" + output + "]");
        assertEquals("1 3 6", output.trim(), "Own variables should retain values across procedure re-entry");
    }
    @Test
    public void switch_declaration_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/switch_declaration.alg", "gnb/perseus/programs", "SwitchDeclaration", BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        System.out.println("=== SWITCH DECLARATION JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END SWITCH DECLARATION ===");

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("goto seed") || jasminSource.contains("goto squarephase") || jasminSource.contains("goto doublephase") || jasminSource.contains("goto report"),
                "Switch lowering should emit goto instructions for direct labels, nested switch targets, and inline-if targets");
        assertTrue(jasminSource.contains("if_icmpne"),
                "Switch lowering should compare the computed index against switch entries");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        try {
            FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/SwitchDeclaration.class"));
        } catch (Exception e) {
            throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
        }

        String output = runClassWithTimeout(BUILD_DIR, "gnb.perseus.programs.SwitchDeclaration", 10_000);
        System.out.println("Switch declaration output: [" + output + "]");
        assertEquals("25", output.trim(), "Switch declaration should drive the staged score computation to the expected total");
    }

    @Test
    public void syntax_error_diagnostic_test() throws Exception {
        Path tempFile = BUILD_DIR.resolve("syntax_error_test.alg");
        Files.createDirectories(BUILD_DIR);
        Files.writeString(tempFile, "begin x := end");

        try {
            PerseusCompiler.compileToFile(
                    tempFile.toString(), "gnb/perseus/programs", "SyntaxErrorTest", BUILD_DIR);
            fail("Expected compilation to fail with a syntax diagnostic");
        } catch (CompilationFailedException e) {
            assertFalse(e.getDiagnostics().isEmpty(), "Expected at least one diagnostic");
            CompilerDiagnostic diagnostic = e.getDiagnostics().get(0);
            assertEquals("PERS1001", diagnostic.code(), "Expected syntax error code");
            assertEquals(1, diagnostic.line(), "Expected syntax error on line 1");
            assertTrue(diagnostic.column() > 0, "Expected a positive column number");
            assertTrue(diagnostic.file().endsWith("syntax_error_test.alg"),
                    "Expected diagnostic to report the source file");
        }
    }

    @Test
    public void semantic_error_diagnostic_test() throws Exception {
        Path tempFile = BUILD_DIR.resolve("semantic_error_test.alg");
        Files.createDirectories(BUILD_DIR);
        Files.writeString(tempFile, "begin integer x; x := y end");

        try {
            PerseusCompiler.compileToFile(
                    tempFile.toString(), "gnb/perseus/programs", "SemanticErrorTest", BUILD_DIR);
            fail("Expected compilation to fail with a semantic diagnostic");
        } catch (CompilationFailedException e) {
            assertEquals(1, e.getDiagnostics().size(), "Expected exactly one diagnostic");
            CompilerDiagnostic diagnostic = e.getDiagnostics().get(0);
            assertEquals("PERS2001", diagnostic.code(), "Expected undeclared variable code");
            assertEquals(1, diagnostic.line(), "Expected semantic error on line 1");
            assertTrue(diagnostic.column() > 0, "Expected a positive column number");
            assertTrue(diagnostic.message().contains("Undeclared variable: y"),
                    "Expected the diagnostic message to mention the missing variable");
        }
    }
}

