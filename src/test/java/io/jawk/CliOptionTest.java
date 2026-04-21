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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
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
	public void posixOptionDisablesArraysOfArrays() {
		Cli cli = new Cli();
		cli.parse(new String[] { "--posix", "{ print 1 }" });

		assertFalse(cli.getSettings().isAllowArraysOfArrays());
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
	public void helpOutputDoesNotAdvertiseUnsupportedOutputOption() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("CLI help omits unsupported -o option")
				.argument("-h")
				.run();

		assertFalse(result.output().contains("[-o output-filename]"));
		assertFalse(result.output().contains(" -o = "));
	}

	@Test(expected = AwkTestSupport.OutputLimitExceededException.class)
	public void cliTestBuilderRejectsRunawayOutput() throws Exception {
		AwkTestSupport
				.cliTest("CLI output limit")
				.script("BEGIN { for (i = 0; i < 1000; i++) print \"0123456789\" }")
				.maxOutputBytes(64)
				.run();
	}

	@Test
	public void cliTestBuilderAcceptsRawStdinBytes() throws Exception {
		AwkTestSupport
				.cliTest("CLI raw stdin bytes")
				.script("{ print length($0) }")
				.stdin(new byte[]
				{ (byte) 0xED, (byte) 0xA0, (byte) 0x80, '\n' })
				.expectLines("1")
				.runAndAssert();
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

	private static void writeProgram(File target, String script) throws Exception {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(target))) {
			oos.writeObject(new Awk().compile(script));
		}
	}
}
