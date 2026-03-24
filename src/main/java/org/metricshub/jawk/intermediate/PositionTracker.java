package org.metricshub.jawk.intermediate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.ext.ExtensionFunction;
import java.util.regex.Pattern;

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

	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "PositionTracker must iterate over the shared tuple list")
	public PositionTracker(java.util.List<Tuple> queue) {
		this.queue = queue;
		this.tuple = queue.isEmpty() ? null : queue.get(idx);
	}

	public boolean isEOF() {
		return idx >= queue.size();
	}

	public void next() {
		++idx;
		tuple = tuple.getNext();
	}

	public void jump(Address address) {
		idx = address.index();
		tuple = queue.get(idx);
	}

	@Override
	public String toString() {
		return "[" + idx + "]-->" + tuple.toString();
	}

	public Opcode opcode() {
		return tuple.getOpcode();
	}

	/**
	 * Returns the long argument at the specified index without type dispatch.
	 *
	 * @param argIdx argument index
	 * @return the long value
	 */
	public long intArg(int argIdx) {
		return tuple.getInts()[argIdx];
	}

	/**
	 * Returns the boolean argument at the specified index without type dispatch.
	 *
	 * @param argIdx argument index
	 * @return the boolean value
	 */
	public boolean boolArg(int argIdx) {
		return tuple.getBools()[argIdx];
	}

	/**
	 * Returns the string argument at the specified index without type dispatch.
	 *
	 * @param argIdx argument index
	 * @return the string value
	 */
	public String stringArg(int argIdx) {
		return tuple.getStrings()[argIdx];
	}

	/**
	 * Returns the double argument at the specified index without type dispatch.
	 *
	 * @param argIdx argument index
	 * @return the double value
	 */
	public double doubleArg(int argIdx) {
		return tuple.getDoubles()[argIdx];
	}

	public Object arg(int argIdx) {
		Class<?> c = tuple.getTypes()[argIdx];
		if (c == Long.class) {
			return tuple.getInts()[argIdx];
		}
		if (c == Double.class) {
			return tuple.getDoubles()[argIdx];
		}
		if (c == String.class) {
			return tuple.getStrings()[argIdx];
		}
		if (c == Pattern.class) {
			return tuple.getPatterns()[argIdx];
		}
		if (c == Address.class) {
			return tuple.getAddress();
		}
		if (c == ExtensionFunction.class) {
			return tuple.getExtensionFunction();
		}
		throw new Error("Invalid arg type: " + c + ", arg_idx = " + argIdx + ", tuple = " + tuple);
	}

	public Pattern patternArg(int argIdx) {
		if (tuple.getTypes()[argIdx] != Pattern.class) {
			throw new Error("Tuple does not contain a Pattern at index " + argIdx + ": " + tuple);
		}
		return tuple.getPatterns()[argIdx];
	}

	public ExtensionFunction extensionFunctionArg() {
		if (tuple.getTypes()[0] != ExtensionFunction.class) {
			throw new Error("Tuple does not contain an extension function: " + tuple);
		}
		return tuple.getExtensionFunction();
	}

	public Address addressArg() {
		if (tuple.getAddress() == null) {
			tuple.setAddress(tuple.getAddressSupplier().get());
		}
		return tuple.getAddress();
	}

	public Class<?> classArg() {
		return tuple.getCls();
	}

	public int lineNumber() {
		return tuple.getLineno();
	}

	public int current() {
		return idx;
	}

	public void jump(int index) {
		this.idx = index;
		tuple = queue.get(this.idx);
	}
}
