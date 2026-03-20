package org.metricshub.jawk.backend;

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
 * Pure regex replacement helpers used by the AVM.
 */
final class RegexRuntimeSupport {

	private RegexRuntimeSupport() {}

	static String prepareReplacement(String awkRepl) {
		if (awkRepl == null) {
			return "";
		}

		if ((awkRepl.indexOf('\\') == -1) && (awkRepl.indexOf('$') == -1) && (awkRepl.indexOf('&') == -1)) {
			return awkRepl;
		}

		StringBuilder javaRepl = new StringBuilder();
		for (int i = 0; i < awkRepl.length(); i++) {
			char c = awkRepl.charAt(i);

			if (c == '\\' && i < awkRepl.length() - 1) {
				i++;
				c = awkRepl.charAt(i);
				if (c == '&') {
					javaRepl.append('&');
					continue;
				} else if (c == '\\') {
					javaRepl.append("\\\\");
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
