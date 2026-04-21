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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration suite derived from vendored gawk metadata. The suite stages the
 * gawk snapshot into the Failsafe working directory, parses the portable
 * generated rules from {@code Maketests}, loads a curated manifest of simple
 * handwritten {@code Makefile.am} rules, and executes those cases through
 * {@link AwkTestSupport#cliTest(String)}.
 */
@RunWith(Parameterized.class)
public class GawkCompatibilityIT {

	private static final String GAWK_RESOURCE_PATH = "/gawk";
	private static final String MAKETESTS_FILE = "Maketests";
	private static final String MANUAL_CASES_FILE = "manual-cases.properties";
	private static final String SKIP_MANIFEST_FILE = "skips.properties";
	private static final String EXIT_CODE_PREFIX = "EXIT CODE: ";
	private static final String ACTUAL_OUTPUT_DIRECTORY = "gawk-actual";
	private static final String STAGED_DIRECTORY_PREFIX = "gawk-staged-";
	private static final String STAGED_PATH_PREFIX = "@path:";
	private static final int MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;
	private static final int DIFF_CONTEXT_RADIUS = 80;
	private static final boolean LOG_PROGRESS = Boolean.getBoolean("jawk.gawk.progress");

	private static SuiteState suiteState;

	/**
	 * Ensures that the vendored gawk resources are staged and parsed before the
	 * parameterised suite begins execution.
	 *
	 * @throws Exception when loading the suite metadata fails
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {
		loadSuiteState();
	}

	/**
	 * Removes the staged gawk snapshot after the parameterised suite completes.
	 *
	 * @throws Exception when cleaning the staged resources fails
	 */
	@AfterClass
	public static void afterAll() throws Exception {
		if (suiteState == null) {
			return;
		}
		deleteRecursively(suiteState.stagedDirectory);
		suiteState = null;
	}

	/**
	 * Returns every gawk compatibility case discovered from the vendored
	 * generated and handwritten metadata snapshots.
	 *
	 * @return parameter values for the suite
	 * @throws Exception when loading or validating the gawk metadata fails
	 */
	@Parameters(name = "GAWK {0}")
	public static Iterable<GawkCompatibilityCase> parameters() throws Exception {
		return loadSuiteState().cases;
	}

	/** Gawk compatibility case under test. */
	@Parameter
	public GawkCompatibilityCase gawkCase;

	/**
	 * Executes one gawk compatibility case unless the explicit skip manifest marks
	 * it unsupported by the in-process Jawk harness.
	 *
	 * @throws Exception when preparing or executing the case fails unexpectedly
	 */
	@Test
	public void test() throws Exception {
		SuiteState state = loadSuiteState();
		String skipReason = state.skipReasons.get(gawkCase.name());
		Assume.assumeTrue(skipReason, skipReason == null);
		if (LOG_PROGRESS) {
			System.out.println("GAWK " + gawkCase.name());
		}

		AwkTestSupport.CliTestBuilder builder = AwkTestSupport
				.cliTest("GAWK " + gawkCase.name())
				.emulateCliMain()
				.mergeStdoutAndStderr()
				.maxOutputBytes(MAX_CAPTURED_OUTPUT_BYTES);

		for (String argument : gawkCase.arguments()) {
			builder.argument(resolveStagedPathSpec(state.stagedDirectory, argument));
		}
		for (String scriptFileName : gawkCase.scriptFileNames()) {
			builder.argument("-f", state.stagedDirectory.resolve(scriptFileName).toString());
		}
		for (String operand : gawkCase.operands()) {
			builder.operand(resolveStagedPathSpec(state.stagedDirectory, operand));
		}
		if (gawkCase.stdinFileName() != null) {
			builder.stdin(Files.readAllBytes(state.stagedDirectory.resolve(gawkCase.stdinFileName())));
		}

		AwkTestSupport.TestResult result;
		try {
			result = builder.build().run();
		} catch (AwkTestSupport.OutputLimitExceededException ex) {
			fail(
					"Captured output for GAWK "
							+ gawkCase.name()
							+ " exceeded "
							+ ex.maxBytes()
							+ " bytes. Enable -Djawk.gawk.progress=true to log case execution.");
			return;
		}
		String actual = renderMaketestsOutput(result.output(), result.exitCode());
		String expected = normalizeNewlines(readUtf8(state.stagedDirectory.resolve(gawkCase.expectedFileName())));
		assertOutputMatches(state, expected, actual);
	}

	private static synchronized SuiteState loadSuiteState() throws Exception {
		if (suiteState != null) {
			return suiteState;
		}
		Path resourceDirectory = resolveResourceDirectory();
		Path stagedDirectory = stageResourceDirectory(resourceDirectory);
		Path actualOutputDirectory = Files.createDirectories(stagedDirectory.resolve(ACTUAL_OUTPUT_DIRECTORY));
		List<GawkCompatibilityCase> parsedCases = parseCases(
				resourceDirectory.resolve(MAKETESTS_FILE),
				resourceDirectory.resolve(MANUAL_CASES_FILE));
		Map<String, String> skipReasons = loadSkipReasons(resourceDirectory.resolve(SKIP_MANIFEST_FILE));
		validateCoverage(parsedCases, skipReasons);
		suiteState = new SuiteState(stagedDirectory, actualOutputDirectory, parsedCases, skipReasons);
		return suiteState;
	}

	private static Path resolveResourceDirectory() throws Exception {
		URL resourceUrl = GawkCompatibilityIT.class.getResource(GAWK_RESOURCE_PATH);
		if (resourceUrl == null) {
			throw new IOException("Couldn't find resource " + GAWK_RESOURCE_PATH);
		}
		Path resourceDirectory = Paths.get(resourceUrl.toURI());
		if (!Files.isDirectory(resourceDirectory)) {
			throw new IOException(GAWK_RESOURCE_PATH + " is not a directory");
		}
		return resourceDirectory;
	}

	private static Path stageResourceDirectory(Path sourceDirectory) throws IOException {
		Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
		Files.createDirectories(workingDirectory);
		Path stagedDirectory = Files.createTempDirectory(workingDirectory, STAGED_DIRECTORY_PREFIX);
		try (Stream<Path> paths = Files.walk(sourceDirectory)) {
			paths.forEach(path -> copyToWorkingDirectory(sourceDirectory, stagedDirectory, path));
		}
		createConfiguredMakefileAlias(stagedDirectory);
		return stagedDirectory;
	}

	private static void copyToWorkingDirectory(Path sourceDirectory, Path workingDirectory, Path sourcePath) {
		try {
			Path relativePath = sourceDirectory.relativize(sourcePath);
			if (relativePath.toString().isEmpty()) {
				return;
			}
			Path destination = workingDirectory.resolve(relativePath);
			if (Files.isDirectory(sourcePath)) {
				Files.createDirectories(destination);
			} else {
				Path parent = destination.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to stage gawk resource " + sourcePath, ex);
		}
	}

	private static List<GawkCompatibilityCase> parseCases(Path maketestsPath, Path manualCasesPath) throws IOException {
		List<GawkCompatibilityCase> cases = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(maketestsPath, StandardCharsets.UTF_8)) {
			cases.addAll(GawkMaketestsParser.parse(reader));
		}
		try (Reader reader = Files.newBufferedReader(manualCasesPath, StandardCharsets.UTF_8)) {
			cases.addAll(GawkManualCasesParser.parse(reader));
		}
		return Collections.unmodifiableList(cases);
	}

	private static Map<String, String> loadSkipReasons(Path manifestPath) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		Map<String, String> skipReasons = new LinkedHashMap<>();
		for (String name : properties.stringPropertyNames()) {
			skipReasons.put(name, properties.getProperty(name));
		}
		return Collections.unmodifiableMap(skipReasons);
	}

	private static void validateCoverage(
			List<GawkCompatibilityCase> parsedCases,
			Map<String, String> skipReasons) {
		TreeSet<String> parsedNames = new TreeSet<>();
		TreeSet<String> duplicateNames = new TreeSet<>();
		TreeSet<String> missingSkipEntries = new TreeSet<>();
		for (GawkCompatibilityCase parsedCase : parsedCases) {
			if (!parsedNames.add(parsedCase.name())) {
				duplicateNames.add(parsedCase.name());
			}
			if (parsedCase.requiresExplicitSkip() && !skipReasons.containsKey(parsedCase.name())) {
				missingSkipEntries.add(parsedCase.name());
			}
		}
		if (!duplicateNames.isEmpty()) {
			throw new IllegalStateException("Duplicate gawk case names discovered: " + String.join(", ", duplicateNames));
		}
		if (!missingSkipEntries.isEmpty()) {
			throw new IllegalStateException("Missing gawk skip manifest entries: " + String.join(", ", missingSkipEntries));
		}

		TreeSet<String> staleSkipEntries = new TreeSet<>(skipReasons.keySet());
		staleSkipEntries.removeAll(parsedNames);
		if (!staleSkipEntries.isEmpty()) {
			throw new IllegalStateException(
					"Skip manifest contains unknown gawk cases: " + String.join(", ", staleSkipEntries));
		}
	}

	private static String renderMaketestsOutput(String output, int exitCode) {
		String normalized = normalizeNewlines(output);
		if (exitCode == 0) {
			return normalized;
		}
		return normalized + EXIT_CODE_PREFIX + exitCode + "\n";
	}

	private static String resolveStagedPathSpec(Path stagedDirectory, String value) {
		if (value != null && value.startsWith(STAGED_PATH_PREFIX)) {
			return stagedDirectory.resolve(value.substring(STAGED_PATH_PREFIX.length())).normalize().toString();
		}
		return value;
	}

	private static void createConfiguredMakefileAlias(Path stagedDirectory) throws IOException {
		Path makefile = stagedDirectory.resolve("Makefile");
		Path makefileIn = stagedDirectory.resolve("Makefile.in");
		if (Files.notExists(makefile) && Files.exists(makefileIn)) {
			Files.copy(makefileIn, makefile);
		}
	}

	private static void deleteRecursively(Path directory) throws IOException {
		if (directory == null || Files.notExists(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ex) {
					throw new IllegalStateException("Failed to delete staged gawk resource " + path, ex);
				}
			});
		} catch (IllegalStateException ex) {
			if (ex.getCause() instanceof IOException) {
				throw (IOException) ex.getCause();
			}
			throw ex;
		}
	}

	private void assertOutputMatches(SuiteState state, String expected, String actual) throws IOException {
		if (expected.equals(actual)) {
			return;
		}
		Path actualOutputPath = state.actualOutputDirectory.resolve(gawkCase.name() + ".actual");
		Files.write(actualOutputPath, actual.getBytes(StandardCharsets.UTF_8));
		int mismatchIndex = firstMismatchIndex(expected, actual);
		throw new AssertionError(buildMismatchMessage(actualOutputPath, expected, actual, mismatchIndex));
	}

	private String buildMismatchMessage(Path actualOutputPath, String expected, String actual, int mismatchIndex) {
		StringBuilder message = new StringBuilder();
		message.append("Unexpected output for GAWK ").append(gawkCase.name());
		if (mismatchIndex >= 0) {
			message.append(" at char ").append(mismatchIndex);
		}
		message
				.append(" (expected length ")
				.append(expected.length())
				.append(", actual length ")
				.append(actual.length())
				.append(").");
		if (mismatchIndex >= 0) {
			message
					.append(" Expected snippet: ")
					.append(snippetAround(expected, mismatchIndex))
					.append(". Actual snippet: ")
					.append(snippetAround(actual, mismatchIndex))
					.append(".");
		}
		message
				.append(" Expected file: ")
				.append(gawkCase.expectedFileName())
				.append(". Actual output written to ")
				.append(actualOutputPath);
		return message.toString();
	}

	private static int firstMismatchIndex(String expected, String actual) {
		int commonLength = Math.min(expected.length(), actual.length());
		for (int index = 0; index < commonLength; index++) {
			if (expected.charAt(index) != actual.charAt(index)) {
				return index;
			}
		}
		if (expected.length() != actual.length()) {
			return commonLength;
		}
		return -1;
	}

	private static String snippetAround(String text, int index) {
		if (text.isEmpty()) {
			return "\"\"";
		}
		int start = Math.max(0, index - DIFF_CONTEXT_RADIUS);
		int end = Math.min(text.length(), index + DIFF_CONTEXT_RADIUS);
		String prefix = start > 0 ? "..." : "";
		String suffix = end < text.length() ? "..." : "";
		return "\""
				+ prefix
				+ sanitizeSnippet(text.substring(start, end))
				+ suffix
				+ "\"";
	}

	private static String sanitizeSnippet(String text) {
		return text
				.replace("\\", "\\\\")
				.replace("\r", "\\r")
				.replace("\n", "\\n")
				.replace("\t", "\\t");
	}

	private static String readUtf8(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static String normalizeNewlines(String text) {
		return text.replace("\r\n", "\n").replace("\r", "\n");
	}

	private static final class SuiteState {
		private final Path stagedDirectory;
		private final Path actualOutputDirectory;
		private final List<GawkCompatibilityCase> cases;
		private final Map<String, String> skipReasons;

		SuiteState(
				Path stagedDirectory,
				Path actualOutputDirectory,
				List<GawkCompatibilityCase> cases,
				Map<String, String> skipReasons) {
			this.stagedDirectory = stagedDirectory;
			this.actualOutputDirectory = actualOutputDirectory;
			this.cases = Collections.unmodifiableList(new ArrayList<>(cases));
			this.skipReasons = skipReasons;
		}
	}
}
