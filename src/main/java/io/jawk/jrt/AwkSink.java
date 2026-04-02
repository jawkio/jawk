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
import java.math.BigDecimal;
import java.util.Locale;
import org.metricshub.printf4j.Printf4J;

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
	 * @param format format string passed to {@code printf}
	 * @param values arguments supplied after the format string
	 * @throws IOException if the sink cannot write the output
	 */
	public abstract void printf(String ofs, String ors, String ofmt, String format, Object... values)
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
	 * The default implementation throws {@link UnsupportedOperationException}.
	 * Override this method in sinks that need to handle process output.
	 * </p>
	 *
	 * @return print stream that should receive raw process output
	 * @throws UnsupportedOperationException if this sink does not support raw process output
	 */
	public PrintStream getPrintStream() {
		throw new UnsupportedOperationException("This sink does not support raw process output.");
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
	 * AWK applies {@code OFMT} not only to numeric values, but also to strings
	 * that can be interpreted numerically. This helper preserves that behaviour
	 * for text-based sinks.
	 * </p>
	 *
	 * @param value operand to format
	 * @param ofmt numeric output format
	 * @return the textual representation AWK would print for this operand
	 */
	protected final String formatPrintArgument(Object value, String ofmt) {
		return formatOutputValue(normalizePrintArgument(value), ofmt, locale);
	}

	/**
	 * Formats one {@code printf} result string using this sink's locale.
	 *
	 * @param format format string passed to {@code printf}
	 * @param values arguments supplied after the format string
	 * @return formatted text
	 */
	protected final String formatPrintfResult(String format, Object... values) {
		Object[] safeValues = values == null ? new Object[0] : values;
		return Printf4J.sprintf(locale, format, safeValues);
	}

	/**
	 * Converts a {@code print} operand into the value shape AWK uses before it
	 * applies {@code OFMT}.
	 * <p>
	 * When a non-numeric object renders as a numeric string, AWK treats it as a
	 * number for plain {@code print}. Structured sinks can reuse this helper when
	 * they want text output compatible with standard AWK behaviour.
	 * </p>
	 *
	 * @param value operand to normalize
	 * @return the normalized value, either unchanged or converted to a numeric form
	 */
	protected final Object normalizePrintArgument(Object value) {
		if (value == null || value instanceof Number) {
			return value;
		}
		try {
			return Double.valueOf(new BigDecimal(value.toString()).doubleValue());
		} catch (NumberFormatException e) {
			return value;
		}
	}

	/**
	 * Formats one already-normalized AWK output value.
	 *
	 * @param value value to format
	 * @param ofmt numeric output format
	 * @param locale locale used for numeric formatting
	 * @return textual output for {@code value}
	 */
	protected static String formatOutputValue(Object value, String ofmt, Locale locale) {
		if (value == null) {
			return "";
		}
		if (!(value instanceof Number)) {
			return value.toString();
		}

		double number = ((Number) value).doubleValue();
		if (JRT.isActuallyLong(number)) {
			return Long.toString((long) Math.rint(number));
		}

		try {
			String rendered = String.format(locale, ofmt, number);
			if ((rendered.indexOf('.') > -1 || rendered.indexOf(',') > -1)
					&& rendered.indexOf('e') == -1
					&& rendered.indexOf('E') == -1) {
				while (rendered.endsWith("0")) {
					rendered = rendered.substring(0, rendered.length() - 1);
				}
				if (rendered.endsWith(".") || rendered.endsWith(",")) {
					rendered = rendered.substring(0, rendered.length() - 1);
				}
			}
			return rendered;
		} catch (java.util.UnknownFormatConversionException e) {
			return "";
		}
	}
}
