package org.metricshub.jawk.jrt;

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

import java.util.Enumeration;

/**
 * Similar to StringTokenizer, except that tokens are delimited
 * by a single character.
 *
 * @author Danny Daglas
 */
public class SingleCharacterTokenizer implements Enumeration<Object> {

	private String input;
	private char splitChar;
	private int currentPos = 0;
	private boolean hasMoreTokens = true;

	/**
	 * Construct a RegexTokenizer.
	 *
	 * @param input The input string to tokenize.
	 * @param splitChar The character which delineates tokens
	 *        within the input string.
	 */
	public SingleCharacterTokenizer(String input, int splitChar) {
		this.input = input;
		this.splitChar = (char) splitChar;
		hasMoreTokens = !input.isEmpty();
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasMoreElements() {
		return hasMoreTokens;
	}

	/** {@inheritDoc} */
	@Override
	public Object nextElement() {

		for (int i = currentPos; i < input.length(); i++) {
			if (input.charAt(i) == splitChar) {
				String token = input.substring(currentPos, i);
				currentPos = i + 1;
				return token;
			}
		}

		// We reached the end of the input, return what we have
		String token = input.substring(currentPos);
		currentPos = input.length();
		hasMoreTokens = false;
		return token;
	}
}
