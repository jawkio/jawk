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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.JavaStringFormatAwkSink;
import org.junit.Test;

/**
 * Script-level tests for AWK {@code printf}/{@code sprintf} semantics
 * (issue #528), matching gawk behavior.
 */
public class PrintfTest {

	@Test
	public void testStringConversionOfIntegralDouble() throws Exception {
		// The symptom reported in issue #528: i++ produces a double, and %s
		// must print its integral value without a fractional part.
		AwkTestSupport.awkTest("printf %s prints integral doubles without fraction")
				.script("BEGIN { a[0]=1; a[1]=2; for (i=0; i in a; i++) printf(\"x[%s]\\n\", i) }")
				.expectLines("x[0]", "x[1]")
				.runAndAssert();
	}

	@Test
	public void testStringConversionHonorsConvfmt() throws Exception {
		AwkTestSupport.awkTest("printf %s converts numbers with CONVFMT")
				.script("BEGIN { CONVFMT=\"%.2g\"; printf \"%s|\", 3.14159; s = sprintf(\"%s\", 3.14159); print s }")
				.expectLines("3.1|3.1")
				.runAndAssert();
	}

	@Test
	public void testOfmtDoesNotAffectPrintf() throws Exception {
		AwkTestSupport.awkTest("printf %s ignores OFMT")
				.script("BEGIN { OFMT=\"%.2f\"; printf \"%s\\n\", 3.14159 }")
				.expectLines("3.14159")
				.runAndAssert();
	}

	@Test
	public void testCharConversionOfNumericValue() throws Exception {
		AwkTestSupport.awkTest("printf %c prints the character of a numeric code")
				.script("BEGIN { printf \"%c%c\\n\", 65, 98.7 }")
				.expectLines("Ab")
				.runAndAssert();
	}

	@Test
	public void testCharConversionOfNumericField() throws Exception {
		AwkTestSupport.awkTest("printf %c treats numeric fields as codes")
				.script("{ printf \"%c\\n\", $1 }")
				.stdin("65\n")
				.expectLines("A")
				.runAndAssert();
	}

	@Test
	public void testDynamicPrecision() throws Exception {
		AwkTestSupport.awkTest("printf dynamic star width and precision")
				.script("BEGIN { printf \"%.*s|%*d|%-*d|\\n\", 3, \"foobar\", 5, 42, 5, 42 }")
				.expectLines("foo|   42|42   |")
				.runAndAssert();
	}

	@Test
	public void testIntegerConversions() throws Exception {
		AwkTestSupport.awkTest("printf integer conversions truncate and wrap like gawk")
				.script("BEGIN { printf \"%d|%d|%i|%u|%x|%o\\n\", 42.7, -42.7, \"1e3\", -1, -1, 8 }")
				.expectLines("42|-42|1000|18446744073709551615|ffffffffffffffff|10")
				.runAndAssert();
	}

	@Test
	public void testOutOfRangeIntegerConversions() throws Exception {
		AwkTestSupport.awkTest("printf out-of-range integers match gawk")
				.script("BEGIN { printf \"%d|%d|%x\\n\", 2^100, 2^63, 2^100 }")
				.expectLines("1267650600228229401496703205376|9223372036854775808|1.26765e+30")
				.runAndAssert();
	}

	@Test
	public void testPrintOfHugeIntegralValues() throws Exception {
		AwkTestSupport.awkTest("print renders huge integral values in full")
				.script("BEGIN { print 2^100; print int(2^100); print 2^53 }")
				.expectLines("1267650600228229401496703205376", "1267650600228229401496703205376", "9007199254740992")
				.runAndAssert();
	}

	@Test
	public void testNonFiniteValues() throws Exception {
		AwkTestSupport.awkTest("printf prints nan and inf like gawk")
				.script("BEGIN { printf \"%f|%d|%s\\n\", log(-1), log(-1), 2 * 10^308 }")
				.expectLines("nan|nan|inf")
				.runAndAssert();
	}

	@Test
	public void testUnknownSpecifierPrintsVerbatim() throws Exception {
		AwkTestSupport.awkTest("printf unknown conversion prints verbatim without consuming arguments")
				.script("BEGIN { printf \"%q%d|%kmarco|a%nb\\n\", 1, 2 }")
				.expectLines("%q1|%kmarco|a%nb")
				.runAndAssert();
	}

	@Test
	public void testNotEnoughArgumentsIsFatal() throws Exception {
		AwkTestSupport.awkTest("printf with too few arguments is a fatal error")
				.script("BEGIN { printf \"%s %s\\n\", \"a\" }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void testExtraArgumentsAreIgnored() throws Exception {
		AwkTestSupport.awkTest("printf ignores extra arguments")
				.script("BEGIN { printf \"%s %s\\n\", \"a\", \"b\", \"c\" }")
				.expectLines("a b")
				.runAndAssert();
	}

	@Test
	public void testPositionalSpecifiers() throws Exception {
		AwkTestSupport.awkTest("printf gawk positional specifiers")
				.script("BEGIN { printf \"%2$s %1$s\\n\", \"world\", \"hello\" }")
				.expectLines("hello world")
				.runAndAssert();
	}

	@Test
	public void testUnterminatedStarPositionIsFatal() throws Exception {
		AwkTestSupport.awkTest("printf digits after star without dollar are fatal")
				.script("BEGIN { printf \"%*2d\\n\", 5, 42 }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void testMixedPositionalSpecifiersAreFatal() throws Exception {
		AwkTestSupport.awkTest("printf mixing positional and sequential specifiers is fatal")
				.script("BEGIN { printf \"%2$s %s\\n\", \"a\", \"b\" }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void testGroupingFlag() throws Exception {
		AwkTestSupport.awkTest("printf apostrophe flag groups thousands")
				.script("BEGIN { printf \"%'d\\n\", 1234567 }")
				.expectLines("1,234,567")
				.runAndAssert();
	}

	@Test
	public void testSprintfRoundHalfEven() throws Exception {
		AwkTestSupport.awkTest("printf %f rounds halfway cases to even like gawk")
				.script("BEGIN { printf \"%.0f|%.0f|%.0f|%.2f\\n\", 2.5, 3.5, 4.5, 0.125 }")
				.expectLines("2|4|4|0.12")
				.runAndAssert();
	}

	@Test
	public void testJavaStringFormatSink() throws Exception {
		// JavaStringFormatAwkSink formats with Java's String.format, giving
		// scripts access to Java-only conversions such as %,d grouping.
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		new Awk().script("BEGIN { n = 1234566; printf \"%,d|%05.1f|%s|%s\\n\", n + 1, 3.5, \"ok\", novalue }")
				.execute(new JavaStringFormatAwkSink(new PrintStream(output, true)));
		// The integral arithmetic result (a double at runtime) reaches
		// String.format as a Long, and the uninitialized variable as null.
		assertEquals("1,234,567|003.5|ok|null\n", output.toString());
	}

	@Test
	public void testPrintfToFileHonorsConvfmt() throws Exception {
		AwkTestSupport.awkTest("printf to a file honors CONVFMT for %s")
				.path("out.txt")
				.script(
						"BEGIN { CONVFMT=\"%.2g\"; f=\"{{out.txt}}\"; printf \"%s\\n\", 3.14159 > f; close(f); "
								+ "while ((getline x < f) > 0) print x }")
				.expectLines("3.1")
				.runAndAssert();
	}
}
