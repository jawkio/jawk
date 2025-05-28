package org.metricshub.jawk;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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

import java.io.InputStream;
import java.io.PrintStream;
import org.metricshub.jawk.util.AwkParameters;
import org.metricshub.jawk.util.AwkSettings;

/**
 * Entry point into the parsing, analysis, and execution/compilation
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a stand-alone application.
 * If you want to use Jawk as a library, please use {@see Awk}.
 *
 * @author Danny Daglas
 */
public final class Main {

	/**
	 * Prohibit the instantiation of this class, other than the
	 * way required by JSR 223.
	 */
	@SuppressWarnings("unused")
	private Main() {}

	/**
	 * Class constructor to support the JSR 223 scripting interface
	 * already provided by Java SE 6. No work is performed here
	 * to avoid SpotBugs CT_CONSTRUCTOR_THROW warnings.
	 *
	 * @param args String arguments from the command-line.
	 * @param is The input stream to use as stdin.
	 * @param os The output stream to use as stdout.
	 * @param es The output stream to use as stderr.
	 */
	public Main(String[] args, InputStream is, PrintStream os, PrintStream es) {
		this.args = args;
		this.is = is;
		this.os = os;
		this.es = es;
	}

	private String[] args;
	private InputStream is;
	private PrintStream os;
	private PrintStream es;

	/**
	 * Executes the awk engine using the parameters configured in the
	 * constructor.
	 *
	 * @throws Exception enables exceptions to propagate to the caller.
	 */
	public void invoke() throws Exception {
		System.setIn(is);
		System.setOut(os);
		System.setErr(es);

		AwkSettings settings = AwkParameters.parseCommandLineArguments(args);
		Awk awk = new Awk();
		awk.invoke(settings);
	}

	/**
	 * Convenience factory that replicates the previous constructor
	 * behaviour.
	 *
	 * @param args String arguments from the command-line.
	 * @param is The input stream to use as stdin.
	 * @param os The output stream to use as stdout.
	 * @param es The output stream to use as stderr.
	 * @return An initialised {@code Main} instance.
	 * @throws Exception enables exceptions to propagate to the caller.
	 */
	public static Main create(String[] args, InputStream is, PrintStream os, PrintStream es) throws Exception {
		Main main = new Main(args, is, os, es);
		main.invoke();
		return main;
	}

	/**
	 * The entry point to Jawk for the VM.
	 * <p>
	 * The main method is a simple call to the invoke method.
	 * The current implementation is basically as follows:
	 * <blockquote>
	 * <pre>
	 * System.exit(invoke(args));
	 * </pre>
	 * </blockquote>
	 *
	 * @param args Command line arguments to the VM.
	 */
	@SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
	public static void main(String[] args) {
		try {
			AwkSettings settings = AwkParameters.parseCommandLineArguments(args);
			Awk awk = new Awk();
			awk.invoke(settings);
		} catch (ExitException e) {
			System.exit(e.getCode());
		} catch (Exception e) {
			System.err.printf("%s: %s\n", e.getClass().getSimpleName(), e.getMessage());
			System.exit(1);
		}
	}
}
