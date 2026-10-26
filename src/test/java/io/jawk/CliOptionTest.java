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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import io.jawk.frontend.ast.ParserException;
import io.jawk.jsr223.JawkScriptEngineFactory;
import io.jawk.util.JawkVersion;

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
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile prints runtime report")
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
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile records function exit")
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
	public void profileOptionRecordsIndirectUserFunctions() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile records indirect user functions")
				.argument("--profile")
				.script(
						"function inner() { return 1 } " + "function outer() { callback = \"inner\"; extension = \"typeof\"; "
								+ "@extension(1); return @callback() } " + "BEGIN { print outer() }")
				.expectLines("1")
				.run();

		result.assertExpected();
		assertTrue(result.errorOutput().contains("inner"));
		assertTrue(result.errorOutput().contains("outer"));
		assertTrue(result.errorOutput().contains("typeof"));
	}

	@Test
	public void profileOptionRecordsFunctionsUnwoundByNextfile() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile records functions unwound by nextfile")
				.argument("--profile")
				.script("function inner() { nextfile } function outer() { inner() } { outer() }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\nb2\n")
				.file("f3", "c1\nc2\n")
				.operand("{{f1}}", "{{f2}}", "{{f3}}")
				.expect("")
				.run();

		result.assertExpected();
		// Each file triggers exactly one outer() -> inner() -> nextfile chain;
		// nextfile abandons both calls, which must still be recorded once each.
		assertEquals(3, profiledFunctionCount(result.errorOutput(), "inner"));
		assertEquals(3, profiledFunctionCount(result.errorOutput(), "outer"));
	}

	@Test
	public void profileOptionRecordsFunctionsUnwoundByNext() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile records functions unwound by next")
				.argument("--profile")
				.script("function inner() { next } function outer() { inner() } { outer() }")
				.stdin("a\nb\nc\n")
				.expect("")
				.run();

		result.assertExpected();
		// Each record triggers exactly one outer() -> inner() -> next chain;
		// next abandons both calls, which must still be recorded once each.
		assertEquals(3, profiledFunctionCount(result.errorOutput(), "inner"));
		assertEquals(3, profiledFunctionCount(result.errorOutput(), "outer"));
	}

	/**
	 * Extracts the execution count of one function from a {@code --profile}
	 * report.
	 *
	 * @param report the profiling report text
	 * @param functionName the function to look up
	 * @return the reported execution count
	 */
	private static long profiledFunctionCount(String report, String functionName) {
		Matcher matcher = Pattern.compile("^\\s{2}" + Pattern.quote(functionName) + "\\s+(\\d+)\\s", Pattern.MULTILINE)
				.matcher(report);
		assertTrue("function `" + functionName + "' missing from profiling report:\n" + report, matcher.find());
		return Long.parseLong(matcher.group(1));
	}

	@Test
	public void profileOptionWithFilenameWritesReportToFile() throws Exception {
		File profile = tempFolder.newFile("profile.txt");
		assertTrue(profile.delete());

		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile=file writes runtime report")
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

		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile=file reports write failure")
				.argument("--profile=" + profileDirectory.getAbsolutePath())
				.script("BEGIN { print 1 }")
				.expectThrow(UncheckedIOException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Failed to write profiling report"));
	}

	@Test
	public void profileOptionWithEmptyFilenameIsRejected() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI --profile= rejects empty filename")
				.argument("--profile=")
				.script("BEGIN { print 1 }")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Need output filename for --profile"));
	}

	@Test
	public void attachedProgramFileOptionLoadsScript() throws Exception {
		AwkTestSupport.cliTest("CLI -fprog.awk loads the script like -f prog.awk")
				.file("prog.awk", "BEGIN { print \"attached\" }")
				.argument("-f{{prog.awk}}")
				.expectLines("attached")
				.runAndAssert();
	}

	@Test
	public void attachedProgramFileOptionCombinesWithSeparateForm() throws Exception {
		AwkTestSupport.cliTest("CLI mixes -fone.awk with -f two.awk")
				.file("one.awk", "BEGIN { print \"one\" }")
				.file("two.awk", "BEGIN { print \"two\" }")
				.argument("-f{{one.awk}}", "-f", "{{two.awk}}")
				.expectLines("one", "two")
				.runAndAssert();
	}

	@Test
	public void attachedVariableAssignmentIsApplied() throws Exception {
		AwkTestSupport.cliTest("CLI -vx=42 assigns the variable like -v x=42")
				.argument("-vx=42")
				.script("BEGIN { print x }")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void attachedFieldSeparatorIsApplied() throws Exception {
		AwkTestSupport.cliTest("CLI -F: sets the field separator like -F :")
				.argument("-F:")
				.script("{ print $2 }")
				.stdin("a:b:c\n")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void attachedOptionArgumentAfterDoubleDashStaysInArgv() throws Exception {
		AwkTestSupport.cliTest("CLI attached option-argument after -- is an operand")
				.file("argv.awk", "BEGIN { for (i = 1; i < ARGC; i++) print i \"=\" ARGV[i] }")
				.argument("-f", "{{argv.awk}}", "--")
				.operand("-fnot-an-option.awk")
				.expectLines("1=-fnot-an-option.awk")
				.runAndAssert();
	}

	@Test
	public void attachedUnknownOptionWithoutScriptIsStillRejected() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI unknown glued option without script is rejected")
				.argument("-q2")
				.script("{ print }")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Unknown parameter: -q2"));
	}

	@Test
	public void doubleDashEndsOptionProcessing() throws Exception {
		// After "--", the dash-leading argument is no longer an option: it is
		// the inline script (without "--" it would be rejected as unknown)
		AwkTestSupport.cliTest("CLI -- ends option processing")
				.argument("--", "-1 { print \"ok\" }")
				.stdin("x\n")
				.expectLines("ok")
				.runAndAssert();
	}

	@Test
	public void doubleDashPassesRemainingArgumentsToArgv() throws Exception {
		AwkTestSupport.cliTest("CLI -- passes remaining arguments to ARGV")
				.file("argv.awk", "BEGIN { for (i = 1; i < ARGC; i++) print i \"=\" ARGV[i] }")
				.argument("-f", "{{argv.awk}}", "--")
				.operand("-v", "x=1")
				.expectLines("1=-v", "2=x=1")
				.runAndAssert();
	}

	@Test
	public void singleDashOperandReadsStandardInput() throws Exception {
		AwkTestSupport.cliTest("CLI - operand reads standard input")
				.script("{ print FILENAME \":\" $0 }")
				.operand("-")
				.stdin("hello\n")
				.expectLines("-:hello")
				.runAndAssert();
	}

	@Test
	public void singleDashOperandMixesWithRegularFiles() throws Exception {
		AwkTestSupport.cliTest("CLI - operand mixes with regular input files")
				.script("{ print FILENAME \":\" FNR \":\" $0 }")
				.file("data.txt", "a\n")
				.operand("-", "{{data.txt}}")
				.stdin("s\n")
				.expectLines("-:1:s", "{{data.txt}}:1:a")
				.runAndAssert();
	}

	@Test
	public void singleDashOperandWorksWithBeginFileRules() throws Exception {
		AwkTestSupport.cliTest("CLI - operand works with BEGINFILE rules")
				.script("BEGINFILE { print \"BF:\" FILENAME } { print $0 }")
				.operand("-")
				.stdin("s\n")
				.expectLines("BF:-", "s")
				.runAndAssert();
	}

	@Test
	public void singleDashBeforeScriptIsTreatedAsScript() throws Exception {
		// "-" no longer terminates option processing while being skipped: it is
		// an operand, so here it is (mis)used as the inline script, like gawk
		AwkTestSupport.cliTest("CLI - before script is treated as the script")
				.argument("-", "BEGIN { print \"never\" }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void unreadableFileAfterStdinOperandDoesNotCloseCallerInput() throws Exception {
		File missing = new File(tempFolder.getRoot(), "missing.txt");
		CloseTrackingInputStream input = new CloseTrackingInputStream("s\n".getBytes(StandardCharsets.UTF_8));

		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI unreadable file after - operand keeps caller input open")
				.stdin(input)
				.script("{ print }")
				.operand("-", missing.getAbsolutePath())
				.expectThrow(Exception.class)
				.run();

		result.assertExpected();
		assertFalse(input.isClosed());
	}

	@Test
	public void unknownOptionAfterScriptFileIsPassedToArgv() throws Exception {
		AwkTestSupport.cliTest("CLI unknown option after -f is passed to ARGV")
				.file("argv.awk", "BEGIN { for (i = 1; i < ARGC; i++) print i \"=\" ARGV[i] }")
				.argument("-f", "{{argv.awk}}", "-q")
				.operand("one")
				.expectLines("1=-q", "2=one")
				.runAndAssert();
	}

	@Test
	public void unknownOptionWithoutScriptIsRejected() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI unknown option without script is rejected")
				.argument("-q")
				.script("{ print }")
				.expectThrow(IllegalArgumentException.class)
				.run();

		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("Unknown parameter: -q"));
	}

	@Test
	public void posixOptionDisablesArraysOfArrays() {
		Cli cli = new Cli();
		cli.parse(new String[] { "--posix", "{ print 1 }" });

		assertTrue(cli.getSettings().isPosix());
	}

	@Test
	public void posixOptionRejectsNestedArrays() throws Exception {
		AwkTestSupport.cliTest("CLI --posix rejects nested arrays")
				.argument("--posix")
				.script("BEGIN { a[1][2] = 42 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void posixOptionStillAllowsClassicMultidimensionalSubscripts() throws Exception {
		AwkTestSupport.cliTest("CLI --posix still allows classic multidimensional subscripts")
				.argument("--posix")
				.script("BEGIN { a[1,2] = 42; print a[1,2] }")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void posixOptionRejectsPrecompiledProgramsInEitherOrder() throws Exception {
		File compiled = tempFolder.newFile("program.ser");
		writeProgram(compiled, "BEGIN { print 1 }");

		AwkTestSupport.TestResult posixThenLoad = AwkTestSupport.cliTest("CLI rejects --posix before -L")
				.argument("--posix", "-L", compiled.getAbsolutePath())
				.expectThrow(IllegalArgumentException.class)
				.run();
		posixThenLoad.assertExpected();
		assertTrue(posixThenLoad.thrownException().getMessage().contains("--posix cannot be combined with -L"));

		AwkTestSupport.TestResult loadThenPosix = AwkTestSupport.cliTest("CLI rejects --posix after -L")
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
	public void versionOptionPrintsVersionAndJavaRuntime() throws Exception {
		String output = runReportingMode(Collections.<String, String>emptyMap(), "--version");

		String[] lines = output.split("\\R");
		assertEquals(2, lines.length);
		assertTrue(lines[0].matches("jawk \\S+"));
		assertTrue(lines[1].startsWith("Java " + System.getProperty("java.version")));
		assertTrue(lines[1].contains(System.getProperty("java.vm.name")));
	}

	@Test
	public void versionOptionAndScriptEngineFactoryReportTheSameVersion() throws Exception {
		// One source of truth for the version, so the CLI report and the JSR 223
		// report cannot drift apart again (issues #598 and #599)
		String output = runReportingMode(Collections.<String, String>emptyMap(), "--version");
		String reported = output.split("\\R")[0].substring("jawk ".length());

		assertEquals(JawkVersion.getVersion(), reported);
		assertEquals(reported, new JawkScriptEngineFactory().getEngineVersion());
	}

	@Test
	public void shortVersionOptionPrintsTheSameReport() throws Exception {
		assertEquals(
				runReportingMode(Collections.<String, String>emptyMap(), "--version"),
				runReportingMode(Collections.<String, String>emptyMap(), "-V"));
	}

	@Test
	public void versionOptionRejectsOtherArguments() {
		IllegalArgumentException after = assertThrows(
				IllegalArgumentException.class,
				() -> new Cli().parse(new String[]
				{ "--version", "-t" }));
		assertTrue(after.getMessage().contains("When printing the version"));

		IllegalArgumentException before = assertThrows(
				IllegalArgumentException.class,
				() -> new Cli().parse(new String[]
				{ "-t", "-V" }));
		assertTrue(before.getMessage().contains("When printing the version"));
	}

	@Test
	public void versionOptionAfterProgramTextStaysInArgv() throws Exception {
		// Like any option after the program text, --version belongs to the
		// script through ARGV (gawk behavior), not to the CLI
		AwkTestSupport.cliTest("CLI --version after the program text stays in ARGV")
				.script("BEGIN { print ARGV[1] }")
				.operand("--version")
				.expectLines("--version")
				.runAndAssert();
	}

	@Test
	public void helpOutputUsesLauncherProgramName() throws Exception {
		String output = runReportingMode(Collections.singletonMap("JAWK_PROGRAM_NAME", "jawk"), "-h");

		assertTrue(output.contains("jawk [-F fs_val]"));
		assertTrue(output.contains("jawk --list-ext"));
		assertFalse(output.contains("java -jar"));
	}

	@Test
	public void helpOutputFallsBackToJavaJarInvocation() throws Exception {
		String output = runReportingMode(Collections.<String, String>emptyMap(), "-h");

		assertTrue(output.contains("java -jar"));
	}

	@Test
	public void blankProgramNameFallsBackToJavaJarInvocation() throws Exception {
		String output = runReportingMode(Collections.singletonMap("JAWK_PROGRAM_NAME", "   "), "-h");

		assertTrue(output.contains("java -jar"));
	}

	@Test
	public void helpOutputAdvertisesVersionOption() throws Exception {
		String output = runReportingMode(Collections.<String, String>emptyMap(), "-h");

		assertTrue(output.contains("-V or --version"));
	}

	@Test
	public void helpOutputDoesNotAdvertiseUnsupportedOutputOption() throws Exception {
		String output = runReportingMode(Collections.<String, String>emptyMap(), "-h");

		assertFalse(output.contains("[-o output-filename]"));
		assertFalse(output.contains(" -o = "));
	}

	/**
	 * Runs the CLI in a reporting mode that does not execute an AWK script,
	 * such as {@code -h} or {@code --version}, and captures its standard
	 * output.
	 *
	 * @param environment environment variables visible to the CLI
	 * @param args command-line arguments
	 * @return the text printed on standard output
	 * @throws Exception if parsing or running the CLI fails
	 */
	private static String runReportingMode(Map<String, String> environment, String... args) throws Exception {
		ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
		try (
				PrintStream outputStream = new PrintStream(outputBytes, true, StandardCharsets.UTF_8.name());
				PrintStream errorStream = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name())) {
			Cli cli = new Cli(new ByteArrayInputStream(new byte[0]), outputStream, errorStream, environment);
			cli.parse(args);
			cli.run();
		}
		return new String(outputBytes.toByteArray(), StandardCharsets.UTF_8);
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

		AwkTestSupport.cliTest("CLI persist first run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		assertTrue(memory.isFile());
		AwkTestSupport.cliTest("CLI persist second run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("2\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistOptionPersistsAssociativeArraysAcrossCliRuns() throws Exception {
		File memory = new File(tempFolder.getRoot(), "array-memory.bin");

		AwkTestSupport.cliTest("CLI persist array first run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { arr[\"x\"] = 9 }")
				.expect("")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport.cliTest("CLI persist array second run")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print arr[\"x\"] }")
				.expect("9\n")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistOptionExcludesRuntimeMetaTables() throws Exception {
		File memory = new File(tempFolder.getRoot(), "symtab-memory.bin");

		AwkTestSupport.cliTest("CLI persist excludes SYMTAB from its serialized snapshot")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { value = 7; print SYMTAB[\"value\"] }")
				.expectLines("7")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport.cliTest("CLI persist restores user globals and rebuilds SYMTAB")
				.argument("--persist", memory.getAbsolutePath())
				.script("BEGIN { print value, SYMTAB[\"value\"] }")
				.expectLines("7 7")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void persistentMemoryEnvironmentVariablePersistsUserGlobalsAcrossCliRuns() throws Exception {
		File memory = new File(tempFolder.getRoot(), "env-memory.bin");
		Map<String, String> environment = Collections.singletonMap("JAWK_PERSISTENT_MEMORY", memory.getAbsolutePath());

		AwkTestSupport.cliTest("CLI env persist first run")
				.env(environment)
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		assertTrue(memory.isFile());
		AwkTestSupport.cliTest("CLI env persist second run")
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

		AwkTestSupport.cliTest("CLI persist option first run")
				.env(environment)
				.argument("--persist", optionMemory.getAbsolutePath())
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport.cliTest("CLI env-only persist run")
				.env(environment)
				.script("BEGIN { print ++i }")
				.expect("1\n")
				.expectExit(0)
				.runAndAssert();
		AwkTestSupport.cliTest("CLI persist option second run")
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

		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("CLI persist rejects wrong serialized type")
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

	private static final class CloseTrackingInputStream extends ByteArrayInputStream {
		private boolean closed;

		CloseTrackingInputStream(byte[] data) {
			super(data);
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}

		private boolean isClosed() {
			return closed;
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
