package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import gnb.perseus.postprocess.FixLimits;

public class ProceduresTest extends CompilerTest {

    @Test
	public void thunkIsolation_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/procedures/thunk_isolation.alg", "gnb/perseus/programs", "ThunkIsolation", BUILD_DIR);
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
	public void oneton_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/procedures/oneton.alg", "gnb/perseus/programs", "OnetonTest", BUILD_DIR);
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
	public void jen_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/procedures/jen.alg", "gnb/perseus/programs", "JenTest", BUILD_DIR);
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
    public void proc_var_test() throws Exception {
        // Compile Algol source to Jasmin
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/procedures/proc_var.alg", "gnb/perseus/programs", "ProcVar", BUILD_DIR);
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
                "test/algol/procedures/proc_param.alg", "gnb/perseus/programs", "ProcParam", BUILD_DIR);
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
                "test/algol/procedures/proc_typed_simple.alg", "gnb/perseus/programs", "ProcTypedSimple", BUILD_DIR);
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
	public void deferred_typing_name_assignment_test() throws Exception {
		// Compile Algol source to Jasmin
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/deferred_typing_name_assignment.alg", "gnb/perseus/programs", "DeferredTypingNameAssignmentTest", BUILD_DIR);
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
			FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/DeferredTypingNameAssignmentTest.class"));
		} catch (Exception e) {
			throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
		}

		// Run and capture output
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.DeferredTypingNameAssignmentTest");
		System.out.println("Deferred typing output: [" + output + "]");
		assertTrue(output.contains("43"), "Expected output to contain 43");
		assertTrue(output.contains("4.14"), "Expected output to contain 4.14");
	}

	@Test
	public void deferred_typing_value_and_name_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/deferred_typing_value_and_name.alg", "gnb/perseus/programs", "DeferredTypingValueAndNameTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		System.out.println("=== DEFERRED TYPING VALUE AND NAME JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END DEFERRED TYPING VALUE AND NAME ===");

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		try {
			FixLimits.fixClassFamilyInPlace(BUILD_DIR.resolve("gnb/perseus/programs/DeferredTypingValueAndNameTest.class"));
		} catch (Exception e) {
			throw new AssertionError("ASM CheckClassAdapter verification failed: " + e.getMessage(), e);
		}

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.DeferredTypingValueAndNameTest");
		System.out.println("Deferred typing value and name output: [" + output + "]");
		assertEquals("5.0 5.5 6.0 6.5", output.trim());
	}

	@Test
	public void typed_return_coercion_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/typed_return_coercion.alg", "gnb/perseus/programs", "TypedReturnCoercionTest", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.TypedReturnCoercionTest");
		assertEquals("3,-3", output.trim());
	}

	@Test
	public void boolean_and_ref_return_procedures_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/proc_boolean_and_ref_returns.alg",
			"gnb/perseus/programs",
			"ProcBooleanAndRefReturns",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains(".method public static positive(I)I"),
			"Boolean procedures should lower to JVM int/boolean return conventions");
		assertTrue(jasminSource.contains(".method public static builder(Ljava/lang/String;)Ljava/lang/Object;"),
			"Reference-returning procedures should lower to JVM object returns");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ProcBooleanAndRefReturns");
		assertEquals("TFabc", output.trim());
	}

	@Test
	public void boolean_and_ref_procedure_params_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/proc_boolean_and_ref_params.alg",
			"gnb/perseus/programs",
			"ProcBooleanAndRefParams",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("ReferenceProcedure"),
			"Ref-returning procedure parameters should use the reference procedure interface");
		assertTrue(jasminSource.contains("IntegerProcedure"),
			"Boolean-returning procedure parameters should use the integer/boolean procedure interface");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ProcBooleanAndRefParams");
		assertEquals("abcT", output.trim());
	}

	@Test
	public void anonymous_proc_param_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/anonymous_proc_param.alg",
			"gnb/perseus/programs",
			"AnonymousProcParam",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("__anonproc"),
			"Anonymous procedures should lower to synthetic static helper methods");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcParam");
		assertEquals("42", output.trim());
	}

	@Test
	public void anonymous_proc_void_param_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/anonymous_proc_void_param.alg",
			"gnb/perseus/programs",
			"AnonymousProcVoidParam",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("__anonproc"),
			"Void anonymous procedures should lower to synthetic static helper methods");
		assertTrue(jasminSource.contains("VoidProcedure"),
			"Void anonymous procedures should lower through the void procedure interface");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcVoidParam");
		assertEquals("Hello", output.trim());
	}

	@Test
	public void anonymous_proc_capture_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/anonymous_proc_capture.alg",
			"gnb/perseus/programs",
			"AnonymousProcCapture",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("__anonproc"),
			"Captured anonymous procedures should lower to synthetic static helper methods");
		assertTrue(jasminSource.contains("__env_outer_base"),
			"Captured anonymous procedures should load the enclosing procedure parameter bridge");
		assertTrue(jasminSource.contains("__env_outer_offset"),
			"Captured anonymous procedures should load the enclosing procedure local bridge");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcCapture");
		assertEquals("79", output.trim());
	}

	@Test
	public void anonymous_proc_rebind_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/anonymous_proc_rebind.alg",
			"gnb/perseus/programs",
			"AnonymousProcRebind",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("__anonproc"),
			"Rebound anonymous procedures should lower to synthetic static helper methods");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcRebind");
		assertEquals("Hello", output.trim());
	}

	@Test
	public void anonymous_proc_rebind_value_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
			"test/algol/procedures/anonymous_proc_rebind_value.alg",
			"gnb/perseus/programs",
			"AnonymousProcRebindValue",
			BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);

		assertFalse(jasminSource.startsWith("ERROR"),
			"Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
		assertTrue(jasminSource.contains("__anonproc"),
			"Typed rebound anonymous procedures should lower to synthetic static helper methods");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);
		String output = runClass(BUILD_DIR, "gnb.perseus.programs.AnonymousProcRebindValue");
		assertEquals("42", output.trim());
	}

    @Test
    public void deferred_name_params_mixed_type_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
            "test/algol/procedures/deferred_name_params_mixed_type.alg", "gnb/perseus/programs", "DeferredNameParamsMixedType", BUILD_DIR);
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
            "test/algol/procedures/deferred_missing_formal_types.alg", "gnb/perseus/programs", "DeferredMissingFormalTypes", BUILD_DIR);
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
    public void nested_scope_access_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/procedures/nested_scope_access.alg", "gnb/perseus/programs", "NestedScopeAccess", BUILD_DIR);
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
                "test/algol/procedures/nested_digits.alg", "gnb/perseus/programs", "NestedDigits", BUILD_DIR);
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
                "test/algol/procedures/thunk_closure_isolation.alg", "gnb/perseus/programs", "ThunkClosureIsolation", BUILD_DIR);
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
}
