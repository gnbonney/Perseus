package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class NamespacesTest extends CompilerTest {

    @Test
    public void namespace_basic_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/namespaces/namespace_basic.alg",
                "gnb/perseus/programs",
                "NamespaceBasicTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        assertTrue(Files.exists(BUILD_DIR.resolve("mylib/geometry/Point.class")),
                "The declared class should be emitted under the source namespace");

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.NamespaceBasicTest");
        assertEquals("", output.trim(),
                "A namespaced library-style class unit should compile even without a main-style program body");
    }

    @Test
    public void namespace_multi_class_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/namespaces/namespace_multi_class.alg",
                "gnb/perseus/programs",
                "NamespaceMultiClassTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        assertTrue(Files.exists(BUILD_DIR.resolve("mylib/geometry/Point.class")),
                "The first declared class should be emitted under the shared namespace");
        assertTrue(Files.exists(BUILD_DIR.resolve("mylib/geometry/ColoredPoint.class")),
                "The second declared class should be emitted under the shared namespace");

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.NamespaceMultiClassTest");
        assertEquals("", output.trim(),
                "Multiple classes in the same namespace should compile as one library-style compilation unit");
    }

    @Test
    public void namespace_library_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/namespaces/namespace_library.alg",
                "gnb/perseus/programs",
                "NamespaceLibraryTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"), "Compilation should not produce an error");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.NamespaceLibraryTest");
        assertEquals("", output.trim(),
                "A namespaced library unit should compile successfully for later separate compilation use");
    }

    @Test
    public void namespace_client_test() throws Exception {
        Path libraryDir = Files.createTempDirectory(BUILD_DIR, "namespace-lib");
        Path clientDir = Files.createTempDirectory(BUILD_DIR, "namespace-client");

        Path libraryJasmin = PerseusCompiler.compileToFile(
                "test/algol/namespaces/namespace_library.alg",
                "mylib/numeric",
                "NamespaceLibraryTest",
                libraryDir,
                java.util.List.of(libraryDir));
        String librarySource = Files.readString(libraryJasmin);
        assertFalse(librarySource.startsWith("ERROR"), "Library compilation should not produce an error");
        PerseusCompiler.assemble(libraryJasmin, libraryDir);

        Path clientJasmin = PerseusCompiler.compileToFile(
                "test/algol/namespaces/namespace_client.alg",
                "gnb/perseus/programs",
                "NamespaceClientTest",
                clientDir,
                java.util.List.of(clientDir, libraryDir));
        String clientSource = Files.readString(clientJasmin);
        assertFalse(clientSource.startsWith("ERROR"), "Client compilation should not produce an error");

        PerseusCompiler.assemble(clientJasmin, clientDir);

        String output = runClassWithClasspath(clientDir, "gnb.perseus.programs.NamespaceClientTest", libraryDir);
        assertEquals("5.0", output.trim(),
                "A client should be able to call a separately compiled namespaced library procedure");
    }
}
