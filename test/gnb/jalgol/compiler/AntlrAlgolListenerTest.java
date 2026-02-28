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

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.FileReader;

import org.junit.Test;

import gnb.jalgol.compiler.AntlrAlgolListener;

/**
 * @author Greg Bonney
 *
 */
public class AntlrAlgolListenerTest {

	@Test
	public void hello() {
		System.out.println(AntlrAlgolListener.compile(
				"test/algol/hello.alg", "gnb/jalgol/programs", "Hello"));
	}

}
