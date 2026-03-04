// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

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

	public static Path compileToFile(String algolFile, String packageName, String className, Path outputDir)
			throws IOException {
		String jasminSource = compile(algolFile, packageName, className);
		Files.createDirectories(outputDir);
		Path jasminFile = outputDir.resolve(className + ".j");
		Files.writeString(jasminFile, jasminSource);
		return jasminFile;
	}

	public static void assemble(Path jasminFile, Path classOutputDir) throws IOException, InterruptedException {
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
		String output = "NO OUTPUT";
		try {
			ANTLRInputStream is = new ANTLRInputStream(new FileReader(fileName));
			AlgolLexer lexer = new AlgolLexer(is);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			AlgolParser parser = new AlgolParser(tokens);
			lexer.removeErrorListeners();
			lexer.addErrorListener(DescriptiveErrorListener.INSTANCE);
			parser.removeErrorListeners();
			parser.addErrorListener(DescriptiveErrorListener.INSTANCE);
			ProgramContext programContext = parser.program();
			if (programContext != null) {
				ParseTreeWalker walker = new ParseTreeWalker();

				// Pass 1: build symbol table (variable names and types, in declaration order)
				SymbolTableBuilder symBuilder = new SymbolTableBuilder();
				walker.walk(symBuilder, programContext);
				Map<String, String> symbolTable = symBuilder.getSymbolTable();

				// Assign JVM local variable slots: slot 0 = args, doubles take 2 slots, ints take 1
				Map<String, Integer> localIndex = new LinkedHashMap<>();
				int nextLocal = 1;
				for (Map.Entry<String, String> entry : symbolTable.entrySet()) {
					String name = entry.getKey();
					String type = entry.getValue();
					localIndex.put(name, nextLocal);
					nextLocal += ("integer".equals(type) || "boolean".equals(type)) ? 1 : 2;
				}
				int numLocals = Math.max(nextLocal, 1); // always at least 1 for args

				// Pass 1.5: type inference for expressions
				TypeInferencer typeInf = new TypeInferencer(symbolTable);
				walker.walk(typeInf, programContext);
				Map<AlgolParser.ExprContext, String> exprTypes = typeInf.getExprTypes();

				// Pass 2: generate Jasmin code
				String source = Paths.get(fileName).getFileName().toString();
				CodeGenerator codegen = new CodeGenerator(source, packageName, className, symbolTable, localIndex, numLocals, exprTypes);
				walker.walk(codegen, programContext);
				output = codegen.getOutput();
			}
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return "ERROR: " + e.getMessage() + "\n" + sw.toString();
		}
		return output;
	}
}
