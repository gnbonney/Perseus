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
}
