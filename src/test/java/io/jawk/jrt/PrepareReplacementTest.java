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
 * Tests for {@link JRT#prepareSubReplacement(String, boolean)} and
 * {@link JRT#prepareGensubReplacement(String, int)}.
 */
public class PrepareReplacementTest {

	@Test
	public void testPrepareSubReplacementGawkRules() {
		assertEquals("don't change", JRT.prepareSubReplacement("don't change", false));
		assertEquals("a$0a", JRT.prepareSubReplacement("a&a", false));
		assertEquals("1$01", JRT.prepareSubReplacement("1&1", false));
		assertEquals("a$0b$0c", JRT.prepareSubReplacement("a&b&c", false));
		assertEquals("a\\$", JRT.prepareSubReplacement("a$", false));
		// \& is a literal &
		assertEquals("a&b", JRT.prepareSubReplacement("a\\&b", false));
		// \\& is a literal \ followed by the match
		assertEquals("a\\\\$0", JRT.prepareSubReplacement("a\\\\&", false));
		// \\\& is a literal \&
		assertEquals("a\\\\&", JRT.prepareSubReplacement("a\\\\\\&", false));
		// \\\\ is a literal \\, also when followed by &
		assertEquals("a\\\\\\\\", JRT.prepareSubReplacement("a\\\\\\\\", false));
		assertEquals("a\\\\\\\\$0", JRT.prepareSubReplacement("a\\\\\\\\&", false));
		assertEquals("a\\\\\\\\&", JRT.prepareSubReplacement("a\\\\\\\\\\&", false));
		// any other backslash is kept as is: \\ stays \\, \c stays \c
		assertEquals("a\\\\\\\\", JRT.prepareSubReplacement("a\\\\", false));
		assertEquals("a\\\\b", JRT.prepareSubReplacement("a\\b", false));
		assertEquals("a\\\\\\\\q", JRT.prepareSubReplacement("a\\\\q", false));
		assertEquals("a\\\\1", JRT.prepareSubReplacement("a\\1", false));
		assertEquals("a\\\\", JRT.prepareSubReplacement("a\\", false));
		assertEquals("a\\\\\\$", JRT.prepareSubReplacement("a\\$", false));
		assertEquals("a\\\\\\\\\\$", JRT.prepareSubReplacement("a\\\\$", false));
		assertEquals("", JRT.prepareSubReplacement("", false));
		assertEquals("", JRT.prepareSubReplacement(null, false));
	}

	@Test
	public void testPrepareSubReplacementPosixRules() {
		assertEquals("a$0a", JRT.prepareSubReplacement("a&a", true));
		assertEquals("a\\$", JRT.prepareSubReplacement("a$", true));
		// \& is a literal &, \\ a literal \
		assertEquals("a&b", JRT.prepareSubReplacement("a\\&b", true));
		assertEquals("a\\\\", JRT.prepareSubReplacement("a\\\\", true));
		assertEquals("a\\\\$0", JRT.prepareSubReplacement("a\\\\&", true));
		assertEquals("a\\\\&", JRT.prepareSubReplacement("a\\\\\\&", true));
		assertEquals("a\\\\\\\\", JRT.prepareSubReplacement("a\\\\\\\\", true));
		assertEquals("a\\\\q", JRT.prepareSubReplacement("a\\\\q", true));
		// any other backslash is kept as is
		assertEquals("a\\\\b", JRT.prepareSubReplacement("a\\b", true));
		assertEquals("a\\\\1", JRT.prepareSubReplacement("a\\1", true));
		assertEquals("a\\\\", JRT.prepareSubReplacement("a\\", true));
		assertEquals("a\\\\\\$", JRT.prepareSubReplacement("a\\$", true));
		assertEquals("a\\\\\\$", JRT.prepareSubReplacement("a\\\\$", true));
		assertEquals("", JRT.prepareSubReplacement(null, true));
	}

	@Test
	public void testPrepareGensubReplacement() {
		assertEquals("don't change", JRT.prepareGensubReplacement("don't change", 0));
		assertEquals("a$0a", JRT.prepareGensubReplacement("a&a", 0));
		assertEquals("a\\$", JRT.prepareGensubReplacement("a$", 0));
		// \N is a group reference, the empty string beyond the pattern's groups
		assertEquals("a$1b", JRT.prepareGensubReplacement("a\\1b", 1));
		assertEquals("a$0b", JRT.prepareGensubReplacement("a\\0b", 0));
		assertEquals("ab", JRT.prepareGensubReplacement("a\\2b", 1));
		// \& is a literal &, \\ a literal \, a trailing \ a literal \
		assertEquals("a&b", JRT.prepareGensubReplacement("a\\&b", 0));
		assertEquals("a\\\\b", JRT.prepareGensubReplacement("a\\\\b", 0));
		assertEquals("a\\\\", JRT.prepareGensubReplacement("a\\", 0));
		// any other \c is a plain c
		assertEquals("aq", JRT.prepareGensubReplacement("a\\q", 0));
		assertEquals("a\\$", JRT.prepareGensubReplacement("a\\$", 0));
		assertEquals("", JRT.prepareGensubReplacement("", 0));
		assertEquals("", JRT.prepareGensubReplacement(null, 0));
	}
}
