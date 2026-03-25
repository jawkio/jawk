package org.metricshub.jawk;

import static org.junit.Assert.*;

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
}
