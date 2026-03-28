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

public class ExternalProceduresTest extends CompilerTest {

    @Test
	public void external_algol_client_test() throws Exception {
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
				"External Algol linkage should call the separately compiled library procedure");
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

    
}
