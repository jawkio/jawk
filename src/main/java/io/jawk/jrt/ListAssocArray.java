package io.jawk.jrt;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright 2006 - 2026 MetricsHub
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
 * embedding APIs. Java {@link Map} instances remain map-backed for performance,
 * while nested {@link List} instances are replaced by materialized
 * {@link AssocArray} instances. Recursive object graphs are guarded with an
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
		populateFromList(this, values, false, newActiveContainers());
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
		AssocArray array = sortedArrayKeys ? AssocArray.createSorted() : new ListAssocArray();
		populateFromList(array, values, sortedArrayKeys, newActiveContainers());
		return array;
	}

	/**
	 * Normalizes a value supplied from Java before it is stored in the AVM.
	 * <p>
	 * Lists are materialized as AWK arrays. Maps are kept as the original map
	 * instance for the existing no-copy fast path, but their values are scanned so
	 * nested lists can be materialized too.
	 * </p>
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @return the normalized value
	 */
	static Object normalizeValue(Object value, boolean sortedArrayKeys) {
		return normalizeValue(value, sortedArrayKeys, newActiveContainers());
	}

	/**
	 * Normalizes one value within a potentially nested object graph.
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param activeContainers identity-based set of maps/lists currently being
	 *        traversed; used only as a recursion stack to break cycles
	 * @return the normalized value
	 */
	private static Object normalizeValue(Object value, boolean sortedArrayKeys, Set<Object> activeContainers) {
		if (value instanceof List) {
			return createFromList((List<?>) value, sortedArrayKeys, activeContainers);
		}
		if (value instanceof Map) {
			normalizeMapValues(value, sortedArrayKeys, activeContainers);
		}
		return value;
	}

	/**
	 * Creates an associative array from a nested list while sharing the caller's
	 * recursion stack.
	 *
	 * @param values list values to expose as an AWK array
	 * @param sortedArrayKeys {@code true} to use sorted AWK array keys
	 * @param activeContainers identity-based set of maps/lists currently being
	 *        traversed
	 * @return an AWK associative array containing the list values, or an empty
	 *         array when a recursive reference to the same list is detected
	 */
	private static AssocArray createFromList(List<?> values, boolean sortedArrayKeys, Set<Object> activeContainers) {
		AssocArray array = sortedArrayKeys ? AssocArray.createSorted() : new ListAssocArray();
		populateFromList(array, values, sortedArrayKeys, activeContainers);
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
	 * @param activeContainers identity-based recursion stack for maps/lists
	 */
	private static void populateFromList(
			AssocArray target,
			List<?> values,
			boolean sortedArrayKeys,
			Set<Object> activeContainers) {
		if (values == null || !activeContainers.add(values)) {
			return;
		}
		try {
			long index = 0L;
			for (Object listValue : values) {
				target.put(Long.valueOf(index), normalizeValue(listValue, sortedArrayKeys, activeContainers));
				index++;
			}
		} finally {
			activeContainers.remove(values);
		}
	}

	/**
	 * Converts nested lists inside a Java map while preserving the map instance
	 * itself.
	 * <p>
	 * Preserving the map keeps direct map insertion and retrieval fast in the AVM.
	 * Only map values that actually change, normally nested lists, are written
	 * back. The map is removed from {@code activeContainers} in {@code finally}
	 * so another branch can still normalize the same shared map later.
	 * </p>
	 *
	 * @param value map value to normalize in place
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param activeContainers identity-based recursion stack for maps/lists
	 */
	private static void normalizeMapValues(Object value, boolean sortedArrayKeys, Set<Object> activeContainers) {
		if (!activeContainers.add(value)) {
			return;
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> map = (Map<Object, Object>) value;
		try {
			for (Map.Entry<Object, Object> entry : map.entrySet()) {
				Object originalValue = entry.getValue();
				Object normalizedValue = normalizeValue(originalValue, sortedArrayKeys, activeContainers);
				if (normalizedValue != originalValue) {
					try {
						entry.setValue(normalizedValue);
					} catch (UnsupportedOperationException | ClassCastException ex) {
						throw new IllegalArgumentException(
								"Injected maps must be mutable when nested List values need to be materialized as AWK arrays.",
								ex);
					}
				}
			}
		} finally {
			activeContainers.remove(value);
		}
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
