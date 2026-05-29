package io.jawk.jrt;

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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Microbenchmarks for {@link JRT#compare2(Object, Object, int)} operand shapes
 * that are common in comparison-heavy AWK programs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class JRTCompare2Benchmark {

	private Object longLeft;
	private Object longRightEqual;
	private Object longRightGreater;
	private Object doubleLeft;
	private Object doubleRightEqual;
	private Object doubleRightGreater;
	private Object longAsDoubleRightEqual;
	private Object stringLeft;
	private Object stringRightEqual;
	private Object stringRightGreater;
	private Object plainNumericStringLeft;
	private Object plainNumericStringRightEqual;
	private Object plainNumericStringRightGreater;
	private Object nonNumericString;
	private Object strNumLeft;
	private Object strNumRightEqual;
	private Object strNumRightGreater;
	private Object nonNumericStrNum;

	/**
	 * Initializes benchmark operands as mutable state fields so the benchmark body
	 * does not feed compile-time constants directly to the JIT.
	 */
	@Setup(Level.Trial)
	public void setup() {
		this.longLeft = Long.valueOf(123L);
		this.longRightEqual = Long.valueOf(123L);
		this.longRightGreater = Long.valueOf(456L);
		this.doubleLeft = Double.valueOf(123.25D);
		this.doubleRightEqual = Double.valueOf(123.25D);
		this.doubleRightGreater = Double.valueOf(456.5D);
		this.longAsDoubleRightEqual = Double.valueOf(123.0D);
		this.stringLeft = "alpha";
		this.stringRightEqual = "alpha";
		this.stringRightGreater = "bravo";
		this.plainNumericStringLeft = "123";
		this.plainNumericStringRightEqual = "123.0";
		this.plainNumericStringRightGreater = "456";
		this.nonNumericString = "2x";
		this.strNumLeft = new StrNum("123");
		this.strNumRightEqual = new StrNum("123.0");
		this.strNumRightGreater = new StrNum("456");
		this.nonNumericStrNum = new StrNum("2x");
	}

	/**
	 * Measures equality for two boxed {@link Long} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean longEquals() {
		return JRT.compare2(this.longLeft, this.longRightEqual, 0);
	}

	/**
	 * Measures less-than comparison for two boxed {@link Long} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean longLessThan() {
		return JRT.compare2(this.longLeft, this.longRightGreater, -1);
	}

	/**
	 * Measures equality for two boxed {@link Double} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean doubleEquals() {
		return JRT.compare2(this.doubleLeft, this.doubleRightEqual, 0);
	}

	/**
	 * Measures less-than comparison for two boxed {@link Double} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean doubleLessThan() {
		return JRT.compare2(this.doubleLeft, this.doubleRightGreater, -1);
	}

	/**
	 * Measures equality for a boxed {@link Long} and boxed {@link Double}.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean mixedLongDoubleEquals() {
		return JRT.compare2(this.longLeft, this.longAsDoubleRightEqual, 0);
	}

	/**
	 * Measures equality for two equal plain {@link String} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean stringEquals() {
		return JRT.compare2(this.stringLeft, this.stringRightEqual, 0);
	}

	/**
	 * Measures less-than comparison for two plain {@link String} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean stringLessThan() {
		return JRT.compare2(this.stringLeft, this.stringRightGreater, -1);
	}

	/**
	 * Measures equality for two plain numeric-looking {@link String} operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean plainNumericStringEquals() {
		return JRT.compare2(this.plainNumericStringLeft, this.plainNumericStringRightEqual, 0);
	}

	/**
	 * Measures less-than comparison for two plain numeric-looking {@link String}
	 * operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean plainNumericStringLessThan() {
		return JRT.compare2(this.plainNumericStringLeft, this.plainNumericStringRightGreater, -1);
	}

	/**
	 * Measures equality for a boxed {@link Long} and a plain numeric-looking
	 * {@link String} operand.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean mixedLongPlainNumericStringEquals() {
		return JRT.compare2(this.longLeft, this.plainNumericStringRightEqual, 0);
	}

	/**
	 * Measures equality for two input-derived numeric string operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean strNumEquals() {
		return JRT.compare2(this.strNumLeft, this.strNumRightEqual, 0);
	}

	/**
	 * Measures less-than comparison for two input-derived numeric string operands.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean strNumLessThan() {
		return JRT.compare2(this.strNumLeft, this.strNumRightGreater, -1);
	}

	/**
	 * Measures equality for a boxed {@link Long} and an input-derived numeric
	 * string operand.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean mixedLongStrNumEquals() {
		return JRT.compare2(this.longLeft, this.strNumRightEqual, 0);
	}

	/**
	 * Measures fallback string comparison for a numeric operand and a non-numeric
	 * string.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean mixedLongNonNumericStringLessThan() {
		return JRT.compare2(this.longLeft, this.nonNumericString, -1);
	}

	/**
	 * Measures fallback string comparison for a numeric operand and a nonnumeric
	 * input-derived string.
	 *
	 * @return the comparison result
	 */
	@Benchmark
	public boolean mixedLongNonNumericStrNumLessThan() {
		return JRT.compare2(this.longLeft, this.nonNumericStrNum, -1);
	}
}
