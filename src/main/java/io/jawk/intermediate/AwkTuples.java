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

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.ext.ExtensionFunction;
import io.jawk.jrt.JRT;

/**
 * <p>
 * AwkTuples class.
 * </p>
 *
 * @author Danny Daglas
 */
public class AwkTuples implements Serializable {

	private static final long serialVersionUID = 3L;

	/** Address manager */
	private final AddressManager addressManager = new AddressManager();

	/** Description of the primary script source, used for runtime diagnostics. */
	private String sourceDescription;

	/**
	 * Records the description of the primary script source (typically its file
	 * name) so runtime diagnostics can point at it.
	 *
	 * @param sourceDescriptionParam script source description
	 */
	public void setSourceDescription(String sourceDescriptionParam) {
		this.sourceDescription = sourceDescriptionParam;
	}

	/**
	 * Returns the description of the primary script source.
	 *
	 * @return script source description, or {@code null} when unknown
	 */
	public String getSourceDescription() {
		return sourceDescription;
	}

	// made public to access static members of AwkTuples via Java Reflection

	// made public to be accessable via Java Reflection
	// (see toOpcodeString() method below)

	/**
	 * Override add() to populate the line number for each tuple,
	 * rather than polluting all the constructors with this assignment.
	 */
	/**
	 * The tuple queue intentionally uses an {@link ArrayList}. The address mapping
	 * logic stores tuple indexes (rather than node references) so that jump targets
	 * can be serialized and patched efficiently. A linked list would make every
	 * lookup O(n) and complicate address reassignment.
	 */
	private List<Tuple> queue = new ArrayList<Tuple>(100) {
		private static final long serialVersionUID = -6334362156408598578L;

		@Override
		public boolean add(Tuple t) {
			t.setLineNumber(linenoStack.peek());
			return super.add(t);
		}
	};

	/** Whether tuple post-processing has already been applied. */
	private boolean postProcessed;

	/** Whether optimization passes have already been applied. */
	private boolean optimized;

	/** Whether this tuple stream was produced by {@code compileExpression()}. */
	private boolean evalTupleStream;

	/**
	 * Address of the END blocks section, where a runtime {@code exit} jumps;
	 * {@code null} for expression streams. Property addresses are remapped
	 * explicitly by the optimizer and seeded as reachability roots, so they
	 * stay valid even when no tuple references them.
	 */
	private Address exitAddress;

	/**
	 * Address of the ENDFILE section, or {@code null} when the program has
	 * no BEGINFILE/ENDFILE rules.
	 */
	private Address endFileAddress;

	/**
	 * Address of the {@code NEXT_FILE} tuple that opens each input file, or
	 * {@code null} when the program does not use per-file input stepping.
	 */
	private Address nextFileAddress;

	/**
	 * <p>
	 * toOpcodeString.
	 * </p>
	 *
	 * @param opcode a int
	 * @return a {@link java.lang.String} object
	 */
	public static String toOpcodeString(int opcode) {
		return Opcode.fromId(opcode).name();
	}

	/**
	 * <p>
	 * pop.
	 * </p>
	 */
	public void pop() {
		queue.add(new Tuple.NoOperandTuple(Opcode.POP));
	}

	/**
	 * <p>
	 * push.
	 * </p>
	 *
	 * @param o a {@link java.lang.Object} object
	 */
	public void push(Object o) {
		if (o instanceof String) {
			queue.add(new Tuple.PushStringTuple(o.toString()));
		} else if (o instanceof Integer) {
			queue.add(new Tuple.PushLongTuple((long) (Integer) o));
		} else if (o instanceof Long) {
			queue.add(new Tuple.PushLongTuple((long) (Long) o));
		} else if (o instanceof Double) {
			queue.add(new Tuple.PushDoubleTuple((Double) o));
		}
	}

