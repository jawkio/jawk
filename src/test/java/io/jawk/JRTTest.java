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

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.JRT;

public class JRTTest {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").contains("Windows");

	@Test
	public void testToDouble() {
		assertEquals(65.0, JRT.toDouble('A'), 0);
		assertEquals(65.0, JRT.toDouble(65), 0);
		assertEquals(65.0, JRT.toDouble(65L), 0);
		assertEquals(65.0, JRT.toDouble(65.0), 0);
		assertEquals(65.1, JRT.toDouble(65.1), 0);
		assertEquals(65.9, JRT.toDouble(65.9), 0);
		assertEquals(65.0, JRT.toDouble(Integer.valueOf(65)), 0);
		assertEquals(65.0, JRT.toDouble(Long.valueOf(65)), 0);
		assertEquals(65.0, JRT.toDouble(Float.valueOf(65)), 0);
		assertEquals(65.0, JRT.toDouble(Double.valueOf(65)), 0);
		assertEquals(65.0, JRT.toDouble("65"), 0);
		assertEquals(65.0, JRT.toDouble("65A"), 0);
		assertEquals(65.0, JRT.toDouble("65A6666666666666666666666666600000000033333333333999999999999"), 0);
		assertEquals(65.0, JRT.toDouble("6.5E+1"), 0);
		assertEquals(0.0, JRT.toDouble(""), 0);
		Object nothing = null;
		assertEquals(0.0, JRT.toDouble(nothing), 0);
	}

	@Test
	public void testToDoubleKeepsAllDigitsOfLongNumericStrings() {
		// A numeric string is converted in full: it is not truncated to the
		// length of a Double's textual representation.
		assertEquals(1.0e26, JRT.toDouble("100000000000000004764729344"), 0);
		assertEquals(1.0e50, JRT.toDouble("1" + new String(new char[50]).replace('\0', '0')), 0);
		assertEquals(
				0.00000000000000000000000000001234567890123456789,
				JRT.toDouble("0.00000000000000000000000000001234567890123456789"),
				0);
		// The numeric prefix is still honored, however long the trailing text is.
		assertEquals(65.0, JRT.toDouble("65fix" + new String(new char[100]).replace('\0', '9')), 0);
	}

	@Test
	public void testToDoubleNumericPrefix() {
		assertEquals(25.0, JRT.toDouble("25fix"), 0);
		assertEquals(3.14, JRT.toDouble("3.14abc"), 0);
		assertEquals(0.0, JRT.toDouble("abc"), 0);
		// Leading whitespace is skipped, trailing text simply ends the prefix.
		assertEquals(12.0, JRT.toDouble("  12"), 0);
		assertEquals(12.0, JRT.toDouble("\t\n12"), 0);
		assertEquals(12.0, JRT.toDouble("12  "), 0);
		// Signs, and fractional parts on either side of the decimal point.
		assertEquals(5.0, JRT.toDouble("+5"), 0);
		assertEquals(-5.0, JRT.toDouble("-5"), 0);
		assertEquals(0.5, JRT.toDouble(".5"), 0);
		assertEquals(5.0, JRT.toDouble("5."), 0);
		assertEquals(-0.5, JRT.toDouble("-.5"), 0);
		assertEquals(0.0, JRT.toDouble("-"), 0);
		assertEquals(0.0, JRT.toDouble("."), 0);
		assertEquals(0.0, JRT.toDouble("+ 5"), 0);
		// Exponents, including the backtracking cases.
		assertEquals(1000.0, JRT.toDouble("1e3"), 0);
		assertEquals(0.001, JRT.toDouble("1E-3"), 0);
		assertEquals(5000.0, JRT.toDouble("5.e3"), 0);
		assertEquals(1.0, JRT.toDouble("1e"), 0);
		assertEquals(1.0, JRT.toDouble("1e+"), 0);
		assertEquals(1.0, JRT.toDouble("1efoo"), 0);
		assertEquals(1000.0, JRT.toDouble("1e3foo"), 0);
		// Java accepts these; AWK does not.
		assertEquals(0.0, JRT.toDouble("Infinity"), 0);
		assertEquals(0.0, JRT.toDouble("NaN"), 0);
		assertEquals(0.0, JRT.toDouble("0x1A"), 0);
		assertEquals(0.0, JRT.toDouble("0x1p4"), 0);
		assertEquals(0.0, JRT.toDouble("0x1.8p1"), 0);
		assertEquals(25.0, JRT.toDouble("25f"), 0);
		assertEquals(1.0, JRT.toDouble("1d"), 0);
		// Overflow yields infinity rather than an error, as in gawk.
		assertTrue(Double.isInfinite(JRT.toDouble("1e400")));
	}

