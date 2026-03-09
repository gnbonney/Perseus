// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ProgramContext;

/**
 * Façade for the Algol-to-Jasmin compiler.
 * Orchestrates the two-pass compilation pipeline:
 *   Pass 1 — SymbolTableBuilder: collect variable declarations
 *   Pass 2 — CodeGenerator: emit Jasmin assembly
 */
public class AntlrAlgolListener {

	/** Jasmin source for the Thunk interface needed by call-by-name compiled programs. */
	private static final String THUNK_INTERFACE_JASMIN =
		".interface public gnb/jalgol/compiler/Thunk\n" +
		".super java/lang/Object\n\n" +
		".method public abstract get()Ljava/lang/Object;\n.end method\n\n" +
		".method public abstract set(Ljava/lang/Object;)V\n.end method\n";

	/** Jasmin source for procedure reference interfaces needed by procedure-as-values compiled programs. */
	private static final String PROCEDURE_INTERFACES_JASMIN =
		".interface public gnb/jalgol/compiler/VoidProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)V\n.end method\n\n" +
		".interface public gnb/jalgol/compiler/RealProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)D\n.end method\n\n" +
		".interface public gnb/jalgol/compiler/IntegerProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)I\n.end method\n\n" +
		".interface public gnb/jalgol/compiler/StringProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)Ljava/lang/String;\n.end method\n";

	public static Path compileToFile(String algolFile, String packageName, String className, Path outputDir)
			throws IOException {
		// Run the full pipeline so we can access the CodeGenerator for thunk outputs
		CodeGenerator codegen;
		try {
			codegen = runPipeline(algolFile, packageName, className);
		} catch (Exception e) {
			throw new IOException("Compilation failed", e);
		}
		Files.createDirectories(outputDir);

		// Write main .j file
		Path jasminFile = outputDir.resolve(className + ".j");
		Files.writeString(jasminFile, codegen.getOutput());

		// Write each thunk class as its own .j file (Jasmin can only handle one class per file)
		Map<String, String> thunkOutputs = codegen.getThunkClassOutputs();
		for (Map.Entry<String, String> thunk : thunkOutputs.entrySet()) {
			Path thunkFile = outputDir.resolve(thunk.getKey() + ".j");
			Files.writeString(thunkFile, thunk.getValue());
		}

		// Write each procedure reference class as its own .j file
		Map<String, String> procRefOutputs = codegen.getProcRefClassOutputs();
		for (Map.Entry<String, String> procRef : procRefOutputs.entrySet()) {
			Path procRefFile = outputDir.resolve(procRef.getKey() + ".j");
			Files.writeString(procRefFile, procRef.getValue());
		}

		// If there are any thunk classes, also emit the Thunk interface so the compiled
		// program is self-contained and doesn't depend on the compiler's own class files.
		if (!thunkOutputs.isEmpty()) {
			Path thunkIfaceFile = outputDir.resolve("Thunk.j");
			Files.writeString(thunkIfaceFile, THUNK_INTERFACE_JASMIN);
		}

		// If there are any procedure reference classes, emit the procedure interfaces
		if (!procRefOutputs.isEmpty()) {
			Path procIfaceFile = outputDir.resolve("ProcedureInterfaces.j");
			Files.writeString(procIfaceFile, PROCEDURE_INTERFACES_JASMIN);
		}

		// Assemble the procedure interfaces if they were emitted alongside this file
		Path procIface = jasminFile.getParent().resolve("ProcedureInterfaces.j");
		if (java.nio.file.Files.exists(procIface)) {
			try {
				assembleOne(procIface, outputDir);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Assembly of procedure interfaces interrupted", e);
			}
		}

		return jasminFile;
	}

	public static void assemble(Path jasminFile, Path classOutputDir) throws IOException, InterruptedException {
		// Assemble the main file
		assembleOne(jasminFile, classOutputDir);

		// Also assemble any companion thunk class files (e.g. Foo$Thunk0.j, Foo$Thunk1.j)
		String baseName = jasminFile.getFileName().toString().replace(".j", "");
		try (java.nio.file.DirectoryStream<Path> ds =
				java.nio.file.Files.newDirectoryStream(jasminFile.getParent(), baseName + "$*.j")) {
			for (Path companion : ds) {
				assembleOne(companion, classOutputDir);
			}
		} catch (java.nio.file.NoSuchFileException ignored) { }

		// Assemble the Thunk interface if it was emitted alongside this file
		Path thunkIface = jasminFile.getParent().resolve("Thunk.j");
		if (java.nio.file.Files.exists(thunkIface)) {
			assembleOne(thunkIface, classOutputDir);
		}

		// Assemble the ProcedureInterfaces if they were emitted alongside this file
		Path procIface = jasminFile.getParent().resolve("ProcedureInterfaces.j");
		if (java.nio.file.Files.exists(procIface)) {
			assembleOne(procIface, classOutputDir);
		}
	}

