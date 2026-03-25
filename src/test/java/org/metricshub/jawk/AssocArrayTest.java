package org.metricshub.jawk;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.metricshub.jawk.intermediate.UninitializedObject;
import org.metricshub.jawk.jrt.AssocArray;

public class AssocArrayTest {

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
	public void testInjectAssocArrayVariable() throws Exception {
		AssocArray data = AssocArray.createHash();
		data.put("key1", "hello");
		data.put("key2", "world");

		AwkTestSupport
				.awkTest("inject AssocArray into array variable")
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

		AwkTestSupport
				.awkTest("inject Map into array variable")
				.script("BEGIN{ print arr[\"a\"], arr[\"b\"] }")
				.preassign("arr", data)
				.expectLines("alpha beta")
				.runAndAssert();
	}

	@Test
	public void testInjectAssocArrayIterateForIn() throws Exception {
		AssocArray data = AssocArray.createSorted();
		data.put("x", "1");
		data.put("y", "2");
		data.put("z", "3");

		AwkTestSupport
				.awkTest("inject AssocArray and iterate with for-in")
				.script("BEGIN{ for (k in arr) print k, arr[k] }")
				.preassign("arr", data)
				.expectLines("x 1", "y 2", "z 3")
				.runAndAssert();
	}

	@Test
	public void testInjectScalarIntoArrayVariableThrows() throws Exception {
		AwkTestSupport
				.awkTest("inject scalar into array variable should throw")
				.script("BEGIN{ for (k in arr) print k }")
				.preassign("arr", "notAnArray")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}
}
