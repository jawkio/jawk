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

import static io.jawk.AwkTestSupport.awkTest;

import java.util.Locale;
import io.jawk.util.AwkSettings;
import org.junit.Test;

public class StrNumSemanticsTest {

	@Test
	public void testArithmeticKeepsNumericPrefixConversion() throws Exception {
		awkTest("arithmetic parses numeric prefixes")
				.script("{ print($1 + 1) }")
				.stdin("2x\n2.3x\n2x.3x\n2e+02\n0x10\n")
				.expectLines("3", "3.3", "3", "201", "1")
				.runAndAssert();
	}

	@Test
	public void testInputComparisonsUseStrNumAttribute() throws Exception {
		awkTest("input-derived values compare as strnum only when fully numeric")
				.script("{ print($1 < 10) }")
				.stdin("2x\n2x.3x\n2e01\n9\n0x10\n")
				.expectLines("0", "0", "0", "1", "1")
				.runAndAssert();
	}

	@Test
	public void testAssignmentPreservesStrNumAttribute() throws Exception {
		awkTest("assignment preserves strnum attribute")
				.script("{ x = $1; print(x < 10) }")
				.stdin("9\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testStringOperationProducesPlainString() throws Exception {
		awkTest("concatenation produces plain string")
				.script("{ x = $1 \"\"; print(x < 10) }")
				.stdin("9\n")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void testStringLiteralsArePlainStrings() throws Exception {
		awkTest("string literals force string comparison")
				.script("BEGIN { print(\"9\" < 10); print(9 < \"10\") }")
				.expectLines("0", "0")
				.runAndAssert();
	}

	@Test
	public void testNumericOperationProducesNumber() throws Exception {
		awkTest("numeric operation produces numeric value")
				.script("{ x = $1 + 0; print(x < 10) }")
				.stdin("9\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testUninitializedEqualsNumericZeroStrNum() throws Exception {
		awkTest("uninitialized equals numeric zero strnum")
				.script("{ print($1 == undefined) }")
				.stdin("0.000\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testFieldAssignmentPreservesAssignedAttribute() throws Exception {
		awkTest("field assignment preserves assigned attribute")
				.script("{ $1 = $2; print($1 < 10); $1 = \"3.00\"; print($1 < 10); $1 = 3.00; print($1 < 10) }")
				.stdin("2.00 3.00\n")
				.expectLines("1", "0", "1")
				.runAndAssert();
	}

	@Test
	public void testAssigningDollarZeroCreatesNumericStringFields() throws Exception {
		awkTest("assigning dollar zero creates numeric string fields")
				.script("{ $0 = \"2.00 3.00\"; print($1 < 10) }")
				.stdin("ignored\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testAssignedDollarZeroRemainsPlainString() throws Exception {
		awkTest("assigned dollar zero remains plain string")
				.script("{ $0 = \"2.00 3.00\"; print($0 < 10); print($1 < 10) }")
				.stdin("ignored\n")
				.expectLines("0", "1")
				.runAndAssert();
	}

	@Test
	public void testStrNumComparisonUsesRuntimeLocale() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.setLocale(Locale.FRANCE);

		awkTest("strnum comparison uses runtime locale")
				.withAwk(new Awk(settings))
				.script("{ print($1 < 10) }")
				.stdin("3,14\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testNumericStrNumTruthinessUsesNumericValue() throws Exception {
		awkTest("input-derived numeric string truthiness uses numeric value")
				.script("{ print($1 ? \"true\" : \"false\") }")
				.stdin("0\n2\n2a\n")
				.expectLines("false", "true", "true")
				.runAndAssert();
	}
}
