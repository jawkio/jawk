package org.metricshub.jawk.frontend.ast;

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

import java.io.IOException;

/**
 * Signals a lexical analysis failure while parsing AWK source text.
 */
public class LexerException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a lexer exception annotated with source location information.
	 *
	 * @param msg Failure message
	 * @param sourceDescription Human-readable source description
	 * @param lineNumber 0-based or parser-reported line number where the error
	 *        occurred
	 */
	public LexerException(String msg, String sourceDescription, int lineNumber) {
		super(msg + " (" + sourceDescription + ":" + lineNumber + ")");
	}
}
