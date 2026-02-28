/*******************************************************************************
 * Copyright (c) 2017 Greg Bonney and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Greg Bonney - initial design and implementation
 *******************************************************************************/
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