	private static void assembleOne(Path jasminFile, Path classOutputDir) throws IOException, InterruptedException {
		Files.createDirectories(classOutputDir);
		// Find jasmin-2.4/jasmin.jar on the classpath (self-contained: includes jas + java_cup)
		String jasminJar = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
				.filter(p -> p.contains("jasmin"))
				.findFirst()
				.orElseThrow(() -> new IOException("jasmin jar not found on classpath"));
		ProcessBuilder pb = new ProcessBuilder(
				"java", "-cp", jasminJar, "jasmin.Main",
				"-d", classOutputDir.toString(), jasminFile.toString());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getOutputStream().close(); // close subprocess stdin so it doesn't block waiting for input
		String jasminOutput = new String(p.getInputStream().readAllBytes());
		int exitCode = p.waitFor();
		if (exitCode != 0) {
			throw new IOException("Jasmin assembly failed (exit " + exitCode + "): " + jasminOutput);
		}
	}

	@SuppressWarnings("deprecation")
	public static String compile(String fileName, String packageName, String className) {
		try {
			return runPipeline(fileName, packageName, className).getOutput();
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return "ERROR: " + e.getMessage() + "\n" + sw.toString();
		}
	}

	/**
	 * Runs the full compilation pipeline and returns the CodeGenerator so callers
	 * can access both the main Jasmin output and any generated thunk class outputs.
	 */
	@SuppressWarnings("deprecation")
	static CodeGenerator runPipeline(String fileName, String packageName, String className) throws Exception {
		ANTLRInputStream is = new ANTLRInputStream(new FileReader(fileName));
		AlgolLexer lexer = new AlgolLexer(is);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		AlgolParser parser = new AlgolParser(tokens);
		lexer.removeErrorListeners();
		lexer.addErrorListener(DescriptiveErrorListener.INSTANCE);
		parser.removeErrorListeners();
		parser.addErrorListener(DescriptiveErrorListener.INSTANCE);
		ProgramContext programContext = parser.program();

		// Pass 1: build symbol table
		SymbolTableBuilder symBuilder = new SymbolTableBuilder();
		ParseTreeWalker walker = new ParseTreeWalker();
		walker.walk(symBuilder, programContext);
		Map<String, String> symbolTable = symBuilder.getSymbolTable();
		Map<String, String> mainSymbolTable = symBuilder.getMainSymbolTable();
		Map<String, int[]> arrayBounds = symBuilder.getArrayBounds();
		Map<String, SymbolTableBuilder.ProcInfo> procedures = symBuilder.getProcedures();

		// Check for procedure variables (procedure names that are used in assignments or expressions)
		Set<String> procedureVariables = new LinkedHashSet<>();
		walker.walk(new AlgolBaseListener() {
			private final Deque<String> currentProcedure = new ArrayDeque<>();

			@Override
			public void enterProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
				currentProcedure.push(ctx.identifier().getText());
			}

			@Override
			public void exitProcedureDecl(AlgolParser.ProcedureDeclContext ctx) {
				currentProcedure.pop();
			}

			@Override
			public void enterAssignment(AlgolParser.AssignmentContext ctx) {
				for (AlgolParser.LvalueContext lv : ctx.lvalue()) {
					String name = lv.identifier().getText();
					String type = symbolTable.get(name);
					// If a procedure name is used as an L-value, it's a procedure variable.
					if (type != null && type.startsWith("procedure:")) {
						// But NOT if it's the current procedure being assigned to (return value for typed procs)
						// For 13A, we simplify: if it's assigned to at all, it's a variable UNLESS it's typed.
						if (!name.equals(currentProcedure.peek())) {
							procedureVariables.add(name);
						}
					}
				}
			}

			@Override
			public void enterVarExpr(AlgolParser.VarExprContext ctx) {
				String name = ctx.identifier().getText();
				String type = symbolTable.get(name);
				// If a procedure name is used in an expression but NOT as a call, it's a variable reference.
				if (type != null && type.startsWith("procedure:")) {
					// Check if it's actually a call.
					boolean isCall = false;
					org.antlr.v4.runtime.tree.ParseTree p = ctx.getParent();
					while (p != null) {
						if (p instanceof AlgolParser.ProcedureCallContext call) {
							if (call.identifier().getText().equals(name)) {
								isCall = true;
								break;
							}
						}
						p = p.getParent();
					}
					if (!isCall) {
						procedureVariables.add(name);
					}
				}
			}

			@Override
			public void enterProcedureCall(AlgolParser.ProcedureCallContext ctx) {
				String name = ctx.identifier().getText();
				String type = symbolTable.get(name);
				// If something is called that looks like a variable...
				// For Milestone 13A, if it's a known procedure variable, it's a variable call.
				if (type != null && type.startsWith("procedure:")) {
					SymbolTableBuilder.ProcInfo info = procedures.get(name);
					// For 13A: any procedure name called at the top level or from another procedure 
					// that has no parameters is potentially a variable.
					if (info != null && info.paramNames.isEmpty()) {
						if (!name.equals(currentProcedure.peek())) {
							procedureVariables.add(name);
						}
					}
				}
			}
		}, programContext);

