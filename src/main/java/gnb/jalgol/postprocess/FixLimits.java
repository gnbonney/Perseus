package gnb.jalgol.postprocess;

import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.*;

public class FixLimits {
    public static void main(String[] args) throws Exception {
        String inputClass = args.length > 0 ? args[0] : "gnb/jalgol/programs/YourClass.class";
        String outputClass = args.length > 1 ? args[1] : "fixed/YourClass.class";

        ClassReader cr = new ClassReader(new FileInputStream(inputClass));

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        // Optional: wrap in CheckClassAdapter for detailed verification
        CheckClassAdapter verifier = new CheckClassAdapter(cw, true);  // true = print errors to stderr

        ClassVisitor cv = verifier;  // or just cw if no check needed

        cr.accept(cv, 0);

        byte[] fixedBytes = cw.toByteArray();

        try (FileOutputStream fos = new FileOutputStream(outputClass)) {
            fos.write(fixedBytes);
        }

        System.out.println("Fixed class written to " + outputClass);
    }
}