	@Test
	public void testToDoubleSignedInfinityAndNaN() {
		// AWK requires a signed, complete "inf" or "nan", matched without regard
		// to case and optionally surrounded by whitespace.
		assertEquals(Double.NEGATIVE_INFINITY, JRT.toDouble("-inf"), 0);
		assertEquals(Double.POSITIVE_INFINITY, JRT.toDouble("+inf"), 0);
		assertEquals(Double.NEGATIVE_INFINITY, JRT.toDouble("-INF"), 0);
		assertEquals(Double.NEGATIVE_INFINITY, JRT.toDouble("  -inf  "), 0);
		assertTrue(Double.isNaN(JRT.toDouble("-nan")));
		assertTrue(Double.isNaN(JRT.toDouble("+NaN")));
		// Anything more than the bare token is ordinary text: the gawk fixture
		// inf-nan-torture expects "-inform" and "-nancy" to convert to zero.
		assertEquals(0.0, JRT.toDouble("-inform"), 0);
		assertEquals(0.0, JRT.toDouble("-nancy"), 0);
		assertEquals(0.0, JRT.toDouble("+inform"), 0);
		assertEquals(0.0, JRT.toDouble("-infx"), 0);
		assertEquals(0.0, JRT.toDouble("-Infinity"), 0);
		// So is an unsigned token, or a sign detached from the word.
		assertEquals(0.0, JRT.toDouble("inf"), 0);
		assertEquals(0.0, JRT.toDouble("nan"), 0);
		assertEquals(0.0, JRT.toDouble("- inf"), 0);
		assertEquals(0.0, JRT.toDouble("-in"), 0);
	}

	@Test
	public void testToLong() {
		assertEquals(65L, JRT.toLong('A'));
		assertEquals(65L, JRT.toLong(65));
		assertEquals(65L, JRT.toLong(65L));
		assertEquals(65L, JRT.toLong(65.0));
		assertEquals(65L, JRT.toLong(65.1));
		assertEquals(65L, JRT.toLong(65.9));
		assertEquals(65L, JRT.toLong(Integer.valueOf(65)));
		assertEquals(65L, JRT.toLong(Long.valueOf(65)));
		assertEquals(65L, JRT.toLong(Float.valueOf(65)));
		assertEquals(65L, JRT.toLong(Double.valueOf(65)));
		assertEquals(65L, JRT.toLong("65"));
		assertEquals(65L, JRT.toLong("65A"));
		assertEquals(65L, JRT.toLong("65A6666666666666666666666666600000000033333333333999999999999"));
		assertEquals(0L, JRT.toLong(""));
		Object nothing = null;
		assertEquals(0L, JRT.toLong(nothing));
	}

	@Test
	public void testToLongUsesTheSameNumericConversionAsToDouble() {
		// Fractional and exponent forms convert, then truncate toward zero.
		assertEquals(10L, JRT.toLong("1e1"));
		assertEquals(20L, JRT.toLong("2e1"));
		assertEquals(3L, JRT.toLong("3.9"));
		assertEquals(-3L, JRT.toLong("-3.9"));
		assertEquals(100L, JRT.toLong("1e2"));
		assertEquals(1L, JRT.toLong("1e"));
		// Leading whitespace and numeric prefixes behave as in toDouble.
		assertEquals(3L, JRT.toLong(" 3"));
		assertEquals(25L, JRT.toLong("25fix"));
		assertEquals(0L, JRT.toLong("abc"));
		// A value beyond the 64-bit range saturates rather than wrapping.
		assertEquals(Long.MAX_VALUE, JRT.toLong("99999999999999999999999"));
		assertEquals(Long.MIN_VALUE, JRT.toLong("-99999999999999999999999"));
	}

