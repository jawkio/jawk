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
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * Parser for POSIX {@code TZ} environment specifications such as
 * {@code XXX3}, {@code EST5EDT4}, or {@code CET-1CEST,M3.5.0,M10.5.0/3},
 * which {@link TimeZone#getTimeZone(String)} does not understand (it falls
 * back to GMT). The grammar is
 * {@code std offset[dst[offset][,start[/time],end[/time]]]}; when the DST
 * transition rules are omitted, the current United States rules apply, as in
 * glibc.
 */
final class PosixTimeZone {

	/** Default transition time, 02:00:00 local, per POSIX. */
	private static final int DEFAULT_TRANSITION_SECONDS = 7200;

	/** Cumulative days before each month for the {@code Jn} day-of-year form. */
	private static final int[] DAYS_BEFORE_MONTH = { 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334 };

	/** POSIX TZ specification being parsed. */
	private final String spec;

	/** Cursor into {@link #spec}. */
	private int pos;

	/** One DST transition rule: either day-of-week-in-month or an exact date. */
	private static final class Rule {
		private int month;
		private int day;
		private int dayOfWeek;
		private int timeMillis = DEFAULT_TRANSITION_SECONDS * 1000;
	}

	private PosixTimeZone(String specParam) {
		this.spec = specParam;
	}

	/**
	 * Parses a POSIX TZ specification.
	 *
	 * @param spec specification, e.g. {@code CET-1CEST,M3.5.0,M10.5.0/3}
	 * @return the corresponding time zone, or {@code null} when the
	 *         specification does not follow the POSIX grammar
	 */
	static TimeZone parse(String spec) {
		try {
			return new PosixTimeZone(spec).timeZone();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private TimeZone timeZone() {
		name();
		// POSIX offsets are positive west of Greenwich: XXX3 is UTC-03:00
		int standardOffsetMillis = -offsetSeconds() * 1000;
		if (atEnd()) {
			return new SimpleTimeZone(standardOffsetMillis, spec);
		}
		name();
		int daylightOffsetMillis = standardOffsetMillis + 3600000;
		if (!atEnd() && peek() != ',') {
			daylightOffsetMillis = -offsetSeconds() * 1000;
		}
		int savingsMillis = daylightOffsetMillis - standardOffsetMillis;
		if (savingsMillis <= 0) {
			throw new IllegalArgumentException("Daylight time must be ahead of standard time: " + spec);
		}
		Rule start;
		Rule end;
		if (atEnd()) {
			// no explicit rules: glibc applies the current US rules
			start = usRule(Calendar.MARCH, 2);
			end = usRule(Calendar.NOVEMBER, 1);
		} else {
			expect(',');
			start = rule();
			expect(',');
			end = rule();
			if (!atEnd()) {
				throw new IllegalArgumentException("Trailing characters in TZ specification: " + spec);
			}
		}
		return new SimpleTimeZone(
				standardOffsetMillis,
				spec,
				start.month,
				start.day,
				start.dayOfWeek,
				start.timeMillis,
				end.month,
				end.day,
				end.dayOfWeek,
				end.timeMillis,
				savingsMillis);
	}

	/** The current US transition rule: n-th Sunday of the month at 02:00. */
	private static Rule usRule(int month, int weekOfMonth) {
		Rule rule = new Rule();
		rule.month = month;
		rule.day = weekOfMonth;
		rule.dayOfWeek = Calendar.SUNDAY;
		return rule;
	}

	/*
	 * A transition rule: Mm.w.d (day d of week w in month m, w=5 meaning the
	 * last one), Jn (day of a year without leap days), or n (zero-based day
	 * of year); each optionally followed by /time.
	 */
	private Rule rule() {
		Rule rule = new Rule();
		if (!atEnd() && peek() == 'M') {
			pos++;
			int month = number(1, 12);
			expect('.');
			int week = number(1, 5);
			expect('.');
			int weekday = number(0, 6);
			rule.month = month - 1;
			// SimpleTimeZone: 5th week means the last occurrence
			rule.day = week == 5 ? -1 : week;
			rule.dayOfWeek = weekday + 1;
		} else {
			int dayOfYear;
			if (!atEnd() && peek() == 'J') {
				pos++;
				dayOfYear = number(1, 365) - 1;
			} else {
				// the zero-based form counts leap days; treating it as a
				// non-leap ordinal is the closest exact-date approximation
				dayOfYear = number(0, 365);
			}
			int month = 11;
			while (month > 0 && DAYS_BEFORE_MONTH[month] > dayOfYear) {
				month--;
			}
			rule.month = month;
			rule.day = dayOfYear - DAYS_BEFORE_MONTH[month] + 1;
			// dayOfWeek 0 selects SimpleTimeZone's exact day-of-month form
			rule.dayOfWeek = 0;
		}
		if (!atEnd() && peek() == '/') {
			pos++;
			rule.timeMillis = offsetSeconds() * 1000;
		}
		return rule;
	}

	/** A zone name: either {@code <quoted>} or a run of letters. */
	private void name() {
		if (!atEnd() && peek() == '<') {
			int close = spec.indexOf('>', pos + 1);
			if (close < 0) {
				throw new IllegalArgumentException("Unterminated quoted zone name: " + spec);
			}
			pos = close + 1;
			return;
		}
		int start = pos;
		while (!atEnd() && Character.isLetter(peek())) {
			pos++;
		}
		if (pos == start) {
			throw new IllegalArgumentException("Missing zone name in TZ specification: " + spec);
		}
	}

	/** A signed {@code hh[:mm[:ss]]} offset, in seconds. */
	private int offsetSeconds() {
		int sign = 1;
		if (!atEnd() && (peek() == '+' || peek() == '-')) {
			if (peek() == '-') {
				sign = -1;
			}
			pos++;
		}
		int seconds = number(0, 167) * 3600;
		if (!atEnd() && peek() == ':') {
			pos++;
			seconds += number(0, 59) * 60;
			if (!atEnd() && peek() == ':') {
				pos++;
				seconds += number(0, 59);
			}
		}
		return sign * seconds;
	}

	private int number(int min, int max) {
		int start = pos;
		int value = 0;
		while (!atEnd() && peek() >= '0' && peek() <= '9') {
			value = value * 10 + peek() - '0';
			pos++;
		}
		if (pos == start || value < min || value > max) {
			throw new IllegalArgumentException("Invalid number in TZ specification: " + spec);
		}
		return value;
	}

	private void expect(char c) {
		if (atEnd() || peek() != c) {
			throw new IllegalArgumentException("Expected '" + c + "' in TZ specification: " + spec);
		}
		pos++;
	}

	private char peek() {
		return spec.charAt(pos);
	}

	private boolean atEnd() {
		return pos >= spec.length();
	}
}
