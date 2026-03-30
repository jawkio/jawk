package io.jawk.intermediate;

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

/**
 * A pointer to a tuple within the list of tuples.
 * Addresses are used for jumps, especially in reaction to
 * conditional checks (i.e., if false, jump to else block, etc.).
 * <p>
 * Addresses have the following properties:
 * <ul>
 * <li>A name (label).
 * <li>An index into the tuple queue.
 * </ul>
 * An address may not necessarily have an index assigned upon creation.
 * However, upon tuple traversal, all address indexes must
 * point to a valid tuple.
 * <p>
 * All addresses should have a meaningful label.
 *
 * @author Danny Daglas
 */
public class Address implements java.io.Serializable {

	private static final long serialVersionUID = 109610985341478678L;

	/** Human-readable label used when rendering or debugging this address. */
	private final String lbl;

	/** Tuple index currently bound to this address, or {@code -1} when unresolved. */
	private int idx = -1;

	/**
	 * Creates an unresolved address with the supplied label.
	 *
	 * @param lbl Label to associate with this jump target
	 */
	public Address(String lbl) {
		this.lbl = lbl;
	}

	/**
	 * The label of the address.
	 * It is particularly useful when dumping tuples to an output stream.
	 *
	 * @return The label of the tuple.
	 */
	public String label() {
		return lbl;
	}

	/**
	 * Set the tuple index of this address.
	 * This can be deferred anytime after creation of the address,
	 * but the index must be assigned prior to traversing the tuples.
	 *
	 * @param index The tuple location within the tuple list (queue)
	 *        for this address.
	 */
	public void assignIndex(int index) {
		this.idx = index;
	}

	/**
	 * <p>
	 * index.
	 * </p>
	 *
	 * @return The index into the tuple queue/array.
	 */
	public int index() {
		return idx;
	}

	@Override
	public String toString() {
		return label();
	}
}
