package io.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 */

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
		AwkTestSupport.deleteRecursively(suiteState.stagedDirectory);
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
		String actual = AwkTestSupport.appendExitCode(result.output(), result.exitCode(), EXIT_CODE_PREFIX);
		String expected = AwkTestSupport.readUtf8Normalized(state.stagedDirectory.resolve(caseName + ".ok"));
		AwkTestSupport
				.assertOutputMatches(
						"GAWK " + caseName,
						expected,
						actual,
						state.actualOutputDirectory.resolve(caseName + ".actual"));
	}

	private static synchronized SuiteState loadSuiteState() throws Exception {
		if (suiteState != null) {
			return suiteState;
		}
		Path resourceDirectory = resolveResourceDirectory();
		Path stagedDirectory = null;
		try {
			stagedDirectory = AwkTestSupport.stageDirectory(resourceDirectory, STAGED_DIRECTORY_PREFIX);
			Path actualOutputDirectory = resolveActualOutputDirectory();
			suiteState = new SuiteState(stagedDirectory, actualOutputDirectory);
			return suiteState;
		} catch (Exception ex) {
			AwkTestSupport.deleteRecursively(stagedDirectory);
			throw ex;
		}
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

	private static Path resolveActualOutputDirectory() throws IOException {
		Path actualOutputDirectory = AwkTestSupport
				.buildDirectory(GawkManualIT.class)
				.resolve("failsafe-reports")
				.resolve(ACTUAL_OUTPUT_DIRECTORY);
		return Files.createDirectories(actualOutputDirectory);
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
