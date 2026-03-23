package gnb.jalgol.postprocess;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FixLimits {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            fixClass(Paths.get("gnb/jalgol/programs/YourClass.class"), Paths.get("fixed/YourClass.class"));
            return;
        }
        if (args.length == 2) {
            fixClass(Paths.get(args[0]), Paths.get(args[1]));
            return;
        }
        if (args.length == 1) {
            fixClassFamilyInPlace(Paths.get(args[0]));
            return;
        }
        for (String arg : args) {
            fixClassInPlace(Paths.get(arg));
        }
    }

    public static void fixClassFamilyInPlace(Path mainClass) throws Exception {
        for (Path classFile : relatedClassFiles(mainClass)) {
            fixClassInPlace(classFile);
        }
    }

    public static List<Path> relatedClassFiles(Path mainClass) throws IOException {
        Path normalizedMainClass = mainClass.toAbsolutePath().normalize();
        if (!Files.exists(normalizedMainClass)) {
            throw new FileNotFoundException(normalizedMainClass.toString());
        }

        String fileName = normalizedMainClass.getFileName().toString();
        if (!fileName.endsWith(".class")) {
            throw new IllegalArgumentException("Expected a .class file: " + normalizedMainClass);
        }

        Path parent = normalizedMainClass.getParent();
        if (parent == null) {
            return List.of(normalizedMainClass);
        }

        String baseName = fileName.substring(0, fileName.length() - ".class".length());
        List<Path> related = new ArrayList<>();
        related.add(normalizedMainClass);

        List<Path> companions = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, baseName + "$*.class")) {
            for (Path companion : stream) {
                companions.add(companion.toAbsolutePath().normalize());
            }
        }
        companions.sort(Comparator.comparing(path -> path.getFileName().toString()));
        related.addAll(companions);
        return related;
    }

    public static void fixClassInPlace(Path classFile) throws Exception {
        Path normalizedClassFile = classFile.toAbsolutePath().normalize();
        Path tempFile = Files.createTempFile(normalizedClassFile.getParent(),
                normalizedClassFile.getFileName().toString(), ".tmp");
        try {
            fixClass(normalizedClassFile, tempFile);
            Files.move(tempFile, normalizedClassFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public static void fixClass(Path inputClass, Path outputClass) throws Exception {
        byte[] fixedBytes = fixClassBytes(inputClass);
        Path normalizedOutputClass = outputClass.toAbsolutePath().normalize();
        Path parent = normalizedOutputClass.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream fos = Files.newOutputStream(normalizedOutputClass)) {
            fos.write(fixedBytes);
        }
        System.out.println("Fixed class written to " + normalizedOutputClass);
    }

    private static byte[] fixClassBytes(Path inputClass) throws Exception {
        byte[] fixedBytes;
        try (InputStream fis = Files.newInputStream(inputClass)) {
            ClassReader cr = new ClassReader(fis);

            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            // Optional: wrap in CheckClassAdapter for detailed verification
            CheckClassAdapter verifier = new CheckClassAdapter(cw, true);  // true = print errors to stderr

            ClassVisitor cv = verifier;  // or just cw if no check needed

            cr.accept(cv, 0);

            fixedBytes = cw.toByteArray();
        }
        return fixedBytes;
    }
}
