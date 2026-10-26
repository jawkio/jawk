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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Verifies that the children of {@code system()} and of a command input pipe
 * ({@code "cmd" | getline}) inherit Jawk's standard input, as POSIX requires,
 * when Jawk reads the standard input of the JVM — and that embedded executions
 * bound to a custom stream keep the child's standard input closed.
 * <p>
 * The inheritance can only be observed across a real process boundary, so the
 * inheriting cases run through
 * {@link AwkTestSupport#runCliInFreshJvm(String, String, String)}, which
 * spawns the CLI in a fresh JVM whose standard input is redirected from a
 * file.
 */
public class SpawnedProcessStdinTest {

	@Test
	public void commandInputPipeChildReadsJawkStandardInput() throws Exception {
		String output = AwkTestSupport.runCliInFreshJvm(
				"cmd|getline child reads Jawk stdin",
				"BEGIN { \"sort\" | getline line; print \"[\" line \"]\" }",
				"hello\n");
		assertEquals("[hello]\n", output);
	}

	@Test
	public void systemChildReadsJawkStandardInput() throws Exception {
		String output = AwkTestSupport
				.runCliInFreshJvm("system() child reads Jawk stdin", "BEGIN { system(\"sort\") }", "zulu\nalpha\n");
		assertEquals("alpha\nzulu\n", output);
	}

	@Test
	public void embeddedExecutionKeepsChildStandardInputClosed() throws Exception {
		// A custom input stream cannot be lent to another OS process, so the
		// child sees end of input at once and getline returns 0
		AwkTestSupport.cliTest("embedded cmd|getline child gets no standard input")
				.script("BEGIN { n = (\"sort\" | getline line); print n \"[\" line \"]\" }")
				.stdin("never seen by the child\n")
				.expectLines("0[]")
				.runAndAssert();
	}
}
