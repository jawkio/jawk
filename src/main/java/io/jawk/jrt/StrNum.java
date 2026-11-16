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

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * An input-derived scalar: text that may also act as a number, following
 * POSIX "numeric string" (strnum) semantics. Input fields, {@code getline}
 * results, {@code split()} pieces, and similar input-originated values are
 * represented as instances of this class so comparisons and gawk's
 * {@code typeof()} can distinguish them from plain string constants.
 * <p>
 * Instances are serializable so they can be retained in persistent memory.
 */
public final class StrNum implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String value;
	private final char decimalSeparator;
	// Whether Java wrote the text, for a number: its decimal separator is the
	// period in every run, whatever the locale
	private final boolean javaNumber;
	// Parse caches, recomputed on demand after deserialization
	private transient Boolean numeric;
	private transient Double numericValue;

	StrNum(String value) {
		this(value, '.');
	}

	StrNum(String value, char decimalSeparator) {
		this(value, decimalSeparator, false);
	}

	private StrNum(String value, char decimalSeparator, boolean javaNumber) {
		this.value = value == null ? "" : value;
		this.decimalSeparator = decimalSeparator;
		this.javaNumber = javaNumber;
	}

	/**
	 * Creates the numeric string of a Java number a double cannot hold, such as
	 * a {@link BigDecimal}: its text, which keeps its digits.
	 *
	 * @param number number to represent
	 * @return the numeric string
	 */
	static StrNum ofJavaNumber(Number number) {
		return new StrNum(number.toString(), '.', true);
	}

	/**
	 * Returns whether this scalar is the text of a Java number, which reads
	 * with a period as its decimal separator whatever the locale of the run: it
	 * must not be converted again for another locale.
	 *
	 * @return {@code true} when created with {@link #ofJavaNumber(Number)}
	 */
	boolean isJavaNumber() {
		return javaNumber;
	}

	/**
	 * Returns whether this scalar's text parses as an AWK number, making it a
	 * strnum.
	 *
	 * @return {@code true} when the text is a valid AWK number
	 */
	public boolean isNumber() {
		if (numeric == null) {
			numeric = Boolean.valueOf(JRT.isParseableNumber(value, decimalSeparator));
		}
		return numeric.booleanValue();
	}

	/**
	 * Returns this scalar's numeric value.
	 *
	 * @return the parsed numeric value
	 */
	public double doubleValue() {
		if (numericValue == null) {
			numericValue = Double.valueOf(parseDoubleValue());
		}
		return numericValue.doubleValue();
	}

	private double parseDoubleValue() {
		// Recognition ignores leading and trailing blanks, so parsing must
		// strip them: the two move together.
		String normalizedValue = JRT.normalizeNumberForComparison(JRT.trimWhitespace(value), decimalSeparator);
		try {
			return Double.parseDouble(normalizedValue);
		} catch (NumberFormatException nfe) {
			return new BigDecimal(normalizedValue).doubleValue();
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