	@Test
	public void testToLongConvertsEveryInRangeIntegerExactly() {
		// Values a long can hold convert exactly, including those a double
		// cannot represent.
		assertEquals(1000000000000000001L, JRT.toLong("1000000000000000001"));
		assertEquals(999999999999999999L, JRT.toLong("999999999999999999"));
		assertEquals(Long.MAX_VALUE, JRT.toLong("9223372036854775807"));
		assertEquals(Long.MAX_VALUE - 1, JRT.toLong("9223372036854775806"));
		assertEquals(Long.MIN_VALUE, JRT.toLong("-9223372036854775808"));
		assertEquals(Long.MIN_VALUE + 1, JRT.toLong("-9223372036854775807"));
		// Just outside the range, the value saturates instead of wrapping.
		assertEquals(Long.MAX_VALUE, JRT.toLong("9223372036854775808"));
		assertEquals(Long.MIN_VALUE, JRT.toLong("-9223372036854775809"));
		// An exponent marker with no digits is not part of the number, so the
		// prefix is still the bare integer and stays exact.
		assertEquals(1000000000000000001L, JRT.toLong("1000000000000000001e"));
		assertEquals(1000000000000000001L, JRT.toLong("1000000000000000001e+"));
		assertEquals(1000000000000000001L, JRT.toLong("1000000000000000001E"));
		assertEquals(1000000000000000001L, JRT.toLong("1000000000000000001efoo"));
		// An exponent with digits does change the value, so it is honored.
		assertEquals(10L, JRT.toLong("1e1"));
		assertEquals(10L, JRT.toLong("1e+1"));
		assertEquals(0L, JRT.toLong("1e-1"));
		assertEquals(1000L, JRT.toLong("1e3foo"));
	}

	@Test
	public void testCompare2Uninitialized() {
		// Uninitialized ==
		assertTrue(JRT.compare2(new UninitializedObject(), new UninitializedObject(), 0));
		assertTrue(JRT.compare2(new UninitializedObject(), "0", 0));
		assertTrue(JRT.compare2(new UninitializedObject(), 0, 0));
		assertTrue(JRT.compare2("0", new UninitializedObject(), 0));
		assertTrue(JRT.compare2(0, new UninitializedObject(), 0));
		assertFalse(JRT.compare2(new UninitializedObject(), "1", 0));
		assertFalse(JRT.compare2(new UninitializedObject(), 1, 0));
		assertFalse(JRT.compare2("1", new UninitializedObject(), 0));
		assertFalse(JRT.compare2(1, new UninitializedObject(), 0));

		// Uninitialized <
		assertFalse(JRT.compare2(new UninitializedObject(), new UninitializedObject(), -1));
		assertFalse(JRT.compare2(new UninitializedObject(), "0", -1));
		assertFalse(JRT.compare2(new UninitializedObject(), 0, -1));
		assertFalse(JRT.compare2("0", new UninitializedObject(), -1));
		assertFalse(JRT.compare2(0, new UninitializedObject(), -1));
		assertTrue(JRT.compare2(new UninitializedObject(), "1", -1));
		assertTrue(JRT.compare2(new UninitializedObject(), 1, -1));
		assertFalse(JRT.compare2("1", new UninitializedObject(), -1));
		assertFalse(JRT.compare2(1, new UninitializedObject(), -1));

		// Uninitialized >
		assertFalse(JRT.compare2(new UninitializedObject(), new UninitializedObject(), 1));
		assertFalse(JRT.compare2(new UninitializedObject(), "0", 1));
		assertFalse(JRT.compare2(new UninitializedObject(), 0, 1));
		assertFalse(JRT.compare2("0", new UninitializedObject(), 1));
		assertFalse(JRT.compare2(0, new UninitializedObject(), 1));
		assertFalse(JRT.compare2(new UninitializedObject(), "1", 1));
		assertFalse(JRT.compare2(new UninitializedObject(), 1, 1));
		assertTrue(JRT.compare2("1", new UninitializedObject(), 1));
		assertTrue(JRT.compare2(1, new UninitializedObject(), 1));
	}

	@Test
	public void testCompare2NumericOperands() {
		assertTrue(JRT.compare2(3L, 3L, 0));
		assertFalse(JRT.compare2(3L, 4L, 0));
		assertTrue(JRT.compare2(3L, 4L, -1));
		assertTrue(JRT.compare2(4L, 3L, 1));
		assertTrue(JRT.compare2(3.5D, 3.5D, 0));
		assertTrue(JRT.compare2(3L, 3.0D, 0));
		assertTrue(JRT.compare2(3L, 3.5D, -1));
	}

	@Test
	public void testCompare2ExactIntegralOperands() {
		// Two exact 64-bit integers compare exactly, without the precision
		// loss a double conversion would introduce beyond 2^53.
		assertFalse(JRT.compare2(9007199254740993L, 9007199254740992L, 0));
		assertTrue(JRT.compare2(9007199254740993L, 9007199254740992L, 1));
		assertTrue(JRT.compare2(9007199254740992L, 9007199254740993L, -1));
		assertFalse(JRT.compare2(Long.MAX_VALUE, Long.MAX_VALUE - 1, 0));
		assertTrue(JRT.compare2(Long.MAX_VALUE, Long.MAX_VALUE - 1, 1));
		// Integer operands take the same exact path.
		assertTrue(JRT.compare2(Integer.valueOf(3), 3L, 0));
	}

