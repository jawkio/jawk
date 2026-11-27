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

/**
 * Tests for the value of {@code ++} and {@code --}, prefix and postfix, on
 * every kind of lvalue: a prefix form evaluates to the new value, a postfix
 * form to the numeric value before the change, and the subscript of an array
 * element or the number of a field is evaluated once. Expected outputs are
 * gawk's.
 */
public class IncrementDecrementTest {

	private static final String RECORD = "8 5 x y z\n";

	@Test
	public void prefixOnFieldEvaluatesToTheUpdatedField() throws Exception {
		AwkTestSupport.awkTest("++$n and --$n evaluate to the new value of the field (#667)")
				.script("{ $2 = 3; print ++$2; print $2; $1 = 5; print --$1, $1 }")
				.stdin(RECORD)
				.expectLines("4", "4", "4 4")
				.runAndAssert();
	}

	@Test
	public void prefixOnEmptyOrMissingFieldStartsFromZero() throws Exception {
		AwkTestSupport.awkTest("++$n on a text, an empty field or a field beyond NF (#667)")
				.script("{ print ++$7, $7, --$9, NF; print ++$3 + 10 }")
				.stdin(RECORD)
				.expectLines("1 1 -1 9", "11")
				.runAndAssert();
	}

	@Test
	public void prefixOnFieldInStatement() throws Exception {
		AwkTestSupport.awkTest("++$n and --$n as statements update the record (#667)")
				.script("{ ++$2; --$1; print }")
				.stdin(RECORD)
				.expectLines("7 6 x y z")
				.runAndAssert();
	}

	@Test
	public void postfixOnFieldEvaluatesToTheOldNumber() throws Exception {
		AwkTestSupport.awkTest("$n++ and $n-- evaluate to the old value of the field")
				.script("{ print $2++, $2, $1--, $1 }")
				.stdin(RECORD)
				.expectLines("5 6 8 7")
				.runAndAssert();
	}

	@Test
	public void postfixOnFieldEvaluatesTheFieldNumberOnce() throws Exception {
		AwkTestSupport.awkTest("$(i++)++ and $(i++)-- evaluate the field number once")
				.script("{ i = 1; print $(i++)--, i, $0; i = 2; print $(i++)++, i, $0 }")
				.stdin(RECORD)
				.expectLines("8 2 7 5 x y z", "5 3 7 6 x y z")
				.runAndAssert();
	}

	@Test
	public void postfixOnTextVariableEvaluatesToItsNumber() throws Exception {
		AwkTestSupport.awkTest("x++ and x-- on a text evaluate to its numeric value (#668)")
				.script(
						"BEGIN { x = \"3x\"; y = x++; print y, x; x = \" 7 \"; y = x--; print \"[\" y \"]\", x;"
								+ " print z++, z--, z }")
				.expectLines("3 4", "[7] 6", "0 1 0")
				.runAndAssert();
	}

	@Test
	public void postfixOnTextLocalVariableEvaluatesToItsNumber() throws Exception {
		AwkTestSupport.awkTest("v++ on a local variable holding a text (#668)")
				.script("function f(v) { v = \"2.5z\"; return v++ } BEGIN { print f() }")
				.expectLines("2.5")
				.runAndAssert();
	}

	@Test
	public void postfixOnTextSpecialVariableEvaluatesToItsNumber() throws Exception {
		AwkTestSupport.awkTest("NR++ and FS-- on a text evaluate to its numeric value")
				.script("BEGIN { NR = \"2a\"; print NR++, NR; FS = \"3x\"; y = FS--; print y }")
				.expectLines("2 3", "3")
				.runAndAssert();
	}

	@Test
	public void arraySubscriptIsEvaluatedOnce() throws Exception {
		AwkTestSupport.awkTest("++ and -- on a[i++] evaluate the subscript once (#669)")
				.script(
						"function f() { calls++; return \"k\" }\n" + "BEGIN {\n" + "  i = 1; a[i++]++; print \"a[i++]++\", i\n"
								+ "  i = 1; a[i++]--; print \"a[i++]--\", i\n" + "  i = 1; ++a[i++]; print \"++a[i++]\", i\n"
								+ "  i = 1; --a[i++]; print \"--a[i++]\", i\n" + "  i = 1; a[i++] += 1; print \"a[i++] += 1\", i\n"
								+ "  calls = 0; b[f()]++; print \"b[f()]++\", calls\n"
								+ "  calls = 0; ++b[f()]; print \"++b[f()]\", calls\n" + "  print a[1], (2 in a), length(a), b[\"k\"]\n"
								+ "}")
				.expectLines(
						"a[i++]++ 2",
						"a[i++]-- 2",
						"++a[i++] 2",
						"--a[i++] 2",
						"a[i++] += 1 2",
						"b[f()]++ 1",
						"++b[f()] 1",
						"1 0 1 2")
				.runAndAssert();
	}

	@Test
	public void arrayElementValues() throws Exception {
		AwkTestSupport.awkTest("value of ++ and -- on array elements (#669)")
				.script(
						"BEGIN { m[\"x\"] = \"3x\"; print m[\"x\"]++, m[\"x\"], ++m[\"x\"], --m[\"y\"], m[\"y\"]--, m[\"y\"];"
								+ " print ++u[\"n\"] * 10, u[\"n\"]--, u[\"n\"] }")
				.expectLines("3 4 5 -1 -1 -2", "10 1 0")
				.runAndAssert();
	}

	@Test
	public void arrayOfArraysSubscriptsAreEvaluatedOnce() throws Exception {
		AwkTestSupport.awkTest("++ and -- on a[i++][j++] evaluate each subscript once (#669)")
				.script(
						"BEGIN { i = 1; j = 1; a[1][1] = 5; print a[i++][j++]++, i, j, a[1][1];" + " i = 1; print --a[i++][1], i }")
				.expectLines("5 2 2 6", "5 2")
				.runAndAssert();
	}

	@Test
	public void arrayParameterElement() throws Exception {
		AwkTestSupport.awkTest("arr[k]-- on an array parameter holding a text")
				.script("function g(arr, k) { return arr[k]-- } BEGIN { h[\"q\"] = \" 4 \"; print g(h, \"q\"), h[\"q\"] }")
				.expectLines("4 3")
				.runAndAssert();
	}
}
