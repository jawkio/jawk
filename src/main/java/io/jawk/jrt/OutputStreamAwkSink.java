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

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Text {@link AwkSink} backed by a {@link PrintStream}.
 */
public final class OutputStreamAwkSink extends AwkSink {

	private final PrintStream printStream;

	/**
	 * Creates a sink backed by an {@link OutputStream}.
	 *
	 * @param outputStream stream that should receive AWK output
	 */
	public OutputStreamAwkSink(OutputStream outputStream) {
		this(outputStream, Locale.US);
	}

	/**
	 * Creates a sink backed by an {@link OutputStream}.
	 *
	 * @param outputStream stream that should receive AWK output
	 * @param locale locale used for numeric formatting
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The provided stream is the sink's output target by design.")
	public OutputStreamAwkSink(OutputStream outputStream, Locale locale) {
		super(locale);
		Objects.requireNonNull(outputStream, "outputStream");
		if (outputStream instanceof PrintStream) {
			this.printStream = (PrintStream) outputStream;
		} else {
			try {
				this.printStream = new PrintStream(outputStream, false, StandardCharsets.UTF_8.name());
			} catch (java.io.UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * Creates a sink backed directly by a {@link PrintStream}.
	 *
	 * @param printStream stream that should receive AWK output
	 */
	public OutputStreamAwkSink(PrintStream printStream) {
		this(printStream, Locale.US);
	}

	/**
	 * Creates a sink backed directly by a {@link PrintStream}.
	 *
	 * @param printStream stream that should receive AWK output
	 * @param locale locale used for numeric formatting
	 */
	public OutputStreamAwkSink(PrintStream printStream, Locale locale) {
		super(locale);
		this.printStream = Objects.requireNonNull(printStream, "printStream");
	}

	@Override
	public void print(String ofs, String ors, String ofmt, Object... values) {
		for (int i = 0; i < values.length; i++) {
			printStream.print(formatPrintArgument(values[i], ofmt));
			if (i < values.length - 1) {
				printStream.print(ofs);
			}
		}
		printStream.print(ors);
		printStream.flush();
	}

	@Override
	public void printf(String ofs, String ors, String ofmt, String format, Object... values) {
		printStream.print(formatPrintfResult(format, values));
	}

	@Override
	public void flush() {
		printStream.flush();
	}

	@Override
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Callers need the live PrintStream used for process output pumping.")
	public PrintStream getPrintStream() {
		return printStream;
	}
}
