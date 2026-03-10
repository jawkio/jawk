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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Load classes from a particular directory, disregarding the
 * environmental class-path setting.
 * This is useful when a directory is specified for class files,
 * and it would not make sense to deviate from that directory.
 * So this ClassLoader does practically the same
 * like a <code>URLClassLoader</code> with a "file://.../" URL,
 * except that it does not forward calls to its parent,
 * if it can not find the class its self.
 * <p>
 * For Jawk, this is used when the -d argument is present.
 *
 * @author Danny Daglas
 */
public final class DestDirClassLoader extends ClassLoader {

	private String dirname;

	/**
	 * <p>
	 * Constructor for DestDirClassLoader.
	 * </p>
	 *
	 * @param dirname a {@link java.lang.String} object
	 */
	public DestDirClassLoader(String dirname) {
		this.dirname = dirname;
	}

	/** {@inheritDoc} */
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		byte[] b = loadClassData(name);
		return defineClass(name, b, 0, b.length);
	}

	private byte[] loadClassData(String name) throws ClassNotFoundException {
		String fileName = dirname + File.separator + name + ".class";
		try (FileInputStream f = new FileInputStream(fileName); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			byte[] b = new byte[4096];
			int len;
			while ((len = f.read(b, 0, b.length)) >= 0) {
				baos.write(b, 0, len);
			}
			return baos.toByteArray();
		} catch (IOException ioe) {
			throw new ClassNotFoundException("Could not load class " + name + " from file \"" + fileName + "\"", ioe);
		}
	}
}
