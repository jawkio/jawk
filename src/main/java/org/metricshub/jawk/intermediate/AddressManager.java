package org.metricshub.jawk.intermediate;

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages creation and resolution of {@link Address} instances for
 * {@link AwkTuples}.
 */
class AddressManager implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Set<Address> unresolvedAddresses = new HashSet<Address>();
	private final Map<Integer, Address> addressIndexes = new HashMap<Integer, Address>();
	private final Map<String, Integer> addressLabelCounts = new HashMap<String, Integer>();

	Address createAddress(String label) {
		Integer count = addressLabelCounts.get(label);
		if (count == null) {
			count = 0;
		} else {
			count = count + 1;
		}
		addressLabelCounts.put(label, count);
		Address address = new Address(label + "_" + count);
		unresolvedAddresses.add(address);
		return address;
	}

	void resolveAddress(Address address, int index) {
		if (unresolvedAddresses.contains(address)) {
			unresolvedAddresses.remove(address);
			address.assignIndex(index);
			addressIndexes.put(index, address);
		} else {
			throw new Error(address.toString() + " is already resolved, or unresolved from another scope.");
		}
	}

	Address getAddress(int index) {
		return addressIndexes.get(index);
	}

	void remapIndexes(int[] indexMapping) {
		Map<Integer, Address> updated = new HashMap<Integer, Address>();
		for (Map.Entry<Integer, Address> entry : addressIndexes.entrySet()) {
			int oldIndex = entry.getKey().intValue();
			Address address = entry.getValue();
			if (oldIndex >= 0 && oldIndex < indexMapping.length) {
				int mappedIndex = indexMapping[oldIndex];
				if (mappedIndex >= 0) {
					address.assignIndex(mappedIndex);
					updated.put(mappedIndex, address);
				} else {
					address.assignIndex(-1);
				}
			} else {
				address.assignIndex(-1);
			}
		}
		addressIndexes.clear();
		addressIndexes.putAll(updated);
	}
}
