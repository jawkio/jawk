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

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.Deque;
import org.metricshub.jawk.ext.JawkExtension;

/**
 * <p>
 * AwkTuples class.
 * </p>
 *
 * @author Danny Daglas
 */
public class AwkTuples implements Serializable {

	private static final long serialVersionUID = 2L;

	/** Version Manager */
	private VersionManager versionManager = new VersionManager();

	/** Address manager */
	private final AddressManager addressManager = new AddressManager();

	// made public to access static members of AwkTuples via Java Reflection

	// made public to be accessable via Java Reflection
	// (see toOpcodeString() method below)

	/**
	 * Override add() to populate the line number for each tuple,
	 * rather than polluting all the constructors with this assignment.
	 */
	private java.util.List<Tuple> queue = new ArrayList<Tuple>(100) {
		private static final long serialVersionUID = -6334362156408598578L;

		@Override
		public boolean add(Tuple t) {
			t.setLineNumber(linenoStack.peek());
			return super.add(t);
		}
	};

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
		assert (o instanceof String) || (o instanceof Long) || (o instanceof Integer) || (o instanceof Double); // || (o
																																																						// instanceof
																																																						// Pattern);
		if (o instanceof String) {
			queue.add(new Tuple(Opcode.PUSH, o.toString()));
		} else if (o instanceof Integer) {
			queue.add(new Tuple(Opcode.PUSH, (Integer) o));
		} else if (o instanceof Long) {
			queue.add(new Tuple(Opcode.PUSH, (Long) o));
		} else if (o instanceof Double) {
			queue.add(new Tuple(Opcode.PUSH, (Double) o));
		} else {
			assert false : "Invalid type for " + o + ", " + o.getClass();
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
	 * Use this only in initialization for simple evaluation
	 */
	public void setInputForEval() {
		queue.add(new Tuple(Opcode.SET_INPUT_FOR_EVAL));
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
	 * exec.
	 * </p>
	 */
	public void exec() {
		queue.add(new Tuple(Opcode.EXEC));
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
		queue.add(new Tuple(Opcode.REGEXP, regexpStr));
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
	 * <p>
	 * scriptThis.
	 * </p>
	 */
	public void scriptThis() {
		queue.add(new Tuple(Opcode.THIS));
	}

	/**
	 * <p>
	 * extension.
	 * </p>
	 *
	 * @param extensionKeyword a {@link java.lang.String} object
	 * @param paramCount a int
	 * @param isInitial a boolean
	 */
	public void extension(
			String extensionKeyword,
			JawkExtension.ExtensionFunction function,
			int paramCount,
			boolean isInitial) {
		queue.add(new Tuple(Opcode.EXTENSION, extensionKeyword, paramCount, isInitial, function));
	}

	/**
	 * <p>
	 * dump.
	 * </p>
	 *
	 * @param ps a {@link java.io.PrintStream} object
	 */
	public void dump(PrintStream ps) {
		ps.println("(" + versionManager + ")");
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

	/** Map of global variables offsets */
	private Map<String, Integer> globalVarOffsetMap = new HashMap<String, Integer>();

	/** Map of global arrays */
	private Map<String, Boolean> globalVarAarrayMap = new HashMap<String, Boolean>();

	/** List of user function names */
	private Set<String> functionNames = null;

	/**
	 * Accept a {variable_name -&gt; offset} mapping such that global variables can be
	 * assigned while processing name=value and filename command-line arguments.
	 *
	 * @param varname Name of the global variable
	 * @param offset What offset to use for the variable
	 * @param isArray Whether the variable is actually an array
	 */
	public void addGlobalVariableNameToOffsetMapping(String varname, int offset, boolean isArray) {
		if (globalVarOffsetMap.get(varname) != null) {
			assert globalVarAarrayMap.get(varname) != null;
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
	 * <p>
	 * getGlobalVariableOffsetMap.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, Integer> getGlobalVariableOffsetMap() {
		return Collections.unmodifiableMap(globalVarOffsetMap);
	}

	/**
	 * <p>
	 * getGlobalVariableAarrayMap.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, Boolean> getGlobalVariableAarrayMap() {
		return Collections.unmodifiableMap(globalVarAarrayMap);
	}

	/**
	 * <p>
	 * getFunctionNameSet.
	 * </p>
	 *
	 * @return a {@link java.util.Set} object
	 */
	public Set<String> getFunctionNameSet() {
		assert functionNames != null;
		return Collections.unmodifiableSet(functionNames);
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
		int tos = linenoStack.pop();
		assert lineno == tos;
	}

}
