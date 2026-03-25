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

import java.util.LinkedHashMap;

/**
 * An AWK associative array backed by a {@link LinkedHashMap}.
 * <p>
 * Keys are maintained in insertion order, which is useful when predictable
 * iteration order is required.
 * </p>
 *
 * @author MetricsHub
 */
public class LinkedAssocArray extends LinkedHashMap<Object, Object> implements AssocArray {

	private static final long serialVersionUID = 1L;

	/**
	 * Returns the value to which the specified key is mapped, normalizing the key
	 * first. If the key does not exist, a blank ({@link org.metricshub.jawk.intermediate.UninitializedObject})
	 * is inserted and returned, as required by AWK semantics.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value associated with the key, or a blank value if not found
	 */
	@Override
	public Object get(Object key) {
		key = AssocArrayHelper.normalizeKey(key);
		Object result = super.get(key);
		if (result != null) {
			return result;
		}
		Long lKey = AssocArrayHelper.toLongKey(key);
		if (lKey != null) {
			result = super.get(lKey);
			if (result != null) {
				return result;
			}
			key = lKey;
		}
		result = AssocArrayHelper.BLANK;
		super.put(key, result);
		return result;
	}

	/**
	 * Associates the specified value with the specified key, normalizing the key
	 * to a {@code Long} when the key is a valid integer string.
	 *
	 * @param key the key
	 * @param value the value
	 * @return the previous value associated with the key, or {@code null}
	 */
	@Override
	public Object put(Object key, Object value) {
		key = AssocArrayHelper.normalizeKey(key);
		Long lKey = AssocArrayHelper.toLongKey(key);
		return super.put(lKey != null ? lKey : key, value);
	}

	/**
	 * Removes the mapping for the specified key, trying both the original and its
	 * {@code Long} equivalent.
	 *
	 * @param key the key whose mapping is to be removed
	 * @return the previous value associated with the key, or {@code null}
	 */
	@Override
	public Object remove(Object key) {
		key = AssocArrayHelper.normalizeKey(key);
		Object result = super.remove(key);
		if (result != null) {
			return result;
		}
		Long lKey = AssocArrayHelper.toLongKey(key);
		return lKey != null ? super.remove(lKey) : null;
	}

	/**
	 * Returns the specification version of the underlying {@link LinkedHashMap}
	 * class.
	 *
	 * @return the specification version string, or {@code null} if unavailable
	 */
	@Override
	public String getMapVersion() {
		return LinkedHashMap.class.getPackage().getSpecificationVersion();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws AwkRuntimeException always, to prevent accidental use of
	 *         {@link LinkedHashMap#toString()} in an AWK
	 *         evaluation context
	 */
	@Override
	public String toString() {
		throw new AwkRuntimeException("Cannot evaluate an unindexed array.");
	}
}
