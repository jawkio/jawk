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
import java.util.regex.Pattern;

/**
 * Similar to StringTokenizer, except that tokens are delimited
 * by a regular expression.
 *
 * @author Danny Daglas
 */
public class RegexTokenizer implements Enumeration<Object> {

	private String[] array;
	private int idx = 0;

	/**
	 * Construct a RegexTokenizer.
	 *
	 * @param input The input string to tokenize.
	 * @param delimitterRegexPattern The regular expression delineating tokens
	 *        within the input string.
	 */
	public RegexTokenizer(String input, String delimitterRegexPattern) {
		this(input, delimitterRegexPattern, 0);
	}

	/**
	 * Construct a RegexTokenizer with explicit {@link Pattern} flags.
	 *
	 * @param input The input string to tokenize.
	 * @param delimitterRegexPattern The regular expression delineating tokens
	 *        within the input string.
	 * @param flags {@link Pattern} compilation flags, such as
	 *        {@link Pattern#CASE_INSENSITIVE}
	 */
	public RegexTokenizer(String input, String delimitterRegexPattern, int flags) {
		this(input, Pattern.compile(delimitterRegexPattern, flags));
	}

	/**
	 * Construct a RegexTokenizer around a precompiled pattern.
	 *
	 * @param input The input string to tokenize.
	 * @param pattern The compiled delimiter pattern.
	 */
	public RegexTokenizer(String input, Pattern pattern) {
		if (input.isEmpty()) {
			array = new String[0];
		} else {
			array = pattern.split(input, -1);
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasMoreElements() {
		return idx < array.length;
	}

	/** {@inheritDoc} */
	@Override
	public Object nextElement() {
		return array[idx++];
	}
}
