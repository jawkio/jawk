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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Output target used by AWK {@code print} and {@code printf} statements.
 * <p>
 * Implementations decide how to represent AWK output, whether as text written
 * to a stream, appended characters, or structured values collected by the
 * embedding application. Numeric rendering uses the sink's immutable
 * construction-time locale.
 * </p>
 */
public abstract class AwkSink {

	private final Locale locale;

	/**
	 * Creates a sink using the default {@link Locale#US} formatting rules.
	 */
	protected AwkSink() {
		this(Locale.US);
	}

	/**
	 * Creates a sink using the supplied locale for numeric formatting.
	 *
	 * @param localeParam locale to use for numeric formatting
	 */
	protected AwkSink(Locale localeParam) {
		this.locale = localeParam == null ? Locale.US : localeParam;
	}

	/**
	 * Returns the locale used by this sink when it renders numeric values.
	 *
	 * @return sink locale
	 */
	public final Locale getLocale() {
		return locale;
	}

	/**
	 * Writes one AWK {@code print} operation.
	 *
	 * @param ofs output field separator
	 * @param ors output record separator
	 * @param ofmt numeric output format used by plain {@code print}
	 * @param values values supplied to {@code print}
	 * @throws IOException if the sink cannot write the output
	 */
	public abstract void print(String ofs, String ors, String ofmt, Object... values) throws IOException;

	/**
	 * Writes one AWK {@code printf} operation.
	 *
	 * @param ofs output field separator
	 * @param ors output record separator
	 * @param ofmt numeric output format available to the sink
	 * @param convfmt number-to-string conversion format ({@code CONVFMT}),
	 *        used by {@code %s} to convert numeric values the way AWK does
	 * @param format format string passed to {@code printf}
	 * @param values arguments supplied after the format string
	 * @throws IOException if the sink cannot write the output
	 */
	public abstract void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values)
			throws IOException;

	/**
	 * Flushes any buffered output held by this sink.
	 *
	 * @throws IOException if the sink cannot be flushed
	 */
	public void flush() throws IOException {
		// Most sinks do not buffer explicitly.
	}

	/**
	 * Returns a {@link PrintStream} view that receives raw process output written
	 * by spawned commands such as {@code system("...")}.
	 * <p>
	 * The default implementation returns a stream that silently discards all
	 * output. Override this method in sinks that need to capture process output.
	 * </p>
	 *
	 * @return print stream that should receive raw process output
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The shared discard stream is stateless and safe to expose.")
	public PrintStream getPrintStream() {
		return NULL_PRINT_STREAM;
	}

	/** Shared discard stream returned by the default {@link #getPrintStream()}. */
	private static final PrintStream NULL_PRINT_STREAM = newNullPrintStream();

	/**
	 * A shared no-op sink that silently discards all output.
	 * <p>
	 * This singleton is safe to share across all JRT/AVM instances because
	 * its {@link #print(String, String, String, Object...)},
	 * {@link #printf(String, String, String, String, String, Object...)}, and
	 * {@link #flush()} operations are all no-ops.
	 */
	public static final AwkSink NOP_SINK = new NoOpAwkSink();

	private static final class NoOpAwkSink extends AwkSink {

		NoOpAwkSink() {
			super();
		}

		@Override
		public void print(String ofs, String ors, String ofmt, Object... values) {
			// discard
		}

		@Override
		public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values) {
			// discard
		}
	}

	private static PrintStream newNullPrintStream() {
		try {
			return new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
					// discard
				}

				@Override
				public void write(byte[] b, int off, int len) {
					// discard
				}
			}, false, "UTF-8") {

				@Override
				public void close() {
					// Prevent closing; this stream is a shared singleton.
				}
			};
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Creates a sink backed by an {@link OutputStream}.
	 *
	 * @param outputStream stream that should receive AWK output
	 * @return sink writing to {@code outputStream}
	 */
	public static AwkSink from(OutputStream outputStream) {
		return from(outputStream, Locale.US);
	}

	/**
	 * Creates a sink backed by an {@link OutputStream}.
	 *
	 * @param outputStream stream that should receive AWK output
	 * @param locale locale to use for numeric formatting
	 * @return sink writing to {@code outputStream}
	 */
	public static AwkSink from(OutputStream outputStream, Locale locale) {
		return new OutputStreamAwkSink(outputStream, locale);
	}

	/**
	 * Creates a sink backed by a {@link PrintStream}.
	 *
	 * @param printStream stream that should receive AWK output
	 * @return sink writing to {@code printStream}
	 */
	public static AwkSink from(PrintStream printStream) {
		return from(printStream, Locale.US);
	}

	/**
	 * Creates a sink backed by a {@link PrintStream}.
	 *
	 * @param printStream stream that should receive AWK output
	 * @param locale locale to use for numeric formatting
	 * @return sink writing to {@code printStream}
	 */
	public static AwkSink from(PrintStream printStream, Locale locale) {
		return new OutputStreamAwkSink(printStream, locale);
	}

	/**
	 * Creates a sink backed by an {@link Appendable}.
	 *
	 * @param appendable appendable that should receive AWK output
	 * @return sink writing to {@code appendable}
	 */
	public static AwkSink from(Appendable appendable) {
		return from(appendable, Locale.US);
	}

	/**
	 * Creates a sink backed by an {@link Appendable}.
	 *
	 * @param appendable appendable that should receive AWK output
	 * @param locale locale to use for numeric formatting
	 * @return sink writing to {@code appendable}
	 */
	public static AwkSink from(Appendable appendable, Locale locale) {
		return new AppendableAwkSink(appendable, locale);
	}

	/**
	 * Formats one operand of a plain AWK {@code print} statement.
	 * <p>
	 * Numeric values are rendered with {@code OFMT} (or as integers when they
	 * hold an integral value). String values — including numeric strings that
	 * originate from input — are printed verbatim, as required by POSIX.
	 * </p>
	 *
	 * @param value operand to format
	 * @param ofmt numeric output format
	 * @return the textual representation AWK would print for this operand
	 */
	protected final String formatPrintArgument(Object value, String ofmt) {
		return AwkPrintf.toAwkString(value, ofmt, locale);
	}

	/**
	 * Formats a string in the same way as AWK's {@code sprintf()} built-in,
	 * converting numeric {@code %s} operands with the supplied {@code CONVFMT}
	 * value.
	 * <p>
	 * Subclasses may override this method to customize formatting. The default
	 * implementation delegates to
	 * {@link AwkPrintf#sprintf(Locale, String, String, Object...)}. The
	 * built-in sinks render {@code printf} output through this method, so
	 * overriding it keeps {@code printf} and {@code sprintf} consistent.
	 * </p>
	 *
	 * @param convfmt number-to-string conversion format ({@code CONVFMT})
	 * @param format format string
	 * @param values arguments supplied after the format string
	 * @return formatted text
	 */
	public String sprintf(String convfmt, String format, Object... values) {
		Object[] safeValues = values == null ? new Object[0] : values;
		return AwkPrintf.sprintf(locale, convfmt, format, safeValues);
	}
}
