package org.metricshub.jawk;

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
 * With this Exception, any part of the code may request a
 * <code>System.exit(code)</code> call with a specific code.
 *
 * @author Danny Daglas
 */
public class ExitException extends Exception {

	private static final long serialVersionUID = 1L;

	/** Constant <code>EXIT_CODE_OK=0</code> */
	public static final int EXIT_CODE_OK = 0;

	/** The exit code being returned */
	private final int code;

	/**
	 * Request exit with the <code>EXIT_CODE_OK</code>.
	 */
	public ExitException() {
		this(EXIT_CODE_OK);
	}

	/**
	 * <p>
	 * Constructor for ExitException.
	 * </p>
	 *
	 * @param code a int
	 */
	public ExitException(int code) {
		this(code, "");
	}

	/**
	 * <p>
	 * Constructor for ExitException.
	 * </p>
	 *
	 * @param message a {@link java.lang.String} object
	 */
	public ExitException(String message) {
		this(EXIT_CODE_OK, message);
	}

	/**
	 * <p>
	 * Constructor for ExitException.
	 * </p>
	 *
	 * @param code a int
	 * @param message a {@link java.lang.String} object
	 */
	public ExitException(int code, String message) {
		this(code, message, null);
	}

	/**
	 * <p>
	 * Constructor for ExitException.
	 * </p>
	 *
	 * @param code a int
	 * @param cause a {@link java.lang.Throwable} object
	 */
	public ExitException(int code, Throwable cause) {
		this(code, "", cause);
	}

	/**
	 * <p>
	 * Constructor for ExitException.
	 * </p>
	 *
	 * @param code a int
	 * @param message a {@link java.lang.String} object
	 * @param cause a {@link java.lang.Throwable} object
	 */
	public ExitException(int code, String message, Throwable cause) {
		super(message + " (exit-code: " + code + ")", cause);
		this.code = code;
	}

	/**
	 * Returns the code to be passed to the <code>System.exit(code)</code> call.
	 *
	 * @return a int
	 */
	public int getCode() {
		return code;
	}
}
