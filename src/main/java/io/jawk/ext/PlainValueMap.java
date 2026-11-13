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

import io.jawk.jrt.StrNum;

/**
 * Live view of an AWK array handed to extension functions by default: reads
 * return plain Java values (a {@link StrNum} as its {@code String} text, a
 * nested array as another view), writes go straight to the AWK array.
 */
final class PlainValueMap extends AbstractMap<Object, Object> {

	private final Map<Object, Object> array;

	PlainValueMap(Map<Object, Object> arrayParam) {
		this.array = arrayParam;
	}

	/**
	 * Converts a runtime value into the plain Java value extensions receive.
	 *
	 * @param value runtime value
	 * @return the {@code String} text of a {@link StrNum}, a view of an array,
	 *         or the value itself
	 */
	@SuppressWarnings("unchecked")
	static Object plain(Object value) {
		if (value instanceof StrNum) {
			return value.toString();
		}
		if (value instanceof Map && !(value instanceof PlainValueMap)) {
			return new PlainValueMap((Map<Object, Object>) value);
		}
		return value;
	}

	@Override
	public int size() {
		return array.size();
	}

	@Override
	public boolean containsKey(Object key) {
		return array.containsKey(key);
	}

	@Override
	public Object get(Object key) {
		return plain(array.get(key));
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
	private static final class PlainEntry implements Map.Entry<Object, Object> {

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
