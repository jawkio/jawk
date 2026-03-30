package io.jawk;

import static org.junit.Assert.*;

import org.junit.Test;
import io.jawk.jrt.ConditionPair;

public class ConditionPairTest {

	@Test
	public void test() {
		ConditionPair cp = new ConditionPair();
		assertFalse("Begin outside", cp.update(false, false));
		assertTrue("Entering", cp.update(true, false));
		assertTrue("Still inside", cp.update(false, false));
		assertTrue("Leaving", cp.update(false, true));
		assertFalse("Outside", cp.update(false, false));

		assertTrue("Re-entering", cp.update(true, false));
		assertTrue("Still inside", cp.update(false, false));
		assertTrue("Re-re-entering", cp.update(true, false));
		assertTrue("Leaving", cp.update(false, true));
		assertFalse("Outside", cp.update(false, false));
		assertFalse("Re-leaving", cp.update(false, true));

		assertTrue("Entering and leaving", cp.update(true, true));
		assertFalse("So we're outside", cp.update(false, false));
	}

	@Test
	public void testEdgeTransitions() {
		ConditionPair cp = new ConditionPair();

		// Ending a range while already outside should not activate it
		assertFalse(cp.update(false, true));

		// Start and end on the same record when already inside
		assertTrue(cp.update(true, false)); // enter
		assertTrue(cp.update(true, true)); // start and end again
		assertFalse(cp.update(false, false)); // now outside
	}
}
