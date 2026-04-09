package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class CollectionsTest extends CompilerTest {

    @Test
    public void vector_basic_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/vector_basic.alg",
                "gnb/perseus/programs",
                "VectorBasicTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("new java/util/ArrayList"),
                "Vector declarations should lower to Java ArrayList construction");
        assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z"),
                "Vector append should lower to ArrayList.add");
        assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;"),
                "Vector indexing should lower to ArrayList.get");
        assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/size()I"),
                "Vector size access should lower to ArrayList.size");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorBasicTest");
        assertEquals("3 4 3 3 9", output.trim().replaceAll("\\s+", " "),
                "Vector append, indexing, length, and size access should work through the Java-backed first slice");
    }

    @Test
    public void vector_for_in_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/vector_for_in.alg",
                "gnb/perseus/programs",
                "VectorForInTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/size()I"),
                "for ... in ... do over vectors should iterate by ArrayList.size");
        assertTrue(jasminSource.contains("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;"),
                "for ... in ... do over vectors should load each element through ArrayList.get");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorForInTest");
        assertEquals("12 3 2 4 6", output.trim().replaceAll("\\s+", " "),
                "Vector iteration should traverse elements in order without changing the underlying vector when the iteration variable is assigned in the body");
    }
}
