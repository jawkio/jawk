package io.jawk;

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
 * Exception thrown when an operation is not permitted in sandbox mode.
 */
public class AwkSandboxException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new sandbox exception with the provided message.
	 *
	 * @param message description of the sandbox violation
	 */
	public AwkSandboxException(String message) {
		super(message);
	}

	/**
	 * Creates a new sandbox exception with the provided message and cause.
	 *
	 * @param message description of the sandbox violation
	 * @param cause underlying cause of the violation
	 */
	public AwkSandboxException(String message, Throwable cause) {
		super(message, cause);
	}
}
