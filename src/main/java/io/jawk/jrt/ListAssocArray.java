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
import java.util.LinkedHashMap;
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
 * embedding APIs. Java {@link Map} instances remain map-backed for performance,
 * while nested {@link List} instances are replaced by materialized
 * {@link AssocArray} instances and String leaves can be converted to input
 * scalars (numeric strings). Recursive object graphs are guarded with an
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
	 * Lists are materialized as AWK arrays. Maps are kept as the original map
	 * instance for the existing no-copy fast path, but their values are scanned so
	 * nested lists can be materialized and String leaves converted too. A map
	 * that rejects these updates (read-only or type-checked) is copied instead.
	 * </p>
	 *
	 * @param value value to normalize
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param stringConverter conversion applied to every {@link String} found
	 *        inside a list or map, at any depth
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
			@SuppressWarnings("unchecked")
			Map<Object, Object> map = (Map<Object, Object>) value;
			return normalizeMapValues(map, sortedArrayKeys, stringConverter, activeContainers);
		}
		if (value instanceof String) {
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
	 * Converts nested lists and String values inside a Java map while preserving
	 * the map instance itself when possible.
	 * <p>
	 * Preserving the map keeps direct map insertion and retrieval fast in the AVM.
	 * Only map values that actually change, nested lists and converted Strings,
	 * are written back. A map that rejects the write (read-only or type-checked)
	 * is copied into a {@link LinkedHashMap}, which is normalized and returned
	 * instead, leaving the caller's map untouched. The map is removed from
	 * {@code activeContainers} in {@code finally} so another branch can still
	 * normalize the same shared map later.
	 * </p>
	 *
	 * @param map map value to normalize in place
	 * @param sortedArrayKeys {@code true} when materialized lists should use
	 *        sorted AWK array keys
	 * @param stringConverter conversion applied to {@link String} values
	 * @param activeContainers identity-based recursion stack for maps/lists
	 * @return {@code map} itself, or its normalized copy when {@code map}
	 *         rejected an update
	 */
	private static Map<Object, Object> normalizeMapValues(
			Map<Object, Object> map,
			boolean sortedArrayKeys,
			UnaryOperator<Object> stringConverter,
			Set<Object> activeContainers) {
		if (!activeContainers.add(map)) {
			return map;
		}
		try {
			for (Map.Entry<Object, Object> entry : map.entrySet()) {
				Object originalValue = entry.getValue();
				Object normalizedValue = normalizeValue(originalValue, sortedArrayKeys, stringConverter, activeContainers);
				if (normalizedValue != originalValue) {
					try {
						entry.setValue(normalizedValue);
					} catch (UnsupportedOperationException | ClassCastException ex) {
						return normalizeMapValues(new LinkedHashMap<>(map), sortedArrayKeys, stringConverter, activeContainers);
					}
				}
			}
			return map;
		} finally {
			activeContainers.remove(map);
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
