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
	public void testSpawnProcessCat() throws Exception {
		Assume.assumeFalse(IS_WINDOWS);
		AwkTestSupport
				.awkTest("cat process")
				.script("BEGIN { print \"Hello\" | \"cat\"; close(\"cat\") }")
				.expectLines("Hello")
				.runAndAssert();
	}

	@Test
	public void testSpawnProcessMore() throws Exception {
		Assume.assumeTrue(IS_WINDOWS);
		AwkTestSupport
				.awkTest("more process")
				.script("BEGIN { print \"Hello\" | \"more\"; close(\"more\") }")
				.expectLines("Hello", "")
				.runAndAssert();
	}

	@Test
	public void testSystemPipe() throws Exception {
		Assume.assumeFalse(IS_WINDOWS);
		AwkTestSupport
				.awkTest("system pipe")
				.script("BEGIN { print(system(\"echo test | grep test\")) }")
				.expectLines("test", "0")
				.runAndAssert();
	}

	@Test
	public void testSystemPipeWindows() throws Exception {
		Assume.assumeTrue(IS_WINDOWS);
		AwkTestSupport
				.awkTest("system pipe windows")
				.script("BEGIN { print(system(\"echo test|findstr test\")) }")
				.expectLines("test", "0")
				.runAndAssert();
	}

	@Test
	public void testPrintfSpecialCharacters() throws Exception {
		AwkTestSupport
				.awkTest("printf special characters")
				.script("BEGIN { printf \"%c\\n\", 17379 }")
				.expectLines("\u43e3")
				.runAndAssert();
	}

	@Test
	public void testSplitSetsFieldZero() {
		AssocArray aa = AssocArray.createHash();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		jrt.setCONVFMT("%.6g");
		int n = jrt.split(aa, "a b");
		assertEquals(2, n);
		assertEquals(2L, aa.get(0L));
	}

	@Test
	public void testSplitUsesLongIndexesForPlainMap() {
		Map<Object, Object> map = new LinkedHashMap<>();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		jrt.setCONVFMT("%.6g");
		int n = jrt.split(map, "a b");
		assertEquals(2, n);
		assertEquals(2L, map.get(0L));
		assertEquals("a", map.get(1L));
		assertEquals("b", map.get(2L));
		assertFalse(map.containsKey(1));
	}

	@Test
	public void testSplitRegexWhitespace() {
		AssocArray aa = AssocArray.createHash();
		JRT jrt = new JRT(null, Locale.US, AwkSink.from(System.out, Locale.US), System.err);
		jrt.setCONVFMT("%.6g");
		int n = jrt.split("[ \t]+", aa, " 9853   shen");
		assertEquals(3, n);
		assertEquals("", aa.get(1));
		assertEquals("9853", aa.get(2));
		assertEquals("shen", aa.get(3));
	}

	@Test
	public void testRegexFsKeepsLeadingAndTrailingSeparators() throws Exception {
		AwkTestSupport
				.awkTest("regex fs retains separators")
				.script("BEGIN { FS = \"[ \\t\\n]+\" } { print $2 }")
				.stdin("  a  b  c  d ")
				.expectLines("a")
				.runAndAssert();
	}
}
