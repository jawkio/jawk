package org.metricshub.jawk.intermediate;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.metricshub.jawk.util.AwkLogger;
import org.metricshub.jawk.util.LinkedListStackImpl;
import org.metricshub.jawk.util.MyStack;
import org.slf4j.Logger;

/**
 * <p>AwkTuples class.</p>
 *
 * @author Danny Daglas
 */
public class AwkTuples implements Serializable {

	private static final long serialVersionUID = 2L;

	/** Our logger */
	private static final Logger LOG = AwkLogger.getLogger(AwkTuples.class);

	/** Version Manager */
	private VersionManager version_manager = new VersionManager();

	private static final class AddressImpl implements Address, Serializable {

		/**
		 *
		 */
		private static final long serialVersionUID = 109610985341478678L;
		private String lbl;
		private int idx = -1;

		private AddressImpl(String lbl) {
			this.lbl = lbl;
		}

		@Override
		public String label() {
			return lbl;
		}

		@Override
		public String toString() {
			return label();
		}

		@Override
		public void assignIndex(int idx) {
			this.idx = idx;
		}

		@Override
		public int index() {
			assert idx >= 0 : toString();
			return idx;
		}
	}

	private class PositionImpl implements PositionForInterpretation, PositionForCompilation {
		// index within the queue

		private int idx = 0;
		private Tuple tuple = queue.isEmpty() ? null : queue.get(idx); // current tuple

		@Override
		public int index() {
			return idx;
		}

		@Override
		public boolean isEOF() {
			return idx >= queue.size();
		}

		@Override
		public void next() {
			assert tuple != null;
			++idx;
			tuple = tuple.getNext();
			assert queue.size() == idx || queue.get(idx) == tuple;
		}

		@Override
		public void jump(Address address) {
			tuple = queue.get(idx = address.index());
		}

		@Override
		public String toString() {
			return "[" + idx + "]-->" + tuple.toString();
		}

		@Override
		public int opcode() {
			return tuple.getOpcode();
		}

