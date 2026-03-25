package org.metricshub.jawk.jrt;

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

import org.metricshub.jawk.intermediate.UninitializedObject;

/**
 * Package-private utility class providing shared key-normalization helpers for
 * the {@link AssocArray} implementations.
 *
 * @author MetricsHub
 */
final class AssocArrayHelper {

	/** A "null" value in AWK (shared blank reference). */
	static final UninitializedObject BLANK = new UninitializedObject();

	private AssocArrayHelper() {}

	/**
	 * Converts a key to the canonical form expected by AWK: {@code null} and
	 * {@link UninitializedObject} map to the empty string.
	 *
	 * @param key the raw key
	 * @return the normalized key, never {@code null}
	 */
	static Object normalizeKey(Object key) {
		return (key == null || key instanceof UninitializedObject) ? "" : key;
	}

	/**
	 * Attempts to parse the key as a {@code Long}.
	 *
	 * @param key the key to parse (must not be {@code null})
	 * @return the {@code Long} value, or {@code null} if the key cannot be parsed
	 *         as a long integer
	 */
	static Long toLongKey(Object key) {
		try {
			return Long.parseLong(key.toString());
		} catch (Exception e) { // NOPMD - EmptyCatchBlock: intentionally ignored
			return null;
		}
	}
}
