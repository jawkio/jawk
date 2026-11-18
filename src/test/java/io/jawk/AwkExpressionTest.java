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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for what {@link AwkExpression} tells about a compiled expression:
 * {@link AwkExpression#isConstant()}, {@link AwkExpression#getConstantValue()}
 * and {@link AwkExpression#getFieldIndex()}.
 */
public class AwkExpressionTest {

	private static final String[] CONSTANTS = {
			"\"enclosure\"",
			"\"\"",
			"42",
			"0",
			"0.10",
			"010",
			"1e3",
			"-5",
			"+\"3\"",
			"2 * 3",
			"1 / 4",
			"2 ^ 10",
			"1 < 2",
			"\"a\" \"b\"",
			"\"a\" \"b\" \"c\"" };

	private static final String[] NOT_CONSTANTS = {
			"$1",
			"$0",
			"NF",
			"length",
			"x",
			"toupper(\"a\")",
			"$1 \"a\"",
			"\"a\" x",
			"(x = 1)",
			"1 ? \"a\" : x",
			"/a/",
			"" };

	private static final String RECORD = "alpha beta gamma delta";

	@Test
	public void testConstantExpressions() throws Exception {
		Awk awk = new Awk();
		for (String source : CONSTANTS) {
			AwkExpression expression = awk.compileExpression(source);
			assertTrue(source, expression.isConstant());
			assertEquals(source, awk.eval(expression), expression.getConstantValue());
			assertEquals(source, -1, expression.getFieldIndex());
		}
	}

	@Test
	public void testConstantValueIsTheEvaluatedOne() throws Exception {
		Awk awk = new Awk();
		assertEquals("enclosure", awk.compileExpression("\"enclosure\"").getConstantValue());
		assertEquals(42L, awk.compileExpression("42").getConstantValue());
		assertEquals(0.1, awk.compileExpression("0.10").getConstantValue());
		assertEquals(10L, awk.compileExpression("010").getConstantValue());
		assertEquals(1000L, awk.compileExpression("1e3").getConstantValue());
		assertEquals(6L, awk.compileExpression("2 * 3").getConstantValue());
		assertEquals("ab", awk.compileExpression("\"a\" \"b\"").getConstantValue());
	}

	@Test
	public void testNotConstantExpressions() throws Exception {
		Awk awk = new Awk();
		for (String source : NOT_CONSTANTS) {
			AwkExpression expression = awk.compileExpression(source);
			assertFalse(source, expression.isConstant());
			IllegalStateException thrown = assertThrows(source, IllegalStateException.class, expression::getConstantValue);
			assertEquals("The expression is not constant.", thrown.getMessage());
		}
	}

	@Test
	public void testFieldIndex() throws Exception {
		Awk awk = new Awk();
		String[] fields = RECORD.split(" ");
		assertEquals(0, awk.compileExpression("$0").getFieldIndex());
		assertEquals(RECORD, awk.eval(awk.compileExpression("$0"), RECORD));
		for (String source : new String[] { "$1", "$3", "$4", "$(1 + 1)", "$\"2\"" }) {
			AwkExpression expression = awk.compileExpression(source);
			int index = expression.getFieldIndex();
			assertTrue(source, index > 0);
			assertEquals(source, fields[index - 1], awk.eval(expression, RECORD));
		}
		assertEquals(3, awk.compileExpression("$3").getFieldIndex());
		assertEquals(2, awk.compileExpression("$(1 + 1)").getFieldIndex());
	}

	@Test
	public void testNotAFieldIndex() throws Exception {
		Awk awk = new Awk();
		for (String source : new String[] { "$NF", "$(i)", "$1 $2", "$1 + 0", "\"a\"", "3", "$(-1)", "($3 = 1)", "" }) {
			assertEquals(source, -1, awk.compileExpression(source).getFieldIndex());
		}
	}

	@Test
	public void testWithoutOptimizationOnlyLiteralsAreReported() throws Exception {
		Awk awk = new Awk();
		AwkExpression literal = awk.compileExpression("\"a\"", true);
		assertTrue(literal.isConstant());
		assertEquals("a", literal.getConstantValue());
		assertEquals(awk.eval(literal), literal.getConstantValue());

		assertFalse(awk.compileExpression("2 * 3", true).isConstant());
		assertEquals(-1, awk.compileExpression("$3", true).getFieldIndex());
	}

	@Test
	public void testSandboxedExpression() throws Exception {
		Awk awk = new SandboxedAwk();
		assertEquals("a", awk.compileExpression("\"a\"").getConstantValue());
		assertEquals(3, awk.compileExpression("$3").getFieldIndex());
	}
}
