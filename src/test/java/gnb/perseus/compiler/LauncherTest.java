package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

public class LauncherTest extends CompilerTest {

    record ProcessResult(int exitCode, String stdout, String stderr) {}

    private ProcessResult runProcess(List<String> cmd) throws Exception {
        System.out.println("runProcess: " + cmd);
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
        System.out.println("runProcess: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        return new ProcessResult(exitCode, stdout, stderr);
    }

    @Test
    public void perseus_launcher_compile_test() throws Exception {
        ProcessResult installResult = runProcess(List.of("cmd.exe", "/c", "gradle", "installDist", "--console=plain", "--no-daemon"));
        assertEquals(0, installResult.exitCode(),
                "installDist should produce the perseus launcher distribution");

        Path launcher = Path.of("build", "install", "perseus", "bin", "perseus.bat");
        assertTrue(Files.exists(launcher),
                "installDist should generate perseus.bat");

        Path outDir = BUILD_DIR.resolve("launcher-out");
        Files.createDirectories(outDir);

        ProcessResult runResult = runProcess(List.of(
                launcher.toAbsolutePath().toString(),
                "test/algol/core/hello.alg",
                "-d",
                outDir.toString()));

        assertEquals(0, runResult.exitCode(),
                "perseus launcher should compile hello.alg successfully");

        Path classFile = outDir.resolve("gnb/perseus/programs/Hello.class");
        assertTrue(Files.exists(classFile),
                "perseus launcher should emit the compiled class file");

        String output = runClass(outDir, "gnb.perseus.programs.Hello");
        assertEquals("Hello World", output.trim(),
                "Program compiled through the perseus launcher should run successfully");
    }
}
