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
