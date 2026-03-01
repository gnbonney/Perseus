// Copyright (c) 2017-2026 Greg Bonney

package gnb.jalgol.compiler;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @author Greg Bonney
 *
 */
public class AntlrAlgolListenerTest {

	@Test
	public void hello() {
		String output = AntlrAlgolListener.compile(
				"test/algol/hello.alg", "gnb/jalgol/programs", "Hello");
		System.out.println(output);
		assertNotEquals("NO OUTPUT", output, "Compilation should succeed");
		assertTrue(output.contains(".class public gnb/jalgol/programs/Hello"),
				"Output should declare the correct class");
		assertTrue(output.contains(".method public static main([Ljava/lang/String;)V"),
				"Output should declare a main method");
		assertTrue(output.endsWith(".end method"),
				"Output should close the main method");
	}

}
