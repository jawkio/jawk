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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.ext.annotations.JawkIntrospect;
import io.jawk.ext.annotations.JawkRawValue;
import io.jawk.jrt.AssocArray;

/**
 * Tests that extension functions receive plain Java values by default, and
 * raw runtime values only when they opt in.
 */
public class PlainExtensionValuesTest {

	/** Extension reporting what it receives and writing to the arrays it gets. */
	public static class ValuesExtension extends AbstractExtension {

		/**
		 * Returns the class name of a scalar argument.
		 *
		 * @param value argument
		 * @return simple class name
		 */
		@JawkFunction("plainClass")
		public String plainClass(Object value) {
			return value.getClass().getSimpleName();
		}

		/**
		 * Returns the class name of a raw scalar argument.
		 *
		 * @param value argument
		 * @return simple class name
		 */
		@JawkFunction("rawClass")
		public String rawClass(@JawkRawValue Object value) {
			return value.getClass().getSimpleName();
		}

		/**
		 * Returns the class name of an introspected argument.
		 *
		 * @param value argument
		 * @return simple class name
		 */
		@JawkFunction("introspectClass")
		public String introspectClass(@JawkIntrospect Object value) {
			return value.getClass().getSimpleName();
		}

		/**
		 * Describes an array read through the plain view.
		 *
		 * @param array array
		 * @return sorted key=type pairs
		 */
		@JawkFunction("describe")
		public String describe(@JawkAssocArray Map<Object, Object> array) {
			return describeMap(array);
		}

		/**
		 * Describes an array received raw.
		 *
		 * @param array array
		 * @return sorted key=type pairs
		 */
		@JawkFunction("describeRaw")
		public String describeRaw(@JawkAssocArray @JawkRawValue Map<Object, Object> array) {
			return describeMap(array);
		}

		/**
		 * Modifies an array through the plain view.
		 *
		 * @param array array
		 * @return 1
		 */
		@JawkFunction("modify")
		@SuppressWarnings("unchecked")
		public int modify(@JawkAssocArray Map<Object, Object> array) {
			array.put("new", "v");
			array.remove("gone");
			((Map<Object, Object>) array.get("sub")).put("x", Long.valueOf(42L));
			for (Iterator<Map.Entry<Object, Object>> it = array.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Object, Object> entry = it.next();
				if ("drop".equals(entry.getKey())) {
					it.remove();
				} else if ("set".equals(entry.getKey())) {
					entry.setValue("changed");
				}
			}
			return 1;
		}

		/**
		 * Looks up missing and present keys through the plain view.
		 *
		 * @param array array
		 * @return lookup results
		 */
		@JawkFunction("lookup")
		public String lookup(@JawkAssocArray Map<Object, Object> array) {
			return array.get("nope") + "," + array.getOrDefault("nope", "dflt") + "," + array.containsKey("1") + ","
					+ array.get("1") + "," + array.computeIfAbsent("made", key -> "m");
		}

		/**
		 * Reports whether views of the same AWK array are the same object.
		 *
		 * @param first first array
		 * @param second second array
		 * @return identity results for the arguments and a nested array
		 */
		@JawkFunction("same")
		public String same(@JawkAssocArray Map<Object, Object> first, @JawkAssocArray Map<Object, Object> second) {
			return (first == second) + "," + (first.get("sub") == second.get("sub"));
		}

		/**
		 * Copies a nested array from one view to another, and sets every value
		 * of the destination to {@code null} through its entries.
		 *
		 * @param source array holding {@code sub}
		 * @param dest array receiving {@code sub}
		 * @param cleared array whose values are set to {@code null}
		 * @return 1
		 */
		@JawkFunction("copyAndClear")
		public int copyAndClear(
				@JawkAssocArray Map<Object, Object> source,
				@JawkAssocArray Map<Object, Object> dest,
				@JawkAssocArray Map<Object, Object> cleared) {
			dest.put("sub", source.get("sub"));
			cleared.replaceAll((key, value) -> null);
			return 1;
		}

