import java.io.*;

public class TestManBoy {
    public static void main(String[] args) throws Exception {
        // Compile and run ManBoy
        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", "build/test-algol", "gnb.perseus.programs.ManBoy"
        );
        pb.directory(new File("c:/Users/gnbon/Projects/Perseus"));
        
        // Capture output
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String output;
        StringBuilder result = new StringBuilder();
        while ((output = br.readLine()) != null) {
            result.append(output).append("\n");
        }
        
        // Wait and check exit code
        int exitCode = p.waitFor();
        
        System.out.println("ManBoy output: [" + result.toString().trim() + "]");
        System.out.println("Exit code: " + exitCode);
        System.out.println("Expected: -67.0");
    }
}
