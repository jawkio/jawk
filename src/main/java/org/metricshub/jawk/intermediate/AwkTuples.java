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
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.ext.ExtensionFunction;
import org.metricshub.jawk.jrt.JRT;

/**
 * <p>
 * AwkTuples class.
 * </p>
 *
 * @author Danny Daglas
 */
public class AwkTuples implements Serializable {

	private static final long serialVersionUID = 2L;

	/** Address manager */
	private final AddressManager addressManager = new AddressManager();

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
	private java.util.List<Tuple> queue = new ArrayList<Tuple>(100) {
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

	/** Whether this tuple stream was produced by {@code compileForEval()}. */
	private boolean evalTupleStream;

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
		queue.add(new Tuple(Opcode.POP));
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
			queue.add(new Tuple(Opcode.PUSH_STRING, o.toString()));
		} else if (o instanceof Integer) {
			queue.add(new Tuple(Opcode.PUSH_LONG, (long) (Integer) o));
		} else if (o instanceof Long) {
			queue.add(new Tuple(Opcode.PUSH_LONG, (long) (Long) o));
		} else if (o instanceof Double) {
			queue.add(new Tuple(Opcode.PUSH_DOUBLE, (Double) o));
		}
	}

	/**
	 * <p>
	 * ifFalse.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void ifFalse(Address address) {
		queue.add(new Tuple(Opcode.IFFALSE, address));
	}

	/**
	 * <p>
	 * toNumber.
	 * </p>
	 */
	public void toNumber() {
		queue.add(new Tuple(Opcode.TO_NUMBER));
	}

	/**
	 * <p>
	 * ifTrue.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void ifTrue(Address address) {
		queue.add(new Tuple(Opcode.IFTRUE, address));
	}

	/**
	 * <p>
	 * gotoAddress.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void gotoAddress(Address address) {
		queue.add(new Tuple(Opcode.GOTO, address));
	}

	/**
	 * <p>
	 * createAddress.
	 * </p>
	 *
	 * @param label a {@link java.lang.String} object
	 * @return a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public Address createAddress(String label) {
		return addressManager.createAddress(label);
	}

	/**
	 * <p>
	 * address.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 * @return a {@link org.metricshub.jawk.intermediate.AwkTuples} object
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
		queue.add(new Tuple(Opcode.NOP));
	}

	/**
	 * <p>
	 * print.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void print(int numExprs) {
		queue.add(new Tuple(Opcode.PRINT, numExprs));
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
		queue.add(new Tuple(Opcode.PRINT_TO_FILE, numExprs, append));
	}

	/**
	 * <p>
	 * printToPipe.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printToPipe(int numExprs) {
		queue.add(new Tuple(Opcode.PRINT_TO_PIPE, numExprs));
	}

	/**
	 * <p>
	 * printf.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printf(int numExprs) {
		queue.add(new Tuple(Opcode.PRINTF, numExprs));
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
		queue.add(new Tuple(Opcode.PRINTF_TO_FILE, numExprs, append));
	}

	/**
	 * <p>
	 * printfToPipe.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void printfToPipe(int numExprs) {
		queue.add(new Tuple(Opcode.PRINTF_TO_PIPE, numExprs));
	}

	/**
	 * <p>
	 * sprintf.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void sprintf(int numExprs) {
		queue.add(new Tuple(Opcode.SPRINTF, numExprs));
	}

	/**
	 * <p>
	 * length.
	 * </p>
	 *
	 * @param numExprs a int
	 */
	public void length(int numExprs) {
		queue.add(new Tuple(Opcode.LENGTH, numExprs));
	}

	/**
	 * <p>
	 * concat.
	 * </p>
	 */
	public void concat() {
		queue.add(new Tuple(Opcode.CONCAT));
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
		queue.add(new Tuple(Opcode.ASSIGN, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.ASSIGN_ARRAY, offset, isGlobal));
	}

	/**
	 * <p>
	 * assignAsInput.
	 * </p>
	 */
	public void assignAsInput() {
		queue.add(new Tuple(Opcode.ASSIGN_AS_INPUT));
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
		queue.add(new Tuple(Opcode.ASSIGN_AS_INPUT_FIELD));
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
		queue.add(new Tuple(Opcode.DEREFERENCE, offset, isArray, isGlobal));
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
		queue.add(new Tuple(Opcode.PLUS_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MINUS_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MULT_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.DIV_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MOD_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.POW_EQ, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.PLUS_EQ_ARRAY, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MINUS_EQ_ARRAY, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MULT_EQ_ARRAY, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.DIV_EQ_ARRAY, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.MOD_EQ_ARRAY, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.POW_EQ_ARRAY, offset, isGlobal));
	}

	/**
	 * <p>
	 * plusEqInputField.
	 * </p>
	 */
	public void plusEqInputField() {
		queue.add(new Tuple(Opcode.PLUS_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * minusEqInputField.
	 * </p>
	 */
	public void minusEqInputField() {
		queue.add(new Tuple(Opcode.MINUS_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * multEqInputField.
	 * </p>
	 */
	public void multEqInputField() {
		queue.add(new Tuple(Opcode.MULT_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * divEqInputField.
	 * </p>
	 */
	public void divEqInputField() {
		queue.add(new Tuple(Opcode.DIV_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * modEqInputField.
	 * </p>
	 */
	public void modEqInputField() {
		queue.add(new Tuple(Opcode.MOD_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * powEqInputField.
	 * </p>
	 */
	public void powEqInputField() {
		queue.add(new Tuple(Opcode.POW_EQ_INPUT_FIELD));
	}

	/**
	 * <p>
	 * srand.
	 * </p>
	 *
	 * @param num a int
	 */
	public void srand(int num) {
		queue.add(new Tuple(Opcode.SRAND, num));
	}

	/**
	 * <p>
	 * rand.
	 * </p>
	 */
	public void rand() {
		queue.add(new Tuple(Opcode.RAND));
	}

	/**
	 * <p>
	 * intFunc.
	 * </p>
	 */
	public void intFunc() {
		queue.add(new Tuple(Opcode.INTFUNC));
	}

	/**
	 * <p>
	 * sqrt.
	 * </p>
	 */
	public void sqrt() {
		queue.add(new Tuple(Opcode.SQRT));
	}

	/**
	 * <p>
	 * log.
	 * </p>
	 */
	public void log() {
		queue.add(new Tuple(Opcode.LOG));
	}

	/**
	 * <p>
	 * exp.
	 * </p>
	 */
	public void exp() {
		queue.add(new Tuple(Opcode.EXP));
	}

	/**
	 * <p>
	 * sin.
	 * </p>
	 */
	public void sin() {
		queue.add(new Tuple(Opcode.SIN));
	}

	/**
	 * <p>
	 * cos.
	 * </p>
	 */
	public void cos() {
		queue.add(new Tuple(Opcode.COS));
	}

	/**
	 * <p>
	 * atan2.
	 * </p>
	 */
	public void atan2() {
		queue.add(new Tuple(Opcode.ATAN2));
	}

	/**
	 * <p>
	 * match.
	 * </p>
	 */
	public void match() {
		queue.add(new Tuple(Opcode.MATCH));
	}

	/**
	 * <p>
	 * index.
	 * </p>
	 */
	public void index() {
		queue.add(new Tuple(Opcode.INDEX));
	}

	/**
	 * <p>
	 * subForDollar0.
	 * </p>
	 *
	 * @param isGsub a boolean
	 */
	public void subForDollar0(boolean isGsub) {
		queue.add(new Tuple(Opcode.SUB_FOR_DOLLAR_0, isGsub));
	}

	/**
	 * <p>
	 * subForDollarReference.
	 * </p>
	 *
	 * @param isGsub a boolean
	 */
	public void subForDollarReference(boolean isGsub) {
		queue.add(new Tuple(Opcode.SUB_FOR_DOLLAR_REFERENCE, isGsub));
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
		queue.add(new Tuple(Opcode.SUB_FOR_VARIABLE, offset, isGlobal, isGsub));
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
		queue.add(new Tuple(Opcode.SUB_FOR_ARRAY_REFERENCE, offset, isGlobal, isGsub));
	}

	/**
	 * <p>
	 * split.
	 * </p>
	 *
	 * @param numargs a int
	 */
	public void split(int numargs) {
		queue.add(new Tuple(Opcode.SPLIT, numargs));
	}

	/**
	 * <p>
	 * substr.
	 * </p>
	 *
	 * @param numargs a int
	 */
	public void substr(int numargs) {
		queue.add(new Tuple(Opcode.SUBSTR, numargs));
	}

	/**
	 * <p>
	 * tolower.
	 * </p>
	 */
	public void tolower() {
		queue.add(new Tuple(Opcode.TOLOWER));
	}

	/**
	 * <p>
	 * toupper.
	 * </p>
	 */
	public void toupper() {
		queue.add(new Tuple(Opcode.TOUPPER));
	}

	/**
	 * <p>
	 * system.
	 * </p>
	 */
	public void system() {
		queue.add(new Tuple(Opcode.SYSTEM));
	}

	/**
	 * <p>
	 * swap.
	 * </p>
	 */
	public void swap() {
		queue.add(new Tuple(Opcode.SWAP));
	}

	/**
	 * <p>
	 * add.
	 * </p>
	 */
	public void add() {
		queue.add(new Tuple(Opcode.ADD));
	}

	/**
	 * <p>
	 * subtract.
	 * </p>
	 */
	public void subtract() {
		queue.add(new Tuple(Opcode.SUBTRACT));
	}

	/**
	 * <p>
	 * multiply.
	 * </p>
	 */
	public void multiply() {
		queue.add(new Tuple(Opcode.MULTIPLY));
	}

	/**
	 * <p>
	 * divide.
	 * </p>
	 */
	public void divide() {
		queue.add(new Tuple(Opcode.DIVIDE));
	}

	/**
	 * <p>
	 * mod.
	 * </p>
	 */
	public void mod() {
		queue.add(new Tuple(Opcode.MOD));
	}

	/**
	 * <p>
	 * pow.
	 * </p>
	 */
	public void pow() {
		queue.add(new Tuple(Opcode.POW));
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
		queue.add(new Tuple(Opcode.INC, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.DEC, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.POSTINC, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.POSTDEC, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.INC_ARRAY_REF, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.DEC_ARRAY_REF, offset, isGlobal));
	}

	/**
	 * <p>
	 * incDollarRef.
	 * </p>
	 */
	public void incDollarRef() {
		queue.add(new Tuple(Opcode.INC_DOLLAR_REF));
	}

	/**
	 * <p>
	 * decDollarRef.
	 * </p>
	 */
	public void decDollarRef() {
		queue.add(new Tuple(Opcode.DEC_DOLLAR_REF));
	}

	/**
	 * <p>
	 * dup.
	 * </p>
	 */
	public void dup() {
		queue.add(new Tuple(Opcode.DUP));
	}

	/**
	 * <p>
	 * not.
	 * </p>
	 */
	public void not() {
		queue.add(new Tuple(Opcode.NOT));
	}

	/**
	 * <p>
	 * negate.
	 * </p>
	 */
	public void negate() {
		queue.add(new Tuple(Opcode.NEGATE));
	}

	/**
	 * <p>
	 * unary plus.
	 * </p>
	 */
	public void unaryPlus() {
		queue.add(new Tuple(Opcode.UNARY_PLUS));
	}

	/**
	 * <p>
	 * cmpEq.
	 * </p>
	 */
	public void cmpEq() {
		queue.add(new Tuple(Opcode.CMP_EQ));
	}

	/**
	 * <p>
	 * cmpLt.
	 * </p>
	 */
	public void cmpLt() {
		queue.add(new Tuple(Opcode.CMP_LT));
	}

	/**
	 * <p>
	 * cmpGt.
	 * </p>
	 */
	public void cmpGt() {
		queue.add(new Tuple(Opcode.CMP_GT));
	}

	/**
	 * <p>
	 * matches.
	 * </p>
	 */
	public void matches() {
		queue.add(new Tuple(Opcode.MATCHES));
	}

	/**
	 * <p>
	 * dereferenceArray.
	 * </p>
	 */
	public void dereferenceArray() {
		queue.add(new Tuple(Opcode.DEREF_ARRAY));
	}

	/**
	 * <p>
	 * key list.
	 * </p>
	 */
	public void keylist() {
		queue.add(new Tuple(Opcode.KEYLIST));
	}

	/**
	 * <p>
	 * isEmptyList.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void isEmptyList(Address address) {
		queue.add(new Tuple(Opcode.IS_EMPTY_KEYLIST, address));
	}

	/**
	 * <p>
	 * getFirstAndRemoveFromList.
	 * </p>
	 */
	public void getFirstAndRemoveFromList() {
		queue.add(new Tuple(Opcode.GET_FIRST_AND_REMOVE_FROM_KEYLIST));
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
		queue.add(new Tuple(Opcode.CHECK_CLASS, cls));
		return true;
	}

	/**
	 * <p>
	 * getInputField.
	 * </p>
	 */
	public void getInputField() {
		queue.add(new Tuple(Opcode.GET_INPUT_FIELD));
	}

	/**
	 * <p>
	 * getInputField.
	 * </p>
	 *
	 * @param fieldIndex a long
	 */
	public void getInputField(long fieldIndex) {
		queue.add(new Tuple(Opcode.GET_INPUT_FIELD_CONST, fieldIndex));
	}

	/**
	 * <p>
	 * consumeInput.
	 * </p>
	 *
	 * @param address a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void consumeInput(Address address) {
		queue.add(new Tuple(Opcode.CONSUME_INPUT, address));
	}

	/**
	 * <p>
	 * getlineInput.
	 * </p>
	 */
	public void getlineInput() {
		queue.add(new Tuple(Opcode.GETLINE_INPUT));
	}

	/**
	 * <p>
	 * getlineInputToTarget.
	 * </p>
	 */
	public void getlineInputToTarget() {
		queue.add(new Tuple(Opcode.GETLINE_INPUT_TO_TARGET));
	}

	/**
	 * <p>
	 * useAsFileInput.
	 * </p>
	 */
	public void useAsFileInput() {
		queue.add(new Tuple(Opcode.USE_AS_FILE_INPUT));
	}

	/**
	 * <p>
	 * useAsCommandInput.
	 * </p>
	 */
	public void useAsCommandInput() {
		queue.add(new Tuple(Opcode.USE_AS_COMMAND_INPUT));
	}

	/**
	 * <p>
	 * nfOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void nfOffset(int offset) {
		queue.add(new Tuple(Opcode.NF_OFFSET, offset));
	}

	/**
	 * <p>
	 * nrOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void nrOffset(int offset) {
		queue.add(new Tuple(Opcode.NR_OFFSET, offset));
	}

	/**
	 * <p>
	 * fnrOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void fnrOffset(int offset) {
		queue.add(new Tuple(Opcode.FNR_OFFSET, offset));
	}

	/**
	 * <p>
	 * fsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void fsOffset(int offset) {
		queue.add(new Tuple(Opcode.FS_OFFSET, offset));
	}

	/**
	 * <p>
	 * rsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rsOffset(int offset) {
		queue.add(new Tuple(Opcode.RS_OFFSET, offset));
	}

	/**
	 * <p>
	 * ofsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void ofsOffset(int offset) {
		queue.add(new Tuple(Opcode.OFS_OFFSET, offset));
	}

	/**
	 * <p>
	 * orsOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void orsOffset(int offset) {
		queue.add(new Tuple(Opcode.ORS_OFFSET, offset));
	}

	/**
	 * <p>
	 * rstartOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rstartOffset(int offset) {
		queue.add(new Tuple(Opcode.RSTART_OFFSET, offset));
	}

	/**
	 * <p>
	 * rlengthOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void rlengthOffset(int offset) {
		queue.add(new Tuple(Opcode.RLENGTH_OFFSET, offset));
	}

	/**
	 * <p>
	 * filenameOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void filenameOffset(int offset) {
		queue.add(new Tuple(Opcode.FILENAME_OFFSET, offset));
	}

	/**
	 * <p>
	 * subsepOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void subsepOffset(int offset) {
		queue.add(new Tuple(Opcode.SUBSEP_OFFSET, offset));
	}

	/**
	 * <p>
	 * convfmtOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void convfmtOffset(int offset) {
		queue.add(new Tuple(Opcode.CONVFMT_OFFSET, offset));
	}

	/**
	 * <p>
	 * ofmtOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void ofmtOffset(int offset) {
		queue.add(new Tuple(Opcode.OFMT_OFFSET, offset));
	}

	/**
	 * <p>
	 * environOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void environOffset(int offset) {
		queue.add(new Tuple(Opcode.ENVIRON_OFFSET, offset));
	}

	/**
	 * <p>
	 * argcOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void argcOffset(int offset) {
		queue.add(new Tuple(Opcode.ARGC_OFFSET, offset));
	}

	/**
	 * <p>
	 * argvOffset.
	 * </p>
	 *
	 * @param offset a int
	 */
	public void argvOffset(int offset) {
		queue.add(new Tuple(Opcode.ARGV_OFFSET, offset));
	}

	// JRT-managed special variable helpers
	/** Pushes the current value of {@code NF} onto the operand stack. */
	public void pushNF() {
		queue.add(new Tuple(Opcode.PUSH_NF));
	}

	/** Assigns the top-of-stack value to {@code NF}. */
	public void assignNF() {
		queue.add(new Tuple(Opcode.ASSIGN_NF));
	}

	/** Pushes the current value of {@code NR} onto the operand stack. */
	public void pushNR() {
		queue.add(new Tuple(Opcode.PUSH_NR));
	}

	/** Assigns the top-of-stack value to {@code NR}. */
	public void assignNR() {
		queue.add(new Tuple(Opcode.ASSIGN_NR));
	}

	/** Pushes the current value of {@code FNR} onto the operand stack. */
	public void pushFNR() {
		queue.add(new Tuple(Opcode.PUSH_FNR));
	}

	/** Assigns the top-of-stack value to {@code FNR}. */
	public void assignFNR() {
		queue.add(new Tuple(Opcode.ASSIGN_FNR));
	}

	/** Pushes the current value of {@code FS} onto the operand stack. */
	public void pushFS() {
		queue.add(new Tuple(Opcode.PUSH_FS));
	}

	/** Assigns the top-of-stack value to {@code FS}. */
	public void assignFS() {
		queue.add(new Tuple(Opcode.ASSIGN_FS));
	}

	/** Pushes the current value of {@code RS} onto the operand stack. */
	public void pushRS() {
		queue.add(new Tuple(Opcode.PUSH_RS));
	}

	/** Assigns the top-of-stack value to {@code RS}. */
	public void assignRS() {
		queue.add(new Tuple(Opcode.ASSIGN_RS));
	}

	/** Pushes the current value of {@code OFS} onto the operand stack. */
	public void pushOFS() {
		queue.add(new Tuple(Opcode.PUSH_OFS));
	}

	/** Assigns the top-of-stack value to {@code OFS}. */
	public void assignOFS() {
		queue.add(new Tuple(Opcode.ASSIGN_OFS));
	}

	/** Pushes the current value of {@code ORS} onto the operand stack. */
	public void pushORS() {
		queue.add(new Tuple(Opcode.PUSH_ORS));
	}

	/** Assigns the top-of-stack value to {@code ORS}. */
	public void assignORS() {
		queue.add(new Tuple(Opcode.ASSIGN_ORS));
	}

	/** Pushes the current value of {@code RSTART} onto the operand stack. */
	public void pushRSTART() {
		queue.add(new Tuple(Opcode.PUSH_RSTART));
	}

	/** Assigns the top-of-stack value to {@code RSTART}. */
	public void assignRSTART() {
		queue.add(new Tuple(Opcode.ASSIGN_RSTART));
	}

	/** Pushes the current value of {@code RLENGTH} onto the operand stack. */
	public void pushRLENGTH() {
		queue.add(new Tuple(Opcode.PUSH_RLENGTH));
	}

	/** Assigns the top-of-stack value to {@code RLENGTH}. */
	public void assignRLENGTH() {
		queue.add(new Tuple(Opcode.ASSIGN_RLENGTH));
	}

	/** Pushes the current value of {@code FILENAME} onto the operand stack. */
	public void pushFILENAME() {
		queue.add(new Tuple(Opcode.PUSH_FILENAME));
	}

	/** Assigns the top-of-stack value to {@code FILENAME}. */
	public void assignFILENAME() {
		queue.add(new Tuple(Opcode.ASSIGN_FILENAME));
	}

	/** Pushes the current value of {@code SUBSEP} onto the operand stack. */
	public void pushSUBSEP() {
		queue.add(new Tuple(Opcode.PUSH_SUBSEP));
	}

	/** Assigns the top-of-stack value to {@code SUBSEP}. */
	public void assignSUBSEP() {
		queue.add(new Tuple(Opcode.ASSIGN_SUBSEP));
	}

	/** Pushes the current value of {@code CONVFMT} onto the operand stack. */
	public void pushCONVFMT() {
		queue.add(new Tuple(Opcode.PUSH_CONVFMT));
	}

	/** Assigns the top-of-stack value to {@code CONVFMT}. */
	public void assignCONVFMT() {
		queue.add(new Tuple(Opcode.ASSIGN_CONVFMT));
	}

	/** Pushes the current value of {@code OFMT} onto the operand stack. */
	public void pushOFMT() {
		queue.add(new Tuple(Opcode.PUSH_OFMT));
	}

	/** Assigns the top-of-stack value to {@code OFMT}. */
	public void assignOFMT() {
		queue.add(new Tuple(Opcode.ASSIGN_OFMT));
	}

	/** Pushes the current value of {@code ARGC} onto the operand stack. */
	public void pushARGC() {
		queue.add(new Tuple(Opcode.PUSH_ARGC));
	}

	/** Assigns the top-of-stack value to {@code ARGC}. */
	public void assignARGC() {
		queue.add(new Tuple(Opcode.ASSIGN_ARGC));
	}

	/**
	 * <p>
	 * applyRS.
	 * </p>
	 */
	public void applyRS() {
		queue.add(new Tuple(Opcode.APPLY_RS));
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
		queue.add(new Tuple(Opcode.FUNCTION, funcName, numFormalParams));
	}

	// public void callFunction(Address addr, String funcName, int numFormalParams, int numActualParams) {
	// queue.add(new Tuple(Opcode.CALL_FUNCTION, addr, funcName, numFormalParams, numActualParams)); }

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
		queue.add(new Tuple(Opcode.CALL_FUNCTION, addressSupplier, funcName, numFormalParams, numActualParams));
	}

	/**
	 * <p>
	 * setReturnResult.
	 * </p>
	 */
	public void setReturnResult() {
		queue.add(new Tuple(Opcode.SET_RETURN_RESULT));
	}

	/**
	 * <p>
	 * returnFromFunction.
	 * </p>
	 */
	public void returnFromFunction() {
		queue.add(new Tuple(Opcode.RETURN_FROM_FUNCTION));
	}

	/**
	 * <p>
	 * setNumGlobals.
	 * </p>
	 *
	 * @param numGlobals a int
	 */
	public void setNumGlobals(int numGlobals) {
		queue.add(new Tuple(Opcode.SET_NUM_GLOBALS, numGlobals));
	}

	/**
	 * <p>
	 * close.
	 * </p>
	 */
	public void close() {
		queue.add(new Tuple(Opcode.CLOSE));
	}

	/**
	 * <p>
	 * applySubsep.
	 * </p>
	 *
	 * @param count a int
	 */
	public void applySubsep(int count) {
		queue.add(new Tuple(Opcode.APPLY_SUBSEP, count));
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
		queue.add(new Tuple(Opcode.DELETE_ARRAY_ELEMENT, offset, isGlobal));
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
		queue.add(new Tuple(Opcode.DELETE_ARRAY, offset, isGlobal));
	}

	/**
	 * <p>
	 * setExitAddress.
	 * </p>
	 *
	 * @param addr a {@link org.metricshub.jawk.intermediate.Address} object
	 */
	public void setExitAddress(Address addr) {
		queue.add(new Tuple(Opcode.SET_EXIT_ADDRESS, addr));
	}

	/**
	 * <p>
	 * setWithinEndBlocks.
	 * </p>
	 *
	 * @param b a boolean
	 */
	public void setWithinEndBlocks(boolean b) {
		queue.add(new Tuple(Opcode.SET_WITHIN_END_BLOCKS, b));
	}

	/**
	 * <p>
	 * exitWithCode.
	 * </p>
	 */
	public void exitWithCode() {
		queue.add(new Tuple(Opcode.EXIT_WITH_CODE));
	}

	/**
	 * <p>
	 * exitWithCode.
	 * </p>
	 */
	public void exitWithoutCode() {
		queue.add(new Tuple(Opcode.EXIT_WITHOUT_CODE));
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
		queue.add(new Tuple(Opcode.REGEXP, regexpStr, precompiled));
	}

	/**
	 * <p>
	 * regexpPair.
	 * </p>
	 */
	public void conditionPair() {
		queue.add(new Tuple(Opcode.CONDITION_PAIR));
	}

	/**
	 * <p>
	 * isIn.
	 * </p>
	 */
	public void isIn() {
		queue.add(new Tuple(Opcode.IS_IN));
	}

	/**
	 * Emits a tuple that pushes the current script context onto the stack.
	 */
	public void scriptThis() {
		queue.add(new Tuple(Opcode.THIS));
	}

	/**
	 * Emits an extension invocation tuple.
	 *
	 * @param function metadata describing the extension method to invoke
	 * @param paramCount number of arguments supplied for the call
	 * @param isInitial {@code true} when this tuple opens an extension call sequence
	 */
	public void extension(ExtensionFunction function, int paramCount, boolean isInitial) {
		queue.add(new Tuple(Opcode.EXTENSION, function, paramCount, isInitial));
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
	 * @return a {@link org.metricshub.jawk.intermediate.PositionTracker} object
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

		java.util.List<Tuple> original = new ArrayList<Tuple>(queue);
		int[] indexMapping = new int[originalSize];
		Arrays.fill(indexMapping, -1);
		java.util.List<Tuple> optimizedQueue = new ArrayList<Tuple>(originalSize);

		boolean modified = false;
		int oldIndex = 0;
		int newIndex = 0;
		while (oldIndex < originalSize) {
			Tuple tuple = original.get(oldIndex);
			Object literal = literalValue(tuple);
			if (literal != null) {
				if ((oldIndex + 1) < originalSize) {
					Tuple nextTuple = original.get(oldIndex + 1);
					if (nextTuple.getOpcode() == Opcode.GET_INPUT_FIELD) {
						long fieldIndex = JRT.toLong(literal);
						Tuple replacement = createGetInputFieldConst(
								fieldIndex,
								tuple.getLineno());
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
							Tuple replacement = createLiteralPush(folded, tuple.getLineno());
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
						Tuple replacement = createLiteralPush(folded, tuple.getLineno());
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

	private void mapFoldedRange(int[] indexMapping, int startIndex, int length, int newIndex) {
		for (int idx = 0; idx < length; idx++) {
			indexMapping[startIndex + idx] = newIndex;
		}
	}

	private Object literalValue(Tuple tuple) {
		switch (tuple.getOpcode()) {
		case PUSH_LONG:
			return Long.valueOf(tuple.getInts()[0]);
		case PUSH_DOUBLE:
			return Double.valueOf(tuple.getDoubles()[0]);
		case PUSH_STRING:
			return tuple.getStrings()[0];
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
			return JRT.compare2(left, right, 0) ? Long.valueOf(1L) : Long.valueOf(0L);
		case CMP_LT:
			return JRT.compare2(left, right, -1) ? Long.valueOf(1L) : Long.valueOf(0L);
		case CMP_GT:
			return JRT.compare2(left, right, 1) ? Long.valueOf(1L) : Long.valueOf(0L);
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
			tuple = new Tuple(Opcode.PUSH_LONG, ((Long) value).longValue());
		} else if (value instanceof Integer) {
			tuple = new Tuple(Opcode.PUSH_LONG, ((Integer) value).longValue());
		} else if (value instanceof Double) {
			tuple = new Tuple(Opcode.PUSH_DOUBLE, ((Double) value).doubleValue());
		} else if (value instanceof Number) {
			double d = ((Number) value).doubleValue();
			if (JRT.isActuallyLong(d)) {
				tuple = new Tuple(Opcode.PUSH_LONG, (long) Math.rint(d));
			} else {
				tuple = new Tuple(Opcode.PUSH_DOUBLE, d);
			}
		} else if (value instanceof String) {
			tuple = new Tuple(Opcode.PUSH_STRING, (String) value);
		} else {
			throw new IllegalArgumentException("Unsupported literal value: " + value);
		}
		tuple.setLineNumber(lineNumber);
		return tuple;
	}

	private Tuple createGetInputFieldConst(long fieldIndex, int lineNumber) {
		Tuple tuple = new Tuple(Opcode.GET_INPUT_FIELD_CONST, fieldIndex);
		tuple.setLineNumber(lineNumber);
		return tuple;
	}

	private void remapAddresses(int[] indexMapping) {
		if (indexMapping.length == 0) {
			return;
		}
		Set<Address> processedAddresses = Collections.newSetFromMap(new IdentityHashMap<Address, Boolean>());
		for (Tuple tuple : queue) {
			Address address = tuple.getAddress();
			if (address != null && processedAddresses.add(address)) {
				int oldIndex = address.index();
				if (oldIndex >= 0 && oldIndex < indexMapping.length) {
					int mappedIndex = indexMapping[oldIndex];
					if (mappedIndex < 0) {
						throw new Error("Address " + address + " references removed tuple " + oldIndex);
					}
					address.assignIndex(mappedIndex);
				}
			}
		}
		addressManager.remapIndexes(indexMapping);
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
		case ASSIGN_ARRAY:
		case DEREFERENCE:
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
