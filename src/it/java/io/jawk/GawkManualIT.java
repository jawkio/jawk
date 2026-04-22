package io.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ᛫᛫᛫᛫᛫᛫
 * Copyright (C) 2006 - 2026 MetricsHub
 * ᛫᛫᛫᛫᛫᛫
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 */

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Curated handwritten gawk compatibility cases expressed directly with
 * {@link AwkTestSupport}. These cover simple {@code Makefile.am} rules that do
 * not justify another metadata parser.
 */
public class GawkManualIT {

	private static final String GAWK_RESOURCE_PATH = "/gawk";
	private static final String EXIT_CODE_PREFIX = "EXIT CODE: ";
	private static final String ACTUAL_OUTPUT_DIRECTORY = "gawk-manual-actual";
	private static final String STAGED_DIRECTORY_PREFIX = "gawk-manual-";
	private static final int MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;
	private static final int DIFF_CONTEXT_RADIUS = 80;
	private static final boolean LOG_PROGRESS = Boolean.getBoolean("jawk.gawk.progress");

	private static SuiteState suiteState;

	@BeforeClass
	public static void beforeAll() throws Exception {
		loadSuiteState();
	}

	@AfterClass
	public static void afterAll() throws Exception {
		if (suiteState == null) {
			return;
		}
		deleteRecursively(suiteState.stagedDirectory);
		suiteState = null;
	}

	@Test
	public void argcasfile() throws Exception {
		assertManualCase(
				"argcasfile",
				(builder, state) -> {
					builder.argument("-f", state.stagedDirectory.resolve("argcasfile.awk").toString());
					builder.operand("ARGC=1", " /no/such/file");
					builder.stdin(Files.readAllBytes(state.stagedDirectory.resolve("argcasfile.in")));
				});
	}

	@Test
	public void beginfile1() throws Exception {
		assertManualCase(
				"beginfile1",
				(builder, state) -> {
					builder.argument("-f", state.stagedDirectory.resolve("beginfile1.awk").toString());
					builder
							.operand(
									state.stagedDirectory.resolve("beginfile1.awk").toString(),
									state.stagedDirectory.toString(),
									state.stagedDirectory.resolve("no").resolve("such").resolve("file").toString(),
									state.stagedDirectory.resolve("Makefile").toString());
				});
	}

	@Test
	public void eofsrc1() throws Exception {
		assertManualCase(
				"eofsrc1",
				(builder, state) -> builder
						.argument(
								"-f",
								state.stagedDirectory.resolve("eofsrc1a.awk").toString(),
								"-f",
								state.stagedDirectory.resolve("eofsrc1b.awk").toString()));
	}

	@Test
	public void longwrds() throws Exception {
		assertManualCase(
				"longwrds",
				(builder, state) -> {
					builder.argument("-v", "SORT=sort", "-f", state.stagedDirectory.resolve("longwrds.awk").toString());
					builder.stdin(Files.readAllBytes(state.stagedDirectory.resolve("longwrds.in")));
				});
	}

	@Test
	public void nsawk1a() throws Exception {
		assertManualCase(
				"nsawk1a",
				(builder, state) -> builder.argument("-f", state.stagedDirectory.resolve("nsawk1.awk").toString()));
	}

	@Test
	public void nsawk1b() throws Exception {
		assertManualCase(
				"nsawk1b",
				(builder, state) -> builder
						.argument(
								"-v",
								"I=fine",
								"-f",
								state.stagedDirectory.resolve("nsawk1.awk").toString()));
	}

	@Test
	public void nsawk1c() throws Exception {
		assertManualCase(
				"nsawk1c",
				(builder, state) -> builder
						.argument(
								"-v",
								"awk::I=fine",
								"-f",
								state.stagedDirectory.resolve("nsawk1.awk").toString()));
	}

	@Test
	public void nsawk2a() throws Exception {
		assertManualCase(
				"nsawk2a",
				(builder, state) -> builder
						.argument(
								"-v",
								"I=fine",
								"-f",
								state.stagedDirectory.resolve("nsawk2.awk").toString()));
	}

	@Test
	public void nsawk2b() throws Exception {
		assertManualCase(
				"nsawk2b",
				(builder, state) -> builder
						.argument(
								"-v",
								"awk::I=fine",
								"-f",
								state.stagedDirectory.resolve("nsawk2.awk").toString()));
	}

	@Test
	public void nsidentifier() throws Exception {
		assertManualCase(
				"nsidentifier",
				(builder, state) -> builder
						.argument(
								"-v",
								"SORT=sort",
								"-f",
								state.stagedDirectory.resolve("nsidentifier.awk").toString()));
	}

	@Test
	public void readfile2() throws Exception {
		assertManualCase(
				"readfile2",
				(builder, state) -> {
					builder.argument("-f", state.stagedDirectory.resolve("readfile2.awk").toString());
					builder
							.operand(
									state.stagedDirectory.resolve("readfile2.awk").toString(),
									state.stagedDirectory.resolve("readdir.awk").toString());
				});
	}

