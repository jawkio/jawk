package io.jawk;

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

import io.jawk.intermediate.AwkTuples;
import io.jawk.intermediate.Opcode;
import io.jawk.intermediate.PositionTracker;
import io.jawk.intermediate.Tuple;
import io.jawk.jrt.JRT;

/**
 * Public view of a compiled AWK expression.
 * <p>
 * Expressions are compiled into immutable tuple streams that can be reused
 * across multiple evaluations. Instances are produced by
 * {@link Awk#compileExpression(String)}.
 * </p>
 * <p>
 * {@link #isConstant()}, {@link #getConstantValue()} and
 * {@link #getFieldIndex()} tell a host that the expression is trivial, so
 * that it can skip the evaluation altogether. They read the compiled tuples,
 * after optimization: what the optimizer does not reduce to one value or one
 * field is reported as neither, which is always safe (the host evaluates the
 * expression). An expression compiled with the optimization disabled is thus
 * only constant when it is one literal, and is never a field.
 * </p>
 */
public class AwkExpression extends AwkTuples {
	private static final long serialVersionUID = 1L;

	AwkExpression() {
		super();
	}

	/**
	 * Returns whether this expression has the same value whatever the input and
	 * the variables: a string or number literal ({@code "a"}, {@code 42}), or
	 * what constant folding reduces to one ({@code 2 * 3}, {@code "a" "b"}).
	 * An expression that reads a field or a variable, or calls a function, is
	 * never constant.
	 *
	 * @return {@code true} when {@link #getConstantValue()} gives the value of
	 *         this expression
	 */
	public boolean isConstant() {
		return constantValue() != null;
	}

	/**
	 * Returns the value of this constant expression, exactly as
	 * {@link Awk#eval(AwkExpression)} returns it: a {@link String}, a
	 * {@link Long} or a {@link Double}.
	 * <p>
	 * This is the evaluated value, not the source text: {@code 0.10} is
	 * {@code 0.1}, {@code 010} is {@code 10}, and {@code 1e3} is {@code 1000}. A
	 * host that needs the text of a number as it was written must keep it itself.
	 * </p>
	 *
	 * @return the value of this expression
	 * @throws IllegalStateException when this expression is not constant (see
	 *         {@link #isConstant()})
	 */
	public Object getConstantValue() {
		Object value = constantValue();
		if (value == null) {
			throw new IllegalStateException("The expression is not constant.");
		}
		return value;
	}

	/**
	 * Returns the index {@code n} when this expression is exactly {@code $n}
	 * with a constant {@code n}: {@code 0} for {@code $0} (the record),
	 * {@code 3} for {@code $3}. Evaluating the expression on a record then
	 * gives the field of that index.
	 * <p>
	 * {@code $NF}, {@code $(i)} and {@code $1 $2} are not such expressions, nor
	 * is {@code $(-1)}, which fails when evaluated.
	 * </p>
	 *
	 * @return the index of the field, or {@code -1} when this expression is not
	 *         a single field of constant index
	 */
	public int getFieldIndex() {
		Tuple tuple = valueTuple();
		if (tuple instanceof Tuple.InputFieldTuple) {
			long index = ((Tuple.InputFieldTuple) tuple).getFieldIndex();
			if (index >= 0 && index <= Integer.MAX_VALUE) {
				return (int) index;
			}
		}
		return -1;
	}

	/**
	 * Returns the value of the literal this expression was compiled to.
	 *
	 * @return the value, or {@code null} when this expression is not constant
	 */
	private Object constantValue() {
		Tuple tuple = valueTuple();
		if (tuple instanceof Tuple.PushStringTuple) {
			return ((Tuple.PushStringTuple) tuple).getValue();
		}
		if (tuple instanceof Tuple.PushLongTuple) {
			return Long.valueOf(((Tuple.PushLongTuple) tuple).getValue());
		}
		if (tuple instanceof Tuple.PushDoubleTuple) {
			return JRT.toJavaScalar(Double.valueOf(((Tuple.PushDoubleTuple) tuple).getValue()));
		}
		return null;
	}

	/**
	 * Returns the tuple that computes the value of this expression, when there
	 * is exactly one besides the prelude the compiler emits for every
	 * expression.
	 *
	 * @return the only tuple of this expression, or {@code null} when it has
	 *         none or several
	 */
	private Tuple valueTuple() {
		Tuple value = null;
		for (PositionTracker position = top(); !position.isEOF(); position.next()) {
			Opcode opcode = position.opcode();
			if (opcode == Opcode.SET_NUM_GLOBALS || opcode == Opcode.BEFORE_START_HOOKS || opcode == Opcode.NOP) {
				continue;
			}
			if (value != null) {
				return null;
			}
			value = position.current();
		}
		return value;
	}
}
