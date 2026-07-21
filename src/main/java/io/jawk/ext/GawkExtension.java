package io.jawk.ext;

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

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.backend.AVM;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkBeforeStart;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.ext.annotations.JawkOptional;
import io.jawk.ext.annotations.JawkRawValue;
import io.jawk.ext.annotations.JawkRegexp;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.intermediate.UntypedObject;
import io.jawk.jrt.IllegalAwkArgumentException;
import io.jawk.jrt.JRT;
import io.jawk.jrt.StrNum;

/**
 * GNU awk compatibility extension for array sorting and type introspection.
 */
public class GawkExtension extends AbstractExtension implements JawkExtension {

	private static final String VAL_TYPE_ASC = "@val_type_asc";

	/** Default {@code strftime()} format, as in gawk's C locale. */
	private static final String DEFAULT_STRFTIME_FORMAT = "%a %b %e %H:%M:%S %Z %Y";

	/** gawk's default field pattern when {@code FPAT} is unset. */
	private static final String DEFAULT_FPAT = "[^\\s]+";

	/** Default gettext text domain, as in gawk. */
	private static final String DEFAULT_TEXTDOMAIN = "messages";

	/** Directory reported for text domains never bound with {@code bindtextdomain()}. */
	private static final String DEFAULT_LOCALE_DIRECTORY = "/usr/share/locale";

	/** Largest double (2^53) whose conversion to long is exact. */
	private static final double MAX_EXACT_LONG_DOUBLE = 9007199254740992.0D;

	/** DST offset forced by a positive {@code mktime()} DST hint in zones without savings. */
	private static final int DEFAULT_DST_SAVINGS = 3600000;

	/** Half a year, the probe distance used to find a date's applicable DST savings. */
	private static final long HALF_YEAR_MILLIS = 182L * 24L * 3600L * 1000L;

	/** Locale categories accepted by the gettext functions, as in gawk. */
	private static final Set<String> LOCALE_CATEGORIES = Collections
			.unmodifiableSet(
					new HashSet<String>(
							Arrays
									.asList(
											"LC_ALL",
											"LC_COLLATE",
											"LC_CTYPE",
											"LC_MESSAGES",
											"LC_MONETARY",
											"LC_NUMERIC",
											"LC_TIME")));

	/** Interpreter this per-engine extension instance is bound to. */
	private AVM avm;

	/** Comparison-function names already warned about; created on first use. */
	private Set<String> warnedComparators;

	/** Per-domain directory bindings established by {@code bindtextdomain()}; created on first use. */
	private Map<String, String> textdomainBindings;

	private static final class SortEntry {
		private final Object index;
		private final Object value;

