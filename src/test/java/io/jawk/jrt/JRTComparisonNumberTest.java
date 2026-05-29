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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jawk.intermediate.UninitializedObject;
import org.junit.Test;

public class JRTComparisonNumberTest {

	@Test
	public void testIsParseableNumberAcceptsDecimalForms() {
		assertTrue(JRT.isParseableNumber("0", '.'));
		assertTrue(JRT.isParseableNumber("123", '.'));
		assertTrue(JRT.isParseableNumber("+123", '.'));
		assertTrue(JRT.isParseableNumber("-123", '.'));
		assertTrue(JRT.isParseableNumber("123.45", '.'));
		assertTrue(JRT.isParseableNumber("+.5", '.'));
		assertTrue(JRT.isParseableNumber("5.", '.'));
		assertTrue(JRT.isParseableNumber("1e2", '.'));
		assertTrue(JRT.isParseableNumber("1E2", '.'));
		assertTrue(JRT.isParseableNumber("-1E+2", '.'));
		assertTrue(JRT.isParseableNumber("+1e-2", '.'));
	}

	@Test
	public void testIsParseableNumberRejectsInvalidDecimalForms() {
		assertFalse(JRT.isParseableNumber("", '.'));
		assertFalse(JRT.isParseableNumber("+", '.'));
		assertFalse(JRT.isParseableNumber("-", '.'));
		assertFalse(JRT.isParseableNumber(".", '.'));
		assertFalse(JRT.isParseableNumber("e1", '.'));
		assertFalse(JRT.isParseableNumber("1e", '.'));
		assertFalse(JRT.isParseableNumber("1e+", '.'));
		assertFalse(JRT.isParseableNumber("1e-", '.'));
		assertFalse(JRT.isParseableNumber("1.2.3", '.'));
		assertFalse(JRT.isParseableNumber("123abc", '.'));
		assertFalse(JRT.isParseableNumber("abc123", '.'));
	}

	@Test
	public void testIsParseableNumberRejectsHexadecimal() {
		assertFalse(JRT.isParseableNumber("0x0", '.'));
		assertFalse(JRT.isParseableNumber("0x10", '.'));
		assertFalse(JRT.isParseableNumber("-0x10", '.'));
		assertFalse(JRT.isParseableNumber("+0XFF", '.'));
	}

	@Test
	public void testIsParseableNumberUsesLocaleDecimalSeparator() {
		assertTrue(JRT.isParseableNumber("3,14", ','));
		assertFalse(JRT.isParseableNumber("3.14", ','));
	}

	@Test
	public void testStrNumComparesNumericallyAgainstNumber() {
		assertTrue(JRT.compare2(new StrNum("9"), 10L, -1));
		assertTrue(JRT.compare2(10L, new StrNum("9"), 1));
		assertTrue(JRT.compare2(new StrNum("3.0"), 3L, 0));
	}

	@Test
	public void testPlainStringForcesStringComparison() {
		assertFalse(JRT.compare2("9", 10L, -1));
		assertFalse(JRT.compare2(9L, "10", -1));
		assertFalse(JRT.compare2(new StrNum("9"), "10", -1));
	}

	@Test
	public void testNonNumericStrNumFallsBackToStringComparison() {
		Object value = new StrNum("2x");
		assertTrue(value instanceof StrNum);
		assertFalse(JRT.compare2(value, 10L, -1));
		assertEquals(2.0D, JRT.toDouble(value), 0.0D);
	}

	@Test
	public void testUninitializedEqualsNumericZeroStrNum() {
		assertTrue(JRT.compare2(new UninitializedObject(), new StrNum("0.000"), 0));
		assertTrue(JRT.compare2(new StrNum("0.000"), new UninitializedObject(), 0));
		assertFalse(JRT.compare2(new UninitializedObject(), new StrNum("0.000"), -1));
	}
}
