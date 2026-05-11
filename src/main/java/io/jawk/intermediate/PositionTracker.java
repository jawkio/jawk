package io.jawk.intermediate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
 * Marks a position within the tuple list (queue).
 *
 * @author Danny Daglas
 */
public class PositionTracker {

	private int idx = 0;
	private final java.util.List<Tuple> queue;
	private Tuple tuple;

	/**
	 * Creates a tracker positioned at the first tuple in the queue.
	 *
	 * @param queue Tuple stream to traverse
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "PositionTracker must iterate over the shared tuple list")
	public PositionTracker(java.util.List<Tuple> queue) {
		this.queue = queue;
		this.tuple = queue.isEmpty() ? null : queue.get(idx);
	}

	/**
	 * Indicates whether the tracker has moved past the last tuple.
	 *
	 * @return {@code true} when no current tuple remains
	 */
	public boolean isEOF() {
		return idx >= queue.size();
	}

	/**
	 * Advances to the next tuple in sequence.
	 */
	public void next() {
		++idx;
		tuple = tuple.getNext();
	}

	/**
	 * Jumps directly to the tuple identified by the supplied address.
	 *
	 * @param address Address to jump to
	 */
	public void jump(Address address) {
		idx = address.index();
		tuple = queue.get(idx);
	}

	@Override
	public String toString() {
		return "[" + idx + "]-->" + tuple.toString();
	}

	/**
	 * Returns the opcode of the current tuple.
	 *
	 * @return Current opcode
	 */
	public Opcode opcode() {
		return tuple.getOpcode();
	}

	/**
	 * Returns the source line number associated with the current tuple.
	 *
	 * @return Tuple source line number
	 */
	public int lineNumber() {
		return tuple.getLineNumber();
	}

	/**
	 * Returns the current tuple index.
	 *
	 * @return Current queue index
	 */
	public int currentIndex() {
		return idx;
	}

	/**
	 * Returns the current typed tuple.
	 *
	 * @return current tuple
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Tuple mutation is limited to package-internal stream construction and deferred address resolution")
	public Tuple current() {
		return tuple;
	}

	/**
	 * Jumps directly to the tuple at the supplied queue index.
	 *
	 * @param index Tuple index to jump to
	 */
	public void jump(int index) {
		this.idx = index;
		tuple = queue.get(this.idx);
	}
}
