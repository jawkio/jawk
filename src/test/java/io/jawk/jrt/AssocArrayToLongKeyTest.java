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
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AssocArrayToLongKeyTest {

	/** The default AWK SUBSEP character, used to join multidimensional keys. */
	private static final String SUBSEP = String.valueOf((char) 0x1C);

	@Test
	public void testAcceptsIntegerForms() {
		assertEquals(Long.valueOf(0L), AssocArray.toLongKey("0"));
		assertEquals(Long.valueOf(42L), AssocArray.toLongKey("42"));
		assertEquals(Long.valueOf(42L), AssocArray.toLongKey("+42"));
		assertEquals(Long.valueOf(-42L), AssocArray.toLongKey("-42"));
		assertEquals(Long.valueOf(0L), AssocArray.toLongKey("-0"));
		assertEquals(Long.valueOf(0L), AssocArray.toLongKey("+0"));
		assertEquals(Long.valueOf(7L), AssocArray.toLongKey("007"));
		assertEquals(Long.valueOf(42L), AssocArray.toLongKey("0000000000000000000042"));
		assertEquals(Long.valueOf(Long.MAX_VALUE), AssocArray.toLongKey("9223372036854775807"));
		assertEquals(Long.valueOf(Long.MIN_VALUE), AssocArray.toLongKey("-9223372036854775808"));
	}

	@Test
	public void testAcceptsNonStringKeys() {
		assertEquals(Long.valueOf(42L), AssocArray.toLongKey(Long.valueOf(42L)));
		assertEquals(Long.valueOf(-7L), AssocArray.toLongKey(Integer.valueOf(-7)));
	}

	@Test
	public void testRejectsNonIntegerForms() {
		assertNull(AssocArray.toLongKey(""));
		assertNull(AssocArray.toLongKey("+"));
		assertNull(AssocArray.toLongKey("-"));
		assertNull(AssocArray.toLongKey(" 42"));
		assertNull(AssocArray.toLongKey("42 "));
		assertNull(AssocArray.toLongKey("4 2"));
		assertNull(AssocArray.toLongKey("42.0"));
		assertNull(AssocArray.toLongKey("4.2e1"));
		assertNull(AssocArray.toLongKey("0x2A"));
		assertNull(AssocArray.toLongKey("42abc"));
		assertNull(AssocArray.toLongKey("abc42"));
		assertNull(AssocArray.toLongKey("--42"));
		assertNull(AssocArray.toLongKey("+-42"));
		assertNull(AssocArray.toLongKey("4+2"));
		assertNull(AssocArray.toLongKey("42-"));
		// SUBSEP-joined multidimensional key
		assertNull(AssocArray.toLongKey("1" + SUBSEP + "2"));
	}

	@Test
	public void testReturnsNullWhenKeyToStringThrows() {
		// Extensions allow associative arrays as keys; their toString()
		// intentionally throws, and such keys must be treated as non-numeric
		assertNull(AssocArray.toLongKey(AssocArray.createHash()));
		assertNull(AssocArray.toLongKey(AssocArray.createSorted()));
	}

	@Test
	public void testReturnsNullWhenKeyToStringReturnsNull() {
		// A contract-violating toString() must be treated as non-numeric,
		// exactly like the former Long.parseLong(null) failure path
		Object badKey = new Object() {
			@Override
			public String toString() {
				return null;
			}
		};
		assertNull(AssocArray.toLongKey(badKey));
	}

	@Test
	public void testRejectsOutOfRangeValues() {
		assertNull(AssocArray.toLongKey("9223372036854775808"));
		assertNull(AssocArray.toLongKey("-9223372036854775809"));
		assertNull(AssocArray.toLongKey("12345678901234567890"));
		assertNull(AssocArray.toLongKey("-12345678901234567890"));
		assertNull(AssocArray.toLongKey("99999999999999999999999999999"));
	}

	/**
	 * Exhaustive parity check with the reference implementation: for every
	 * sample, {@code toLongKey} must accept/reject exactly what
	 * {@link Long#parseLong(String)} accepts/rejects, and produce the same value.
	 */
	@Test
	public void testMatchesLongParseLongExactly() {
		String[] samples = {
				"0",
				"1",
				"-1",
				"+1",
				"42",
				"+42",
				"-42",
				"007",
				"-007",
				"+007",
				"",
				" ",
				"+",
				"-",
				"++1",
				"--1",
				"+-1",
				"-+1",
				" 42",
				"42 ",
				"4 2",
				"\t42",
				"42\n",
				"9223372036854775806",
				"9223372036854775807",
				"9223372036854775808",
				"-9223372036854775807",
				"-9223372036854775808",
				"-9223372036854775809",
				"0000000000000000000042",
				"-0000000000000000000042",
				"12345678901234567890",
				"99999999999999999999999999999",
				"3.14",
				"1e5",
				"0x1F",
				"Infinity",
				"NaN",
				"abc",
				"42abc",
				"abc42",
				"1" + SUBSEP + "2",
				"a" + SUBSEP + "b",
				// non-ASCII digits: Long.parseLong accepts these via Character.digit
				"١٢٣", // Arabic-Indic "123"
				"１２", // fullwidth "12"
				"-١",
				"١a",
				"1٢", };
		for (String sample : samples) {
			Long expected;
			try {
				expected = Long.parseLong(sample);
			} catch (NumberFormatException e) {
				expected = null;
			}
			assertEquals("key: \"" + sample + "\"", expected, AssocArray.toLongKey(sample));
		}
	}
}
