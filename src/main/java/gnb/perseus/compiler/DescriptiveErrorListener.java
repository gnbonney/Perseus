// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.compiler;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;

/**
 * @author Greg Bonney
 *
 */
public class DescriptiveErrorListener extends BaseErrorListener {
	private final String sourceName;
	private final List<CompilerDiagnostic> diagnostics = new ArrayList<>();

	public DescriptiveErrorListener(String sourceName) {
		this.sourceName = sourceName;
	}

	public boolean hasErrors() {
		return !diagnostics.isEmpty();
	}

	public List<CompilerDiagnostic> getDiagnostics() {
		return List.copyOf(diagnostics);
	}

	@Override
	public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
			int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
		diagnostics.add(CompilerDiagnostic.error(
				"PERS1001",
				sourceName,
				line,
				charPositionInLine + 1,
				msg));
	}

}
