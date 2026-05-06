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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.jawk.intermediate.Opcode;

/**
 * Snapshot of tuple and function execution statistics collected by a
 * {@link ProfilingAVM}.
 */
public final class ProfilingReport {

	private final List<Entry> tupleEntries;
	private final List<Entry> functionEntries;

	ProfilingReport(Map<Opcode, Accumulator> tupleStats, Map<String, Accumulator> functionStats) {
		this.tupleEntries = Collections.unmodifiableList(toTupleEntries(tupleStats));
		this.functionEntries = Collections.unmodifiableList(toFunctionEntries(functionStats));
	}

	/**
	 * Returns tuple execution statistics sorted by descending total time.
	 *
	 * @return tuple execution entries
	 */
	public List<Entry> getTupleEntries() {
		return tupleEntries;
	}

	/**
	 * Returns function execution statistics sorted by descending total time.
	 *
	 * @return function execution entries
	 */
	public List<Entry> getFunctionEntries() {
		return functionEntries;
	}

	/**
	 * Prints this profiling report to the supplied stream.
	 *
	 * @param out destination stream
	 */
	public void print(PrintStream out) {
		out.println("Jawk profiling report");
		out.println();
		printSection(out, "Tuple execution", tupleEntries);
		out.println();
		printSection(out, "Function execution", functionEntries);
	}

	private static void printSection(PrintStream out, String title, List<Entry> entries) {
		out.println(title + ":");
		if (entries.isEmpty()) {
			out.println("  (none)");
			return;
		}
		out.printf(Locale.ROOT, "  %-32s %12s %14s %14s%n", "Name", "Count", "Time (ms)", "Avg (ns)");
		for (Entry entry : entries) {
			out
					.printf(
							Locale.ROOT,
							"  %-32s %12d %14.3f %14.0f%n",
							entry.getName(),
							entry.getCount(),
							entry.getTotalNanos() / 1_000_000.0d,
							entry.getAverageNanos());
		}
	}

	private static List<Entry> toTupleEntries(Map<Opcode, Accumulator> stats) {
		List<Entry> entries = new ArrayList<Entry>(stats.size());
		for (Map.Entry<Opcode, Accumulator> entry : stats.entrySet()) {
			entries.add(new Entry(entry.getKey().name(), entry.getValue().count, entry.getValue().totalNanos));
		}
		sort(entries);
		return entries;
	}

	private static List<Entry> toFunctionEntries(Map<String, Accumulator> stats) {
		List<Entry> entries = new ArrayList<Entry>(stats.size());
		for (Map.Entry<String, Accumulator> entry : stats.entrySet()) {
			entries.add(new Entry(entry.getKey(), entry.getValue().count, entry.getValue().totalNanos));
		}
		sort(entries);
		return entries;
	}

	private static void sort(List<Entry> entries) {
		Collections
				.sort(
						entries,
						Comparator
								.comparingLong(Entry::getTotalNanos)
								.reversed()
								.thenComparing(Comparator.comparingLong(Entry::getCount).reversed())
								.thenComparing(Entry::getName));
	}

	static final class Accumulator {
		private long count;
		private long totalNanos;

		void add(long elapsedNanos) {
			count++;
			totalNanos += elapsedNanos;
		}
	}

	/**
	 * One profiling table row.
	 */
	public static final class Entry {
		private final String name;
		private final long count;
		private final long totalNanos;

		private Entry(String name, long count, long totalNanos) {
			this.name = name;
			this.count = count;
			this.totalNanos = totalNanos;
		}

		/**
		 * Returns the tuple type or function name.
		 *
		 * @return entry name
		 */
		public String getName() {
			return name;
		}

		/**
		 * Returns the number of observed executions.
		 *
		 * @return execution count
		 */
		public long getCount() {
			return count;
		}

		/**
		 * Returns the total elapsed time in nanoseconds.
		 *
		 * @return total elapsed nanoseconds
		 */
		public long getTotalNanos() {
			return totalNanos;
		}

		/**
		 * Returns the average elapsed time in nanoseconds.
		 *
		 * @return average elapsed nanoseconds
		 */
		public double getAverageNanos() {
			return count == 0 ? 0.0d : (double) totalNanos / count;
		}
	}
}
