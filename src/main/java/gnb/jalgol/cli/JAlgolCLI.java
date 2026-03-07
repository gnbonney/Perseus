package gnb.jalgol.cli;

import gnb.jalgol.compiler.AntlrAlgolListener;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JAlgolCLI {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java JAlgolCLI <inputFile> <outputDir> <className>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputDir = args[1];
        String className = args[2];

        try {
            Path outputPath = AntlrAlgolListener.compileToFile(inputFile, "gnb/jalgol/programs", className, Paths.get(outputDir));
            System.out.println("Compilation successful. Jasmin file generated at: " + outputPath);
        } catch (Exception e) {
            System.err.println("Compilation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}