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
 * Tests that a numeric value compared against a string is converted with the AWK number-to-string
 * rule, not with {@link Object#toString()}.
 * <p>
 * A number compared against a string is a string comparison in AWK, and POSIX requires a value
 * exactly equal to an integer to be converted as if by {@code %d}, anything else through
 * {@code CONVFMT}. Converting with {@code Double.toString()} rendered {@code 291} as
 * {@code "291.0"} and {@code 1.04152956928E11} as {@code "1.04152956928E11"}, so a computed
 * integer stopped comparing equal to its own digits while {@code print}, concatenation,
 * {@code length()}, {@code match()}, {@code gsub()} and {@code split()} all still saw the right
 * string.
 */
public class NumberToStringComparisonTest {

	@Test
	public void testComputedIntegerMatchesItsDigits() throws Exception {
		AwkTestSupport.awkTest("A computed integer must match an anchored digit pattern")
				.script("BEGIN { x = \"2\" * 3; print (x ~ /^[0-9]+$/) }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A computed integer must not carry a fractional part into the match")
				.script("BEGIN { x = \"2\" * 3; print (x ~ /^6$/) (x ~ /\\./) }")
				.expectLines("10")
				.runAndAssert();
		AwkTestSupport.awkTest("A large computed integer must not be matched in exponent notation")
				.script("BEGIN { big = \"97\" * 1024 * 1024 * 1024; print (big ~ /^104152956928$/) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testComputedIntegerComparesEqualToItsDigits() throws Exception {
		AwkTestSupport.awkTest("Equality against the digits of a computed integer must hold")
				.script("BEGIN { x = \"2\" * 3; print (x == \"6\") (x != \"6\") }")
				.expectLines("10")
				.runAndAssert();
		AwkTestSupport.awkTest("Ordering against the digits of a computed integer must be an equal comparison")
				.script("BEGIN { x = \"2\" * 3; print (x > \"6\") (x < \"6\") (x >= \"6\") (x <= \"6\") }")
				.expectLines("0011")
				.runAndAssert();
		AwkTestSupport.awkTest("A large computed integer must compare equal to its digits")
				.script("BEGIN { big = \"97\" * 1024 * 1024 * 1024; print (big == \"104152956928\") }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testEveryArithmeticOperatorIsCovered() throws Exception {
		AwkTestSupport.awkTest("Every arithmetic operator must yield a value comparable to its digits")
				.script(
						"BEGIN { print ((\"2\" * 3) == \"6\") ((\"2\" + 4) == \"6\") ((\"12\" / 2) == \"6\")"
								+ " ((\"16\" % 10) == \"6\") ((\"6\" ^ 1) == \"6\") }")
				.expectLines("11111")
				.runAndAssert();
		AwkTestSupport.awkTest("A field operand must behave like a string operand")
				.script("{ x = $1 * 3; print (x == \"291\") (x ~ /^[0-9]+$/) }")
				.stdin("97\n")
				.expectLines("11")
				.runAndAssert();
	}

	/**
	 * A comparison that resolves numerically must not convert its operands at all, so an invalid or
	 * very wide {@code CONVFMT} cannot affect it: there is no string in such a comparison to format.
	 */
	@Test
	public void testNumericComparisonDoesNotEvaluateConvfmt() throws Exception {
		AwkTestSupport.awkTest("A number compared with a numeric field must ignore CONVFMT entirely")
				.script("{ CONVFMT = \"%f%f\"; x = 7 / 2; print (x == $1) }")
				.stdin("3.5\n")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("Two numbers must compare without evaluating CONVFMT")
				.script("BEGIN { CONVFMT = \"%f%f\"; print (7 / 2 == 3.5) (1 < 2) }")
				.expectLines("11")
				.runAndAssert();
		AwkTestSupport.awkTest("An uninitialized value compared with a number must ignore CONVFMT")
				.script("BEGIN { CONVFMT = \"%f%f\"; x = 7 / 2; print (unset == 0) (unset < x) }")
				.expectLines("11")
				.runAndAssert();
	}

	@Test
	public void testNonIntegralValuesStillUseConvfmt() throws Exception {
		AwkTestSupport.awkTest("A non-integral value keeps its CONVFMT rendering")
				.script("BEGIN { x = 7 / 2; print (x == \"3.5\") (x ~ /^3\\.5$/) }")
				.expectLines("11")
				.runAndAssert();
		AwkTestSupport.awkTest("A script-assigned CONVFMT must drive the comparison")
				.script("BEGIN { CONVFMT = \"%.2f\"; x = 7 / 2; print (x == \"3.50\") (x == \"3.5\") }")
				.expectLines("10")
				.runAndAssert();
	}
}
