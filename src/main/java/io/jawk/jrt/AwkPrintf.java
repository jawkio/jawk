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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWK's {@code printf}/{@code sprintf} formatting engine.
 * <p>
 * This class implements the POSIX AWK formatting semantics (as implemented by
 * gawk), which differ from both C's {@code printf()} and
 * {@link java.lang.String#format(String, Object...)} in several ways:
 * </p>
 * <ul>
 * <li>{@code %s} converts numeric values to strings with AWK's number-to-string
 * rules: integral values are printed without a fractional part, and other
 * values are formatted with {@code CONVFMT};</li>
 * <li>{@code %c} prints the character for a numeric code point, or the first
 * character of a string value;</li>
 * <li>{@code %i} is an alias for {@code %d}, and {@code %u} prints the value
 * as an unsigned 64-bit integer;</li>
 * <li>dynamic field width and precision ({@code *}) consume arguments, and
 * gawk-style positional specifiers ({@code %n$}) are honored;</li>
 * <li>integer conversions of values that exceed the 64-bit range fall back to
 * the full decimal expansion ({@code %d}/{@code %i}) or {@code %g} notation
 * ({@code %u}/{@code %o}/{@code %x}/{@code %X}), like gawk;</li>
 * <li>NaN and infinities print as {@code nan}, {@code inf}, and {@code -inf};</li>
 * <li>{@code %e}, {@code %f}, and {@code %g} round halfway cases to even, like
 * the C library used by gawk;</li>
 * <li>unknown conversion specifiers are printed verbatim without consuming an
 * argument, and a fatal {@link AwkRuntimeException} is raised when there are
 * not enough arguments to satisfy the format string.</li>
 * </ul>
 * <p>
 * This formatting logic was originally externalized in the
 * <a href="https://github.com/metricshub/printf4j">Printf4J</a> project, which
 * emulated glibc's {@code printf()}. It has been reincorporated into Jawk and
 * adapted to AWK's semantics.
 * </p>
 */
public final class AwkPrintf {

	/** Default AWK number-to-string conversion format ({@code CONVFMT}). */
	public static final String DEFAULT_CONVFMT = "%.6g";

	/** Conversion characters recognized as AWK format specifiers. */
	private static final String CONVERSION_CHARS = "diouxXeEfFgGaAcs";

	/** Length modifier characters accepted (and ignored) like gawk. */
	private static final String LENGTH_MODIFIERS = "hjlLtz";

	/** A one-character string holding the NUL character, printed by {@code %c} for empty values. */
	private static final String NUL_STRING = Character.toString((char) 0);

	/** 2^63 as a double, the first value beyond the signed 64-bit range. */
	private static final double TWO_POW_63 = 9.223372036854775808e18;

	/** 2^64 as a {@link BigInteger}, used for unsigned wrapping checks. */
	private static final BigInteger TWO_POW_64 = BigInteger.ONE.shiftLeft(64);

	/** No width or precision operand in the specifier. */
	private static final int OPERAND_NONE = 0;

	/** Width or precision given as literal digits in the format string. */
	private static final int OPERAND_FIXED = 1;

	/** Width or precision given as {@code *}, consuming a sequential argument. */
	private static final int OPERAND_STAR = 2;

	/** Width or precision given as {@code *n$}, referencing a positional argument. */
	private static final int OPERAND_STAR_POSITIONAL = 3;

	/** Shared flag set with every flag cleared, for segments that ignore flags. */
	private static final Flags NO_FLAGS = new Flags(false, false, false, false, false, false);

	/** Maximum number of parsed format strings kept in {@link #FORMAT_CACHE}. */
	private static final int FORMAT_CACHE_LIMIT = 256;

	/**
	 * Cache of parsed format strings. Parsing depends only on the format text,
	 * not on the locale or the arguments, and AWK programs typically reuse a
	 * handful of format strings (including {@code CONVFMT}) for every record.
	 */
	private static final ConcurrentHashMap<String, Segment[]> FORMAT_CACHE = new ConcurrentHashMap<String, Segment[]>();

	/** Maximum number of locales kept in {@link #LOCALE_SYMBOLS_CACHE}. */
	private static final int LOCALE_SYMBOLS_CACHE_LIMIT = 64;

	/**
	 * Per-locale decimal and grouping separator characters.
	 * {@link DecimalFormatSymbols#getInstance(Locale)} clones the symbols on
	 * every call, which is far too expensive to repeat for every conversion.
	 */
	private static final ConcurrentHashMap<Locale, LocaleSymbols> LOCALE_SYMBOLS_CACHE = new ConcurrentHashMap<Locale, LocaleSymbols>();

	private AwkPrintf() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Formats the given arguments with AWK's {@code sprintf()} semantics, using
	 * {@link Locale#US} and the default {@code CONVFMT} ({@code "%.6g"}).
	 *
	 * @param format AWK format string
	 * @param args arguments supplied after the format string
	 * @return the formatted text
	 * @throws AwkRuntimeException when there are not enough arguments to
	 *         satisfy the format string
	 */
	public static String sprintf(final String format, final Object... args) {
		return sprintf(Locale.US, DEFAULT_CONVFMT, format, args);
	}

	/**
	 * Formats the given arguments with AWK's {@code sprintf()} semantics.
	 *
	 * @param locale locale used for numeric formatting (decimal separator,
	 *        grouping separator for the {@code '} flag)
	 * @param convfmt number-to-string conversion format ({@code CONVFMT}) used
	 *        by {@code %s} for non-integral numeric values
	 * @param format AWK format string
	 * @param args arguments supplied after the format string
	 * @return the formatted text
	 * @throws AwkRuntimeException when there are not enough arguments to
	 *         satisfy the format string
	 */
	public static String sprintf(final Locale locale, final String convfmt, final String format, final Object... args) {
		Locale actualLocale = locale == null ? Locale.US : locale;
		// An explicitly empty CONVFMT stays empty, like gawk; only a null
		// (absent) format selects the default.
		String actualConvfmt = convfmt == null ? DEFAULT_CONVFMT : convfmt;
		Object[] actualArgs = args == null ? new Object[0] : args;
		return new AwkPrintfFormatter(actualLocale, actualConvfmt, format, actualArgs).format();
	}

	/**
	 * Converts a value to a string using AWK's number-to-string rules.
	 * <p>
	 * Non-numeric values are converted with {@code toString()}. Numeric values
	 * holding an integral value are printed without a fractional part (using
	 * the full decimal expansion when the value exceeds the 64-bit range), NaN
	 * and infinities print as {@code nan}, {@code inf}, and {@code -inf}, and
	 * all other numeric values are formatted with the supplied conversion
	 * format ({@code CONVFMT} or {@code OFMT}).
	 * </p>
	 *
	 * @param value value to convert
	 * @param conversionFormat number-to-string conversion format
	 * @param locale locale used for numeric formatting
	 * @return the AWK string value of {@code value}
	 */
	public static String toAwkString(final Object value, final String conversionFormat, final Locale locale) {
		if (value == null) {
			return "";
		}
		if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
			// Preserve exact 64-bit values that a double round-trip would corrupt.
			return Long.toString(((Number) value).longValue());
		}
		if (!(value instanceof Number)) {
			return value.toString();
		}
		return numberToAwkString(((Number) value).doubleValue(), conversionFormat, locale);
	}

	private static String numberToAwkString(final double number, final String conversionFormat, final Locale locale) {
		if (Double.isNaN(number)) {
			return "nan";
		}
		if (Double.isInfinite(number)) {
			return number > 0 ? "inf" : "-inf";
		}
		if (JRT.isActuallyLong(number)) {
			double rounded = Math.rint(number);
			if (rounded >= -TWO_POW_63 && rounded < TWO_POW_63) {
				return Long.toString((long) rounded);
			}
			// The exact binary value of the double is intended: it makes rounding match gawk's C library.
			return new BigDecimal(rounded).toBigInteger().toString(); // NOPMD
		}
		// An explicitly empty CONVFMT/OFMT stays empty, like gawk; only a
		// null (absent) format selects the default.
		String fmt = conversionFormat == null ? DEFAULT_CONVFMT : conversionFormat;
		return sprintf(locale, DEFAULT_CONVFMT, fmt, Double.valueOf(number));
	}

	/**
	 * Immutable set of conversion flags parsed from one format specifier.
	 */
	private static final class Flags {

		private final boolean leftJustify;
		private final boolean plusSign;
		private final boolean spaceSign;
		private final boolean zeroPad;
		private final boolean alternate;
		private final boolean grouping;

		Flags(boolean leftJustify, boolean plusSign, boolean spaceSign, boolean zeroPad, boolean alternate,
				boolean grouping) {
			this.leftJustify = leftJustify;
			this.plusSign = plusSign;
			this.spaceSign = spaceSign;
			this.zeroPad = zeroPad;
			this.alternate = alternate;
			this.grouping = grouping;
		}

		/**
		 * Returns these flags with the {@code '} grouping flag cleared, for
		 * conversions that gawk never groups (octal and hexadecimal).
		 *
		 * @return an equivalent flag set without grouping
		 */
		Flags withoutGrouping() {
			return grouping ? new Flags(leftJustify, plusSign, spaceSign, zeroPad, alternate, false) : this;
		}

		/**
		 * Returns these flags with the {@code -} flag set, as a negative
		 * dynamic field width requires.
		 *
		 * @return an equivalent flag set with left justification
		 */
		Flags withLeftJustify() {
			return leftJustify ? this : new Flags(true, plusSign, spaceSign, zeroPad, alternate, grouping);
		}
	}

	/**
	 * Decimal and grouping separator characters of one locale.
	 */
	private static final class LocaleSymbols {

		private final char decimalSeparator;
		private final char groupingSeparator;

		LocaleSymbols(char decimalSeparator, char groupingSeparator) {
			this.decimalSeparator = decimalSeparator;
			this.groupingSeparator = groupingSeparator;
		}
	}

	/**
	 * One parsed piece of a format string: either verbatim text or one
	 * conversion specifier. Segments are immutable and shared between all
	 * calls using the same format string, so rendering replays argument
	 * consumption, argument-mode tracking, and fatal parse errors in exactly
	 * the order the previous single-pass implementation produced them.
	 */
	private static final class Segment {

		/** Verbatim text to append, or {@code null} for a conversion. */
		private final String literal;

		/** Positional ({@code n$}) argument index, or 0 for sequential. */
		private final int argPosition;

		private final Flags flags;

		/** Width operand kind, one of the {@code OPERAND_*} constants. */
		private final int widthKind;

		/** Fixed width value, or the {@code *n$} width argument position. */
		private final int width;

		/** Precision operand kind, one of the {@code OPERAND_*} constants. */
		private final int precisionKind;

		/** Fixed precision value, or the {@code *n$} precision argument position. */
		private final int precision;

		/** Conversion character, or {@code '\0'} for literal segments. */
		private final char conversion;

		/**
		 * Fatal parse error raised when rendering reaches this segment (after
		 * the width operand has consumed its argument), or {@code null}.
		 */
		private final String parseError;

		Segment(String literal, int argPosition, Flags flags, int widthKind, int width, int precisionKind, int precision,
				char conversion, String parseError) {
			this.literal = literal;
			this.argPosition = argPosition;
			this.flags = flags;
			this.widthKind = widthKind;
			this.width = width;
			this.precisionKind = precisionKind;
			this.precision = precision;
			this.conversion = conversion;
			this.parseError = parseError;
		}
	}

	/**
	 * Returns the decimal and grouping separators of the given locale, from a
	 * bounded cache.
	 */
	private static LocaleSymbols localeSymbolsFor(Locale locale) {
		LocaleSymbols symbols = LOCALE_SYMBOLS_CACHE.get(locale);
		if (symbols == null) {
			DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);
			symbols = new LocaleSymbols(dfs.getDecimalSeparator(), dfs.getGroupingSeparator());
			if (LOCALE_SYMBOLS_CACHE.size() >= LOCALE_SYMBOLS_CACHE_LIMIT) {
				LOCALE_SYMBOLS_CACHE.clear();
			}
			LOCALE_SYMBOLS_CACHE.putIfAbsent(locale, symbols);
		}
		return symbols;
	}

	/**
	 * Returns the parsed form of the given format string, from a bounded
	 * cache.
	 */
	private static Segment[] compiledFormat(String format) {
		Segment[] segments = FORMAT_CACHE.get(format);
		if (segments == null) {
			segments = compileFormat(format);
			if (FORMAT_CACHE.size() >= FORMAT_CACHE_LIMIT) {
				// Rare: a program cycling through many generated format
				// strings. Dropping the whole cache keeps the bound simple
				// and the hit path lock-free.
				FORMAT_CACHE.clear();
			}
			Segment[] winner = FORMAT_CACHE.putIfAbsent(format, segments);
			if (winner != null) {
				return winner;
			}
		}
		return segments;
	}

	/**
	 * Parses a format string into segments. This method never throws: fatal
	 * parse errors are recorded as error segments so that rendering raises
	 * them only once every preceding conversion has consumed its arguments,
	 * preserving the error precedence of single-pass processing.
	 */
	private static Segment[] compileFormat(String format) {
		List<Segment> segments = new ArrayList<Segment>();
		StringBuilder literal = new StringBuilder();
		int length = format.length();
		int i = 0;
		while (i < length) {
			char c = format.charAt(i);
			if (c != '%') {
				literal.append(c);
				i++;
				continue;
			}
			if (i + 1 >= length) {
				// Dangling '%' at the end of the format: print it verbatim.
				literal.append('%');
				break;
			}
			if (format.charAt(i + 1) == '%') {
				literal.append('%');
				i += 2;
				continue;
			}
			i = compileSpecifier(format, i, segments, literal);
			if (i < 0) {
				// A fatal parse error segment was emitted; the rest of the
				// format string is unreachable.
				break;
			}
		}
		flushLiteral(segments, literal);
		return segments.toArray(new Segment[0]);
	}

	/**
	 * Parses one format specifier starting at {@code start} (which points at
	 * the {@code '%'}), emitting segments, and returns the index of the first
	 * character after the specifier, or a negative value when a fatal parse
	 * error segment was emitted.
	 */
	private static int compileSpecifier(String format, int start, List<Segment> segments, StringBuilder literal) {
		int length = format.length();
		int i = start + 1;

		// gawk-style positional specifier: %n$...
		int argPosition = 0;
		int digitsEnd = i;
		while (digitsEnd < length && isAsciiDigit(format.charAt(digitsEnd))) {
			digitsEnd++;
		}
		if (digitsEnd > i && digitsEnd < length && format.charAt(digitsEnd) == '$') {
			argPosition = parseInt(format, i, digitsEnd);
			if (argPosition <= 0) {
				emitError(segments, literal, OPERAND_NONE, 0, "argument index with `$' must be > 0 in `" + format + "'");
				return -1;
			}
			i = digitsEnd + 1;
		}

		// Flags, in any order and possibly repeated.
		boolean leftJustify = false;
		boolean plusSign = false;
		boolean spaceSign = false;
		boolean zeroPad = false;
		boolean alternate = false;
		boolean grouping = false;
		flagLoop: while (i < length) {
			switch (format.charAt(i)) {
			case '-':
				leftJustify = true;
				break;
			case '+':
				plusSign = true;
				break;
			case ' ':
				spaceSign = true;
				break;
			case '0':
				zeroPad = true;
				break;
			case '#':
				alternate = true;
				break;
			case '\'':
				grouping = true;
				break;
			default:
				break flagLoop;
			}
			i++;
		}

		// Field width: digits, or '*' (optionally '*n$').
		int widthKind = OPERAND_NONE;
		int width = -1;
		if (i < length && format.charAt(i) == '*') {
			i++;
			int starArgEnd = starPositionEnd(format, i);
			if (starArgEnd == i && i < length && isAsciiDigit(format.charAt(i))) {
				// Digits after a star operand not terminated by '$' are
				// fatal rather than literal, like gawk: "%*2d".
				emitError(
						segments,
						literal,
						OPERAND_NONE,
						0,
						"no `$' supplied for positional field width or precision in `" + format + "'");
				return -1;
			}
			if (starArgEnd > i) {
				widthKind = OPERAND_STAR_POSITIONAL;
				width = parseInt(format, i, starArgEnd - 1);
				i = starArgEnd;
			} else {
				widthKind = OPERAND_STAR;
			}
		} else {
			int widthEnd = i;
			while (widthEnd < length && isAsciiDigit(format.charAt(widthEnd))) {
				widthEnd++;
			}
			if (widthEnd > i) {
				widthKind = OPERAND_FIXED;
				width = parseInt(format, i, widthEnd);
				i = widthEnd;
			}
		}

		// Precision: '.' followed by digits (empty means 0), or '.*'.
		int precisionKind = OPERAND_NONE;
		int precision = -1;
		if (i < length && format.charAt(i) == '.') {
			i++;
			if (i < length && format.charAt(i) == '*') {
				i++;
				int starArgEnd = starPositionEnd(format, i);
				if (starArgEnd == i && i < length && isAsciiDigit(format.charAt(i))) {
					// The width operand (if any) still consumes its argument
					// before this error is raised, like gawk: "%*.*2d".
					emitError(
							segments,
							literal,
							widthKind,
							width,
							"no `$' supplied for positional field width or precision in `" + format + "'");
					return -1;
				}
				if (starArgEnd > i) {
					precisionKind = OPERAND_STAR_POSITIONAL;
					precision = parseInt(format, i, starArgEnd - 1);
					i = starArgEnd;
				} else {
					precisionKind = OPERAND_STAR;
				}
			} else {
				int precisionEnd = i;
				while (precisionEnd < length && isAsciiDigit(format.charAt(precisionEnd))) {
					precisionEnd++;
				}
				precisionKind = OPERAND_FIXED;
				precision = precisionEnd == i ? 0 : parseInt(format, i, precisionEnd);
				i = precisionEnd;
			}
		}

		// Length modifiers (h, j, l, L, t, z) are each accepted at most
		// once and ignored, like gawk. A repeated modifier such as "ll"
		// or "hh" makes the whole specifier invalid, also like gawk.
		int modifierMask = 0;
		while (i < length) {
			int modifierIndex = LENGTH_MODIFIERS.indexOf(format.charAt(i));
			if (modifierIndex < 0 || (modifierMask & 1 << modifierIndex) != 0) {
				break;
			}
			modifierMask |= 1 << modifierIndex;
			i++;
		}

		if (i < length && format.charAt(i) == '%') {
			// A percent conversion reached through flags, width, or
			// precision prints a plain '%' and ignores them all, like
			// gawk ("%5%" prints "%"). Star operands still consume their
			// arguments, and an explicit position still pins the format to
			// positional mode, also like gawk.
			emitVerbatim(
					segments,
					literal,
					new Segment("%", argPosition, NO_FLAGS, widthKind, width, precisionKind, precision, (char) 0, null));
			return i + 1;
		}

		if (i >= length || CONVERSION_CHARS.indexOf(format.charAt(i)) < 0) {
			// Unknown or unterminated conversion: print the specifier
			// verbatim (including the offending character) without
			// consuming a value argument, like gawk. Star operands still
			// consume their arguments, and an explicit position still pins
			// the format to positional mode, also like gawk.
			int end = i < length ? i + 1 : length;
			emitVerbatim(
					segments,
					literal,
					new Segment(
							format.substring(start, end),
							argPosition,
							NO_FLAGS,
							widthKind,
							width,
							precisionKind,
							precision,
							(char) 0,
							null));
			return end;
		}

		char conversion = format.charAt(i);
		i++;

		flushLiteral(segments, literal);
		segments.add(
				new Segment(
						null,
						argPosition,
						new Flags(leftJustify, plusSign, spaceSign, zeroPad, alternate, grouping),
						widthKind,
						width,
						precisionKind,
						precision,
						conversion,
						null));
		return i;
	}

	/**
	 * Emits verbatim specifier text (a {@code %} conversion or an unknown
	 * conversion). When the specifier has no side effects (no star operand to
	 * consume, no positional index to validate), the text is merged into the
	 * pending literal run; otherwise a standalone segment replays those side
	 * effects before appending the text.
	 */
	private static void emitVerbatim(List<Segment> segments, StringBuilder literal, Segment segment) {
		boolean sideEffectFree = segment.argPosition == 0
				&& segment.widthKind != OPERAND_STAR
				&& segment.widthKind != OPERAND_STAR_POSITIONAL
				&& segment.precisionKind != OPERAND_STAR
				&& segment.precisionKind != OPERAND_STAR_POSITIONAL;
		if (sideEffectFree) {
			literal.append(segment.literal);
			return;
		}
		flushLiteral(segments, literal);
		segments.add(segment);
	}

	/**
	 * Emits a fatal parse error segment, preceded by any width operand whose
	 * argument consumption must be replayed before the error is raised.
	 */
	private static void emitError(
			List<Segment> segments,
			StringBuilder literal,
			int widthKind,
			int width,
			String message) {
		flushLiteral(segments, literal);
		segments.add(new Segment(null, 0, NO_FLAGS, widthKind, width, OPERAND_NONE, -1, (char) 0, message));
	}

	/** Emits the pending literal text run as a segment, if any. */
	private static void flushLiteral(List<Segment> segments, StringBuilder literal) {
		if (literal.length() > 0) {
			segments.add(new Segment(literal.toString(), 0, NO_FLAGS, OPERAND_NONE, -1, OPERAND_NONE, -1, (char) 0, null));
			literal.setLength(0);
		}
	}

	/**
	 * Returns the index right after a {@code n$} sequence starting at
	 * {@code i}, or {@code i} when there is no such sequence.
	 */
	private static int starPositionEnd(String format, int i) {
		int length = format.length();
		int digitsEnd = i;
		while (digitsEnd < length && isAsciiDigit(format.charAt(digitsEnd))) {
			digitsEnd++;
		}
		if (digitsEnd > i && digitsEnd < length && format.charAt(digitsEnd) == '$') {
			return digitsEnd + 1;
		}
		return i;
	}

	/**
	 * Stateful renderer for one {@code sprintf()} call, walking the parsed
	 * segments of the format string.
	 */
	private static final class AwkPrintfFormatter {

		private final Locale locale;
		private final String convfmt;
		private final String format;
		private final Object[] args;
		private final StringBuilder out;
		private final char decimalSeparator;
		private final char groupingSeparator;

		/** Index of the next sequential argument to consume. */
		private int argIndex;

		/** Whether a positional ({@code n$}) argument reference was seen. */
		private boolean sawPositional;

		/** Whether a sequential argument reference was seen. */
		private boolean sawSequential;

		AwkPrintfFormatter(Locale locale, String convfmt, String format, Object[] args) {
			this.locale = locale;
			this.convfmt = convfmt;
			this.format = format;
			this.args = args;
			this.out = new StringBuilder(format.length() + 16);
			LocaleSymbols symbols = localeSymbolsFor(locale);
			this.decimalSeparator = symbols.decimalSeparator;
			this.groupingSeparator = symbols.groupingSeparator;
		}

		String format() {
			for (Segment segment : compiledFormat(format)) {
				renderSegment(segment);
			}
			return out.toString();
		}

		/**
		 * Renders one segment: evaluates the width and precision operands
		 * (consuming their arguments), raises any recorded parse error, and
		 * appends the literal text or the converted value.
		 */
		private void renderSegment(Segment segment) {
			Flags flags = segment.flags;
			int width = -1;
			if (segment.widthKind != OPERAND_NONE) {
				if (segment.widthKind == OPERAND_FIXED) {
					width = segment.width;
				} else {
					long dynamicWidth;
					if (segment.widthKind == OPERAND_STAR) {
						// A sequential star operand pins the format to
						// sequential mode; an explicitly positioned one is
						// neutral.
						recordArgumentMode(false);
						dynamicWidth = (long) JRT.toDouble(nextArg());
					} else {
						// gawk treats a zero-indexed star operand ("%*0$d")
						// as the value zero, without consuming an argument.
						dynamicWidth = segment.width == 0 ? 0 : (long) JRT.toDouble(argAt(segment.width));
					}
					if (dynamicWidth < 0) {
						flags = flags.withLeftJustify();
						dynamicWidth = -dynamicWidth;
					}
					width = (int) Math.min(dynamicWidth, Integer.MAX_VALUE);
				}
			}
			if (segment.parseError != null) {
				throw new AwkRuntimeException(segment.parseError);
			}
			int precision = -1;
			if (segment.precisionKind != OPERAND_NONE) {
				if (segment.precisionKind == OPERAND_FIXED) {
					precision = segment.precision;
				} else {
					long dynamicPrecision;
					if (segment.precisionKind == OPERAND_STAR) {
						// Same sequential-mode tracking as the width operand.
						recordArgumentMode(false);
						dynamicPrecision = (long) JRT.toDouble(nextArg());
					} else {
						// Same zero-index rule as the width operand.
						dynamicPrecision = segment.precision == 0 ? 0 : (long) JRT.toDouble(argAt(segment.precision));
					}
					// A negative dynamic precision means "no precision" in C.
					if (dynamicPrecision >= 0) {
						precision = (int) Math.min(dynamicPrecision, Integer.MAX_VALUE);
					}
				}
			}
			if (segment.literal != null) {
				// Verbatim text; a positional specifier still pins the format
				// to positional mode and validates its index, like gawk.
				if (segment.argPosition > 0) {
					recordArgumentMode(true);
					requireArgumentIndex(segment.argPosition);
				}
				out.append(segment.literal);
				return;
			}
			recordArgumentMode(segment.argPosition > 0);
			Object arg = segment.argPosition > 0 ? argAt(segment.argPosition) : nextArg();
			render(segment.conversion, flags, width, precision, arg);
		}

		private Object nextArg() {
			if (argIndex >= args.length) {
				throw new AwkRuntimeException("not enough arguments to satisfy format string `" + format + "'");
			}
			return args[argIndex++];
		}

		private Object argAt(int position) {
			requireArgumentIndex(position);
			return args[position - 1];
		}

		/**
		 * Validates a positional ({@code n$}) argument index against the
		 * supplied arguments, like gawk, which checks the index even for
		 * conversions that do not consume the referenced value.
		 */
		private void requireArgumentIndex(int position) {
			if (position <= 0) {
				throw new AwkRuntimeException("argument index with `$' must be > 0 in `" + format + "'");
			}
			if (position > args.length) {
				throw new AwkRuntimeException(
						"argument index " + position + " greater than total number of supplied arguments in `" + format + "'");
			}
		}

		/**
		 * Records how one conversion selects its value argument and rejects
		 * format strings that mix positional ({@code n$}) and sequential
		 * conversions, like gawk. Star width and precision operands are not
		 * tracked: gawk allows an explicitly positioned star operand
		 * ({@code %*2$s}) alongside sequential conversions.
		 *
		 * @param positional whether the conversion used an {@code n$} index
		 */
		private void recordArgumentMode(boolean positional) {
			if (positional) {
				sawPositional = true;
			} else {
				sawSequential = true;
			}
			if (sawPositional && sawSequential) {
				throw new AwkRuntimeException("must use `count$' on all formats or none in `" + format + "'");
			}
		}

		private void render(char conversion, Flags flags, int width, int precision, Object arg) {
			switch (conversion) {
			case 'c':
				appendPadded(characterOf(arg), flags.leftJustify, false, width);
				break;
			case 's':
				String s = toAwkString(arg, convfmt, locale);
				if (precision >= 0 && s.codePointCount(0, s.length()) > precision) {
					// The precision counts characters (code points), so it
					// can never split a surrogate pair.
					s = s.substring(0, s.offsetByCodePoints(0, precision));
				}
				appendPadded(s, flags.leftJustify, false, width);
				break;
			case 'd':
			case 'i':
				renderSignedInteger(flags, width, precision, arg);
				break;
			case 'u':
			case 'o':
			case 'x':
			case 'X':
				renderUnsignedInteger(conversion, flags, width, precision, arg);
				break;
			case 'e':
			case 'E':
			case 'f':
			case 'F':
			case 'g':
			case 'G':
			case 'a':
			case 'A':
				renderFloat(conversion, flags, width, precision, arg);
				break;
			default:
				// Unreachable: the caller only passes known conversions.
				break;
			}
		}

		/** Renders the {@code %c} character for the given argument. */
		private String characterOf(Object arg) {
			if (arg == null) {
				return NUL_STRING;
			}
			boolean numeric = arg instanceof Number || (arg instanceof StrNum && ((StrNum) arg).isNumber());
			if (numeric) {
				long code = (long) JRT.toDouble(arg);
				StringBuilder sb = new StringBuilder(2);
				if (code >= 0 && code <= Character.MAX_CODE_POINT) {
					sb.appendCodePoint((int) code);
				} else {
					sb.append((char) code);
				}
				return sb.toString();
			}
			String s = arg.toString();
			if (s.isEmpty()) {
				return NUL_STRING;
			}
			StringBuilder sb = new StringBuilder(2);
			sb.appendCodePoint(s.codePointAt(0));
			return sb.toString();
		}

		private void renderSignedInteger(Flags flags, int width, int precision, Object arg) {
			double d = JRT.toDouble(arg);
			if (renderNonFinite('d', flags, width, d)) {
				return;
			}

			boolean negative;
			String magnitude;
			if (arg instanceof Long || arg instanceof Integer || arg instanceof Short || arg instanceof Byte) {
				long v = ((Number) arg).longValue();
				negative = v < 0;
				magnitude = negative ? Long.toUnsignedString(-v) : Long.toString(v);
			} else if (d >= -TWO_POW_63 && d < TWO_POW_63) {
				long v = (long) d;
				negative = v < 0;
				magnitude = negative ? Long.toUnsignedString(-v) : Long.toString(v);
			} else {
				// Out of 64-bit range: print the full decimal expansion of the
				// (integral) double, like gawk.
				// The exact binary value of the double is intended: it makes rounding match gawk's C library.
				BigInteger bi = new BigDecimal(d).toBigInteger(); // NOPMD
				negative = bi.signum() < 0;
				magnitude = bi.abs().toString();
			}

			String sign = negative ? "-" : flags.plusSign ? "+" : flags.spaceSign ? " " : "";
			appendInteger(sign, "", magnitude, flags, width, precision, isZeroMagnitude(magnitude));
		}

		private void renderUnsignedInteger(char conversion, Flags flags, int width, int precision, Object arg) {
			double d = JRT.toDouble(arg);
			if (renderNonFinite(conversion, flags, width, d)) {
				return;
			}

			int radix = conversion == 'o' ? 8 : conversion == 'u' ? 10 : 16;
			String magnitude;
			if (arg instanceof Long || arg instanceof Integer || arg instanceof Short || arg instanceof Byte) {
				magnitude = Long.toUnsignedString(((Number) arg).longValue(), radix);
			} else if (d >= -TWO_POW_63 && d < TWO_POW_63) {
				magnitude = Long.toUnsignedString((long) d, radix);
			} else {
				// The exact binary value of the double is intended: it makes rounding match gawk's C library.
				BigInteger bi = new BigDecimal(d).toBigInteger(); // NOPMD
				if (bi.signum() >= 0 && bi.compareTo(TWO_POW_64) < 0) {
					magnitude = bi.toString(radix);
				} else {
					// Out of the unsigned 64-bit range: fall back to %g
					// notation with the original sign, flags, precision, and
					// width, like gawk.
					renderFloat('g', flags, width, precision, Double.valueOf(d));
					return;
				}
			}
			if (conversion == 'X') {
				magnitude = magnitude.toUpperCase(Locale.ROOT);
			}

			boolean zeroValue = d == 0;
			boolean zeroMagnitude = isZeroMagnitude(magnitude);
			int actualPrecision = precision;
			if (zeroMagnitude && precision == 0 && (flags.alternate || !zeroValue)) {
				// gawk prints "0" rather than nothing for a zero magnitude
				// with an explicit zero precision when the '#' flag is given,
				// or when the original value is nonzero and merely truncates
				// to zero.
				actualPrecision = 1;
			}
			// The '#' prefix depends on the original value, not the truncated
			// magnitude: gawk prints "0x0" for %#.0x with 0.1. For %o, gawk
			// always adds the alternate leading zero in addition to any
			// precision padding: %#.5o of 1 prints "000001".
			String prefix = "";
			if (flags.alternate && !zeroValue) {
				if (conversion == 'x') {
					prefix = "0x";
				} else if (conversion == 'X') {
					prefix = "0X";
				} else if (conversion == 'o') {
					prefix = "0";
				}
			}
			// gawk's ' flag groups decimal output only, never octal or
			// hexadecimal.
			Flags integerFlags = conversion == 'u' ? flags : flags.withoutGrouping();
			appendInteger("", prefix, magnitude, integerFlags, width, actualPrecision, zeroMagnitude);
		}

		/**
		 * Applies precision, grouping, and width to an integer body and
		 * appends it to the output.
		 */
		private void appendInteger(
				String sign,
				String prefix,
				String magnitude,
				Flags flags,
				int width,
				int precision,
				boolean zeroMagnitude) {
			String digits = magnitude;
			if (precision == 0 && zeroMagnitude) {
				// C: a zero value with an explicit zero precision prints no
				// characters. gawk drops the sign flags as well.
				appendPadded("", flags.leftJustify, false, width);
				return;
			}
			if (precision > digits.length()) {
				digits = zeros(precision - digits.length()) + digits;
			}
			if (flags.grouping) {
				digits = groupDigits(digits);
			}
			String body = sign + prefix + digits;
			if (width > body.length() && flags.zeroPad && !flags.leftJustify && precision < 0) {
				// Zero padding goes between the sign/prefix and the digits.
				out.append(sign).append(prefix);
				out.append(zeros(width - body.length()));
				out.append(digits);
				return;
			}
			appendPadded(body, flags.leftJustify, false, width);
		}

		private void renderFloat(char conversion, Flags flags, int width, int precision, Object arg) {
			double d = JRT.toDouble(arg);
			if (renderNonFinite(conversion, flags, width, d)) {
				return;
			}
			String magnitude = floatBody(conversion, flags, precision, d);
			if (magnitude == null) {
				return;
			}
			boolean negative = d < 0 || (d == 0 && Double.doubleToRawLongBits(d) != 0L);
			String sign = negative ? "-" : flags.plusSign ? "+" : flags.spaceSign ? " " : "";
			// The hexadecimal prefix of %a/%A stays ahead of any zero padding,
			// like an integer prefix.
			String prefix = "";
			if (magnitude.startsWith("0x") || magnitude.startsWith("0X")) {
				prefix = magnitude.substring(0, 2);
				magnitude = magnitude.substring(2);
			}
			String full = sign + prefix + magnitude;
			if (width > full.length() && flags.zeroPad && !flags.leftJustify) {
				out.append(sign).append(prefix);
				out.append(zeros(width - full.length()));
				out.append(magnitude);
				return;
			}
			appendPadded(full, flags.leftJustify, false, width);
		}

		/**
		 * Renders the digits of a finite double for a floating-point
		 * conversion, without sign and without width padding: the absolute
		 * value is formatted and the caller applies the sign.
		 */
		private String floatBody(char conversion, Flags flags, int precision, double d) {
			double abs = Math.abs(d);
			switch (conversion) {
			case 'f':
			case 'F': {
				int p = precision < 0 ? 6 : precision;
				String s = decimalString(roundHalfEvenToScale(abs, p));
				if (flags.alternate && p == 0) {
					s = forceDecimalSeparator(s);
				}
				if (flags.grouping) {
					s = groupDigits(s);
				}
				return s;
			}
			case 'e':
			case 'E': {
				int p = precision < 0 ? 6 : precision;
				String s = scientific(abs, p);
				if (flags.alternate && p == 0) {
					int exponentStart = s.indexOf('e');
					s = forceDecimalSeparator(s.substring(0, exponentStart)) + s.substring(exponentStart);
				}
				return conversion == 'E' ? s.toUpperCase(Locale.ROOT) : s;
			}
			case 'g':
			case 'G': {
				int p = precision < 0 ? 6 : precision == 0 ? 1 : precision;
				String s = generalFloat(abs, p, flags.alternate, flags.grouping);
				return conversion == 'G' ? s.toUpperCase(Locale.ROOT) : s;
			}
			case 'a':
			case 'A':
			default: {
				// %a is C-library dependent in gawk; delegate to Java's
				// hexadecimal float notation.
				StringBuilder spec = new StringBuilder("%");
				if (precision >= 0) {
					spec.append('.').append(precision);
				}
				spec.append(conversion);
				try {
					String s = String.format(locale, spec.toString(), Double.valueOf(abs));
					return s;
				} catch (IllegalFormatException e) {
					out.append(spec);
					return null;
				}
			}
			}
		}

		/** Formats {@code abs >= 0} in C's {@code %e} notation. */
		private String scientific(double abs, int precision) {
			BigDecimal mantissa;
			int exponent;
			if (abs == 0) {
				mantissa = BigDecimal.ZERO.setScale(precision);
				exponent = 0;
			} else {
				BigDecimal rounded = roundHalfEvenToSignificant(abs, precision + 1);
				exponent = rounded.precision() - rounded.scale() - 1;
				mantissa = rounded.movePointLeft(exponent).setScale(precision, RoundingMode.UNNECESSARY);
			}
			return decimalString(mantissa) + "e" + (exponent < 0 ? "-" : "+") + exponentDigits(Math.abs(exponent));
		}

		/** Formats {@code abs >= 0} in C's {@code %g} notation. */
		private String generalFloat(double abs, int precision, boolean alternate, boolean grouping) {
			if (abs == 0) {
				return alternate ? forceDecimalSeparator("0") + zeros(precision - 1) : "0";
			}
			BigDecimal rounded = roundHalfEvenToSignificant(abs, precision);
			int exponent = rounded.precision() - rounded.scale() - 1;
			if (exponent >= -4 && exponent < precision) {
				String s = decimalString(rounded.setScale(precision - 1 - exponent, RoundingMode.UNNECESSARY));
				s = alternate ? forceDecimalSeparator(s) : stripTrailingFractionZeros(s);
				// gawk groups %g in fixed notation, like %f, but never in
				// exponential notation.
				return grouping ? groupDigits(s) : s;
			}
			String mantissa = decimalString(
					rounded.movePointLeft(exponent).setScale(precision - 1, RoundingMode.UNNECESSARY));
			mantissa = alternate ? forceDecimalSeparator(mantissa) : stripTrailingFractionZeros(mantissa);
			return mantissa + "e" + (exponent < 0 ? "-" : "+") + exponentDigits(Math.abs(exponent));
		}

		/**
		 * Renders NaN and infinities for any numeric conversion, honoring the
		 * sign flags and field width, and returns {@code true} when the value
		 * was such a special value.
		 */
		private boolean renderNonFinite(char conversion, Flags flags, int width, double d) {
			if (!Double.isNaN(d) && !Double.isInfinite(d)) {
				return false;
			}
			String body;
			if (Double.isNaN(d)) {
				body = flags.plusSign ? "+nan" : flags.spaceSign ? " nan" : "nan";
			} else if (d > 0) {
				body = flags.plusSign ? "+inf" : flags.spaceSign ? " inf" : "inf";
			} else {
				body = "-inf";
			}
			if (isUpperCaseConversion(conversion)) {
				body = body.toUpperCase(Locale.ROOT);
			}
			// The zero flag is ignored for non-finite values, like C.
			appendPadded(body, flags.leftJustify, false, width);
			return true;
		}

		/** Renders a {@link BigDecimal} using the locale's decimal separator. */
		private String decimalString(BigDecimal value) {
			String s = value.toPlainString();
			return decimalSeparator == '.' ? s : s.replace('.', decimalSeparator);
		}

		/** Inserts locale grouping separators into the integer part of {@code s}. */
		private String groupDigits(String s) {
			int end = s.indexOf(decimalSeparator);
			if (end < 0) {
				end = s.length();
			}
			StringBuilder sb = new StringBuilder(s.length() + 8);
			for (int i = 0; i < end; i++) {
				sb.append(s.charAt(i));
				int remaining = end - 1 - i;
				if (remaining > 0 && remaining % 3 == 0 && isAsciiDigit(s.charAt(i))) {
					sb.append(groupingSeparator);
				}
			}
			sb.append(s, end, s.length());
			return sb.toString();
		}

		/**
		 * Appends the locale decimal separator when {@code s} has none, as
		 * the '#' flag requires for {@code %g} results without fractional
		 * digits.
		 */
		private String forceDecimalSeparator(String s) {
			return s.indexOf(decimalSeparator) < 0 ? s + decimalSeparator : s;
		}

		private String stripTrailingFractionZeros(String s) {
			if (s.indexOf(decimalSeparator) < 0) {
				return s;
			}
			int end = s.length();
			while (end > 0 && s.charAt(end - 1) == '0') {
				end--;
			}
			if (end > 0 && s.charAt(end - 1) == decimalSeparator) {
				end--;
			}
			return s.substring(0, end);
		}

		private void appendPadded(String body, boolean leftJustify, boolean zeroPad, int width) {
			// The field width counts characters (code points), so that a
			// supplementary character fills one column, not two.
			int bodyLength = body.codePointCount(0, body.length());
			if (width <= bodyLength) {
				out.append(body);
				return;
			}
			int padLength = width - bodyLength;
			if (leftJustify) {
				out.append(body);
				appendSpaces(padLength);
			} else if (zeroPad) {
				out.append(zeros(padLength)).append(body);
			} else {
				appendSpaces(padLength);
				out.append(body);
			}
		}

		private void appendSpaces(int count) {
			out.append(repeat(' ', count));
		}
	}

	/**
	 * Rounds the exact binary value of {@code abs} to {@code scale} fractional
	 * digits with {@link RoundingMode#HALF_EVEN}, producing exactly the same
	 * result as {@code new BigDecimal(abs).setScale(scale, HALF_EVEN)}.
	 * <p>
	 * When the shortest decimal representation of {@code abs} provably rounds
	 * to the same value (see {@link #roundsLikeExact}), it is used instead of
	 * the exact expansion, which is much cheaper for values like {@code 0.1}
	 * whose exact binary expansion has dozens or hundreds of digits.
	 * </p>
	 */
	private static BigDecimal roundHalfEvenToScale(double abs, int scale) {
		BigDecimal shortest = shortestDecimal(abs);
		if (shortest != null && roundsLikeExact(abs, shortest, shortest.scale() - scale, -scale)) {
			return shortest.setScale(scale, RoundingMode.HALF_EVEN);
		}
		// The exact binary value of the double is intended: it makes rounding match gawk's C library.
		return new BigDecimal(abs).setScale(scale, RoundingMode.HALF_EVEN); // NOPMD
	}

	/**
	 * Rounds the exact binary value of {@code abs} to {@code digits}
	 * significant digits with {@link RoundingMode#HALF_EVEN}, producing a
	 * result numerically equal to
	 * {@code new BigDecimal(abs).round(new MathContext(digits, HALF_EVEN))}.
	 */
	private static BigDecimal roundHalfEvenToSignificant(double abs, int digits) {
		if (digits > 0) {
			BigDecimal shortest = shortestDecimal(abs);
			if (shortest != null) {
				// Decimal exponent of the rounding unit: eS - digits + 1,
				// where eS = precision - scale - 1.
				int unitExponent = shortest.precision() - shortest.scale() - digits;
				if (roundsLikeExact(abs, shortest, shortest.precision() - digits, unitExponent)) {
					return shortest.round(new MathContext(digits, RoundingMode.HALF_EVEN));
				}
			}
		}
		// The exact binary value of the double is intended: it makes rounding match gawk's C library.
		return new BigDecimal(abs).round(new MathContext(digits, RoundingMode.HALF_EVEN)); // NOPMD
	}

	/**
	 * Returns the shortest decimal representation of {@code abs}, or
	 * {@code null} when {@code abs} is zero, subnormal, or non-finite (those
	 * always take the exact path).
	 */
	private static BigDecimal shortestDecimal(double abs) {
		if (!(abs >= Double.MIN_NORMAL) || abs > Double.MAX_VALUE) {
			return null;
		}
		return new BigDecimal(Double.toString(abs));
	}

	/**
	 * Decides whether rounding the shortest decimal representation of
	 * {@code abs} at the position of the unit {@code 10^unitExponent}
	 * provably yields the same value as rounding the exact binary expansion.
	 * <p>
	 * The exact value differs from the shortest representation by at most
	 * half an ulp (the shortest representation parses back to the same
	 * double). Requiring the rounding unit to exceed 100 ulps and the first
	 * dropped digit to be away from the halfway point guarantees that no
	 * rounding boundary — and no power of ten, where the two values could
	 * disagree on the position of the leading digit — lies between the two
	 * values, so both round identically.
	 * </p>
	 *
	 * @param abs the positive, normal double being rounded
	 * @param shortest its shortest decimal representation
	 * @param remainderDigits how many digits of {@code shortest} fall below
	 *        the rounding unit
	 * @param unitExponent decimal exponent of the rounding unit
	 * @return whether the shortest representation can be rounded instead of
	 *         the exact expansion
	 */
	private static boolean roundsLikeExact(double abs, BigDecimal shortest, int remainderDigits, int unitExponent) {
		if (unitExponent < -307 || unitExponent > 308) {
			// Math.pow(10, unitExponent) would leave the normal double range.
			return false;
		}
		if (Math.ulp(abs) * 100.0 >= Math.pow(10.0, unitExponent)) {
			return false;
		}
		if (remainderDigits <= 0) {
			// The shortest representation is an exact multiple of the
			// rounding unit; the exact value rounds to it.
			return true;
		}
		String digits = shortest.unscaledValue().toString();
		if (remainderDigits > digits.length()) {
			// The dropped fraction starts with a zero digit: far from the
			// halfway point.
			return true;
		}
		// With the unit at least 200 times the maximum difference between the
		// exact value and its shortest representation, only a first dropped
		// digit of 4 or 5 can put the two values on opposite sides of a
		// rounding boundary.
		char first = digits.charAt(digits.length() - remainderDigits);
		return first != '4' && first != '5';
	}

	/** Renders an exponent value with at least two digits, like C. */
	private static String exponentDigits(int exponent) {
		String digits = Integer.toString(exponent);
		return digits.length() < 2 ? "0" + digits : digits;
	}

	private static boolean isUpperCaseConversion(char conversion) {
		return conversion == 'X' || conversion == 'E' || conversion == 'F' || conversion == 'G' || conversion == 'A';
	}

	private static boolean isZeroMagnitude(String magnitude) {
		for (int i = 0; i < magnitude.length(); i++) {
			if (magnitude.charAt(i) != '0') {
				return false;
			}
		}
		return true;
	}

	private static boolean isAsciiDigit(char c) {
		return c >= '0' && c <= '9';
	}

	/**
	 * Parses a run of decimal digits, clamping absurd widths, precisions, and
	 * argument positions to {@link Integer#MAX_VALUE} instead of failing the
	 * way {@link Integer#parseInt(String)} would.
	 */
	private static int parseInt(String s, int from, int to) {
		try {
			return Integer.parseInt(s.substring(from, to));
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	private static String zeros(int count) {
		return repeat('0', count);
	}

	private static String repeat(char c, int count) {
		if (count <= 0) {
			return "";
		}
		char[] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}
}
