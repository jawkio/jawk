package org.metricshub.jawk.jrt;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * An {@link InputSource} that reads records from an {@link InputStream},
 * traversing the {@code ARGV} array to open filenames and apply
 * {@code name=value} variable assignments exactly like the classic AWK
 * command-line flow.
 * <p>
 * When no filename arguments are present in {@code ARGV}, records are read
 * from the supplied default {@link InputStream} (usually {@code System.in}).
 * This class is the default {@link InputSource} used internally by the
 * runtime when no custom source has been configured via
 * {@code AwkSettings#setInputSource(...)}.
 * </p>
 * <p>
 * API note: this type is public to allow runtime wiring between packages, but
 * it is considered an internal implementation detail. Embedding applications
 * should implement {@link InputSource} directly rather than depend on this
 * class, whose behavior may change in future releases.
 * </p>
 *
 * @see InputSource
 */
public class StreamInputSource implements InputSource {

	private final InputStream defaultInput;
	private final VariableManager vm;
	private final JRT jrt;

	// ARGV traversal state
	private AssocArray arglistAa;
	private int arglistIdx;
	private int arglistMaxKey;
	private boolean hasFilenames;

	// Current reader and record
	private PartitioningReader partitioningReader;
	private boolean currentFromFilenameList;
	private String currentRecord;

	/**
	 * Creates a stream-backed input source.
	 *
	 * @param defaultInput the fallback input stream used when {@code ARGV}
	 *        contains no filename arguments (typically {@code System.in})
	 * @param vm the variable manager providing access to {@code ARGV} and
	 *        {@code ARGC}
	 * @param jrt the JRT instance used for string conversion and special
	 *        variable updates
	 */
	public StreamInputSource(InputStream defaultInput, VariableManager vm, JRT jrt) {
		this.defaultInput = Objects.requireNonNull(defaultInput, "defaultInput");
		this.vm = Objects.requireNonNull(vm, "vm");
		this.jrt = Objects.requireNonNull(jrt, "jrt");
	}

	/** {@inheritDoc} */
	@Override
	public boolean nextRecord() throws IOException {
		initializeArgList();

		while (true) {
			if ((partitioningReader == null || currentRecord == null)
					&& !prepareNextReader()) {
				return false;
			}

			currentRecord = partitioningReader.readRecord();
			if (currentRecord != null) {
				currentFromFilenameList = partitioningReader.fromFilenameList();
				return true;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getRecord() {
		return currentRecord;
	}

	/**
	 * Always returns {@code null} so that the runtime splits {@code $0} using
	 * the current field separator (FS).
	 *
	 * @return {@code null}
	 */
	@Override
	public List<String> getFields() {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isFromFilenameList() {
		return currentFromFilenameList;
	}

	/**
	 * Propagates a record-separator change to the active
	 * {@link PartitioningReader}.
	 *
	 * @param rs the new record separator value
	 */
	public void setRecordSeparator(String rs) {
		if (partitioningReader != null) {
			partitioningReader.setRecordSeparator(rs);
		}
	}

	/**
	 * Returns the underlying {@link PartitioningReader} currently in use, or
	 * {@code null} if no reader has been opened yet.
	 *
	 * @return the active reader, or {@code null}
	 */
	PartitioningReader getPartitioningReader() {
		return partitioningReader;
	}

	// ------------------------------------------------------------------
	// ARGV traversal logic (moved from JRT)
	// ------------------------------------------------------------------

	/**
	 * Initialize internal state for traversing {@code ARGV}.
	 */
	private void initializeArgList() {
		if (arglistAa != null) {
			return;
		}
		arglistAa = (AssocArray) vm.getARGV();
		arglistMaxKey = computeMaxArgvKey();
		arglistIdx = 1;
		hasFilenames = detectFilenames();
	}

	/**
	 * Compute the highest numeric key present in the current {@code arglistAa}.
	 *
	 * @return the maximum integer key, or {@code 0} when the array is empty
	 */
	private int computeMaxArgvKey() {
		int max = 0;
		for (Object key : arglistAa.keySet()) {
			int idx = (int) JRT.toLong(key);
			if (idx > max) {
				max = idx;
			}
		}
		return max;
	}

	/**
	 * Determine whether {@code ARGV} contains any filename entries (arguments
	 * without an equals sign).
	 *
	 * @return {@code true} if at least one filename was found
	 */
	private boolean detectFilenames() {
		int traversalArgCount = getTraversalArgCount();
		for (int i = 1; i < traversalArgCount; i++) {
			if (arglistAa.isIn(i)) {
				String arg = jrt.toAwkString(arglistAa.get(i));
				if (arg.isEmpty() || arg.indexOf('=') > 0) {
					continue;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieve the number of command-line arguments supplied to the script.
	 *
	 * @return {@code ARGC} converted to an {@code int}
	 */
	private int getArgCount() {
		long raw = JRT.toLong(vm.getARGC());
		if (raw <= 0) {
			return 0;
		}
		if (raw > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) raw;
	}

	/**
	 * Return the effective upper bound for ARGV traversal, capped by the
	 * highest known ARGV key so that absurdly large ARGC values do not
	 * cause unbounded iteration over missing entries.
	 *
	 * @return the capped traversal count
	 */
	private int getTraversalArgCount() {
		int argCount = getArgCount();
		if (argCount <= 0) {
			return 0;
		}
		return Math.min(argCount, arglistMaxKey + 1);
	}

	/**
	 * Obtain the next valid argument from {@code ARGV}, skipping
	 * uninitialized or empty entries.
	 *
	 * @return the next argument as an AWK string, or {@code null} if none
	 *         remain
	 */
	private String nextArgument() {
		int traversalArgCount = getTraversalArgCount();
		while (arglistIdx < traversalArgCount) {
			int idx = arglistIdx++;
			if (!arglistAa.isIn(idx)) {
				continue;
			}
			String arg = jrt.toAwkString(arglistAa.get(idx));
			if (!arg.isEmpty()) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * Prepare the {@link PartitioningReader} for the next input source. This
	 * may be a filename, a variable assignment, or standard input if no
	 * filenames remain.
	 *
	 * @return {@code true} if a reader was prepared, {@code false} if no more
	 *         input is available
	 * @throws IOException if an I/O error occurs while opening a file
	 */
	private boolean prepareNextReader() throws IOException {
		boolean ready = false;
		arglistMaxKey = computeMaxArgvKey();
		hasFilenames = detectFilenames();
		while (!ready) {
			String arg = nextArgument();
			if (arg == null) {
				// ARGC/ARGV may have changed while evaluating assignments.
				hasFilenames = detectFilenames();
				if (partitioningReader == null && !hasFilenames) {
					partitioningReader = new PartitioningReader(
							new InputStreamReader(defaultInput, StandardCharsets.UTF_8),
							jrt.getRSString());
					jrt.setFILENAMEViaJrt("");
					return true;
				}
				closeCurrentReaderIfFileStream();
				return false;
			}
			if (arg.indexOf('=') > 0) {
				setFilelistVariable(arg);
				// Recompute bounds so ARGC changes are reflected immediately.
				arglistMaxKey = computeMaxArgvKey();
				hasFilenames = detectFilenames();
				if (partitioningReader == null && !hasFilenames) {
					partitioningReader = new PartitioningReader(
							new InputStreamReader(defaultInput, StandardCharsets.UTF_8),
							jrt.getRSString());
					jrt.setFILENAMEViaJrt("");
					return true;
				}
				if (partitioningReader != null) {
					jrt.setNR(jrt.getNR() + 1);
				}
			} else {
				closeCurrentReaderIfFileStream();
				partitioningReader = new PartitioningReader(
						new InputStreamReader(new FileInputStream(arg), StandardCharsets.UTF_8),
						jrt.getRSString(),
						true);
				jrt.setFILENAMEViaJrt(arg);
				jrt.setFNR(0L);
				ready = true;
			}
		}
		return true;
	}

	/**
	 * Closes the current {@link PartitioningReader} if it wraps a file stream
	 * (not {@code defaultInput}). This prevents file-descriptor leaks when
	 * traversing multiple ARGV files.
	 */
	private void closeCurrentReaderIfFileStream() {
		if (partitioningReader != null && partitioningReader.fromFilenameList()) {
			try {
				partitioningReader.close();
			} catch (IOException ignored) {
				// Best-effort close; the file is no longer needed.
			}
		}
	}

	/**
	 * Parse a {@code name=value} argument from the command line and assign it
	 * to the corresponding AWK variable.
	 *
	 * @param nameValue argument in the form {@code name=value}
	 */
	private void setFilelistVariable(String nameValue) {
		int eqIdx = nameValue.indexOf('=');
		assert eqIdx >= 0;
		if (eqIdx == 0) {
			throw new IllegalArgumentException(
					"Must have a non-blank variable name in a name=value variable assignment argument.");
		}
		String name = nameValue.substring(0, eqIdx);
		String value = nameValue.substring(eqIdx + 1);
		Object obj;
		try {
			obj = Integer.parseInt(value);
		} catch (NumberFormatException nfe) {
			try {
				obj = Double.parseDouble(value);
			} catch (NumberFormatException nfe2) {
				obj = value;
			}
		}
		vm.assignVariable(name, obj);
	}
}
