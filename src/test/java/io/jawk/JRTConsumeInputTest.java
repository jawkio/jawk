package io.jawk;

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

import org.junit.Test;
import io.jawk.AwkTestSupport;

/**
 * Tests for the {@link io.jawk.jrt.JRT#consumeInput} method.
 */
public class JRTConsumeInputTest {

	/**
	 * Ensures that variable assignments interleaved with filenames in
	 * {@code ARGV} correctly advance {@code NR}.
	 *
	 * @throws Exception if the AWK invocation fails
	 */
	@Test
	public void testVariableAssignmentBetweenFilesIncrementsNR() throws Exception {
		AwkTestSupport
				.awkTest("variable assignments interleaved with filenames advance NR")
				.file("file1", "a\n")
				.file("file2", "b\n")
				.script("{ next } \nEND { print NR }")
				.operand("{{file1}}", "X=1", "{{file2}}")
				.expectLines("3")
				.runAndAssert();
	}

	/**
	 * Ensures ARGC-only scripts can still control input traversal when ARGV is not
	 * materialized.
	 *
	 * @throws Exception if the AWK invocation fails
	 */
	@Test
	public void testArgcOnlyScriptCanForceStdinTraversal() throws Exception {
		AwkTestSupport
				.awkTest("argc-only script forces stdin traversal")
				.file("file1", "from-file\n")
				.script("BEGIN { ARGC = 0 } { print $0 }")
				.operand("{{file1}}")
				.stdin("from-stdin\n")
				.expectLines("from-stdin")
				.runAndAssert();
	}

	/**
	 * Ensures operand-list ARGC assignments still affect traversal even when ARGV
	 * is not materialized.
	 *
	 * @throws Exception if the AWK invocation fails
	 */
	@Test
	public void testOperandArgcAssignmentAffectsTraversalWithoutArgvOffset() throws Exception {
		AwkTestSupport
				.awkTest("operand argc assignment affects traversal without argv offset")
				.file("file1", "from-file\n")
				.script("{ print $0 }")
				.operand("ARGC=0", "{{file1}}")
				.stdin("from-stdin\n")
				.expectLines("from-stdin")
				.runAndAssert();
	}

	/**
	 * Ensures large ARGC values assigned from operands do not prevent normal
	 * traversal when ARGV is unreferenced.
	 *
	 * @throws Exception if the AWK invocation fails
	 */
	@Test
	public void testLargeOperandArgcAssignmentStillTraversesBoundedArgvView() throws Exception {
		AwkTestSupport
				.awkTest("large operand ARGC assignment remains bounded by ARGV view")
				.file("file1", "from-file\n")
				.script("{ print FILENAME \":\" $0 } END { print NR }")
				.operand("ARGC=5000000", "{{file1}}")
				.expectLines("{{file1}}:from-file", "1")
				.runAndAssert();
	}

	/**
	 * Ensures oversized ARGC values are handled safely during traversal without
	 * throwing overflow exceptions.
	 *
	 * @throws Exception if the AWK invocation fails
	 */
	@Test
	public void testOversizedOperandArgcAssignmentDoesNotOverflowTraversal() throws Exception {
		AwkTestSupport
				.awkTest("oversized operand ARGC assignment is clamped for traversal")
				.file("file1", "from-file\n")
				.script("{ print FILENAME \":\" $0 } END { print NR }")
				.operand("ARGC=1e309", "{{file1}}")
				.expectLines("{{file1}}:from-file", "1")
				.runAndAssert();
	}
}
