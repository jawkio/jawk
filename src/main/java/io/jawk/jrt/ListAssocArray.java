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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * An AWK associative array materialized from a Java {@link List}.
 * <p>
 * The source list is copied once into normal AWK array storage using zero-based
 * {@link Long} keys. After construction, this array behaves like any other
 * {@link AssocArray}: scripts may add string keys, delete entries, or create
 * sparse numeric indexes without being constrained by {@link List} semantics.
 * </p>
 * <p>
 * This class also owns normalization of nested Java containers supplied from
 * embedding APIs. Java {@link Map} and {@link List} instances, at any depth, are
 * copied into new {@link AssocArray} instances, never modified, and String
 * leaves can be converted to input scalars (numeric strings). Recursive object
 * graphs are guarded with an
 * identity-based recursion stack so self-referencing maps or lists do not cause
 * infinite conversion.
 * </p>
 *
 * @author MetricsHub
 */
public final class ListAssocArray extends HashAssocArray {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a hash-backed associative array from the supplied list.
	 *
	 * @param values list values to expose as an AWK array
	 */
	public ListAssocArray(List<?> values) {
		populateFromList(this, values, false, UnaryOperator.identity(), newActiveContainers());
	}

	/**
	 * Creates an empty hash-backed instance for factory methods that populate the
	 * array with a shared recursion guard.
	 */
	private ListAssocArray() {}

	/**
	 * Creates an associative array from the supplied list, honoring the runtime
	 * key-ordering setting.
	 *
	 * @param values list values to expose as an AWK array
	 * @param sortedArrayKeys {@code true} to use sorted AWK array keys
	 * @return an AWK associative array containing the list values under zero-based
	 *         {@link Long} keys
	 */
	static AssocArray createFromList(List<?> values, boolean sortedArrayKeys) {
		return createFromList(values, sortedArrayKeys, UnaryOperator.identity(), newActiveContainers());
	}

	/**
	 * Normalizes a value supplied from Java before it is stored in the AVM.
	 * <p>
	 * Lists and maps, at any depth, are copied into new AWK arrays; the caller's
	 * containers are never modified.
	 * </p>
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param stringConverter conversion applied to every {@link String} found
	 *        inside a list or map, at any depth, and to every {@link StrNum}
	 *        (which an array from an earlier run may hold): it receives either
	 *        type
	 * @return the normalized value
	 */
	static Object normalizeValue(Object value, boolean sortedArrayKeys, UnaryOperator<Object> stringConverter) {
		return normalizeValue(value, sortedArrayKeys, stringConverter, newActiveContainers());
	}

	/**
	 * Normalizes one value within a potentially nested object graph.
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param stringConverter conversion applied to {@link String} values
	 * @param activeContainers identity-based set of maps/lists currently being
	 *        traversed; used only as a recursion stack to break cycles
	 * @return the normalized value
	 */
	private static Object normalizeValue(
			Object value,
			boolean sortedArrayKeys,
			UnaryOperator<Object> stringConverter,
			Set<Object> activeContainers) {
		if (value instanceof List) {
			return createFromList((List<?>) value, sortedArrayKeys, stringConverter, activeContainers);
		}
		if (value instanceof Map) {
			return createFromMap((Map<?, ?>) value, sortedArrayKeys, stringConverter, activeContainers);
		}
		if (value instanceof String || value instanceof StrNum) {
			// A StrNum comes from an array of a previous run: the converter rebinds it
			// to the current run's decimal separator
			return stringConverter.apply(value);
		}
		return value;
	}

	/**
	 * Creates an associative array from a nested list while sharing the caller's
	 * recursion stack.
	 *
	 * @param values list values to expose as an AWK array
	 * @param sortedArrayKeys {@code true} to use sorted AWK array keys
	 * @param stringConverter conversion applied to {@link String} values
	 * @param activeContainers identity-based set of maps/lists currently being
	 *        traversed
	 * @return an AWK associative array containing the list values, or an empty
	 *         array when a recursive reference to the same list is detected
	 */
	private static AssocArray createFromList(
			List<?> values,
			boolean sortedArrayKeys,
			UnaryOperator<Object> stringConverter,
			Set<Object> activeContainers) {
		AssocArray array = sortedArrayKeys ? AssocArray.createSorted() : new ListAssocArray();
		populateFromList(array, values, sortedArrayKeys, stringConverter, activeContainers);
		return array;
	}

	/**
	 * Copies a Java list into an AWK associative array using zero-based
	 * {@link Long} keys.
	 * <p>
	 * {@code activeContainers} is intentionally a recursion stack, not a permanent
	 * visited set. The same Java list may appear in two different branches of the
	 * input graph and should be materialized in both places. It is skipped only
	 * when it appears again while already being copied, which indicates a cycle.
	 * </p>
	 *
	 * @param target associative array to populate
	 * @param values list values to copy; {@code null} leaves {@code target} empty
	 * @param sortedArrayKeys {@code true} when nested lists should use sorted AWK
	 *        array keys
	 * @param stringConverter conversion applied to {@link String} values
	 * @param activeContainers identity-based recursion stack for maps/lists
	 */
	private static void populateFromList(
			AssocArray target,
			List<?> values,
			boolean sortedArrayKeys,
			UnaryOperator<Object> stringConverter,
			Set<Object> activeContainers) {
		if (values == null || !activeContainers.add(values)) {
			return;
		}
		try {
			long index = 0L;
			for (Object listValue : values) {
				target.put(Long.valueOf(index), normalizeValue(listValue, sortedArrayKeys, stringConverter, activeContainers));
				index++;
			}
		} finally {
			activeContainers.remove(values);
		}
	}

	/**
	 * Copies a Java map into a new AWK associative array, normalizing every value.
	 * <p>
	 * The caller's map is never modified, so read-only, type-checked or
	 * concurrent maps behave like any other, and the script's writes stay in the
	 * copy. Keys are stored with AWK key semantics, and iteration follows the AWK
	 * array's order, not the source map's. A map that contains itself is copied
	 * as an empty array at the point of recursion, as lists are.
	 * </p>
	 *
	 * @param values map to copy
	 * @param sortedArrayKeys {@code true} to use sorted AWK array keys
	 * @param stringConverter conversion applied to {@link String} values
	 * @param activeContainers identity-based recursion stack for maps/lists
	 * @return an AWK associative array holding the normalized entries
	 */
	private static AssocArray createFromMap(
			Map<?, ?> values,
			boolean sortedArrayKeys,
			UnaryOperator<Object> stringConverter,
			Set<Object> activeContainers) {
		AssocArray array = AssocArray.create(sortedArrayKeys);
		if (!activeContainers.add(values)) {
			return array;
		}
		try {
			for (Map.Entry<?, ?> entry : values.entrySet()) {
				array.put(entry.getKey(), normalizeValue(entry.getValue(), sortedArrayKeys, stringConverter, activeContainers));
			}
		} finally {
			activeContainers.remove(values);
		}
		return array;
	}

	/**
	 * Creates the recursion stack used during normalization.
	 * <p>
	 * Identity comparison is required here. Calling {@link Object#equals(Object)}
	 * on arbitrary maps or lists can be expensive, can treat separate but equal
	 * containers as the same object, and can itself recurse on cyclic structures.
	 * </p>
	 *
	 * @return an empty identity-based set for active container objects
	 */
	private static Set<Object> newActiveContainers() {
		return Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
	}
}
