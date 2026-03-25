// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.compiler;

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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import gnb.perseus.compiler.antlr.PerseusBaseListener;
import gnb.perseus.compiler.antlr.PerseusLexer;
import gnb.perseus.compiler.antlr.PerseusParser;
import gnb.perseus.compiler.antlr.PerseusParser.ProgramContext;

/**
 * Facade for the Perseus-to-Jasmin compiler pipeline.
 * Orchestrates the multi-pass compilation flow:
 *   Pass 1 - SymbolTableBuilder: collect declarations and procedure metadata
 *   Pass 1.5 - TypeInferencer: resolve expression types before emission
 *   Pass 2 - CodeGenerator: emit Jasmin assembly
 */
public class PerseusCompiler {

	/** Jasmin source for the Thunk interface needed by call-by-name compiled programs. */
	private static final String THUNK_INTERFACE_JASMIN =
		".interface public gnb/perseus/compiler/Thunk\n" +
		".super java/lang/Object\n\n" +
		".method public abstract get()Ljava/lang/Object;\n.end method\n\n" +
		".method public abstract sync()V\n.end method\n\n" +
		".method public abstract set(Ljava/lang/Object;)V\n.end method\n";

	/** Jasmin source for procedure reference interfaces needed by procedure-as-values compiled programs. */
	private static final String PROCEDURE_INTERFACES_JASMIN =
		".interface public gnb/perseus/compiler/VoidProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)V\n.end method\n\n" +
		".interface public gnb/perseus/compiler/RealProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)D\n.end method\n\n" +
		".interface public gnb/perseus/compiler/IntegerProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)I\n.end method\n\n" +
		".interface public gnb/perseus/compiler/StringProcedure\n" +
		".super java/lang/Object\n\n" +
		".method public abstract invoke([Ljava/lang/Object;)Ljava/lang/String;\n.end method\n";

	public static Path compileToFile(String algolFile, String packageName, String className, Path outputDir)
			throws IOException {
		// Run the full pipeline so we can access the CodeGenerator for thunk outputs
		CodeGenerator codegen;
		try {
			codegen = runPipeline(algolFile, packageName, className);
			if (codegen == null) {
				throw new IOException("runPipeline returned null");
			}
		} catch (CompilationFailedException e) {
			throw e;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String causeText = e.getMessage() != null ? e.getMessage() : e.toString();
			String message = "Compilation failed: " + causeText + "\n" + sw.toString();
			throw new IOException(message, e);
		}
		Files.createDirectories(outputDir);

		// Write main .j file
		Path jasminFile = outputDir.resolve(className + ".j");
		try {
			Files.writeString(jasminFile, codegen.getOutput());
		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException("Failed to write main Jasmin output", e);
		}

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
		} catch (CompilationFailedException e) {
			return String.join(System.lineSeparator(),
					e.getDiagnostics().stream().map(CompilerDiagnostic::format).toList());
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
		PerseusLexer lexer = new PerseusLexer(is);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PerseusParser parser = new PerseusParser(tokens);
		DescriptiveErrorListener errorListener = new DescriptiveErrorListener(fileName);
		lexer.removeErrorListeners();
		lexer.addErrorListener(errorListener);
		parser.removeErrorListeners();
		parser.addErrorListener(errorListener);
		ProgramContext programContext = parser.program();
		if (errorListener.hasErrors()) {
			throw new CompilationFailedException(errorListener.getDiagnostics());
		}

		// Pass 1: build symbol table
		SymbolTableBuilder symBuilder = new SymbolTableBuilder();
		ParseTreeWalker walker = new ParseTreeWalker();
		walker.walk(symBuilder, programContext);
		Map<String, String> symbolTable = symBuilder.getSymbolTable();
		Map<String, String> mainSymbolTable = symBuilder.getMainSymbolTable();
		Map<String, int[]> arrayBounds = symBuilder.getArrayBounds();
		Map<String, java.util.List<int[]>> arrayBoundPairs = symBuilder.getArrayBoundPairs();
		Map<String, SymbolTableBuilder.ProcInfo> procedures = symBuilder.getProcedures();
		Map<String, PerseusParser.SwitchDeclContext> switchDeclarations = symBuilder.getSwitchDeclarations();

        // Check for procedure variables (procedure names that are used in assignments or expressions)
        Set<String> procedureVariables = new LinkedHashSet<>();
        walker.walk(new PerseusBaseListener() {
            private final Deque<String> currentProcedure = new ArrayDeque<>();

            @Override
            public void enterProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
                currentProcedure.push(ctx.identifier().getText());
            }

            @Override
            public void exitProcedureDecl(PerseusParser.ProcedureDeclContext ctx) {
                currentProcedure.pop();
            }

            private Set<String> collectVarNames(ParseTree tree) {
                Set<String> names = new LinkedHashSet<>();
                if (tree instanceof PerseusParser.VarExprContext ve) {
                    names.add(ve.identifier().getText());
                } else {
                    for (int i = 0; i < tree.getChildCount(); i++) {
                        names.addAll(collectVarNames(tree.getChild(i)));
                    }
                }
                return names;
            }

            @Override
            public void enterAssignment(PerseusParser.AssignmentContext ctx) {
                for (PerseusParser.LvalueContext lv : ctx.lvalue()) {
                    String name = lv.identifier().getText();
                    String type = symbolTable.get(name);
                    // If a procedure name is used as an L-value, it may be a procedure variable.
                    if (type != null && type.startsWith("procedure:")) {
                        if (name.equals(currentProcedure.peek())) {
                            // Assigning to the current procedure from inside its own body.
                            // If there are multiple targets (e.g. B := A := ...), treat this as a
                            // procedure-variable assignment. Otherwise, treat it as a return value
                            // assignment (unless the RHS is a procedure reference).
                            if (ctx.lvalue().size() > 1) {
                                procedureVariables.add(name);
                            } else {
                                SymbolTableBuilder.ProcInfo info = procedures.get(name);
                                if (info == null || "void".equals(info.returnType)) {
                                    // Void procedure: P := hello is always a procedure variable assignment.
                                    procedureVariables.add(name);
                                } else {
                                    // Typed procedure: treat as a procedure-variable assignment if the RHS
                                    // is a direct procedure reference or returns a procedure.
                                    PerseusParser.ExprContext rhs = ctx.expr();
                                    for (String rhsName : collectVarNames(rhs)) {
                                        if (procedures.containsKey(rhsName)) {
                                            procedureVariables.add(name);
                                            break;
                                        }
                                    }
                                }
                            }
                        } else {
                            // Assignment to an outer procedure name is always a procedure-variable binding update.
                            procedureVariables.add(name);
                        }
                    }
                }
            }

        }, programContext);

