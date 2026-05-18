package io.jawk.backend;

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

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import io.jawk.Awk;
import io.jawk.AwkExpression;
import io.jawk.util.AwkSettings;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Microbenchmarks for compiled expression evaluation through the
 * {@link AVM#prepareForEval(String)} and {@link AVM#eval(AwkExpression)} path
 * used by embedding callers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class AVMExpressionBenchmark {

	private AVM avm;
	private AwkExpression literalAddition;
	private AwkExpression fieldAddition;
	private AwkExpression fieldMultiplication;
	private AwkExpression fieldConcatenation;
	private AwkExpression fieldRegexMatch;
	private AwkExpression multiStringConcatenation;
	private AwkExpression mixedExpression;

	/**
	 * Compiles expressions once and creates the reusable interpreter used by this
	 * benchmark state.
	 *
	 * @throws IOException if expression compilation or input preparation fails
	 */
	@Setup(Level.Trial)
	public void setup() throws IOException {
		Awk awk = new Awk();
		this.literalAddition = awk.compileExpression("1 + 2");
		this.fieldAddition = awk.compileExpression("$1 + 1");
		this.fieldMultiplication = awk.compileExpression("$1 * 2");
		this.fieldConcatenation = awk.compileExpression("$1 \" test\"");
		this.fieldRegexMatch = awk.compileExpression("$1 ~ /test/");
		this.multiStringConcatenation = awk.compileExpression("$1 \" test1\" \" test2\" \" test3\"");
		this.mixedExpression = awk.compileExpression("($1 + $2) \":\" ($3 ~ /test/) \":\" $4");
		this.avm = new AVM(new AwkSettings(), Collections.emptyMap());
		this.avm.prepareForEval("42 3.14 test-value suffix");
	}

	/**
	 * Closes the reusable interpreter after the benchmark trial.
	 *
	 * @throws IOException if closing runtime resources fails
	 */
	@TearDown(Level.Trial)
	public void tearDown() throws IOException {
		this.avm.close();
	}

	/**
	 * Measures literal numeric addition through the compiled expression path.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object literalAddition() throws IOException {
		return this.avm.eval(this.literalAddition);
	}

	/**
	 * Measures field-to-number conversion followed by addition.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object fieldAddition() throws IOException {
		return this.avm.eval(this.fieldAddition);
	}

	/**
	 * Measures field-to-number conversion followed by multiplication.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object fieldMultiplication() throws IOException {
		return this.avm.eval(this.fieldMultiplication);
	}

	/**
	 * Measures field string concatenation with a literal suffix.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object fieldConcatenation() throws IOException {
		return this.avm.eval(this.fieldConcatenation);
	}

	/**
	 * Measures field regular expression matching.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object fieldRegexMatch() throws IOException {
		return this.avm.eval(this.fieldRegexMatch);
	}

	/**
	 * Measures a longer chain of string concatenation operations.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object multiStringConcatenation() throws IOException {
		return this.avm.eval(this.multiStringConcatenation);
	}

	/**
	 * Measures mixed numeric, string, field, and regular expression operations.
	 *
	 * @return expression result
	 * @throws IOException if input preparation or evaluation fails
	 */
	@Benchmark
	public Object mixedExpression() throws IOException {
		return this.avm.eval(this.mixedExpression);
	}
}
