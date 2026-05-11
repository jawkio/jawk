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
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import io.jawk.ext.ExtensionFunction;

/**
 * Represents one instruction in the tuple stream produced by {@link AwkTuples}.
 * Concrete subclasses carry only the operands required by their opcode or opcode
 * group.
 *
 * @author Danny Daglas
 * @see AwkTuples
 */
public abstract class Tuple implements Serializable {

	private static final long serialVersionUID = 8105941219003992817L;
	private final Opcode opcode;
	private int lineNumber = -1;
	private Tuple next = null;

	Tuple(Opcode opcode) {
		this.opcode = opcode;
	}

	/**
	 * Returns this tuple's opcode.
	 *
	 * @return opcode executed by the AVM
	 */
	public final Opcode getOpcode() {
		return opcode;
	}

	/**
	 * Returns this tuple's jump/call address, if it has one.
	 *
	 * @return tuple address, or {@code null}
	 */
	public Address getAddress() {
		return null;
	}

	/**
	 * Resolves deferred operands and validates resolved addresses.
	 *
	 * @param queue tuple queue used to validate address targets
	 */
	public void touch(List<Tuple> queue) {
		Address address = getAddress();
		if (address == null) {
			return;
		}
		if (address.index() == -1) {
			throw new Error("address " + address + " is unresolved");
		}
		if (address.index() >= queue.size()) {
			throw new Error("address " + address + " doesn't resolve to an actual list element");
		}
	}

	boolean hasNext() {
		return next != null;
	}

	/**
	 * Returns the next tuple in execution order.
	 *
	 * @return next tuple, or {@code null} at the end of the stream
	 */
	Tuple getNext() {
		return next;
	}

	void setNext(Tuple next) {
		this.next = next;
	}

	void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	/**
	 * Returns the source line number associated with this tuple.
	 *
	 * @return source line number, or {@code -1} when unknown
	 */
	public int getLineNumber() {
		return lineNumber;
	}

	private static String stringArgument(String value) {
		return ", \"" + value + '"';
	}

	private static String patternArgument(Pattern pattern) {
		return ", /" + (pattern == null ? "" : pattern.pattern()) + '/';
	}

	/**
	 * Tuple for opcodes without operands.
	 */
	public static class NoOperandTuple extends Tuple {
		private static final long serialVersionUID = 1L;

		NoOperandTuple(Opcode opcode) {
			super(opcode);
		}

		@Override
		public String toString() {
			return getOpcode().name();
		}
	}

	/**
	 * Tuple for JRT-managed built-in variable operations.
	 */
	public static final class BuiltinVarTuple extends NoOperandTuple {
		private static final long serialVersionUID = 1L;

		BuiltinVarTuple(Opcode opcode) {
			super(opcode);
		}
	}

	/**
	 * Tuple for a long literal.
	 */
	public static final class PushLongTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final long value;

		PushLongTuple(long value) {
			super(Opcode.PUSH_LONG);
			this.value = value;
		}

