package org.metricshub.jawk.intermediate;

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

	/**
	 * Creates a new unresolved address for the supplied label prefix.
	 * <p>
	 * Repeated requests for the same label receive monotonically numbered suffixes
	 * so tuple generation can distinguish placeholder jump targets until they are
	 * resolved to concrete queue indexes.
	 * </p>
	 *
	 * @param label The logical label name used to derive a unique address identity
	 * @return A new unresolved {@link Address} tracked by this manager
	 */
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

	/**
	 * Resolves a previously created address to its first concrete tuple index.
	 *
	 * @param address The unresolved address to bind
	 * @param index The tuple index that now owns the address
	 * @throws Error If the address is already resolved or belongs to another manager
	 */
	void resolveAddress(Address address, int index) {
		if (unresolvedAddresses.contains(address)) {
			unresolvedAddresses.remove(address);
			address.assignIndex(index);
			addressIndexes.put(index, address);
		} else {
			throw new Error(address.toString() + " is already resolved, or unresolved from another scope.");
		}
	}

	/**
	 * Returns the resolved address currently assigned to a tuple index.
	 *
	 * @param index The tuple index to inspect
	 * @return The resolved {@link Address} for that index, or {@code null} when no
	 *         address is bound there
	 */
	Address getAddress(int index) {
		return addressIndexes.get(index);
	}

	/**
	 * Retargets an already resolved address to a different tuple index.
	 * <p>
	 * This is used by tuple optimizations that collapse or reorder queue entries
	 * after initial address resolution.
	 * </p>
	 *
	 * @param address The resolved address to move
	 * @param index The new tuple index for the address
	 */
	void reassignAddress(Address address, int index) {
		int previousIndex = address.index();
		if (previousIndex >= 0) {
			Address current = addressIndexes.get(previousIndex);
			if (current == address && previousIndex != index) {
				addressIndexes.remove(previousIndex);
			}
		}
		addressIndexes.put(index, address);
		address.assignIndex(index);
	}

	/**
	 * Rebuilds the resolved-address index after queue compaction or reordering.
	 * <p>
	 * Each entry in {@code indexMapping} maps an old tuple index to its new index,
	 * or {@code -1} when that tuple was removed entirely.
	 * </p>
	 *
	 * @param indexMapping Old-to-new tuple index mapping produced by an optimizer
	 *        pass
	 */
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
