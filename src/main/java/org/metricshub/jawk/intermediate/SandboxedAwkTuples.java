package org.metricshub.jawk.intermediate;

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

import org.metricshub.jawk.AwkSandboxException;

/**
 * Variant of {@link AwkTuples} that rejects tuple generation for operations not
 * permitted in sandbox mode.
 */
public class SandboxedAwkTuples extends AwkTuples {

	private static final long serialVersionUID = 1L;

	private static void deny(String message) {
		throw new AwkSandboxException(message);
	}

	@Override
	public void printToFile(int numExprs, boolean append) {
		deny("Output redirection is disabled in sandbox mode");
	}

	@Override
	public void printToPipe(int numExprs) {
		deny("Command execution through pipelines is disabled in sandbox mode");
	}

	@Override
	public void printfToFile(int numExprs, boolean append) {
		deny("Output redirection is disabled in sandbox mode");
	}

	@Override
	public void printfToPipe(int numExprs) {
		deny("Command execution through pipelines is disabled in sandbox mode");
	}

	@Override
	public void system() {
		deny("system() is disabled in sandbox mode");
	}

	@Override
	public void useAsCommandInput() {
		deny("Command execution through pipelines is disabled in sandbox mode");
	}

	@Override
	public void useAsFileInput() {
		deny("Input redirection is disabled in sandbox mode");
	}

	/**
	 * In sandbox mode, ARGC is read-only. Block any script attempt to assign
	 * to ARGC at compile time.
	 */
	@Override
	public void assignARGC() {
		deny("Assigning to ARGC is disabled in sandbox mode");
	}

	/**
	 * In sandbox mode, ARGC does not need to be materialized as a global
	 * variable because the script cannot alter it. The runtime falls back
	 * to the command-line argument count.
	 */
	@Override
	public void argcOffset(int offset) {
		// no-op: keep argcOffset at NULL_OFFSET; AVM.getARGC() returns the
		// command-line argument count when ARGC is not materialized.
	}

	/**
	 * In sandbox mode, ARGV does not need to be materialized as a global
	 * variable because the script cannot alter it. The runtime falls back
	 * to a synthetic ARGV built from command-line arguments.
	 */
	@Override
	public void argvOffset(int offset) {
		// no-op: keep argvOffset at NULL_OFFSET; AVM.getARGV() returns a
		// synthetic AssocArray when ARGV is not materialized.
	}
}
