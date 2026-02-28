// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import org.antlr.v4.runtime.BaseErrorListener;

/**
 * @author Greg Bonney
 *
 */
public class DescriptiveErrorListener extends BaseErrorListener {
	public static DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();

	@Override
	public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
			int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
		String sourceName = recognizer.getInputStream().getSourceName();
		if (!sourceName.isEmpty()) {
			sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
		}
		System.err.println(sourceName + "line " + line + ":" + charPositionInLine + " " + msg);
	}

}