	@Test
	public void spacere() throws Exception {
		assertManualCase(
				"spacere",
				(builder, state) -> builder.argument("-f", state.stagedDirectory.resolve("spacere.awk").toString()));
	}

	@Test
	public void symtab6() throws Exception {
		assertManualCase(
				"symtab6",
				(builder, state) -> builder.argument("-f", state.stagedDirectory.resolve("symtab6.awk").toString()));
	}

	@Test
	public void symtab9() throws Exception {
		assertManualCase(
				"symtab9",
				(builder, state) -> builder.argument("-f", state.stagedDirectory.resolve("symtab9.awk").toString()));
	}

	private void assertManualCase(String caseName, ManualCaseConfigurer configurer) throws Exception {
		SuiteState state = loadSuiteState();
		if (LOG_PROGRESS) {
			System.out.println("GAWK manual " + caseName);
		}
		AwkTestSupport.CliTestBuilder builder = AwkTestSupport
				.cliTest("GAWK " + caseName)
				.emulateCliMain()
				.mergeStdoutAndStderr()
				.maxOutputBytes(MAX_CAPTURED_OUTPUT_BYTES);
		configurer.configure(builder, state);
		AwkTestSupport.TestResult result;
		try {
			result = builder.build().run();
		} catch (AwkTestSupport.OutputLimitExceededException ex) {
			fail(
					"Captured output for GAWK "
							+ caseName
							+ " exceeded "
							+ ex.maxBytes()
							+ " bytes. Enable -Djawk.gawk.progress=true to log case execution.");
			return;
		}
		String actual = renderCliOutput(result.output(), result.exitCode());
		String expected = normalizeNewlines(readUtf8(state.stagedDirectory.resolve(caseName + ".ok")));
		assertOutputMatches(state, caseName, expected, actual);
	}

	private static synchronized SuiteState loadSuiteState() throws Exception {
		if (suiteState != null) {
			return suiteState;
		}
		Path resourceDirectory = resolveResourceDirectory();
		Path stagedDirectory = stageResourceDirectory(resourceDirectory);
		Path actualOutputDirectory = resolveActualOutputDirectory();
		suiteState = new SuiteState(stagedDirectory, actualOutputDirectory);
		return suiteState;
	}

	private static Path resolveResourceDirectory() throws Exception {
		URL resourceUrl = GawkManualIT.class.getResource(GAWK_RESOURCE_PATH);
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

	private void assertOutputMatches(SuiteState state, String caseName, String expected, String actual)
			throws IOException {
		if (expected.equals(actual)) {
			return;
		}
		Path actualOutputPath = state.actualOutputDirectory.resolve(caseName + ".actual");
		Files.write(actualOutputPath, actual.getBytes(StandardCharsets.UTF_8));
		int mismatchIndex = firstMismatchIndex(expected, actual);
		throw new AssertionError(buildMismatchMessage(caseName, actualOutputPath, expected, actual, mismatchIndex));
	}

	private String buildMismatchMessage(
			String caseName,
			Path actualOutputPath,
			String expected,
			String actual,
			int mismatchIndex) {
		StringBuilder message = new StringBuilder();
		message.append("Unexpected output for GAWK ").append(caseName);
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
		message.append(" Actual output written to ").append(actualOutputPath);
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

	private static String renderCliOutput(String output, int exitCode) {
		String normalized = normalizeNewlines(output);
		if (exitCode == 0) {
			return normalized;
		}
		return normalized + EXIT_CODE_PREFIX + exitCode + "\n";
	}

	private static Path resolveActualOutputDirectory() throws IOException {
		Path actualOutputDirectory = resolveBuildDirectory().resolve("failsafe-reports").resolve(ACTUAL_OUTPUT_DIRECTORY);
		return Files.createDirectories(actualOutputDirectory);
	}

	private static Path resolveBuildDirectory() throws IOException {
		try {
			Path classesDirectory = Paths
					.get(GawkManualIT.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			Path buildDirectory = classesDirectory.getParent();
			if (buildDirectory == null) {
				throw new IOException("Couldn't determine build directory from " + classesDirectory);
			}
			return buildDirectory;
		} catch (Exception ex) {
			throw new IOException("Couldn't resolve Jawk build directory", ex);
		}
	}

	private static String readUtf8(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static String normalizeNewlines(String text) {
		return text.replace("\r\n", "\n").replace("\r", "\n");
	}

	private interface ManualCaseConfigurer {
		void configure(AwkTestSupport.CliTestBuilder builder, SuiteState state) throws Exception;
	}

	private static final class SuiteState {
		private final Path stagedDirectory;
		private final Path actualOutputDirectory;

		SuiteState(Path stagedDirectory, Path actualOutputDirectory) {
			this.stagedDirectory = stagedDirectory;
			this.actualOutputDirectory = actualOutputDirectory;
		}
	}
}