		private SortEntry(Object indexParam, Object valueParam) {
			this.index = indexParam;
			this.value = valueParam;
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getExtensionName() {
		return "GawkExtension";
	}

	/**
	 * Installs the {@code PROCINFO["sorted_in"]} traversal order for
	 * {@code for-in} loops and binds this per-engine extension instance to its
	 * interpreter. SYMTAB and FUNCTAB are populated by the interpreter itself.
	 *
	 * @param avmParam interpreter about to execute
	 * @param jrt runtime associated with {@code avmParam}
	 */
	@JawkBeforeStart
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The extension is a per-engine instance deliberately bound to its interpreter")
	public void initializeGawkVariables(AVM avmParam, JRT jrt) {
		this.avm = avmParam;
		avm.setForInKeyOrder(this::orderForInKeys);
	}

	/**
	 * Sorts an array by value, optionally writing the result to another array.
	 *
	 * @param source source array
	 * @param dest destination array, or {@code null} to sort in place
	 * @param how predefined sorting mode, or {@code null} for the default
	 * @return number of sorted elements
	 */
	@JawkFunction("asort")
	public Long asort(
			@JawkAssocArray Map<Object, Object> source,
			@JawkOptional @JawkAssocArray Map<Object, Object> dest,
			@JawkOptional Object how) {
		return sort(source, dest, how, false);
	}

	/**
	 * Sorts an array by index, optionally writing the result to another array.
	 *
	 * @param source source array
	 * @param dest destination array, or {@code null} to sort in place
	 * @param how predefined sorting mode, or {@code null} for the default
	 * @return number of sorted elements
	 */
	@JawkFunction("asorti")
	public Long asorti(
			@JawkAssocArray Map<Object, Object> source,
			@JawkOptional @JawkAssocArray Map<Object, Object> dest,
			@JawkOptional Object how) {
		return sort(source, dest, how, true);
	}

	/**
	 * Returns the gawk type category for a value.
	 *
	 * @param value value to inspect
	 * @param meta optional metadata destination array
	 * @return gawk type name
	 */
	@JawkFunction("typeof")
	public String typeof(@JawkRawValue Object value, @JawkOptional @JawkAssocArray Map<Object, Object> meta) {
		if (meta != null) {
			meta.clear();
			if (value instanceof Map) {
				meta.put("array_type", arrayType((Map<?, ?>) value));
			}
		}
		return typeOf(value);
	}

	/**
	 * Returns whether the supplied value is an array.
	 *
	 * @param value value to inspect
	 * @return 1 for arrays, 0 otherwise
	 */
	@JawkFunction("isarray")
	public Long isarray(@JawkRawValue Object value) {
		return value instanceof Map ? Long.valueOf(1L) : Long.valueOf(0L);
	}

	/**
	 * Creates a boolean-typed numeric value used by gawk's test suite.
	 *
	 * @param value truth value
	 * @return boolean numeric value
	 */
	@JawkFunction("mkbool")
	public GawkBool mkbool(Object value) {
		// gawk applies ordinary AWK truthiness: a non-empty non-numeric
		// string like "abc" is true, not numeric-coerced to 0
		return new GawkBool(getJrt().toBoolean(value));
	}

	/**
	 * Performs a small gawk-compatible {@code gensub()} substitution.
	 *
	 * @param regexp regular expression
	 * @param replacement replacement text
	 * @param how occurrence selector or {@code g}
	 * @param target target text, or {@code null} to default to {@code $0}
	 * @return substituted text
	 */
	@JawkFunction("gensub")
	public String gensub(@JawkRegexp Object regexp, Object replacement, Object how, @JawkOptional Object target) {
		Pattern pattern = regexp instanceof Pattern ?
				(Pattern) regexp : Pattern.compile(toAwkString(regexp));
		// gawk: a truthy IGNORECASE makes all regexp operations case-insensitive
		pattern = getJrt().caseAwarePattern(pattern);
		Object targetValue = target == null ? getJrt().getInputLine() : target;
		Matcher matcher = pattern.matcher(toAwkString(targetValue));
		String repl = JRT.prepareReplacement(toAwkString(replacement), pattern.matcher("").groupCount());
		String selector = toAwkString(how);
		// gawk: any string beginning with 'g' or 'G' selects a global replacement
		if (!selector.isEmpty() && (selector.charAt(0) == 'g' || selector.charAt(0) == 'G')) {
			return matcher.replaceAll(repl);
		}
		// gawk coerces the selector with AWK numeric conversion (so " 2" and
		// "1e1" are the occurrences 2 and 10) and warns only when the result,
		// truncated, is below 1
		double selected = JRT.toDouble(how);
		if (selected < 1.0D) {
			warnAtCurrentLine("gensub: third argument `%s' treated as 1", selector);
			selected = 1.0D;
		}
		int occurrence = (int) selected;
		if (occurrence == 1) {
			return matcher.replaceFirst(repl);
		}
		StringBuffer result = new StringBuffer();
		int seen = 0;
		while (matcher.find()) {
			seen++;
			if (seen == occurrence) {
				matcher.appendReplacement(result, repl);
				break;
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Returns the current time in seconds since the epoch.
	 *
	 * @return seconds since 1970-01-01 00:00:00 UTC
	 */
	@JawkFunction("systime")
	public Long systime() {
		return Long.valueOf(System.currentTimeMillis() / 1000L);
	}

	/**
	 * Converts a gawk {@code "YYYY MM DD HH MM SS [DST]"} date specification
	 * into seconds since the epoch, normalizing out-of-range values.
	 *
	 * @param datespec date specification with six or seven numeric fields
	 * @param utcFlag when truthy, interpret the specification as UTC
	 * @return seconds since the epoch, or -1 when the specification is invalid
	 */
	@JawkFunction("mktime")
	public Long mktime(Object datespec, @JawkOptional Object utcFlag) {
		String[] fields = toAwkString(datespec).trim().split("\\s+");
		if (fields.length < 6 || fields.length > 7) {
			return Long.valueOf(-1L);
		}
		int[] values = new int[fields.length];
		for (int i = 0; i < fields.length; i++) {
			try {
				values[i] = Integer.parseInt(fields[i]);
			} catch (NumberFormatException e) {
				return Long.valueOf(-1L);
			}
		}
		boolean utc = utcFlag != null && getJrt().toBoolean(utcFlag);
		TimeZone timeZone = utc ? TimeZone.getTimeZone("UTC") : localTimeZone();
		GregorianCalendar calendar = new GregorianCalendar(timeZone);
		// proleptic Gregorian: gawk's civil dates never switch to Julian
		calendar.setGregorianChange(new Date(Long.MIN_VALUE));
		calendar.setLenient(true);
		calendar.clear();
		calendar.set(values[0], values[1] - 1, values[2], values[3], values[4], values[5]);
		long millis = calendar.getTimeInMillis();
		if (fields.length == 7 && !utc && values[6] >= 0) {
			// like C's tm_isdst: a non-negative hint forces the DST offset (the
			// one applicable at that date, which may differ from the zone's
			// current savings); a negative hint lets the zone's rules decide
			calendar.clear();
			calendar.set(values[0], values[1] - 1, values[2], values[3], values[4], values[5]);
			calendar.set(Calendar.DST_OFFSET, values[6] > 0 ? applicableDstSavings(timeZone, millis) : 0);
			millis = calendar.getTimeInMillis();
		} else if (!utc) {
			millis = resolveOverlapLikeGlibc(millis, timeZone);
		}
		return Long.valueOf(Math.floorDiv(millis, 1000L));
	}

	/**
	 * DST adjustment applicable around an instant. Zones like
	 * Australia/Lord_Howe changed their savings over time, so the current
	 * {@code getDSTSavings()} value may be wrong for older dates: probing half
	 * a year around the date finds that era's actual adjustment. Zones without
	 * any savings (fixed offsets) get C's usual one-hour adjustment.
	 */
	private static int applicableDstSavings(TimeZone timeZone, long millis) {
		long best = 0L;
		try {
			java.time.zone.ZoneRules rules = timeZone.toZoneId().getRules();
			for (long probe : new long[] { millis, millis - HALF_YEAR_MILLIS, millis + HALF_YEAR_MILLIS }) {
				best = Math.max(best, rules.getDaylightSavings(java.time.Instant.ofEpochMilli(probe)).toMillis());
			}
		} catch (RuntimeException e) {
			// custom zones or timestamps beyond java.time's range
			best = timeZone.getDSTSavings();
		}
		return best > 0L ? (int) best : DEFAULT_DST_SAVINGS;
	}

	/**
	 * Resolves ambiguous fall-back wall times the way glibc's {@code mktime()}
	 * does when {@code tm_isdst} is unspecified: the chosen occurrence is the
	 * one whose UTC offset is in effect at the instant obtained by reading the
	 * wall time as UTC. That favors the daylight occurrence in zones west of
	 * Greenwich (America/New_York) and the standard one east of it
	 * (Europe/Berlin); {@link GregorianCalendar} would always pick the later
	 * standard-time occurrence.
	 */
	private static long resolveOverlapLikeGlibc(long millis, TimeZone timeZone) {
		int savings = applicableDstSavings(timeZone, millis);
		long earlier = millis - savings;
		// the shifted instant represents the same wall time exactly when it
		// falls in daylight saving while the original resolution is standard
		if (timeZone.getOffset(earlier) - timeZone.getOffset(millis) != savings) {
			return millis;
		}
		long wallReadAsUtc = millis + timeZone.getOffset(millis);
		return timeZone.getOffset(wallReadAsUtc) == timeZone.getOffset(earlier) ? earlier : millis;
	}

	/**
	 * Formats a timestamp with C {@code strftime(3)} conversion specifiers.
	 *
	 * @param format format string; defaults to {@code PROCINFO["strftime"]} or
	 *        gawk's {@code "%a %b %e %H:%M:%S %Z %Y"}
	 * @param timestamp seconds since the epoch; defaults to the current time
	 * @param utcFlag when truthy, format in UTC instead of the local time zone
	 * @return formatted timestamp
	 */
	@JawkFunction("strftime")
	public String strftime(
			@JawkOptional Object format,
			@JawkOptional Object timestamp,
			@JawkOptional Object utcFlag) {
		String formatString = format == null ? defaultStrftimeFormat() : toAwkString(format);
		long seconds = timestamp == null ?
				System.currentTimeMillis() / 1000L : (long) JRT.toDouble(timestamp);
		boolean utc = utcFlag != null && getJrt().toBoolean(utcFlag);
		TimeZone timeZone = utc ? TimeZone.getTimeZone("UTC") : localTimeZone();
		return Strftime.format(formatString, seconds, timeZone);
	}

	/**
	 * Returns the local time zone for {@code mktime()} and {@code strftime()},
	 * honoring {@code ENVIRON["TZ"]}: gawk supports changing the time zone
	 * from within the script through the AWK environment.
	 */
	private TimeZone localTimeZone() {
		Object environ = getVm().getVariable("ENVIRON");
		if (environ instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<Object, Object> environMap = (Map<Object, Object>) environ;
			if (JRT.containsAwkKey(environMap, "TZ")) {
				String tz = getJrt().toAwkString(JRT.getAssocArrayValue(environMap, "TZ"));
				if (tz.startsWith(":")) {
					// POSIX: a leading colon introduces an implementation-defined
					// (here: Olson) time zone name
					tz = tz.substring(1);
				}
				// POSIX: an explicitly empty TZ means UTC
				return tz.isEmpty() ? TimeZone.getTimeZone("UTC") : resolveTimeZone(tz);
			}
		}
		return TimeZone.getDefault();
	}

	/**
	 * Resolves a TZ value: an Olson or Java zone ID when known, otherwise a
	 * POSIX TZ specification such as {@code XXX3} or
	 * {@code CET-1CEST,M3.5.0,M10.5.0/3}, which
	 * {@link TimeZone#getTimeZone(String)} alone would silently turn into GMT.
	 * Java's custom {@code GMT+3} IDs conflict with POSIX, where a positive
	 * offset lies west of Greenwich: for TZ values the POSIX reading wins, as
	 * in gawk, so {@code GMT+3} is UTC-03:00.
	 */
	private static TimeZone resolveTimeZone(String id) {
		if (id.startsWith("GMT+") || id.startsWith("GMT-")) {
			TimeZone posix = PosixTimeZone.parse(id);
			if (posix != null) {
				return posix;
			}
		}
		TimeZone zone = TimeZone.getTimeZone(id);
		if ("GMT".equals(zone.getID()) && !"GMT".equals(id)) {
			TimeZone posix = PosixTimeZone.parse(id);
			if (posix != null) {
				return posix;
			}
		}
		return zone;
	}

	/** Returns {@code PROCINFO["strftime"]} when set, gawk's default format otherwise. */
	private String defaultStrftimeFormat() {
		Object procinfo = getVm().getVariable("PROCINFO");
		if (procinfo instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<Object, Object> procinfoMap = (Map<Object, Object>) procinfo;
			if (JRT.containsAwkKey(procinfoMap, "strftime")) {
				return getJrt().toAwkString(JRT.getAssocArrayValue(procinfoMap, "strftime"));
			}
		}
		return DEFAULT_STRFTIME_FORMAT;
	}

	/**
	 * Converts a string to a number, recognizing gawk's non-decimal notation:
	 * a {@code 0x} prefix selects hexadecimal and a leading {@code 0} over
	 * octal digits selects octal.
	 *
	 * @param value value to convert
	 * @return numeric value
	 */
	@JawkFunction("strtonum")
	public Number strtonum(@JawkRawValue Object value) {
		if (value instanceof Number) {
			return (Number) value;
		}
		if (value instanceof StrNum && ((StrNum) value).isNumber()) {
			// gawk resolves numeric-looking input fields to plain numbers
			// before looking at the base, so "011" from input is decimal 11
			return Double.valueOf(((StrNum) value).doubleValue());
		}
		String text = toAwkString(value);
		switch (numberBase(text)) {
		case 16:
			return parseNonDecimal(text, 2, 16);
		case 8:
			return parseNonDecimal(text, 1, 8);
		default:
			return Double.valueOf(JRT.toDouble(text));
		}
	}

	/**
	 * Determines the numeric base of a string constant, as gawk does: a
	 * {@code 0x}/{@code 0X} prefix means hexadecimal, and a leading zero means
	 * octal unless the token is really a decimal number. Scanning the
	 * contiguous digit prefix, a digit above 7 or an adjacent decimal point or
	 * exponent makes the constant decimal (so {@code 019} is 19 and
	 * {@code 011e2} is 1100), while any other character merely terminates the
	 * numeric token (so {@code 011x} is 9 and {@code 077foo.5} is 63).
	 */
	private static int numberBase(String text) {
		if (text.length() < 2 || text.charAt(0) != '0') {
			return 10;
		}
		char second = text.charAt(1);
		if (second == 'x' || second == 'X') {
			return 16;
		}
		for (int i = 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '.' || c == 'e' || c == 'E') {
				return 10;
			}
			if (c < '0' || c > '9') {
				break;
			}
			if (c > '7') {
				return 10;
			}
		}
		return 8;
	}

	/**
	 * Parses digits of the given base, stopping at the first invalid
	 * character, as gawk's non-decimal scanner does ({@code "0x"} is 0).
	 */
	private static Number parseNonDecimal(String text, int offset, int base) {
		double value = 0.0D;
		for (int i = offset; i < text.length(); i++) {
			int digit = Character.digit(text.charAt(i), base);
			if (digit < 0) {
				break;
			}
			value = value * base + digit;
		}
		if (value <= MAX_EXACT_LONG_DOUBLE) {
			return Long.valueOf((long) value);
		}
		return Double.valueOf(value);
	}

	/**
	 * Splits a string by content: pieces matching {@code fieldpat} become
	 * fields, the text between them becomes separators. This is gawk's
	 * {@code patsplit()}, the function form of {@code FPAT} field splitting.
	 *
	 * @param source text to split
	 * @param array destination array for the fields
	 * @param fieldpat field pattern, or {@code null} to use the {@code FPAT}
	 *        global variable (default {@code "[^[:space:]]+"})
	 * @param seps optional destination array for the separators; entry 0 holds
	 *        the text before the first field
	 * @return number of fields
	 */
	@JawkFunction("patsplit")
	public Long patsplit(
			Object source,
			@JawkAssocArray Map<Object, Object> array,
			@JawkOptional @JawkRegexp Object fieldpat,
			@JawkOptional @JawkAssocArray Map<Object, Object> seps) {
		if (array == seps) {
			throw new IllegalAwkArgumentException("patsplit: cannot use the same array for second and fourth args");
		}
		String str = toAwkString(source);
		Pattern pattern = fieldPattern(fieldpat);
		array.clear();
		if (seps != null) {
			seps.clear();
		}
		if (str.isEmpty()) {
			return Long.valueOf(0L);
		}
		/*
		 * gawk's FPAT splitting rules: every accepted match becomes a field,
		 * except that a zero-length match immediately following a non-empty
		 * field is skipped, retrying one character (code point) further. The separators are
		 * the gaps around the accepted fields: seps[i] is the text between
		 * fields i and i+1, seps[0] the text before the first field, seps[n]
		 * the text after the last one. The matcher region makes anchors behave
		 * as if the already-consumed prefix were gone, as in gawk.
		 */
		Matcher matcher = pattern.matcher(str);
		int length = str.length();
		int pos = 0;
		int previousEnd = 0;
		long fieldCount = 0L;
		boolean lastMatchNonEmpty = false;
		while (pos <= length) {
			matcher.region(pos, length);
			if (!matcher.find()) {
				break;
			}
			int start = matcher.start();
			int end = matcher.end();
			if (end > start) {
				lastMatchNonEmpty = true;
				putSeparator(seps, fieldCount, str.substring(previousEnd, start));
				array.put(Long.valueOf(++fieldCount), getJrt().toInputScalar(str.substring(start, end)));
				previousEnd = end;
				pos = end;
				if (pos >= length) {
					break;
				}
			} else if (lastMatchNonEmpty) {
				lastMatchNonEmpty = false;
				pos = str.offsetByCodePoints(pos, 1);
			} else {
				putSeparator(seps, fieldCount, str.substring(previousEnd, start));
				array.put(Long.valueOf(++fieldCount), getJrt().toInputScalar(""));
				previousEnd = start;
				if (start >= length) {
					// trailing empty field at end of input: done
					break;
				}
				pos = str.offsetByCodePoints(start, 1);
			}
		}
		// seps[n] holds the text after the last field: the rest of the input,
		// the empty string when the input ends at a field boundary
		putSeparator(seps, fieldCount, str.substring(previousEnd));
		return Long.valueOf(fieldCount);
	}

	/** Stores a separator, unless the caller omitted the separator array. */
	private void putSeparator(Map<Object, Object> separators, long index, String value) {
		if (separators != null) {
			separators.put(Long.valueOf(index), getJrt().toInputScalar(value));
		}
	}

	/**
	 * Resolves the {@code patsplit()} field pattern: argument, FPAT, or gawk's
	 * default. Jawk has no FPAT special variable, so an unset FPAT stands in
	 * for gawk's built-in default; an explicitly empty pattern is fatal, as in
	 * gawk.
	 */
	private Pattern fieldPattern(Object fieldpat) {
		if (fieldpat instanceof Pattern) {
			Pattern pattern = (Pattern) fieldpat;
			requireNonEmptyFieldPattern(pattern.pattern());
			return getJrt().caseAwarePattern(pattern);
		}
		String expression;
		if (fieldpat != null) {
			expression = toAwkString(fieldpat);
		} else {
			Object fpat = getVm().getVariable("FPAT");
			if (fpat == null || fpat instanceof UninitializedObject) {
				return getJrt().dynamicPattern(DEFAULT_FPAT);
			}
			expression = toAwkString(fpat);
		}
		requireNonEmptyFieldPattern(expression);
		return getJrt().dynamicPattern(expression);
	}

	/** Rejects an empty field pattern with gawk's fatal diagnostic. */
	private static void requireNonEmptyFieldPattern(String expression) {
		if (expression.isEmpty()) {
			throw new IllegalAwkArgumentException("patsplit: field pattern must be non-null");
		}
	}

	/**
	 * Returns the translation of a string in the given text domain and locale
	 * category. Jawk ships no message catalogs, so the text is returned
	 * untranslated, exactly like gawk without a matching {@code .mo} file.
	 *
	 * @param string text to translate
	 * @param domain text domain; defaults to {@code TEXTDOMAIN}
	 * @param category locale category; validated, then ignored (no catalogs)
	 * @return the untranslated text
	 */
	@JawkFunction("dcgettext")
	public String dcgettext(Object string, @JawkOptional Object domain, @JawkOptional Object category) {
		checkLocaleCategory(category);
		return toAwkString(string);
	}

	/**
	 * Returns the singular or plural form of a message according to a number.
	 * Without message catalogs this applies the English plural rule, exactly
	 * like gawk without a matching {@code .mo} file.
	 *
	 * @param singular singular form
	 * @param plural plural form
	 * @param number quantity deciding the form
	 * @param domain text domain; defaults to {@code TEXTDOMAIN}
	 * @param category locale category; validated, then ignored (no catalogs)
	 * @return {@code singular} when the number is 1, {@code plural} otherwise
	 */
	@JawkFunction("dcngettext")
	public String dcngettext(
			Object singular,
			Object plural,
			Object number,
			@JawkOptional Object domain,
			@JawkOptional Object category) {
		checkLocaleCategory(category);
		return (long) JRT.toDouble(number) == 1L ? toAwkString(singular) : toAwkString(plural);
	}

	/**
	 * Rejects invalid locale category arguments with gawk's fatal diagnostic,
	 * which names dcgettext even for {@code dcngettext()}.
	 */
	private void checkLocaleCategory(Object category) {
		if (category == null) {
			return;
		}
		String name = toAwkString(category);
		if (!LOCALE_CATEGORIES.contains(name)) {
			throw new IllegalAwkArgumentException("dcgettext: `" + name + "' is not a valid locale category");
		}
	}

	/**
	 * Binds a text domain to a message catalog directory and returns the
	 * binding, mirroring gawk's {@code bindtextdomain()}.
	 *
	 * @param directory directory to bind; the AWK empty string queries the
	 *        current binding without changing it
	 * @param domain text domain; defaults to {@code TEXTDOMAIN}
	 * @return the directory now bound to the domain
	 */
	@JawkFunction("bindtextdomain")
	public String bindtextdomain(Object directory, @JawkOptional Object domain) {
		String domainName = domain == null ? currentTextdomain() : toAwkString(domain);
		if (domainName.isEmpty()) {
			// C's bindtextdomain() rejects an explicitly empty domain: gawk
			// returns the empty string and no binding changes
			return "";
		}
		String directoryName = toAwkString(directory);
		if (textdomainBindings == null) {
			textdomainBindings = new HashMap<String, String>();
		}
		if (!directoryName.isEmpty()) {
			textdomainBindings.put(domainName, directoryName);
		}
		String bound = textdomainBindings.get(domainName);
		return bound == null ? DEFAULT_LOCALE_DIRECTORY : bound;
	}

	/** Returns the {@code TEXTDOMAIN} variable, or gawk's default domain when unset. */
	private String currentTextdomain() {
		Object textdomain = getVm().getVariable("TEXTDOMAIN");
		String name = textdomain == null ? "" : toAwkString(textdomain);
		return name.isEmpty() ? DEFAULT_TEXTDOMAIN : name;
	}

	/**
	 * Prints a gawk-style diagnostic located at the extension call currently
	 * being dispatched, e.g. {@code gawk: script.awk:4: warning: ...}.
	 */
	private void warnAtCurrentLine(String format, Object... args) {
		String source = avm == null ? null : avm.getSourceDescription();
		String basename = source == null ? "" : new File(source).getName();
		getJrt()
				.printWarning(
						String
								.format(
										"gawk: %s:%d: warning: %s",
										basename,
										avm == null ? 0 : avm.getCurrentLineNumber(),
										String.format(format, args)));
	}

	/**
	 * Returns the {@code for (index in array)} traversal order mandated by
	 * {@code PROCINFO["sorted_in"]}, or the array's natural key order when no
	 * sort mode is in effect.
	 */
	private Collection<Object> orderForInKeys(Map<Object, Object> map) {
		String mode = currentSortedIn();
		if (mode == null || mode.isEmpty() || "@unsorted".equals(mode)) {
			return map.keySet();
		}
		return sortedKeys(map, effectiveSortMode(mode, VAL_TYPE_ASC), getJrt(), currentIgnoreCase());
	}

	private String currentSortedIn() {
		Object procinfo = getVm().getVariable("PROCINFO");
		if (!(procinfo instanceof Map)) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> procinfoMap = (Map<Object, Object>) procinfo;
		if (!JRT.containsAwkKey(procinfoMap, "sorted_in")) {
			return null;
		}
		return getJrt().toAwkString(JRT.getAssocArrayValue(procinfoMap, "sorted_in"));
	}

	/*
	 * Gawk also accepts the name of a user-defined comparison function, which
	 * Jawk does not support: those fall back to the default ordering with a
	 * one-time warning. Unknown @-modes are typos and stay fatal, as in gawk.
	 */
	private String effectiveSortMode(String mode, String defaultMode) {
		if (mode.isEmpty()) {
			// gawk treats an empty mode like an omitted one
			return defaultMode;
		}
		if (mode.charAt(0) != '@') {
			warnUnsupportedComparator(mode);
			return defaultMode;
		}
		return mode;
	}

	private void warnUnsupportedComparator(String name) {
		if (warnedComparators == null) {
			warnedComparators = new HashSet<String>();
		}
		if (warnedComparators.add(name)) {
			warnAtCurrentLine("sort comparison function `%s' is not supported; using default ordering", name);
		}
	}

	private Long sort(Map<Object, Object> source, Map<Object, Object> dest, Object how, boolean indicesAsValues) {
		Map<Object, Object> destination = dest == null ? source : dest;
		// gawk's defaults: value-type order for asort(), string index order
		// for asorti() (indexes are strings, so no type ranking applies)
		String defaultMode = indicesAsValues ? "@ind_str_asc" : VAL_TYPE_ASC;
		String mode = how == null ? defaultMode : effectiveSortMode(toAwkString(how), defaultMode);
		List<SortEntry> entries = entries(source);
		// @unsorted keeps the natural traversal order: no sorting at all
		if (!"@unsorted".equals(mode)) {
			Collections.sort(entries, comparator(mode, getJrt(), currentIgnoreCase()));
		}
		destination.clear();
		long idx = 1L;
		for (SortEntry entry : entries) {
			// asorti() writes indices as string values, as in gawk: the
			// internal key object may be a Long for numeric-looking indexes
			Object value = indicesAsValues ? getJrt().toAwkString(entry.index) : entry.value;
			destination.put(Long.valueOf(idx++), value);
		}
		return Long.valueOf(entries.size());
	}

	/**
	 * Sorts and returns map keys according to a gawk predefined sort mode.
	 *
	 * @param map map whose keys should be sorted
	 * @param mode predefined sort mode
	 * @param jrt runtime used for AWK string conversion
	 * @param ignoreCase whether string comparisons ignore case
	 * @return sorted keys
	 */
	private static List<Object> sortedKeys(Map<Object, Object> map, String mode, JRT jrt, boolean ignoreCase) {
		List<SortEntry> entries = entries(map);
		Collections.sort(entries, comparator(mode, jrt, ignoreCase));
		List<Object> keys = new ArrayList<Object>(entries.size());
		for (SortEntry entry : entries) {
			keys.add(entry.index);
		}
		return keys;
	}

	private static List<SortEntry> entries(Map<Object, Object> map) {
		List<SortEntry> entries = new ArrayList<SortEntry>(map.size());
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			entries.add(new SortEntry(entry.getKey(), entry.getValue()));
		}
		return entries;
	}

	private boolean currentIgnoreCase() {
		return getJrt().isIgnoreCase();
	}

	/*
	 * Callers handle @unsorted before reaching this point (no sort at all),
	 * both for asort()/asorti() and for the for-in traversal hook.
	 */
	private static Comparator<SortEntry> comparator(String effectiveMode, JRT jrt, boolean ignoreCase) {
		boolean desc = effectiveMode.endsWith("_desc");
		Comparator<SortEntry> comparator;
		/*
		 * Gawk's predefined orderings first choose whether indexes or values are
		 * compared, then choose numeric, string, or type-aware comparison. Arrays
		 * are kept in a separate group because gawk does not stringify subarrays
		 * during type-aware sorting. Unknown modes are fatal, as in gawk;
		 * function-name comparators are not supported.
		 */
		switch (effectiveMode) {
		case "@ind_num_asc":
		case "@ind_num_desc":
			comparator = (left, right) -> compareNumericThenText(left.index, right.index, jrt, ignoreCase);
			break;
		case "@ind_str_asc":
		case "@ind_str_desc":
			comparator = (left, right) -> compareStrings(left.index, right.index, jrt, ignoreCase);
			break;
		case "@ind_type_asc":
		case "@ind_type_desc":
			comparator = (left, right) -> compareByTypeThenValue(left.index, right.index, jrt, ignoreCase);
			break;
		case "@val_num_asc":
		case "@val_num_desc":
			comparator = (left, right) -> compareNumericThenText(left.value, right.value, jrt, ignoreCase);
			break;
		case "@val_str_asc":
		case "@val_str_desc":
			comparator = (left, right) -> compareStrings(left.value, right.value, jrt, ignoreCase);
			break;
		case "@val_type_asc":
		case "@val_type_desc":
			comparator = (left, right) -> compareByTypeThenValue(left.value, right.value, jrt, ignoreCase);
			break;
		default:
			throw new IllegalAwkArgumentException("Invalid sort comparison mode '" + effectiveMode + "'");
		}
		return desc ? comparator.reversed() : comparator;
	}

	/*
	 * The comparators below implement gawk's predefined sort orderings
	 * (@ind_num_*, @val_str_*, @val_type_*, ...). They cannot reuse
	 * JRT.compare2(): that method implements AWK's relational operators
	 * (boolean outcome, strnum coercion rules), while these need a three-way
	 * ordering that first RANKS values by gawk type (numbers < strings <
	 * subarrays, in mode-specific order) and then compares within the rank
	 * using a comparison FORCED by the mode (numeric or string), honoring
	 * IGNORECASE for strings.
	 */

	/**
	 * Type-aware ordering used by the {@code @..._type_...} modes and as gawk's
	 * default: numbers and strnums first (compared numerically), then strings
	 * (compared as text), then subarrays (mutually unordered).
	 */
	private static int compareByTypeThenValue(Object left, Object right, JRT jrt, boolean ignoreCase) {
		int leftRank = typeRank(left);
		int rightRank = typeRank(right);
		if (leftRank != rightRank) {
			return Integer.compare(leftRank, rightRank);
		}
		if (left instanceof Map && right instanceof Map) {
			return 0;
		}
		if (leftRank == 0) {
			return compareNumbers(left, right);
		}
		return compareStrings(left, right, jrt, ignoreCase);
	}

	/** Rank for type-aware ordering: 0 = number/strnum, 1 = string, 2 = subarray. */
	private static int typeRank(Object value) {
		if (value instanceof Number || isStrnum(value)) {
			return 0;
		}
		if (value instanceof Map) {
			return 2;
		}
		return 1;
	}

	/** Numeric three-way comparison; subarrays sort as 0 so ranking decides first. */
	private static int compareNumbers(Object left, Object right) {
		return Double.compare(numericSortValue(left), numericSortValue(right));
	}

	private static double numericSortValue(Object value) {
		return value instanceof Map ? 0.0D : JRT.toDouble(value);
	}

	/**
	 * Ordering for the {@code @..._num_...} modes: gawk coerces every scalar to
	 * a number (non-numeric strings count as 0), breaks numeric ties with a
	 * string comparison, and sorts subarrays last.
	 */
	private static int compareNumericThenText(Object left, Object right, JRT jrt, boolean ignoreCase) {
		if (left instanceof Map || right instanceof Map) {
			if (left instanceof Map && right instanceof Map) {
				return 0;
			}
			return left instanceof Map ? 1 : -1;
		}
		int numeric = compareNumbers(left, right);
		if (numeric != 0) {
			return numeric;
		}
		return compareStrings(left, right, jrt, ignoreCase);
	}

	/**
	 * Text three-way comparison through {@code toAwkString} (CONVFMT/locale),
	 * folding case when {@code IGNORECASE} is set; subarrays sort last.
	 */
	private static int compareStrings(Object left, Object right, JRT jrt, boolean ignoreCase) {
		if (left instanceof Map || right instanceof Map) {
			if (left instanceof Map && right instanceof Map) {
				return 0;
			}
			return left instanceof Map ? 1 : -1;
		}
		String leftString = jrt.toAwkString(left);
		String rightString = jrt.toAwkString(right);
		// compareToIgnoreCase folds per character without allocating, applying
		// the same rule as JRT.compare2 on string relational operators
		return ignoreCase ? leftString.compareToIgnoreCase(rightString) : leftString.compareTo(rightString);
	}

	private static String typeOf(Object value) {
		if (value == null || value instanceof UntypedObject) {
			return "untyped";
		}
		if (value instanceof Map) {
			return "array";
		}
		if (value instanceof GawkBool) {
			return "number|bool";
		}
		if (value instanceof Number) {
			return "number";
		}
		if (value instanceof Pattern) {
			return "regexp";
		}
		if (value instanceof UninitializedObject) {
			return "unassigned";
		}
		return isStrnum(value) ? "strnum" : "string";
	}

	private static boolean isStrnum(Object value) {
		return value instanceof StrNum && ((StrNum) value).isNumber();
	}

	private static String arrayType(Map<?, ?> map) {
		if (map.isEmpty()) {
			return "null";
		}
		boolean allNonNegativeIntegral = true;
		for (Object key : map.keySet()) {
			if (!(key instanceof Number)) {
				return "str";
			}
			long longValue = ((Number) key).longValue();
			double doubleValue = ((Number) key).doubleValue();
			if (Double.compare(doubleValue, (double) longValue) != 0) {
				return "str";
			}
			if (longValue < 0) {
				allNonNegativeIntegral = false;
			}
		}
		return allNonNegativeIntegral ? "cint" : "int";
	}
}
