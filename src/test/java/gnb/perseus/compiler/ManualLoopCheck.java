package gnb.perseus.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ManualLoopCheck {
    private static final Path BUILD_DIR = Paths.get("build/test-algol");

    public static void main(String[] args) throws Exception {
        check("test/algol/core/loop_break_continue.alg", "LoopBreakContinueTest");
        check("test/algol/core/for_in_array.alg", "ForInArrayTest");
    }

    private static void check(String sourceFile, String className) throws Exception {
        Path jasminFile = PerseusCompiler.compileToFile(
                sourceFile, "gnb/perseus/programs", className, BUILD_DIR);
        String jasminSource = Files.readString(jasminFile);
        System.out.println("=== " + className + " JASMIN ===");
        System.out.println(jasminSource);
        System.out.println("=== END " + className + " JASMIN ===");
        PerseusCompiler.assemble(jasminFile, BUILD_DIR);
        String output = runClass(BUILD_DIR, "gnb.perseus.programs." + className);
        System.out.println(className + " output=[" + output + "]");
    }

    private static String runClass(Path classDir, String className) throws Exception {
        List<String> cmd = Arrays.asList("java", "-cp", classDir.toString(), className);
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
        System.out.println("runClass: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Process failed for " + className + ": exit=" + exitCode
                            + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        }
        return stdout;
    }
}