		@Override
		public long intArg(int arg_idx) {
			Class<?> c = tuple.getTypes()[arg_idx];
			if (c == Long.class) {
				return tuple.getInts()[arg_idx];
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public boolean boolArg(int arg_idx) {
			Class<?> c = tuple.getTypes()[arg_idx];
			if (c == Boolean.class) {
				return tuple.getBools()[arg_idx];
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public Object arg(int arg_idx) {
			Class<?> c = tuple.getTypes()[arg_idx];
			if (c == Long.class) {
				return tuple.getInts()[arg_idx];
			}
			if (c == Double.class) {
				return tuple.getDoubles()[arg_idx];
			}
			if (c == String.class) {
				return tuple.getStrings()[arg_idx];
			}
			if (c == Address.class) {
				assert arg_idx == 0;
				return tuple.getAddress();
			}
			throw new Error("Invalid arg type: " + c + ", arg_idx = " + arg_idx + ", tuple = " + tuple);
		}

		@Override
		public Address addressArg() {
			assert tuple.getAddress() != null || tuple.getHasFuncAddr() != null : "tuple.address = " + tuple.getAddress() + ", tuple.has_func_addr = " + tuple.getHasFuncAddr();
			if (tuple.getAddress() == null) {
				tuple.setAddress(tuple.getHasFuncAddr().getFunctionAddress());
			}
			return tuple.getAddress();
		}

		@Override
		public Class<?> classArg() {
			//Tuple tuple = queue.get(idx);
			assert tuple.getCls() != null;
			return tuple.getCls();
		}

		@Override
		public int lineNumber() {
			assert tuple.getLineno() != -1 : "The line number should have been set by queue.add(), but was not.";
			return tuple.getLineno();
		}

		@Override
		public int current() {
			return idx;
		}

		@Override
		public void jump(int idx) {
			tuple = queue.get(this.idx = idx);
		}
	}

	// made public to access static members of AwkTuples via Java Reflection
	private static final class Tuple implements Serializable {

		/**
		 *
		 */
		private static final long serialVersionUID = 8105941219003992817L;
		private int opcode;
		private long[] ints = new long[4];
		private boolean[] bools = new boolean[4];
		private double[] doubles = new double[4];
		private String[] strings = new String[4];
		private Class<?>[] types = new Class[4];
		private Address address = null;
		private Class<?> cls = null;
		private transient HasFunctionAddress hasFuncAddr = null;
		// to avoid polluting the constructors,
		// setLineNumber(int) populates this field
		// (called by an anonymous inner subclass of ArrayList,
		// assigned to queue - see above)
		private int lineno = -1;
		private Tuple next = null;

		private Tuple(int opcode) {
			this.opcode = opcode;
		}

		private Tuple(int opcode, long i1) {
			this(opcode);
			ints[0] = i1;
			types[0] = Long.class;
		}

		private Tuple(int opcode, long i1, long i2) {
			this(opcode, i1);
			ints[1] = i2;
			types[1] = Long.class;
		}

		private Tuple(int opcode, long i1, boolean b2) {
			this(opcode, i1);
			bools[1] = b2;
			types[1] = Boolean.class;
		}

		private Tuple(int opcode, long i1, boolean b2, boolean b3) {
			this(opcode, i1, b2);
			bools[2] = b3;
			types[2] = Boolean.class;
		}

		private Tuple(int opcode, double d1) {
			this(opcode);
			doubles[0] = d1;
			types[0] = Double.class;
		}

		private Tuple(int opcode, String s1) {
			this(opcode);
			strings[0] = s1;
			types[0] = String.class;
		}

		private Tuple(int opcode, boolean b1) {
			this(opcode);
			bools[0] = b1;
			types[0] = Boolean.class;
		}

		private Tuple(int opcode, String s1, long i2) {
			this(opcode, s1);
			ints[1] = i2;
			types[1] = Long.class;
		}

		private Tuple(int opcode, Address address) {
			this(opcode);
			this.address = address;
			types[0] = Address.class;
		}

		private Tuple(int opcode, String strarg, long intarg, boolean boolarg) {
			this(opcode, strarg, intarg);
			bools[2] = boolarg;
			types[2] = Boolean.class;
		}

		private Tuple(int opcode, HasFunctionAddress has_func_addr, String s2, long i3, long i4) {
			this(opcode);
			this.hasFuncAddr = has_func_addr;
			strings[1] = s2;
			types[1] = String.class;
			ints[2] = i3;
			types[2] = Long.class;
			ints[3] = i4;
			types[3] = Long.class;
		}

		private Tuple(int opcode, Class<?> cls) {
			this(opcode);
			this.cls = cls;
			types[0] = Class.class;
		}

		private Tuple(int opcode, String s1, String s2) {
			this(opcode, s1);
			strings[1] = s2;
			types[1] = String.class;
		}

		private boolean hasNext() {
			return (next != null);
		}

		private Tuple getNext() {
			return next;
		}

		private void setNext(Tuple next) {
			this.next = next;
		}

		private void setLineNumber(int lineno) {
			assert this.lineno == -1 : "The line number was already set to " + this.lineno + ". Later lineno = " + lineno + ".";
			this.lineno = lineno;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(toOpcodeString(opcode));
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
				} else if (type == Address.class) {
					assert (idx == 0);
					sb.append(address);
				} else if (type == Class.class) {
					assert (idx == 0);
					sb.append(cls);
				} else if (type == HasFunctionAddress.class) {
					assert (idx == 0);
					sb.append(hasFuncAddr);
				} else {
					throw new Error("Unknown param type (" + idx + "): " + type);
				}
				++idx;
			}
			return sb.toString();
		}

		/**
		 * Update this tuple to populate the address argument value if necessary;
		 * and, check if address points to a proper element in the tuple queue.
		 * <p>
		 * The address will be updated only if there exists a HasFunctionAddress
		 * argument for this tuple.
		 *
		 * <p>
		 * This is executed after the tuples are constructed so that function address
		 * references can be resolved. Otherwise, forward declared functions will
		 * not be resolved in the Tuple list.
		 *
		 */
		public void touch(java.util.List<Tuple> queue) {
			assert lineno != -1 : "The line number should have been set by queue.add(), but was not.";
			if (hasFuncAddr != null) {
				address = hasFuncAddr.getFunctionAddress();
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

		private int getOpcode() {
			return opcode;
		}

		private long[] getInts() {
			return ints;
		}

		private boolean[] getBools() {
			return bools;
		}

		private double[] getDoubles() {
			return doubles;
		}

		private String[] getStrings() {
			return strings;
		}

		private Class<?>[] getTypes() {
			return types;
		}

		private void setAddress(Address address) {
			this.address = address;
		}

		private Address getAddress() {
			return address;
		}

		private int getLineno() {
			return lineno;
		}

		@SuppressWarnings("unused")
		private void setOpcode(int opcode) {
			this.opcode = opcode;
		}

		private Class<?> getCls() {
			return cls;
		}

		private HasFunctionAddress getHasFuncAddr() {
			return hasFuncAddr;
		}
	}

	// made public to be accessable via Java Reflection
	// (see toOpcodeString() method below)

	/**
	 * Pops an item off the operand stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _POP_ = 257;	// x -> 0
	/**
	 * Pushes an item onto the operand stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _PUSH_ = 258;	// 0 -> x
	/**
	 * Pops and evaluates the top-of-stack; if
	 * false, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 *
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _IFFALSE_ = 259;	// x -> 0
	/**
	 * Converts the top-of-stack to a number.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x (as a number)
	 *
	 */
	public static final int _TO_NUMBER_ = 260;	// x1 -> x2
	/**
	 * Pops and evaluates the top-of-stack; if
	 * true, it jumps to a specified address.
	 * <p>
	 * Argument: address
	 *
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _IFTRUE_ = 261;	// x -> 0
	/**
	 * Jumps to a specified address. The operand stack contents
	 * are unaffected.
	 */
	public static final int _GOTO_ = 262;	// 0 -> 0
	/**
	 * A no-operation. The operand stack contents are
	 * unaffected.
	 */
	public static final int _NOP_ = 263;	// 0 -> 0
	/**
	 * Prints N number of items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINT_ = 264;	// x1, x2, ... xn -> 0
	/**
	 * Prints N number of items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINT_TO_FILE_ = 265;	// x1, x2, ... xn -> 0
	/**
	 * Prints N number of items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINT_TO_PIPE_ = 266;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINTF_ = 267;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a specified file. The file is passed in on the stack.
	 * The number of items are passed in as a tuple argument,
	 * as well as whether to overwrite the file or not (append mode).
	 * <p>
	 * Argument 1: # of items (N)<br/>
	 * Argument 2: true = append, false = overwrite
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN filename ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINTF_TO_FILE_ = 268;	// x1, x2, ... xn -> 0
	/**
	 * Performs a formatted print of N items that are on the operand stack to
	 * a process executing a specified command (via a pipe).
	 * The command string is passed in on the stack.
	 * The number of items are passed in as a tuple argument.
	 * <p>
	 * Argument: # of items (N)
	 *
	 * <p>
	 * Stack before: x1 x2 x3 .. xN command-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _PRINTF_TO_PIPE_ = 269;	// x1, x2, ... xn -> 0
	/** Constant <code>_SPRINTF_=270</code> */
	public static final int _SPRINTF_ = 270;	// x1, x2, ... xn -> 0
	/**
	 * Depending on the argument, pop and evaluate the string length of the top-of-stack
	 * or evaluate the string length of $0; in either case, push the result onto
	 * the stack.
	 * <p>
	 * The input field length evaluation mode is provided to support backward
	 * compatibility with the deprecated usage of length (i.e., no arguments).
	 *
	 * <p>
	 * Argument: 0 to use $0, use top-of-stack otherwise
	 *
	 * <p>
	 * If argument is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: length-of-$0 ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: length-of-x ...
	 * </blockquote>
	 *
	 */
	public static final int _LENGTH_ = 271;	// 0 -> x or x1 -> x2
	/**
	 * Pop and concatenate two strings from the top-of-stack; push the result onto
	 * the stack.
	 * <p>
	 * Stack before: x y ...<br/>
	 * Stack after: x-concatenated-with-y ...
	 *
	 */
	public static final int _CONCAT_ = 272;	// x2, x1 -> x1x2
	/**
	 * Assigns the top-of-stack to a variable. The contents of the stack
	 * are unaffected.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _ASSIGN_ = 273;	// x -> 0
	/**
	 * Assigns an item to an array element. The item remains on the stack.
	 * <p>
	 * Argument 1: offset of the particular associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: index-into-array item ...<br/>
	 * Stack after: item ...
	 *
	 */
	public static final int _ASSIGN_ARRAY_ = 274;	// x2, x1 -> 0
	/**
	 * Assigns the top-of-stack to $0. The contents of the stack are unaffected.
	 * Upon assignment, individual field variables are recalculated.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _ASSIGN_AS_INPUT_ = 275;	// x -> 0
	/**
	 * Assigns an item as a particular input field; the field number can be 0.
	 * Upon assignment, associating input fields are affected. For example, if
	 * the following assignment were made:
	 * <blockquote>
	 * <pre>
	 * $3 = "hi"
	 * </pre>
	 * </blockquote>
	 * $0 would be recalculated. Likewise, if the following assignment were made:
	 * <blockquote>
	 * <pre>
	 * $0 = "hello there"
	 * </pre>
	 * </blockquote>
	 * $1, $2, ... would be recalculated.
	 * <p>
	 * Stack before: field-num x ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _ASSIGN_AS_INPUT_FIELD_ = 276;	// x, y -> x
	/**
	 * Obtains an item from the variable manager and push it onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _DEREFERENCE_ = 277;	// 0 -> x
	/**
	 * Increase the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x+n ...
	 *
	 */
	public static final int _PLUS_EQ_ = 278;	// x -> x
	/**
	 * Decreases the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x-n ...
	 *
	 */
	public static final int _MINUS_EQ_ = 279;	// x -> x
	/**
	 * Multiplies the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x*n ...
	 *
	 */
	public static final int _MULT_EQ_ = 280;	// x -> x
	/**
	 * Divides the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x/n ...
	 *
	 */
	public static final int _DIV_EQ_ = 281;	// x -> x
	/**
	 * Takes the modules of the contents of the variable by an adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x%n ...
	 *
	 */
	public static final int _MOD_EQ_ = 282;	// x -> x
	/**
	 * Raises the contents of the variable to the power of the adjustment value;
	 * assigns the result to the variable and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: n ...<br/>
	 * Stack after: x^n ...
	 *
	 */
	public static final int _POW_EQ_ = 283;	// x -> x
	/**
	 * Increase the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x+n ...
	 *
	 */
	public static final int _PLUS_EQ_ARRAY_ = 284;	// x -> x
	/**
	 * Decreases the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x-n ...
	 *
	 */
	public static final int _MINUS_EQ_ARRAY_ = 285;	// x -> x
	/**
	 * Multiplies the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x*n ...
	 *
	 */
	public static final int _MULT_EQ_ARRAY_ = 286;	// x -> x
	/**
	 * Divides the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x/n ...
	 *
	 */
	public static final int _DIV_EQ_ARRAY_ = 287;	// x -> x
	/**
	 * Takes the modulus of the contents of an indexed array by an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x%n ...
	 *
	 */
	public static final int _MOD_EQ_ARRAY_ = 288;	// x -> x
	/**
	 * Raises the contents of an indexed array to the power of an adjustment value;
	 * assigns the result to the array and pushes the result onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx n ...<br/>
	 * Stack after: x^n ...
	 *
	 */
	public static final int _POW_EQ_ARRAY_ = 289;	// x -> x
	/**
	 * Increases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x+n ...
	 *
	 */
	public static final int _PLUS_EQ_INPUT_FIELD_ = 290;	// x1,x2 -> x
	/**
	 * Decreases the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x-n ...
	 *
	 */
	public static final int _MINUS_EQ_INPUT_FIELD_ = 291;	// x1,x2 -> x
	/**
	 * Multiplies the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x*n ...
	 *
	 */
	public static final int _MULT_EQ_INPUT_FIELD_ = 292;	// x1,x2 -> x
	/**
	 * Divides the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x/n ...
	 *
	 */
	public static final int _DIV_EQ_INPUT_FIELD_ = 293;	// x1,x2 -> x
	/**
	 * Takes the modulus of the contents of an input field by an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x%n ...
	 *
	 */
	public static final int _MOD_EQ_INPUT_FIELD_ = 294;	// x1,x2 -> x
	/**
	 * Raises the contents of an input field to the power of an adjustment value;
	 * assigns the result to the input field and pushes the result onto the stack.
	 * <p>
	 * Stack before: input-field_number n ...<br/>
	 * Stack after: x^n ...
	 *
	 */
	public static final int _POW_EQ_INPUT_FIELD_ = 295;	// x1,x2 -> x

	/**
	 * Seeds the random number generator. If there are no arguments, the current
	 * time (as a long value) is used as the seed. Otherwise, the top-of-stack is
	 * popped and used as the seed value.
	 * <p>
	 * Argument: # of arguments
	 *
	 * <p>
	 * If # of arguments is 0:
	 * <blockquote>
	 * Stack before: ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: x ...<br/>
	 * Stack after: old-seed ...
	 * </blockquote>
	 *
	 */
	public static final int _SRAND_ = 296;	// x2, x1 -> x1, x2
	/**
	 * Obtains the next random number from the random number generator
	 * and push it onto the stack.
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: random-number ...
	 *
	 */
	public static final int _RAND_ = 297;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, removes its fractional part,
	 * if any, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: (int)x ...
	 *
	 */
	public static final int _INTFUNC_ = 298;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, takes its square root,
	 * and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sqrt(x) ...
	 *
	 */
	public static final int _SQRT_ = 299;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.log method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: log(x) ...
	 *
	 */
	public static final int _LOG_ = 300;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.exp method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: exp(x) ...
	 *
	 */
	public static final int _EXP_ = 301;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.sin method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: sin(x) ...
	 *
	 */
	public static final int _SIN_ = 302;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the top-of-stack, calls the java.lang.Math.cos method
	 * with the top-of-stack as the argument, and places the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: cos(x) ...
	 *
	 */
	public static final int _COS_ = 303;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that pops the first two items off the stack,
	 * calls the java.lang.Math.atan2 method
	 * with these as arguments, and places the result onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: atan2(x1,x2) ...
	 *
	 */
	public static final int _ATAN2_ = 304;	// x2, x1 -> x1, x2
	/**
	 * Built-in function that searches a string as input to a regular expression,
	 * the location of the match is pushed onto the stack.
	 * The RSTART and RLENGTH variables are set as a side effect.
	 * If a match is found, RSTART and function return value are set
	 * to the location of the match and RLENGTH is set to the length
	 * of the substring matched against the regular expression.
	 * If no match is found, RSTART (and return value) is set to
	 * 0 and RLENGTH is set to -1.
	 * <p>
	 * Stack before: string regexp ...<br/>
	 * Stack after: RSTART ...
	 *
	 */
	public static final int _MATCH_ = 305;	// x1, x2 -> x
	/**
	 * Built-in function that locates a substring within a source string
	 * and pushes the location onto the stack. If the substring is
	 * not found, 0 is pushed onto the stack.
	 * <p>
	 * Stack before: string substring ...<br/>
	 * Stack after: location-index ...
	 *
	 */
	public static final int _INDEX_ = 306;	// x1, x2 -> x
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in $0 and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 *
	 * <p>
	 * Stack before: regexp replacement-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _SUB_FOR_DOLLAR_0_ = 307;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a field reference and replaces it with another.
	 * <p>
	 * Argument: true if global sub, false otherwise.
	 *
	 * <p>
	 * Stack before: field-num regexp replacement-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _SUB_FOR_DOLLAR_REFERENCE_ = 308;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular variable and replaces it with another.
	 * <p>
	 * Argument 1: variable offset in variable manager<br/>
	 * Argument 2: is global variable<br/>
	 * Argument 3: is global sub
	 *
	 * <p>
	 * Stack before: regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _SUB_FOR_VARIABLE_ = 309;	// x -> 0
	/**
	 * Built-in function that substitutes an occurrence (or all occurrences)
	 * of a string in a particular array cell and replaces it with another.
	 * <p>
	 * Argument 1: array map offset in variable manager<br/>
	 * Argument 2: is global array map<br/>
	 * Argument 3: is global sub
	 *
	 * <p>
	 * Stack before: array-index regexp replacement-string orig-string ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _SUB_FOR_ARRAY_REFERENCE_ = 310;	// x -> 0
	/**
	 * Built-in function to split a string by a regexp and put the
	 * components into an array.
	 * <p>
	 * Argument: # of arguments (parameters on stack)
	 *
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string array ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string array regexp ...<br/>
	 * Stack after: n ...
	 * </blockquote>
	 *
	 */
	public static final int _SPLIT_ = 311;	// x1 -> x2
	/**
	 * Built-in function that pushes a substring of the top-of-stack
	 * onto the stack.
	 * The tuple argument indicates whether to limit the substring
	 * to a particular end position, or to take the substring
	 * up to the end-of-string.
	 * <p>
	 * Argument: # of arguments
	 *
	 * <p>
	 * If # of arguments is 2:
	 * <blockquote>
	 * Stack before: string start-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 * else
	 * <blockquote>
	 * Stack before: string start-pos end-pos ...<br/>
	 * Stack after: substring ...
	 * </blockquote>
	 *
	 */
	public static final int _SUBSTR_ = 312;	// x1 -> x2
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to lower case and pushes the result onto the stack.
	 * <p>
	 * Stack before: STRING-ARGUMENT ...<br/>
	 * Stack after: string-argument ...
	 *
	 */
	public static final int _TOLOWER_ = 313;	// x1 -> x2
	/**
	 * Built-in function that converts all the letters in the top-of-stack
	 * to upper case and pushes the result onto the stack.
	 * <p>
	 * Stack before: string-argument ...<br/>
	 * Stack after: STRING-ARGUMENT ...
	 *
	 */
	public static final int _TOUPPER_ = 314;	// x1 -> x2
	/**
	 * Built-in function that executes the top-of-stack as a system command
	 * and pushes the return code onto the stack.
	 * <p>
	 * Stack before: cmd ...<br/>
	 * Stack after: return-code ...
	 *
	 */
	public static final int _SYSTEM_ = 315;	// x1 -> x2

	/**
	 * Swaps the top two elements of the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x2 x1 ...
	 *
	 */
	public static final int _SWAP_ = 316;	// x2, x1 -> x1, x2

	/**
	 * Numerically adds the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1+x2 ...
	 *
	 */
	public static final int _ADD_ = 317;	// x2, x1 -> x1+x2
	/**
	 * Numerically subtracts the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1-x2 ...
	 *
	 */
	public static final int _SUBTRACT_ = 318;	// x2, x1 -> x1-x2
	/**
	 * Numerically multiplies the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1*x2 ...
	 *
	 */
	public static final int _MULTIPLY_ = 319;	// x2, x1 -> x1*x2
	/**
	 * Numerically divides the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1/x2 ...
	 *
	 */
	public static final int _DIVIDE_ = 320;	// x2, x1 -> x1/x2
	/**
	 * Numerically takes the modulus of the top two elements of the stack with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1%x2 ...
	 *
	 */
	public static final int _MOD_ = 321;	// x2, x1 -> x1/x2
	/**
	 * Numerically raises the top element to the power of the next element with the result
	 * pushed onto the stack.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1^x2 ...
	 *
	 */
	public static final int _POW_ = 322;	// x2, x1 -> x1/x2

	/**
	 * Increases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x+1 ...
	 *
	 */
	public static final int _INC_ = 323;	// 0 -> x
	/**
	 * Decreases the variable reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x-1 ...
	 *
	 */
	public static final int _DEC_ = 324;	// 0 -> x
	/**
	 * Increases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x+1 ...
	 *
	 */
	public static final int _INC_ARRAY_REF_ = 325;	// x -> x
	/**
	 * Decreases the array element reference by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the associative array into the variable manager<br/>
	 * Argument 2: whether the associative array is global or local
	 *
	 * <p>
	 * Stack before: array-idx ...<br/>
	 * Stack after: x-1 ...
	 *
	 */
	public static final int _DEC_ARRAY_REF_ = 326;	// x -> x
	/**
	 * Increases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x+1
	 *
	 */
	public static final int _INC_DOLLAR_REF_ = 327;	// x -> x
	/**
	 * Decreases the input field variable by one; pushes the result
	 * onto the stack.
	 * <p>
	 * Stack before: field-idx ...<br/>
	 * Stack after: x-1
	 *
	 */
	public static final int _DEC_DOLLAR_REF_ = 328;	// x -> x

	/**
	 * Duplicates the top-of-stack on the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x x ...
	 *
	 */
	public static final int _DUP_ = 329;	// x -> x, x
	/**
	 * Evaluates the logical NOT of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: !x ...
	 *
	 */
	public static final int _NOT_ = 330;	// x -> !x
	/**
	 * Evaluates the numerical NEGATION of the top stack element;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: -x ...
	 *
	 */
	public static final int _NEGATE_ = 331;	// x -> -x

	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if equal, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1==x2
	 *
	 */
	public static final int _CMP_EQ_ = 332;	// x2, x1 -> x1 == x2
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &lt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&lt;x2
	 *
	 */
	public static final int _CMP_LT_ = 333;	// x2, x1 -> x1 < x2
	/**
	 * Compares the top two stack elements; pushes 1 onto the stack if x1 &gt; x2, 0 if not equal.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: x1&gt;x2
	 *
	 */
	public static final int _CMP_GT_ = 334;	// x2, x1 -> x1 < x2
	/**
	 * Applies a regular expression to the top stack element; pushes 1 if it matches,
	 * 0 if it does not match.
	 * <p>
	 * Stack before: x1 x2 ...<br/>
	 * Stack after: (x1 ~ /x2/) ...
	 *
	 */
	public static final int _MATCHES_ = 335;	// x2, x1 -> x1 ~ x2

	/**
	 * <strong>Extension:</strong> Pauses the execution thread by N number of seconds.
	 * <p>
	 * Stack before: N ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _SLEEP_ = 336;	// x -> 0
	/** Constant <code>_DUMP_=337</code> */
	public static final int _DUMP_ = 337;	// x -> 0

	/** Constant <code>_DEREF_ARRAY_=338</code> */
	public static final int _DEREF_ARRAY_ = 338;	// x -> x

	// for (x in y) {keyset} support
	/**
	 * Retrieves and pushes a Set of keys from an associative array onto the stack.
	 * The Set is tagged with a KeyList interface.
	 * <p>
	 * Stack before: associative-array ...<br/>
	 * Stack after: key-list-set ...
	 *
	 */
	public static final int _KEYLIST_ = 339;	// 0 -> {keylist}
	/**
	 * Tests whether the KeyList (set) is empty; jumps to the argument
	 * address if empty, steps to the next instruction if not.
	 * <p>
	 * Argument: jump-address-if-empty
	 *
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: ...
	 *
	 */
	public static final int _IS_EMPTY_KEYLIST_ = 340;	// {keylist} -> 0
	/**
	 * Removes an item from the KeyList (set) and pushes it onto the operand stack.
	 * <p>
	 * Stack before: key-list ...<br/>
	 * Stack after: 1st-item ...
	 *
	 */
	public static final int _GET_FIRST_AND_REMOVE_FROM_KEYLIST_ = 341;	// {keylist} -> x

	// assertions
	/**
	 * Checks whether the top-of-stack is of a particular class type;
	 * if not, an AwkRuntimeException is thrown.
	 * The stack remains unchanged upon a successful check.
	 * <p>
	 * Argument: class-type (i.e., KeyList.class)
	 *
	 * <p>
	 * Stack before: obj ...<br/>
	 * Stack after: obj ...
	 *
	 */
	public static final int _CHECK_CLASS_ = 342;	// {class} -> 0

	// input
	//* Obtain an input string from stdin; push the result onto the stack.
	/**
	 * Push an input field onto the stack.
	 * <p>
	 * Stack before: field-id ...<br/>
	 * Stack after: x ...
	 *
	 */
	public static final int _GET_INPUT_FIELD_ = 343;	// 0 -> x
	/**
	 * Consume next line of input; assigning $0 and recalculating $1, $2, etc.
	 * The input can come from the following sources:
	 * <ul>
	 * <li>stdin
	 * <li>filename arguments
	 * </ul>
	 * The operand stack is unaffected.
	 */
	public static final int _CONSUME_INPUT_ = 344;	// 0 -> 0
	/**
	 * Obtains input from stdin/filename-args and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: input-string return-code ...
	 *
	 */
	public static final int _GETLINE_INPUT_ = 345;	// 0 -> x
	/**
	 * Obtains input from a file and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the file is opened and the handle
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 *
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 *
	 * <p>
	 * Stack before: filename ...<br/>
	 * Stack after: input-string return-code ...
	 *
	 */
	public static final int _USE_AS_FILE_INPUT_ = 346;	// x1 -> x2
	/**
	 * Obtains input from a command (process) and pushes
	 * input line and status code onto the stack.
	 * The input is partitioned into records based on the RS variable
	 * assignment as a regular expression.
	 * <p>
	 * Upon initial execution, the a process is spawned to execute
	 * the specified command and the process reference
	 * is maintained until it is explicitly closed, or until
	 * the VM exits. Subsequent calls will obtain subsequent
	 * lines (records) of input until no more records are available.
	 *
	 * <p>
	 * If there is input available, the input string and a return code
	 * of 1 is pushed. If EOF is reached, a blank (null) string ("")
	 * is pushed along with a 0 return code. Upon an IO error,
	 * a blank string and a -1 is pushed onto the operand stack.
	 *
	 * <p>
	 * Stack before: command-line ...<br/>
	 * Stack after: input-string return-code ...
	 *
	 */
	public static final int _USE_AS_COMMAND_INPUT_ = 347;	// x1 -> x2

	// variable housekeeping
	/**
	 * Assign the NF variable offset. This is important for the
	 * AVM to set the variables as new input lines are processed.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _NF_OFFSET_ = 348;	// 0 -> 0
	/**
	 * Assign the NR variable offset. This is important for the
	 * AVM to increase the record number as new input lines received.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _NR_OFFSET_ = 349;	// 0 -> 0
	/**
	 * Assign the FNR variable offset. This is important for the
	 * AVM to increase the "file" record number as new input lines are received.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _FNR_OFFSET_ = 350;	// 0 -> 0
	/**
	 * Assign the FS variable offset. This is important for the
	 * AVM to know how to split fields upon incoming records of input.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _FS_OFFSET_ = 351;	// 0 -> 0
	/**
	 * Assign the RS variable offset. This is important for the
	 * AVM to know how to create records from the stream(s) of input.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _RS_OFFSET_ = 352;	// 0 -> 0
	/**
	 * Assign the OFS variable offset. This is important for the
	 * AVM to use when outputting expressions via PRINT.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _OFS_OFFSET_ = 353;	// 0 -> 0
	/**
	 * Assign the RSTART variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _RSTART_OFFSET_ = 354;	// 0 -> 0
	/**
	 * Assign the RLENGTH variable offset. The AVM sets this variable while
	 * executing the match() builtin function.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _RLENGTH_OFFSET_ = 355;	// 0 -> 0
	/**
	 * Assign the FILENAME variable offset. The AVM sets this variable while
	 * processing files from the command-line for input.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _FILENAME_OFFSET_ = 356;	// 0 -> 0
	/**
	 * Assign the SUBSEP variable offset. The AVM uses this variable while
	 * building an index of a multi-dimensional array.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _SUBSEP_OFFSET_ = 357;	// 0 -> 0
	/**
	 * Assign the CONVFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _CONVFMT_OFFSET_ = 358;	// 0 -> 0
	/**
	 * Assign the OFMT variable offset. The AVM uses this variable while
	 * converting numbers to strings for printing.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _OFMT_OFFSET_ = 359;	// 0 -> 0
	/**
	 * Assign the ENVIRON variable offset. The AVM provides environment
	 * variables through this array.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _ENVIRON_OFFSET_ = 360;	// 0 -> 0
	/**
	 * Assign the ARGC variable offset. The AVM provides the number of
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _ARGC_OFFSET_ = 361;	// 0 -> 0
	/**
	 * Assign the ARGV variable offset. The AVM provides command-line
	 * arguments via this variable.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _ARGV_OFFSET_ = 362;	// 0 -> 0

	/**
	 * Apply the RS variable by notifying the partitioning reader that
	 * there is a new regular expression to use when partitioning input
	 * records.
	 * <p>
	 * The stack remains unaffected.
	 *
	 */
	public static final int _APPLY_RS_ = 363;	// 0 -> 0

	/**
	 * Call a user function.
	 * <p>
	 * Stack before: x1, x2, ..., xn <br>
	 * Stack after: f(x1, x2, ..., xn)
	 */
	public static final int _CALL_FUNCTION_ = 364;	// x1,x2,...,xn -> x

	/**
	 * Define a user function.
	 * <p>
	 * Stack remains unchanged
	 */
	public static final int _FUNCTION_ = 365;	// 0 -> 0

	/**
	 * Sets the return value of a user function.
	 * <p>
	 * Stack before: x <br>
	 * Stack after: ...
	 */
	public static final int _SET_RETURN_RESULT_ = 366;	// x -> 0

	/**
	 * Get the return value of the user function that was called
	 * <p>
	 * Stack before: ... <br>
	 * Stack after: x
	 */
	public static final int _RETURN_FROM_FUNCTION_ = 367;	// 0 -> x

	/**
	 * Internal: sets the number of global variables
	 */
	public static final int _SET_NUM_GLOBALS_ = 368;	// 0 -> 0

	/**
	 * Close the specified file.
	 * <p>
	 * Stack before: file name <br>
	 * Stack after: result of the close operation
	 */
	public static final int _CLOSE_ = 369;	// x -> x

	/**
	 * Convert a list of array indices to a concatenated string with SUBSEP.
	 * This is used for multidimensional arrays.
	 * <p>
	 * Stack before: i1, i2, ..., in <br>
	 * Stack after: "i1SUBSEPi2SUBSEP...in"
	 */
	public static final int _APPLY_SUBSEP_ = 370;	// x -> 0

	/**
	 * Deletes an entry in an array.
	 * <p>
	 * Stack before: i <br>
	 * Stack after: ...
	 */
	public static final int _DELETE_ARRAY_ELEMENT_ = 371;	// 0 -> 0

	/**
	 * Internal.
	 * <p>
	 * Stack remains unchanged.
	 */
	public static final int _SET_EXIT_ADDRESS_ = 372;	// 0 -> 0

	/**
	 * Internal.
	 * <p>
	 * Stack remains unchanged.
	 */
	public static final int _SET_WITHIN_END_BLOCKS_ = 373;	// 0 -> 0

	/**
	 * Terminates execution and returns specified exit code.
	 * <p>
	 * Stack before: integer <br>
	 * Stack after: N/A
	 */
	public static final int _EXIT_WITH_CODE_ = 374;	// 0 -> 0

	/**
	 * Returns a regex pattern.
	 * <p>
	 * Stack before: ... <br>
	 * Stack after: the regex pattern object
	 */
	public static final int _REGEXP_ = 375;		// 0 -> x

	/**
	 * Returns a pair of regex patterns.
	 * <p>
	 * Stack before: pattern1, pattern2 <br>
	 * Stack after: regex pair object
	 */
	public static final int _CONDITION_PAIR_ = 376;		// x, y -> x

	/**
	 * Returns whether the specified key is in the array.
	 * <p>
	 * Stack before: key, array <br>
	 * Stack after: true|false
	 */
	public static final int _IS_IN_ = 377;		// x,y -> x

	/**
	 * Cast to integer
	 * <p>
	 * Stack before: object <br>
	 * Stack after: integer
	 */
	public static final int _CAST_INT_ = 378;		// x -> (int)x

	/**
	 * Cast to double
	 * <p>
	 * Stack before: object <br>
	 * Stack after: double
	 */
	public static final int _CAST_DOUBLE_ = 379;		// x -> (double)x

	/**
	 * Cast to string
	 * <p>
	 * Stack before: object <br>
	 * Stack after: string
	 */
	public static final int _CAST_STRING_ = 380;		// x -> (string)x

	/**
	 * Deprecated.
	 */
	public static final int _THIS_ = 381;		// 0 -> (this)

	/**
	 * Call a function from an extension
	 * <p>
	 * Stack before: x1, x2, ..., xn <br>
	 * Stack after: f(x1, x2, ..., xn)
	 */
	public static final int _EXTENSION_ = 382;		// x1,x2,...,xn -> x

	/**
	 * Execute the specified AWK code
	 * <p>
	 * Stack before: script <br>
	 * Stack after: exit code of the script, or zero when successful, -1 when failed
	 */
	public static final int _EXEC_ = 383;		// x1,x2,...,xn -> x

	/**
	 * Delete the specified array.
	 * <p>
	 * Stack remains unchanged.
	 */
	public static final int _DELETE_ARRAY_ = 384;	// 0 -> 0

	/**
	 * Converts the top stack element to a number;
	 * pushes the result onto the stack.
	 * <p>
	 * Stack before: x ...<br/>
	 * Stack after: x ... (as a number)
	 *
	 */
	public static final int _UNARY_PLUS_ = 385;	// x -> -x

	/**
	 * Terminates execution without specifying an exit code.
	 * <p>
	 * Stack before: N/A <br>
	 * Stack after: N/A
	 */
	public static final int _EXIT_WITHOUT_CODE_ = 386;	// 0 -> 0

	/**
	 * Assign the ORS variable offset. This is important for the
	 * AVM to use when outputting expressions via PRINT.
	 * <p>
	 * The operand stack is unaffected.
	 *
	 */
	public static final int _ORS_OFFSET_ = 387;	// 0 -> 0

	/**
	 * Increases the variable reference by one; pushes the original value
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ... or 0 if uninitialized
	 *
	 */
	public static final int _POSTINC_ = 388;	// 0 -> x

	/**
	 * Decreases the variable reference by one; pushes the original value
	 * onto the stack.
	 * <p>
	 * Argument 1: offset of the particular variable into the variable manager<br/>
	 * Argument 2: whether the variable is global or local
	 *
	 * <p>
	 * Stack before: ...<br/>
	 * Stack after: x ... or 0 if uninitialized
	 *
	 */
	public static final int _POSTDEC_ = 389;	// 0 -> x

	/**
	 * Override add() to populate the line number for each tuple,
	 * rather than polluting all the constructors with this assignment.
	 */
	private java.util.List<Tuple> queue = new ArrayList<Tuple>(100) {

		private static final long serialVersionUID = -6334362156408598578L;

		@Override
		public boolean add(Tuple t) {
			t.setLineNumber(lineno_stack.peek());
			return super.add(t);
		}
	};

	/** Unresolved addresses */
	private Set<Address> unresolved_addresses = new HashSet<Address>();

	/** Needed only for dumping intermediate code to text such that address labels are provided. */
	private Map<Integer, Address> address_indexes = new HashMap<Integer, Address>();
	/** Needed only for dumping intermediate code to text such that address labels are provided. */
	private Map<String, Integer> address_label_counts = new HashMap<String, Integer>();

	/**
	 * <p>toOpcodeString.</p>
	 *
	 * @param opcode a int
	 * @return a {@link java.lang.String} object
	 */
	public static String toOpcodeString(int opcode) {
		Class<?> c = AwkTuples.class;
		Field[] fields = c.getDeclaredFields();
		try {
			for (Field field : fields) {
				if ((field.getModifiers() & Modifier.STATIC) > 0 && field.getType() == Long.TYPE && field.getLong(null) == opcode) {
					return field.getName();
				}
			}
		} catch (IllegalAccessException iac) {
			LOG.error("Failed to create OP-Code string", iac);
			return "[" + opcode + ": " + iac + "]";
		}
		return "{" + opcode + "}";
	}

	/**
	 * <p>pop.</p>
	 */
	public void pop() {
		queue.add(new Tuple(_POP_));
	}

	/**
	 * <p>push.</p>
	 *
	 * @param o a {@link java.lang.Object} object
	 */
	public void push(Object o) {
		assert (o instanceof String) || (o instanceof Long) || (o instanceof Integer) || (o instanceof Double); //  || (o instanceof Pattern);
		if (o instanceof String) {
			queue.add(new Tuple(_PUSH_, o.toString()));
		} else if (o instanceof Integer) {
			queue.add(new Tuple(_PUSH_, (Integer) o));
		} else if (o instanceof Long) {
			queue.add(new Tuple(_PUSH_, (Long) o));
		} else if (o instanceof Double) {
			queue.add(new Tuple(_PUSH_, (Double) o));
		} else {
			assert false : "Invalid type for " + o + ", "+o.getClass();
		}
	}

	/**
	 * <p>ifFalse.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void ifFalse(Address address) {
		queue.add(new Tuple(_IFFALSE_, address));
	}

	/**
	 * <p>toNumber.</p>
	 */
	public void toNumber() {
		queue.add(new Tuple(_TO_NUMBER_));
	}

	/**
	 * <p>ifTrue.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void ifTrue(Address address) {
		queue.add(new Tuple(_IFTRUE_, address));
	}

	/**
	 * <p>gotoAddress.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void gotoAddress(Address address) {
		queue.add(new Tuple(_GOTO_, address));
	}

	/**
	 * <p>createAddress.</p>
	 *
	 * @param label a {@link java.lang.String} object
	 * @return a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public Address createAddress(String label) {
		Integer I = address_label_counts.get(label);
		if (I == null) {
			I = 0;
		} else {
			//I = new Integer(I.intValue()+1);
			I = I + 1;
		}
		address_label_counts.put(label, I);
		Address address = new AddressImpl(label + "_" + I);
		unresolved_addresses.add(address);
		return address;
	}

	/**
	 * <p>address.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 * @return a {@link org.metricshub.jawk.intermediate.AwkTuples} object
	 */
	public AwkTuples address(Address address) {
		if (unresolved_addresses.contains(address)) {
			unresolved_addresses.remove(address);
			address.assignIndex(queue.size());
			address_indexes.put(queue.size(), address);
			return this;
		}
		throw new Error(address.toString() + " is already resolved, or unresolved from another scope.");
	}

	/**
	 * <p>nop.</p>
	 */
	public void nop() {
		queue.add(new Tuple(_NOP_));
	}

	/**
	 * <p>print.</p>
	 *
	 * @param num_exprs a int
	 */
	public void print(int num_exprs) {
		queue.add(new Tuple(_PRINT_, num_exprs));
	}

	/**
	 * <p>printToFile.</p>
	 *
	 * @param num_exprs a int
	 * @param append a boolean
	 */
	public void printToFile(int num_exprs, boolean append) {
		queue.add(new Tuple(_PRINT_TO_FILE_, num_exprs, append));
	}

	/**
	 * <p>printToPipe.</p>
	 *
	 * @param num_exprs a int
	 */
	public void printToPipe(int num_exprs) {
		queue.add(new Tuple(_PRINT_TO_PIPE_, num_exprs));
	}

	/**
	 * <p>printf.</p>
	 *
	 * @param num_exprs a int
	 */
	public void printf(int num_exprs) {
		queue.add(new Tuple(_PRINTF_, num_exprs));
	}

	/**
	 * <p>printfToFile.</p>
	 *
	 * @param num_exprs a int
	 * @param append a boolean
	 */
	public void printfToFile(int num_exprs, boolean append) {
		queue.add(new Tuple(_PRINTF_TO_FILE_, num_exprs, append));
	}

	/**
	 * <p>printfToPipe.</p>
	 *
	 * @param num_exprs a int
	 */
	public void printfToPipe(int num_exprs) {
		queue.add(new Tuple(_PRINTF_TO_PIPE_, num_exprs));
	}

	/**
	 * <p>sprintf.</p>
	 *
	 * @param num_exprs a int
	 */
	public void sprintf(int num_exprs) {
		queue.add(new Tuple(_SPRINTF_, num_exprs));
	}

	/**
	 * <p>length.</p>
	 *
	 * @param num_exprs a int
	 */
	public void length(int num_exprs) {
		queue.add(new Tuple(_LENGTH_, num_exprs));
	}

	/**
	 * <p>concat.</p>
	 */
	public void concat() {
		queue.add(new Tuple(_CONCAT_));
	}

	/**
	 * <p>assign.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void assign(int offset, boolean is_global) {
		queue.add(new Tuple(_ASSIGN_, offset, is_global));
	}

	/**
	 * <p>assignArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void assignArray(int offset, boolean is_global) {
		queue.add(new Tuple(_ASSIGN_ARRAY_, offset, is_global));
	}

	/**
	 * <p>assignAsInput.</p>
	 */
	public void assignAsInput() {
		queue.add(new Tuple(_ASSIGN_AS_INPUT_));
	}

	/**
	 * <p>assignAsInputField.</p>
	 */
	public void assignAsInputField() {
		queue.add(new Tuple(_ASSIGN_AS_INPUT_FIELD_));
	}

	/**
	 * <p>dereference.</p>
	 *
	 * @param offset a int
	 * @param is_array a boolean
	 * @param is_global a boolean
	 */
	public void dereference(int offset, boolean is_array, boolean is_global) {
		queue.add(new Tuple(_DEREFERENCE_, offset, is_array, is_global));
	}

	/**
	 * <p>plusEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void plusEq(int offset, boolean is_global) {
		queue.add(new Tuple(_PLUS_EQ_, offset, is_global));
	}

	/**
	 * <p>minusEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void minusEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MINUS_EQ_, offset, is_global));
	}

	/**
	 * <p>multEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void multEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MULT_EQ_, offset, is_global));
	}

	/**
	 * <p>divEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void divEq(int offset, boolean is_global) {
		queue.add(new Tuple(_DIV_EQ_, offset, is_global));
	}

	/**
	 * <p>modEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void modEq(int offset, boolean is_global) {
		queue.add(new Tuple(_MOD_EQ_, offset, is_global));
	}

	/**
	 * <p>powEq.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void powEq(int offset, boolean is_global) {
		queue.add(new Tuple(_POW_EQ_, offset, is_global));
	}

	/**
	 * <p>plusEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void plusEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_PLUS_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>minusEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void minusEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MINUS_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>multEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void multEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MULT_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>divEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void divEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_DIV_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>modEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void modEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_MOD_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>powEqArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void powEqArray(int offset, boolean is_global) {
		queue.add(new Tuple(_POW_EQ_ARRAY_, offset, is_global));
	}

	/**
	 * <p>plusEqInputField.</p>
	 */
	public void plusEqInputField() {
		queue.add(new Tuple(_PLUS_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>minusEqInputField.</p>
	 */
	public void minusEqInputField() {
		queue.add(new Tuple(_MINUS_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>multEqInputField.</p>
	 */
	public void multEqInputField() {
		queue.add(new Tuple(_MULT_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>divEqInputField.</p>
	 */
	public void divEqInputField() {
		queue.add(new Tuple(_DIV_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>modEqInputField.</p>
	 */
	public void modEqInputField() {
		queue.add(new Tuple(_MOD_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>powEqInputField.</p>
	 */
	public void powEqInputField() {
		queue.add(new Tuple(_POW_EQ_INPUT_FIELD_));
	}

	/**
	 * <p>srand.</p>
	 *
	 * @param num a int
	 */
	public void srand(int num) {
		queue.add(new Tuple(_SRAND_, num));
	}

	/**
	 * <p>rand.</p>
	 */
	public void rand() {
		queue.add(new Tuple(_RAND_));
	}

	/**
	 * <p>intFunc.</p>
	 */
	public void intFunc() {
		queue.add(new Tuple(_INTFUNC_));
	}

	/**
	 * <p>sqrt.</p>
	 */
	public void sqrt() {
		queue.add(new Tuple(_SQRT_));
	}

	/**
	 * <p>log.</p>
	 */
	public void log() {
		queue.add(new Tuple(_LOG_));
	}

	/**
	 * <p>exp.</p>
	 */
	public void exp() {
		queue.add(new Tuple(_EXP_));
	}

	/**
	 * <p>sin.</p>
	 */
	public void sin() {
		queue.add(new Tuple(_SIN_));
	}

	/**
	 * <p>cos.</p>
	 */
	public void cos() {
		queue.add(new Tuple(_COS_));
	}

	/**
	 * <p>atan2.</p>
	 */
	public void atan2() {
		queue.add(new Tuple(_ATAN2_));
	}

	/**
	 * <p>match.</p>
	 */
	public void match() {
		queue.add(new Tuple(_MATCH_));
	}

	/**
	 * <p>index.</p>
	 */
	public void index() {
		queue.add(new Tuple(_INDEX_));
	}

	/**
	 * <p>subForDollar0.</p>
	 *
	 * @param is_gsub a boolean
	 */
	public void subForDollar0(boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_DOLLAR_0_, is_gsub));
	}

	/**
	 * <p>subForDollarReference.</p>
	 *
	 * @param is_gsub a boolean
	 */
	public void subForDollarReference(boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_DOLLAR_REFERENCE_, is_gsub));
	}

	/**
	 * <p>subForVariable.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 * @param is_gsub a boolean
	 */
	public void subForVariable(int offset, boolean is_global, boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_VARIABLE_, offset, is_global, is_gsub));
	}

	/**
	 * <p>subForArrayReference.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 * @param is_gsub a boolean
	 */
	public void subForArrayReference(int offset, boolean is_global, boolean is_gsub) {
		queue.add(new Tuple(_SUB_FOR_ARRAY_REFERENCE_, offset, is_global, is_gsub));
	}

	/**
	 * <p>split.</p>
	 *
	 * @param numargs a int
	 */
	public void split(int numargs) {
		queue.add(new Tuple(_SPLIT_, numargs));
	}

	/**
	 * <p>substr.</p>
	 *
	 * @param numargs a int
	 */
	public void substr(int numargs) {
		queue.add(new Tuple(_SUBSTR_, numargs));
	}

	/**
	 * <p>tolower.</p>
	 */
	public void tolower() {
		queue.add(new Tuple(_TOLOWER_));
	}

	/**
	 * <p>toupper.</p>
	 */
	public void toupper() {
		queue.add(new Tuple(_TOUPPER_));
	}

	/**
	 * <p>system.</p>
	 */
	public void system() {
		queue.add(new Tuple(_SYSTEM_));
	}

	/**
	 * <p>exec.</p>
	 */
	public void exec() {
		queue.add(new Tuple(_EXEC_));
	}

	/**
	 * <p>swap.</p>
	 */
	public void swap() {
		queue.add(new Tuple(_SWAP_));
	}

	/**
	 * <p>add.</p>
	 */
	public void add() {
		queue.add(new Tuple(_ADD_));
	}

	/**
	 * <p>subtract.</p>
	 */
	public void subtract() {
		queue.add(new Tuple(_SUBTRACT_));
	}

	/**
	 * <p>multiply.</p>
	 */
	public void multiply() {
		queue.add(new Tuple(_MULTIPLY_));
	}

	/**
	 * <p>divide.</p>
	 */
	public void divide() {
		queue.add(new Tuple(_DIVIDE_));
	}

	/**
	 * <p>mod.</p>
	 */
	public void mod() {
		queue.add(new Tuple(_MOD_));
	}

	/**
	 * <p>pow.</p>
	 */
	public void pow() {
		queue.add(new Tuple(_POW_));
	}

	/**
	 * <p>inc.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void inc(int offset, boolean is_global) {
		queue.add(new Tuple(_INC_, offset, is_global));
	}

	/**
	 * <p>dec.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void dec(int offset, boolean is_global) {
		queue.add(new Tuple(_DEC_, offset, is_global));
	}

	/**
	 * <p>postInc.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void postInc(int offset, boolean is_global) {
		queue.add(new Tuple(_POSTINC_, offset, is_global));
	}

	/**
	 * <p>postDec.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void postDec(int offset, boolean is_global) {
		queue.add(new Tuple(_POSTDEC_, offset, is_global));
	}

	/**
	 * <p>incArrayRef.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void incArrayRef(int offset, boolean is_global) {
		queue.add(new Tuple(_INC_ARRAY_REF_, offset, is_global));
	}

	/**
	 * <p>decArrayRef.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void decArrayRef(int offset, boolean is_global) {
		queue.add(new Tuple(_DEC_ARRAY_REF_, offset, is_global));
	}

	/**
	 * <p>incDollarRef.</p>
	 */
	public void incDollarRef() {
		queue.add(new Tuple(_INC_DOLLAR_REF_));
	}

	/**
	 * <p>decDollarRef.</p>
	 */
	public void decDollarRef() {
		queue.add(new Tuple(_DEC_DOLLAR_REF_));
	}

	/**
	 * <p>dup.</p>
	 */
	public void dup() {
		queue.add(new Tuple(_DUP_));
	}

	/**
	 * <p>not.</p>
	 */
	public void not() {
		queue.add(new Tuple(_NOT_));
	}

	/**
	 * <p>negate.</p>
	 */
	public void negate() {
		queue.add(new Tuple(_NEGATE_));
	}

	/**
	 * <p>unary plus.</p>
	 */
	public void unaryPlus() {
		queue.add(new Tuple(_UNARY_PLUS_));
	}

	/**
	 * <p>cmpEq.</p>
	 */
	public void cmpEq() {
		queue.add(new Tuple(_CMP_EQ_));
	}

	/**
	 * <p>cmpLt.</p>
	 */
	public void cmpLt() {
		queue.add(new Tuple(_CMP_LT_));
	}

	/**
	 * <p>cmpGt.</p>
	 */
	public void cmpGt() {
		queue.add(new Tuple(_CMP_GT_));
	}

	/**
	 * <p>matches.</p>
	 */
	public void matches() {
		queue.add(new Tuple(_MATCHES_));
	}

	/**
	 * <p>sleep.</p>
	 *
	 * @param num_args a int
	 */
	public void sleep(int num_args) {
		queue.add(new Tuple(_SLEEP_, num_args));
	}

	/**
	 * <p>dump.</p>
	 *
	 * @param num_args a int
	 */
	public void dump(int num_args) {
		queue.add(new Tuple(_DUMP_, num_args));
	}

	/**
	 * <p>dereferenceArray.</p>
	 */
	public void dereferenceArray() {
		queue.add(new Tuple(_DEREF_ARRAY_));
	}

	/**
	 * <p>keylist.</p>
	 */
	public void keylist() {
		queue.add(new Tuple(_KEYLIST_));
	}

	/**
	 * <p>isEmptyList.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void isEmptyList(Address address) {
		queue.add(new Tuple(_IS_EMPTY_KEYLIST_, address));
	}

	/**
	 * <p>getFirstAndRemoveFromList.</p>
	 */
	public void getFirstAndRemoveFromList() {
		queue.add(new Tuple(_GET_FIRST_AND_REMOVE_FROM_KEYLIST_));
	}

	/**
	 * <p>checkClass.</p>
	 *
	 * @param cls a {@link java.lang.Class} object
	 * @return a boolean
	 */
	public boolean checkClass(Class<?> cls) {
		queue.add(new Tuple(_CHECK_CLASS_, cls));
		return true;
	}

	/**
	 * <p>getInputField.</p>
	 */
	public void getInputField() {
		queue.add(new Tuple(_GET_INPUT_FIELD_));
	}

	/**
	 * <p>consumeInput.</p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void consumeInput(Address address) {
		queue.add(new Tuple(_CONSUME_INPUT_, address));
	}

	/**
	 * <p>getlineInput.</p>
	 */
	public void getlineInput() {
		queue.add(new Tuple(_GETLINE_INPUT_));
	}

	/**
	 * <p>useAsFileInput.</p>
	 */
	public void useAsFileInput() {
		queue.add(new Tuple(_USE_AS_FILE_INPUT_));
	}

	/**
	 * <p>useAsCommandInput.</p>
	 */
	public void useAsCommandInput() {
		queue.add(new Tuple(_USE_AS_COMMAND_INPUT_));
	}

	/**
	 * <p>nfOffset.</p>
	 *
	 * @param offset a int
	 */
	public void nfOffset(int offset) {
		queue.add(new Tuple(_NF_OFFSET_, offset));
	}

	/**
	 * <p>nrOffset.</p>
	 *
	 * @param offset a int
	 */
	public void nrOffset(int offset) {
		queue.add(new Tuple(_NR_OFFSET_, offset));
	}

	/**
	 * <p>fnrOffset.</p>
	 *
	 * @param offset a int
	 */
	public void fnrOffset(int offset) {
		queue.add(new Tuple(_FNR_OFFSET_, offset));
	}

	/**
	 * <p>fsOffset.</p>
	 *
	 * @param offset a int
	 */
	public void fsOffset(int offset) {
		queue.add(new Tuple(_FS_OFFSET_, offset));
	}

	/**
	 * <p>rsOffset.</p>
	 *
	 * @param offset a int
	 */
	public void rsOffset(int offset) {
		queue.add(new Tuple(_RS_OFFSET_, offset));
	}

	/**
	 * <p>ofsOffset.</p>
	 *
	 * @param offset a int
	 */
	public void ofsOffset(int offset) {
		queue.add(new Tuple(_OFS_OFFSET_, offset));
	}

	/**
	 * <p>orsOffset.</p>
	 *
	 * @param offset a int
	 */
	public void orsOffset(int offset) {
		queue.add(new Tuple(_ORS_OFFSET_, offset));
	}

	/**
	 * <p>rstartOffset.</p>
	 *
	 * @param offset a int
	 */
	public void rstartOffset(int offset) {
		queue.add(new Tuple(_RSTART_OFFSET_, offset));
	}

	/**
	 * <p>rlengthOffset.</p>
	 *
	 * @param offset a int
	 */
	public void rlengthOffset(int offset) {
		queue.add(new Tuple(_RLENGTH_OFFSET_, offset));
	}

	/**
	 * <p>filenameOffset.</p>
	 *
	 * @param offset a int
	 */
	public void filenameOffset(int offset) {
		queue.add(new Tuple(_FILENAME_OFFSET_, offset));
	}

	/**
	 * <p>subsepOffset.</p>
	 *
	 * @param offset a int
	 */
	public void subsepOffset(int offset) {
		queue.add(new Tuple(_SUBSEP_OFFSET_, offset));
	}

	/**
	 * <p>convfmtOffset.</p>
	 *
	 * @param offset a int
	 */
	public void convfmtOffset(int offset) {
		queue.add(new Tuple(_CONVFMT_OFFSET_, offset));
	}

	/**
	 * <p>ofmtOffset.</p>
	 *
	 * @param offset a int
	 */
	public void ofmtOffset(int offset) {
		queue.add(new Tuple(_OFMT_OFFSET_, offset));
	}

	/**
	 * <p>environOffset.</p>
	 *
	 * @param offset a int
	 */
	public void environOffset(int offset) {
		queue.add(new Tuple(_ENVIRON_OFFSET_, offset));
	}

	/**
	 * <p>argcOffset.</p>
	 *
	 * @param offset a int
	 */
	public void argcOffset(int offset) {
		queue.add(new Tuple(_ARGC_OFFSET_, offset));
	}

	/**
	 * <p>argvOffset.</p>
	 *
	 * @param offset a int
	 */
	public void argvOffset(int offset) {
		queue.add(new Tuple(_ARGV_OFFSET_, offset));
	}

	/**
	 * <p>applyRS.</p>
	 */
	public void applyRS() {
		queue.add(new Tuple(_APPLY_RS_));
	}

	/**
	 * <p>function.</p>
	 *
	 * @param func_name a {@link java.lang.String} object
	 * @param num_formal_params a int
	 */
	public void function(String func_name, int num_formal_params) {
		queue.add(new Tuple(_FUNCTION_, func_name, num_formal_params));
	}
	//public void callFunction(Address addr, String func_name, int num_formal_params, int num_actual_params) { queue.add(new Tuple(_CALL_FUNCTION_, addr, func_name, num_formal_params, num_actual_params)); }

	/**
	 * <p>callFunction.</p>
	 *
	 * @param has_func_addr a {@link org.metricshub.jawk.intermediate.HasFunctionAddress} object
	 * @param func_name a {@link java.lang.String} object
	 * @param num_formal_params a int
	 * @param num_actual_params a int
	 */
	public void callFunction(HasFunctionAddress has_func_addr, String func_name, int num_formal_params, int num_actual_params) {
		queue.add(new Tuple(_CALL_FUNCTION_, has_func_addr, func_name, num_formal_params, num_actual_params));
	}

	/**
	 * <p>setReturnResult.</p>
	 */
	public void setReturnResult() {
		queue.add(new Tuple(_SET_RETURN_RESULT_));
	}

	/**
	 * <p>returnFromFunction.</p>
	 */
	public void returnFromFunction() {
		queue.add(new Tuple(_RETURN_FROM_FUNCTION_));
	}

	/**
	 * <p>setNumGlobals.</p>
	 *
	 * @param num_globals a int
	 */
	public void setNumGlobals(int num_globals) {
		queue.add(new Tuple(_SET_NUM_GLOBALS_, num_globals));
	}

	/**
	 * <p>close.</p>
	 */
	public void close() {
		queue.add(new Tuple(_CLOSE_));
	}

	/**
	 * <p>applySubsep.</p>
	 *
	 * @param count a int
	 */
	public void applySubsep(int count) {
		queue.add(new Tuple(_APPLY_SUBSEP_, count));
	}

	/**
	 * <p>deleteArrayElement.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void deleteArrayElement(int offset, boolean is_global) {
		queue.add(new Tuple(_DELETE_ARRAY_ELEMENT_, offset, is_global));
	}

	/**
	 * <p>deleteArray.</p>
	 *
	 * @param offset a int
	 * @param is_global a boolean
	 */
	public void deleteArray(int offset, boolean is_global) {
		queue.add(new Tuple(_DELETE_ARRAY_, offset, is_global));
	}

	/**
	 * <p>setExitAddress.</p>
	 *
	 * @param addr a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void setExitAddress(Address addr) {
		queue.add(new Tuple(_SET_EXIT_ADDRESS_, addr));
	}

	/**
	 * <p>setWithinEndBlocks.</p>
	 *
	 * @param b a boolean
	 */
	public void setWithinEndBlocks(boolean b) {
		queue.add(new Tuple(_SET_WITHIN_END_BLOCKS_, b));
	}

	/**
	 * <p>exitWithCode.</p>
	 */
	public void exitWithCode() {
		queue.add(new Tuple(_EXIT_WITH_CODE_));
	}

	/**
	 * <p>exitWithCode.</p>
	 */
	public void exitWithoutCode() {
		queue.add(new Tuple(_EXIT_WITHOUT_CODE_));
	}

	/**
	 * <p>regexp.</p>
	 *
	 * @param regexp_str a {@link java.lang.String} object
	 */
	public void regexp(String regexp_str) {
		queue.add(new Tuple(_REGEXP_, regexp_str));
	}

	/**
	 * <p>regexpPair.</p>
	 */
	public void conditionPair() {
		queue.add(new Tuple(_CONDITION_PAIR_));
	}

	/**
	 * <p>isIn.</p>
	 */
	public void isIn() {
		queue.add(new Tuple(_IS_IN_));
	}

	/**
	 * <p>castInt.</p>
	 */
	public void castInt() {
		queue.add(new Tuple(_CAST_INT_));
	}

	/**
	 * <p>castDouble.</p>
	 */
	public void castDouble() {
		queue.add(new Tuple(_CAST_DOUBLE_));
	}

	/**
	 * <p>castString.</p>
	 */
	public void castString() {
		queue.add(new Tuple(_CAST_STRING_));
	}

	/**
	 * <p>scriptThis.</p>
	 */
	public void scriptThis() {
		queue.add(new Tuple(_THIS_));
	}

	/**
	 * <p>extension.</p>
	 *
	 * @param extension_keyword a {@link java.lang.String} object
	 * @param param_count a int
	 * @param is_initial a boolean
	 */
	public void extension(String extension_keyword, int param_count, boolean is_initial) {
		queue.add(new Tuple(_EXTENSION_, extension_keyword, param_count, is_initial));
	}

	/**
	 * <p>dump.</p>
	 *
	 * @param ps a {@link java.io.PrintStream} object
	 */
	public void dump(PrintStream ps) {
		ps.println("(" + version_manager + ")");
		ps.println();
		for (int i = 0; i < queue.size(); i++) {
			Address address = address_indexes.get(i);
			if (address == null) {
				ps.println(i + " : " + queue.get(i));
			} else {
				ps.println(i + " : [" + address + "] : " + queue.get(i));
			}
		}
	}

	/**
	 * <p>top.</p>
	 *
	 * @return a {@link org.metricshub.jawk.intermediate.Position} object
	 */
	public Position top() {
		return new PositionImpl();
	}

	/**
	 * Executed after all tuples are entered in the queue.
	 * Its main functions are:
	 * <ul>
	 * <li>Assign queue.next to the next element in the queue.
	 * <li>Calls touch(...) per Tuple so that addresses can be normalized/assigned/allocated
	 * properly.
	 * </ul>
	 */
        public void postProcess() {
                assert queue.isEmpty() || !queue.get(0).hasNext() : "postProcess() already executed";
                // allocate nexts
                for (int i = 0; i < queue.size() - 1; i++) {
                        queue.get(i).setNext(queue.get(i + 1));
                }
                // touch per element
                for (Tuple tuple : queue) {
                        tuple.touch(queue);
                }
        }

        /**
         * Merge tuples and metadata from another {@link AwkTuples} instance into
         * this one.  Address indexes of the appended tuples are shifted
         * accordingly and queue links are fixed.
         *
         * @param other The {@link AwkTuples} instance whose content should be
         *            appended to this instance.
         */
        public void mergeFrom(AwkTuples other) {
                if (other == null || other.queue.isEmpty()) {
                        return;
                }

                int offset = queue.size();

                // fix next pointer of the last tuple in this queue
                if (!queue.isEmpty()) {
                        queue.get(queue.size() - 1).setNext(other.queue.get(0));
                }

                // shift indexes in address maps
                for (Map.Entry<Integer, Address> e : other.address_indexes.entrySet()) {
                        Address addr = e.getValue();
                        if (addr.index() >= 0) {
                                addr.assignIndex(addr.index() + offset);
                        }
                        address_indexes.put(e.getKey() + offset, addr);
                }

                // append tuples adjusting embedded addresses
                for (Tuple t : other.queue) {
                        if (t.address != null) {
                                t.address.assignIndex(t.address.index() + offset);
                        }
                        if (t.hasFuncAddr != null) {
                                Address a = t.hasFuncAddr.getFunctionAddress();
                                if (a != null && a.index() >= 0) {
                                        a.assignIndex(a.index() + offset);
                                }
                        }
                        queue.add(t);
                }

                unresolved_addresses.addAll(other.unresolved_addresses);
                address_label_counts.putAll(other.address_label_counts);
                global_var_offset_map.putAll(other.global_var_offset_map);
                global_var_aarray_map.putAll(other.global_var_aarray_map);
                if (other.function_names != null) {
                        if (function_names == null) {
                                function_names = new HashSet<String>();
                        }
                        function_names.addAll(other.function_names);
                }
        }

	/** Map of global variables offsets */
	private Map<String, Integer> global_var_offset_map = new HashMap<String, Integer>();

	/** Map of global arrays */
	private Map<String, Boolean> global_var_aarray_map = new HashMap<String, Boolean>();

	/** List of user function names */
	private Set<String> function_names = null;

	/**
	 * Accept a {variable_name -&gt; offset} mapping such that global variables can be
	 * assigned while processing name=value and filename command-line arguments.
	 *
	 * @param varname Name of the global variable
	 * @param offset What offset to use for the variable
	 * @param is_array Whether the variable is actually an array
	 */
	public void addGlobalVariableNameToOffsetMapping(String varname, int offset, boolean is_array) {
		if (global_var_offset_map.get(varname) != null) {
			assert global_var_aarray_map.get(varname) != null;
			return;
		}
		global_var_offset_map.put(varname, offset);
		global_var_aarray_map.put(varname, is_array);
	}

	/**
	 * Accept a set of function names from the parser. This is
	 * useful for invalidating name=value assignments from the
	 * command line parameters, either via -v arguments or
	 * passed into ARGV.
	 *
	 * @param function_names A set of function name strings.
	 */
	public void setFunctionNameSet(Set<String> function_names) {
		// setFunctionNameSet is called with a keySet from
		// a HashMap as a parameter, which is NOT
		// Serializable. Creating a new HashSet around
		// the parameter resolves the issue.
		// Otherwise, attempting to serialize this
		// object results in a NotSerializableEexception
		// being thrown because of function_names field
		// being a keyset from a HashMap.
		this.function_names = new HashSet<String>(function_names);
	}

	/**
	 * <p>getGlobalVariableOffsetMap.</p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, Integer> getGlobalVariableOffsetMap() {
		return global_var_offset_map;
	}

	/**
	 * <p>getGlobalVariableAarrayMap.</p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, Boolean> getGlobalVariableAarrayMap() {
		return global_var_aarray_map;
	}

	/**
	 * <p>getFunctionNameSet.</p>
	 *
	 * @return a {@link java.util.Set} object
	 */
	public Set<String> getFunctionNameSet() {
		assert function_names != null;
		return function_names;
	}

	/** linenumber stack ... */
	private MyStack<Integer> lineno_stack = new LinkedListStackImpl<Integer>();

	/**
	 * Push the current line number onto the line number stack.
	 * This is called by the parser to keep track of the
	 * current source line number. Keeping track of line
	 * numbers this way allows the runtime to report
	 * more meaningful errors by providing source line numbers
	 * within error reports.
	 *
	 * @param lineno The current source line number.
	 */
	public void pushSourceLineNumber(int lineno) {
		lineno_stack.push(lineno);
	}

	/**
	 * <p>popSourceLineNumber.</p>
	 *
	 * @param lineno a int
	 */
	public void popSourceLineNumber(int lineno) {
		int tos = lineno_stack.pop();
		assert (lineno == tos);
	}


	/**
	 * Intermediate file version manager. Ensures the AwkTuples
	 * class version matches the version supported by the interpreter/compiler.
	 */
	private static class VersionManager implements Serializable {

		private static final long serialVersionUID = -2015316238483923915L;

		/**
		 * Class version number.
		 * This number is modified by the developer.
		 * It should be modified only if modifications
		 * to pre-existing tuple arguments are modified,
		 * or if instruction codes are removed.
		 * <p>
		 * <ul>
		 * <li> Version 1 - Initial release.
		 * <li> Version 2 - Changes to support compilation to JVM.
		 * </ul>
		 *
		 */
		private static final int CLASS_VERSION = 2;

		/**
		 * Instance version number.
		 * The only way it could be different from the
		 * class version is only during deserialization.
		 */
		private int INSTANCE_VERSION = CLASS_VERSION;

		/**
		 * Upon deserialization, ensures the instance
		 * version matches the class version.
		 * @throws IOException upon an IO error
		 * @throws ClassNotFoundException if the class
		 *	that is deserialized cannot be found
		 * @throws InvalidClassException if the
		 *	instance version does not match
		 *	the class version
		 */
		private void readObject(ObjectInputStream ois)
				throws IOException, ClassNotFoundException
		{
			INSTANCE_VERSION = ois.readInt();
			if (INSTANCE_VERSION != CLASS_VERSION) {
				throw new InvalidClassException("Invalid intermeidate file format (instance version " + INSTANCE_VERSION + " != class version " + CLASS_VERSION + ")");
			}
		}

		private void writeObject(ObjectOutputStream oos)
				throws IOException
		{
			oos.writeInt(INSTANCE_VERSION);
		}

		@Override
		public String toString() {
			return "intermediate file format version = " + INSTANCE_VERSION;
		}
	}
}
