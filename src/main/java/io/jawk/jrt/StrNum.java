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

final class StrNum {

	private final String value;
	private final char decimalSeparator;
	private Boolean numeric;
	private Double numericValue;

	StrNum(String value) {
		this(value, '.');
	}

	StrNum(String value, char decimalSeparator) {
		this.value = value == null ? "" : value;
		this.decimalSeparator = decimalSeparator;
	}

	boolean isNumber() {
		if (numeric == null) {
			numeric = Boolean.valueOf(JRT.isParseableNumber(value, decimalSeparator));
		}
		return numeric.booleanValue();
	}

	double doubleValue() {
		if (numericValue == null) {
			numericValue = Double.valueOf(parseDoubleValue());
		}
		return numericValue.doubleValue();
	}

	private double parseDoubleValue() {
		try {
			return new BigDecimal(JRT.normalizeNumberForComparison(value, decimalSeparator)).doubleValue();
		} catch (NumberFormatException nfe) {
			return JRT.parseDoubleForComparison(value, decimalSeparator);
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
