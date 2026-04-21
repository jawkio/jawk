package io.jawk;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class GawkManualCasesParser {

	private static final String CASES_PROPERTY = "cases";

	private GawkManualCasesParser() {}

	static List<GawkCompatibilityCase> parse(Reader reader) throws IOException {
		Properties properties = new Properties();
		properties.load(reader);
		List<String> caseNames = parseCaseNames(properties.getProperty(CASES_PROPERTY));
		List<GawkCompatibilityCase> cases = new ArrayList<>(caseNames.size());
		Set<String> seen = new LinkedHashSet<>();
		for (String caseName : caseNames) {
			if (!seen.add(caseName)) {
				throw new IllegalArgumentException("Duplicate manual gawk case name: " + caseName);
			}
			cases.add(parseCase(properties, caseName));
		}
		return Collections.unmodifiableList(cases);
	}

	private static GawkCompatibilityCase parseCase(Properties properties, String caseName) {
		List<String> scripts = parseList(require(properties, caseName, "scripts"));
		if (scripts.isEmpty()) {
			throw new IllegalArgumentException("Manual gawk case " + caseName + " must declare at least one script");
		}
		return new ManualGawkCase(
				caseName,
				parseList(properties.getProperty(propertyName(caseName, "arguments"))),
				scripts,
				parseList(properties.getProperty(propertyName(caseName, "operands"))),
				optional(properties, caseName, "stdin"),
				require(properties, caseName, "expected"));
	}

	private static List<String> parseCaseNames(String rawCaseNames) {
		if (rawCaseNames == null || rawCaseNames.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<String> caseNames = new ArrayList<>();
		for (String rawCaseName : rawCaseNames.split(",")) {
			String caseName = rawCaseName.trim();
			if (!caseName.isEmpty()) {
				caseNames.add(caseName);
			}
		}
		return Collections.unmodifiableList(caseNames);
	}

	private static List<String> parseList(String rawValue) {
		if (rawValue == null || rawValue.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<String> values = new ArrayList<>();
		Arrays.stream(rawValue.split("\\|", -1)).forEach(value -> {
			if (!value.isEmpty()) {
				values.add(value);
			}
		});
		return Collections.unmodifiableList(values);
	}

	private static String require(Properties properties, String caseName, String suffix) {
		String value = optional(properties, caseName, suffix);
		if (value == null) {
			throw new IllegalArgumentException(
					"Missing property " + propertyName(caseName, suffix) + " for manual gawk case " + caseName);
		}
		return value;
	}

	private static String optional(Properties properties, String caseName, String suffix) {
		String value = properties.getProperty(propertyName(caseName, suffix));
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String propertyName(String caseName, String suffix) {
		return caseName + "." + suffix;
	}

	private static final class ManualGawkCase implements GawkCompatibilityCase {
		private final String name;
		private final List<String> arguments;
		private final List<String> scriptFileNames;
		private final List<String> operands;
		private final String stdinFileName;
		private final String expectedFileName;

		ManualGawkCase(
				String name,
				List<String> arguments,
				List<String> scriptFileNames,
				List<String> operands,
				String stdinFileName,
				String expectedFileName) {
			this.name = name;
			this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
			this.scriptFileNames = Collections.unmodifiableList(new ArrayList<>(scriptFileNames));
			this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
			this.stdinFileName = stdinFileName;
			this.expectedFileName = expectedFileName;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public List<String> arguments() {
			return arguments;
		}

		@Override
		public List<String> scriptFileNames() {
			return scriptFileNames;
		}

		@Override
		public List<String> operands() {
			return operands;
		}

		@Override
		public String stdinFileName() {
			return stdinFileName;
		}

		@Override
		public String expectedFileName() {
			return expectedFileName;
		}

		@Override
		public boolean requiresExplicitSkip() {
			return false;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
