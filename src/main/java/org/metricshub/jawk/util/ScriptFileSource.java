package org.metricshub.jawk.util;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright 2006 - 2026 MetricsHub
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Represents one AWK-script file content source.
 *
 * @author Danny Daglas
 */
public class ScriptFileSource extends ScriptSource {

	private String filePath;
	private Reader fileReader;

	/**
	 * <p>
	 * Constructor for ScriptFileSource.
	 * </p>
	 *
	 * @param filePath a {@link java.lang.String} object
	 */
	public ScriptFileSource(String filePath) {
		super(filePath, null);
		this.filePath = filePath;
		this.fileReader = null;
	}

	/**
	 * <p>
	 * Getter for the field <code>filePath</code>.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getFilePath() {
		return filePath;
	}

	/** {@inheritDoc} */
	@Override
	public Reader getReader() {
		if (fileReader == null) {
			try {
				fileReader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
			} catch (IOException ex) {
				throw new UncheckedIOException("Failed to open script source for reading: " + filePath, ex);
			}
		}

		return fileReader;
	}
}
