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

import java.io.IOException;
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
 * Microbenchmarks for short, precompiled AWK scripts that exercise runtime input
 * traversal, expression evaluation, and output formatting together.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class AwkScriptBenchmark {

	private Awk awk;
	private AwkProgram sumInputProgram;
	private AwkProgram projectMatchingProgram;
	private String mixedNumericInput;

	/**
	 * Compiles benchmark scripts once and initializes stable input records.
	 *
	 * @throws IOException if script compilation fails
	 */
	@Setup(Level.Trial)
	public void setup() throws IOException {
		this.awk = new Awk();
		this.sumInputProgram = this.awk
				.compile(
						"BEGIN { total = 0; }\n"
								+ "/^input/ { total += $2; }\n"
								+ "END { print total; }\n");
		this.projectMatchingProgram = this.awk
				.compile(
						"/^input/ { print $1, $2 + 1, ($2 ~ /^[0-9]/) }\n");
		this.mixedNumericInput = "input 10\n"
				+ "input 3.14\n"
				+ "input 0\n"
				+ "input -2\n"
				+ "input 1e02\n"
				+ "input non-numeric\n"
				+ "comment 1000000\n"
				+ "input 0xff\n";
	}

	/**
	 * Measures a simple numeric aggregation script over mixed input records.
	 *
	 * @return script output
	 * @throws IOException if execution fails
	 * @throws ExitException if the script exits non-zero
	 */
	@Benchmark
	public String sumInputValues() throws IOException, ExitException {
		return this.awk.script(this.sumInputProgram).input(this.mixedNumericInput).execute();
	}

	/**
	 * Measures field projection with numeric coercion and regular expression
	 * matching over the same stable input records.
	 *
	 * @return script output
	 * @throws IOException if execution fails
	 * @throws ExitException if the script exits non-zero
	 */
	@Benchmark
	public String projectMatchingValues() throws IOException, ExitException {
		return this.awk.script(this.projectMatchingProgram).input(this.mixedNumericInput).execute();
	}
}
