// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler.antlr;

import static org.junit.Assert.*;

import java.io.FileReader;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.Test;

import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;

public class AlgolParserTest {

	@Test
	public void parse() {
		try {
			ANTLRInputStream is = new ANTLRInputStream(new FileReader("test/algol/hello.alg"));
			AlgolLexer lexer = new AlgolLexer(is);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			AlgolParser parser = new AlgolParser(tokens);
			ParseTree choose = parser.program();
			if (choose != null) {
				ParseTreeWalker walker = new ParseTreeWalker();
				walker.walk(new ParseTreeListener() {
					@Override
					public void visitTerminal(TerminalNode node) {
						System.out.print("\n" + node.getText() + " " + node.getSymbol());
					}

					@Override
					public void visitErrorNode(ErrorNode node) {
						// System.out.print("\n" + node.getText() + " " + node.getSymbol());
					}

					@Override
					public void exitEveryRule(ParserRuleContext ctx) {
						// System.out.println(ctx.getText());
					}

					@Override
					public void enterEveryRule(ParserRuleContext ctx) {
						// System.out.println(ctx.getText());
					}
				}, choose);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