	/**
	 * <p>
	 * ifFalse.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 */
	public void ifFalse(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.IFFALSE, address));
	}

	/**
	 * <p>
	 * toNumber.
	 * </p>
	 */
	public void toNumber() {
		queue.add(new Tuple.NoOperandTuple(Opcode.TO_NUMBER));
	}

	/**
	 * <p>
	 * ifTrue.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 */
	public void ifTrue(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.IFTRUE, address));
	}

	/**
	 * <p>
	 * gotoAddress.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 */
	public void gotoAddress(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.GOTO, address));
	}

	/**
	 * <p>
	 * createAddress.
	 * </p>
	 *
	 * @param label a {@link java.lang.String} object
	 * @return a {@link io.jawk.intermediate.Address} object
	 */
	public Address createAddress(String label) {
		return addressManager.createAddress(label);
	}

	/**
	 * <p>
	 * address.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 * @return a {@link io.jawk.intermediate.AwkTuples} object
	 */
	public AwkTuples address(Address address) {
		addressManager.resolveAddress(address, queue.size());
		return this;
	}

	/**
	 * <p>
	 * nop.
	 * </p>
	 */
	public void nop() {
		queue.add(new Tuple.NoOperandTuple(Opcode.NOP));
	}

	/**
	 * <p>
	 * print.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void print(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.PRINT, numExprs));
	}

	/**
	 * <p>
	 * printToFile.
	 * </p>
	 *
	 * @param numExprs a int
	 * @param append a boolean
	 */
	public void printToFile(int numExprs, boolean append) {
		queue.add(new Tuple.CountAndAppendTuple(Opcode.PRINT_TO_FILE, numExprs, append));
	}

	/**
	 * <p>
	 * printToPipe.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printToPipe(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.PRINT_TO_PIPE, numExprs));
	}

	/**
	 * <p>
	 * printf.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printf(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.PRINTF, numExprs));
	}

	/**
	 * <p>
	 * printfToFile.
	 * </p>
	 *
	 * @param numExprs a int
	 * @param append a boolean
	 */
	public void printfToFile(int numExprs, boolean append) {
		queue.add(new Tuple.CountAndAppendTuple(Opcode.PRINTF_TO_FILE, numExprs, append));
	}

	/**
	 * <p>
	 * printfToPipe.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printfToPipe(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.PRINTF_TO_PIPE, numExprs));
	}

	/**
	 * <p>
	 * sprintf.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void sprintf(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.SPRINTF, numExprs));
	}

	/**
	 * <p>
	 * length.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void length(int numExprs) {
		queue.add(new Tuple.CountTuple(Opcode.LENGTH, numExprs));
	}

	/**
	 * <p>
	 * concat.
	 * </p>
	 */
	public void concat() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CONCAT));
	}

	/**
	 * <p>
	 * assign.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void assign(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.ASSIGN, offset, isGlobal));
	}

	/**
	 * <p>
	 * assignArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void assignArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.ASSIGN_ARRAY, offset, isGlobal));
	}

	/**
	 * Assigns a value to a stack-provided associative-array element.
	 */
	public void assignMapElement() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ASSIGN_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * assignAsInput.
	 * </p>
	 */
	public void assignAsInput() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ASSIGN_AS_INPUT));
	}

	/**
	 * Marks this tuple stream as an expression-eval program rather than a full
	 * AWK script. Eval tuple streams can use small tuple-level optimizations that
	 * are unsafe for the general case.
	 */
	public void markEvalTupleStream() {
		evalTupleStream = true;
	}

	/**
	 * <p>
	 * assignAsInputField.
	 * </p>
	 */
	public void assignAsInputField() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ASSIGN_AS_INPUT_FIELD));
	}

	/**
	 * <p>
	 * dereference.
	 * </p>
	 *
	 * @param offset a int
	 * @param isArray a boolean
	 * @param isGlobal a boolean
	 */
	public void dereference(int offset, boolean isArray, boolean isGlobal) {
		queue.add(new Tuple.DereferenceTuple(offset, isArray, isGlobal));
	}

	/**
	 * Emits a variable read that does not assign a blank value when the variable
	 * is still untyped.
	 * <p>
	 * This is used by extension functions such as gawk's {@code typeof()} that
	 * need the current lvalue state, not AWK's normal scalar autovivification side
	 * effect.
	 * </p>
	 *
	 * @param offset variable offset
	 * @param isGlobal whether the variable is global
	 */
	public void peekDereference(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.PEEK_DEREFERENCE, offset, isGlobal));
	}

	/**
	 * <p>
	 * plusEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void plusEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.PLUS_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * minusEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void minusEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.MINUS_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * multEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void multEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.MULT_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * divEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void divEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.DIV_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * modEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void modEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.MOD_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * powEq.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void powEq(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignTuple(Opcode.POW_EQ, offset, isGlobal));
	}

	/**
	 * <p>
	 * plusEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void plusEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.PLUS_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies {@code +=} to a stack-provided associative-array element.
	 */
	public void plusEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.PLUS_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * minusEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void minusEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.MINUS_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies {@code -=} to a stack-provided associative-array element.
	 */
	public void minusEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.MINUS_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * multEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void multEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.MULT_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies {@code *=} to a stack-provided associative-array element.
	 */
	public void multEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.MULT_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * divEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void divEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.DIV_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies {@code /=} to a stack-provided associative-array element.
	 */
	public void divEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.DIV_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * modEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void modEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.MOD_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies {@code %=} to a stack-provided associative-array element.
	 */
	public void modEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.MOD_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * powEqArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void powEqArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.CompoundAssignArrayTuple(Opcode.POW_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * Applies exponentiation assignment to a stack-provided associative-array
	 * element.
	 */
	public void powEqMapElement() {
		queue.add(new Tuple.CompoundAssignMapElementTuple(Opcode.POW_EQ_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * plusEqInputField.
	 * </p>
	 */
	public void plusEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.PLUS_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * minusEqInputField.
	 * </p>
	 */
	public void minusEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.MINUS_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * multEqInputField.
	 * </p>
	 */
	public void multEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.MULT_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * divEqInputField.
	 * </p>
	 */
	public void divEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.DIV_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * modEqInputField.
	 * </p>
	 */
	public void modEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.MOD_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * powEqInputField.
	 * </p>
	 */
	public void powEqInputField() {
		queue.add(new Tuple.CompoundAssignInputFieldTuple(Opcode.POW_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * srand.
	 * </p>
	 *
	 * @param num a int
	 */
	public void srand(int num) {
		queue.add(new Tuple.CountTuple(Opcode.SRAND, num));
	}

	/**
	 * <p>
	 * rand.
	 * </p>
	 */
	public void rand() {
		queue.add(new Tuple.NoOperandTuple(Opcode.RAND));
	}

	/**
	 * <p>
	 * intFunc.
	 * </p>
	 */
	public void intFunc() {
		queue.add(new Tuple.NoOperandTuple(Opcode.INTFUNC));
	}

	/**
	 * <p>
	 * sqrt.
	 * </p>
	 */
	public void sqrt() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SQRT));
	}

	/**
	 * <p>
	 * log.
	 * </p>
	 */
	public void log() {
		queue.add(new Tuple.NoOperandTuple(Opcode.LOG));
	}

	/**
	 * <p>
	 * exp.
	 * </p>
	 */
	public void exp() {
		queue.add(new Tuple.NoOperandTuple(Opcode.EXP));
	}

	/**
	 * <p>
	 * sin.
	 * </p>
	 */
	public void sin() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SIN));
	}

	/**
	 * <p>
	 * cos.
	 * </p>
	 */
	public void cos() {
		queue.add(new Tuple.NoOperandTuple(Opcode.COS));
	}

	/**
	 * <p>
	 * atan2.
	 * </p>
	 */
	public void atan2() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ATAN2));
	}

	/**
	 * <p>
	 * match.
	 * </p>
	 */
	public void match() {
		queue.add(new Tuple.NoOperandTuple(Opcode.MATCH));
	}

	/**
	 * <p>
	 * index.
	 * </p>
	 */
	public void index() {
		queue.add(new Tuple.NoOperandTuple(Opcode.INDEX));
	}

	/**
	 * <p>
	 * subForDollar0.
	 * </p>
	 *
	 * @param isGsub a boolean
	 */
	public void subForDollar0(boolean isGsub) {
		queue.add(new Tuple.BooleanTuple(Opcode.SUB_FOR_DOLLAR_0, isGsub));
	}

	/**
	 * <p>
	 * subForDollarReference.
	 * </p>
	 *
	 * @param isGsub a boolean
	 */
	public void subForDollarReference(boolean isGsub) {
		queue.add(new Tuple.BooleanTuple(Opcode.SUB_FOR_DOLLAR_REFERENCE, isGsub));
	}

	/**
	 * <p>
	 * subForVariable.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 * @param isGsub a boolean
	 */
	public void subForVariable(int offset, boolean isGlobal, boolean isGsub) {
		queue.add(new Tuple.SubstitutionVariableTuple(Opcode.SUB_FOR_VARIABLE, offset, isGlobal, isGsub));
	}

	/**
	 * <p>
	 * subForArrayReference.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 * @param isGsub a boolean
	 */
	public void subForArrayReference(int offset, boolean isGlobal, boolean isGsub) {
		queue.add(new Tuple.SubstitutionVariableTuple(Opcode.SUB_FOR_ARRAY_REFERENCE, offset, isGlobal, isGsub));
	}

	/**
	 * Applies {@code sub}/{@code gsub} to a stack-provided associative-array
	 * element.
	 *
	 * @param isGsub {@code true} for {@code gsub}, {@code false} for {@code sub}
	 */
	public void subForMapReference(boolean isGsub) {
		queue.add(new Tuple.BooleanTuple(Opcode.SUB_FOR_MAP_REFERENCE, isGsub));
	}

	/**
	 * <p>
	 * split.
	 * </p>
	 *
	 * @param numargs a int
	 */
	public void split(int numargs) {
		queue.add(new Tuple.CountTuple(Opcode.SPLIT, numargs));
	}

	/**
	 * <p>
	 * substr.
	 * </p>
	 *
	 * @param numargs a int
	 */
	public void substr(int numargs) {
		queue.add(new Tuple.CountTuple(Opcode.SUBSTR, numargs));
	}

	/**
	 * <p>
	 * tolower.
	 * </p>
	 */
	public void tolower() {
		queue.add(new Tuple.NoOperandTuple(Opcode.TOLOWER));
	}

	/**
	 * <p>
	 * toupper.
	 * </p>
	 */
	public void toupper() {
		queue.add(new Tuple.NoOperandTuple(Opcode.TOUPPER));
	}

	/**
	 * <p>
	 * system.
	 * </p>
	 */
	public void system() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SYSTEM));
	}

	/**
	 * <p>
	 * swap.
	 * </p>
	 */
	public void swap() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SWAP));
	}

	/**
	 * <p>
	 * add.
	 * </p>
	 */
	public void add() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ADD));
	}

	/**
	 * <p>
	 * subtract.
	 * </p>
	 */
	public void subtract() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SUBTRACT));
	}

	/**
	 * <p>
	 * multiply.
	 * </p>
	 */
	public void multiply() {
		queue.add(new Tuple.NoOperandTuple(Opcode.MULTIPLY));
	}

	/**
	 * <p>
	 * divide.
	 * </p>
	 */
	public void divide() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DIVIDE));
	}

	/**
	 * <p>
	 * mod.
	 * </p>
	 */
	public void mod() {
		queue.add(new Tuple.NoOperandTuple(Opcode.MOD));
	}

	/**
	 * <p>
	 * pow.
	 * </p>
	 */
	public void pow() {
		queue.add(new Tuple.NoOperandTuple(Opcode.POW));
	}

	/**
	 * <p>
	 * inc.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void inc(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.INC, offset, isGlobal));
	}

	/**
	 * <p>
	 * dec.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void dec(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.DEC, offset, isGlobal));
	}

	/**
	 * <p>
	 * postInc.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void postInc(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.POSTINC, offset, isGlobal));
	}

	/**
	 * <p>
	 * postDec.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void postDec(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.POSTDEC, offset, isGlobal));
	}

	/**
	 * <p>
	 * incArrayRef.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void incArrayRef(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.INC_ARRAY_REF, offset, isGlobal));
	}

	/**
	 * Increments a stack-provided associative-array element reference.
	 */
	public void incMapRef() {
		queue.add(new Tuple.NoOperandTuple(Opcode.INC_MAP_REF));
	}

	/**
	 * <p>
	 * decArrayRef.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void decArrayRef(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.DEC_ARRAY_REF, offset, isGlobal));
	}

	/**
	 * Decrements a stack-provided associative-array element reference.
	 */
	public void decMapRef() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DEC_MAP_REF));
	}

	/**
	 * <p>
	 * incDollarRef.
	 * </p>
	 */
	public void incDollarRef() {
		queue.add(new Tuple.NoOperandTuple(Opcode.INC_DOLLAR_REF));
	}

	/**
	 * <p>
	 * decDollarRef.
	 * </p>
	 */
	public void decDollarRef() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DEC_DOLLAR_REF));
	}

	/**
	 * <p>
	 * dup.
	 * </p>
	 */
	public void dup() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DUP));
	}

	/**
	 * <p>
	 * not.
	 * </p>
	 */
	public void not() {
		queue.add(new Tuple.NoOperandTuple(Opcode.NOT));
	}

	/**
	 * <p>
	 * negate.
	 * </p>
	 */
	public void negate() {
		queue.add(new Tuple.NoOperandTuple(Opcode.NEGATE));
	}

	/**
	 * <p>
	 * unary plus.
	 * </p>
	 */
	public void unaryPlus() {
		queue.add(new Tuple.NoOperandTuple(Opcode.UNARY_PLUS));
	}

	/**
	 * <p>
	 * cmpEq.
	 * </p>
	 */
	public void cmpEq() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CMP_EQ));
	}

	/**
	 * <p>
	 * cmpLt.
	 * </p>
	 */
	public void cmpLt() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CMP_LT));
	}

	/**
	 * <p>
	 * cmpGt.
	 * </p>
	 */
	public void cmpGt() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CMP_GT));
	}

	/**
	 * <p>
	 * matches.
	 * </p>
	 */
	public void matches() {
		queue.add(new Tuple.NoOperandTuple(Opcode.MATCHES));
	}

	/**
	 * <p>
	 * dereferenceArray.
	 * </p>
	 */
	public void dereferenceArray() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DEREF_ARRAY));
	}

	/**
	 * Looks up an associative-array element without creating a blank entry when
	 * the key is missing.
	 */
	public void peekArrayElement() {
		queue.add(new Tuple.NoOperandTuple(Opcode.PEEK_ARRAY_ELEMENT));
	}

	/**
	 * Dereferences an associative-array element as a nested array, creating it if
	 * needed.
	 */
	public void ensureArrayElement() {
		queue.add(new Tuple.NoOperandTuple(Opcode.ENSURE_ARRAY_ELEMENT));
	}

	/**
	 * <p>
	 * key list.
	 * </p>
	 */
	public void keylist() {
		queue.add(new Tuple.NoOperandTuple(Opcode.KEYLIST));
	}

	/**
	 * <p>
	 * isEmptyList.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 */
	public void isEmptyList(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.IS_EMPTY_KEYLIST, address));
	}

	/**
	 * <p>
	 * getFirstAndRemoveFromList.
	 * </p>
	 */
	public void getFirstAndRemoveFromList() {
		queue.add(new Tuple.NoOperandTuple(Opcode.GET_FIRST_AND_REMOVE_FROM_KEYLIST));
	}

	/**
	 * <p>
	 * checkClass.
	 * </p>
	 *
	 * @param cls a {@link java.lang.Class} object
	 * @return a boolean
	 */
	public boolean checkClass(Class<?> cls) {
		queue.add(new Tuple.ClassTuple(cls));
		return true;
	}

	/**
	 * <p>
	 * getInputField.
	 * </p>
	 */
	public void getInputField() {
		queue.add(new Tuple.NoOperandTuple(Opcode.GET_INPUT_FIELD));
	}

	/**
	 * <p>
	 * getInputField.
	 * </p>
	 *
	 * @param fieldIndex a long
	 */
	public void getInputField(long fieldIndex) {
		queue.add(new Tuple.InputFieldTuple(fieldIndex));
	}

	/**
	 * <p>
	 * consumeInput.
	 * </p>
	 *
	 * @param address a {@link io.jawk.intermediate.Address} object
	 */
	public void consumeInput(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.CONSUME_INPUT, address));
	}

	/**
	 * <p>
	 * getlineInput.
	 * </p>
	 */
	public void getlineInput() {
		queue.add(new Tuple.NoOperandTuple(Opcode.GETLINE_INPUT));
	}

	/**
	 * <p>
	 * getlineInputToTarget.
	 * </p>
	 */
	public void getlineInputToTarget() {
		queue.add(new Tuple.NoOperandTuple(Opcode.GETLINE_INPUT_TO_TARGET));
	}

	/**
	 * <p>
	 * useAsFileInput.
	 * </p>
	 */
	public void useAsFileInput() {
		queue.add(new Tuple.NoOperandTuple(Opcode.USE_AS_FILE_INPUT));
	}

	/**
	 * <p>
	 * useAsCommandInput.
	 * </p>
	 */
	public void useAsCommandInput() {
		queue.add(new Tuple.NoOperandTuple(Opcode.USE_AS_COMMAND_INPUT));
	}

	/**
	 * <p>
	 * nfOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void nfOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.NF_OFFSET, offset));
	}

	/**
	 * <p>
	 * nrOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void nrOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.NR_OFFSET, offset));
	}

	/**
	 * <p>
	 * fnrOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void fnrOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.FNR_OFFSET, offset));
	}

	/**
	 * <p>
	 * fsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void fsOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.FS_OFFSET, offset));
	}

	/**
	 * <p>
	 * rsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rsOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.RS_OFFSET, offset));
	}

	/**
	 * <p>
	 * ofsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void ofsOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.OFS_OFFSET, offset));
	}

	/**
	 * <p>
	 * orsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void orsOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.ORS_OFFSET, offset));
	}

	/**
	 * <p>
	 * rstartOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rstartOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.RSTART_OFFSET, offset));
	}

	/**
	 * <p>
	 * rlengthOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rlengthOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.RLENGTH_OFFSET, offset));
	}

	/**
	 * <p>
	 * filenameOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void filenameOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.FILENAME_OFFSET, offset));
	}

	/**
	 * <p>
	 * subsepOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void subsepOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.SUBSEP_OFFSET, offset));
	}

	/**
	 * <p>
	 * convfmtOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void convfmtOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.CONVFMT_OFFSET, offset));
	}

	/**
	 * <p>
	 * ofmtOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void ofmtOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.OFMT_OFFSET, offset));
	}

	/**
	 * <p>
	 * environOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void environOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.ENVIRON_OFFSET, offset));
	}

	/**
	 * Emits the tuple that runs the extension beforeStart hooks, placed at the
	 * end of the preamble.
	 */
	public void beforeStartHooks() {
		queue.add(new Tuple.NoOperandTuple(Opcode.BEFORE_START_HOOKS));
	}

	/**
	 * Emits the tuple populating the SYMTAB array.
	 *
	 * @param offset offset of the SYMTAB global
	 */
	public void updateSymtab(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.UPDATE_SYMTAB, offset));
	}

	/**
	 * Emits the tuple populating the FUNCTAB array.
	 *
	 * @param offset offset of the FUNCTAB global
	 */
	public void updateFunctab(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.UPDATE_FUNCTAB, offset));
	}

	/**
	 * <p>
	 * argcOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void argcOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.ARGC_OFFSET, offset));
	}

	/**
	 * <p>
	 * argvOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void argvOffset(int offset) {
		queue.add(new Tuple.LongTuple(Opcode.ARGV_OFFSET, offset));
	}

	// JRT-managed special variable helpers
	/** Pushes the current value of {@code NF} onto the operand stack. */
	public void pushNF() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_NF));
	}

	/** Assigns the top-of-stack value to {@code NF}. */
	public void assignNF() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_NF));
	}

	/** Pushes the current value of {@code NR} onto the operand stack. */
	public void pushNR() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_NR));
	}

	/** Assigns the top-of-stack value to {@code NR}. */
	public void assignNR() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_NR));
	}

	/** Pushes the current value of {@code FNR} onto the operand stack. */
	public void pushFNR() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_FNR));
	}

	/** Assigns the top-of-stack value to {@code FNR}. */
	public void assignFNR() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_FNR));
	}

	/** Pushes the current value of {@code FS} onto the operand stack. */
	public void pushFS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_FS));
	}

	/** Assigns the top-of-stack value to {@code FS}. */
	public void assignFS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_FS));
	}

	/**
	 * Emits a tuple pushing the value of IGNORECASE.
	 */
	public void pushIGNORECASE() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_IGNORECASE));
	}

	/**
	 * Emits a tuple assigning the top of the stack to IGNORECASE.
	 */
	public void assignIGNORECASE() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_IGNORECASE));
	}

	/**
	 * Emits the tuple pushing the value of ERRNO, managed by the JRT.
	 */
	public void pushERRNO() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_ERRNO));
	}

	/**
	 * Emits the tuple assigning the top of the stack to ERRNO, managed by the
	 * JRT.
	 */
	public void assignERRNO() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_ERRNO));
	}

	/**
	 * Emits the tuple pushing the value of ARGIND, managed by the JRT.
	 */
	public void pushARGIND() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_ARGIND));
	}

	/**
	 * Emits the tuple assigning the top of the stack to ARGIND, managed by
	 * the JRT.
	 */
	public void assignARGIND() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_ARGIND));
	}

	/** Pushes the current value of {@code RS} onto the operand stack. */
	public void pushRS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_RS));
	}

	/** Assigns the top-of-stack value to {@code RS}. */
	public void assignRS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_RS));
	}

	/** Pushes the current value of {@code OFS} onto the operand stack. */
	public void pushOFS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_OFS));
	}

	/** Assigns the top-of-stack value to {@code OFS}. */
	public void assignOFS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_OFS));
	}

	/** Pushes the current value of {@code ORS} onto the operand stack. */
	public void pushORS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_ORS));
	}

	/** Assigns the top-of-stack value to {@code ORS}. */
	public void assignORS() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_ORS));
	}

	/** Pushes the current value of {@code RSTART} onto the operand stack. */
	public void pushRSTART() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_RSTART));
	}

	/** Assigns the top-of-stack value to {@code RSTART}. */
	public void assignRSTART() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_RSTART));
	}

	/** Pushes the current value of {@code RLENGTH} onto the operand stack. */
	public void pushRLENGTH() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_RLENGTH));
	}

	/** Assigns the top-of-stack value to {@code RLENGTH}. */
	public void assignRLENGTH() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_RLENGTH));
	}

	/** Pushes the current value of {@code FILENAME} onto the operand stack. */
	public void pushFILENAME() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_FILENAME));
	}

	/** Assigns the top-of-stack value to {@code FILENAME}. */
	public void assignFILENAME() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_FILENAME));
	}

	/** Pushes the current value of {@code SUBSEP} onto the operand stack. */
	public void pushSUBSEP() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_SUBSEP));
	}

	/** Assigns the top-of-stack value to {@code SUBSEP}. */
	public void assignSUBSEP() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_SUBSEP));
	}

	/** Pushes the current value of {@code CONVFMT} onto the operand stack. */
	public void pushCONVFMT() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_CONVFMT));
	}

	/** Assigns the top-of-stack value to {@code CONVFMT}. */
	public void assignCONVFMT() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_CONVFMT));
	}

	/** Pushes the current value of {@code OFMT} onto the operand stack. */
	public void pushOFMT() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_OFMT));
	}

	/** Assigns the top-of-stack value to {@code OFMT}. */
	public void assignOFMT() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_OFMT));
	}

	/** Pushes the current value of {@code ARGC} onto the operand stack. */
	public void pushARGC() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.PUSH_ARGC));
	}

	/** Assigns the top-of-stack value to {@code ARGC}. */
	public void assignARGC() {
		queue.add(new Tuple.BuiltinVarTuple(Opcode.ASSIGN_ARGC));
	}

	/**
	 * <p>
	 * applyRS.
	 * </p>
	 */
	public void applyRS() {
		queue.add(new Tuple.NoOperandTuple(Opcode.APPLY_RS));
	}

	/**
	 * <p>
	 * function.
	 * </p>
	 *
	 * @param funcName a {@link java.lang.String} object
	 * @param numFormalParams a int
	 */
	public void function(String funcName, int numFormalParams) {
		queue.add(new Tuple.FunctionTuple(funcName, numFormalParams));
	}

	/**
	 * <p>
	 * callFunction.
	 * </p>
	 *
	 * @param addressSupplier supplier resolving the function's entry point
	 * @param funcName a {@link java.lang.String} object
	 * @param numFormalParams a int
	 * @param numActualParams a int
	 */
	public void callFunction(
			Supplier<Address> addressSupplier,
			String funcName,
			int numFormalParams,
			int numActualParams) {
		queue.add(new Tuple.CallFunctionTuple(addressSupplier, funcName, numFormalParams, numActualParams));
	}

	/**
	 * Emits a tuple that prints a diagnostic message to the warning stream when
	 * executed. Planted by the parser just before the instruction it describes,
	 * so the warning appears in runtime order, exactly where gawk emits it.
	 *
	 * @param message warning text to print
	 */
	public void warning(String message) {
		queue.add(new Tuple.WarningTuple(message));
	}

	/**
	 * <p>
	 * setReturnResult.
	 * </p>
	 */
	public void setReturnResult() {
		queue.add(new Tuple.NoOperandTuple(Opcode.SET_RETURN_RESULT));
	}

	/**
	 * <p>
	 * returnFromFunction.
	 * </p>
	 */
	public void returnFromFunction() {
		queue.add(new Tuple.NoOperandTuple(Opcode.RETURN_FROM_FUNCTION));
	}

	/**
	 * <p>
	 * setNumGlobals.
	 * </p>
	 *
	 * @param numGlobals a int
	 */
	public void setNumGlobals(int numGlobals) {
		queue.add(new Tuple.CountTuple(Opcode.SET_NUM_GLOBALS, numGlobals));
	}

	/**
	 * <p>
	 * close.
	 * </p>
	 */
	public void close() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CLOSE));
	}

	/**
	 * <p>
	 * applySubsep.
	 * </p>
	 *
	 * @param count a int
	 */
	public void applySubsep(int count) {
		queue.add(new Tuple.CountTuple(Opcode.APPLY_SUBSEP, count));
	}

	/**
	 * <p>
	 * deleteArrayElement.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void deleteArrayElement(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.DELETE_ARRAY_ELEMENT, offset, isGlobal));
	}

	/**
	 * Deletes a stack-provided associative-array element.
	 */
	public void deleteMapElement() {
		queue.add(new Tuple.NoOperandTuple(Opcode.DELETE_MAP_ELEMENT));
	}

	/**
	 * <p>
	 * deleteArray.
	 * </p>
	 *
	 * @param offset a int
	 * @param isGlobal a boolean
	 */
	public void deleteArray(int offset, boolean isGlobal) {
		queue.add(new Tuple.VariableTuple(Opcode.DELETE_ARRAY, offset, isGlobal));
	}

	/**
	 * Registers the address of the END blocks section, so that a runtime
	 * {@code exit} statement can jump to it. A property of the tuple stream
	 * rather than a tuple: the interpreter reads it once when it installs
	 * the program.
	 *
	 * @param addr address of the END blocks section
	 */
	public void setExitAddress(Address addr) {
		exitAddress = addr;
	}

	/**
	 * Returns the address of the END blocks section, or {@code null} when
	 * the tuple stream is an expression stream with no END blocks.
	 *
	 * @return address of the END blocks section, or {@code null}
	 */
	public Address getExitAddress() {
		return exitAddress;
	}

	/**
	 * <p>
	 * setWithinEndBlocks.
	 * </p>
	 *
	 * @param b a boolean
	 */
	public void setWithinEndBlocks(boolean b) {
		queue.add(new Tuple.BooleanTuple(Opcode.SET_WITHIN_END_BLOCKS, b));
	}

	/**
	 * Registers the address of the ENDFILE section, so that a runtime
	 * {@code nextfile} statement can jump to it. A property of the tuple
	 * stream rather than a tuple: the interpreter reads it once when it
	 * installs the program.
	 *
	 * @param addr address of the ENDFILE section
	 */
	public void setEndFileAddress(Address addr) {
		endFileAddress = addr;
	}

	/**
	 * Returns the address of the ENDFILE section, or {@code null} when the
	 * program has no BEGINFILE/ENDFILE rules.
	 *
	 * @return address of the ENDFILE section, or {@code null}
	 */
	public Address getEndFileAddress() {
		return endFileAddress;
	}

	/**
	 * Registers the address of the {@code NEXT_FILE} tuple that opens each
	 * input file, so that a runtime {@code nextfile} statement can bypass
	 * the ENDFILE rules for input files that could not be opened. A property
	 * of the tuple stream rather than a tuple: the interpreter reads it once
	 * when it installs the program.
	 *
	 * @param addr address of the NEXT_FILE tuple
	 */
	public void setNextFileAddress(Address addr) {
		nextFileAddress = addr;
	}

	/**
	 * Returns the address of the {@code NEXT_FILE} tuple that opens each
	 * input file, or {@code null} when the program does not use per-file
	 * input stepping.
	 *
	 * @return address of the NEXT_FILE tuple, or {@code null}
	 */
	public Address getNextFileAddress() {
		return nextFileAddress;
	}

	/**
	 * Emits the tuple advancing the main input to the next input file, or
	 * jumping to the given address when no input file remains.
	 *
	 * @param address address to jump to when no more input files remain
	 */
	public void nextFile(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.NEXT_FILE, address));
	}

	/**
	 * Emits the tuple consuming one record of the current input file only,
	 * jumping to the given address at end of the current file.
	 *
	 * @param address address to jump to at end of the current input file
	 */
	public void consumeFileInput(Address address) {
		queue.add(new Tuple.AddressTuple(Opcode.CONSUME_FILE_INPUT, address));
	}

	/**
	 * Emits the tuple executing the {@code nextfile} statement at runtime.
	 */
	public void execNextfile() {
		queue.add(new Tuple.NoOperandTuple(Opcode.EXEC_NEXTFILE));
	}

	/**
	 * <p>
	 * exitWithCode.
	 * </p>
	 */
	public void exitWithCode() {
		queue.add(new Tuple.NoOperandTuple(Opcode.EXIT_WITH_CODE));
	}

	/**
	 * <p>
	 * exitWithCode.
	 * </p>
	 */
	public void exitWithoutCode() {
		queue.add(new Tuple.NoOperandTuple(Opcode.EXIT_WITHOUT_CODE));
	}

	/**
	 * <p>
	 * regexp.
	 * </p>
	 *
	 * @param regexpStr a {@link java.lang.String} object
	 */
	public void regexp(String regexpStr) {
		// For literal regexes (created by RegexpAst), precompile the Pattern
		// and store it alongside the original string to skip runtime compilation.
		Pattern precompiled = Pattern.compile(regexpStr);
		queue.add(new Tuple.RegexTuple(regexpStr, precompiled));
	}

	/**
	 * <p>
	 * regexpPair.
	 * </p>
	 */
	public void conditionPair() {
		queue.add(new Tuple.NoOperandTuple(Opcode.CONDITION_PAIR));
	}

	/**
	 * <p>
	 * isIn.
	 * </p>
	 */
	public void isIn() {
		queue.add(new Tuple.NoOperandTuple(Opcode.IS_IN));
	}

	/**
	 * Emits a tuple that pushes the current script context onto the stack.
	 */
	public void scriptThis() {
		queue.add(new Tuple.NoOperandTuple(Opcode.THIS));
	}

	/**
	 * Emits an extension invocation tuple.
	 *
	 * @param function metadata describing the extension method to invoke
	 * @param paramCount number of arguments supplied for the call
	 * @param isInitial {@code true} when this tuple opens an extension call sequence
	 */
	public void extension(ExtensionFunction function, int paramCount, boolean isInitial) {
		queue.add(new Tuple.ExtensionTuple(function, paramCount, isInitial));
	}

	/**
	 * Dumps the queued tuples to the provided {@link PrintStream}.
	 *
	 * @param ps destination stream for the tuple listing
	 */
	public void dump(PrintStream ps) {
		ps.println("(intermediate serialVersionUID = " + serialVersionUID + ")");
		ps.println();
		for (int i = 0; i < queue.size(); i++) {
			Address address = addressManager.getAddress(i);
			if (address == null) {
				ps.println(i + " : " + queue.get(i));
			} else {
				ps.println(i + " : [" + address + "] : " + queue.get(i));
			}
		}
	}

	/**
	 * <p>
	 * top.
	 * </p>
	 *
	 * @return a {@link io.jawk.intermediate.PositionTracker} object
	 */
	public PositionTracker top() {
		return new PositionTracker(queue);
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
		if (postProcessed) {
			return;
		}
		if (!queue.isEmpty() && queue.get(0).hasNext()) {
			postProcessed = true;
			return;
		}
		assignSequentialNextPointers();
		for (Tuple tuple : queue) {
			tuple.touch(queue);
		}
		postProcessed = true;
	}

	/**
	 * Performs tuple queue optimizations such as reachability pruning, redundant
	 * eval-global setup removal, and NOP collapsing.
	 * <p>
	 * This method is idempotent. Repeated invocations after a successful
	 * optimization run will have no additional effect.
	 * </p>
	 * <p>
	 * Peephole optimization happens at the tuple layer instead of during AST
	 * construction. Folding after parsing guarantees that any tuple-level
	 * transformations (for example, address resolution and extension hooks) have
	 * already run, and it keeps a single optimization toggle ({@code optimize()})
	 * for callers. Performing the work at the tuple layer also lets us recurse
	 * until no more changes occur without complicating the parser.
	 * </p>
	 */
	public void optimize() {
		if (optimized) {
			return;
		}
		if (!postProcessed) {
			postProcess();
		}
		boolean queueModified = removeRedundantEvalSetNumGlobals();
		queueModified |= peepholeOptimize();
		if (queueModified) {
			reprocessQueue();
		}
		simplifyControlFlow();
		optimizeQueue();
		optimized = true;
	}

	/**
	 * Removes the synthetic {@code SET_NUM_GLOBALS} prelude from eval tuple
	 * streams that never touch runtime-stack-backed variables or global metadata.
	 * <p>
	 * Expression compilation always emits the opcode up front, but field-only or
	 * JRT-special-only expressions can execute without initializing the AVM global
	 * frame. Dropping the tuple here keeps the runtime path lean while preserving
	 * the parser's simpler tuple construction flow.
	 * </p>
	 *
	 * @return {@code true} when a redundant eval {@code SET_NUM_GLOBALS} tuple was
	 *         removed
	 */
	private boolean removeRedundantEvalSetNumGlobals() {
		int setNumGlobalsIndex = -1;
		for (int i = 0; i < queue.size(); i++) {
			Opcode opcode = queue.get(i).getOpcode();
			if (opcode == null) {
				continue;
			}
			switch (opcode) {
			case SET_NUM_GLOBALS:
				if (setNumGlobalsIndex != -1) {
					return false;
				}
				setNumGlobalsIndex = i;
				break;
			default:
				if (requiresEvalGlobalFrame(opcode)) {
					return false;
				}
				break;
			}
		}
		if (!evalTupleStream || setNumGlobalsIndex < 0) {
			return false;
		}

		int[] indexMapping = new int[queue.size()];
		for (int i = 0, nextIndex = 0; i < queue.size(); i++) {
			if (i == setNumGlobalsIndex) {
				indexMapping[i] = nextIndex;
			} else {
				indexMapping[i] = nextIndex++;
			}
		}
		queue.remove(setNumGlobalsIndex);
		remapAddresses(indexMapping);
		return true;
	}

	private boolean peepholeOptimize() {
		// Keep running the local rewrite pass because one fold can expose another.
		// Example: PUSH 1, PUSH 2, ADD, NEGATE first becomes PUSH 3, NEGATE and
		// only the next pass can fold it to PUSH -3.
		boolean modified = false;
		boolean passModified;
		do {
			passModified = peepholeOptimizePass();
			modified |= passModified;
		} while (passModified);
		return modified;
	}

	private boolean peepholeOptimizePass() {
		int originalSize = queue.size();
		if (originalSize < 2) {
			return false;
		}

		List<Tuple> original = new ArrayList<Tuple>(queue);
		int[] indexMapping = new int[originalSize];
		Arrays.fill(indexMapping, -1);
		List<Tuple> optimizedQueue = new ArrayList<Tuple>(originalSize);
		boolean[] isAddressTarget = addressTargets(original, originalSize);

		boolean modified = false;
		int oldIndex = 0;
		int newIndex = 0;
		while (oldIndex < originalSize) {
			Tuple tuple = original.get(oldIndex);
			// If an earlier rewrite already happened in this pass, wait for the
			// next pass before collapsing concat runs. That gives literal folding
			// priority so fully constant chains become one PUSH_STRING instead of a
			// partially folded PUSH_STRING plus MULTI_CONCAT.
			ConcatRun concatRun = !modified ? concatRun(original, isAddressTarget, oldIndex) : null;
			if (concatRun != null) {
				// Chained concatenations compile as a run of binary CONCAT tuples
				// after all operands have been pushed. Collapse that postfix run into
				// one counted MULTI_CONCAT, e.g. CONCAT, CONCAT, CONCAT ->
				// MULTI_CONCAT 4.
				Tuple replacement = createMultiConcat(concatRun.itemCount, tuple.getLineNumber());
				optimizedQueue.add(replacement);
				mapFoldedRange(indexMapping, oldIndex, concatRun.tupleCount, newIndex);
				oldIndex += concatRun.tupleCount;
				newIndex++;
				modified = true;
				continue;
			}

			if (tuple.getOpcode() == Opcode.ASSIGN && (oldIndex + 1) < originalSize) {
				Tuple nextTuple = original.get(oldIndex + 1);
				// Statement assignments compile as ASSIGN followed by POP because
				// ASSIGN normally leaves the assigned value on the stack for
				// expression contexts such as print (a = 1). When the result is
				// discarded immediately, replace both opcodes with ASSIGN_NOPUSH
				// unless the POP itself is a branch target. Branches that land on
				// the POP must continue to skip the assignment and only discard the
				// already-computed expression result.
				if (nextTuple.getOpcode() == Opcode.POP && !isAddressTarget[oldIndex + 1]) {
					Tuple replacement = createAssignNoPush(tuple);
					optimizedQueue.add(replacement);
					mapFoldedRange(indexMapping, oldIndex, 2, newIndex);
					oldIndex += 2;
					newIndex++;
					modified = true;
					continue;
				}
			}

			Object literal = literalValue(tuple);
			if (literal != null) {
				if ((oldIndex + 1) < originalSize) {
					Tuple nextTuple = original.get(oldIndex + 1);
					if (nextTuple.getOpcode() == Opcode.GET_INPUT_FIELD) {
						// Replace PUSH literal + GET_INPUT_FIELD with the constant-field
						// opcode so $1, $2, etc. do not need a stack round trip for the
						// field index.
						long fieldIndex = JRT.toLong(literal);
						Tuple replacement = createGetInputFieldConst(
								fieldIndex,
								tuple.getLineNumber());
						optimizedQueue.add(replacement);
						mapFoldedRange(indexMapping, oldIndex, 2, newIndex);
						oldIndex += 2;
						newIndex++;
						modified = true;
						continue;
					}
				}
				if ((oldIndex + 2) < originalSize) {
					Tuple nextTuple = original.get(oldIndex + 1);
					Tuple opTuple = original.get(oldIndex + 2);
					Object secondLiteral = literalValue(nextTuple);
					if (secondLiteral != null) {
						Object folded = foldBinary(literal, secondLiteral, opTuple);
						if (folded != null) {
							// Fold two literal pushes followed by a pure binary operator
							// into a single literal push, e.g. PUSH 1, PUSH 2, ADD ->
							// PUSH 3.
							Tuple replacement = createLiteralPush(folded, tuple.getLineNumber());
							optimizedQueue.add(replacement);
							mapFoldedRange(indexMapping, oldIndex, 3, newIndex);
							oldIndex += 3;
							newIndex++;
							modified = true;
							continue;
						}
					}
				}
				if ((oldIndex + 1) < originalSize) {
					Tuple opTuple = original.get(oldIndex + 1);
					Object folded = foldUnary(literal, opTuple);
					if (folded != null) {
						// Fold one literal push followed by a pure unary operator into a
						// single literal push, e.g. PUSH 5, NEGATE -> PUSH -5.
						Tuple replacement = createLiteralPush(folded, tuple.getLineNumber());
						optimizedQueue.add(replacement);
						mapFoldedRange(indexMapping, oldIndex, 2, newIndex);
						oldIndex += 2;
						newIndex++;
						modified = true;
						continue;
					}
				}
			}

			optimizedQueue.add(tuple);
			indexMapping[oldIndex] = newIndex;
			oldIndex++;
			newIndex++;
		}

		if (!modified) {
			return false;
		}

		for (int i = 0; i < optimizedQueue.size(); i++) {
			queue.set(i, optimizedQueue.get(i));
		}
		for (int i = queue.size() - 1; i >= optimizedQueue.size(); i--) {
			queue.remove(i);
		}

		remapAddresses(indexMapping);
		return true;
	}

	private boolean[] addressTargets(List<Tuple> tuples, int tupleCount) {
		boolean[] targets = new boolean[tupleCount];
		for (Tuple tuple : tuples) {
			Address address = tuple.getAddress();
			if (address != null) {
				int index = address.index();
				if (index >= 0 && index < tupleCount) {
					targets[index] = true;
				}
			}
		}
		return targets;
	}

	private void mapFoldedRange(int[] indexMapping, int startIndex, int length, int newIndex) {
		for (int idx = 0; idx < length; idx++) {
			indexMapping[startIndex + idx] = newIndex;
		}
	}

	private ConcatRun concatRun(List<Tuple> original, boolean[] isAddressTarget, int oldIndex) {
		Tuple tuple = original.get(oldIndex);
		if (tuple.getOpcode() != Opcode.CONCAT || isAddressTarget[oldIndex]) {
			return null;
		}

		int itemCount = 2;
		int tupleCount = 1;
		int currentIndex = oldIndex + 1;
		while (currentIndex < original.size()
				&& original.get(currentIndex).getOpcode() == Opcode.CONCAT
				&& !isAddressTarget[currentIndex]) {
			itemCount++;
			tupleCount++;
			currentIndex++;
		}

		if (tupleCount < 2) {
			return null;
		}
		return new ConcatRun(tupleCount, itemCount);
	}

	private Object literalValue(Tuple tuple) {
		switch (tuple.getOpcode()) {
		case PUSH_LONG:
			return Long.valueOf(((Tuple.PushLongTuple) tuple).getValue());
		case PUSH_DOUBLE:
			return Double.valueOf(((Tuple.PushDoubleTuple) tuple).getValue());
		case PUSH_STRING:
			return ((Tuple.PushStringTuple) tuple).getValue();
		default:
			return null;
		}
	}

	private Object foldBinary(Object left, Object right, Tuple operation) {
		Opcode opcode = operation.getOpcode();
		if (opcode == null) {
			return null;
		}
		switch (opcode) {
		case ADD: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = d1 + d2;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case SUBTRACT: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = d1 - d2;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case MULTIPLY: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = d1 * d2;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case DIVIDE: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = d1 / d2;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case MOD: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = d1 % d2;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case POW: {
			double d1 = JRT.toDouble(left);
			double d2 = JRT.toDouble(right);
			double ans = Math.pow(d1, d2);
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case CMP_EQ:
		case CMP_LT:
		case CMP_GT:
			// only numeric comparisons are compile-time constants: string
			// comparisons depend on the runtime IGNORECASE setting
			if (!(left instanceof Number) || !(right instanceof Number)) {
				return null;
			}
			return JRT.compare2(left, right, opcode == Opcode.CMP_EQ ? 0 : opcode == Opcode.CMP_LT ? -1 : 1) ?
					Long.valueOf(1L) : Long.valueOf(0L);
		case CONCAT:
			if (left instanceof String && right instanceof String) {
				return ((String) left) + ((String) right);
			}
			return null;
		default:
			return null;
		}
	}

	private Object foldUnary(Object literal, Tuple operation) {
		Opcode opcode = operation.getOpcode();
		if (opcode == null) {
			return null;
		}
		switch (opcode) {
		case NEGATE: {
			double value = JRT.toDouble(literal);
			double ans = -value;
			if (JRT.isActuallyLong(ans)) {
				return Long.valueOf((long) Math.rint(ans));
			}
			return Double.valueOf(ans);
		}
		case UNARY_PLUS: {
			double value = JRT.toDouble(literal);
			if (JRT.isActuallyLong(value)) {
				return Long.valueOf((long) Math.rint(value));
			}
			return Double.valueOf(value);
		}
		default:
			return null;
		}
	}

	private Tuple createLiteralPush(Object value, int lineNumber) {
		Tuple tuple;
		if (value instanceof Long) {
			tuple = new Tuple.PushLongTuple(((Long) value).longValue());
		} else if (value instanceof Integer) {
			tuple = new Tuple.PushLongTuple(((Integer) value).longValue());
		} else if (value instanceof Double) {
			tuple = new Tuple.PushDoubleTuple(((Double) value).doubleValue());
		} else if (value instanceof Number) {
			double d = ((Number) value).doubleValue();
			if (JRT.isActuallyLong(d)) {
				tuple = new Tuple.PushLongTuple((long) Math.rint(d));
			} else {
				tuple = new Tuple.PushDoubleTuple(d);
			}
		} else if (value instanceof String) {
			tuple = new Tuple.PushStringTuple((String) value);
		} else {
			throw new IllegalArgumentException("Unsupported literal value: " + value);
		}
		tuple.setLineNumber(lineNumber);
		return tuple;
	}

	private Tuple createAssignNoPush(Tuple tuple) {
		Tuple.VariableTuple variableTuple = (Tuple.VariableTuple) tuple;
		Tuple replacement = new Tuple.VariableTuple(
				Opcode.ASSIGN_NOPUSH,
				variableTuple.getVariableOffset(),
				variableTuple.isGlobal());
		replacement.setLineNumber(tuple.getLineNumber());
		return replacement;
	}

	private Tuple createGetInputFieldConst(long fieldIndex, int lineNumber) {
		Tuple tuple = new Tuple.InputFieldTuple(fieldIndex);
		tuple.setLineNumber(lineNumber);
		return tuple;
	}

	private Tuple createMultiConcat(int itemCount, int lineNumber) {
		Tuple tuple = new Tuple.CountTuple(Opcode.MULTI_CONCAT, itemCount);
		tuple.setLineNumber(lineNumber);
		return tuple;
	}

	private static final class ConcatRun {
		private final int tupleCount;
		private final int itemCount;

		private ConcatRun(int tupleCount, int itemCount) {
			this.tupleCount = tupleCount;
			this.itemCount = itemCount;
		}
	}

	private void remapAddresses(int[] indexMapping) {
		if (indexMapping.length == 0) {
			return;
		}
		Set<Address> processedAddresses = Collections.newSetFromMap(new IdentityHashMap<Address, Boolean>());
		for (Tuple tuple : queue) {
			remapAddress(tuple.getAddress(), indexMapping, processedAddresses);
		}
		// Property addresses may not be referenced by any tuple (e.g. after
		// jump threading rewired the loop-back GOTO), so they must be
		// remapped explicitly to stay valid.
		remapAddress(exitAddress, indexMapping, processedAddresses);
		remapAddress(endFileAddress, indexMapping, processedAddresses);
		remapAddress(nextFileAddress, indexMapping, processedAddresses);
		addressManager.remapIndexes(indexMapping);
	}

	private static void seedPropertyAddress(
			Address address,
			int size,
			boolean[] reachable,
			Deque<Integer> worklist) {
		if (address == null) {
			return;
		}
		int targetIndex = address.index();
		if (targetIndex >= 0 && targetIndex < size && !reachable[targetIndex]) {
			reachable[targetIndex] = true;
			worklist.addLast(targetIndex);
		}
	}

	private static void remapAddress(Address address, int[] indexMapping, Set<Address> processedAddresses) {
		if (address == null || !processedAddresses.add(address)) {
			return;
		}
		int oldIndex = address.index();
		if (oldIndex >= 0 && oldIndex < indexMapping.length) {
			int mappedIndex = indexMapping[oldIndex];
			if (mappedIndex < 0) {
				throw new Error("Address " + address + " references removed tuple " + oldIndex);
			}
			address.assignIndex(mappedIndex);
		}
	}

	private void reprocessQueue() {
		assignSequentialNextPointers();
		for (Tuple tuple : queue) {
			tuple.touch(queue);
		}
	}

	private boolean simplifyControlFlow() {
		boolean modified = false;
		boolean passModified;
		do {
			passModified = simplifyControlFlowPass();
			if (passModified) {
				reprocessQueue();
			}
			modified |= passModified;
		} while (passModified);
		return modified;
	}

	private boolean simplifyControlFlowPass() {
		int size = queue.size();
		if (size < 2) {
			return false;
		}

		boolean modified = false;
		boolean[] remove = new boolean[size];
		int[] redirectTargets = new int[size];
		int[] visitStamps = new int[size];
		int nextVisitStamp = 1;
		Arrays.fill(redirectTargets, -1);

		for (int i = 0; i < size; i++) {
			Tuple tuple = queue.get(i);
			Address address = tuple.getAddress();
			if (address != null) {
				int resolvedTarget = resolveJumpEquivalentIndex(
						address.index(),
						size,
						visitStamps,
						nextVisitStamp++);
				if (resolvedTarget >= 0 && resolvedTarget != address.index()) {
					addressManager.reassignAddress(address, resolvedTarget);
					modified = true;
				}
			}

			switch (tuple.getOpcode()) {
			case NOP: {
				int redirectTarget = resolveJumpEquivalentIndex(
						i + 1,
						size,
						visitStamps,
						nextVisitStamp++);
				if (redirectTarget >= 0) {
					remove[i] = true;
					redirectTargets[i] = redirectTarget;
					modified = true;
				}
				break;
			}
			case GOTO: {
				int target = resolveJumpEquivalentIndex(
						tuple.getAddress().index(),
						size,
						visitStamps,
						nextVisitStamp++);
				int fallthroughTarget = resolveJumpEquivalentIndex(
						i + 1,
						size,
						visitStamps,
						nextVisitStamp++);
				if (target >= 0 && target == fallthroughTarget) {
					remove[i] = true;
					redirectTargets[i] = fallthroughTarget;
					modified = true;
				}
				break;
			}
			default:
				break;
			}
		}

		if (!modified) {
			return false;
		}

		boolean anyRemoved = false;
		for (boolean removeTuple : remove) {
			if (removeTuple) {
				anyRemoved = true;
				break;
			}
		}
		if (!anyRemoved) {
			return true;
		}

		int[] indexMapping = new int[size];
		Arrays.fill(indexMapping, -1);
		int nextIndex = 0;
		for (int i = 0; i < size; i++) {
			if (!remove[i]) {
				indexMapping[i] = nextIndex++;
			}
		}
		for (int i = 0; i < size; i++) {
			if (remove[i] && redirectTargets[i] >= 0) {
				indexMapping[i] = indexMapping[redirectTargets[i]];
			}
		}

		compactQueue(remove);

		remapAddresses(indexMapping);
		return true;
	}

	private int resolveJumpEquivalentIndex(int index, int size, int[] visitStamps, int stamp) {
		if (index < 0 || index >= size) {
			return -1;
		}
		int current = index;
		while (current >= 0 && current < size && visitStamps[current] != stamp) {
			visitStamps[current] = stamp;
			Tuple tuple = queue.get(current);
			switch (tuple.getOpcode()) {
			case NOP:
				current++;
				break;
			case GOTO: {
				Address address = tuple.getAddress();
				if (address == null) {
					return current;
				}
				current = address.index();
				break;
			}
			default:
				return current;
			}
		}
		return -1;
	}

	private void assignSequentialNextPointers() {
		for (int i = 0; i < queue.size(); i++) {
			Tuple nextTuple = (i + 1) < queue.size() ? queue.get(i + 1) : null;
			queue.get(i).setNext(nextTuple);
		}
	}

	private void compactQueue(boolean[] remove) {
		ArrayList<Tuple> compactedQueue = new ArrayList<Tuple>(queue.size());
		for (int i = 0; i < remove.length; i++) {
			if (!remove[i]) {
				compactedQueue.add(queue.get(i));
			}
		}
		queue.clear();
		queue.addAll(compactedQueue);
	}

	private void optimizeQueue() {
		int size = queue.size();
		if (size <= 1) {
			return;
		}

		boolean[] reachable = new boolean[size];
		int[] referencesFromReachable = new int[size];

		Deque<Integer> worklist = new ArrayDeque<>();
		if (!queue.isEmpty()) {
			reachable[0] = true;
			worklist.add(0);
		}
		// The property addresses are runtime jump targets (exit, nextfile,
		// and the ENDFILE section it resumes at) that no tuple may reference:
		// treat them as reachability roots so their sections are never
		// eliminated as dead code.
		seedPropertyAddress(exitAddress, size, reachable, worklist);
		seedPropertyAddress(endFileAddress, size, reachable, worklist);
		seedPropertyAddress(nextFileAddress, size, reachable, worklist);

		while (!worklist.isEmpty()) {
			int index = worklist.removeFirst();
			Tuple tuple = queue.get(index);

			if (fallsThrough(tuple.getOpcode())) {
				Tuple nextTuple = tuple.getNext();
				if (nextTuple != null) {
					int nextIndex = index + 1;
					if (!reachable[nextIndex]) {
						reachable[nextIndex] = true;
						worklist.addLast(nextIndex);
					}
				}
			}

			Address address = tuple.getAddress();
			if (address != null) {
				int targetIndex = address.index();
				if (targetIndex < 0 || targetIndex >= size) {
					throw new Error("address " + address + " doesn't resolve to an actual list element");
				}
				referencesFromReachable[targetIndex]++;
				if (!reachable[targetIndex]) {
					reachable[targetIndex] = true;
					worklist.addLast(targetIndex);
				}
			}
		}

		for (int i = 0; i < size; i++) {
			if (!reachable[i] && referencesFromReachable[i] > 0) {
				reachable[i] = true;
				worklist.addLast(i);
			}
		}

		while (!worklist.isEmpty()) {
			int index = worklist.removeFirst();
			Tuple tuple = queue.get(index);

			if (fallsThrough(tuple.getOpcode())) {
				Tuple nextTuple = tuple.getNext();
				if (nextTuple != null) {
					int nextIndex = index + 1;
					if (!reachable[nextIndex]) {
						reachable[nextIndex] = true;
						worklist.addLast(nextIndex);
					}
				}
			}

			Address address = tuple.getAddress();
			if (address != null) {
				int targetIndex = address.index();
				if (targetIndex < 0 || targetIndex >= size) {
					throw new Error("address " + address + " doesn't resolve to an actual list element");
				}
				referencesFromReachable[targetIndex]++;
				if (!reachable[targetIndex]) {
					reachable[targetIndex] = true;
					worklist.addLast(targetIndex);
				}
			}
		}

		boolean anyRemoved = false;
		boolean[] remove = new boolean[size];
		for (int i = 0; i < size; i++) {
			if (!reachable[i]) {
				remove[i] = true;
				anyRemoved = true;
				continue;
			}
			Tuple tuple = queue.get(i);
			if (tuple.getOpcode() == Opcode.NOP && referencesFromReachable[i] == 0) {
				remove[i] = true;
				anyRemoved = true;
			}
		}

		if (!anyRemoved) {
			return;
		}

		int[] indexMapping = new int[size];
		int nextIndex = 0;
		for (int i = 0; i < size; i++) {
			if (remove[i]) {
				indexMapping[i] = -1;
			} else {
				indexMapping[i] = nextIndex++;
			}
		}

		compactQueue(remove);

		if (!queue.isEmpty()) {
			assignSequentialNextPointers();
		}

		remapAddresses(indexMapping);
	}

	private boolean fallsThrough(Opcode opcode) {
		if (opcode == null) {
			return true;
		}
		switch (opcode) {
		case GOTO:
		case EXIT_WITH_CODE:
		case EXIT_WITHOUT_CODE:
			return false;
		default:
			return true;
		}
	}

	/** Map of global variables offsets */
	private Map<String, Integer> globalVarOffsetMap = new HashMap<String, Integer>();

	/** Map of global arrays */
	private Map<String, Boolean> globalVarAarrayMap = new HashMap<String, Boolean>();

	/** List of user function names */
	private Set<String> functionNames = new HashSet<String>();

	/** Whether metadata collections are frozen for execution. */
	private boolean metadataFrozen;

	/**
	 * Accept a {variable_name -&gt; offset} mapping such that global variables can be
	 * assigned while processing name=value and filename command-line arguments.
	 *
	 * @param varname Name of the global variable
	 * @param offset What offset to use for the variable
	 * @param isArray Whether the variable is actually an array
	 */
	public void addGlobalVariableNameToOffsetMapping(String varname, int offset, boolean isArray) {
		ensureMetadataMutable();
		if (globalVarOffsetMap.get(varname) != null) {
			return;
		}
		globalVarOffsetMap.put(varname, offset);
		globalVarAarrayMap.put(varname, isArray);
	}

	/**
	 * Accept a set of function names from the parser. This is
	 * useful for invalidating name=value assignments from the
	 * command line parameters, either via -v arguments or
	 * passed into ARGV.
	 *
	 * @param names A set of function name strings.
	 */
	public void setFunctionNameSet(Set<String> names) {
		ensureMetadataMutable();
		// setFunctionNameSet is called with a keySet from
		// a HashMap as a parameter, which is Opcode.NOT
		// Serializable. Creating a new HashSet around
		// the parameter resolves the issue.
		// Otherwise, attempting to serialize this
		// object results in a NotSerializableEexception
		// being thrown because of functionNames field
		// being a keyset from a HashMap.
		this.functionNames = new HashSet<String>(names);
	}

	/**
	 * Freezes the tuple metadata after compilation so execution can reuse the
	 * published maps and sets without creating fresh unmodifiable wrappers.
	 * Repeated calls are ignored.
	 */
	public void freezeMetadata() {
		if (metadataFrozen) {
			return;
		}
		globalVarOffsetMap = freezeMap(globalVarOffsetMap);
		globalVarAarrayMap = freezeMap(globalVarAarrayMap);
		functionNames = freezeSet(functionNames);
		metadataFrozen = true;
	}

	/**
	 * <p>
	 * getGlobalVariableOffsetMap.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "freezeMetadata() replaces this field with an unmodifiable snapshot before compiled tuples are exposed")
	public Map<String, Integer> getGlobalVariableOffsetMap() {
		return globalVarOffsetMap;
	}

	/**
	 * <p>
	 * getGlobalVariableAarrayMap.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "freezeMetadata() replaces this field with an unmodifiable snapshot before compiled tuples are exposed")
	public Map<String, Boolean> getGlobalVariableAarrayMap() {
		return globalVarAarrayMap;
	}

	/**
	 * <p>
	 * getFunctionNameSet.
	 * </p>
	 *
	 * @return a {@link java.util.Set} object
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "freezeMetadata() replaces this field with an unmodifiable snapshot before compiled tuples are exposed")
	public Set<String> getFunctionNameSet() {
		return functionNames;
	}

	private void ensureMetadataMutable() {
		if (metadataFrozen) {
			throw new IllegalStateException("Tuple metadata is frozen.");
		}
	}

	private static <K, V> Map<K, V> freezeMap(Map<K, V> map) {
		if (map.isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new HashMap<K, V>(map));
	}

	private static <T> Set<T> freezeSet(Set<T> set) {
		if (set.isEmpty()) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new HashSet<T>(set));
	}

	private boolean requiresEvalGlobalFrame(Opcode opcode) {
		switch (opcode) {
		case ASSIGN:
		case ASSIGN_NOPUSH:
		case ASSIGN_ARRAY:
		case DEREFERENCE:
		case PEEK_DEREFERENCE:
		case PLUS_EQ:
		case MINUS_EQ:
		case MULT_EQ:
		case DIV_EQ:
		case MOD_EQ:
		case POW_EQ:
		case PLUS_EQ_ARRAY:
		case MINUS_EQ_ARRAY:
		case MULT_EQ_ARRAY:
		case DIV_EQ_ARRAY:
		case MOD_EQ_ARRAY:
		case POW_EQ_ARRAY:
		case CALL_FUNCTION:
			// extension calls read globals (e.g. IGNORECASE) and their
			// beforeStart hooks assign gawk-owned arrays
		case EXTENSION:
		case SET_RETURN_RESULT:
		case RETURN_FROM_FUNCTION:
		case MATCH:
		case DELETE_ARRAY_ELEMENT:
		case DELETE_ARRAY:
		case ENVIRON_OFFSET:
		case ARGC_OFFSET:
		case ARGV_OFFSET:
		case ASSIGN_ARGC:
		case PUSH_ARGC:
			return true;
		default:
			return false;
		}
	}

	/** linenumber stack ... */
	private Deque<Integer> linenoStack = new ArrayDeque<Integer>();

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
		linenoStack.push(lineno);
	}

	/**
	 * <p>
	 * popSourceLineNumber.
	 * </p>
	 *
	 * @param lineno a int
	 */
	public void popSourceLineNumber(int lineno) {
		linenoStack.pop();
	}

}
