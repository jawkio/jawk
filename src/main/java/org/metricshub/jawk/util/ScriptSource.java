package org.metricshub.jawk.util;

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
import java.io.Reader;

/**
 * Represents one AWK-script content source.
 * This is usually either a string,
 * given on the command line with the first non-"-" parameter,
 * or an "*.awk" script,
 * given as a path with a "-f" command line switch.
 *
 * @author Danny Daglas
 */
public class ScriptSource {

	/** Constant <code>DESCRIPTION_COMMAND_LINE_SCRIPT="&lt;command-line-supplied-script&gt;"</code> */
	public static final String DESCRIPTION_COMMAND_LINE_SCRIPT = "<command-line-supplied-script>";

	private String description;
	private Reader reader;

	/**
	 * <p>
	 * Constructor for ScriptSource.
	 * </p>
	 *
	 * @param description a {@link java.lang.String} object
	 * @param reader a {@link java.io.Reader} object
	 */
	public ScriptSource(String description, Reader reader) {
		this.description = description;
		this.reader = reader;
	}

	/**
	 * <p>
	 * Getter for the field <code>description</code>.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public final String getDescription() {
		return description;
	}

	/**
	 * Obtain the {@link Reader} serving the script contents.
	 *
	 * @return The reader which contains the script contents.
	 * @throws java.io.IOException if any.
	 */
	public Reader getReader() throws IOException {
		return reader;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getDescription();
	}
}
