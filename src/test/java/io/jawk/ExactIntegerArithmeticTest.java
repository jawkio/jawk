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
 * Tests that arithmetic on integral operands stays exact in 64 bits
 * (<a href="https://github.com/jawkio/jawk/issues/537">#537</a>): results and
 * comparisons beyond 2^53 keep all their digits, and results that do not fit
 * in 64 bits fall back to floating point.
 */
public class ExactIntegerArithmeticTest {

	@Test
	public void testIntegralResultsStayExactBeyondDoublePrecision() throws Exception {
		AwkTestSupport.awkTest("Addition of integral operands must not round beyond 2^53")
				.script("BEGIN { print 9007199254740992 + 1 }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("Subtraction of integral operands must not round beyond 2^53")
				.script("BEGIN { print 9007199254740994 - 1 }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("Multiplication of integral operands must not round beyond 2^53")
				.script("BEGIN { print 3002399751580331 * 3 }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("An even division of integral operands must stay exact")
				.script("BEGIN { print 9223372036854775806 / 2 }")
				.expectLines("4611686018427387903")
				.runAndAssert();
		AwkTestSupport.awkTest("A remainder of integral operands must stay exact")
				.script("BEGIN { print 9223372036854775807 % 10 }")
				.expectLines("7")
				.runAndAssert();
	}

	@Test
	public void testCompoundAssignmentsAndSteppingStayExact() throws Exception {
		AwkTestSupport.awkTest("+= on a variable must stay exact beyond 2^53")
				.script("BEGIN { x = 9007199254740992; x += 1; print x }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("++ on a variable must stay exact beyond 2^53")
				.script("BEGIN { x = 9007199254740992; x++; print x }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("-- on a variable must stay exact beyond 2^53")
				.script("BEGIN { x = 9007199254740994; x--; print x }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("+= on an array element must stay exact beyond 2^53")
				.script("BEGIN { a[1] = 9007199254740992; a[1] += 1; print a[1] }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("++ on an array element must stay exact beyond 2^53")
				.script("BEGIN { a[1] = 9007199254740992; a[1]++; print a[1] }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("An incremented counter must print as an integer")
				.script("BEGIN { cnt[\"k\"]++; ++cnt[\"k\"]; printf \"%s\\n\", cnt[\"k\"] }")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void testFieldArithmeticStaysExact() throws Exception {
		AwkTestSupport.awkTest("+= on a field holding an exact integer must stay exact beyond 2^53")
				.script("{ $1 = 9007199254740992; $1 += 1; print $1 }")
				.stdin("x\n")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("++ on a field holding an exact integer must stay exact beyond 2^53")
				.script("{ $1 = 9007199254740992; $1++; print $1 }")
				.stdin("x\n")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("++ on a field must yield the exact original value")
				.script("{ $1 = 9007199254740993; print $1++ }")
				.stdin("x\n")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("+= on a missing field must start from integer zero")
				.script("{ $3 += 2; print $3 }")
				.stdin("a\n")
				.expectLines("2")
				.runAndAssert();
		AwkTestSupport.awkTest("A field must retain its numeric scalar across repeated updates")
				.script("{ $1 = 9007199254740992; $1 += 1; $1 += 1; print $1 }")
				.stdin("x\n")
				.expectLines("9007199254740994")
				.runAndAssert();
		AwkTestSupport.awkTest("Repeated ++ on a field must stay exact beyond 2^53")
				.script("{ $1 = 9007199254740992; $1++; $1++; print $1, $0 }")
				.stdin("x\n")
				.expectLines("9007199254740994 9007199254740994")
				.runAndAssert();
	}

	@Test
	public void testRecordRebuildConvertsNumericFieldsWithConvfmt() throws Exception {
		AwkTestSupport.awkTest("$0 must be rebuilt with CONVFMT, not raw double digits")
				.script("{ $1 = 0.1 + 0.2; print }")
				.stdin("x y\n")
				.expectLines("0.3 y")
				.runAndAssert();
		AwkTestSupport.awkTest("A numeric field keeps its full value while $0 shows its CONVFMT form")
				.script("{ CONVFMT = \"%.2g\"; $1 = 0.123456; print; print $1 }")
				.stdin("x y\n")
				.expectLines("0.12 y", "0.123456")
				.runAndAssert();
		AwkTestSupport.awkTest("Input-derived fields join back verbatim")
				.script("{ $3 = \"z\"; print }")
				.stdin("0.30000000000000004 y w\n")
				.expectLines("0.30000000000000004 y z")
				.runAndAssert();
		AwkTestSupport.awkTest("$NF++ on an empty record targets $0, which stays record text")
				.script("{ print $NF++; print $NF }")
				.stdin("\n")
				.expectLines("0", "1")
				.runAndAssert();
	}

	@Test
	public void testUninitializedAccumulatorsStayExact() throws Exception {
		AwkTestSupport.awkTest("+= into an uninitialized variable must start from integer zero")
				.script("BEGIN { s += 9007199254740992; s += 1; print s }")
				.expectLines("9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("+= into a missing array element must start from integer zero")
				.script("BEGIN { a[1] += 9007199254740992; a[1] += 1; print a[1] }")
				.expectLines("9007199254740993")
				.runAndAssert();
	}

	@Test
	public void testUnaryOperatorsStayExact() throws Exception {
		AwkTestSupport.awkTest("Unary minus must not round an exact integer")
				.script("BEGIN { x = 9007199254740993; print -x }")
				.expectLines("-9007199254740993")
				.runAndAssert();
		AwkTestSupport.awkTest("Unary plus must not round an exact integer")
				.script("BEGIN { x = 9007199254740993; print +x }")
				.expectLines("9007199254740993")
				.runAndAssert();
	}

	@Test
	public void testComparisonsOfExactIntegersAreExact() throws Exception {
		AwkTestSupport.awkTest("Adjacent integers beyond 2^53 must compare as different")
				.script("BEGIN { print (9007199254740993 == 9007199254740992) }")
				.expectLines("0")
				.runAndAssert();
		AwkTestSupport.awkTest("Ordering beyond 2^53 must be exact")
				.script("BEGIN { print (9007199254740993 > 9007199254740992) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testOverflowFallsBackToFloatingPoint() throws Exception {
		AwkTestSupport.awkTest("A sum beyond 64 bits must fall back to floating point")
				.script("BEGIN { print 9223372036854775806 + 2 }")
				.expectLines("9223372036854775808")
				.runAndAssert();
		AwkTestSupport.awkTest("A product beyond 64 bits must fall back to floating point")
				.script("BEGIN { print 4611686018427387904 * 4 }")
				.expectLines("18446744073709551616")
				.runAndAssert();
		AwkTestSupport.awkTest("Negating the most negative 64-bit integer must fall back to floating point")
				.script("BEGIN { x = -9223372036854775807 - 1; print -x }")
				.expectLines("9223372036854775808")
				.runAndAssert();
	}

	@Test
	public void testNonIntegralArithmeticIsUnchanged() throws Exception {
		AwkTestSupport.awkTest("A fractional quotient must stay floating point")
				.script("BEGIN { print 5 / 2 }")
				.expectLines("2.5")
				.runAndAssert();
		AwkTestSupport.awkTest("A remainder by zero must stay nan")
				.script("BEGIN { print 5 % 0 }")
				.expectLines("nan")
				.runAndAssert();
		AwkTestSupport.awkTest("A division by zero must stay inf")
				.script("BEGIN { print 5 / 0 }")
				.expectLines("inf")
				.runAndAssert();
		AwkTestSupport.awkTest("Exponentiation stays in floating point")
				.script("BEGIN { print 2 ^ 62 }")
				.expectLines("4611686018427387904")
				.runAndAssert();
		AwkTestSupport.awkTest("Mixed integral and fractional operands compute in floating point")
				.script("BEGIN { print 1 + 2.5 }")
				.expectLines("3.5")
				.runAndAssert();
	}

	@Test
	public void testConstantFoldingMatchesRuntimeSemantics() throws Exception {
		// A fractional literal keeps double semantics whether the expression
		// is folded at compile time (literals only) or evaluated at runtime
		// (through a variable): folding must not gain exactness.
		AwkTestSupport.awkTest("A folded double addition must round exactly like the runtime one")
				.script("BEGIN { print (9007199254740992.0 + 1 == 9007199254740993) }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A runtime double addition rounds beyond 2^53")
				.script("BEGIN { x = 9007199254740992.0; print (x + 1 == 9007199254740993) }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A folded integral addition must stay exact like the runtime one")
				.script("BEGIN { x = 9007199254740992; y = x + 1; print (9007199254740992 + 1 == y) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testTightLoopAccumulationIsExact() throws Exception {
		AwkTestSupport.awkTest("A counting loop must accumulate exactly")
				.script("BEGIN { s = 0; for (i = 0; i < 100000; i++) s = s + i; print s }")
				.expectLines("4999950000")
				.runAndAssert();
	}
}
