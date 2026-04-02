package io.jawk;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;

/**
 * Test extension used by the unit tests to exercise the annotation-based
 * extension infrastructure.
 */
public class TestExtension extends AbstractExtension implements JawkExtension {

	private static final String EXTENSION_NAME = "TestExtension";

	/**
	 * Returns the logical name of the test extension.
	 */
	@Override
	public String getExtensionName() {
		return EXTENSION_NAME;
	}

	/**
	 * Concatenates the associative array values {@code count} times.
	 *
	 * @param count number of concatenations to perform
	 * @param array associative array providing the fragments to concatenate
	 * @return concatenated string
	 */
	@JawkFunction("myExtensionFunction")
	public String myExtensionFunction(Number count, @JawkAssocArray Map<Object, Object> array) {
		StringBuilder result = new StringBuilder();
		int iterations = count.intValue();
		List<Object> keys = new ArrayList<>(array.keySet());
		Collections.sort(keys, Comparator.comparing(String::valueOf));
		for (int i = 0; i < iterations; i++) {
			for (Object key : keys) {
				result.append(array.get(key));
			}
		}
		return result.toString();
	}

	/**
	 * Counts the number of keys present in the provided associative arrays.
	 *
	 * @param arrays associative arrays whose key counts should be aggregated
	 * @return total number of keys across all arrays
	 */
	@SafeVarargs
	@JawkFunction("varArgAssoc")
	public final int varArgAssoc(@JawkAssocArray Map<?, ?>... arrays) {
		int total = 0;
		for (Map<?, ?> array : arrays) {
			total += array.keySet().size();
		}
		return total;
	}
}
