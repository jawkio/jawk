package org.metricshub.jawk.jrt;

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
 * A runtime exception thrown by Jawk. It is provided
 * to conveniently distinguish between Jawk runtime
 * exceptions and other runtime exceptions.
 *
 * @author Danny Daglas
 */
public class AwkRuntimeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Source line associated with the failure, or {@code -1} when unknown. */
	private final int lineNumber;

	/**
	 * <p>
	 * Constructor for AwkRuntimeException.
	 * </p>
	 *
	 * @param msg a {@link java.lang.String} object
	 */
	public AwkRuntimeException(String msg) {
		super(msg);
		this.lineNumber = -1;
	}

	/**
	 * Creates a runtime exception with a message and root cause but without a
	 * specific source line.
	 *
	 * @param msg Failure message
	 * @param cause Root cause
	 */
	public AwkRuntimeException(String msg, Throwable cause) {
		super(msg, cause);
		this.lineNumber = -1;
	}

	/**
	 * <p>
	 * Constructor for AwkRuntimeException.
	 * </p>
	 *
	 * @param lineno a int
	 * @param msg a {@link java.lang.String} object
	 */
	public AwkRuntimeException(int lineno, String msg) {
		super(msg);
		this.lineNumber = lineno;
	}

	/**
	 * Creates a runtime exception tied to a source line and a root cause.
	 *
	 * @param lineno AWK source line associated with the failure
	 * @param msg Failure message
	 * @param cause Root cause
	 */
	public AwkRuntimeException(int lineno, String msg, Throwable cause) {
		super(msg, cause);
		this.lineNumber = lineno;
	}

	/**
	 * Returns the line number associated with this exception or {@code -1} if
	 * unavailable.
	 *
	 * @return the offending line number or {@code -1}
	 */
	public int getLineNumber() {
		return lineNumber;
	}
}