	@Test
	public void testExactIntegralArithmetic() {
		// Integral operands stay exact 64-bit integers.
		assertEquals(Long.valueOf(3L), JRT.add(1L, 2L));
		assertEquals(Long.valueOf(3L), JRT.add(Integer.valueOf(1), 2L));
		assertEquals(Long.valueOf(9007199254740993L), JRT.add(9007199254740992L, 1L));
		assertEquals(Long.valueOf(-1L), JRT.subtract(1L, 2L));
		assertEquals(Long.valueOf(9007199254740991L), JRT.subtract(9007199254740992L, 1L));
		assertEquals(Long.valueOf(6L), JRT.multiply(2L, 3L));
		assertEquals(Long.valueOf(4611686018427387903L), JRT.divide(9223372036854775806L, 2L));
		assertEquals(Long.valueOf(7L), JRT.mod(Long.MAX_VALUE, 10L));
		assertEquals(Long.valueOf(-9007199254740993L), JRT.negate(9007199254740993L));
		assertEquals(Long.valueOf(9007199254740993L), JRT.inc(9007199254740992L));
		assertEquals(Long.valueOf(9007199254740991L), JRT.dec(9007199254740992L));
	}

	@Test
	public void testExactIntegralArithmeticOverflowFallsBackToDouble() {
		assertEquals(9.223372036854776E18, ((Number) JRT.add(Long.MAX_VALUE, 1L)).doubleValue(), 0);
		assertEquals(-9.223372036854776E18, ((Number) JRT.subtract(Long.MIN_VALUE, 1L)).doubleValue(), 0);
		assertEquals(1.8446744073709552E19, ((Number) JRT.multiply(Long.MAX_VALUE, 2L)).doubleValue(), 0);
		assertEquals(9.223372036854776E18, ((Number) JRT.divide(Long.MIN_VALUE, -1L)).doubleValue(), 0);
		assertEquals(9.223372036854776E18, ((Number) JRT.inc(Long.MAX_VALUE)).doubleValue(), 0);
		assertEquals(-9.223372036854776E18, ((Number) JRT.dec(Long.MIN_VALUE)).doubleValue(), 0);
		assertEquals(9.223372036854776E18, ((Number) JRT.negate(Long.MIN_VALUE)).doubleValue(), 0);
	}

	@Test
	public void testNonIntegralArithmeticStaysInFloatingPoint() {
		// A fractional quotient, a zero divisor, or any non-integral operand
		// computes in floating point, exactly as before.
		assertEquals(Double.valueOf(2.5), JRT.divide(5L, 2L));
		assertEquals(Double.valueOf(4.0), JRT.add(1.5, 2.5));
		assertEquals(Double.valueOf(3.0), JRT.add("1", 2L));
		assertTrue(Double.isNaN(((Number) JRT.mod(5L, 0L)).doubleValue()));
		assertTrue(Double.isInfinite(((Number) JRT.divide(5L, 0L)).doubleValue()));
		assertEquals(Double.valueOf(8.0), JRT.pow(2L, 3L));
	}

	@Test
	public void testCompare2PlainStrings() {
		assertFalse(JRT.compare2("3", "3.0", 0));
		assertTrue(JRT.compare2("3", "4.0", -1));
		assertTrue(JRT.compare2("4.0", "3", 1));
		assertFalse(JRT.compare2("1e2", "100", 0));
		assertFalse(JRT.compare2("+.5", "0.5", 0));
		assertFalse(JRT.compare2("5.", "5.0", 0));
		assertFalse(JRT.compare2("-1E+2", "-100", 0));
		assertFalse(JRT.compare2("1e2147483649", "2", 0));
		assertTrue(JRT.compare2("1e2147483649", "2", -1));
	}

	@Test
	public void testCompare2MixedNumberAndString() {
		assertFalse(JRT.compare2(3L, "3.0", 0));
		assertFalse(JRT.compare2("3.0", 3L, 0));
		assertTrue(JRT.compare2(3L, "4", -1));
		assertTrue(JRT.compare2("4", 3L, 1));
	}

	@Test
	public void testCompare2FallsBackToStringComparison() {
		assertFalse(JRT.compare2("3x", "3.0", 0));
		assertTrue(JRT.compare2("3x", "4", -1));
		assertTrue(JRT.compare2(10L, "2x", -1));
		assertTrue(JRT.compare2("2x", 10L, 1));
		assertTrue(JRT.compare2("1e", "2", -1));
	}