        // Add detected procedure variables to mainSymbolTable so they get JVM slots.
		// Only add names that were actually observed as variable references (assignments or VarExpr),
		// NOT all no-param procedures (which would cause outer calls to route through a variable slot).
		for (String pv : procedureVariables) {
			String type = symbolTable.get(pv);
			if (type != null && type.startsWith("procedure:")) {
				if (!mainSymbolTable.containsKey(pv)) {
					System.out.println("DEBUG: Adding procedure variable to mainSymbolTable: " + pv);
					mainSymbolTable.put(pv, type);
				}
			}
		}

		// Assign JVM local variable slots
		Map<String, Integer> localIndex = new LinkedHashMap<>();
		int nextLocal = 1;
		
		// Map for procedure variables (marker map; value -1 indicates no local slot)
		Map<String, Integer> procVarSlotsMap = new LinkedHashMap<>();
		for (String pv : procedureVariables) {
			String type = symbolTable.get(pv);
			if (type != null && type.startsWith("procedure:")) {
				procVarSlotsMap.put(pv, -1);
			}
		}
		
		// Sort mainSymbolTable entries by name or maintain order to ensure stability?
		// SymbolTableBuilder uses LinkedHashMap so order is preserved.
		System.out.println("DEBUG: Assigning slots for mainSymbolTable: " + mainSymbolTable);
		for (Map.Entry<String, String> entry : mainSymbolTable.entrySet()) {
			String name = entry.getKey();
			String type = entry.getValue();
			if (type.endsWith("[]")) continue;
			// Procedure variables are stored in static fields, not local slots.
			// This ensures calls/assignments always use the shared binding and avoids
			// uninitialized local slots (e.g. ProcVar, ProcTypedSimple).
		}
		int numLocals = Math.max(nextLocal, 1);

		// Pass 1.5: type inference
		TypeInferencer typeInf = new TypeInferencer(fileName, symbolTable);
		try {
			walker.walk(typeInf, programContext);
		} catch (DiagnosticException e) {
			throw new CompilationFailedException(java.util.List.of(e.getDiagnostic()));
		}
		Map<PerseusParser.ExprContext, String> exprTypes = typeInf.getExprTypes();

		// Pass 2: code generation
		String source = Paths.get(fileName).getFileName().toString();
		CodeGenerator codegen = new CodeGenerator(source, packageName, className,
				mainSymbolTable, localIndex, numLocals, exprTypes, arrayBounds, arrayBoundPairs,
				symBuilder.getProcedures(), switchDeclarations, procVarSlotsMap);
		walker.walk(codegen, programContext);
		return codegen;
	}
}



