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

public class CoreLanguageTest extends CompilerTest{

    @Test
	public void hello() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/hello.alg", "gnb/perseus/programs", "Hello", BUILD_DIR);
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
				"test/algol/core/primer1.alg", "gnb/perseus/programs", "Primer1", BUILD_DIR);
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
				"test/algol/core/primer2.alg", "gnb/perseus/programs", "Primer2", BUILD_DIR);
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
				"test/algol/core/primer3.alg", "gnb/perseus/programs", "Primer3", BUILD_DIR);
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
	public void primer4() throws Exception {
		// Compile Algol source to Jasmin (for loop with 1000 iterations)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/primer4.alg", "gnb/perseus/programs", "Primer4", BUILD_DIR);
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
	public void primer5() throws Exception {
		// Compile Algol source to Jasmin (approximation of e via Taylor series)
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/primer5.alg", "gnb/perseus/programs", "Primer5", BUILD_DIR);
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
				"test/algol/core/boolean.alg", "gnb/perseus/programs", "Boolean", BUILD_DIR);
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
	public void array_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/array.alg", "gnb/perseus/programs", "ArrayTest", BUILD_DIR);
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
				"test/algol/core/real_array.alg", "gnb/perseus/programs", "RealArrayTest", BUILD_DIR);
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
				"test/algol/core/array_param.alg", "gnb/perseus/programs", "ArrayParamTest", BUILD_DIR);
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
				"test/algol/core/matrix_trace.alg", "gnb/perseus/programs", "MatrixTraceTest", BUILD_DIR);
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
				"test/algol/core/modified_division.alg", "gnb/perseus/programs", "ModifiedDivisionTest", BUILD_DIR);
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
				"test/algol/core/power_associativity.alg", "gnb/perseus/programs", "PowerAssociativityTest", BUILD_DIR);
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
				"test/algol/core/boolean_imp_eqv.alg", "gnb/perseus/programs", "BooleanImpEqvTest", BUILD_DIR);
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
				"test/algol/core/representation_synonyms.alg", "gnb/perseus/programs", "RepresentationSynonymsTest", BUILD_DIR);
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
				"test/algol/core/brace_blocks.alg", "gnb/perseus/programs", "BraceBlocksTest", BUILD_DIR);
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
	public void numeric_labels_and_dummy_statements_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/numeric_labels_and_dummy_statements.alg", "gnb/perseus/programs", "NumericLabelsAndDummyStatementsTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== NUMERIC LABELS AND DUMMY STATEMENTS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END NUMERIC LABELS AND DUMMY STATEMENTS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.NumericLabelsAndDummyStatementsTest");
		System.out.println("numeric_labels_and_dummy_statements output: [" + output + "]");
		assertEquals("3", output.trim(),
				"Numeric labels and dummy statements should support report-style goto control flow");
	}

	@Test
	public void parameter_delimiters_absmax_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/core/parameter_delimiters_absmax.alg", "gnb/perseus/programs", "ParameterDelimitersAbsmaxTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== PARAMETER DELIMITERS ABSMAX JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END PARAMETER DELIMITERS ABSMAX ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ParameterDelimitersAbsmaxTest");
		System.out.println("parameter_delimiters_absmax output: [" + output + "]");
		assertEquals("4.5\n3", output.trim(),
				"Named parameter delimiters should work in both procedure declarations and calls");
	}

    @Test
    public void boolean_operators_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/boolean_operators.alg", "gnb/perseus/programs",
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
    public void own_variables_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/own_variables.alg", "gnb/perseus/programs", "OwnVariables", BUILD_DIR);
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
                "test/algol/core/switch_declaration.alg", "gnb/perseus/programs", "SwitchDeclaration", BUILD_DIR);
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
}
