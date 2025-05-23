package org.metricshub.jawk.util;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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
import java.io.InputStream;
import java.io.Reader;

/**
 * Represents one AWK-script content source.
 * This is usually either a string,
 * given on the command line with the first non-option parameter,
 * or after the "--" argument,
 * or an "*.awk" (normal) or "*.ai" (intermediate) script,
 * given as a path with a "-f" command line switch.
 *
 * @author Danny Daglas
 */
public class ScriptSource {

	/** Constant <code>DESCRIPTION_COMMAND_LINE_SCRIPT="&lt;command-line-supplied-script&gt;"</code> */
	public static final String DESCRIPTION_COMMAND_LINE_SCRIPT
			= "<command-line-supplied-script>";

	private String description;
	private Reader reader;
	private boolean intermediate;

	/**
	 * <p>Constructor for ScriptSource.</p>
	 *
	 * @param description a {@link java.lang.String} object
	 * @param reader a {@link java.io.Reader} object
	 * @param intermediate a boolean
	 */
	public ScriptSource(String description, Reader reader, boolean intermediate) {

		this.description = description;
		this.reader = reader;
		this.intermediate = intermediate;
	}

	/**
	 * <p>Getter for the field <code>description</code>.</p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public final String getDescription() {
		return description;
	}

	/**
	 * Obtain the InputStream containing the intermediate file.
	 * This returns non-null only if {@see #isIntermediate()}
	 * returns <code>false</code>.
	 *
	 * @return The reader which contains the intermediate file, null if
	 *   either the -f argument is not used, or the argument does not
	 *   refer to an intermediate file.
	 * @throws java.io.IOException if any.
	 */
	public Reader getReader()
			throws IOException
	{
		return reader;
	}

	/**
	 * Returns the <code>InputStream</code> serving the contents of this source.
	 * This returns non-<code>null</code> only if {@see #isIntermediate()}
	 * returns <code>true</code>.
	 *
	 * @return a {@link java.io.InputStream} object
	 * @throws java.io.IOException if any.
	 */
	public InputStream getInputStream()
			throws IOException
	{
		return null;
	}

	/**
	 * Indicates whether the underlying source is an intermediate file.
	 * Intermediate files end with the ".ai" extension.
	 * No other determination is made whether the file is an intermediate
	 * one or not.
	 * That is, the content of the file is not checked.
	 *
	 * @return <code>true</code> if the "-f optarg" is an intermediate file
	 *   (a file ending in ".ai"), <code>false</code> otherwise.
	 */
	public final boolean isIntermediate() {
		return intermediate;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getDescription();
	}
}
