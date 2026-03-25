package gnb.perseus.cli;

import gnb.perseus.compiler.CompilationFailedException;
import gnb.perseus.compiler.PerseusCompiler;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PerseusCLI {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java PerseusCLI <inputFile> <outputDir> <className>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputDir = args[1];
        String className = args[2];

        try {
            Path outputPath = PerseusCompiler.compileToFile(inputFile, "gnb/perseus/programs", className, Paths.get(outputDir));
            System.out.println("Compilation successful. Jasmin file generated at: " + outputPath);
        } catch (CompilationFailedException e) {
            for (var diagnostic : e.getDiagnostics()) {
                System.err.println(diagnostic.format());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Internal compiler error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
