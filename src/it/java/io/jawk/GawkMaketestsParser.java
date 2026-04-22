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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GawkMaketestsParser {

	private static final String DUMMY_TARGET = "Gt-dummy";
	private static final Pattern TARGET_PATTERN = Pattern.compile("^([A-Za-z0-9_.+-]+):\\s*$");
	private static final Pattern FLAG_PATTERN = Pattern.compile("--[a-z-]+|-M");
	private static final Pattern LOCALE_PATTERN = Pattern.compile("GAWKLOCALE=([^;\\s]+)");
	private static final Set<String> DIRECT_CLI_FLAGS = new LinkedHashSet<>(Arrays.asList("--posix", "--sandbox"));
	private static final Set<String> NOOP_FLAGS = new LinkedHashSet<>(
			Arrays.asList("--non-decimal-data", "--re-interval"));
	private static final Set<String> UNSUPPORTED_FLAGS = new LinkedHashSet<>(
			Arrays.asList("--csv", "--debug", "--lint", "--lint-old", "--pretty-print", "--traditional", "-M"));

	private GawkMaketestsParser() {}

	static List<GawkCase> parse(Reader reader) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(reader);
		List<GawkCase> cases = new ArrayList<>();
		String currentTarget = null;
		List<String> currentBlock = new ArrayList<>();
		String line;
		while ((line = bufferedReader.readLine()) != null) {
			Matcher matcher = TARGET_PATTERN.matcher(line);
			if (matcher.matches()) {
				addCase(cases, currentTarget, currentBlock);
				currentTarget = matcher.group(1);
				currentBlock = new ArrayList<>();
			} else if (currentTarget != null) {
				currentBlock.add(line);
			}
		}
		addCase(cases, currentTarget, currentBlock);
		return Collections.unmodifiableList(cases);
	}

	private static void addCase(List<GawkCase> cases, String target, List<String> blockLines) {
		if (target == null || DUMMY_TARGET.equals(target)) {
			return;
		}
		String commandLine = findCommandLine(target, blockLines);
		String blockText = String.join("\n", blockLines);
		boolean shellScript = commandLine.contains("$@.sh");
		List<String> flags = parseFlags(commandLine);
		List<String> runnableFlags = new ArrayList<>();
		List<String> unsupportedFlags = new ArrayList<>();
		for (String flag : flags) {
			if (DIRECT_CLI_FLAGS.contains(flag)) {
				runnableFlags.add(flag);
			} else if (NOOP_FLAGS.contains(flag)) {
				continue;
			} else if (UNSUPPORTED_FLAGS.contains(flag)) {
				unsupportedFlags.add(flag);
			} else {
				unsupportedFlags.add(flag);
			}
		}
		String localeTag = parseLocaleTag(blockText);
		boolean readsStandardInput = commandLine.contains("< \"$(srcdir)\"/$@.in");
		boolean hasMpfrExpectedVariant = blockText.contains("$@-mpfr.ok");
		cases
				.add(
						new GawkCase(
								target,
								shellScript,
								flags,
								runnableFlags,
								unsupportedFlags,
								readsStandardInput,
								hasMpfrExpectedVariant,
								localeTag));
	}

	private static String findCommandLine(String target, List<String> blockLines) {
		for (String line : blockLines) {
			if (line.contains("$(AWK)") || line.contains("$@.sh")) {
				return line;
			}
		}
		throw new IllegalArgumentException("Unable to find AWK command line for Maketests target " + target);
	}

	private static List<String> parseFlags(String commandLine) {
		LinkedHashSet<String> flags = new LinkedHashSet<>();
		Matcher matcher = FLAG_PATTERN.matcher(commandLine);
		while (matcher.find()) {
			flags.add(matcher.group());
		}
		return Collections.unmodifiableList(new ArrayList<>(flags));
	}

	private static String parseLocaleTag(String blockText) {
		Matcher matcher = LOCALE_PATTERN.matcher(blockText);
		if (!matcher.find()) {
			return null;
		}
		String gawkLocale = matcher.group(1);
		if ("C".equals(gawkLocale)) {
			return null;
		}
		int modifierIndex = gawkLocale.indexOf('@');
		String localeWithoutModifier = modifierIndex >= 0 ? gawkLocale.substring(0, modifierIndex) : gawkLocale;
		String[] languageAndCountry = localeWithoutModifier.split("[_.]", 3);
		if (languageAndCountry.length < 2) {
			return localeWithoutModifier;
		}
		return languageAndCountry[0].toLowerCase(Locale.ROOT)
				+ "-"
				+ languageAndCountry[1].toUpperCase(Locale.ROOT);
	}

	static final class GawkCase {
		private final String name;
		private final boolean shellScript;
		private final List<String> flags;
		private final List<String> runnableFlags;
		private final List<String> unsupportedFlags;
		private final boolean readsStandardInput;
		private final boolean hasMpfrExpectedVariant;
		private final String localeTag;

		GawkCase(
				String name,
				boolean shellScript,
				List<String> flags,
				List<String> runnableFlags,
				List<String> unsupportedFlags,
				boolean readsStandardInput,
				boolean hasMpfrExpectedVariant,
				String localeTag) {
			this.name = name;
			this.shellScript = shellScript;
			this.flags = Collections.unmodifiableList(new ArrayList<>(flags));
			this.runnableFlags = Collections.unmodifiableList(new ArrayList<>(runnableFlags));
			this.unsupportedFlags = Collections.unmodifiableList(new ArrayList<>(unsupportedFlags));
			this.readsStandardInput = readsStandardInput;
			this.hasMpfrExpectedVariant = hasMpfrExpectedVariant;
			this.localeTag = localeTag;
		}

		public String name() {
			return name;
		}

		String scriptMode() {
			return shellScript ? "sh" : "awk";
		}

		String scriptFileName() {
			return name + "." + scriptMode();
		}

		List<String> flags() {
			return flags;
		}

		List<String> runnableFlags() {
			return runnableFlags;
		}

		List<String> unsupportedFlags() {
			return unsupportedFlags;
		}

		boolean readsStandardInput() {
			return readsStandardInput;
		}

		public String stdinFileName() {
			return readsStandardInput ? name + ".in" : null;
		}

		boolean hasMpfrExpectedVariant() {
			return hasMpfrExpectedVariant;
		}

		public String expectedFileName() {
			return name + ".ok";
		}

		String localeTag() {
			return localeTag;
		}

		public boolean requiresExplicitSkip() {
			return shellScript || !unsupportedFlags.isEmpty();
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
