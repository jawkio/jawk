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
 * Thrown when exit() is called within a Jawk script.
 * <p>
 * Within Jawk, EndException is caught twice. The first
 * catch block executes when exit() is called within BEGIN
 * or action blocks. When invoked, the END blocks are
 * executed. The second catch block executes when exit() is
 * called within any of the END blocks. When invoked,
 * Jawk terminates with an exit code.
 *
 * @author Danny Daglas
 */
public class EndException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * <p>
	 * Constructor for EndException.
	 * </p>
	 *
	 * @param s a {@link java.lang.String} object
	 */
	public EndException(String s) {
		super(s);
	}

	/**
	 * <p>
	 * Constructor for EndException.
	 * </p>
	 */
	public EndException() {
		super();
	}
}