		// Now add the detected procedure variables to mainSymbolTable so they get slots.
		for (String pv : procedureVariables) {
			String type = symbolTable.get(pv);
			// For 13A, everything in procedureVariables gets a slot.
			if (type != null && type.startsWith("procedure:")) {
				if (!mainSymbolTable.containsKey(pv)) {
					mainSymbolTable.put(pv, type);
				}
			}
		}

		// Also find ALL procedure names and ensure they are in mainSymbolTable if they are variables
		// This is a safety pass for Milestone 13A specifically.
		for (Map.Entry<String, SymbolTableBuilder.ProcInfo> entry : procedures.entrySet()) {
			String name = entry.getKey();
			SymbolTableBuilder.ProcInfo info = entry.getValue();
			String type = symbolTable.get(name);
			if (type != null && type.startsWith("procedure:")) {
				if (info != null && info.paramNames.isEmpty() && !"oneton".equals(name)) {
					System.out.println("DEBUG: Explicitly adding procedure variable to mainSymbolTable: " + name);
					if (!mainSymbolTable.containsKey(name)) {
						mainSymbolTable.put(name, type);
					}
				}
			}
		}

		// Assign JVM local variable slots
		Map<String, Integer> localIndex = new LinkedHashMap<>();
		int nextLocal = 1;
		
		// Map for procedure variables specifically for Milestone 13A
		Map<String, Integer> procVarSlotsMap = new LinkedHashMap<>();
		
		// Sort mainSymbolTable entries by name or maintain order to ensure stability?
		// SymbolTableBuilder uses LinkedHashMap so order is preserved.
		System.out.println("DEBUG: Assigning slots for mainSymbolTable: " + mainSymbolTable);
		for (Map.Entry<String, String> entry : mainSymbolTable.entrySet()) {
			String name = entry.getKey();
			String type = entry.getValue();
			if (type.endsWith("[]")) continue;

			localIndex.put(name, nextLocal);
			if (type.startsWith("procedure:")) {
				procVarSlotsMap.put(name, nextLocal);
			}
			System.out.println("DEBUG: Assigned slot " + nextLocal + " to " + name + " (type " + type + ")");
			nextLocal += "real".equals(type) ? 2 : 1;
		}
		int numLocals = Math.max(nextLocal, 1);

		// Pass 1.5: type inference
		TypeInferencer typeInf = new TypeInferencer(symbolTable);
		walker.walk(typeInf, programContext);
		Map<AlgolParser.ExprContext, String> exprTypes = typeInf.getExprTypes();

		// Pass 2: code generation
		String source = Paths.get(fileName).getFileName().toString();
		CodeGenerator codegen = new CodeGenerator(source, packageName, className,
				mainSymbolTable, localIndex, numLocals, exprTypes, arrayBounds,
				symBuilder.getProcedures(), procVarSlotsMap);
		walker.walk(codegen, programContext);
		return codegen;
	}
}
