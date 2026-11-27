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

/**
 * Thrown, when {@link io.jawk.util.AwkSettings#setStrictNumbers(boolean)
 * strict numbers} are enabled, by an arithmetic operator or a numeric function
 * whose operand is not a number, and by {@link JRT#requireNumber(Object)},
 * which an extension function calls whatever the setting.
 */
public class AwkNumberFormatException extends AwkRuntimeException {

	private static final long serialVersionUID = 1L;

	/** Offending text, or {@code null} for a value that was never assigned. */
	private final String value;

	/**
	 * Creates the exception for an operand that is not a number.
	 *
	 * @param value text of the operand, or {@code null} for a value that was
	 *        never assigned (a variable, an array element, a field beyond
	 *        {@code NF})
	 */
	public AwkNumberFormatException(String value) {
		this(-1, value);
	}

	/**
	 * Creates the exception for an operand that is not a number, tied to a
	 * source line.
	 *
	 * @param lineno AWK source line of the failure, or {@code -1} when unknown
	 * @param value text of the operand, or {@code null} for a value that was
	 *        never assigned
	 */
	public AwkNumberFormatException(int lineno, String value) {
		super(lineno, value == null ? "An unassigned value is not a number" : "\"" + value + "\" is not a number");
		this.value = value;
	}

	/**
	 * Returns the text that was used as a number.
	 *
	 * @return the offending text, or {@code null} for a value that was never
	 *         assigned
	 */
	public String getValue() {
		return value;
	}
}
