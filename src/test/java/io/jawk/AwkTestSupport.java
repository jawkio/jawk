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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.jawk.ext.JawkExtension;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.InputSource;

/**
 * Reusable helpers for building and executing Jawk tests. This consolidates the
 * logic that was historically duplicated across different test suites, so that
 * all tests share the same approach for managing temporary files, providing
 * input, and capturing the results of either {@link Awk} or {@link Cli}
 * executions. The class exposes fluent builders ({@link #awkTest(String)} and
 * {@link #cliTest(String)}) that let tests describe their scripts, inputs, and
 * expectations declaratively before executing or asserting the results.
 */
public final class AwkTestSupport {

	private static final boolean IS_POSIX = !System
			.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT)
			.contains("win");

	private static final Path SHARED_TEMP_DIR;

	static {
		try {
			SHARED_TEMP_DIR = Files.createTempDirectory("jawk-shared");
			SHARED_TEMP_DIR.toFile().deleteOnExit();
		} catch (IOException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	private AwkTestSupport() {}

	/**
	 * Creates a builder for a unit test that exercises the {@link Awk} API directly.
	 * The returned builder can be configured with scripts, inputs, operands, and
	 * expectations before executing the test.
	 *
	 * @param description human readable description used in assertion messages
	 * @return a builder configured with the provided description
	 */
	public static AwkTestBuilder awkTest(String description) {
		return new AwkTestBuilder(description);
	}

	/**
	 * Creates a builder for a unit test that exercises the {@link Cli} entry
	 * point. The builder records all inputs and expectations before invoking the
	 * CLI.
	 *
	 * @param description human readable description used in assertion messages
	 * @return a builder configured with the provided description
	 */
	public static CliTestBuilder cliTest(String description) {
		return new CliTestBuilder(description);
	}

	/**
	 * Returns the shared temporary directory that builders use when a test opts
	 * into creating temporary files. Tests can reference this value directly when
	 * they need deterministic paths outside the per-test sandbox.
	 *
	 * @return the lazily created shared temporary directory
	 */
	public static Path sharedTempDirectory() {
		return SHARED_TEMP_DIR;
	}

	static Path stageDirectory(Path sourceDirectory, String prefix) throws IOException {
		Path stagedDirectory = Files.createTempDirectory(prefix);
		try {
			copyDirectoryRecursively(sourceDirectory, stagedDirectory);
			return stagedDirectory;
		} catch (IOException | RuntimeException ex) {
			deleteRecursively(stagedDirectory);
			throw ex;
		}
	}

	static void copyDirectoryRecursively(Path sourceDirectory, Path destinationDirectory) throws IOException {
		try (Stream<Path> paths = Files.walk(sourceDirectory)) {
			paths.forEach(path -> copyToDirectory(sourceDirectory, destinationDirectory, path));
		}
	}

	static String readUtf8(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	static String readUtf8Normalized(Path path) throws IOException {
		return normalizeNewlines(readUtf8(path));
	}

	static String normalizeNewlines(String text) {
		return text.replace("\r\n", "\n").replace("\r", "\n");
	}

	static ExpectedCliTranscript readExpectedCliTranscript(Path path, String exitCodePrefix) throws IOException {
		String transcript = readUtf8Normalized(path);
		int trailerLineStart = transcript.length();
		while (trailerLineStart > 0 && transcript.charAt(trailerLineStart - 1) == '\n') {
			trailerLineStart--;
		}
		while (trailerLineStart > 0 && transcript.charAt(trailerLineStart - 1) != '\n') {
			trailerLineStart--;
		}
		String lastLine = transcript.substring(trailerLineStart).trim();
		if (!lastLine.startsWith(exitCodePrefix)) {
			return new ExpectedCliTranscript(transcript, null);
		}
		Integer exitCode = Integer.valueOf(lastLine.substring(exitCodePrefix.length()).trim());
		return new ExpectedCliTranscript(transcript.substring(0, trailerLineStart), exitCode);
	}

	static final class OutputLimitExceededException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final int maxBytes;

		OutputLimitExceededException(int maxBytes) {
			super("Captured output exceeded " + maxBytes + " bytes");
			this.maxBytes = maxBytes;
		}

		int maxBytes() {
			return maxBytes;
		}
	}

	static final class ExpectedCliTranscript {
		private final String output;
		private final Integer exitCode;

		ExpectedCliTranscript(String output, Integer exitCode) {
			this.output = output;
			this.exitCode = exitCode;
		}

		String output() {
			return output;
		}

		Integer exitCode() {
			return exitCode;
		}
	}

	/**
	 * Represents a fully configured test case produced by one of the builders.
	 * Implementations know how to prepare the execution environment, run the
	 * script, and assert expectations.
	 */
	public interface ConfiguredTest {
		/**
		 * Provides a human readable description that is included in assertion
		 * messages.
		 *
		 * @return the description defined by the builder
		 */
		String description();

		/**
		 * Skips the test when the current environment cannot satisfy the test
		 * prerequisites (for instance POSIX specific behaviour).
		 */
		void assumeSupported();

		/**
		 * Executes the configured test case and returns the captured result
		 * without asserting it.
		 *
		 * @return the captured output, exit code, and expected values
		 * @throws Exception when executing the test fails unexpectedly
		 */
		TestResult run() throws Exception;

		/**
		 * Executes the configured test case and immediately asserts that the
		 * observed result matches the configured expectations.
		 *
		 * @throws Exception when executing the test fails unexpectedly
		 */
		default void runAndAssert() throws Exception {
			assumeSupported();
			run().assertExpected();
		}
	}

	/**
	 * Captures the outcome of executing a configured test including the raw
	 * output, exit code, and any expectations configured on the builder. Instances
	 * can assert the recorded values against expectations.
	 */
	public static final class TestResult {
		private final String description;
		private final String output;
		private final int exitCode;
		private final String expectedOutput;
		private final List<String> expectedLines;
		private final Integer expectedExitCode;
		private final Class<? extends Throwable> expectedException;
		private final Throwable thrownException;

		TestResult(
				String description,
				String output,
				int exitCode,
				String expectedOutput,
				List<String> expectedLines,
				Integer expectedExitCode,
				Class<? extends Throwable> expectedException,
				Throwable thrownException) {
			this.description = description;
			this.output = output;
			this.exitCode = exitCode;
			this.expectedOutput = expectedOutput;
			this.expectedLines = expectedLines != null ? Collections.unmodifiableList(new ArrayList<>(expectedLines)) : null;
			this.expectedExitCode = expectedExitCode;
			this.expectedException = expectedException;
			this.thrownException = thrownException;
		}

		/**
		 * Returns the description that was supplied when the test was defined.
		 *
		 * @return the human readable description
		 */
		public String description() {
			return description;
		}

		/**
		 * Returns the captured stdout of the test execution.
		 *
		 * @return the captured output as a UTF-8 string
		 */
		public String output() {
			return output;
		}

		/**
		 * Returns the exit code reported by the execution.
		 *
		 * @return the exit code observed at runtime
		 */
		public int exitCode() {
			return exitCode;
		}

		/**
		 * Returns the captured output split into individual lines. Trailing
		 * newline characters are ignored and Windows style line endings are
		 * normalised.
		 *
		 * @return the output split into lines, or an empty array when no output
		 *         was produced
		 */
		public String[] lines() {
			List<String> split = readOutputLines(output);
			return split.toArray(new String[0]);
		}

		/**
		 * Verifies that the captured output, exit code, or thrown exception match
		 * the expectations defined in the builder.
		 */
		public void assertExpected() {
			if (expectedException != null) {
				if (thrownException == null) {
					throw new AssertionError(
							"Expected exception "
									+ expectedException.getName()
									+ " for "
									+ description
									+ " but execution completed successfully");
				}
				if (!expectedException.isInstance(thrownException)) {
					throw new AssertionError(
							"Expected exception "
									+ expectedException.getName()
									+ " for "
									+ description
									+ " but got "
									+ thrownException.getClass().getName());
				}
				return;
			}
			if (expectedLines != null) {
				List<String> actualLines = readOutputLines(output);
				assertArrayEquals(
						"Unexpected output for " + description,
						expectedLines.toArray(new String[0]),
						actualLines.toArray(new String[0]));
			} else if (expectedOutput != null) {
				assertEquals("Unexpected output for " + description, expectedOutput, output);
			}
			if (expectedExitCode != null) {
				assertEquals("Unexpected exit code for " + description, expectedExitCode.intValue(), exitCode);
			} else {
				assertEquals("Unexpected exit code for " + description, 0, exitCode);
			}
		}

		private static List<String> readOutputLines(String output) {
			if (output.isEmpty()) {
				return Collections.emptyList();
			}
			List<String> lines = new ArrayList<>();
			try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			} catch (IOException ex) {
				throw new UncheckedIOException("Failed to split captured output", ex);
			}
			return Collections.unmodifiableList(lines);
		}

		public String expectedOutput() {
			return expectedOutput;
		}

		/**
		 * Returns the expected exit code configured on the builder.
		 *
		 * @return the expected exit code or {@code null} when the default of zero
		 *         should be asserted
		 */
		public Integer expectedExitCode() {
			return expectedExitCode;
		}

		/**
		 * Returns the exception that was thrown while executing the test.
		 *
		 * @return the thrown exception or {@code null} when execution completed
		 *         normally
		 */
		public Throwable thrownException() {
			return thrownException;
		}

		/**
		 * Returns the exception type that was expected during execution.
		 *
		 * @return the expected exception type or {@code null} when no exception
		 *         was expected
		 */
		public Class<? extends Throwable> expectedException() {
			return expectedException;
		}
	}

	/**
	 * Fluent builder for tests that execute {@link Awk} directly. The builder
	 * exposes helpers to preassign variables, provide extensions, and otherwise
	 * mirror the runtime configuration used when embedding Jawk.
	 */
	public static final class AwkTestBuilder extends BaseTestBuilder<AwkTestBuilder> {
		private final Map<String, Object> preAssignments = new LinkedHashMap<>();
		private Awk customAwk;
		private final List<JawkExtension> extensions = new ArrayList<>();
		private InputSource inputSource;

		private AwkTestBuilder(String description) {
			super(description);
		}

		/**
		 * Registers a value to pre-assign to a variable before the script is
		 * executed.
		 *
		 * @param name the variable name
		 * @param value the value to expose to the script
		 * @return this builder for method chaining
		 */
		public AwkTestBuilder preassign(String name, Object value) {
			preAssignments.put(name, value);
			return this;
		}

		/**
		 * Supplies an {@link Awk} instance to use when invoking the script.
		 *
		 * @param awkEngine the engine to execute the script with
		 * @return this builder for method chaining
		 * @throws IllegalArgumentException when {@code awkEngine} is {@code null}
		 */
		public AwkTestBuilder withAwk(Awk awkEngine) {
			if (awkEngine == null) {
				throw new IllegalArgumentException("Awk instance must not be null");
			}
			this.customAwk = awkEngine;
			return this;
		}

		/**
		 * Adds extensions that will be loaded when creating the {@link Awk}
		 * instance used by this test.
		 *
		 * @param extensionsParam the extensions to enable, ignored when
		 *        {@code null}
		 * @return this builder for method chaining
		 */
		public AwkTestBuilder withExtensions(JawkExtension... extensionsParam) {
			if (extensionsParam != null) {
				extensions.addAll(Arrays.asList(extensionsParam));
			}
			return this;
		}

		/**
		 * Adds extensions that will be loaded when creating the {@link Awk}
		 * instance used by this test.
		 *
		 * @param extensionsParam the extensions to enable, ignored when
		 *        {@code null}
		 * @return this builder for method chaining
		 */
		public AwkTestBuilder withExtensions(Collection<? extends JawkExtension> extensionsParam) {
			if (extensionsParam != null) {
				extensions.addAll(extensionsParam);
			}
			return this;
		}

		/**
		 * Configures a structured input source consumed by the runtime instead of
		 * stdin.
		 *
		 * @param inputSourceParam input source to consume
		 * @return this builder for method chaining
		 * @throws IllegalArgumentException when {@code inputSourceParam} is
		 *         {@code null}
		 */
		public AwkTestBuilder withInputSource(InputSource inputSourceParam) {
			if (inputSourceParam == null) {
				throw new IllegalArgumentException("InputSource must not be null");
			}
			this.inputSource = inputSourceParam;
			return this;
		}

		@Override
		protected AwkTestCase buildTestCase(
				TestLayout layout,
				Map<String, String> files,
				List<String> operands,
				List<String> placeholders) {
			if (useTempDir && !preAssignments.containsKey("TEMPDIR")) {
				preAssignments.put("TEMPDIR", SHARED_TEMP_DIR.toString());
			}
			return new AwkTestCase(
					layout,
					files,
					operands,
					placeholders,
					requiresPosix,
					preAssignments,
					customAwk,
					extensions,
					inputSource);
		}
	}

	/**
	 * Fluent builder for tests that exercise the {@link Cli} entry point. The
	 * builder takes care of wiring command-line arguments, assignments, and
	 * expectations before invoking the CLI.
	 */
	public static final class CliTestBuilder extends BaseTestBuilder<CliTestBuilder> {
		private final List<String> argumentSpecs = new ArrayList<>();
		private final Map<String, Object> assignments = new LinkedHashMap<>();
		private boolean emulateCliMain;
		private boolean mergeStdoutAndStderr;
		private Integer maxOutputBytes;
		private byte[] stdinBytes;

		private CliTestBuilder(String description) {
			super(description);
		}

		/**
		 * Adds raw command-line arguments to supply to the CLI when the test is
		 * executed. Path placeholders are resolved at runtime.
		 *
		 * @param args the arguments to add
		 * @return this builder for method chaining
		 */
		public CliTestBuilder argument(String... args) {
			argumentSpecs.addAll(Arrays.asList(args));
			return this;
		}

		/**
		 * Preassigns a variable using {@code -v} style CLI options before
		 * executing the script.
		 *
		 * @param name the variable name
		 * @param value the value to expose to the script
		 * @return this builder for method chaining
		 */
		public CliTestBuilder preassign(String name, Object value) {
			assignments.put(name, value);
			return this;
		}

		/**
		 * Provides text data that will be delivered on standard input when the
		 * script runs. Calling this method clears any previously configured raw
		 * byte stdin for the same builder.
		 *
		 * @param stdin the content to stream into standard input
		 * @return this builder for method chaining
		 */
		@Override
		public CliTestBuilder stdin(String stdin) {
			stdinBytes = null;
			return super.stdin(stdin);
		}

		/**
		 * Provides raw bytes that will be delivered on standard input when the
		 * script runs. This preserves fixture bytes exactly as supplied, which is
		 * useful for compatibility cases whose redirected input is not valid
		 * UTF-8. Calling this method clears any previously configured text stdin
		 * for the same builder.
		 *
		 * @param stdinParam raw bytes to stream into standard input
		 * @return this builder for method chaining
		 * @throws IllegalArgumentException when {@code stdinParam} is {@code null}
		 */
		public CliTestBuilder stdin(byte[] stdinParam) {
			if (stdinParam == null) {
				throw new IllegalArgumentException("stdinParam must not be null");
			}
			stdin = null;
			stdinBytes = Arrays.copyOf(stdinParam, stdinParam.length);
			return this;
		}

		/**
		 * Captures CLI standard output and standard error through the same
		 * stream. This mirrors shell invocations that redirect {@code 2>&1},
		 * allowing tests to compare diagnostics and regular output as a single
		 * combined result.
		 *
		 * @return this builder for method chaining
		 */
		public CliTestBuilder mergeStdoutAndStderr() {
			mergeStdoutAndStderr = true;
			return this;
		}

		/**
		 * Executes the CLI through the same exception handling flow used by
		 * {@link Cli#main(String[])} so that diagnostics are rendered to the error
		 * stream and failures become exit codes instead of propagating as Java
		 * exceptions.
		 *
		 * @return this builder for method chaining
		 */
		public CliTestBuilder emulateCliMain() {
			emulateCliMain = true;
			return this;
		}

		/**
		 * Caps the combined captured CLI output. This prevents compatibility
		 * suites from exhausting heap space when a script emits unbounded output.
		 *
		 * @param maxBytes maximum number of UTF-8 bytes to capture
		 * @return this builder for method chaining
		 * @throws IllegalArgumentException when {@code maxBytes} is not positive
		 */
		public CliTestBuilder maxOutputBytes(int maxBytes) {
			if (maxBytes <= 0) {
				throw new IllegalArgumentException("maxBytes must be positive");
			}
			maxOutputBytes = maxBytes;
			return this;
		}

		@Override
		protected CliTestCase buildTestCase(
				TestLayout layout,
				Map<String, String> files,
				List<String> operands,
				List<String> placeholders) {
			if (useTempDir && !assignments.containsKey("TEMPDIR")) {
				assignments.put("TEMPDIR", SHARED_TEMP_DIR.toString());
			}
			return new CliTestCase(
					layout,
					files,
					operands,
					placeholders,
					requiresPosix,
					argumentSpecs,
					assignments,
					emulateCliMain,
					mergeStdoutAndStderr,
					maxOutputBytes,
					stdinBytes);
		}
	}

	/**
	 * Shared implementation for the fluent builders exposed by
	 * {@link AwkTestSupport}. Subclasses specialise the execution behaviour while
	 * reusing the configuration helpers defined here.
	 *
	 * @param <B> the builder type used for fluent chaining
	 */
	private abstract static class BaseTestBuilder<B extends BaseTestBuilder<B>> {
		protected final String description;
		protected String script;
		protected String stdin;
		protected final Map<String, String> fileContents = new LinkedHashMap<>();
		protected final List<String> operandSpecs = new ArrayList<>();
		protected final List<String> pathPlaceholders = new ArrayList<>();
		protected String expectedOutput;
		protected List<String> expectedLines;
		protected Integer expectedExitCode;
		protected Class<? extends Throwable> expectedException;
		protected boolean requiresPosix;
		protected boolean useTempDir;
		protected List<Function<String, String>> postProcessors = new ArrayList<>();

		BaseTestBuilder(String description) {
			this.description = description;
		}

		/**
		 * Sets the AWK script to execute using a raw {@link String}. Any
		 * placeholder tokens in the script are resolved before execution.
		 *
		 * @param script the script contents
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B script(String script) {
			this.script = script;
			return (B) this;
		}

		/**
		 * Sets the AWK script to execute using a stream. The contents are read as
		 * UTF-8 and treated equivalently to {@link #script(String)}.
		 *
		 * @param scriptStream the stream supplying the script contents
		 * @return this builder for method chaining
		 * @throws IllegalArgumentException when {@code scriptStream} is
		 *         {@code null}
		 * @throws UncheckedIOException when the stream cannot be read
		 */
		public B script(InputStream scriptStream) {
			if (scriptStream == null) {
				throw new IllegalArgumentException("scriptStream must not be null");
			}
			try (InputStream in = scriptStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
				return script(new String(out.toByteArray(), StandardCharsets.UTF_8));
			} catch (IOException ex) {
				throw new UncheckedIOException("Failed to read script stream", ex);
			}
		}

		/**
		 * Provides data that will be delivered on standard input when the script
		 * runs.
		 *
		 * @param stdin the content to stream into standard input
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B stdin(String stdin) {
			this.stdin = stdin;
			return (B) this;
		}

		/**
		 * Adds a temporary file to create before the script runs. The file is
		 * written inside the per-test temporary directory and can be referenced
		 * with {@code {{name}}} placeholders.
		 *
		 * @param name the relative path within the temporary directory
		 * @param contents the file contents to write as UTF-8
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B file(String name, String contents) {
			fileContents.put(name, contents);
			return (B) this;
		}

		/**
		 * Adds operands to pass to the script when it is executed. Placeholders
		 * are resolved at runtime.
		 *
		 * @param operands the operands to add
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B operand(String... operands) {
			operandSpecs.addAll(Arrays.asList(operands));
			return (B) this;
		}

		/**
		 * Reserves an empty path inside the temporary directory and exposes its
		 * location via a placeholder.
		 *
		 * @param placeholder the placeholder identifier to resolve in scripts or
		 *        inputs
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B path(String placeholder) {
			pathPlaceholders.add(placeholder);
			return (B) this;
		}

		/**
		 * Adds a post-processing function to the output of the AWK script
		 * that will be used before running the assertions.
		 *
		 * @param processor function used for post-processing the output of the AWK script
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B postProcessWith(Function<String, String> processor) {
			if (processor != null) {
				postProcessors.add(processor);
			}
			return (B) this;
		}

		/**
		 * Declares the exact output expected from the script.
		 *
		 * @param expected the expected output
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B expect(String expected) {
			this.expectedOutput = expected;
			this.expectedLines = null;
			return (B) this;
		}

		/**
		 * Declares the expected output using individual lines. A trailing newline
		 * is automatically appended when at least one line is supplied.
		 *
		 * @param lines the expected output lines
		 * @return this builder for method chaining
		 */
		public B expectLines(String... lines) {
			return expectLines(Arrays.asList(Arrays.copyOf(lines, lines.length)));
		}

		/**
		 * Declares the expected output using individual lines. A trailing newline
		 * is automatically appended when at least one line is supplied.
		 *
		 * @param lines the expected output lines
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B expectLines(List<String> lines) {
			this.expectedLines = new ArrayList<>(lines);
			this.expectedOutput = null;
			return (B) this;
		}

		/**
		 * Declares the expected output using individual lines. A trailing newline
		 * is automatically appended when at least one line is supplied.
		 *
		 * @param File instance from which we will extract the lines to be matched with
		 * @return this builder for method chaining
		 * @throws IOException if file cannot be read
		 */
		public B expectLines(File expectedResultFile) throws IOException {
			return expectLines(expectedResultFile.toPath());
		}

		/**
		 * Declares the expected output using individual lines. A trailing newline
		 * is automatically appended when at least one line is supplied.
		 *
		 * @param Path to the file from which we will extract the lines to be matched with
		 * @return this builder for method chaining
		 * @throws IOException if file cannot be read
		 */
		public B expectLines(Path expectedResultPath) throws IOException {
			return expectLines(Files.readAllLines(expectedResultPath, StandardCharsets.UTF_8));
		}

		/**
		 * Declares the expected exit code for the execution.
		 *
		 * @param code the exit code to expect
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B expectExit(int code) {
			this.expectedExitCode = code;
			return (B) this;
		}

		/**
		 * Declares the exception type that the script is expected to throw.
		 *
		 * @param exceptionClass the expected exception type
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B expectThrow(Class<? extends Throwable> exceptionClass) {
			this.expectedException = exceptionClass;
			return (B) this;
		}

		/**
		 * Marks the test as requiring POSIX behaviour. The test is skipped when
		 * running on a non-POSIX platform.
		 *
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B posixOnly() {
			this.requiresPosix = true;
			return (B) this;
		}

		/**
		 * Indicates that the test expects a dedicated temporary directory. The
		 * directory path can be referenced using the {@code {{TEMPDIR}}}
		 * placeholder.
		 *
		 * @return this builder for method chaining
		 */
		@SuppressWarnings("unchecked")
		public B withTempDir() {
			this.useTempDir = true;
			return (B) this;
		}

		/**
		 * Produces an immutable {@link ConfiguredTest} based on the recorded
		 * configuration.
		 *
		 * @return a configured test ready for execution
		 */
		public ConfiguredTest build() {
			TestLayout layout = new TestLayout(
					description,
					script,
					stdin,
					postProcessors,
					expectedOutput,
					expectedLines,
					expectedExitCode,
					expectedException);
			Map<String, String> files = new LinkedHashMap<>(fileContents);
			List<String> operands = new ArrayList<>(operandSpecs);
			List<String> placeholders = new ArrayList<>(pathPlaceholders);
			return buildTestCase(layout, files, operands, placeholders);
		}

		/**
		 * Executes the configured test and returns the captured result without
		 * asserting it.
		 *
		 * @return the captured result
		 * @throws Exception when execution fails unexpectedly
		 */
		public TestResult run() throws Exception {
			return build().run();
		}

		/**
		 * Executes the configured test and immediately asserts the recorded
		 * expectations.
		 *
		 * @throws Exception when execution fails unexpectedly
		 */
		public void runAndAssert() throws Exception {
			build().runAndAssert();
		}

		protected abstract BaseTestCase buildTestCase(
				TestLayout layout,
				Map<String, String> fileContents,
				List<String> operandSpecs,
				List<String> pathPlaceholders);
	}

	private abstract static class BaseTestCase implements ConfiguredTest {
		private final TestLayout layout;
		private final Map<String, String> fileContents;
		private final List<String> operandSpecs;
		private final List<String> pathPlaceholders;
		private final boolean requiresPosix;

		BaseTestCase(
				TestLayout layout,
				Map<String, String> fileContents,
				List<String> operandSpecs,
				List<String> pathPlaceholders,
				boolean requiresPosix) {
			this.layout = layout;
			this.fileContents = fileContents;
			this.operandSpecs = operandSpecs;
			this.pathPlaceholders = pathPlaceholders;
			this.requiresPosix = requiresPosix;
		}

		@Override
		public String description() {
			return layout.description;
		}

		@Override
		public void assumeSupported() {
			if (requiresPosix) {
				assumeTrue("POSIX-like environment required for " + layout.description, IS_POSIX);
			}
		}

		@Override
		public final TestResult run() throws Exception {
			assumeSupported();
			ExecutionEnvironment env = prepareEnvironment();
			try {
				return executeAndCapture(env);
			} finally {
				deleteRecursively(env.tempDir);
			}
		}

		private TestResult executeAndCapture(ExecutionEnvironment env) throws Exception {
			try {
				// Execute AWK and get the output
				ActualResult result = execute(env);
				String actualOutput = result.output;

				// Post-processing of the output
				if (layout.postProcessors != null) {
					for (Function<String, String> processor : layout.postProcessors) {
						actualOutput = processor.apply(actualOutput);
					}
				}

				// Post-processing of the expected result (resolve temporary paths)
				String expected = layout.expectedOutput != null ? env.resolve(layout.expectedOutput) : null;
				List<String> expectedLines = null;
				if (layout.expectedLines != null) {
					expectedLines = new ArrayList<>(layout.expectedLines.size());
					for (String line : layout.expectedLines) {
						expectedLines.add(env.resolve(line));
					}
				}

				return new TestResult(
						layout.description,
						actualOutput,
						result.exitCode,
						expected,
						expectedLines,
						layout.expectedExitCode,
						layout.expectedException,
						null);
			} catch (Throwable ex) {
				OutputLimitExceededException outputLimitException = findOutputLimitExceeded(ex);
				if (outputLimitException != null) {
					throw outputLimitException;
				}
				if (layout.expectedException != null && layout.expectedException.isInstance(ex)) {
					return new TestResult(
							layout.description,
							"",
							0,
							null,
							null,
							layout.expectedExitCode,
							layout.expectedException,
							ex);
				}
				if (ex instanceof Exception) {
					throw (Exception) ex;
				}
				throw (Error) ex;
			}
		}

		protected abstract ActualResult execute(ExecutionEnvironment env) throws Exception;

		protected ExecutionEnvironment prepareEnvironment() throws IOException {
			Path tempDir = Files.createTempDirectory("jawk-test");
			Map<String, Path> placeholders = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : fileContents.entrySet()) {
				Path path = tempDir.resolve(entry.getKey());
				Path parent = path.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}

				if (entry.getValue() != null) {
					try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
						writer.write(entry.getValue());
					}
				}
				placeholders.put(entry.getKey(), path);
			}
			for (String placeholder : pathPlaceholders) {
				Path path = tempDir.resolve(placeholder);
				Path parent = path.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				placeholders.put(placeholder, path);
			}
			return new ExecutionEnvironment(tempDir, placeholders);
		}

		protected List<String> resolvedOperands(ExecutionEnvironment env) {
			return operandSpecs
					.stream()
					.map(env::resolve)
					.collect(Collectors.toList());
		}

		protected String resolvedScript(ExecutionEnvironment env) {
			return layout.script != null ? env.resolveScript(layout.script) : null;
		}

		protected String resolvedStdin(ExecutionEnvironment env) {
			return layout.stdin != null ? env.resolve(layout.stdin) : null;
		}
	}

	private static final class AwkTestCase extends BaseTestCase {
		private final Map<String, Object> preAssignments;
		private final Awk customAwk;
		private final List<JawkExtension> extensions;
		private final InputSource inputSource;

		AwkTestCase(
				TestLayout layout,
				Map<String, String> fileContents,
				List<String> operandSpecs,
				List<String> pathPlaceholders,
				boolean requiresPosix,
				Map<String, Object> preAssignments,
				Awk customAwk,
				List<JawkExtension> extensions,
				InputSource inputSource) {
			super(layout, fileContents, operandSpecs, pathPlaceholders, requiresPosix);
			this.preAssignments = new LinkedHashMap<>(preAssignments);
			this.customAwk = customAwk;
			this.extensions = new ArrayList<>(extensions);
			this.inputSource = inputSource;
		}

		@Override
		protected ActualResult execute(ExecutionEnvironment env) throws Exception {
			Awk awk = customAwk != null ? customAwk : new Awk(extensions);
			StringBuilder out = new StringBuilder();
			AwkProgram program = awk.compile(resolvedScript(env));
			Awk.AwkRunBuilder builder = awk
					.script(program)
					.arguments(resolvedOperands(env))
					.variables(preAssignments);
			if (inputSource != null) {
				builder.input(inputSource);
			} else {
				String stdin = resolvedStdin(env);
				if (stdin != null) {
					builder.input(stdin);
				}
			}
			int exitCode = 0;
			try {
				builder.execute(out);
			} catch (ExitException ex) {
				exitCode = ex.getCode();
			}
			return new ActualResult(out.toString(), exitCode);
		}
	}

	private static final class CliTestCase extends BaseTestCase {
		private final List<String> argumentSpecs;
		private final Map<String, Object> assignments;
		private final boolean emulateCliMain;
		private final boolean mergeStdoutAndStderr;
		private final Integer maxOutputBytes;
		private final byte[] stdinBytes;

		CliTestCase(
				TestLayout layout,
				Map<String, String> fileContents,
				List<String> operandSpecs,
				List<String> pathPlaceholders,
				boolean requiresPosix,
				List<String> argumentSpecs,
				Map<String, Object> assignments,
				boolean emulateCliMain,
				boolean mergeStdoutAndStderr,
				Integer maxOutputBytes,
				byte[] stdinBytes) {
			super(layout, fileContents, operandSpecs, pathPlaceholders, requiresPosix);
			this.argumentSpecs = new ArrayList<>(argumentSpecs);
			this.assignments = new LinkedHashMap<>(assignments);
			this.emulateCliMain = emulateCliMain;
			this.mergeStdoutAndStderr = mergeStdoutAndStderr;
			this.maxOutputBytes = maxOutputBytes;
			this.stdinBytes = stdinBytes != null ? Arrays.copyOf(stdinBytes, stdinBytes.length) : null;
		}

		@Override
		protected ActualResult execute(ExecutionEnvironment env) throws Exception {
			String stdin = resolvedStdin(env);
			InputStream in = stdinBytes != null ?
					new ByteArrayInputStream(Arrays.copyOf(stdinBytes, stdinBytes.length)) :
					stdin != null ?
							new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)) :
							new ByteArrayInputStream(new byte[0]);
			ByteArrayOutputStream outBytes = maxOutputBytes != null ?
					new LimitedByteArrayOutputStream(maxOutputBytes.intValue()) :
					new ByteArrayOutputStream();
			PrintStream stdout = new PrintStream(outBytes, true, StandardCharsets.UTF_8.name());
			PrintStream stderr = mergeStdoutAndStderr ?
					stdout :
					new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name());
			Cli cli = new Cli(in, stdout, stderr);

			List<String> args = new ArrayList<>();
			for (Map.Entry<String, Object> entry : assignments.entrySet()) {
				args.add("-v");
				args.add(entry.getKey() + "=" + String.valueOf(entry.getValue()));
			}
			for (String spec : argumentSpecs) {
				args.add(env.resolve(spec));
			}
			String resolvedScript = resolvedScript(env);
			if (resolvedScript != null) {
				args.add(resolvedScript);
			}
			args.addAll(resolvedOperands(env));

			int exitCode = 0;
			try {
				if (emulateCliMain) {
					exitCode = executeLikeCliMain(cli, stderr, args);
				} else {
					cli.parse(args.toArray(new String[0]));
					cli.run();
				}
			} catch (ExitException ex) {
				exitCode = ex.getCode();
			}
			return new ActualResult(
					outBytes.toString(StandardCharsets.UTF_8.name()),
					exitCode);
		}

		private static int executeLikeCliMain(Cli cli, PrintStream err, List<String> args) throws Exception {
			try {
				cli.parse(args.toArray(new String[0]));
				cli.run();
				return 0;
			} catch (ExitException ex) {
				return ex.getCode();
			} catch (AwkRuntimeException ex) {
				OutputLimitExceededException outputLimitException = findOutputLimitExceeded(ex);
				if (outputLimitException != null) {
					throw outputLimitException;
				}
				if (ex.getLineNumber() > 0) {
					err.printf("%s (line %d): %s%n", ex.getClass().getSimpleName(), ex.getLineNumber(), ex.getMessage());
				} else {
					err.printf("%s: %s%n", ex.getClass().getSimpleName(), ex.getMessage());
				}
				return 1;
			} catch (IllegalArgumentException ex) {
				err.println("Failed to parse arguments. Please see the help/usage output (cmd line switch '-h').");
				ex.printStackTrace(err);
				return 1;
			} catch (Exception ex) {
				err.printf("%s: %s%n", ex.getClass().getSimpleName(), ex.getMessage());
				return 1;
			}
		}
	}

	private static final class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
		private final int maxBytes;

		LimitedByteArrayOutputStream(int maxBytes) {
			this.maxBytes = maxBytes;
		}

		@Override
		public synchronized void write(int b) {
			ensureCapacityFor(1);
			super.write(b);
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) {
			ensureCapacityFor(len);
			super.write(b, off, len);
		}

		private void ensureCapacityFor(int additionalBytes) {
			if (additionalBytes > 0 && count > maxBytes - additionalBytes) {
				throw new OutputLimitExceededException(maxBytes);
			}
		}
	}

	private static final class ExecutionEnvironment {
		private final Path tempDir;
		private final Map<String, Path> placeholders;

		ExecutionEnvironment(Path tempDir, Map<String, Path> placeholders) {
			this.tempDir = tempDir;
			this.placeholders = placeholders;
		}

		String resolve(String value) {
			if (value == null) {
				return null;
			}
			return replacePlaceholders(value, false);
		}

		String resolveScript(String value) {
			if (value == null) {
				return null;
			}
			return replacePlaceholders(value, true);
		}

		private String replacePlaceholders(String value, boolean escapeForScript) {
			String result = value;
			for (Map.Entry<String, Path> entry : placeholders.entrySet()) {
				String replacement = entry.getValue().toString();
				if (escapeForScript) {
					replacement = escapeForAwkString(replacement);
				}
				result = result.replace("{{" + entry.getKey() + "}}", replacement);
			}
			return result;
		}
	}

	private static final class ActualResult {
		final String output;
		final int exitCode;

		ActualResult(String output, int exitCode) {
			this.output = output;
			this.exitCode = exitCode;
		}
	}

	private static final class TestLayout {
		final String description;
		final String script;
		final String stdin;
		final List<Function<String, String>> postProcessors;
		final String expectedOutput;
		final List<String> expectedLines;
		final Integer expectedExitCode;
		final Class<? extends Throwable> expectedException;

		TestLayout(
				String description,
				String script,
				String stdin,
				List<Function<String, String>> postProcessors,
				String expectedOutput,
				List<String> expectedLines,
				Integer expectedExitCode,
				Class<? extends Throwable> expectedException) {
			this.description = description;
			this.script = script;
			this.stdin = stdin;
			this.postProcessors = postProcessors != null ?
					Collections.unmodifiableList(new ArrayList<>(postProcessors)) : null;
			this.expectedOutput = expectedOutput;
			this.expectedLines = expectedLines != null ? Collections.unmodifiableList(new ArrayList<>(expectedLines)) : null;
			this.expectedExitCode = expectedExitCode;
			this.expectedException = expectedException;
		}
	}

	static void deleteRecursively(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// best effort cleanup
				}
			});
		}
	}

	private static OutputLimitExceededException findOutputLimitExceeded(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof OutputLimitExceededException) {
				return (OutputLimitExceededException) current;
			}
			current = current.getCause();
		}
		return null;
	}

	private static void copyToDirectory(Path sourceDirectory, Path destinationDirectory, Path sourcePath) {
		try {
			Path relativePath = sourceDirectory.relativize(sourcePath);
			if (relativePath.toString().isEmpty()) {
				return;
			}
			Path destination = destinationDirectory.resolve(relativePath);
			if (Files.isDirectory(sourcePath)) {
				Files.createDirectories(destination);
			} else {
				Path parent = destination.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.copy(sourcePath, destination);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to stage test resource " + sourcePath, ex);
		}
	}

	private static String escapeForAwkString(String value) {
		StringBuilder builder = new StringBuilder(value.length() * 2);
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch == '\\' || ch == '"') {
				builder.append('\\');
			}
			builder.append(ch);
		}
		return builder.toString();
	}
}
