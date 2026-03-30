package io.jawk.backend;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ••••••
 * Copyright (C) 2006 - 2026 MetricsHub
 * ••••••
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link RegexRuntimeSupport}.
 */
public class RegexRuntimeSupportTest {

	@Test
	public void testPrepareReplacement() {
		assertEquals("don't change", RegexRuntimeSupport.prepareReplacement("don't change"));
		assertEquals("a$0a", RegexRuntimeSupport.prepareReplacement("a&a"));
		assertEquals("1$01", RegexRuntimeSupport.prepareReplacement("1&1"));
		assertEquals("a$0b$0c", RegexRuntimeSupport.prepareReplacement("a&b&c"));
		assertEquals("a\\b", RegexRuntimeSupport.prepareReplacement("a\\b"));
		assertEquals("a&b", RegexRuntimeSupport.prepareReplacement("a\\&b"));
		assertEquals("a\\", RegexRuntimeSupport.prepareReplacement("a\\"));
		assertEquals("a\\$", RegexRuntimeSupport.prepareReplacement("a$"));
		assertEquals("a\\\\$", RegexRuntimeSupport.prepareReplacement("a\\$"));
		assertEquals("a\\\\\\$", RegexRuntimeSupport.prepareReplacement("a\\\\$"));
		assertEquals("a\\\\$0", RegexRuntimeSupport.prepareReplacement("a\\\\&"));
		assertEquals("a\\\\&", RegexRuntimeSupport.prepareReplacement("a\\\\\\&"));
		assertEquals("", RegexRuntimeSupport.prepareReplacement(""));
		assertEquals("", RegexRuntimeSupport.prepareReplacement(null));
	}
}
