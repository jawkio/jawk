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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

	/** Opcode the AVM executes for this tuple. */
	private final Opcode opcode;

	/** Source line this tuple was generated from, {@code -1} when unknown. */
	private int lineNumber = -1;

	/** Next tuple in execution order, {@code null} at the end of the stream. */
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
	 * Returns every jump/call address carried by this tuple.
	 *
	 * @return tuple addresses, or an empty list
	 */
	public List<Address> getAddresses() {
		Address address = getAddress();
		if (address == null) {
			return Collections.emptyList();
		}
		return Collections.singletonList(address);
	}

	/**
	 * Resolves deferred operands and validates resolved addresses.
	 *
	 * @param queue tuple queue used to validate address targets
	 */
	public void touch(List<Tuple> queue) {
		for (Address address : getAddresses()) {
			if (address.index() == -1) {
				throw new Error("address " + address + " is unresolved");
			}
			if (address.index() >= queue.size()) {
				throw new Error("address " + address + " doesn't resolve to an actual list element");
			}
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
	 * Tuple for discarding an expression-statement value after validating that it
	 * is scalar.
	 */
	public static final class ScalarPopTuple extends NoOperandTuple {
		private static final long serialVersionUID = 1L;

		ScalarPopTuple() {
			super(Opcode.POP);
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

		/** Literal long value pushed onto the operand stack. */
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

		/** Literal double value pushed onto the operand stack. */
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

		/** Literal string value pushed onto the operand stack. */
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

		/** Count operand of the opcode. */
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

		/** {@code true} when the redirection appends instead of truncating. */
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

		/** Long operand, interpreted by the opcode rather than by this tuple. */
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

		/** Jump target, repointed by the optimizer when tuples move. */
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

		/** Offset of the variable within its frame. */
		private final long variableOffset;

		/** {@code true} when the offset addresses the global frame. */
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

		/** Offset of the variable within its frame. */
		private final long variableOffset;

		/** {@code true} when the variable must be initialized as an array. */
		private final boolean array;

		/** {@code true} when the offset addresses the global frame. */
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

		/** Boolean operand of the opcode. */
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

		/** {@code true} for {@code gsub}, {@code false} for {@code sub}. */
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

		/** Original regular expression text, as written in the script. */
		private final String regex;

		/** Regular expression precompiled at parse time. */
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

		/** Runtime type the checked value must be an instance of. */
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

		/** Name of the function being defined. */
		private final String functionName;

		/** Number of formal parameters the function declares. */
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

		/** Name of the called function. */
		private final String functionName;

		/** Number of formal parameters the called function declares. */
		private final long numFormalParams;

		/** Number of arguments this call site evaluates. */
		private final long numActualParams;

		CallFunctionTuple(Supplier<Address> addressSupplier, String functionName, long numFormalParams,
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
				addressSupplier = null;
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
			return getOpcode().name() + ", " + getAddress() + stringArgument(functionName) + ", " + numFormalParams + ", "
					+ numActualParams;
		}
	}

	/**
	 * Runtime target metadata for a user-defined indirect function call.
	 */
	public static final class IndirectFunctionTarget implements Serializable {
		private static final long serialVersionUID = 1L;
		private transient Supplier<Address> addressSupplier;

		/** Function entry point, resolved from the supplier on first access. */
		private Address address;

		/** Number of formal parameters the target function declares. */
		private final long numFormalParams;

		/** Zero-based indexes of the formal parameters that are arrays. */
		private final Set<Integer> arrayParameterIndexes;

		/**
		 * Creates target metadata whose address is resolved during tuple
		 * post-processing.
		 *
		 * @param addressSupplierParam function entry-point supplier
		 * @param numFormalParamsParam formal parameter count
		 * @param arrayParameterIndexesParam zero-based array parameter indexes
		 */
		public IndirectFunctionTarget(Supplier<Address> addressSupplierParam, long numFormalParamsParam,
				Set<Integer> arrayParameterIndexesParam) {
			addressSupplier = addressSupplierParam;
			numFormalParams = numFormalParamsParam;
			arrayParameterIndexes = Collections.unmodifiableSet(new HashSet<Integer>(arrayParameterIndexesParam));
		}

		private void resolve() {
			if (address == null && addressSupplier != null) {
				address = addressSupplier.get();
				addressSupplier = null;
			}
		}

		/**
		 * Returns the resolved function entry point.
		 *
		 * @return function address
		 */
		public Address getAddress() {
			resolve();
			return address;
		}

		/**
		 * Returns the function's formal parameter count.
		 *
		 * @return formal parameter count
		 */
		public long getNumFormalParams() {
			return numFormalParams;
		}

		/**
		 * Returns whether the parameter at the supplied index is an array.
		 *
		 * @param index zero-based formal parameter index
		 * @return {@code true} when the formal parameter is an array
		 */
		public boolean isArrayParameter(int index) {
			return arrayParameterIndexes.contains(Integer.valueOf(index));
		}
	}

	/**
	 * Tuple for a function call whose target name is evaluated at runtime.
	 */
	public static final class IndirectCallTuple extends Tuple {
		private static final long serialVersionUID = 1L;

		/** User-defined functions the call site may resolve to, by name. */
		private final Map<String, IndirectFunctionTarget> userFunctions;

		/** Extension functions the call site may resolve to, by name. */
		private final Map<String, ExtensionFunction> extensionFunctions;

		/** Number of arguments this call site evaluates. */
		private final long numActualParams;

		/** Script source name this call site comes from, for runtime diagnostics. */
		private final String sourceName;

		/** Script source line this call site comes from, for runtime diagnostics. */
		private final int sourceLine;

		IndirectCallTuple(Map<String, IndirectFunctionTarget> userFunctionsParam,
				Map<String, ExtensionFunction> extensionFunctionsParam, long numActualParamsParam, String sourceNameParam,
				int sourceLineParam) {
			super(Opcode.INDIRECT_CALL);
			userFunctions = userFunctionsParam;
			extensionFunctions = extensionFunctionsParam;
			numActualParams = numActualParamsParam;
			sourceName = sourceNameParam;
			sourceLine = sourceLineParam;
		}

		@Override
		public void touch(List<Tuple> queue) {
			for (IndirectFunctionTarget target : userFunctions.values()) {
				target.resolve();
			}
			super.touch(queue);
		}

		@Override
		public List<Address> getAddresses() {
			List<Address> addresses = new ArrayList<Address>(userFunctions.size());
			for (IndirectFunctionTarget target : userFunctions.values()) {
				addresses.add(target.getAddress());
			}
			return addresses;
		}

		/**
		 * Returns the user-defined functions available to the call site.
		 *
		 * @return function target map
		 */
		@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The map is an immutable metadata snapshot shared by every indirect call tuple")
		public Map<String, IndirectFunctionTarget> getUserFunctions() {
			return userFunctions;
		}

		/**
		 * Returns the extension functions available to the call site.
		 *
		 * @return extension function map
		 */
		@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The map is an immutable metadata snapshot shared by every indirect call tuple")
		public Map<String, ExtensionFunction> getExtensionFunctions() {
			return extensionFunctions;
		}

		/**
		 * Returns the number of actual parameters evaluated by the call site.
		 *
		 * @return actual parameter count
		 */
		public long getNumActualParams() {
			return numActualParams;
		}

		/**
		 * Returns the source name for runtime diagnostics.
		 *
		 * @return source name
		 */
		public String getSourceName() {
			return sourceName;
		}

		/**
		 * Returns the source line for runtime diagnostics.
		 *
		 * @return source line
		 */
		public int getSourceLine() {
			return sourceLine;
		}

		@Override
		public String toString() {
			return getOpcode().name() + ", " + numActualParams;
		}
	}

	/**
	 * Tuple for extension function invocations.
	 */
	public static final class ExtensionTuple extends Tuple {
		private static final long serialVersionUID = 2L;

		/** Extension function this tuple invokes. */
		private final ExtensionFunction function;

		/** Number of arguments passed to the extension function. */
		private final long argCount;

		ExtensionTuple(ExtensionFunction function, long argCount) {
			super(Opcode.EXTENSION);
			this.function = function;
			this.argCount = argCount;
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

		@Override
		public String toString() {
			return getOpcode().name() + ", " + function.getKeyword() + ", " + argCount;
		}
	}

	/**
	 * Tuple carrying a diagnostic message that the interpreter prints to the
	 * warning stream when executed. The parser plants these before the
	 * instruction they describe, so warnings appear in runtime order, exactly
	 * where gawk would emit them.
	 */
	public static final class WarningTuple extends Tuple {
		private static final long serialVersionUID = 1L;

		/** Diagnostic text printed to the warning stream. */
		private final String message;

		WarningTuple(String message) {
			super(Opcode.WARNING);
			this.message = message;
		}

		/**
		 * Returns the warning message to print.
		 *
		 * @return warning text
		 */
		public String getMessage() {
			return message;
		}

		@Override
		public String toString() {
			return getOpcode().name() + stringArgument(message);
		}
	}
}