		/**
		 * Returns the array it receives.
		 *
		 * @param array array
		 * @return the same array
		 */
		@JawkFunction("identity")
		public Map<Object, Object> identity(@JawkAssocArray Map<Object, Object> array) {
			return array;
		}
	}

	@Test
	public void returnedViewIsUnwrapped() {
		ValuesExtension extension = new ValuesExtension();
		AssocArray array = AssocArray.createHash();
		Object result = extension.getExtensionFunctions().get("identity").invoke(extension, new Object[] { array });
		assertSame(array, result);
	}

	@Test
	public void writesStoreAwkValues() throws Exception {
		AwkTestSupport.awkTest("views and nulls written through the plain view become AWK values")
				.script(
						"BEGIN { s[\"sub\"][1] = 1; c[\"x\"] = 1; copyAndClear(s, d, c); "
								+ "print describeRaw(d), describeRaw(c), length(c) }")
				.withExtensions(new ValuesExtension())
				.expectLines("A{sub=A{1=Long}} A{x=UntypedObject} 1")
				.runAndAssert();
	}

	@Test
	public void plainViewWrapsAnyMap() {
		ValuesExtension extension = new ValuesExtension();
		Map<Object, Object> nested = new HashMap<Object, Object>();
		nested.put("n", Long.valueOf(1L));
		Map<Object, Object> map = new HashMap<Object, Object>();
		map.put("sub", nested);
		Object description = extension.getExtensionFunctions().get("describe").invoke(extension, new Object[] { map });
		assertEquals("V{sub=V{n=Long}}", description);
	}

	@Test
	public void missingKeyReadsDoNotCreateElements() throws Exception {
		AwkTestSupport.awkTest("reading a missing key through the plain view does not create it")
				.script("BEGIN { a[1] = \"one\"; print lookup(a); print (\"nope\" in a), a[\"made\"], length(a) }")
				.withExtensions(new ValuesExtension())
				.expectLines("null,dflt,true,one,m", "0 m 2")
				.runAndAssert();
	}

	@Test
	public void sameArrayGetsSameView() throws Exception {
		AwkTestSupport.awkTest("an array passed twice reaches the extension as one view")
				.script("BEGIN { a[\"sub\"][1] = 1; b[\"sub\"][1] = 1; print same(a, a), same(a, b) }")
				.withExtensions(new ValuesExtension())
				.expectLines("true,true false,false")
				.runAndAssert();
	}

	@Test
	public void scalarNumericStringsArePassedAsStrings() throws Exception {
		AwkTestSupport.awkTest("extensions receive input numeric strings as String")
				.script("{ print plainClass($1), plainClass($2), plainClass(\"s\") }")
				.withExtensions(new ValuesExtension())
				.stdin("7 x\n")
				.expectLines("String String String")
				.runAndAssert();
	}

	@Test
	public void rawValueAndIntrospectReceiveStrNum() throws Exception {
		AwkTestSupport.awkTest("@JawkRawValue and @JawkIntrospect receive StrNum")
				.script("{ print rawClass($1), introspectClass($1), introspectClass(\"s\") }")
				.withExtensions(new ValuesExtension())
				.stdin("7\n")
				.expectLines("StrNum StrNum String")
				.runAndAssert();
	}

	@Test
	public void arrayViewReadsPlainValues() throws Exception {
		AwkTestSupport.awkTest("@JawkAssocArray receives a plain view, nested arrays included")
				.script("{ a[1] = $1; a[2] = \"lit\"; a[3] = 2.5; a[\"sub\"][\"k\"] = $2; print describe(a) }")
				.withExtensions(new ValuesExtension())
				.stdin("7 8\n")
				.expectLines("V{1=String,2=String,3=Double,sub=V{k=String}}")
				.runAndAssert();
	}

	@Test
	public void rawArrayKeepsRuntimeValues() throws Exception {
		AwkTestSupport.awkTest("@JawkAssocArray @JawkRawValue receives the AssocArray")
				.script("{ a[1] = $1; a[2] = \"lit\"; a[3] = 2.5; a[\"sub\"][\"k\"] = $2; print describeRaw(a) }")
				.withExtensions(new ValuesExtension())
				.stdin("7 8\n")
				.expectLines("A{1=StrNum,2=String,3=Double,sub=A{k=StrNum}}")
				.runAndAssert();
	}

