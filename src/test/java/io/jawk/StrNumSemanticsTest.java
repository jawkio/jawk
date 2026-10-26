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
import static io.jawk.AwkTestSupport.cliTest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.util.AwkSettings;
import org.junit.Test;

public class StrNumSemanticsTest {

	@Test
	public void testArithmeticKeepsNumericPrefixConversion() throws Exception {
		awkTest("arithmetic parses numeric prefixes").script("{ print($1 + 1) }")
				.stdin("2x\n2.3x\n2x.3x\n2e+02\n0x10\n")
				.expectLines("3", "3.3", "3", "201", "1")
				.runAndAssert();
	}

	@Test
	public void testInputComparisonsUseStrNumAttribute() throws Exception {
		awkTest("input-derived values compare as strnum only when fully numeric").script("{ print($1 < 10) }")
				.stdin("2x\n2x.3x\n2e01\n9\n0x10\n")
				.expectLines("0", "0", "0", "1", "1")
				.runAndAssert();
	}

	@Test
	public void testAssignmentPreservesStrNumAttribute() throws Exception {
		awkTest("assignment preserves strnum attribute").script("{ x = $1; print(x < 10) }")
				.stdin("9\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testStringOperationProducesPlainString() throws Exception {
		awkTest("concatenation produces plain string").script("{ x = $1 \"\"; print(x < 10) }")
				.stdin("9\n")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void testStringLiteralsArePlainStrings() throws Exception {
		awkTest("string literals force string comparison").script("BEGIN { print(\"9\" < 10); print(9 < \"10\") }")
				.expectLines("0", "0")
				.runAndAssert();
	}

	@Test
	public void testNumericOperationProducesNumber() throws Exception {
		awkTest("numeric operation produces numeric value").script("{ x = $1 + 0; print(x < 10) }")
				.stdin("9\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testUninitializedEqualsNumericZeroStrNum() throws Exception {
		awkTest("uninitialized equals numeric zero strnum").script("{ print($1 == undefined) }")
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
		awkTest("assigning dollar zero creates numeric string fields").script("{ $0 = \"2.00 3.00\"; print($1 < 10) }")
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
	public void testAssignedDollarZeroPreservesAssignedAttribute() throws Exception {
		awkTest("assigned dollar zero preserves assigned attribute")
				.script("{ $0 = $1; print($0 < 10); $0 = 3.00; print($0 < 10); $0 = \"3.00\"; print($0 < 10) }")
				.stdin("9\n")
				.expectLines("1", "1", "0")
				.runAndAssert();
	}

	@Test
	public void testArgvValuesAreInputDerived() throws Exception {
		awkTest("ARGV values are input-derived").script("BEGIN { $0 = ARGV[1]; print($0 < 10); print($1 < 10); exit }")
				.operand("9")
				.expectLines("1", "1")
				.runAndAssert();
	}

	@Test
	public void testSplitCreatesNumericStringElements() throws Exception {
		awkTest("split array elements are numeric strings")
				.script("BEGIN { split(\"9 9a\", a); print(a[1] < 10); print(a[2] < 10) }")
				.expectLines("1", "0")
				.runAndAssert();
	}

	@Test
	public void testCommandLineVariableAssignmentsAreInputDerived() throws Exception {
		cliTest("CLI variable assignments are numeric strings").preassign("x", "9")
				.script("BEGIN { print(x < 10) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testFilelistVariableAssignmentsAreInputDerived() throws Exception {
		awkTest("filelist variable assignments are numeric strings").script("{ print(x < 10); exit }")
				.operand("x=9")
				.stdin("ignored\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testProgrammaticStringPreassignmentIsInputDerived() throws Exception {
		awkTest("programmatic string preassignments are numeric strings").preassign("x", "9")
				.script("BEGIN { print(x < 10) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedRecordIsNumericString() throws Exception {
		awkTest("blank-padded record compares numerically").script("{ print($0 == 12), ($1 == 12) }")
				.stdin(" 12 \n\t12\t\n")
				.expectLines("1 1", "1 1")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedRecordWithNonNumericTextStaysString() throws Exception {
		awkTest("internal blanks or trailing text keep the record a string").script("{ print($0 == 12) }")
				.stdin(" 1 2 \n 12x \n")
				.expectLines("0", "0")
				.runAndAssert();
	}

	@Test
	public void testBlankOnlyRecordIsNotNumeric() throws Exception {
		awkTest("blank-only record is neither zero nor the empty string").script("{ print($0 == 0), ($0 == \"\") }")
				.stdin("  \n")
				.expectLines("0 0")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedStringConstantStaysString() throws Exception {
		awkTest("blank-padded string constants are never numeric strings").script("BEGIN { x = \" 12 \"; print(x == 12) }")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedSplitElementIsNumericString() throws Exception {
		awkTest("blank-padded split elements compare numerically")
				.script("BEGIN { split(\"a, 12 , 1 2 \", a, \",\"); print(a[2] == 12); print(a[3] == 12) }")
				.expectLines("1", "0")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedGetlineVarIsNumericString() throws Exception {
		awkTest("blank-padded getline var compares numerically")
				.script("BEGIN { getline line < \"{{padded.txt}}\"; print(line == 12); print(line \"\") }")
				.file("padded.txt", " 12 \n")
				.expectLines("1", " 12 ")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedPreassignmentIsNumericString() throws Exception {
		awkTest("blank-padded preassignments compare numerically").preassign("x", " 12 ")
				.script("BEGIN { print(x == 12) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testBlankPaddedRecordTruthinessUsesNumericValue() throws Exception {
		awkTest("blank-padded numeric records are truthy by numeric value").script("{ print($0 ? \"true\" : \"false\") }")
				.stdin(" 0 \n 12 \n")
				.expectLines("false", "true")
				.runAndAssert();
	}

	@Test
	public void testStrNumComparisonUsesRuntimeLocale() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.setLocale(Locale.FRANCE);

		awkTest("strnum comparison uses runtime locale").withAwk(new Awk(settings))
				.script("{ print($1 < 10) }")
				.stdin("3,14\n")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testNumericStrNumTruthinessUsesNumericValue() throws Exception {
		awkTest("input-derived numeric string truthiness uses numeric value").script("{ print($1 ? \"true\" : \"false\") }")
				.stdin("0\n2\n2a\n")
				.expectLines("false", "true", "true")
				.runAndAssert();
	}

	/**
	 * Test extension that feeds preset records into the runtime through
	 * {@code JRT.setInputLine()}, the pathway extensions use to publish input.
	 */
	public static class LineFeedExtension extends AbstractExtension implements JawkExtension {

		private final Deque<String> lines = new ArrayDeque<>();

		/**
		 * Creates the extension with the records to feed, in order.
		 *
		 * @param lines records returned by successive {@code FeedLine()} calls
		 */
		public LineFeedExtension(String... lines) {
			for (String line : lines) {
				this.lines.add(line);
			}
		}

		/** Returns the logical name of the test extension. */
		@Override
		public String getExtensionName() {
			return "LineFeed";
		}

		/**
		 * Publishes the next preset record as the current input line.
		 *
		 * @return 1 when a record was published, 0 when none remain
		 */
		@JawkFunction("FeedLine")
		public int feedLine() {
			String line = lines.poll();
			if (line == null) {
				return 0;
			}
			getJrt().setInputLine(getJrt().toInputScalar(line));
			getJrt().jrtParseFields();
			return 1;
		}
	}

	@Test
	public void testExtensionInputUsesStrNumAttribute() throws Exception {
		awkTest("extension-published records are input-derived").withExtensions(new LineFeedExtension("9", "0"))
				.script("BEGIN { FeedLine(); print($0 < 10); FeedLine(); print($0 ? \"true\" : \"false\") }")
				.expectLines("1", "false")
				.runAndAssert();
	}
}