	@Test
	public void testSpawnProcessCat() throws Exception {
		Assume.assumeFalse(IS_WINDOWS);
		AwkTestSupport.awkTest("cat process")
				.script("BEGIN { print \"Hello\" | \"cat\"; close(\"cat\") }")
				.expectLines("Hello")
				.runAndAssert();
	}

	@Test
	public void testSpawnProcessMore() throws Exception {
		Assume.assumeTrue(IS_WINDOWS);
		AwkTestSupport.awkTest("more process")
				.script("BEGIN { print \"Hello\" | \"more\"; close(\"more\") }")
				.expectLines("Hello", "")
				.runAndAssert();
	}

	@Test
	public void testSystemPipe() throws Exception {
		Assume.assumeFalse(IS_WINDOWS);
		AwkTestSupport.awkTest("system pipe")
				.script("BEGIN { print(system(\"echo test | grep test\")) }")
				.expectLines("test", "0")
				.runAndAssert();
	}

	@Test
	public void testSystemPipeWindows() throws Exception {
		Assume.assumeTrue(IS_WINDOWS);
		AwkTestSupport.awkTest("system pipe windows")
				.script("BEGIN { print(system(\"echo test|findstr test\")) }")
				.expectLines("test", "0")
				.runAndAssert();
	}

	@Test
	public void testPrintfSpecialCharacters() throws Exception {
		AwkTestSupport.awkTest("printf special characters")
				.script("BEGIN { printf \"%c\\n\", 17379 }")
				.expectLines("\u43e3")
				.runAndAssert();
	}

	@Test
	public void testSplitSetsFieldZero() {
		AssocArray aa = AssocArray.createHash();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		int n = jrt.split(aa, "a b");
		assertEquals(2, n);
		assertEquals(2L, aa.get(0L));
	}

	@Test
	public void testSplitUsesLongIndexesForPlainMap() {
		Map<Object, Object> map = new LinkedHashMap<>();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		int n = jrt.split(map, "a b");
		assertEquals(2, n);
		assertEquals(2L, map.get(0L));
		assertEquals("a", map.get(1L).toString());
		assertEquals("b", map.get(2L).toString());
		assertFalse(map.containsKey(1));
	}

	@Test
	public void testGetAwkStringEntryReadsOptionalSettings() {
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		AssocArray aa = AssocArray.createHash();
		aa.put("TZ", "Europe/Paris");
		aa.put("N", 42L);
		assertEquals("Europe/Paris", jrt.getAwkStringEntry(aa, "TZ"));
		assertEquals("42", jrt.getAwkStringEntry(aa, "N"));
		assertNull(jrt.getAwkStringEntry(aa, "MISSING"));

		Map<Object, Object> map = new LinkedHashMap<>();
		map.put("sorted_in", "@ind_num_asc");
		assertEquals("@ind_num_asc", jrt.getAwkStringEntry(map, "sorted_in"));
		assertNull(jrt.getAwkStringEntry(map, "absent"));
	}

	@Test
	public void testSplitRegexWhitespace() {
		AssocArray aa = AssocArray.createHash();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		int n = jrt.split("[ \t]+", aa, " 9853   shen");
		assertEquals(3, n);
		assertEquals("", aa.get(1).toString());
		assertEquals("9853", aa.get(2).toString());
		assertEquals("shen", aa.get(3).toString());
	}

	@Test
	public void testInputDerivedDollarZeroScalarIsCachedUntilRecordChanges() {
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		jrt.setFS(" ");
		jrt.setInputLine(jrt.toInputScalar("9 10"));

		Object firstRead = jrt.getInputLine();
		assertSame(firstRead, jrt.getInputLine());

		jrt.jrtSetInputField("8", 1);

		Object changedRead = jrt.getInputLine();
		assertNotSame(firstRead, changedRead);
		assertSame(changedRead, jrt.getInputLine());
		assertEquals("8 10", changedRead.toString());
	}

	@Test
	public void testFilenamePreservesScalarAttribute() {
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);

		jrt.setFILENAMEViaJrt(jrt.toInputScalar("9"));
		assertTrue(JRT.compare2(jrt.getFILENAME(), Long.valueOf(10L), -1));

		jrt.setFILENAMEViaJrt("9");
		assertFalse(JRT.compare2(jrt.getFILENAME(), Long.valueOf(10L), -1));
	}

	@Test
	public void testRegexFsKeepsLeadingAndTrailingSeparators() throws Exception {
		AwkTestSupport.awkTest("regex fs retains separators")
				.script("BEGIN { FS = \"[ \\t\\n]+\" } { print $2 }")
				.stdin("  a  b  c  d ")
				.expectLines("a")
				.runAndAssert();
	}
}