		/**
		 * Returns the literal value.
		 *
		 * @return literal long value
		 */
		public long getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + value;
		}
	}

	/**
	 * Tuple for a double literal.
	 */
	public static final class PushDoubleTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final double value;

		PushDoubleTuple(double value) {
			super(Opcode.PUSH_DOUBLE);
			this.value = value;
		}

		/**
		 * Returns the literal value.
		 *
		 * @return literal double value
		 */
		public double getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + value;
		}
	}

	/**
	 * Tuple for a string literal.
	 */
	public static final class PushStringTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final String value;

		PushStringTuple(String value) {
			super(Opcode.PUSH_STRING);
			this.value = value;
		}

		/**
		 * Returns the literal value.
		 *
		 * @return literal string value
		 */
		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getOpcode().name() + stringArgument(value);
		}
	}

	/**
	 * Tuple for opcodes whose single operand is a count.
	 */
	public static class CountTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final long count;

		CountTuple(Opcode opcode, long count) {
			super(opcode);
			this.count = count;
		}

		/**
		 * Returns the tuple count operand.
		 *
		 * @return count operand
		 */
		public final long getCount() {
			return count;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + count;
		}
	}

	/**
	 * Tuple for print/printf redirection with an append flag.
	 */
	public static final class CountAndAppendTuple extends CountTuple {
		private static final long serialVersionUID = 1L;
		private final boolean append;

		CountAndAppendTuple(Opcode opcode, long count, boolean append) {
			super(opcode, count);
			this.append = append;
		}

		/**
		 * Indicates whether redirected output should append.
		 *
		 * @return {@code true} for append mode
		 */
		public boolean isAppend() {
			return append;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + getCount() + ", " + append;
		}
	}

	/**
	 * Tuple for a long operand that is not interpreted by the tuple itself.
	 */
	public static class LongTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final long value;

		LongTuple(Opcode opcode, long value) {
			super(opcode);
			this.value = value;
		}

		/**
		 * Returns the long operand.
		 *
		 * @return long operand
		 */
		public final long getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + value;
		}
	}

	/**
	 * Tuple for a constant input-field index.
	 */
	public static final class InputFieldTuple extends LongTuple {
		private static final long serialVersionUID = 1L;

		InputFieldTuple(long fieldIndex) {
			super(Opcode.GET_INPUT_FIELD_CONST, fieldIndex);
		}

		/**
		 * Returns the constant input-field index.
		 *
		 * @return input-field index
		 */
		public long getFieldIndex() {
			return getValue();
		}
	}

	/**
	 * Tuple for an address operand.
	 */
	public static class AddressTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private Address address;

		AddressTuple(Opcode opcode, Address address) {
			super(opcode);
			this.address = address;
		}

		@Override
		public Address getAddress() {
			return address;
		}

		void setAddress(Address address) {
			this.address = address;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + address;
		}
	}

	/**
	 * Tuple for variable offset/global operands.
	 */
	public static class VariableTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final long variableOffset;
		private final boolean global;

		VariableTuple(Opcode opcode, long variableOffset, boolean global) {
			super(opcode);
			this.variableOffset = variableOffset;
			this.global = global;
		}

		/**
		 * Returns the variable offset.
		 *
		 * @return variable offset
		 */
		public final long getVariableOffset() {
			return variableOffset;
		}

		/**
		 * Indicates whether the variable offset belongs to the global frame.
		 *
		 * @return {@code true} for a global variable
		 */
		public final boolean isGlobal() {
			return global;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + variableOffset + ", " + global;
		}
	}

	/**
	 * Tuple for scalar compound assignments.
	 */
	public static final class CompoundAssignTuple extends VariableTuple {
		private static final long serialVersionUID = 1L;

		CompoundAssignTuple(Opcode opcode, long variableOffset, boolean global) {
			super(opcode, variableOffset, global);
		}
	}

	/**
	 * Tuple for array compound assignments.
	 */
	public static final class CompoundAssignArrayTuple extends VariableTuple {
		private static final long serialVersionUID = 1L;

		CompoundAssignArrayTuple(Opcode opcode, long variableOffset, boolean global) {
			super(opcode, variableOffset, global);
		}
	}

	/**
	 * Tuple for stack-provided map element compound assignments.
	 */
	public static final class CompoundAssignMapElementTuple extends NoOperandTuple {
		private static final long serialVersionUID = 1L;

		CompoundAssignMapElementTuple(Opcode opcode) {
			super(opcode);
		}
	}

	/**
	 * Tuple for input-field compound assignments.
	 */
	public static final class CompoundAssignInputFieldTuple extends NoOperandTuple {
		private static final long serialVersionUID = 1L;

		CompoundAssignInputFieldTuple(Opcode opcode) {
			super(opcode);
		}
	}

	/**
	 * Tuple for variable dereference.
	 */
	public static final class DereferenceTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final long variableOffset;
		private final boolean array;
		private final boolean global;

		DereferenceTuple(long variableOffset, boolean array, boolean global) {
			super(Opcode.DEREFERENCE);
			this.variableOffset = variableOffset;
			this.array = array;
			this.global = global;
		}

		/**
		 * Returns the variable offset.
		 *
		 * @return variable offset
		 */
		public long getVariableOffset() {
			return variableOffset;
		}

		/**
		 * Indicates whether this dereference should initialize an array.
		 *
		 * @return {@code true} when the variable is an array
		 */
		public boolean isArray() {
			return array;
		}

		/**
		 * Indicates whether the variable offset belongs to the global frame.
		 *
		 * @return {@code true} for a global variable
		 */
		public boolean isGlobal() {
			return global;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + variableOffset + ", " + array + ", " + global;
		}
	}

	/**
	 * Tuple for boolean operands.
	 */
	public static final class BooleanTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final boolean value;

		BooleanTuple(Opcode opcode, boolean value) {
			super(opcode);
			this.value = value;
		}

		/**
		 * Returns the boolean operand.
		 *
		 * @return boolean operand
		 */
		public boolean getValue() {
			return value;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + value;
		}
	}

	/**
	 * Tuple for sub/gsub against variable-backed values.
	 */
	public static final class SubstitutionVariableTuple extends VariableTuple {
		private static final long serialVersionUID = 1L;
		private final boolean globalSubstitution;

		SubstitutionVariableTuple(Opcode opcode, long variableOffset, boolean global, boolean globalSubstitution) {
			super(opcode, variableOffset, global);
			this.globalSubstitution = globalSubstitution;
		}

		/**
		 * Indicates whether this substitution is global.
		 *
		 * @return {@code true} for {@code gsub}, {@code false} for {@code sub}
		 */
		public boolean isGlobalSubstitution() {
			return globalSubstitution;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + getVariableOffset() + ", " + isGlobal() + ", " + globalSubstitution;
		}
	}

	/**
	 * Tuple for a precompiled literal regular expression.
	 */
	public static final class RegexTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final String regex;
		private final Pattern pattern;

		RegexTuple(String regex, Pattern pattern) {
			super(Opcode.REGEXP);
			this.regex = regex;
			this.pattern = pattern;
		}

		/**
		 * Returns the original regular expression text.
		 *
		 * @return regular expression text
		 */
		public String getRegex() {
			return regex;
		}

		/**
		 * Returns the precompiled regular expression.
		 *
		 * @return compiled pattern
		 */
		public Pattern getPattern() {
			return pattern;
		}

		@Override
		public String toString() {
			return getOpcode().name() + stringArgument(regex) + patternArgument(pattern);
		}
	}

	/**
	 * Tuple for a class check.
	 */
	public static final class ClassTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final Class<?> type;

		ClassTuple(Class<?> type) {
			super(Opcode.CHECK_CLASS);
			this.type = type;
		}

		/**
		 * Returns the required runtime type.
		 *
		 * @return required class
		 */
		public Class<?> getType() {
			return type;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + type;
		}
	}

	/**
	 * Tuple for function definitions.
	 */
	public static final class FunctionTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final String functionName;
		private final long numFormalParams;

		FunctionTuple(String functionName, long numFormalParams) {
			super(Opcode.FUNCTION);
			this.functionName = functionName;
			this.numFormalParams = numFormalParams;
		}

		/**
		 * Returns the function name.
		 *
		 * @return function name
		 */
		public String getFunctionName() {
			return functionName;
		}

		/**
		 * Returns the number of formal parameters.
		 *
		 * @return formal parameter count
		 */
		public long getNumFormalParams() {
			return numFormalParams;
		}

		@Override
		public String toString() {
			return getOpcode().name() + stringArgument(functionName) + ", " + numFormalParams;
		}
	}

	/**
	 * Tuple for function calls.
	 */
	public static final class CallFunctionTuple extends AddressTuple {
		private static final long serialVersionUID = 1L;
		private transient Supplier<Address> addressSupplier;
		private final String functionName;
		private final long numFormalParams;
		private final long numActualParams;

		CallFunctionTuple(
				Supplier<Address> addressSupplier,
				String functionName,
				long numFormalParams,
				long numActualParams) {
			super(Opcode.CALL_FUNCTION, null);
			this.addressSupplier = addressSupplier;
			this.functionName = functionName;
			this.numFormalParams = numFormalParams;
			this.numActualParams = numActualParams;
		}

		@Override
		public Address getAddress() {
			Address address = super.getAddress();
			if (address == null && addressSupplier != null) {
				address = addressSupplier.get();
				setAddress(address);
			}
			return address;
		}

		@Override
		public void touch(List<Tuple> queue) {
			getAddress();
			super.touch(queue);
		}

		/**
		 * Returns the function name.
		 *
		 * @return function name
		 */
		public String getFunctionName() {
			return functionName;
		}

		/**
		 * Returns the number of formal parameters.
		 *
		 * @return formal parameter count
		 */
		public long getNumFormalParams() {
			return numFormalParams;
		}

		/**
		 * Returns the number of actual parameters at this call site.
		 *
		 * @return actual parameter count
		 */
		public long getNumActualParams() {
			return numActualParams;
		}

		@Override
		public String toString() {
			return getOpcode().name()
					+ ", "
					+ getAddress()
					+ stringArgument(functionName)
					+ ", "
					+ numFormalParams
					+ ", "
					+ numActualParams;
		}
	}

	/**
	 * Tuple for extension function invocations.
	 */
	public static final class ExtensionTuple extends Tuple {
		private static final long serialVersionUID = 1L;
		private final ExtensionFunction function;
		private final long argCount;
		private final boolean initial;

		ExtensionTuple(ExtensionFunction function, long argCount, boolean initial) {
			super(Opcode.EXTENSION);
			this.function = function;
			this.argCount = argCount;
			this.initial = initial;
		}

		/**
		 * Returns the extension function metadata.
		 *
		 * @return extension function
		 */
		public ExtensionFunction getFunction() {
			return function;
		}

		/**
		 * Returns the number of extension arguments.
		 *
		 * @return argument count
		 */
		public long getArgCount() {
			return argCount;
		}

		/**
		 * Indicates whether this tuple starts an extension call sequence.
		 *
		 * @return {@code true} for the initial extension call tuple
		 */
		public boolean isInitial() {
			return initial;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + function.getKeyword() + ", " + argCount + ", " + initial;
		}
	}
}
