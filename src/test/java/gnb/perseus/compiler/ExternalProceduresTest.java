package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ExternalProceduresTest extends CompilerTest {

    private CompilationFailedException expectExternalCompilationFailure(String sourceFile, String className) throws Exception {
		try {
			PerseusCompiler.compileToFile(sourceFile, "gnb/perseus/programs", className, BUILD_DIR);
			fail("Expected compilation to fail for " + sourceFile);
			return null;
		} catch (CompilationFailedException e) {
			return e;
		}
    }

    @Test
	public void external_perseus_client_test() throws Exception {
		Path libraryJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_library.alg", "gnb/perseus/programs", "ExternalAlgolLibrary", BUILD_DIR);
		String libraryJasminSource = Files.readString(libraryJasminFile);
		System.out.println("=== EXTERNAL ALGOL LIBRARY JASMIN ===");
		System.out.println(libraryJasminSource);
		System.out.println("=== END EXTERNAL ALGOL LIBRARY ===");

		assertFalse(libraryJasminSource.startsWith("ERROR"), "Library compilation should not produce an error");

		PerseusCompiler.assemble(libraryJasminFile, BUILD_DIR);

		Path clientJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_client.alg", "gnb/perseus/programs", "ExternalAlgolClient", BUILD_DIR);
		String clientJasminSource = Files.readString(clientJasminFile);
		System.out.println("=== EXTERNAL ALGOL CLIENT JASMIN ===");
		System.out.println(clientJasminSource);
		System.out.println("=== END EXTERNAL ALGOL CLIENT ===");

		assertFalse(clientJasminSource.startsWith("ERROR"), "Client compilation should not produce an error");
		assertTrue(clientJasminSource.contains("invokestatic gnb/perseus/programs/ExternalAlgolLibrary/hypot2(DD)D"),
				"Client should link against the separately compiled external Algol procedure");

		PerseusCompiler.assemble(clientJasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalAlgolClient");
		System.out.println("external_algol_client output: [" + output + "]");
		assertEquals("5.0", output.trim(),
				"External Perseus linkage should call the separately compiled library procedure");
	}

	@Test
	public void external_java_static_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_math.alg", "gnb/perseus/programs", "ExternalJavaMath", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA STATIC JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA STATIC ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("invokestatic java/lang/Integer/parseInt(Ljava/lang/String;)I"),
				"Should call the declared external Java static Integer.parseInt method");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/cos(D)D"),
				"Should call the declared external Java static Math.cos method");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaMath");
		System.out.println("external_java_static output: [" + output + "]");
		assertEquals("42 1.0", output.trim(),
				"External Java static interop should support mapped integer, string, and real signatures");
	}

	@Test
	public void external_java_static_alias_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_static_alias.alg", "gnb/perseus/programs", "ExternalJavaStaticAlias", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA STATIC ALIAS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA STATIC ALIAS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("invokestatic java/lang/Math/cos(D)D"),
				"Should call the declared external Java static Math.cos method through the local alias");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaStaticAlias");
		System.out.println("external_java_static_alias output: [" + output + "]");
		assertEquals("1.0", output.trim(),
				"External Java static aliases should let Perseus use a local name while still targeting the real Java method");
	}

	@Test
	public void external_java_static_field_alias_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_static_field_alias.alg", "gnb/perseus/programs", "ExternalJavaStaticFieldAlias", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA STATIC FIELD ALIAS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA STATIC FIELD ALIAS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("getstatic java/lang/System/out Ljava/io/PrintStream;"),
				"Should load the declared external Java static field through its aliased local name");
		assertTrue(jasminSource.contains("invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V"),
				"Should allow a chained instance call through the imported static field binding");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaStaticFieldAlias");
		System.out.println("external_java_static_field_alias output: [" + output + "]");
		assertEquals("Hello from static field", output.trim(),
				"External Java static field aliases should bind object values that can be used for instance calls");
	}

	@Test
	public void external_java_static_constants_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_static_constants.alg", "gnb/perseus/programs", "ExternalJavaStaticConstants", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA STATIC CONSTANTS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA STATIC CONSTANTS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("getstatic java/lang/Double/MAX_VALUE D"),
				"Should import real Java constants through the external static-field binding syntax");
		assertTrue(jasminSource.contains("getstatic java/lang/Integer/MAX_VALUE I"),
				"Should import integer Java constants through the external static-field binding syntax");
		assertTrue(jasminSource.contains("getstatic java/nio/file/StandardOpenOption/READ Ljava/nio/file/StandardOpenOption;"),
				"Should import enum-like static members through the same static-field binding syntax");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaStaticConstants");
		System.out.println("external_java_static_constants output: [" + output + "]");
		assertEquals("RIREAD", output.trim(),
				"External Java static-field bindings should cover both scalar constants and enum-like static members");
	}

	@Test
	public void external_java_null_reference_comparison_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_null_reference_comparison.alg", "gnb/perseus/programs", "ExternalJavaNullReferenceComparison", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA NULL REFERENCE COMPARISON JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA NULL REFERENCE COMPARISON ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("aconst_null"),
				"Source-level null should lower to a JVM null reference literal");
		assertTrue(jasminSource.contains("if_acmpeq") || jasminSource.contains("if_acmpne"),
				"Reference equality and inequality should lower to JVM reference comparisons");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaNullReferenceComparison");
		System.out.println("external_java_null_reference_comparison output: [" + output + "]");
		assertEquals("123", output.trim(),
				"Perseus should support null literals plus = and <> comparisons for object references");
	}

	@Test
	public void external_java_ref_array_basic_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_ref_array_basic.alg", "gnb/perseus/programs", "ExternalJavaRefArrayBasic", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA REF ARRAY BASIC JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA REF ARRAY BASIC ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("anewarray java/lang/Object"),
				"Reference arrays should lower to JVM object arrays");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaRefArrayBasic");
		System.out.println("external_java_ref_array_basic output: [" + output + "]");
		assertEquals("123", output.trim(),
				"Reference arrays should support assignment, null checks, and equality comparisons");
	}

	@Test
	public void external_java_instance_fields_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_instance_fields.alg", "gnb/perseus/programs", "ExternalJavaInstanceFields", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA INSTANCE FIELDS JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA INSTANCE FIELDS ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("getfield java/awt/Point/x I"),
				"Should read the public Java instance field x directly");
		assertTrue(jasminSource.contains("getfield java/awt/Point/y I"),
				"Should read the public Java instance field y directly");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaInstanceFields");
		System.out.println("external_java_instance_fields output: [" + output + "]");
		assertEquals("3,4", output.trim(),
				"External Java instance field access should read public Java fields directly");
	}

	@Test
	public void external_java_overloaded_method_resolution_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_overloaded_method_resolution.alg", "gnb/perseus/programs", "ExternalJavaOverloadedMethodResolution", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA OVERLOADED METHOD RESOLUTION JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA OVERLOADED METHOD RESOLUTION ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/remove(I)Ljava/lang/Object;"),
				"Should select the int overload of ArrayList.remove when called with an integer argument");
		assertTrue(jasminSource.contains("invokevirtual java/lang/Object/toString()Ljava/lang/String;"),
				"Should preserve the selected Java return type so later member calls type-check correctly");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaOverloadedMethodResolution");
		System.out.println("external_java_overloaded_method_resolution output: [" + output + "]");
		assertEquals("abc", output.trim(),
				"Java overload resolution should use argument types so the right overload and return type are selected");
	}

	@Test
	public void external_java_overloaded_constructor_resolution_test() throws Exception {
		Path jasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_java_overloaded_constructor_resolution.alg", "gnb/perseus/programs", "ExternalJavaOverloadedConstructorResolution", BUILD_DIR);
		String jasminSource = Files.readString(jasminFile);
		System.out.println("=== EXTERNAL JAVA OVERLOADED CONSTRUCTOR RESOLUTION JASMIN ===");
		System.out.println(jasminSource);
		System.out.println("=== END EXTERNAL JAVA OVERLOADED CONSTRUCTOR RESOLUTION ===");

		assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");
		assertTrue(jasminSource.contains("invokespecial java/lang/StringBuilder/<init>(Ljava/lang/String;)V"),
				"Should select the StringBuilder(String) constructor when called with a string argument");

		PerseusCompiler.assemble(jasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaOverloadedConstructorResolution");
		System.out.println("external_java_overloaded_constructor_resolution output: [" + output + "]");
		assertEquals("abc", output.trim(),
				"Java constructor overload resolution should use argument types so the right constructor is selected");
	}

	@Test
	public void external_perseus_array_client_test() throws Exception {
		Path libraryJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_array_library.alg", "gnb/perseus/programs", "ExternalAlgolArrayLibrary", BUILD_DIR);
		String libraryJasminSource = Files.readString(libraryJasminFile);
		System.out.println("=== EXTERNAL ALGOL ARRAY LIBRARY JASMIN ===");
		System.out.println(libraryJasminSource);
		System.out.println("=== END EXTERNAL ALGOL ARRAY LIBRARY ===");

		assertFalse(libraryJasminSource.startsWith("ERROR"), "Array library compilation should not produce an error");

		PerseusCompiler.assemble(libraryJasminFile, BUILD_DIR);

		Path clientJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_array_client.alg", "gnb/perseus/programs", "ExternalAlgolArrayClient", BUILD_DIR);
		String clientJasminSource = Files.readString(clientJasminFile);
		System.out.println("=== EXTERNAL ALGOL ARRAY CLIENT JASMIN ===");
		System.out.println(clientJasminSource);
		System.out.println("=== END EXTERNAL ALGOL ARRAY CLIENT ===");

		assertFalse(clientJasminSource.startsWith("ERROR"), "Array client compilation should not produce an error");
		assertTrue(clientJasminSource.contains("invokestatic gnb/perseus/programs/ExternalAlgolArrayLibrary/sum3"),
				"Client should link against the separately compiled external Algol array procedure");

		PerseusCompiler.assemble(clientJasminFile, BUILD_DIR);

		String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalAlgolArrayClient");
		System.out.println("external_algol_array_client output: [" + output + "]");
		assertEquals("6.0", output.trim(),
				"External Perseus linkage should support one-dimensional real array parameters");
	}

	@Test
	public void external_wrong_return_type_diagnostic_test() throws Exception {
		Path libraryJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_library.alg", "gnb/perseus/programs", "ExternalAlgolLibrary", BUILD_DIR);
		PerseusCompiler.assemble(libraryJasminFile, BUILD_DIR);

		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_wrong_return_type.alg",
				"ExternalWrongReturnType");

		assertEquals("PERS3002", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().contains("return type"),
				"Diagnostic should explain that the return type does not match the external target");
		assertTrue(e.getDiagnostics().get(0).message().contains("hypot2"),
				"Diagnostic should name the external procedure");
	}

	@Test
	public void external_java_ambiguous_overload_diagnostic_test() throws Exception {
		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_java_ambiguous_overload.alg",
				"ExternalJavaAmbiguousOverload");

		assertEquals("PERS2010", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().contains("Ambiguous Java overload"),
				"Diagnostic should explain that the Java member call is ambiguous");
		assertTrue(e.getDiagnostics().get(0).message().contains("pick"),
				"Diagnostic should name the Java member");
	}

	@Test
	public void external_java_unsupported_member_diagnostic_test() throws Exception {
		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_java_unsupported_member.alg",
				"ExternalJavaUnsupportedMember");

		assertEquals("PERS2010", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().contains("No matching Java overload"),
				"Diagnostic should explain that no Java overload matched the supplied argument types");
		assertTrue(e.getDiagnostics().get(0).message().contains("java.awt.Point.move"),
				"Diagnostic should name the Java member that failed to resolve");
	}

	@Test
	public void external_java_invalid_reference_comparison_diagnostic_test() throws Exception {
		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_java_invalid_reference_comparison.alg",
				"ExternalJavaInvalidReferenceComparison");

		assertEquals("PERS2012", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().contains("reference comparisons only support = and <>"),
				"Diagnostic should explain that object references only support equality and inequality comparisons");
	}

	@Test
	public void external_wrong_parameter_type_diagnostic_test() throws Exception {
		Path libraryJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_library.alg", "gnb/perseus/programs", "ExternalAlgolLibrary", BUILD_DIR);
		PerseusCompiler.assemble(libraryJasminFile, BUILD_DIR);

		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_wrong_parameter_type.alg",
				"ExternalWrongParameterType");

		assertEquals("PERS3002", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().contains("parameter"),
				"Diagnostic should explain that a parameter type does not match the external target");
		assertTrue(e.getDiagnostics().get(0).message().contains("hypot2"),
				"Diagnostic should name the external procedure");
	}

	@Test
	public void external_array_signature_mismatch_diagnostic_test() throws Exception {
		Path libraryJasminFile = PerseusCompiler.compileToFile(
				"test/algol/external/external_algol_array_library.alg", "gnb/perseus/programs", "ExternalAlgolArrayLibrary", BUILD_DIR);
		PerseusCompiler.assemble(libraryJasminFile, BUILD_DIR);

		CompilationFailedException e = expectExternalCompilationFailure(
				"test/algol/external/external_array_signature_mismatch.alg",
				"ExternalArraySignatureMismatch");

		assertEquals("PERS3002", e.getDiagnostics().get(0).code());
		assertTrue(e.getDiagnostics().get(0).message().toLowerCase().contains("array"),
				"Diagnostic should explain that the external array ABI shape does not match");
		assertTrue(e.getDiagnostics().get(0).message().contains("sum3"),
				"Diagnostic should name the external procedure");
	}

    
}
