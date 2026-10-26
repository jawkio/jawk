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
 * Tests for AWK's string-to-number conversion: a string contributes its leading
 * numeric prefix, however long, and text without such a prefix contributes zero.
 */
public class StringToNumberTest {

	@Test
	public void testLongNumericStringsKeepAllTheirDigits() throws Exception {
		AwkTestSupport.awkTest("A numeric string longer than a Double's textual form converts in full")
				.script("BEGIN { s = sprintf(\"%d\", 10^26); print s + 0 }")
				.expectLines("100000000000000004764729344")
				.runAndAssert();
		AwkTestSupport.awkTest("A long numeric string round-trips through a numeric conversion")
				.script("BEGIN { s = sprintf(\"%d\", 10^26); print (s + 0 == 10^26) ? \"equal\" : \"NOT equal\" }")
				.expectLines("equal")
				.runAndAssert();
		AwkTestSupport.awkTest("A 50-digit numeric string is not truncated")
				.script("BEGIN { print \"12345678901234567890123456789012345678901234567890\" + 0 }")
				.expectLines("12345678901234566660398341115085767575755770822656")
				.runAndAssert();
	}

	@Test
	public void testNumericPrefixConversion() throws Exception {
		AwkTestSupport.awkTest("A string converts using its leading numeric prefix")
				.script("BEGIN { print \"25fix\" + 0; print \"3.14abc\" + 0; print \"abc\" + 0 }")
				.expectLines("25", "3.14", "0")
				.runAndAssert();
		AwkTestSupport.awkTest("Leading whitespace is skipped and trailing text ends the prefix")
				.script("BEGIN { print \"  12\" + 0; print \"12  \" + 0; print \"\\t12x\" + 0 }")
				.expectLines("12", "12", "12")
				.runAndAssert();
		AwkTestSupport.awkTest("An exponent marker without digits is not part of the number")
				.script("BEGIN { print \"1e\" + 0; print \"1e+\" + 0; print \"1e3foo\" + 0 }")
				.expectLines("1", "1", "1000")
				.runAndAssert();
	}

	@Test
	public void testJavaOnlyNumberFormsAreNotAccepted() throws Exception {
		AwkTestSupport.awkTest("An unsigned Infinity or NaN is not an AWK number")
				.script("BEGIN { print \"Infinity\" + 0; print \"NaN\" + 0; print \"inf\" + 0 }")
				.expectLines("0", "0", "0")
				.runAndAssert();
		AwkTestSupport.awkTest("Hexadecimal strings convert to zero, as in gawk")
				.script("BEGIN { print \"0x1A\" + 0; print \"0X1a\" + 0 }")
				.expectLines("0", "0")
				.runAndAssert();
		AwkTestSupport.awkTest("Java hexadecimal floating-point strings are not AWK numbers")
				.script("BEGIN { print \"0x1p4\" + 0; print \"0x1.8p1\" + 0 }")
				.expectLines("0", "0")
				.runAndAssert();
	}

	@Test
	public void testSignedInfinityAndNaN() throws Exception {
		AwkTestSupport.awkTest("A complete signed infinity converts, whatever its case")
				.script("BEGIN { print \"-inf\" + 0; print \"-INF\" + 0; print \"  -inf  \" + 0 }")
				.expectLines("-inf", "-inf", "-inf")
				.runAndAssert();
		AwkTestSupport.awkTest("A complete signed NaN converts")
				.script("BEGIN { print \"-nan\" + 0; print \"+NaN\" + 0 }")
				.expectLines("nan", "nan")
				.runAndAssert();
		AwkTestSupport.awkTest("A word merely starting with a signed inf or nan is ordinary text")
				.script("BEGIN { print \"-inform\" + 0; print \"-nancy\" + 0; print \"-Infinity\" + 0 }")
				.expectLines("0", "0", "0")
				.runAndAssert();
	}

	@Test
	public void testIncrementUsesTheNumericPrefix() throws Exception {
		AwkTestSupport.awkTest("Incrementing a string uses its numeric prefix")
				.script("BEGIN { x = \"41fix\"; x++; print x; y = \"abc\"; y++; print y }")
				.expectLines("42", "1")
				.runAndAssert();
	}

	@Test
	public void testSubstrLengthAcceptsEveryNumericForm() throws Exception {
		AwkTestSupport.awkTest("An exponent-notation length is not truncated to its first digit")
				.script("BEGIN { print substr(\"abcdefgh\", 1, \"1e1\"); print substr(\"abcdefgh\", 1, \"2e1\") }")
				.expectLines("abcdefgh", "abcdefgh")
				.runAndAssert();
		AwkTestSupport.awkTest("A length beyond the integer range yields the rest of the string")
				.script("BEGIN { print substr(\"abcdefgh\", 1, \"1e300\"); print substr(\"abcdefgh\", 1, 1e300) }")
				.expectLines("abcdefgh", "abcdefgh")
				.runAndAssert();
		AwkTestSupport.awkTest("A fractional or padded length truncates toward zero")
				.script("BEGIN { print substr(\"abcdefgh\", 1, \"3.9\"); print substr(\"abcdefgh\", 1, \" 3\") }")
				.expectLines("abc", "abc")
				.runAndAssert();
		AwkTestSupport.awkTest("A non-numeric or negative length yields the empty string")
				.script(
						"BEGIN { print \"[\" substr(\"abcdefgh\", 1, \"abc\") \"]\"; print \"[\" substr(\"abcdefgh\", 1, -1) \"]\" }")
				.expectLines("[]", "[]")
				.runAndAssert();
	}

	@Test
	public void testInputFieldsStillCompareAsNumericStrings() throws Exception {
		AwkTestSupport.awkTest("Input fields keep strnum semantics")
				.script("{ print ($1 == 10) ? \"numeric\" : \"string\"; print ($2 < $1) ? \"less\" : \"notless\" }")
				.stdin(" 10 9\n")
				.expectLines("numeric", "less")
				.runAndAssert();
	}
}
