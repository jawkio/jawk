package io.jawk.backend;

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure regex replacement helpers used by the AVM and the gawk compatibility
 * extension.
 */
public final class RegexRuntimeSupport {

	private RegexRuntimeSupport() {}

	static String prepareReplacement(String awkRepl) {
		return prepareReplacement(awkRepl, false);
	}

	/**
	 * Converts an AWK replacement text into a Java {@link Matcher} replacement:
	 * {@code &} becomes the whole match, {@code \&} a literal ampersand, and
	 * {@code $} is escaped.
	 *
	 * @param awkRepl AWK replacement text
	 * @param backreferences whether {@code \N} denotes capture group {@code N},
	 *        as in gawk's {@code gensub()}; when {@code false}, {@code \N} stays
	 *        literal as in {@code sub()} and {@code gsub()}
	 * @return the equivalent Java replacement string
	 */
	public static String prepareReplacement(String awkRepl, boolean backreferences) {
		if (awkRepl == null) {
			return "";
		}

		if ((awkRepl.indexOf('\\') == -1) && (awkRepl.indexOf('$') == -1) && (awkRepl.indexOf('&') == -1)) {
			return awkRepl;
		}

		StringBuilder javaRepl = new StringBuilder();
		for (int i = 0; i < awkRepl.length(); i++) {
			char c = awkRepl.charAt(i);

			if (c == '\\' && i == awkRepl.length() - 1) {
				// In gensub mode a trailing backslash is a literal backslash;
				// left bare it would make Matcher.appendReplacement throw. The
				// sub()/gsub() mapping keeps its historical bare form.
				javaRepl.append(backreferences ? "\\\\" : "\\");
				continue;
			}

			if (c == '\\') {
				i++;
				c = awkRepl.charAt(i);
				if (c == '&') {
					javaRepl.append('&');
					continue;
				} else if (c == '\\') {
					javaRepl.append("\\\\");
					continue;
				} else if (backreferences && Character.isDigit(c)) {
					javaRepl.append('$').append(c);
					continue;
				}

				javaRepl.append('\\');
			}

			if (c == '$') {
				javaRepl.append("\\$");
			} else if (c == '&') {
				javaRepl.append("$0");
			} else {
				javaRepl.append(c);
			}
		}

		return javaRepl.toString();
	}

	static Integer replaceFirst(String origValue, String repl, String ere, StringBuffer sb) {
		String preparedReplacement = prepareReplacement(repl);
		sb.setLength(0);

		Pattern pattern = Pattern.compile(ere);
		Matcher matcher = pattern.matcher(origValue);
		int count = 0;
		if (matcher.find()) {
			count++;
			matcher.appendReplacement(sb, preparedReplacement);
		}
		matcher.appendTail(sb);
		return Integer.valueOf(count);
	}

	static Integer replaceAll(String origValue, String repl, String ere, StringBuffer sb) {
		sb.setLength(0);

		String preparedReplacement = prepareReplacement(repl);
		Pattern pattern = Pattern.compile(ere);
		Matcher matcher = pattern.matcher(origValue);
		int count = 0;
		while (matcher.find()) {
			count++;
			matcher.appendReplacement(sb, preparedReplacement);
		}
		matcher.appendTail(sb);
		return Integer.valueOf(count);
	}
}
