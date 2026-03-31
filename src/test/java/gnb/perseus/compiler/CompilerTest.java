package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CompilerTest {

    static final Path BUILD_DIR = Paths.get("build/test-algol");

    static String runClass(Path classDir, String className) throws Exception {
    	List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
    	System.out.println("runClass: " + cmd);
    	ProcessBuilder pb = new ProcessBuilder(cmd);
    	pb.redirectErrorStream(false);
    	Process p = pb.start();
    	p.getOutputStream().close(); // close subprocess stdin
    	String stdout;
    	String stderr;
    	try (var out = p.getInputStream(); var err = p.getErrorStream()) {
    		stdout = new String(out.readAllBytes());
    		stderr = new String(err.readAllBytes());
    	}
    	int exitCode = p.waitFor();
    	System.out.println("runClass: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
    	assertEquals(0, exitCode, "Process failed for " + className + ": exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
    	return stdout;
    }

    static String runClassWithClasspath(Path classDir, String className, Path extraClassDir) throws Exception {
        String classpath = classDir + java.io.File.pathSeparator + extraClassDir;
        List<String> cmd = java.util.Arrays.asList("java", "-cp", classpath, className);
        System.out.println("runClassWithClasspath: " + cmd);
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
        System.out.println("runClassWithClasspath: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        assertEquals(0, exitCode, "Process failed for " + className + ": exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "]");
        return stdout;
    }

    record TimedRunResult(String stdout, String stderr, int exitCode, boolean timedOut) {}

	static TimedRunResult runClassForAtMost(Path classDir, String className, long timeoutMs) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClassForAtMost: " + cmd + " timeout=" + timeoutMs + "ms");
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(false);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin
		boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
		boolean timedOut = false;
		if (!finished) {
			timedOut = true;
			p.destroyForcibly();
			p.waitFor();
			System.out.println("runClassForAtMost: process killed after timeout");
		}
		String stdout;
		String stderr;
		try (var out = p.getInputStream(); var err = p.getErrorStream()) {
			stdout = new String(out.readAllBytes());
			stderr = new String(err.readAllBytes());
		}
		int exitCode = p.exitValue();
		System.out.println("runClassForAtMost: exit=" + exitCode + " stdout=[" + stdout + "] stderr=[" + stderr + "] timedOut=" + timedOut);
		return new TimedRunResult(stdout, stderr, exitCode, timedOut);
	}

	static String runClassWithTimeout(Path classDir, String className, long timeoutMs) throws Exception {
		TimedRunResult result = runClassForAtMost(classDir, className, timeoutMs);
		if (result.timedOut()) {
			fail("Process timed out for " + className + " after " + timeoutMs + "ms"
					+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		}
		assertEquals(0, result.exitCode(),
				"Process failed for " + className + " with timeout: exit=" + result.exitCode()
						+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		return result.stdout();
	}

	static String runClassExpectTimeout(Path classDir, String className, long timeoutMs) throws Exception {
		TimedRunResult result = runClassForAtMost(classDir, className, timeoutMs);
		if (!result.timedOut()) {
			fail("Expected " + className + " to time out after " + timeoutMs + "ms"
					+ " but exit=" + result.exitCode()
					+ " stdout=[" + result.stdout() + "] stderr=[" + result.stderr() + "]");
		}
		return result.stdout();
	}

	static String runClassWithInput(Path classDir, String className, String input) throws Exception {
		List<String> cmd = java.util.Arrays.asList("java", "-cp", classDir.toString(), className);
		System.out.println("runClassWithInput: " + cmd + " input=[" + input.replace("\n", "\\n") + "]");
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		// Write input to subprocess stdin
		try (java.io.OutputStream os = p.getOutputStream()) {
			os.write(input.getBytes());
		}
		String output = new String(p.getInputStream().readAllBytes());
		int exitCode = p.waitFor();
		System.out.println("runClassWithInput: exit=" + exitCode + " output=[" + output + "]");
		return output;
	}
    
}
