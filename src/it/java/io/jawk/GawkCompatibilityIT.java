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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
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
 * generated rules from {@code Maketests} and executes those cases through
 * {@link AwkTestSupport#cliTest(String)}.
 */
@RunWith(Parameterized.class)
public class GawkCompatibilityIT {

	private static final String GAWK_RESOURCE_PATH = "/gawk";
	private static final String MAKETESTS_FILE = "Maketests";
	private static final String SKIP_MANIFEST_FILE = "skips.properties";
	private static final String EXIT_CODE_PREFIX = "EXIT CODE: ";
	private static final String STAGED_DIRECTORY_PREFIX = "gawk-staged-";
	private static final int MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;
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
		AwkTestSupport.deleteRecursively(suiteState.stagedDirectory);
		suiteState = null;
	}

	/**
	 * Returns every gawk compatibility case discovered from the vendored
	 * generated metadata snapshot.
	 *
	 * @return parameter values for the suite
	 * @throws Exception when loading or validating the gawk metadata fails
	 */
	@Parameters(name = "GAWK {0}")
	public static Iterable<GawkMaketestsParser.GawkCase> parameters() throws Exception {
		return loadSuiteState().cases;
	}

	/** Gawk compatibility case under test. */
	@Parameter
	public GawkMaketestsParser.GawkCase gawkCase;

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

		if (gawkCase.localeTag() != null) {
			builder.argument("--locale", gawkCase.localeTag());
		}
		for (String flag : gawkCase.runnableFlags()) {
			builder.argument(flag);
		}
		builder.argument("-f", state.stagedDirectory.resolve(gawkCase.scriptFileName()).toString());
		if (gawkCase.stdinFileName() != null) {
			builder.stdin(Files.readAllBytes(state.stagedDirectory.resolve(gawkCase.stdinFileName())));
		}
		AwkTestSupport.ExpectedCliTranscript expected = AwkTestSupport
				.readExpectedCliTranscript(state.stagedDirectory.resolve(gawkCase.expectedFileName()), EXIT_CODE_PREFIX);
		builder.postProcessWith(AwkTestSupport::normalizeNewlines).expect(expected.output());
		if (expected.exitCode() != null) {
			builder.expectExit(expected.exitCode().intValue());
		}

		try {
			builder.build().runAndAssert();
		} catch (AwkTestSupport.OutputLimitExceededException ex) {
			fail(
					"Captured output for GAWK "
							+ gawkCase.name()
							+ " exceeded "
							+ ex.maxBytes()
							+ " bytes. Enable -Djawk.gawk.progress=true to log case execution.");
		}
	}

	private static synchronized SuiteState loadSuiteState() throws Exception {
		if (suiteState != null) {
			return suiteState;
		}
		Path resourceDirectory = resolveResourceDirectory();
		List<GawkMaketestsParser.GawkCase> parsedCases = parseCases(resourceDirectory.resolve(MAKETESTS_FILE));
		Map<String, String> skipReasons = loadSkipReasons(resourceDirectory.resolve(SKIP_MANIFEST_FILE));
		validateCoverage(parsedCases, skipReasons);
		Path stagedDirectory = null;
		try {
			stagedDirectory = AwkTestSupport.stageDirectory(resourceDirectory, STAGED_DIRECTORY_PREFIX);
			suiteState = new SuiteState(stagedDirectory, parsedCases, skipReasons);
			return suiteState;
		} catch (Exception ex) {
			AwkTestSupport.deleteRecursively(stagedDirectory);
			throw ex;
		}
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

	private static List<GawkMaketestsParser.GawkCase> parseCases(Path maketestsPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(maketestsPath, StandardCharsets.UTF_8)) {
			return new ArrayList<>(GawkMaketestsParser.parse(reader));
		}
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
			List<GawkMaketestsParser.GawkCase> parsedCases,
			Map<String, String> skipReasons) {
		TreeSet<String> parsedNames = new TreeSet<>();
		TreeSet<String> duplicateNames = new TreeSet<>();
		TreeSet<String> missingSkipEntries = new TreeSet<>();
		for (GawkMaketestsParser.GawkCase parsedCase : parsedCases) {
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

	private static final class SuiteState {
		private final Path stagedDirectory;
		private final List<GawkMaketestsParser.GawkCase> cases;
		private final Map<String, String> skipReasons;

		SuiteState(
				Path stagedDirectory,
				List<GawkMaketestsParser.GawkCase> cases,
				Map<String, String> skipReasons) {
			this.stagedDirectory = stagedDirectory;
			this.cases = Collections.unmodifiableList(new ArrayList<>(cases));
			this.skipReasons = skipReasons;
		}
	}
}
