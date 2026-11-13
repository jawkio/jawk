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

import io.jawk.intermediate.UninitializedObject;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.StrNum;

/**
 * Live view of an AWK array handed to extension functions by default: reads
 * return plain Java values (a {@link StrNum} as its {@code String} text, a
 * nested array as another view), writes go straight to the AWK array.
 * <p>
 * The backing array is usually an {@link AssocArray}, but any {@link Map} the
 * runtime accepts as an array (such as a map returned by an extension) is
 * viewed the same way.
 * </p>
 */
final class PlainValueMap extends AbstractMap<Object, Object> {

	private final Map<Object, Object> array;

	/**
	 * Views created for one extension call, shared by all its arguments and
	 * nested arrays, so the same AWK array always maps to the same view and
	 * aliases ({@code source == dest}) stay detectable.
	 */
	private final Map<Map<Object, Object>, PlainValueMap> views;

	private PlainValueMap(Map<Object, Object> arrayParam, Map<Map<Object, Object>, PlainValueMap> viewsParam) {
		this.array = arrayParam;
		this.views = viewsParam;
	}

	/**
	 * Converts a runtime value into the plain Java value extensions receive.
	 *
	 * @param value runtime value
	 * @param views views already created for the current extension call, keyed
	 *        by identity
	 * @return the {@code String} text of a {@link StrNum}, {@code ""} for a
	 *         blank or untyped value, the view of an AWK array, or the value
	 *         itself
	 */
	@SuppressWarnings("unchecked")
	static Object plain(Object value, Map<Map<Object, Object>, PlainValueMap> views) {
		if (value instanceof StrNum || value instanceof UninitializedObject) {
			// an unassigned or untyped value reads as AWK's empty string
			return value.toString();
		}
		if (value instanceof Map && !(value instanceof PlainValueMap)) {
			return views.computeIfAbsent((Map<Object, Object>) value, map -> new PlainValueMap(map, views));
		}
		return value;
	}

	private Object plain(Object value) {
		return plain(value, views);
	}

	/**
	 * Returns the runtime value behind a view: the AWK array it shows, so a
	 * view stored in an array (as a value or a key) or returned by an extension
	 * stays an AWK array.
	 *
	 * @param value value written or returned by an extension
	 * @return the array behind a view, or the value itself
	 */
	static Object unwrap(Object value) {
		return value instanceof PlainValueMap ? ((PlainValueMap) value).array : value;
	}

	/**
	 * A view is an AWK array: like {@link AssocArray#toString()}, it has no
	 * string value, so converting it ({@code toAwkString()},
	 * {@code JRT.toDouble()}, {@code String.valueOf()}) fails instead of
	 * producing {@code {key=value}}.
	 *
	 * @throws AwkRuntimeException always
	 */
	@Override
	public String toString() {
		throw new AwkRuntimeException("Attempting to use an array in a scalar context.");
	}

	@Override
	public int size() {
		return array.size();
	}

	/**
	 * Returns whether the element exists. Keys of an {@link AssocArray} follow
	 * the AWK {@code in} operator, so {@code "1"} finds the element {@code 1}.
	 */
	@Override
	public boolean containsKey(Object key) {
		Object arrayKey = unwrap(key);
		return array instanceof AssocArray ? ((AssocArray) array).isIn(arrayKey) : array.containsKey(arrayKey);
	}

	/**
	 * Returns the plain value of an element, or {@code null} when the element
	 * does not exist. Unlike an AWK array reference, reading a missing element
	 * does not create it.
	 */
	@Override
	public Object get(Object key) {
		return containsKey(key) ? plain(array.get(unwrap(key))) : null;
	}

	/** Stores a {@link Boolean} as the boolean-typed number 1 or 0. */
	@Override
	public Object put(Object key, Object value) {
		Object stored = value instanceof Boolean ? new GawkBool((Boolean) value) : unwrap(value);
		return plain(array.put(unwrap(key), stored));
	}

	@Override
	public Object remove(Object key) {
		return plain(array.remove(unwrap(key)));
	}

	@Override
	public void clear() {
		array.clear();
	}

	/**
	 * Returns a live key set whose {@code contains} and {@code remove} match
	 * keys like {@link #containsKey(Object)} and {@link #remove(Object)}.
	 */
	@Override
	public Set<Object> keySet() {
		return new AbstractSet<Object>() {
			@Override
			public int size() {
				return array.size();
			}

			@Override
			public void clear() {
				array.clear();
			}

			@Override
			public boolean contains(Object key) {
				return containsKey(key);
			}

			@Override
			public boolean remove(Object key) {
				if (!containsKey(key)) {
					return false;
				}
				PlainValueMap.this.remove(key);
				return true;
			}

			@Override
			public Iterator<Object> iterator() {
				Iterator<Map.Entry<Object, Object>> entries = entrySet().iterator();
				return new Iterator<Object>() {
					@Override
					public boolean hasNext() {
						return entries.hasNext();
					}

					@Override
					public Object next() {
						return entries.next().getKey();
					}

					@Override
					public void remove() {
						entries.remove();
					}
				};
			}
		};
	}

	/**
	 * Returns a live entry set whose {@code contains} and {@code remove} match
	 * keys like {@link #containsKey(Object)} and compare plain values.
	 */
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
			public boolean contains(Object other) {
				if (!(other instanceof Map.Entry)) {
					return false;
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>) other;
				return containsKey(entry.getKey()) && Objects.equals(get(entry.getKey()), entry.getValue());
			}

			@Override
			public boolean remove(Object other) {
				if (!contains(other)) {
					return false;
				}
				PlainValueMap.this.remove(((Map.Entry<?, ?>) other).getKey());
				return true;
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
			// an array used as a key reads as its view, like an array value
			return plain(entry.getKey());
		}

		@Override
		public Object getValue() {
			return plain(entry.getValue());
		}

		/**
		 * Stores the value through the array's {@code put}, which applies the
		 * AWK rules (a {@code null} becomes the untyped marker). Replacing the
		 * value of an existing key does not disturb the iteration.
		 */
		@Override
		public Object setValue(Object value) {
			return put(entry.getKey(), value);
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
