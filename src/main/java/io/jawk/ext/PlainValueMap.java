package io.jawk.ext;

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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.jawk.jrt.AssocArray;
import io.jawk.jrt.StrNum;

/**
 * Live view of an AWK array handed to extension functions by default: reads
 * return plain Java values (a {@link StrNum} as its {@code String} text, a
 * nested array as another view), writes go straight to the AWK array.
 */
final class PlainValueMap extends AbstractMap<Object, Object> {

	private final AssocArray array;

	/**
	 * Views created for one extension call, shared by all its arguments and
	 * nested arrays, so the same AWK array always maps to the same view and
	 * aliases ({@code source == dest}) stay detectable.
	 */
	private final Map<AssocArray, PlainValueMap> views;

	private PlainValueMap(AssocArray arrayParam, Map<AssocArray, PlainValueMap> viewsParam) {
		this.array = arrayParam;
		this.views = viewsParam;
	}

	/**
	 * Converts a runtime value into the plain Java value extensions receive.
	 *
	 * @param value runtime value
	 * @param views views already created for the current extension call, keyed
	 *        by identity
	 * @return the {@code String} text of a {@link StrNum}, the view of an AWK
	 *         array, or the value itself
	 */
	static Object plain(Object value, Map<AssocArray, PlainValueMap> views) {
		if (value instanceof StrNum) {
			return value.toString();
		}
		if (value instanceof AssocArray) {
			return views.computeIfAbsent((AssocArray) value, array -> new PlainValueMap(array, views));
		}
		return value;
	}

	private Object plain(Object value) {
		return plain(value, views);
	}

	@Override
	public int size() {
		return array.size();
	}

	@Override
	public boolean containsKey(Object key) {
		return array.isIn(key);
	}

	/**
	 * Returns the plain value of an element, or {@code null} when the element
	 * does not exist. Unlike an AWK array reference, reading a missing element
	 * does not create it.
	 */
	@Override
	public Object get(Object key) {
		return array.isIn(key) ? plain(array.get(key)) : null;
	}

	@Override
	public Object put(Object key, Object value) {
		return plain(array.put(key, value));
	}

	@Override
	public Object remove(Object key) {
		return plain(array.remove(key));
	}

	@Override
	public void clear() {
		array.clear();
	}

	@Override
	public Set<Map.Entry<Object, Object>> entrySet() {
		return new AbstractSet<Map.Entry<Object, Object>>() {
			@Override
			public int size() {
				return array.size();
			}

			@Override
			public void clear() {
				array.clear();
			}

			@Override
			public Iterator<Map.Entry<Object, Object>> iterator() {
				Iterator<Map.Entry<Object, Object>> entries = array.entrySet().iterator();
				return new Iterator<Map.Entry<Object, Object>>() {
					@Override
					public boolean hasNext() {
						return entries.hasNext();
					}

					@Override
					public Map.Entry<Object, Object> next() {
						return new PlainEntry(entries.next());
					}

					@Override
					public void remove() {
						entries.remove();
					}
				};
			}
		};
	}

	/** Entry whose value reads plain and writes through to the AWK array. */
	private final class PlainEntry implements Map.Entry<Object, Object> {

		private final Map.Entry<Object, Object> entry;

		private PlainEntry(Map.Entry<Object, Object> entryParam) {
			this.entry = entryParam;
		}

		@Override
		public Object getKey() {
			return entry.getKey();
		}

		@Override
		public Object getValue() {
			return plain(entry.getValue());
		}

		@Override
		public Object setValue(Object value) {
			return plain(entry.setValue(value));
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> that = (Map.Entry<?, ?>) other;
			return Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
		}

		@Override
		public String toString() {
			return getKey() + "=" + getValue();
		}
	}
}
