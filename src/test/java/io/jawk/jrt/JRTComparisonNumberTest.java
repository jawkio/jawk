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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JRTComparisonNumberTest {

	@Test
	public void testIsComparisonNumberAcceptsDecimalForms() {
		assertTrue(JRT.isComparisonNumber("0"));
		assertTrue(JRT.isComparisonNumber("123"));
		assertTrue(JRT.isComparisonNumber("+123"));
		assertTrue(JRT.isComparisonNumber("-123"));
		assertTrue(JRT.isComparisonNumber("123.45"));
		assertTrue(JRT.isComparisonNumber("+.5"));
		assertTrue(JRT.isComparisonNumber("5."));
		assertTrue(JRT.isComparisonNumber("1e2"));
		assertTrue(JRT.isComparisonNumber("1E2"));
		assertTrue(JRT.isComparisonNumber("-1E+2"));
		assertTrue(JRT.isComparisonNumber("+1e-2"));
	}

	@Test
	public void testIsComparisonNumberRejectsInvalidDecimalForms() {
		assertFalse(JRT.isComparisonNumber(""));
		assertFalse(JRT.isComparisonNumber("+"));
		assertFalse(JRT.isComparisonNumber("-"));
		assertFalse(JRT.isComparisonNumber("."));
		assertFalse(JRT.isComparisonNumber("e1"));
		assertFalse(JRT.isComparisonNumber("1e"));
		assertFalse(JRT.isComparisonNumber("1e+"));
		assertFalse(JRT.isComparisonNumber("1e-"));
		assertFalse(JRT.isComparisonNumber("1.2.3"));
		assertFalse(JRT.isComparisonNumber("123abc"));
		assertFalse(JRT.isComparisonNumber("abc123"));
	}

	@Test
	public void testIsComparisonNumberRejectsHexadecimal() {
		assertFalse(JRT.isComparisonNumber("0x0"));
		assertFalse(JRT.isComparisonNumber("0x10"));
		assertFalse(JRT.isComparisonNumber("-0x10"));
		assertFalse(JRT.isComparisonNumber("+0XFF"));
	}
}
