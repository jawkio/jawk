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

import java.io.IOException;
import java.io.PrintStream;
import org.metricshub.jawk.AwkSandboxException;

/**
 * Runtime component that raises {@link AwkSandboxException} when sandboxed code
 * attempts operations that would escape the sandbox.
 */
public class SandboxedJRT extends JRT {

	public SandboxedJRT(VariableManager vm) {
		super(vm);
	}

	@Override
	public PrintStream jrtGetPrintStream(String filename, boolean append) {
		return sandboxViolation("Output redirection is disabled in sandbox mode");
	}

	@Override
	public PrintStream jrtSpawnForOutput(String cmd) {
		return sandboxViolation("Command execution through pipelines is disabled in sandbox mode");
	}

	@Override
	public boolean jrtConsumeFileInput(String filename) throws IOException {
		return sandboxViolation("Input redirection is disabled in sandbox mode");
	}

	@Override
	public boolean jrtConsumeCommandInput(String cmd) throws IOException {
		return sandboxViolation("Command execution through pipelines is disabled in sandbox mode");
	}

	@Override
	public Integer jrtSystem(String cmd) {
		return sandboxViolation("system() is disabled in sandbox mode");
	}

	private static <T> T sandboxViolation(String message) {
		throw new AwkSandboxException(message);
	}
}
