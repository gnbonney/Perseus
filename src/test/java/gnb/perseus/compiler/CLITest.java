package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CLITest extends CompilerTest {

    record ProcessResult(int exitCode, String stdout, String stderr) {}

    private int maxStackForMethod(Path classFile, String methodName) throws Exception {
        byte[] bytes = Files.readAllBytes(classFile);
        ClassReader reader = new ClassReader(bytes);
        final int[] maxStack = { -1 };
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals(methodName)) {
                    return delegate;
                }
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMaxs(int visitMaxStack, int visitMaxLocals) {
                        maxStack[0] = visitMaxStack;
                        super.visitMaxs(visitMaxStack, visitMaxLocals);
                    }
                };
            }
        }, 0);
        return maxStack[0];
    }

    private ProcessResult runCli(List<String> args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("gnb.perseus.cli.PerseusCLI");
        cmd.addAll(args);

        System.out.println("runCli: " + cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        p.getOutputStream().close();
        String stdout;
        String stderr;
        try (var out = p.getInputStream(); var err = p.getErrorStream()) {
            stdout = new String(out.readAllBytes());
            stderr = new String(err.readAllBytes());
        }
        int exitCode = p.waitFor();
        System.out.println("runCli: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        return new ProcessResult(exitCode, stdout, stderr);
    }

    @Test
    public void cli_compile_to_output_directory_test() throws Exception {
        Path outDir = BUILD_DIR.resolve("cli-out");
        Files.createDirectories(outDir);

        ProcessResult result = runCli(List.of(
                "test/algol/hello.alg",
                "-d",
                outDir.toString()));

        assertEquals(0, result.exitCode(),
                "CLI should accept javac-style -d output syntax and compile successfully");

        Path classFile = outDir.resolve("gnb/perseus/programs/Hello.class");
        assertTrue(Files.exists(classFile),
                "CLI should emit class files under the requested output directory");

        String output = runClass(outDir, "gnb.perseus.programs.Hello");
        assertEquals("Hello World", output.trim(),
                "CLI-compiled hello program should run successfully");
    }

    @Test
    public void cli_jar_packaging_test() throws Exception {
        Path outDir = BUILD_DIR.resolve("cli-jar-out");
        Files.createDirectories(outDir);
        Path jarFile = outDir.resolve("hello.jar");

        ProcessResult result = runCli(List.of(
                "test/algol/hello.alg",
                "-d",
                outDir.toString(),
                "--jar",
                jarFile.toString()));

        assertEquals(0, result.exitCode(),
                "CLI should support optional JAR packaging");
        assertTrue(Files.exists(jarFile),
                "CLI should create the requested JAR file");

        List<String> cmd = List.of("java", "-jar", jarFile.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        p.getOutputStream().close();
        String stdout;
        String stderr;
        try (var out = p.getInputStream(); var err = p.getErrorStream()) {
            stdout = new String(out.readAllBytes());
            stderr = new String(err.readAllBytes());
        }
        int exitCode = p.waitFor();

        assertEquals(0, exitCode,
                "Packaged JAR should be runnable");
        assertEquals("Hello World", stdout.trim(),
                "Runnable JAR should execute the compiled Perseus program");
        assertEquals("", stderr.trim(),
                "Runnable JAR should not emit unexpected stderr");
    }

    @Test
    public void cli_standard_compilation_includes_asm_postprocess_test() throws Exception {
        Path outDir = BUILD_DIR.resolve("cli-own-variables-out");
        Files.createDirectories(outDir);

        ProcessResult result = runCli(List.of(
                "test/algol/own_variables.alg",
                "-d",
                outDir.toString(),
                "--class-name",
                "OwnVariablesCli"));

        assertEquals(0, result.exitCode(),
                "CLI should compile successfully when ASM post-processing is part of the default pipeline");

        Path classFile = outDir.resolve("gnb/perseus/programs/OwnVariablesCli.class");
        assertTrue(Files.exists(classFile),
                "CLI should emit the compiled class file");

        String output = runClassWithTimeout(outDir, "gnb.perseus.programs.OwnVariablesCli", 10_000);
        assertEquals("1 3 6", output.trim(),
                "CLI should produce runnable output for programs that rely on ASM post-processing");
    }

    @Test
    public void cli_standard_compilation_recomputes_stack_limits_with_asm_test() throws Exception {
        Path outDir = BUILD_DIR.resolve("cli-asm-out");
        Files.createDirectories(outDir);

        ProcessResult result = runCli(List.of(
                "test/algol/hello.alg",
                "-d",
                outDir.toString(),
                "--class-name",
                "HelloAsm"));

        assertEquals(0, result.exitCode(),
                "CLI should compile successfully with ASM post-processing enabled");

        Path classFile = outDir.resolve("gnb/perseus/programs/HelloAsm.class");
        assertTrue(Files.exists(classFile),
                "CLI should emit the compiled class file");

        int mainMaxStack = maxStackForMethod(classFile, "main");
        assertTrue(mainMaxStack > 0 && mainMaxStack < 64,
                "ASM post-processing should recompute max stack from the compiler's conservative default; found " + mainMaxStack);
    }

    @Test
    public void cli_classpath_support_for_external_algol_test() throws Exception {
        Path libraryOutDir = BUILD_DIR.resolve("cli-cp-lib");
        Path clientOutDir = BUILD_DIR.resolve("cli-cp-client");
        Files.createDirectories(libraryOutDir);
        Files.createDirectories(clientOutDir);

        ProcessResult libraryResult = runCli(List.of(
                "test/algol/external_algol_library.alg",
                "-d",
                libraryOutDir.toString(),
                "--class-name",
                "ExternalAlgolLibrary"));

        assertEquals(0, libraryResult.exitCode(),
                "CLI should compile the external Algol library successfully");

        ProcessResult clientResult = runCli(List.of(
                "test/algol/external_algol_client.alg",
                "-d",
                clientOutDir.toString(),
                "-cp",
                libraryOutDir.toString(),
                "--class-name",
                "ExternalAlgolClient"));

        assertEquals(0, clientResult.exitCode(),
                "CLI should use -cp to resolve separately compiled external Algol code");

        Path clientClassFile = clientOutDir.resolve("gnb/perseus/programs/ExternalAlgolClient.class");
        assertTrue(Files.exists(clientClassFile),
                "CLI should emit the client class file when classpath resolution succeeds");

        String combinedClasspath = clientOutDir + java.io.File.pathSeparator + libraryOutDir;
        List<String> cmd = List.of("java", "-cp", combinedClasspath, "gnb.perseus.programs.ExternalAlgolClient");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        p.getOutputStream().close();
        String stdout;
        String stderr;
        try (var out = p.getInputStream(); var err = p.getErrorStream()) {
            stdout = new String(out.readAllBytes());
            stderr = new String(err.readAllBytes());
        }
        int exitCode = p.waitFor();

        assertEquals(0, exitCode,
                "Client compiled with -cp should run successfully against the separately compiled library");
        assertEquals("5.0", stdout.trim(),
                "Client should call the external Algol library through the CLI classpath");
        assertEquals("", stderr.trim(),
                "Client run should not emit unexpected stderr");
    }
}
