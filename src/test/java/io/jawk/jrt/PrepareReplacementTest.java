package io.jawk.jrt;

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

/**
 * Tests for {@link JRT#prepareReplacement(String, boolean)}.
 */
public class PrepareReplacementTest {

	@Test
	public void testPrepareReplacement() {
		assertEquals("don't change", JRT.prepareReplacement("don't change", false));
		assertEquals("a$0a", JRT.prepareReplacement("a&a", false));
		assertEquals("1$01", JRT.prepareReplacement("1&1", false));
		assertEquals("a$0b$0c", JRT.prepareReplacement("a&b&c", false));
		assertEquals("a\\b", JRT.prepareReplacement("a\\b", false));
		assertEquals("a&b", JRT.prepareReplacement("a\\&b", false));
		assertEquals("a\\", JRT.prepareReplacement("a\\", false));
		assertEquals("a\\$", JRT.prepareReplacement("a$", false));
		assertEquals("a\\\\$", JRT.prepareReplacement("a\\$", false));
		assertEquals("a\\\\\\$", JRT.prepareReplacement("a\\\\$", false));
		assertEquals("a\\\\$0", JRT.prepareReplacement("a\\\\&", false));
		assertEquals("a\\\\&", JRT.prepareReplacement("a\\\\\\&", false));
		assertEquals("", JRT.prepareReplacement("", false));
		assertEquals("", JRT.prepareReplacement(null, false));
	}

	@Test
	public void testPrepareReplacementWithBackreferences() {
		// gensub mode: \N becomes a group reference, trailing backslash is literal
		assertEquals("a$1b", JRT.prepareReplacement("a\\1b", true));
		assertEquals("a\\\\", JRT.prepareReplacement("a\\", true));
		assertEquals("a$0a", JRT.prepareReplacement("a&a", true));
		assertEquals("a&b", JRT.prepareReplacement("a\\&b", true));
	}
}
