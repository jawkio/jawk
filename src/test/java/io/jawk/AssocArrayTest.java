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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.AwkRuntimeException;

public class AssocArrayTest {

	@Test
	public void testIsIntegralNumberKey() {
		assertTrue(AssocArray.isIntegralNumberKey(Long.valueOf(5L)));
		assertTrue(AssocArray.isIntegralNumberKey(Integer.valueOf(5)));
		assertTrue(AssocArray.isIntegralNumberKey(Double.valueOf(5.0)));
		assertTrue(AssocArray.isIntegralNumberKey(Double.valueOf(-0.0)));
		assertFalse(AssocArray.isIntegralNumberKey(Double.valueOf(5.5)));
		assertFalse(AssocArray.isIntegralNumberKey(Double.valueOf(0x1p63)));
		assertFalse(AssocArray.isIntegralNumberKey(Double.valueOf(Double.NaN)));
		assertFalse(AssocArray.isIntegralNumberKey(Float.valueOf(5.0f)));
		assertFalse(AssocArray.isIntegralNumberKey("5"));
		assertFalse(AssocArray.isIntegralNumberKey(null));
	}

	@Test
	public void testInOperatorWithNumericAndStringKeys() {
		AssocArray array = AssocArray.createHash();
		array.put(1L, "one");
		array.put("bar", "barValue");

		assertTrue(array.isIn(1L));
		assertTrue(array.isIn("1"));
		assertTrue(array.isIn("bar"));
		assertFalse(array.isIn("foo"));
		assertEquals(2, array.keySet().size());
	}

	@Test
	public void testInOperatorWithNullKey() {
		AssocArray array = AssocArray.createHash();
		array.put(0L, "zero");

		assertFalse(array.isIn(null));
		assertFalse(array.isIn(new UninitializedObject()));
		assertEquals(1, array.keySet().size());
	}

	@Test
	public void testRemoveNumericStringKey() {
		AssocArray array = AssocArray.createHash();
		array.put(1L, "one");

		assertEquals("one", array.remove("1"));
		assertFalse(array.isIn(1L));
	}

	@Test
	public void testRemoveNumericKeyFromString() {
		AssocArray array = AssocArray.createHash();
		array.put("2", "two");

		assertEquals("two", array.remove(2L));
		assertFalse(array.isIn("2"));
	}

	@Test
	public void testRemoveMissingKey() {
		AssocArray array = AssocArray.createHash();
		array.put(1, "one");

		assertNull(array.remove(3L));
		assertTrue(array.isIn(1L));
	}

	@Test
	public void testUninitializedIndexUsesEmptyString() {
		AssocArray idxArray = AssocArray.createHash();
		Object idx = idxArray.get(0L);
		assertTrue(idx instanceof UninitializedObject);

		AssocArray array = AssocArray.createHash();
		array.put("", "empty");
		array.put(0L, "zero");

		assertEquals("empty", array.get(idx));
		assertEquals("zero", array.get(0L));
	}

	@Test
	public void testCreateAssocArrayFromListUsesZeroBasedIndexes() {
		AssocArray array = AssocArray.createFromList(Arrays.asList("aaa", "bbb", "ccc"), false);

		assertEquals("aaa", array.get(0L));
		assertEquals("bbb", array.get("1"));
		assertEquals("ccc", array.get(2L));
	}

	@Test
	public void testNormalizeMapValueKeepsMapAndConvertsNestedList() {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("items", Arrays.asList("aaa", "bbb", "ccc"));

		Object normalized = AssocArray.normalizeValue(data, false);

		assertSame(data, normalized);
		assertTrue(data.get("items") instanceof AssocArray);
		assertEquals("bbb", ((AssocArray) data.get("items")).get(1L));
	}

	@Test
	public void testInjectAssocArrayVariable() throws Exception {
		AssocArray data = AssocArray.createHash();
		data.put("key1", "hello");
		data.put("key2", "world");

		AwkTestSupport.awkTest("inject AssocArray into array variable")
				.script("BEGIN{ print arr[\"key1\"], arr[\"key2\"] }")
				.preassign("arr", data)
				.expectLines("hello world")
				.runAndAssert();
	}

