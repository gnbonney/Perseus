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
        assertTrue(jasminSource.contains("invokeinterface java/util/List/add(Ljava/lang/Object;)Z 2"),
                "Vector append should lower to the Java List interop surface");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/get(I)Ljava/lang/Object; 2"),
                "Vector indexing should lower to the Java List interop surface");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/size()I 1"),
                "Vector size access should lower to the Java List interop surface");

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
        assertTrue(jasminSource.contains("invokeinterface java/util/List/size()I 1"),
                "for ... in ... do over vectors should iterate by List.size");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/get(I)Ljava/lang/Object; 2"),
                "for ... in ... do over vectors should load each element through List.get");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorForInTest");
        assertEquals("12 3 2 4 6", output.trim().replaceAll("\\s+", " "),
                "Vector iteration should traverse elements in order without changing the underlying vector when the iteration variable is assigned in the body");
    }

    @Test
    public void external_java_iterable_for_in_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/external/external_java_iterable_for_in.alg",
                "gnb/perseus/programs",
                "ExternalJavaIterableForInTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("invokeinterface java/lang/Iterable/iterator()Ljava/util/Iterator; 1"),
                "for ... in ... do over external Java iterable objects should start from Iterable.iterator()");
        assertTrue(jasminSource.contains("invokeinterface java/util/Iterator/hasNext()Z 1"),
                "for ... in ... do over external Java iterable objects should test Iterator.hasNext()");
        assertTrue(jasminSource.contains("invokeinterface java/util/Iterator/next()Ljava/lang/Object; 1"),
                "for ... in ... do over external Java iterable objects should load elements through Iterator.next()");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.ExternalJavaIterableForInTest");
        assertEquals("6 3", output.trim().replaceAll("\\s+", " "),
                "Java Iterable iteration should unbox values for the declared loop variable type and should not alter traversal when the loop variable is assigned inside the body");
    }

    @Test
    public void vector_literal_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/vector_literal.alg",
                "gnb/perseus/programs",
                "VectorLiteralTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("new java/util/ArrayList"),
                "Vector literals should lower to Java ArrayList construction");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/add(Ljava/lang/Object;)Z 2"),
                "Vector literals should populate the Java-backed vector through the List interop surface");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorLiteralTest");
        assertEquals("3 2 6 12", output.trim().replaceAll("\\s+", " "),
                "Vector literals should preserve element order and integrate with length, indexing, and for-in traversal");
    }

    @Test
    public void vector_literal_mixed_numeric_real_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/vector_literal_real.alg",
                "gnb/perseus/programs",
                "VectorLiteralRealTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;"),
                "Mixed numeric vector literals should widen integer elements to boxed doubles");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorLiteralRealTest");
        assertEquals("1.0 2.5 3.0", output.trim().replaceAll("\\s+", " "),
                "Mixed numeric vector literals should infer a real vector and unbox elements correctly");
    }

    @Test
    public void vector_extended_operations_test() throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                "test/algol/core/vector_extended_ops.alg",
                "gnb/perseus/programs",
                "VectorExtendedOpsTest",
                BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);

        assertFalse(jasminSource.startsWith("ERROR"),
                "Compilation should not produce an error: " + jasminSource.substring(0, Math.min(200, jasminSource.length())));
        assertTrue(jasminSource.contains("invokeinterface java/util/List/add(ILjava/lang/Object;)V 3"),
                "Vector insert should lower to indexed Java List.add");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/remove(I)Ljava/lang/Object; 2"),
                "Vector remove should lower to indexed Java List.remove");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/contains(Ljava/lang/Object;)Z 2"),
                "Vector contains should lower to Java List.contains");
        assertTrue(jasminSource.contains("invokeinterface java/util/List/clear()V 1"),
                "Vector clear should lower to Java List.clear");

        PerseusCompiler.assemble(jasminFile, BUILD_DIR);

        String output = runClass(BUILD_DIR, "gnb.perseus.programs.VectorExtendedOpsTest");
        assertEquals("2 4 2 1 0", output.trim().replaceAll("\\s+", " "),
                "Extended vector operations should preserve ordinary Java-backed list behavior");
    }
}
