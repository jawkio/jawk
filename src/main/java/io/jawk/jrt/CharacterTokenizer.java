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

import java.util.Enumeration;

/**
 * Similar to StringTokenizer, except that tokens are characters
 * in the input string themselves.
 * <p>
 * For Jawk, this class is used when NF == "".
 *
 * @author Danny Daglas
 */
public class CharacterTokenizer implements Enumeration<Object> {

	private String input;
	private int idx = 0;

	/**
	 * Construct a CharacterTokenizer.
	 *
	 * @param input The input string to tokenize.
	 */
	public CharacterTokenizer(String input) {
		this.input = input;
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasMoreElements() {
		return idx < input.length();
	}

	/** {@inheritDoc} */
	@Override
	public Object nextElement() {
		return Character.toString(input.charAt(idx++));
	}
}
