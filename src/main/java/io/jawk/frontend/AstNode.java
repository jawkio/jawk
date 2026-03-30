package io.jawk.frontend;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.PrintStream;
import io.jawk.intermediate.AwkTuples;

/**
 * A Jawk abstract syntax tree node. This provides an appropriate public
 * interface to the abstract syntax tree.
 *
 * @author Danny Daglas
 */
public abstract class AstNode {

	/**
	 * Dump a meaningful text representation of this abstract syntax tree node to
	 * the output (print) stream. Either it is called directly by the application
	 * program, or it is called by the parent node of this tree node.
	 *
	 * @param ps The print stream to dump the text representation.
	 */
	public abstract void dump(PrintStream ps);

	/**
	 * Apply semantic checks to this node. The default implementation is to simply
	 * call semanticAnalysis() on all the children of this abstract syntax tree
	 * node. Therefore, this method must be overridden to provide meaningful
	 * semantic analysis / checks.
	 */
	public abstract void semanticAnalysis();

	/**
	 * Appends tuples to the AwkTuples list for this abstract syntax tree node.
	 * Subclasses must implement this method.
	 * <p>
	 * This is called either by the main program to generate a full list of tuples
	 * for the abstract syntax tree, or it is called by other abstract syntax tree
	 * nodes in response to their attempt at populating tuples.
	 *
	 * @param tuples The tuples to populate.
	 * @return The number of items left on the operand stack after these tuples
	 *         have executed.
	 */
	public abstract int populateTuples(AwkTuples tuples);
}
