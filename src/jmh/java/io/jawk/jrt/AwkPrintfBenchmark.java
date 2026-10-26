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

import java.util.Locale;
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
 * Microbenchmarks for the {@link AwkPrintf} formatting hot paths exercised by
 * AWK {@code printf}, {@code sprintf}, and {@code CONVFMT}/{@code OFMT}
 * number-to-string conversions.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class AwkPrintfBenchmark {

	private Locale locale;
	private Object smallDouble;
	private Object typicalDouble;
	private Object fractionDouble;
	private Object tinyDouble;
	private Object hugeDouble;
	private Object longValue;
	private Object stringValue;
	private double convfmtDouble;

	/**
	 * Initializes benchmark operands as mutable state fields so benchmark bodies
	 * do not feed compile-time constants directly to the JIT.
	 */
	@Setup(Level.Trial)
	public void setup() {
		this.locale = Locale.US;
		this.smallDouble = Double.valueOf(42.987D);
		this.typicalDouble = Double.valueOf(123456.789D);
		this.fractionDouble = Double.valueOf(0.1D);
		this.tinyDouble = Double.valueOf(1.2345e-300D);
		this.hugeDouble = Double.valueOf(6.789e300D);
		this.longValue = Long.valueOf(123456789L);
		this.stringValue = "hello world";
		this.convfmtDouble = 3.14159D;
	}

	/**
	 * Measures {@code %.2f} of a typical small double.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfFixedSmall() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%.2f", this.smallDouble);
	}

	/**
	 * Measures {@code %f} (default precision) of a value with a long exact
	 * binary expansion.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfFixedFraction() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%f", this.fractionDouble);
	}

	/**
	 * Measures {@code %e} scientific notation.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfScientific() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%e", this.typicalDouble);
	}

	/**
	 * Measures {@code %.6g}, the default {@code CONVFMT}, on a typical value.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfGeneral() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%.6g", this.typicalDouble);
	}

	/**
	 * Measures {@code %.6g} on a tiny double whose exact decimal expansion has
	 * hundreds of digits.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfGeneralTiny() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%.6g", this.tinyDouble);
	}

	/**
	 * Measures {@code %.6g} on a huge double.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfGeneralHuge() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%.6g", this.hugeDouble);
	}

	/**
	 * Measures a mixed format typical of report-style AWK programs.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfMixed() {
		return AwkPrintf.sprintf(
				this.locale,
				AwkPrintf.DEFAULT_CONVFMT,
				"%s: %05d (%.2f)\n",
				this.stringValue,
				this.longValue,
				this.smallDouble);
	}

	/**
	 * Measures an integer-only format, where format parsing dominates.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfIntegerOnly() {
		return AwkPrintf
				.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "%8d|%-8d|%x", this.longValue, this.longValue, this.longValue);
	}

	/**
	 * Measures a string-only format.
	 *
	 * @return formatted text
	 */
	@Benchmark
	public String sprintfStringOnly() {
		return AwkPrintf.sprintf(this.locale, AwkPrintf.DEFAULT_CONVFMT, "[%s] [%10s]", this.stringValue, this.stringValue);
	}

	/**
	 * Measures {@link AwkPrintf#toAwkString(Object, String, Locale)} for a
	 * fractional double, the {@code CONVFMT} conversion path.
	 *
	 * @return converted text
	 */
	@Benchmark
	public String toAwkStringFractional() {
		return AwkPrintf.toAwkString(Double.valueOf(this.convfmtDouble), AwkPrintf.DEFAULT_CONVFMT, this.locale);
	}
}
