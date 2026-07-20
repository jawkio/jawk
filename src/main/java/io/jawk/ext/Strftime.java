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
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * C-locale {@code strftime(3)}-style timestamp formatter backing the gawk
 * {@code strftime()} extension function.
 * <p>
 * Day, month, and AM/PM names are the fixed C-locale (English) strings, like
 * gawk running under {@code LC_TIME=C}. Unsupported conversion specifiers are
 * copied to the output verbatim, as glibc does.
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
			char specifier = format.charAt(++i);
			// glibc treats the E and O locale modifiers as no-ops in the C locale
			if ((specifier == 'E' || specifier == 'O') && i + 1 < length) {
				specifier = format.charAt(++i);
			}
			appendSpecifier(out, specifier, calendar);
		}
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
			appendPadded(out, Math.floorMod(calendar.get(Calendar.YEAR), 100), 2);
			break;
		case 'Y':
			out.append(calendar.get(Calendar.YEAR));
			break;
		case 'z':
			appendZoneOffset(out, calendar);
			break;
		case 'Z':
			out
					.append(
							calendar
									.getTimeZone()
									.getDisplayName(
											calendar.get(Calendar.DST_OFFSET) != 0,
											TimeZone.SHORT,
											Locale.US));
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
