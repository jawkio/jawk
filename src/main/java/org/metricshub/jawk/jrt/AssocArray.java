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

import java.util.Map;
import org.metricshub.jawk.intermediate.UninitializedObject;

/**
 * An AWK associative array.
 * <p>
 * This interface extends {@link Map} and provides AWK-specific behaviour:
 * automatic key normalization (null and uninitialized values map to {@code ""}),
 * numeric key coercion ({@code "1"} and {@code 1L} address the same slot), and
 * auto-creation of blank entries on first access.
 * </p>
 * <p>
 * Concrete implementations directly extend a JDK {@link Map} class to avoid
 * delegation overhead:
 * </p>
 * <ul>
 * <li>{@link HashAssocArray} &mdash; backed by {@link java.util.HashMap}</li>
 * <li>{@link SortedAssocArray} &mdash; backed by {@link java.util.TreeMap} with
 * AWK key ordering</li>
 * </ul>
 * <p>
 * Use the factory methods to create instances:
 * </p>
 *
 * <pre>
 * AssocArray hash = AssocArray.createHash();
 * AssocArray sorted = AssocArray.createSorted();
 * AssocArray aa = AssocArray.create(sortedArrayKeys);
 * </pre>
 *
 * @author Danny Daglas
 */
public interface AssocArray extends Map<Object, Object> {

	/** A blank (uninitialized) value shared across all AWK array accesses. */
	UninitializedObject BLANK = new UninitializedObject();

// -------------------------------------------------------------------------
// Key-normalization helpers (used by concrete implementations)
// -------------------------------------------------------------------------

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

// -------------------------------------------------------------------------
// AWK-specific default methods
// -------------------------------------------------------------------------

	/**
	 * Returns whether a particular key is contained within the associative array.
	 * <p>
	 * Unlike {@link #get(Object)}, which auto-creates a blank entry when the key
	 * is absent, this method does not modify the array. It exists to support the
	 * AWK {@code IN} keyword.
	 * </p>
	 *
	 * @param key Key to be checked
	 * @return {@code true} if the key (or its numeric equivalent) is present
	 */
	default boolean isIn(Object key) {
		if (key == null || key instanceof UninitializedObject) {
// According to AWK semantics, an uninitialized index
// evaluates to the empty string, not numeric zero
			key = "";
		}
		if (containsKey(key)) {
			return true;
		}
		try {
			long iKey = Long.parseLong(key.toString());
			return containsKey(iKey);
		} catch (Exception e) { // NOPMD - EmptyCatchBlock: intentionally ignored
		}
		return false;
	}

	/**
	 * Provides a string representation of this associative array, recursively
	 * rendering nested arrays.
	 *
	 * @return a human-readable map string of the form {@code {key=value, ...}}
	 */
	default String mapString() {
// Since extensions allow assoc arrays to become keys as well,
// we render nested arrays recursively rather than using toString().
		StringBuilder sb = new StringBuilder().append('{');
		int cnt = 0;
		for (Map.Entry<Object, Object> entry : entrySet()) {
			if (cnt > 0) {
				sb.append(", ");
			}
			Object key = entry.getKey();
			if (key instanceof AssocArray) {
				sb.append(((AssocArray) key).mapString());
			} else {
				sb.append(key.toString());
			}
			sb.append('=');
			Object value = entry.getValue();
			if (value instanceof AssocArray) {
				sb.append(((AssocArray) value).mapString());
			} else {
				sb.append(value.toString());
			}
			++cnt;
		}
		return sb.append('}').toString();
	}

	/**
	 * Stores a value using a primitive {@code long} key, bypassing string parsing.
	 * <p>
	 * This is a convenience overload for callers that already hold a {@code long}
	 * key. The default implementation boxes the key and delegates to
	 * {@link #put(Object, Object)}.
	 * </p>
	 *
	 * @param key the long key
	 * @param value the value to associate with the key
	 * @return the previous value associated with the key, or {@code null}
	 */
	default Object put(long key, Object value) {
		return put(Long.valueOf(key), value);
	}

	/**
	 * Returns the specification version of the underlying JDK {@link Map} class
	 * that backs this implementation.
	 *
	 * @return the specification version string, or {@code null} if unavailable
	 */
	default String getMapVersion() {
		return getClass().getSuperclass().getPackage().getSpecificationVersion();
	}

// -------------------------------------------------------------------------
// Factory methods
// -------------------------------------------------------------------------

	/**
	 * Creates a new hash-based associative array (backed by {@link java.util.HashMap}).
	 *
	 * @return a new {@link HashAssocArray}
	 */
	static AssocArray createHash() {
		return new HashAssocArray();
	}

	/**
	 * Creates a new sorted associative array (backed by {@link java.util.TreeMap}
	 * with AWK key ordering).
	 *
	 * @return a new {@link SortedAssocArray}
	 */
	static AssocArray createSorted() {
		return new SortedAssocArray();
	}

	/**
	 * Creates a new associative array of the appropriate type.
	 *
	 * @param sortedArrayKeys {@code true} to create a sorted (tree-backed) array,
	 *        {@code false} for a hash-backed array
	 * @return a new {@link AssocArray} instance
	 */
	static AssocArray create(boolean sortedArrayKeys) {
		return sortedArrayKeys ? createSorted() : createHash();
	}
}
