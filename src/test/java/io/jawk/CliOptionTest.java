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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import io.jawk.frontend.ast.ParserException;

public class CliOptionTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void shortNoOptimizeOptionDisablesOptimization() {
		Cli cli = new Cli();
		cli.parse(new String[] { "-s", "{ print 1 }" });

		assertTrue(cli.isDisableOptimize());
	}

	@Test
	public void longNoOptimizeOptionDisablesOptimization() {
		Cli cli = new Cli();
		cli.parse(new String[] { "--no-optimize", "{ print 1 }" });

		assertTrue(cli.isDisableOptimize());
	}

	@Test
	public void profileOptionPrintsReportToStderr() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI --profile prints runtime report")
				.argument("--profile")
				.script("function inc(x) { return x + 1 } BEGIN { print inc(1); print inc(2) }")
				.expect("2\n3\n")
				.run();

		result.assertExpected();
		assertTrue(result.errorOutput().contains("Jawk profiling report"));
		assertTrue(result.errorOutput().contains("Tuple execution:"));
		assertTrue(result.errorOutput().contains("Function execution:"));
		assertTrue(result.errorOutput().contains("CALL_FUNCTION"));
		assertTrue(result.errorOutput().contains("inc"));
	}

	@Test
	public void profileOptionRecordsFunctionThatExitsWithoutReturning() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI --profile records function exit")
				.argument("--profile")
				.script("function stop() { exit } END { stop() }")
				.expect("")
				.expectExit(0)
				.run();

		result.assertExpected();
		assertTrue(result.errorOutput().contains("Jawk profiling report"));
		assertTrue(result.errorOutput().contains("stop"));
	}

	@Test
	public void profileOptionWithFilenameWritesReportToFile() throws Exception {
		File profile = tempFolder.newFile("profile.txt");
		assertTrue(profile.delete());

		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI --profile=file writes runtime report")
				.argument("--profile=" + profile.getAbsolutePath())
				.script("function inc(x) { return x + 1 } BEGIN { print inc(1) }")
				.expect("2\n")
				.run();

		result.assertExpected();
		assertEquals("", result.errorOutput());
		assertTrue(profile.isFile());
		String report = new String(Files.readAllBytes(profile.toPath()), StandardCharsets.UTF_8);
		assertTrue(report.contains("Jawk profiling report"));
		assertTrue(report.contains("Tuple execution:"));
		assertTrue(report.contains("Function execution:"));
		assertTrue(report.contains("CALL_FUNCTION"));
		assertTrue(report.contains("inc"));
	}

	@Test
	public void profileOptionWriteFailureIsReportedAsIoFailure() throws Exception {
		File profileDirectory = tempFolder.newFolder("profile-directory");

		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI --profile=file reports write failure")
				.argument("--profile=" + profileDirectory.getAbsolutePath())
				.script("BEGIN { print 1 }")
				.expectThrow(UncheckedIOException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Failed to write profiling report"));
	}

	@Test
	public void profileOptionWithEmptyFilenameIsRejected() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI --profile= rejects empty filename")
				.argument("--profile=")
				.script("BEGIN { print 1 }")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Need output filename for --profile"));
	}

	@Test
	public void posixOptionDisablesArraysOfArrays() {
		Cli cli = new Cli();
		cli.parse(new String[] { "--posix", "{ print 1 }" });

		assertTrue(cli.getSettings().isPosix());
	}

	@Test
	public void posixOptionRejectsNestedArrays() throws Exception {
		AwkTestSupport
				.cliTest("CLI --posix rejects nested arrays")
				.argument("--posix")
				.script("BEGIN { a[1][2] = 42 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void posixOptionStillAllowsClassicMultidimensionalSubscripts() throws Exception {
		AwkTestSupport
				.cliTest("CLI --posix still allows classic multidimensional subscripts")
				.argument("--posix")
				.script("BEGIN { a[1,2] = 42; print a[1,2] }")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void posixOptionRejectsPrecompiledProgramsInEitherOrder() throws Exception {
		File compiled = tempFolder.newFile("program.ser");
		writeProgram(compiled, "BEGIN { print 1 }");

		AwkTestSupport.TestResult posixThenLoad = AwkTestSupport
				.cliTest("CLI rejects --posix before -L")
				.argument("--posix", "-L", compiled.getAbsolutePath())
				.expectThrow(IllegalArgumentException.class)
				.run();
		posixThenLoad.assertExpected();
		assertTrue(posixThenLoad.thrownException().getMessage().contains("--posix cannot be combined with -L"));

		AwkTestSupport.TestResult loadThenPosix = AwkTestSupport
				.cliTest("CLI rejects --posix after -L")
				.argument("-L", compiled.getAbsolutePath(), "--posix")
				.expectThrow(IllegalArgumentException.class)
				.run();
		loadThenPosix.assertExpected();
		assertTrue(loadThenPosix.thrownException().getMessage().contains("--posix cannot be combined with -L"));
	}

	@Test
	public void posixLoadOptionWithoutFilenameReportsMissingArgument() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI reports missing -L argument before --posix incompatibility")
				.argument("--posix", "-L")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Need additional argument for -L"));
	}

	@Test
	public void persistOptionWithoutFilenameReportsMissingArgument() {
		Cli cli = new Cli();
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> cli.parse(new String[]
				{ "--persist" }));
		assertTrue(ex.getMessage().contains("Need additional argument for --persist"));
	}

	@Test
	public void helpOutputDoesNotAdvertiseUnsupportedOutputOption() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI help omits unsupported -o option")
				.argument("-h")
				.run();

		assertFalse(result.output().contains("[-o output-filename]"));
		assertFalse(result.output().contains(" -o = "));
	}

	@Test
	public void loadOptionWithWrongSerializedTypeThrowsFriendlyError() throws Exception {
		File bad = tempFolder.newFile("wrong-type.ser");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(bad))) {
			oos.writeObject("not an AwkProgram");
		}

		Cli cli = new Cli();
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> cli.parse(new String[]
				{ "-L", bad.getAbsolutePath(), "{ print }" }));
		assertTrue(ex.getMessage().contains("does not contain a valid precompiled AwkProgram"));
		assertTrue(ex.getCause() instanceof ClassCastException);
	}

	@Test
	public void persistOptionPersistsUserGlobalsAcrossCliRuns() throws Exception {
		File memory = new File(tempFolder.getRoot(), "memory.bin");

		AwkTestSupport
				.cliTest("CLI persist first run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		assertTrue(memory.isFile());
		AwkTestSupport
				.cliTest("CLI persist second run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("2\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistOptionPersistsAssociativeArraysAcrossCliRuns() throws Exception {
		File memory = new File(tempFolder.getRoot(), "array-memory.bin");

		AwkTestSupport
				.cliTest("CLI persist array first run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { arr[\"x\"] = 9 }")
				.expect("")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport
				.cliTest("CLI persist array second run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print arr[\"x\"] }")
				.expect("9\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistentMemoryEnvironmentVariablePersistsUserGlobalsAcrossCliRuns() throws Exception {
		File memory = new File(tempFolder.getRoot(), "env-memory.bin");
		Map<String, String> environment = Collections
				.singletonMap(
						"JAWK_PERSISTENT_MEMORY",
						memory.getAbsolutePath());

		AwkTestSupport
				.cliTest("CLI env persist first run")
				.env(environment)
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		assertTrue(memory.isFile());
		AwkTestSupport
				.cliTest("CLI env persist second run")
				.env(environment)
				.script("BEGIN { print ++i }")
				.expect("2\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistOptionOverridesPersistentMemoryEnvironmentVariable() throws Exception {
		File optionMemory = new File(tempFolder.getRoot(), "option-memory.bin");
		File envMemory = new File(tempFolder.getRoot(), "env-memory.bin");
		Map<String, String> environment = new HashMap<>();
		environment.put("JAWK_PERSISTENT_MEMORY", envMemory.getAbsolutePath());

		AwkTestSupport
				.cliTest("CLI persist option first run")
				.env(environment)
				.argument("--persist", optionMemory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport
				.cliTest("CLI env-only persist run")
				.env(environment)
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport
				.cliTest("CLI persist option second run")
				.env(environment)
				.argument("--persist", optionMemory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("2\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistOptionWithWrongSerializedTypeThrowsFriendlyError() throws Exception {
		File bad = tempFolder.newFile("wrong-persistent-type.ser");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(bad))) {
			oos.writeObject("not persistent memory");
		}

		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI persist rejects wrong serialized type")
				.argument("--persist", bad.getAbsolutePath())
				.script("BEGIN { print 1 }")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("does not contain valid Jawk persistent memory"));
		assertTrue(result.thrownException().getCause() instanceof ClassCastException);
	}

	@Test
	public void persistOptionFlushesOutputBeforeSaveFailure() throws Exception {
		File blockingParent = tempFolder.newFile("not-a-directory");
		File memory = new File(blockingParent, "memory.bin");
		FlushTrackingOutputStream output = new FlushTrackingOutputStream();
		Cli cli = new Cli(
				new ByteArrayInputStream(new byte[0]),
				new PrintStream(output, false, StandardCharsets.UTF_8.name()),
				new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
				Collections.<String, String>emptyMap());
		cli.parse(new String[] { "--persist", memory.getAbsolutePath(), "BEGIN { print \"ok\" }" });

		IOException ex = assertThrows(IOException.class, () -> cli.run());

		assertTrue(ex.getMessage().contains("Failed to create directory"));
		assertEquals("ok\n", output.flushedText());
	}

	private static void writeProgram(File target, String script) throws Exception {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(target))) {
			oos.writeObject(new Awk().compile(script));
		}
	}

	private static final class FlushTrackingOutputStream extends OutputStream {
		private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
		private final ByteArrayOutputStream flushed = new ByteArrayOutputStream();

		@Override
		public void write(int b) {
			pending.write(b);
		}

		@Override
		public void flush() throws IOException {
			pending.writeTo(flushed);
			pending.reset();
		}

		private String flushedText() {
			return new String(flushed.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
