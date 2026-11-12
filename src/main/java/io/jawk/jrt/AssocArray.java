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

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.intermediate.UntypedObject;

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
 * <li>{@link ListAssocArray} &mdash; materialized from a {@link java.util.List}
 * and backed by {@link java.util.HashMap}</li>
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
 * AssocArray list = AssocArray.createFromList(values, sortedArrayKeys);
 * AssocArray aa = AssocArray.create(sortedArrayKeys);
 * </pre>
 *
 * @author Danny Daglas
 */
public interface AssocArray extends Map<Object, Object> {

	/** A blank (uninitialized) value shared across all AWK array accesses. */
	UninitializedObject BLANK = new UninitializedObject();

	/** An untyped value shared across newly referenced AWK array elements. */
	UntypedObject UNTYPED = new UntypedObject();

// -------------------------------------------------------------------------
// Key-normalization helpers (used by concrete implementations)
// -------------------------------------------------------------------------

	/**
	 * Converts a key to the canonical form expected by AWK: {@code null} and
	 * {@link UninitializedObject} map to the empty string, and internal input
	 * strings map to their string value.
	 *
	 * @param key the raw key
	 * @return the normalized key, never {@code null}
	 */
	static Object normalizeKey(Object key) {
		if (key == null || key instanceof UninitializedObject) {
			return "";
		}
		if (key instanceof StrNum) {
			return key.toString();
		}
		if (key instanceof Double || key instanceof Float) {
			double numericKey = ((Number) key).doubleValue();
			if (JRT.isActuallyLong(numericKey)) {
				return Long.valueOf((long) Math.rint(numericKey));
			}
		}
		return key;
	}

	/**
	 * Returns whether a subscript value is an integral number that
	 * {@link #normalizeKey(Object)} canonicalizes to the same {@code Long}
	 * key as its AWK string form. Such a subscript can be used as a key
	 * directly, without the CONVFMT string conversion that every other
	 * scalar goes through: the resulting key is identical, and skipping the
	 * string round-trip keeps integer-indexed loops fast.
	 *
	 * @param value the subscript value to examine
	 * @return {@code true} if the value can be used as a key without string
	 *         conversion
	 */
	static boolean isIntegralNumberKey(Object value) {
		if (value instanceof Long || value instanceof Integer) {
			return true;
		}
		return value instanceof Double && JRT.toScalarNumber((Double) value) instanceof Long;
	}

	/**
	 * Attempts to parse the key as a {@code Long}.
	 * <p>
	 * This accepts and rejects exactly the same inputs as
	 * {@link Long#parseLong(String)} (radix 10), but scans the string without
	 * ever constructing an exception: most keys are not integer strings (every
	 * multidimensional {@code arr[x,y]} key, for instance), and paying the cost
	 * of a {@link NumberFormatException} stack-trace fill on every array access
	 * is prohibitive.
	 * </p>
	 *
	 * @param key the key to parse (must not be {@code null})
	 * @return the {@code Long} value, or {@code null} if the key cannot be parsed
	 *         as a long integer
	 */
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Defensive check against contract-violating toString() implementations returning null")
	static Long toLongKey(Object key) {
		final String str;
		if (key instanceof String) {
			str = (String) key;
		} else {
			try {
				str = key.toString();
			} catch (RuntimeException e) { // NOPMD - EmptyCatchBlock: intentionally ignored
				// e.g. an AssocArray used as a key throws on toString():
				// treat such keys as non-numeric
				return null;
			}
			if (str == null) {
				// a contract-violating toString(): treat the key as non-numeric,
				// like the former Long.parseLong(null) NumberFormatException path
				return null;
			}
		}
		final int len = str.length();
		if (len == 0) {
			return null;
		}
		int i = 0;
		boolean negative = false;
		long limit = -Long.MAX_VALUE;
		final char firstChar = str.charAt(0);
		if (firstChar < '0') { // possible leading "+" or "-"
			if (firstChar == '-') {
				negative = true;
				limit = Long.MIN_VALUE;
			} else if (firstChar != '+') {
				return null;
			}
			if (len == 1) { // lone "+" or "-"
				return null;
			}
			i = 1;
		}
		// Accumulate negatively, exactly like Long.parseLong, so that
		// Long.MIN_VALUE (whose magnitude exceeds Long.MAX_VALUE) parses too
		final long multmin = limit / 10;
		long result = 0;
		while (i < len) {
			// Character.digit keeps parity with Long.parseLong on non-ASCII digits
			final int digit = Character.digit(str.charAt(i++), 10);
			if (digit < 0 || result < multmin) {
				return null;
			}
			result *= 10;
			if (result < limit + digit) {
				return null;
			}
			result -= digit;
		}
		return negative ? result : -result;
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
		key = normalizeKey(key);
		if (containsKey(key)) {
			return true;
		}
		Long lKey = toLongKey(key);
		return lKey != null && containsKey(lKey);
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

	/**
	 * Creates a new associative array materialized from a Java {@link List}.
	 * <p>
	 * List elements are stored under zero-based {@link Long} keys. Nested
	 * {@link List} values are recursively materialized as associative arrays so
	 * JSON-like object trees can be traversed with AWK array syntax.
	 * </p>
	 *
	 * @param values list values to expose as an AWK array
	 * @param sortedArrayKeys {@code true} to create a sorted array, {@code false}
	 *        for a hash-backed array
	 * @return a new {@link AssocArray} containing the list values
	 */
	static AssocArray createFromList(List<?> values, boolean sortedArrayKeys) {
		return ListAssocArray.createFromList(values, sortedArrayKeys);
	}

	/**
	 * Normalizes an externally supplied structured value before the AVM stores it.
	 * <p>
	 * {@link List} and {@link Map} values, at any depth, are copied into new
	 * {@link AssocArray} instances; the caller's containers are never modified.
	 * </p>
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when converted lists should use sorted
	 *        array keys
	 * @return the normalized value
	 */
	static Object normalizeValue(Object value, boolean sortedArrayKeys) {
		return normalizeValue(value, sortedArrayKeys, UnaryOperator.identity());
	}

	/**
	 * Normalizes an externally supplied structured value before the AVM stores it,
	 * converting every {@link String} inside a {@link List} or {@link Map}, at any
	 * depth, with {@code stringConverter}.
	 * <p>
	 * The AVM passes {@code JRT::toInputScalar} so that nested strings become
	 * numeric strings, like input fields. Lists and maps are copied, never
	 * modified.
	 * </p>
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when converted lists should use sorted
	 *        array keys
	 * @param stringConverter conversion applied to nested {@link String} values
	 * @return the normalized value
	 */
	static Object normalizeValue(Object value, boolean sortedArrayKeys, UnaryOperator<Object> stringConverter) {
		return ListAssocArray.normalizeValue(value, sortedArrayKeys, stringConverter);
	}

}