	@Test
	public void injectedNestedStringsArePlain() throws Exception {
		Map<String, Object> sub = new HashMap<String, Object>();
		sub.put("k", "3");
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("n", "12");
		map.put("sub", sub);
		AwkTestSupport.awkTest("strings nested in an injected map reach extensions as String")
				.script("BEGIN { print describe(m); print describeRaw(m) }")
				.withExtensions(new ValuesExtension())
				.preassign("m", map)
				.expectLines("V{n=String,sub=V{k=String}}", "A{n=StrNum,sub=A{k=StrNum}}")
				.runAndAssert();
	}

	@Test
	public void arrayViewWritesReachTheScript() throws Exception {
		AwkTestSupport.awkTest("writes through the plain view are visible to the script")
				.script(
						"BEGIN { a[\"gone\"] = 1; a[\"drop\"] = 2; a[\"set\"] = 3; a[\"keep\"] = 4; a[\"sub\"][\"y\"] = 5; "
								+ "modify(a); n = 0; for (k in a) n++; "
								+ "print n, a[\"new\"], (\"gone\" in a), (\"drop\" in a), a[\"set\"], a[\"keep\"], "
								+ "a[\"sub\"][\"x\"], a[\"sub\"][\"y\"] }")
				.withExtensions(new ValuesExtension())
				.expectLines("4 v 0 0 changed 4 42 5")
				.runAndAssert();
	}

	/** Declares an {@link AssocArray} parameter without opting in to raw values. */
	public static class AssocArrayTypedExtension extends AbstractExtension {

		/**
		 * Returns the array size.
		 *
		 * @param array array
		 * @return size
		 */
		@JawkFunction("size")
		public int size(@JawkAssocArray AssocArray array) {
			return array.size();
		}
	}

	/** Declares an {@link AssocArray} parameter that opts in to raw values. */
	public static class RawAssocArrayTypedExtension extends AbstractExtension {

		/**
		 * Returns the array size.
		 *
		 * @param array array
		 * @return size
		 */
		@JawkFunction("size")
		public int size(@JawkAssocArray @JawkRawValue AssocArray array) {
			return array.size();
		}
	}

	/** Declares a scalar {@link JawkRawValue} parameter that is not an {@link Object}. */
	public static class StringRawValueExtension extends AbstractExtension {

		/**
		 * Returns the text.
		 *
		 * @param text text
		 * @return the text
		 */
		@JawkFunction("text")
		public String text(@JawkRawValue String text) {
			return text;
		}
	}

	@Test
	public void plainArrayParameterMustBeDeclaredAsMap() throws Exception {
		try {
			new AssocArrayTypedExtension().getExtensionFunctions();
			fail("Expected IllegalStateException");
		} catch (IllegalStateException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("@JawkRawValue"));
		}
		AwkTestSupport.awkTest("an AssocArray parameter works with @JawkRawValue")
				.script("BEGIN { a[1]; a[2]; print size(a) }")
				.withExtensions(new RawAssocArrayTypedExtension())
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void rawScalarParameterMustBeObject() {
		try {
			new StringRawValueExtension().getExtensionFunctions();
			fail("Expected IllegalStateException");
		} catch (IllegalStateException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("JawkRawValue"));
		}
	}

	/*
	 * Arrays are described as A{...} when they are an AssocArray, V{...} when
	 * they are another Map (the plain view); scalars by their class name.
	 */
	@SuppressWarnings("unchecked")
	private static String describeMap(Map<Object, Object> map) {
		List<String> parts = new ArrayList<String>();
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			Object value = entry.getValue();
			String description = value instanceof Map ?
					describeMap((Map<Object, Object>) value) :
					value.getClass().getSimpleName();
			parts.add(entry.getKey() + "=" + description);
		}
		Collections.sort(parts);
		return (map instanceof AssocArray ? "A" : "V") + "{" + String.join(",", parts) + "}";
	}
}
