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
package gnb.algol.programs;

/**
 * @author Greg Bonney
 *
 */
public class Example1 {
	public static void main(String[] args) {
		double x,y,u;
	    x=5/13;y=12/13;
	    u=0.6*x-0.8*y;
	    y=0.8*x+0.6*y;
	    x=u;
	}
}
