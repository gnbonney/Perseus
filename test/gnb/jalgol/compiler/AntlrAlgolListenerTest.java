// Copyright (c) 2017-2026 Greg Bonney

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
