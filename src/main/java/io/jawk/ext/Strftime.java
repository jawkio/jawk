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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * C-locale {@code strftime(3)}-style timestamp formatter backing the gawk
 * {@code strftime()} extension function.
 * <p>
 * Day, month, and AM/PM names are the fixed C-locale (English) strings, like
 * gawk running under {@code LC_TIME=C}. Unsupported conversion specifiers are
 * copied to the output verbatim, as glibc does. Calendar arithmetic follows
 * Java's rules: dates before the common era format with the JDK's year
 * numbering rather than gawk's astronomical years.
 */
final class Strftime {

	/** Abbreviated day names, Sunday first, as in the C locale. */
	private static final String[] SHORT_DAYS = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };

	/** Full day names, Sunday first, as in the C locale. */
	private static final String[] FULL_DAYS = {
			"Sunday",
			"Monday",
			"Tuesday",
			"Wednesday",
			"Thursday",
			"Friday",
			"Saturday"
	};

	/** Abbreviated month names, as in the C locale. */
	private static final String[] SHORT_MONTHS = {
			"Jan",
			"Feb",
			"Mar",
			"Apr",
			"May",
			"Jun",
			"Jul",
			"Aug",
			"Sep",
			"Oct",
			"Nov",
			"Dec"
	};

	/** Full month names, as in the C locale. */
	private static final String[] FULL_MONTHS = {
			"January",
			"February",
			"March",
			"April",
			"May",
			"June",
			"July",
			"August",
			"September",
			"October",
			"November",
			"December"
	};

	private Strftime() {
		// utility class
	}

	/**
	 * Formats a timestamp with {@code strftime(3)} conversion specifiers.
	 *
	 * @param format format string with {@code %}-conversions
	 * @param epochSeconds seconds since the epoch
	 * @param timeZone time zone used to break the timestamp down
	 * @return formatted timestamp
	 */
	static String format(String format, long epochSeconds, TimeZone timeZone) {
		GregorianCalendar calendar = new GregorianCalendar(timeZone, Locale.US);
		// proleptic Gregorian: gawk's civil dates never switch to the Julian
		// calendar, so 1500-01-01 round-trips with mktime()
		calendar.setGregorianChange(new Date(Long.MIN_VALUE));
		// ISO 8601 week rules, needed by %V, %G, and %g; %U and %W are
		// computed from the day of year instead, so this setting is safe
		calendar.setFirstDayOfWeek(Calendar.MONDAY);
		calendar.setMinimalDaysInFirstWeek(4);
		calendar.setTimeInMillis(epochSeconds * 1000L);
		StringBuilder out = new StringBuilder(format.length() * 2);
		appendFormat(out, format, calendar);
		return out.toString();
	}

	private static void appendFormat(StringBuilder out, String format, GregorianCalendar calendar) {
		int length = format.length();
		for (int i = 0; i < length; i++) {
			char c = format.charAt(i);
			if (c != '%' || i + 1 >= length) {
				out.append(c);
				continue;
			}
			i = appendConversion(out, format, i + 1, calendar);
		}
	}

	/**
	 * Renders one conversion whose {@code %} sits at {@code index - 1},
	 * honoring the GNU flag and field-width syntax ({@code %-d}, {@code %_d},
	 * {@code %^a}, {@code %5d}, ...) that gawk supports through glibc.
	 *
	 * @return index of the conversion's last consumed character
	 */
	private static int appendConversion(StringBuilder out, String format, int index, GregorianCalendar calendar) {
		int length = format.length();
		int i = index;
		// GNU flags: '-' no padding, '_' space padding, '0' zero padding,
		// '^' uppercase, '#' swap case
		boolean noPadding = false;
		boolean upperCase = false;
		boolean swapCase = false;
		char padOverride = 0;
		for (; i < length; i++) {
			char c = format.charAt(i);
			if (c == '-') {
				noPadding = true;
			} else if (c == '_') {
				padOverride = ' ';
			} else if (c == '0') {
				padOverride = '0';
			} else if (c == '^') {
				upperCase = true;
			} else if (c == '#') {
				swapCase = true;
			} else {
				break;
			}
		}
		int width = -1;
		while (i < length && format.charAt(i) >= '0' && format.charAt(i) <= '9') {
			width = Math.max(width, 0) * 10 + format.charAt(i) - '0';
			i++;
		}
		// glibc treats the E and O locale modifiers as no-ops in the C locale
		if (i + 1 < length && (format.charAt(i) == 'E' || format.charAt(i) == 'O')) {
			i++;
		}
		if (i >= length) {
			// dangling specifier: emit verbatim, as glibc does
			out.append(format, index - 1, length);
			return length - 1;
		}
		char specifier = format.charAt(i);
		if (!noPadding && !upperCase && !swapCase && padOverride == 0 && width < 0) {
			appendSpecifier(out, specifier, calendar);
			return i;
		}
		StringBuilder piece = new StringBuilder();
		appendSpecifier(piece, specifier, calendar);
		out.append(transform(piece.toString(), noPadding, padOverride, width, upperCase, swapCase));
		return i;
	}

	/** Applies the GNU padding and case flags to a rendered conversion. */
	private static String transform(
			String text,
			boolean noPadding,
			char padOverride,
			int width,
			boolean upperCase,
			boolean swapCase) {
		String result = text;
		if (noPadding) {
			// '-' drops the conversion's own padding; an explicitly requested
			// width is still honored, padded with spaces
			result = pad(stripPadding(result), ' ', Math.max(width, 0));
		} else if (padOverride != 0) {
			int naturalWidth = result.length();
			result = pad(stripPadding(result), padOverride, Math.max(width, naturalWidth));
		} else if (width >= 0) {
			// width alone extends the conversion's own padding: numeric
			// conversions grow with zeros, composite and text ones with spaces
			result = pad(result, defaultPad(result), width);
		}
		if (upperCase) {
			result = result.toUpperCase(Locale.US);
		}
		if (swapCase) {
			result = alternateCase(result);
		}
		return result;
	}

	/** Pads a rendered conversion on the left to the requested width. */
	private static String pad(String text, char pad, int width) {
		if (text.length() >= width) {
			return text;
		}
		StringBuilder padded = new StringBuilder(width);
		for (int i = text.length(); i < width; i++) {
			padded.append(pad);
		}
		return padded.append(text).toString();
	}

	/** Removes the leading zero or space padding of a rendered conversion. */
	private static String stripPadding(String text) {
		int start = 0;
		while (start < text.length() - 1 && (text.charAt(start) == '0' || text.charAt(start) == ' ')) {
			start++;
		}
		return text.substring(start);
	}

	/**
	 * Purely numeric conversions grow with zeros; composite conversions such
	 * as {@code %D} and textual ones pad with spaces, as in gawk.
	 */
	private static char defaultPad(String text) {
		if (text.isEmpty()) {
			return ' ';
		}
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return ' ';
			}
		}
		return '0';
	}

	/**
	 * The GNU {@code #} flag: glibc uppercases conversions that normally
	 * produce lowercase or mixed-case text ({@code %#a} is {@code SUN}) and
	 * lowercases the normally-uppercase ones ({@code %#p} is {@code am}).
	 */
	private static String alternateCase(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isLowerCase(text.charAt(i))) {
				return text.toUpperCase(Locale.US);
			}
		}
		return text.toLowerCase(Locale.US);
	}

	private static void appendSpecifier(StringBuilder out, char specifier, GregorianCalendar calendar) {
		switch (specifier) {
		case 'a':
			out.append(SHORT_DAYS[dayOfWeek(calendar)]);
			break;
		case 'A':
			out.append(FULL_DAYS[dayOfWeek(calendar)]);
			break;
		case 'b':
		case 'h':
			out.append(SHORT_MONTHS[calendar.get(Calendar.MONTH)]);
			break;
		case 'B':
			out.append(FULL_MONTHS[calendar.get(Calendar.MONTH)]);
			break;
		case 'c':
			appendFormat(out, "%a %b %e %H:%M:%S %Y", calendar);
			break;
		case 'C':
			appendPadded(out, calendar.get(Calendar.YEAR) / 100, 2);
			break;
		case 'd':
			appendPadded(out, calendar.get(Calendar.DAY_OF_MONTH), 2);
			break;
		case 'D':
		case 'x':
			appendFormat(out, "%m/%d/%y", calendar);
			break;
		case 'e': {
			int day = calendar.get(Calendar.DAY_OF_MONTH);
			if (day < 10) {
				out.append(' ');
			}
			out.append(day);
			break;
		}
		case 'F':
			appendFormat(out, "%Y-%m-%d", calendar);
			break;
		case 'g':
			appendPadded(out, Math.floorMod(calendar.getWeekYear(), 100), 2);
			break;
		case 'G':
			out.append(calendar.getWeekYear());
			break;
		case 'H':
			appendPadded(out, calendar.get(Calendar.HOUR_OF_DAY), 2);
			break;
		case 'I':
			appendPadded(out, hour12(calendar), 2);
			break;
		case 'j':
			appendPadded(out, calendar.get(Calendar.DAY_OF_YEAR), 3);
			break;
		case 'm':
			appendPadded(out, calendar.get(Calendar.MONTH) + 1, 2);
			break;
		case 'M':
			appendPadded(out, calendar.get(Calendar.MINUTE), 2);
			break;
		case 'n':
			out.append('\n');
			break;
		case 'p':
			out.append(calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
			break;
		case 'r':
			appendFormat(out, "%I:%M:%S %p", calendar);
			break;
		case 'R':
			appendFormat(out, "%H:%M", calendar);
			break;
		case 's':
			out.append(Math.floorDiv(calendar.getTimeInMillis(), 1000L));
			break;
		case 'S':
			appendPadded(out, calendar.get(Calendar.SECOND), 2);
			break;
		case 't':
			out.append('\t');
			break;
		case 'T':
		case 'X':
			appendFormat(out, "%H:%M:%S", calendar);
			break;
		case 'u': {
			int dayOfWeek = dayOfWeek(calendar);
			out.append(dayOfWeek == 0 ? 7 : dayOfWeek);
			break;
		}
		case 'U':
			appendPadded(out, weekNumber(calendar, dayOfWeek(calendar)), 2);
			break;
		case 'V':
			appendPadded(out, calendar.get(Calendar.WEEK_OF_YEAR), 2);
			break;
		case 'w':
			out.append(dayOfWeek(calendar));
			break;
		case 'W':
			appendPadded(out, weekNumber(calendar, (dayOfWeek(calendar) + 6) % 7), 2);
			break;
		case 'y':
			appendPadded(out, calendar.get(Calendar.YEAR) % 100, 2);
			break;
		case 'Y':
			out.append(calendar.get(Calendar.YEAR));
			break;
		case 'z':
			appendZoneOffset(out, calendar);
			break;
		case 'Z':
			out.append(zoneName(calendar));
			break;
		case '%':
			out.append('%');
			break;
		default:
			// unsupported conversions pass through verbatim, as in glibc
			out.append('%').append(specifier);
			break;
		}
	}

	/**
	 * Returns the zone abbreviation for {@code %Z}, choosing the standard or
	 * daylight designation from the formatted instant. The JDK's time zone
	 * data carries per-instant offsets and DST rules but not historical
	 * designations, so a zone that changed names over time (such as
	 * America/Indiana/Knox, Eastern until 2006) reports its current
	 * designation even for older timestamps; the {@code %z} offset is always
	 * the historical one.
	 */
	private static String zoneName(GregorianCalendar calendar) {
		return calendar
				.getTimeZone()
				.getDisplayName(calendar.get(Calendar.DST_OFFSET) != 0, TimeZone.SHORT, Locale.US);
	}

	/** Day of week, 0 = Sunday, matching C's {@code tm_wday}. */
	private static int dayOfWeek(Calendar calendar) {
		return calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
	}

	/** Hour on the 12-hour clock, 12 for midnight and noon. */
	private static int hour12(Calendar calendar) {
		int hour = calendar.get(Calendar.HOUR);
		return hour == 0 ? 12 : hour;
	}

	/**
	 * Week number for {@code %U} and {@code %W}: full and partial weeks since
	 * the first week's starting weekday, computed from the C {@code tm_yday}
	 * and {@code tm_wday} fields.
	 *
	 * @param calendar broken-down timestamp
	 * @param daysSinceWeekStart days elapsed since the week's first day
	 * @return week number, 0 when the date precedes the year's first week
	 */
	private static int weekNumber(Calendar calendar, int daysSinceWeekStart) {
		int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR) - 1;
		return (dayOfYear + 7 - daysSinceWeekStart) / 7;
	}

	private static void appendZoneOffset(StringBuilder out, Calendar calendar) {
		int offsetMinutes = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 60000;
		out.append(offsetMinutes < 0 ? '-' : '+');
		int absoluteMinutes = Math.abs(offsetMinutes);
		appendPadded(out, absoluteMinutes / 60, 2);
		appendPadded(out, absoluteMinutes % 60, 2);
	}

	private static void appendPadded(StringBuilder out, int value, int width) {
		if (value < 0) {
			// fields are non-negative in practice; print pre-year-0 values raw
			out.append(value);
			return;
		}
		String digits = Integer.toString(value);
		for (int i = digits.length(); i < width; i++) {
			out.append('0');
		}
		out.append(digits);
	}
}
