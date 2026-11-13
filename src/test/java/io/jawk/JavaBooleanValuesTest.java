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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.GawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.AssocArray;
import io.jawk.util.AwkSettings;

/**
 * Tests that a {@link Boolean} handed to a script by Java code is the AWK
 * boolean-typed number 1 or 0, wherever it enters the runtime.
 */
public class JavaBooleanValuesTest {

	private static final String USE = "print v, v + 0, (v ? \"on\" : \"off\"), !v, typeof(v)";

	/** Extension handing booleans to the script. */
	public static class BooleanExtension extends AbstractExtension {

		/**
		 * Returns a boolean.
		 *
		 * @param value AWK value
		 * @return whether {@code value} is the text {@code yes}
		 */
		@JawkFunction("isYes")
		public Boolean isYes(String value) {
			return "yes".equals(value);
		}

		/**
		 * Fills an array from a parsed-JSON-like document, as in issue #654.
		 *
		 * @param dest array to fill
		 * @return number of elements
		 */
		@SuppressWarnings("unchecked")
		@JawkFunction("fill")
		public long fill(@JawkAssocArray Map<Object, Object> dest) {
			Map<String, Object> document = new LinkedHashMap<String, Object>();
			document.put("enabled", Boolean.FALSE);
			document.put("flags", Arrays.asList(Boolean.TRUE, Boolean.FALSE));
			dest.clear();
			dest.putAll((Map<Object, Object>) AssocArray.normalizeValue(document, false, getJrt()::toInputScalar));
			return dest.size();
		}

		/**
		 * Writes booleans straight to an array.
		 *
		 * @param dest array to write to
		 */
		@JawkFunction("put")
		public void put(@JawkAssocArray Map<Object, Object> dest) {
			dest.put("on", Boolean.TRUE);
			dest.put("off", Boolean.TRUE);
			dest.entrySet().stream().filter(e -> "off".equals(e.getKey())).forEach(e -> e.setValue(Boolean.FALSE));
		}

		/**
		 * Returns a map holding a boolean.
		 *
		 * @return map
		 */
		@JawkFunction("flags")
		public Map<String, Object> flags() {
			return Collections.<String, Object>singletonMap("enabled", Boolean.TRUE);
		}

		/**
		 * Reads an array element through the plain view.
		 *
		 * @param array array
		 * @return the element {@code enabled}
		 */
		@JawkFunction("enabled")
		public Object enabled(@JawkAssocArray Map<Object, Object> array) {
			return array.get("enabled");
		}
	}

	@Test
	public void booleanVariableIsBooleanNumber() throws Exception {
		AwkTestSupport.awkTest("Boolean variables are 1 and 0")
				.script("BEGIN { v = t; " + USE + "; v = f; " + USE + " }")
				.preassign("t", Boolean.TRUE)
				.preassign("f", Boolean.FALSE)
				.expectLines("1 1 on 0 number|bool", "0 0 off 1 number|bool")
				.runAndAssert();
	}

	@Test
	public void booleansNestedInVariablesAreBooleanNumbers() throws Exception {
		Map<String, Object> document = new LinkedHashMap<String, Object>();
		document.put("enabled", Boolean.FALSE);
		document.put("flags", Arrays.asList(Boolean.TRUE, Boolean.FALSE));
		AwkTestSupport.awkTest("Booleans nested in injected maps and lists are 1 and 0")
				.script("BEGIN { v = doc[\"enabled\"]; " + USE + "; v = doc[\"flags\"][0]; " + USE + " }")
				.preassign("doc", document)
				.expectLines("0 0 off 1 number|bool", "1 1 on 0 number|bool")
				.runAndAssert();
	}

	@Test
	public void booleansNormalizedByAnExtensionAreBooleanNumbers() throws Exception {
		AwkTestSupport.awkTest("Booleans normalized by an extension are 1 and 0")
				.script("BEGIN { fill(r); v = r[\"enabled\"]; " + USE + "; v = r[\"flags\"][0]; " + USE + " }")
				.withExtensions(new GawkExtension(), new BooleanExtension())
				.expectLines("0 0 off 1 number|bool", "1 1 on 0 number|bool")
				.runAndAssert();
	}

	@Test
	public void booleansWrittenToAnArrayViewAreBooleanNumbers() throws Exception {
		AwkTestSupport.awkTest("Booleans written through an array view are 1 and 0")
				.script("BEGIN { put(r); v = r[\"on\"]; " + USE + "; v = r[\"off\"]; " + USE + " }")
				.withExtensions(new GawkExtension(), new BooleanExtension())
				.expectLines("1 1 on 0 number|bool", "0 0 off 1 number|bool")
				.runAndAssert();
	}

	@Test
	public void booleanReturnedByAnExtensionIsBooleanNumber() throws Exception {
		AwkTestSupport.awkTest("Booleans returned by an extension are 1 and 0")
				.script(
						"BEGIN { v = isYes(\"yes\"); " + USE + "; v = isYes(\"no\"); " + USE + ";"
								+ " if (isYes(\"yes\") && !isYes(\"no\")) print enabled(flags()) }")
				.withExtensions(new GawkExtension(), new BooleanExtension())
				.expectLines("1 1 on 0 number|bool", "0 0 off 1 number|bool", "1")
				.runAndAssert();
	}

	@Test
	public void mkboolIsUsableAsACondition() throws Exception {
		AwkTestSupport.awkTest("mkbool() values work in conditions")
				.script("BEGIN { v = mkbool(1); " + USE + "; if (!mkbool(0)) print \"ok\" }")
				.expectLines("1 1 on 0 number|bool", "ok")
				.runAndAssert();
	}

	@Test
	public void booleanSpecialVariablesAreBooleanNumbers() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.putVariable("IGNORECASE", Boolean.TRUE);
		AwkTestSupport.awkTest("Boolean special variables set in the settings are 1 and 0")
				.script("BEGIN { v = IGNORECASE; " + USE + "; print \"ABC\" ~ /abc/ }")
				.withAwk(new Awk(settings))
				.expectLines("1 1 on 0 number|bool", "1")
				.runAndAssert();
		AwkTestSupport.awkTest("Boolean special variables set for one run are 1 and 0")
				.script("BEGIN { print \"ABC\" ~ /abc/, \"x\" }")
				.preassign("IGNORECASE", Boolean.TRUE)
				.preassign("OFS", Boolean.FALSE)
				.expectLines("10x")
				.runAndAssert();
	}
}
