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
 * Tests for numeric constants with exponents ({@code 2e3}, {@code 1.5e-2},
 * {@code .5E+1}, ...) and for printing integral values that exceed the
 * {@code long} range, matching gawk behavior.
 */
public class NumericLiteralTest {

	@Test
	public void testExponentLiterals() throws Exception {
		AwkTestSupport.awkTest("2e3 must lex as the number 2000")
				.script("BEGIN { print 2e3 }")
				.expectLines("2000")
				.runAndAssert();
		AwkTestSupport.awkTest("1.5e2 must lex as the number 150")
				.script("BEGIN { print 1.5e2 }")
				.expectLines("150")
				.runAndAssert();
		AwkTestSupport.awkTest("An uppercase exponent marker must be accepted")
				.script("BEGIN { print 1E3 }")
				.expectLines("1000")
				.runAndAssert();
		AwkTestSupport.awkTest("An explicit positive exponent sign must be accepted")
				.script("BEGIN { print 1e+3 }")
				.expectLines("1000")
				.runAndAssert();
		AwkTestSupport.awkTest("A negative exponent must be accepted")
				.script("BEGIN { print 1e-3 }")
				.expectLines("0.001")
				.runAndAssert();
		AwkTestSupport.awkTest("A leading-dot constant may carry an exponent")
				.script("BEGIN { print .5e2 }")
				.expectLines("50")
				.runAndAssert();
		AwkTestSupport.awkTest("A trailing-dot constant may carry an exponent")
				.script("BEGIN { print 1.e5 }")
				.expectLines("100000")
				.runAndAssert();
	}

	@Test
	public void testExponentBindsTighterThanConcatenation() throws Exception {
		AwkTestSupport.awkTest("1e20 must be one number, not 1 concatenated with variable e20")
				.script("BEGIN { e20 = 7; print 1e20 }")
				.expectLines("100000000000000000000")
				.runAndAssert();
	}

	@Test
	public void testBareEAfterNumberIsAnIdentifier() throws Exception {
		AwkTestSupport.awkTest("1e with no exponent digits must lex as 1 followed by the variable e")
				.script("BEGIN { e = \"X\"; print 1e }")
				.expectLines("1X")
				.runAndAssert();
		AwkTestSupport.awkTest("1E followed by a non-digit, non-sign must lex as 1 followed by the variable E")
				.script("BEGIN { E = \"Y\"; print 1E; print 2 }")
				.expectLines("1Y", "2")
				.runAndAssert();
	}

	@Test
	public void testLiteralsBeyondTheLongRangeAreFloatingPointConstants() throws Exception {
		AwkTestSupport.awkTest("An integer literal too large for 64 bits is accepted as a number")
				.script("BEGIN { print 99999999999999999999999 }")
				.expectLines("99999999999999991611392")
				.runAndAssert();
		AwkTestSupport.awkTest("The first literal beyond the signed 64-bit range is accepted")
				.script("BEGIN { print 9223372036854775808 }")
				.expectLines("9223372036854775808")
				.runAndAssert();
		AwkTestSupport.awkTest("A huge literal takes part in arithmetic")
				.script("BEGIN { print 123456789012345678901234567890 + 0 }")
				.expectLines("123456789012345677877719597056")
				.runAndAssert();
		AwkTestSupport.awkTest("The largest exactly representable integer literal keeps its value")
				.script("BEGIN { print 9223372036854775807 }")
				.expectLines("9223372036854775807")
				.runAndAssert();
	}

	@Test
	public void testIntegralValuesBeyondLongRangePrintAllDigits() throws Exception {
		AwkTestSupport.awkTest("1e20 must print all its digits, not clamp to the long range")
				.script("BEGIN { print 1e20 }")
				.expectLines("100000000000000000000")
				.runAndAssert();
		AwkTestSupport.awkTest("A negative constant beyond the long range must print all its digits")
				.script("BEGIN { print -1e20 }")
				.expectLines("-100000000000000000000")
				.runAndAssert();
		AwkTestSupport.awkTest("A constant-folded power beyond the long range must keep its value")
				.script("BEGIN { print 2^100 }")
				.expectLines("1267650600228229401496703205376")
				.runAndAssert();
		AwkTestSupport.awkTest("A power computed at runtime beyond the long range must print all its digits")
				.script("BEGIN { x = 2; print x^64 }")
				.expectLines("18446744073709551616")
				.runAndAssert();
		AwkTestSupport.awkTest("Number-to-string conversion beyond the long range must yield all digits")
				.script("BEGIN { s = 1e20 \"\"; print s }")
				.expectLines("100000000000000000000")
				.runAndAssert();
		AwkTestSupport.awkTest("Integral values within the long range must still print as integers")
				.script("BEGIN { print 1e18 }")
				.expectLines("1000000000000000000")
				.runAndAssert();
	}
}
