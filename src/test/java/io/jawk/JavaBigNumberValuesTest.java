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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.GawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.AssocArray;
import io.jawk.util.AwkSettings;

/**
 * A {@link BigDecimal} or a {@link BigInteger} handed to a script by Java code
 * is a numeric string: it computes as a number and prints with its digits
 * (issue #658).
 */
public class JavaBigNumberValuesTest {

	private static final BigDecimal DECIMAL = new BigDecimal("1234567.891");

	private static final BigInteger INTEGER = new BigInteger("12345678901234567890");

	/** The value as it prints, computed with, converted to a string, compared as a number, and its type. */
	private static final String USE = "print v, v + 0, v \"\", (v > 1000000), typeof(v)";

	private static final String DECIMAL_USED = "1234567.891 1.23457e+06 1234567.891 1 strnum";

	private static final String INTEGER_USED = "12345678901234567890 12345678901234567168 12345678901234567890 1 strnum";

	/** Extension handing big numbers to the script. */
	public static class BigNumberExtension extends AbstractExtension {

		/**
		 * Returns a big decimal.
		 *
		 * @return the decimal
		 */
		@JawkFunction("decimal")
		public BigDecimal decimal() {
			return DECIMAL;
		}

		/**
		 * Returns a map holding a big integer.
		 *
		 * @return map
		 */
		@JawkFunction("sizes")
		public Map<String, Object> sizes() {
			return Collections.<String, Object>singletonMap("total", INTEGER);
		}

		/**
		 * Reads an array element through the plain view.
		 *
		 * @param array array
		 * @return the element {@code total}
		 */
		@JawkFunction("total")
		public Object total(@JawkAssocArray Map<Object, Object> array) {
			return array.get("total");
		}

		/**
		 * Fills an array from a parsed-JSON-like document.
		 *
		 * @param dest array to fill
		 * @return number of elements
		 */
		@SuppressWarnings("unchecked")
		@JawkFunction("fill")
		public long fill(@JawkAssocArray Map<Object, Object> dest) {
			dest.clear();
			dest.putAll((Map<Object, Object>) AssocArray.normalizeValue(document(), false, getJrt()::toInputScalar));
			return dest.size();
		}

		/**
		 * Writes big numbers straight to an array.
		 *
		 * @param dest array to write to
		 */
		@JawkFunction("put")
		public void put(@JawkAssocArray Map<Object, Object> dest) {
			dest.put("size", DECIMAL);
			dest.put("total", "");
			dest.entrySet().stream().filter(e -> "total".equals(e.getKey())).forEach(e -> e.setValue(INTEGER));
		}
	}

	private static Map<String, Object> document() {
		Map<String, Object> document = new LinkedHashMap<String, Object>();
		document.put("size", DECIMAL);
		document.put("totals", Arrays.asList(INTEGER));
		return document;
	}

	@Test
	public void bigNumberVariableIsNumericString() throws Exception {
		AwkTestSupport.awkTest("BigDecimal and BigInteger variables keep their digits")
				.script("BEGIN { v = d; " + USE + "; v = i; " + USE + " }")
				.preassign("d", DECIMAL)
				.preassign("i", INTEGER)
				.expectLines(DECIMAL_USED, INTEGER_USED)
				.runAndAssert();
	}

	@Test
	public void bigNumbersNestedInVariablesAreNumericStrings() throws Exception {
		AwkTestSupport.awkTest("Big numbers nested in injected maps and lists keep their digits")
				.script("BEGIN { v = doc[\"size\"]; " + USE + "; v = doc[\"totals\"][0]; " + USE + " }")
				.preassign("doc", document())
				.expectLines(DECIMAL_USED, INTEGER_USED)
				.runAndAssert();
	}

	@Test
	public void bigNumbersNormalizedByAnExtensionAreNumericStrings() throws Exception {
		AwkTestSupport.awkTest("Big numbers normalized by an extension keep their digits")
				.script("BEGIN { fill(r); v = r[\"size\"]; " + USE + "; v = r[\"totals\"][0]; " + USE + " }")
				.withExtensions(new GawkExtension(), new BigNumberExtension())
				.expectLines(DECIMAL_USED, INTEGER_USED)
				.runAndAssert();
	}

	@Test
	public void bigNumbersWrittenToAnArrayViewAreNumericStrings() throws Exception {
		AwkTestSupport.awkTest("Big numbers written through an array view keep their digits")
				.script("BEGIN { put(r); v = r[\"size\"]; " + USE + "; v = r[\"total\"]; " + USE + " }")
				.withExtensions(new GawkExtension(), new BigNumberExtension())
				.expectLines(DECIMAL_USED, INTEGER_USED)
				.runAndAssert();
	}

	@Test
	public void bigNumberReturnedByAnExtensionIsNumericString() throws Exception {
		AwkTestSupport.awkTest("Big numbers returned by an extension keep their digits")
				.script("BEGIN { v = decimal(); " + USE + "; print total(sizes()) }")
				.withExtensions(new GawkExtension(), new BigNumberExtension())
				.expectLines(DECIMAL_USED, INTEGER.toString())
				.runAndAssert();
	}

	@Test
	public void bigDecimalKeepsItsScaleAndComparesAsANumber() throws Exception {
		AwkTestSupport.awkTest("A BigDecimal prints with its trailing zeros and its exponent")
				.script("BEGIN { print a, (a == 12.5), b, b + 1 }")
				.preassign("a", new BigDecimal("12.50"))
				.preassign("b", new BigDecimal("1e3"))
				.expectLines("12.50 1 1E+3 1001")
				.runAndAssert();
	}

	@Test
	public void bigNumberIsANumberWhateverTheLocale() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.setLocale(Locale.FRANCE);
		AwkTestSupport.awkTest("A BigDecimal is written with a period, also where the decimal separator is a comma")
				.script("BEGIN { print d, (d > 1000000), doc[\"size\"], (doc[\"size\"] > 1000000) }")
				.withAwk(new Awk(settings))
				.preassign("d", DECIMAL)
				.preassign("doc", document())
				.expectLines("1234567.891 1 1234567.891 1")
				.runAndAssert();
	}
}
