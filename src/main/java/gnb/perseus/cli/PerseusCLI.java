package gnb.perseus.cli;

import gnb.perseus.compiler.CompilationFailedException;
import gnb.perseus.compiler.PerseusCompiler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class PerseusCLI {

    public static void main(String[] args) {
        try {
            CliOptions options = parseArgs(args);
            run(options);
        } catch (CompilationFailedException e) {
            for (var diagnostic : e.getDiagnostics()) {
                System.err.println(diagnostic.format());
            }
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Internal compiler error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(CliOptions options) throws Exception {
        Files.createDirectories(options.outputDir());

        String className = options.className() != null ? options.className() : inferClassName(options.inputFile());
        Path jasminFile = PerseusCompiler.compileToFile(
                options.inputFile().toString(),
                "gnb/perseus/programs",
                className,
                options.outputDir(),
                effectiveClasspath(options));

        PerseusCompiler.assemble(jasminFile, options.outputDir());

        if (options.jarFile() != null) {
            createJar(options.outputDir(), options.jarFile(), "gnb.perseus.programs." + className);
            System.out.println("Compilation successful. JAR generated at: " + options.jarFile());
        } else {
            System.out.println("Compilation successful. Classes generated in: " + options.outputDir());
        }
    }

    private static void printUsage() {
        System.err.println("Usage: perseus <inputFile> [-d <outputDir>] [--jar <jarFile>] [--class-name <name>]");
    }

    private static CliOptions parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing input file.");
        }

        Path inputFile = null;
        Path outputDir = Paths.get("build/perseus-out");
        Path jarFile = null;
        String className = null;
        List<Path> classpathEntries = new ArrayList<>();

        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-d" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for -d");
                    outputDir = Paths.get(args[++i]);
                }
                case "--jar" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for --jar");
                    jarFile = Paths.get(args[++i]);
                }
                case "--class-name" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for --class-name");
                    className = args[++i];
                }
                case "-cp", "--classpath" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + arg);
                    String value = args[++i];
                    for (String entry : value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                        if (!entry.isBlank()) {
                            classpathEntries.add(Paths.get(entry));
                        }
                    }
                }
                default -> positional.add(arg);
            }
        }

        if (positional.isEmpty()) {
            throw new IllegalArgumentException("Missing input file.");
        }
        if (positional.size() > 1) {
            throw new IllegalArgumentException("Only a single input file is supported by the current CLI.");
        }

        inputFile = Paths.get(positional.get(0));
        return new CliOptions(inputFile, outputDir, jarFile, className, classpathEntries);
    }

    private static List<Path> effectiveClasspath(CliOptions options) {
        List<Path> roots = new ArrayList<>();
        roots.add(options.outputDir());
        roots.addAll(options.classpathEntries());
        return roots;
    }

    private static String inferClassName(Path inputFile) {
        String fileName = inputFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char ch : stem.toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                result.append(capitalizeNext ? Character.toUpperCase(ch) : ch);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }
        if (result.isEmpty()) {
            return "Program";
        }
        return result.toString();
    }

    private static void createJar(Path classRoot, Path jarFile, String mainClass) throws IOException {
        if (jarFile.getParent() != null) {
            Files.createDirectories(jarFile.getParent());
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (OutputStream os = Files.newOutputStream(jarFile);
             JarOutputStream jar = new JarOutputStream(os, manifest)) {
            Files.walk(classRoot)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> addJarEntry(jar, classRoot, path));
        }
    }

    private static void addJarEntry(JarOutputStream jar, Path classRoot, Path file) {
        String entryName = classRoot.relativize(file).toString().replace('\\', '/');
        try (InputStream in = Files.newInputStream(file)) {
            jar.putNextEntry(new JarEntry(entryName));
            in.transferTo(jar);
            jar.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("Failed to add JAR entry " + entryName, e);
        }
    }

    private record CliOptions(Path inputFile, Path outputDir, Path jarFile, String className, List<Path> classpathEntries) {}
}
