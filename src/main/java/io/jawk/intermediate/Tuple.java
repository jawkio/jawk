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

import java.io.Serializable;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import io.jawk.ext.ExtensionFunction;

/**
 * Represents a single opcode and its arguments within the tuple stream produced
 * by {@link AwkTuples}. While {@code AwkTuples} manages the list of tuples, this
 * class models one instruction and up to four typed operands.
 * <p>
 * Some tuples defer resolution of a function address until the tuple list is
 * finalized; such tuples hold a {@link Supplier} that provides the
 * {@link Address} when needed.
 *
 * @author Danny Daglas
 * @see AwkTuples
 */
class Tuple implements Serializable {

	private static final long serialVersionUID = 8105941219003992817L;
	private Opcode opcode;
	private long[] ints = new long[4];
	private boolean[] bools = new boolean[4];
	private double[] doubles = new double[4];
	private String[] strings = new String[4];
	private Pattern[] patterns = new Pattern[4];
	private Class<?>[] types = new Class[4];
	private Address address = null;
	private Class<?> cls = null;
	private transient Supplier<Address> addressSupplier = null;
	private int lineno = -1;
	private Tuple next = null;
	private ExtensionFunction extensionFunction;

	Tuple(Opcode opcode) {
		this.opcode = opcode;
	}

	Tuple(Opcode opcode, long i1) {
		this(opcode);
		ints[0] = i1;
		types[0] = Long.class;
	}

	Tuple(Opcode opcode, long i1, long i2) {
		this(opcode, i1);
		ints[1] = i2;
		types[1] = Long.class;
	}

	Tuple(Opcode opcode, long i1, boolean b2) {
		this(opcode, i1);
		bools[1] = b2;
		types[1] = Boolean.class;
	}

	Tuple(Opcode opcode, long i1, boolean b2, boolean b3) {
		this(opcode, i1, b2);
		bools[2] = b3;
		types[2] = Boolean.class;
	}

	Tuple(Opcode opcode, double d1) {
		this(opcode);
		doubles[0] = d1;
		types[0] = Double.class;
	}

	Tuple(Opcode opcode, String s1) {
		this(opcode);
		strings[0] = s1;
		types[0] = String.class;
	}

	Tuple(Opcode opcode, String s1, Pattern p2) {
		this(opcode, s1);
		patterns[1] = p2;
		types[1] = Pattern.class;
	}

	Tuple(Opcode opcode, boolean b1) {
		this(opcode);
		bools[0] = b1;
		types[0] = Boolean.class;
	}

	Tuple(Opcode opcode, String s1, long i2) {
		this(opcode, s1);
		ints[1] = i2;
		types[1] = Long.class;
	}

	Tuple(Opcode opcode, Address address) {
		this(opcode);
		this.address = address;
		types[0] = Address.class;
	}

	Tuple(Opcode opcode, String strarg, long intarg, boolean boolarg) {
		this(opcode, strarg, intarg);
		bools[2] = boolarg;
		types[2] = Boolean.class;
	}

	Tuple(Opcode opcode, ExtensionFunction function, long intarg, boolean boolarg) {
		this(opcode);
		this.extensionFunction = function;
		types[0] = ExtensionFunction.class;
		ints[1] = intarg;
		types[1] = Long.class;
		bools[2] = boolarg;
		types[2] = Boolean.class;
	}

	Tuple(Opcode opcode, Supplier<Address> addressSupplier, String s2, long i3, long i4) {
		this(opcode);
		this.addressSupplier = addressSupplier;
		strings[1] = s2;
		types[1] = String.class;
		ints[2] = i3;
		types[2] = Long.class;
		ints[3] = i4;
		types[3] = Long.class;
	}

	Tuple(Opcode opcode, Class<?> cls) {
		this(opcode);
		this.cls = cls;
		types[0] = Class.class;
	}

	Tuple(Opcode opcode, String s1, String s2) {
		this(opcode, s1);
		strings[1] = s2;
		types[1] = String.class;
	}

	boolean hasNext() {
		return next != null;
	}

	Tuple getNext() {
		return next;
	}

	void setNext(Tuple next) {
		this.next = next;
	}

	void setLineNumber(int lineNumber) {
		this.lineno = lineNumber;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(opcode.name());
		int idx = 0;
		while ((idx < types.length) && (types[idx] != null)) {
			sb.append(", ");
			Class<?> type = types[idx];
			if (type == Long.class) {
				sb.append(ints[idx]);
			} else if (type == Boolean.class) {
				sb.append(bools[idx]);
			} else if (type == Double.class) {
				sb.append(doubles[idx]);
			} else if (type == String.class) {
				sb.append('"').append(strings[idx]).append('"');
			} else if (type == Pattern.class) {
				// Display regex patterns in /.../ form for readability
				Pattern p = patterns[idx];
				sb
						.append('/')
						.append(p == null ? "" : p.pattern())
						.append('/');
			} else if (type == Address.class) {
				sb.append(address);
			} else if (type == ExtensionFunction.class) {
				sb.append(extensionFunction.getKeyword());
			} else if (type == Class.class) {
				sb.append(cls);
			} else {
				throw new Error("Unknown param type (" + idx + "): " + type);
			}
			++idx;
		}
		return sb.toString();
	}

	public void touch(java.util.List<Tuple> queue) {
		if (addressSupplier != null) {
			address = addressSupplier.get();
			types[0] = Address.class;
		}
		if (address != null) {
			if (address.index() == -1) {
				throw new Error("address " + address + " is unresolved");
			}
			if (address.index() >= queue.size()) {
				throw new Error("address " + address + " doesn't resolve to an actual list element");
			}
		}
	}

	Opcode getOpcode() {
		return opcode;
	}

	long[] getInts() {
		return ints;
	}

	boolean[] getBools() {
		return bools;
	}

	double[] getDoubles() {
		return doubles;
	}

	String[] getStrings() {
		return strings;
	}

	Pattern[] getPatterns() {
		return patterns;
	}

	Class<?>[] getTypes() {
		return types;
	}

	Address getAddress() {
		return address;
	}

	Class<?> getCls() {
		return cls;
	}

	Supplier<Address> getAddressSupplier() {
		return addressSupplier;
	}

	ExtensionFunction getExtensionFunction() {
		return extensionFunction;
	}

	void setAddress(Address address) {
		this.address = address;
	}

	int getLineno() {
		return lineno;
	}
}
