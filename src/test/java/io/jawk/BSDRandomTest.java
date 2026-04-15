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

import org.junit.Test;
import io.jawk.jrt.BSDRandom;

/**
 * Unit tests for {@link BSDRandom} verifying deterministic sequences.
 */
public class BSDRandomTest {

	@Test
	public void testDeterministicSequence() {
		BSDRandom rng = new BSDRandom(1);
		double[] expected = {
				0.8401877171547095,
				0.3943829268190930,
				0.7830992237586059,
				0.7984400334760733,
				0.9116473579367843,
				0.1975513692933840,
				0.3352227557148890,
				0.7682295948119040,
				0.2777747108031878,
				0.5539699557954305
		};
		for (double expectedValue : expected) {
			assertEquals(expectedValue, rng.nextDouble(), 1e-15);
		}
	}
}
