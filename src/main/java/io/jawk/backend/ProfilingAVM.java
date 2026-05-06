package io.jawk.backend;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright 2006 - 2026 MetricsHub
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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import io.jawk.ext.ExtensionFunction;
import io.jawk.ext.JawkExtension;
import io.jawk.intermediate.Opcode;
import io.jawk.intermediate.PositionTracker;
import io.jawk.util.AwkSettings;

/**
 * {@link AVM} variant that collects tuple and function execution statistics.
 */
public class ProfilingAVM extends AVM {

	private final Map<Opcode, ProfilingReport.Accumulator> tupleStats = new EnumMap<Opcode, ProfilingReport.Accumulator>(
			Opcode.class);
	private final Map<String, ProfilingReport.Accumulator> functionStats = new LinkedHashMap<String, ProfilingReport.Accumulator>();
	private final Deque<ActiveFunction> activeFunctions = new ArrayDeque<ActiveFunction>();

	/**
	 * Creates a profiling AVM with default settings and no extensions.
	 */
	public ProfilingAVM() {
		this(null, Collections.<String, JawkExtension>emptyMap());
	}

	/**
	 * Creates a profiling AVM with the provided settings and extension instances.
	 *
	 * @param parameters Runtime settings to honor
	 * @param extensionInstances Available extension implementations
	 */
	public ProfilingAVM(AwkSettings parameters,
			Map<String, JawkExtension> extensionInstances) {
		super(parameters, extensionInstances);
	}

	@Override
	protected long beforeTupleExecution(PositionTracker position, Opcode opcode) {
		long now = System.nanoTime();
		if (opcode == Opcode.CALL_FUNCTION) {
			activeFunctions.push(new ActiveFunction(position.stringArg(1), now));
		} else if (opcode == Opcode.EXTENSION) {
			ExtensionFunction function = position.extensionFunctionArg();
			activeFunctions.push(new ActiveFunction(function.getKeyword(), now));
		}
		return now;
	}

	@Override
	protected void afterTupleExecution(PositionTracker position, Opcode opcode, long tupleStartNanos) {
		long now = System.nanoTime();
		statisticsFor(tupleStats, opcode).add(now - tupleStartNanos);
		if (opcode == Opcode.EXTENSION || opcode == Opcode.RETURN_FROM_FUNCTION) {
			recordFunctionExit(now);
		}
	}

	/**
	 * Clears all collected profiling statistics.
	 */
	public void resetProfiling() {
		tupleStats.clear();
		functionStats.clear();
		activeFunctions.clear();
	}

	/**
	 * Returns an immutable snapshot of the collected profiling statistics.
	 *
	 * @return profiling report snapshot
	 */
	public ProfilingReport getProfilingReport() {
		return new ProfilingReport(tupleStats, functionStats);
	}

	private static <K> ProfilingReport.Accumulator statisticsFor(
			Map<K, ProfilingReport.Accumulator> stats,
			K key) {
		ProfilingReport.Accumulator accumulator = stats.get(key);
		if (accumulator == null) {
			accumulator = new ProfilingReport.Accumulator();
			stats.put(key, accumulator);
		}
		return accumulator;
	}

	private void recordFunctionExit(long now) {
		if (activeFunctions.isEmpty()) {
			return;
		}
		ActiveFunction function = activeFunctions.pop();
		statisticsFor(functionStats, function.name).add(now - function.startNanos);
	}

	private static final class ActiveFunction {
		private final String name;
		private final long startNanos;

		private ActiveFunction(String name, long startNanos) {
			this.name = name;
			this.startNanos = startNanos;
		}
	}
}
