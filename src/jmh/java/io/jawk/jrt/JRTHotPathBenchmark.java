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

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import io.jawk.Awk;
import io.jawk.intermediate.UninitializedObject;
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
 * Microbenchmarks for small {@link JRT} conversion and truthiness helpers that
 * are frequently exercised by generated AWK runtime code.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class JRTHotPathBenchmark {

	private Object longValue;
	private Object integerValue;
	private Object doubleValue;
	private Object integerString;
	private Object decimalString;
	private Object exponentString;
	private Object nonNumericString;
	private Object emptyString;
	private Object zeroLong;
	private Object zeroDouble;
	private Object zeroString;
	private Object uninitialized;
	private double integralDouble;
	private double fractionalDouble;
	private double largeIntegralDouble;
	private JRT jrt;
	private Pattern inputPattern;

	/**
	 * Initializes benchmark operands as mutable state fields so benchmark bodies
	 * do not feed compile-time constants directly to the JIT.
	 */
	@Setup(Level.Trial)
	public void setup() {
		this.longValue = Long.valueOf(123456789L);
		this.integerValue = Integer.valueOf(12345);
		this.doubleValue = Double.valueOf(123.75D);
		this.integerString = "123456789";
		this.decimalString = "12345.75";
		this.exponentString = "1.25e6";
		this.nonNumericString = "not-a-number";
		this.emptyString = "";
		this.zeroLong = Long.valueOf(0L);
		this.zeroDouble = Double.valueOf(0.0D);
		this.zeroString = "0";
		this.uninitialized = new UninitializedObject();
		this.integralDouble = 123456789D;
		this.fractionalDouble = 123456.75D;
		this.largeIntegralDouble = 9007199254740992D;
		this.jrt = new JRT(new BenchmarkVariableManager(), Locale.US, AwkSink.NOP_SINK, (PrintStream) null);
		this.jrt.setInputLine("prefix test suffix");
		this.inputPattern = Pattern.compile("test");
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for a boxed {@link Long}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleLong() {
		return JRT.toDouble(this.longValue);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for a boxed {@link Integer}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleInteger() {
		return JRT.toDouble(this.integerValue);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for a boxed {@link Double}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleDouble() {
		return JRT.toDouble(this.doubleValue);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for an integer string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleIntegerString() {
		return JRT.toDouble(this.integerString);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for a decimal string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleDecimalString() {
		return JRT.toDouble(this.decimalString);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for exponent notation.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleExponentString() {
		return JRT.toDouble(this.exponentString);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for a non-numeric string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleNonNumericString() {
		return JRT.toDouble(this.nonNumericString);
	}

	/**
	 * Measures {@link JRT#toDouble(Object)} for an empty string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public double toDoubleEmptyString() {
		return JRT.toDouble(this.emptyString);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for a boxed {@link Long}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongLong() {
		return JRT.toLong(this.longValue);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for a boxed {@link Integer}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongInteger() {
		return JRT.toLong(this.integerValue);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for a boxed {@link Double}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongDouble() {
		return JRT.toLong(this.doubleValue);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for an integer string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongIntegerString() {
		return JRT.toLong(this.integerString);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for a decimal string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongDecimalString() {
		return JRT.toLong(this.decimalString);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for exponent notation.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongExponentString() {
		return JRT.toLong(this.exponentString);
	}

	/**
	 * Measures {@link JRT#toLong(Object)} for a non-numeric string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public long toLongNonNumericString() {
		return JRT.toLong(this.nonNumericString);
	}

	/**
	 * Measures {@link JRT#isActuallyLong(double)} for an integral double.
	 *
	 * @return whether the value is effectively integral
	 */
	@Benchmark
	public boolean isActuallyLongIntegralDouble() {
		return JRT.isActuallyLong(this.integralDouble);
	}

	/**
	 * Measures {@link JRT#isActuallyLong(double)} for a fractional double.
	 *
	 * @return whether the value is effectively integral
	 */
	@Benchmark
	public boolean isActuallyLongFractionalDouble() {
		return JRT.isActuallyLong(this.fractionalDouble);
	}

	/**
	 * Measures {@link JRT#isActuallyLong(double)} for a large integral double.
	 *
	 * @return whether the value is effectively integral
	 */
	@Benchmark
	public boolean isActuallyLongLargeIntegralDouble() {
		return JRT.isActuallyLong(this.largeIntegralDouble);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a non-zero boxed {@link Long}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanLongNonZero() {
		return this.jrt.toBoolean(this.longValue);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a zero boxed {@link Long}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanLongZero() {
		return this.jrt.toBoolean(this.zeroLong);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a zero boxed {@link Double}.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanDoubleZero() {
		return this.jrt.toBoolean(this.zeroDouble);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a non-empty string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanStringNonEmpty() {
		return this.jrt.toBoolean(this.nonNumericString);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for an empty string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanStringEmpty() {
		return this.jrt.toBoolean(this.emptyString);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a zero-like string.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanStringZero() {
		return this.jrt.toBoolean(this.zeroString);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for an uninitialized runtime value.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanUninitialized() {
		return this.jrt.toBoolean(this.uninitialized);
	}

	/**
	 * Measures {@link JRT#toBoolean(Object)} for a regular expression matched
	 * against the current input record.
	 *
	 * @return converted value
	 */
	@Benchmark
	public boolean toBooleanPatternMatch() {
		return this.jrt.toBoolean(this.inputPattern);
	}

	/**
	 * Measures {@link JRT#inc(Object)} for a boxed numeric value.
	 *
	 * @return incremented value
	 */
	@Benchmark
	public Object incrementBoxedDouble() {
		return JRT.inc(this.doubleValue);
	}

	/**
	 * Measures {@link JRT#inc(Object)} for a numeric string.
	 *
	 * @return incremented value
	 */
	@Benchmark
	public Object incrementDecimalString() {
		return JRT.inc(this.decimalString);
	}

	/**
	 * Measures {@link JRT#inc(Object)} for a non-numeric string.
	 *
	 * @return incremented value
	 */
	@Benchmark
	public Object incrementNonNumericString() {
		return JRT.inc(this.nonNumericString);
	}

	/**
	 * Minimal variable manager used by the standalone {@link JRT} benchmark.
	 */
	private static final class BenchmarkVariableManager implements VariableManager {

		private final Object argc = Long.valueOf(0L);
		private final Object argv = JRT.createAwkMap(false);
		private final Object convfmt = Awk.DEFAULT_CONVFMT;
		private final Object fs = Awk.DEFAULT_FS;
		private final Object rs = Awk.DEFAULT_RS;
		private final Object ofs = Awk.DEFAULT_OFS;
		private final Object ors = Awk.DEFAULT_ORS;
		private final Object subsep = Awk.DEFAULT_SUBSEP;

		/** {@inheritDoc} */
		@Override
		public Object getARGC() {
			return this.argc;
		}

		/** {@inheritDoc} */
		@Override
		public Object getARGV() {
			return this.argv;
		}

		/** {@inheritDoc} */
		@Override
		public Object getCONVFMT() {
			return this.convfmt;
		}

		/** {@inheritDoc} */
		@Override
		public Object getFS() {
			return this.fs;
		}

		/** {@inheritDoc} */
		@Override
		public Object getRS() {
			return this.rs;
		}

		/** {@inheritDoc} */
		@Override
		public Object getOFS() {
			return this.ofs;
		}

		/** {@inheritDoc} */
		@Override
		public Object getORS() {
			return this.ors;
		}

		/** {@inheritDoc} */
		@Override
		public Object getSUBSEP() {
			return this.subsep;
		}

		/** {@inheritDoc} */
		@Override
		public void setFILENAME(String fileName) {
			assignVariable("FILENAME", fileName);
		}

		/** {@inheritDoc} */
		@Override
		public void setNF(Integer newNf) {
			assignVariable("NF", newNf);
		}

		/** {@inheritDoc} */
		@Override
		public void incNR() {
			assignVariable("NR", Long.valueOf(1L));
		}

		/** {@inheritDoc} */
		@Override
		public void incFNR() {
			assignVariable("FNR", Long.valueOf(1L));
		}

		/** {@inheritDoc} */
		@Override
		public void resetFNR() {
			assignVariable("FNR", Long.valueOf(0L));
		}

		/** {@inheritDoc} */
		@Override
		public void assignVariable(String name, Object value) {
			// No-op: conversion benchmarks do not read AVM-managed globals.
		}
	}
}
