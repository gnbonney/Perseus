// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import gnb.jalgol.compiler.antlr.AlgolBaseListener;
import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.antlr.AlgolParser.ProgramContext;

/**
 * @author Greg Bonney
 *
 */
public class AntlrAlgolListener extends AlgolBaseListener {
	private String source;
	private String output;
	private String packageName;
	private String className;
	
	public static String getCurrentClassAndMethodNames() {
	    final StackTraceElement e = Thread.currentThread().getStackTrace()[2];
	    final String s = e.getClassName();
	    return s.substring(s.lastIndexOf('.') + 1, s.length()) + "." + e.getMethodName();
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	@Override
	public void enterProgram(ProgramContext ctx) {
		System.out.println("\n*** "+getCurrentClassAndMethodNames());
		super.enterProgram(ctx);
		output = ".source " + source + "\n" + ".class public " + packageName + "/" + className + "\n"
				+ ".super java/lang/Object\n\n" + ".method public <init>()V\n" + ".limit stack 1\n"
				+ ".limit locals 1\n" + "aload_0\n" + "invokespecial java/lang/Object/<init>()V\n" + "return\n"
				+ ".end method\n\n" + ".method public static main([Ljava/lang/String;)V\n" + ".limit stack 2\n"
				+ ".limit locals 1";
	}

	@Override
	public void exitProgram(ProgramContext ctx) {
		System.out.println("\n*** "+getCurrentClassAndMethodNames());
		super.exitProgram(ctx);
		output += "return\n" + ".end method";
	}

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
				AntlrAlgolListener listener = new AntlrAlgolListener();
				Path p = Paths.get(fileName);
				listener.setSource(p.getFileName().toString());
				listener.setPackageName(packageName);
				listener.setClassName(className);
				walker.walk(listener, programContext);
				output = listener.getOutput();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	@Override
	public void visitTerminal(TerminalNode node) {
		System.out.print("\n" + node.getText() + " " + node.getSymbol());
	}

}
