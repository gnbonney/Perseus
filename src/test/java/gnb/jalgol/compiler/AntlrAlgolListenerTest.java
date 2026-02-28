// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import org.junit.jupiter.api.Test;

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
