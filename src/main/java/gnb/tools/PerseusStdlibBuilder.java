// Copyright (c) 2017-2026 Greg Bonney

package gnb.tools;

import gnb.perseus.compiler.PerseusCompiler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Builds the compiled Perseus standard library from source.
 */
public final class PerseusStdlibBuilder {
    private PerseusStdlibBuilder() {
    }

    public static void main(String[] args) throws Exception {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get("src/main/perseus/stdlib");
        Path classOutputDir = args.length > 1 ? Paths.get(args[1]) : Paths.get("build/perseus-stdlib/classes");
        Path jarFile = args.length > 2 ? Paths.get(args[2]) : Paths.get("build/libs/perseus-stdlib.jar");
        build(sourceRoot, classOutputDir, jarFile);
    }

    public static void buildClasses(Path sourceRoot, Path classOutputDir) throws Exception {
        Files.createDirectories(classOutputDir);
        copyRuntimeSupportClass("gnb/perseus/runtime/TextOutputSupport.class", classOutputDir);

        List<Path> sources = collectSources(sourceRoot);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No Perseus stdlib source files found under " + sourceRoot);
        }

        for (Path source : sources) {
            String namespace = PerseusCompiler.detectNamespace(source.toString());
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("Stdlib source file must declare a namespace: " + source);
            }
            String packageName = namespace.replace('.', '/');
            String className = inferClassName(source);
            Path jasminFile = PerseusCompiler.compileToFileInternal(
                    source.toString(),
                    packageName,
                    className,
                    classOutputDir,
                    List.of(classOutputDir),
                    false);
            PerseusCompiler.assemble(jasminFile, classOutputDir);
        }
    }

    public static void build(Path sourceRoot, Path classOutputDir, Path jarFile) throws Exception {
        if (jarFile.getParent() != null) {
            Files.createDirectories(jarFile.getParent());
        }
        buildClasses(sourceRoot, classOutputDir);
        createJar(classOutputDir, jarFile);
    }

    private static List<Path> collectSources(Path sourceRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".alg"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
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
        return result.isEmpty() ? "Program" : result.toString();
    }

    private static void copyRuntimeSupportClass(String resourceName, Path classOutputDir) throws IOException {
        try (InputStream in = PerseusStdlibBuilder.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Runtime support class not found on the build classpath: " + resourceName);
            }
            Path target = classOutputDir.resolve(resourceName.replace('/', java.io.File.separatorChar));
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void createJar(Path classRoot, Path jarFile) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

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
}