	@Test
	public void testInjectMapVariable() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("a", "alpha");
		data.put("b", "beta");

		AwkTestSupport.awkTest("inject Map into array variable")
				.script("BEGIN{ print arr[\"a\"], arr[\"b\"] }")
				.preassign("arr", data)
				.expectLines("alpha beta")
				.runAndAssert();
	}

	@Test
	public void testInjectListVariableUsesZeroBasedIndexes() throws Exception {
		List<String> data = Arrays.asList("aaa", "bbb", "ccc");

		AwkTestSupport.awkTest("inject List into array variable with zero-based indexes")
				.script("BEGIN{ print arr[0], arr[2], length(arr), (3 in arr) }")
				.preassign("arr", data)
				.expectLines("aaa ccc 3 0")
				.runAndAssert();
	}

	@Test
	public void testInjectLinkedListVariableUsesZeroBasedIndexes() throws Exception {
		List<String> data = new LinkedList<>(Arrays.asList("aaa", "bbb", "ccc"));

		AwkTestSupport.awkTest("inject LinkedList into array variable with zero-based indexes")
				.script("BEGIN{ print arr[0], arr[2], length(arr) }")
				.preassign("arr", data)
				.expectLines("aaa ccc 3")
				.runAndAssert();
	}

	@Test
	public void testInjectMapOfListVariable() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("items", Arrays.asList("aaa", "bbb", "ccc"));

		AwkTestSupport.awkTest("inject Map containing List into nested array variable")
				.script("BEGIN{ print payload[\"items\"][0], payload[\"items\"][2] }")
				.preassign("payload", data)
				.expectLines("aaa ccc")
				.runAndAssert();
	}

	@Test
	public void testInjectImmutableMapOfListVariableThrowsClearException() throws Exception {
		Map<Object, Object> mutableData = new LinkedHashMap<>();
		mutableData.put("items", Arrays.asList("aaa", "bbb", "ccc"));
		Map<Object, Object> data = Collections.unmodifiableMap(mutableData);

		AwkTestSupport.TestResult result = AwkTestSupport.awkTest("inject immutable Map containing List should throw")
				.script("BEGIN{ print payload[\"items\"][0] }")
				.preassign("payload", data)
				.expectThrow(AwkRuntimeException.class)
				.run();
		result.assertExpected();

		assertTrue(result.thrownException().getMessage().contains("Injected maps must be mutable"));
		assertTrue(mutableData.get("items") instanceof List);
	}

	@Test
	public void testRejectedFunctionNameAssignmentDoesNotNormalizeMapValue() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("items", Arrays.asList("aaa", "bbb", "ccc"));

		AwkTestSupport.awkTest("rejected function name assignment leaves Map value unchanged")
				.script("function payload(){ return 1 } BEGIN{ print \"unused\" }")
				.preassign("payload", data)
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();

		assertTrue(data.get("items") instanceof List);
	}

	@Test
	public void testIgnoredVariableAssignmentDoesNotNormalizeMapValue() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("items", Arrays.asList("aaa", "bbb", "ccc"));

		AwkTestSupport.awkTest("ignored variable assignment leaves Map value unchanged")
				.script("BEGIN{ print \"ok\" }")
				.preassign("payload", data)
				.expectLines("ok")
				.runAndAssert();

		assertTrue(data.get("items") instanceof List);
	}

	@Test
	public void testInjectListOfMapVariable() throws Exception {
		Map<Object, Object> first = new LinkedHashMap<>();
		first.put("name", "alpha");
		first.put("id", Long.valueOf(1L));
		Map<Object, Object> second = new LinkedHashMap<>();
		second.put("name", "beta");
		second.put("id", Long.valueOf(2L));
		List<Object> data = Arrays.<Object>asList(first, second);

		AwkTestSupport.awkTest("inject List containing Map into nested array variable")
				.script("BEGIN{ print payload[0][\"name\"], payload[1][\"id\"] }")
				.preassign("payload", data)
				.expectLines("alpha 2")
				.runAndAssert();
	}

	@Test
	public void testInjectMapVariableMutatesOriginalMap() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("a", "alpha");

		AwkTestSupport.awkTest("inject Map into array variable without copying")
				.script("BEGIN{ arr[\"b\"] = \"beta\"; delete arr[\"a\"]; print arr[\"b\"], (\"a\" in arr) }")
				.preassign("arr", data)
				.expectLines("beta 0")
				.runAndAssert();

		assertFalse(data.containsKey("a"));
		assertEquals("beta", data.get("b"));
	}

	@Test
	public void testInjectMapVariableDoesNotNormalizeKeys() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("1", "one");
		data.put("2", "two");

		AwkTestSupport.awkTest("inject Map with numeric string keys without key normalization")
				.script("BEGIN{ print (1 in arr) \" \" (\"1\" in arr) }")
				.preassign("arr", data)
				.expectLines("0 1")
				.runAndAssert();
	}

	@Test
	public void testSplitMutatesInjectedMapDirectly() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("old", "value");

		AwkTestSupport.awkTest("split rewrites injected Map directly")
				.script("BEGIN{ print split(\"alpha beta\", arr), arr[1], arr[2] }")
				.preassign("arr", data)
				.expectLines("2 alpha beta")
				.runAndAssert();

		assertFalse(data.containsKey("old"));
		assertFalse(data.containsKey(1));
		assertFalse(data.containsKey(2));
		assertEquals("alpha", data.get(1L).toString());
		assertEquals("beta", data.get(2L).toString());
	}

	@Test
	public void testMissingInjectedMapElementDoesNotReturnRawNull() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();

		AwkTestSupport.awkTest("missing injected Map element behaves like blank without null leakage")
				.script("BEGIN{ print \"[\" arr[1] \"]\", (arr[1] ? 1 : 0) }")
				.preassign("arr", data)
				.expectLines("[] 0")
				.runAndAssert();

		assertTrue(data.isEmpty());
	}

	@Test
	public void testInjectAssocArrayIterateForIn() throws Exception {
		AssocArray data = AssocArray.createSorted();
		data.put("x", "1");
		data.put("y", "2");
		data.put("z", "3");

		AwkTestSupport.awkTest("inject AssocArray and iterate with for-in")
				.script("BEGIN{ for (k in arr) print k, arr[k] }")
				.preassign("arr", data)
				.expectLines("x 1", "y 2", "z 3")
				.runAndAssert();
	}

	@Test
	public void testInjectScalarIntoArrayVariableThrows() throws Exception {
		AwkTestSupport.awkTest("inject scalar into array variable should throw")
				.script("BEGIN{ for (k in arr) print k }")
				.preassign("arr", "notAnArray")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void testDeleteArrayClearsInjectedMap() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("a", "alpha");
		data.put("b", "beta");

		AwkTestSupport.awkTest("delete array clears injected Map")
				.script("BEGIN{ delete arr }")
				.preassign("arr", data)
				.expect("")
				.runAndAssert();

		assertTrue(data.isEmpty());
	}

	@Test
	public void testInjectMapVariableKeepsIntegralDoubleKeys() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put(Double.valueOf(1.0), "one");

		AwkTestSupport.awkTest("injected Map Double keys stay reachable with a Double subscript")
				.script("BEGIN{ print arr[idx], (idx in arr) }")
				.preassign("arr", data)
				.preassign("idx", Double.valueOf(1.0))
				.expectLines("one 1")
				.runAndAssert();
	}

	@Test
	public void testSingleDimensionSubscriptKeyFixedAtCreationTime() throws Exception {
		AwkTestSupport.awkTest("single-dimension subscript keeps its key after CONVFMT changes")
				.script(
						"BEGIN { a = 12.153; test[a] = \"hi\"; CONVFMT = \"%.0f\"; a = 5;"
								+ " for (i in test) printf (\"test[%s] = %s\\n\", i, test[i]) }")
				.expectLines("test[12.153] = hi")
				.runAndAssert();
	}

	@Test
	public void testMultiDimensionSubscriptKeyFixedAtCreationTime() throws Exception {
		AwkTestSupport.awkTest("multi-dimensional subscript keeps its key after CONVFMT changes")
				.script(
						"BEGIN { test[1.5, 2.5] = \"hi\"; CONVFMT = \"%.0f\";"
								+ " for (i in test) { split(i, parts, SUBSEP); print parts[1], parts[2] } }")
				.expectLines("1.5 2.5")
				.runAndAssert();
	}

	@Test
	public void testSubscriptKeyUsesConvfmtInEffectAtCreation() throws Exception {
		AwkTestSupport.awkTest("subscript key is built with the CONVFMT in effect at creation")
				.script("BEGIN { CONVFMT = \"%.2f\"; a[3.14159] = 1; CONVFMT = \"%.6g\";" + " for (k in a) print k }")
				.expectLines("3.14")
				.runAndAssert();
	}

	@Test
	public void testInOperatorConvertsKeyWithCurrentConvfmt() throws Exception {
		AwkTestSupport.awkTest("in operator converts its key with the current CONVFMT")
				.script(
						"BEGIN { a[12.153] = 1; print (12.153 in a);"
								+ " CONVFMT = \"%.0f\"; print (12.153 in a), ((12.153) in a) }")
				.expectLines("1", "0 0")
				.runAndAssert();
	}

	@Test
	public void testInOperatorConvertsKeyAfterArrayOperandEvaluation() throws Exception {
		AwkTestSupport.awkTest("in operator converts its key after the array operand is evaluated")
				.script(
						"function f() { CONVFMT = \"%.0f\"; return \"x\" }"
								+ " BEGIN { outer[\"x\"][12.153] = 1; CONVFMT = \"%.6g\";" + " print (12.153 in outer[f()]) }")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void testInOperatorJoinsMultidimKeyAfterArrayOperandEvaluation() throws Exception {
		AwkTestSupport.awkTest("in operator joins a multi-dimensional key after the array operand is evaluated")
				.script(
						"function f() { CONVFMT = \"%.0f\"; return \"x\" }"
								+ " BEGIN { outer[\"x\"][1.2, 2.3] = 1; CONVFMT = \"%.6g\";" + " print ((1.2, 2.3) in outer[f()]) }")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void testNonIntegralFloatSubscriptConvertsAtCreation() throws Exception {
		AwkTestSupport.awkTest("non-integral Float subscript keys as its CONVFMT string at creation")
				.script("BEGIN { a[f] = 1; CONVFMT = \"%.0f\"; for (k in a) print k; print (12.153 in a) }")
				.preassign("f", Float.valueOf(12.153f))
				.expectLines("12.153", "0")
				.runAndAssert();
	}

	@Test
	public void testDeleteConvertsKeyWithCurrentConvfmt() throws Exception {
		AwkTestSupport.awkTest("delete converts its key with the current CONVFMT")
				.script("BEGIN { a[12.153] = 1; CONVFMT = \"%.0f\"; delete a[12.153];" + " print length(a) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testOutOfLongRangeIntegralSubscriptKeysAsExactDigits() throws Exception {
		AwkTestSupport.awkTest("integral subscript beyond the signed 64-bit range keys as its exact digits")
				.script(
						"BEGIN { a[2^63] = 1; for (i in a) print i; print (2^63 in a);"
								+ " CONVFMT = \"%.0f\"; print (2^63 in a) }")
				.expectLines("9223372036854775808", "1", "1")
				.runAndAssert();
	}

	@Test
	public void testIntegralSubscriptsUnaffectedByConvfmt() throws Exception {
		AwkTestSupport.awkTest("integral subscripts are not affected by CONVFMT")
				.script(
						"BEGIN { n = split(\"aaa bbb\", arr); CONVFMT = \"%.2f\"; i = 1.0;"
								+ " print arr[i], arr[2], (2 in arr), (2.0 in arr) }")
				.expectLines("aaa bbb 1 1")
				.runAndAssert();
	}

	@Test
	public void testDeleteArrayKeepsInjectedMapBoundForLaterWrites() throws Exception {
		Map<Object, Object> data = new LinkedHashMap<>();
		data.put("a", "alpha");

		AwkTestSupport.awkTest("delete array keeps injected Map bound")
				.script("BEGIN{ delete arr; arr[1] = \"after\"; print arr[1] }")
				.preassign("arr", data)
				.expectLines("after")
				.runAndAssert();

		assertFalse(data.containsKey("a"));
		assertEquals("after", data.get(1L));
	}
}
