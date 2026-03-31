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

        String packageName = normalizePackageName(options.packageName());
        if (options.inputFiles().size() == 1) {
            Path inputFile = options.inputFiles().get(0);
            String className = options.className() != null ? options.className() : inferClassName(inputFile);
            Path jasminFile = PerseusCompiler.compileToFile(
                    inputFile.toString(),
                    packageName,
                    className,
                    options.outputDir(),
                    effectiveClasspath(options));

            PerseusCompiler.assemble(jasminFile, options.outputDir());

            if (options.jarFile() != null) {
                createJar(options.outputDir(), options.jarFile(), toBinaryClassName(packageName, className));
                System.out.println("Compilation successful. JAR generated at: " + options.jarFile());
            } else {
                System.out.println("Compilation successful. Classes generated in: " + options.outputDir());
            }
        } else {
            validateSharedNamespace(options.inputFiles());
            for (Path inputFile : options.inputFiles()) {
                String className = inferClassName(inputFile);
                Path jasminFile = PerseusCompiler.compileToFile(
                        inputFile.toString(),
                        packageName,
                        className,
                        options.outputDir(),
                        effectiveClasspath(options));
                PerseusCompiler.assemble(jasminFile, options.outputDir());
            }
            System.out.println("Compilation successful. Classes generated in: " + options.outputDir());
        }
    }

    private static void printUsage() {
        System.err.println("Usage: perseus <inputFile> [<inputFile> ...] [-d <outputDir>] [--jar <jarFile>] [--class-name <name>] [--package <name>]");
    }

    private static CliOptions parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing input file.");
        }

        Path outputDir = Paths.get("build/perseus-out");
        Path jarFile = null;
        String className = null;
        String packageName = "gnb.perseus.programs";
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
                case "--package" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for --package");
                    packageName = args[++i];
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

        List<Path> inputFiles = positional.stream().map(Paths::get).toList();
        if (inputFiles.size() > 1 && className != null) {
            throw new IllegalArgumentException("--class-name is only supported when compiling a single input file.");
        }
        if (inputFiles.size() > 1 && jarFile != null) {
            throw new IllegalArgumentException("--jar is only supported when compiling a single input file.");
        }

        return new CliOptions(inputFiles, outputDir, jarFile, className, packageName, classpathEntries);
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

    private static String normalizePackageName(String packageName) {
        return packageName.replace('.', '/').replace('\\', '/');
    }

    private static String toBinaryClassName(String internalPackageName, String className) {
        if (internalPackageName == null || internalPackageName.isBlank()) {
            return className;
        }
        return internalPackageName.replace('/', '.') + "." + className;
    }

    private static void validateSharedNamespace(List<Path> inputFiles) throws Exception {
        String expectedNamespace = null;
        for (Path inputFile : inputFiles) {
            String namespace = PerseusCompiler.detectNamespace(inputFile.toString());
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException(
                        "When compiling multiple source files together, each file must declare the same namespace: "
                                + inputFile);
            }
            if (expectedNamespace == null) {
                expectedNamespace = namespace;
            } else if (!expectedNamespace.equals(namespace)) {
                throw new IllegalArgumentException(
                        "When compiling multiple source files together, all files must declare the same namespace. Expected "
                                + expectedNamespace + " but found " + namespace + " in " + inputFile);
            }
        }
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

    private record CliOptions(List<Path> inputFiles, Path outputDir, Path jarFile, String className, String packageName, List<Path> classpathEntries) {}
}
