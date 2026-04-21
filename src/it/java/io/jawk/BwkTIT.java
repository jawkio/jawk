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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration suite based on BWK text-processing tests. Each AWK script in the
 * BWK compatibility resources executes against the shared BWK input file and
 * its output is compared with the recorded result.
 *
 * @see <a href="https://github.com/onetrueawk/awk">One True Awk</a>
 */
@RunWith(Parameterized.class)
public class BwkTIT {

	private static final String BWK_T_PATH = "/bwk/t";
	private static Path bwkTDirectory;
	private static Path scriptsDirectory;

	/**
	 * Initializes the BWK.t integration suite.
	 *
	 * @throws Exception when resource discovery fails
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {}

	/**
	 * Returns the BWK.t script names discovered from the integration-test
	 * resources.
	 *
	 * @return the parameter values for this suite
	 * @throws Exception when resource discovery fails
	 */
	@Parameters(name = "BWK.t {0}")
	public static Iterable<String> awkList() throws Exception {
		URL bwkTUrl = BwkTIT.class.getResource(BWK_T_PATH);
		if (bwkTUrl == null) {
			throw new IOException("Couldn't find resource " + BWK_T_PATH);
		}
		bwkTDirectory = Paths.get(bwkTUrl.toURI());
		if (!bwkTDirectory.toFile().isDirectory()) {
			throw new IOException(BWK_T_PATH + " is not a directory");
		}
		scriptsDirectory = bwkTDirectory.resolve("scripts");
		if (!scriptsDirectory.toFile().isDirectory()) {
			throw new IOException("scripts is not a directory");
		}

		return Arrays
				.stream(scriptsDirectory.toFile().listFiles())
				.filter(scriptFile -> scriptFile.getName().startsWith("t."))
				.map(File::getName)
				.sorted()
				.collect(Collectors.toList());
	}

	/** Path to the AWK test script to execute. */
	@Parameter
	public String awkName;

	/**
	 * Executes one BWK.t script and compares its output with the expected result.
	 *
	 * @throws Exception when the test setup or execution fails unexpectedly
	 */
	@Test
	public void test() throws Exception {
		Path awkPath = scriptsDirectory.resolve(awkName);
		Path okPath = bwkTDirectory.resolve("results/" + awkName + ".ok");
		Path inputPath = bwkTDirectory.resolve("inputs/test.data");

		int expectedCode = 0;
		if ("t.exit".equals(awkName)) {
			expectedCode = 1;
		} else if ("t.exit1".equals(awkName)) {
			expectedCode = 2;
		}

		if ("t.in2".equals(awkName) || "t.intest2".equals(awkName)) {
			String expectedResult = Files
					.readAllLines(okPath, StandardCharsets.UTF_8)
					.stream()
					.sorted()
					.collect(Collectors.joining("\n"));

			AwkTestSupport
					.awkTest("BWK.t " + awkName)
					.script(Files.newInputStream(awkPath))
					.operand(inputPath.toString())
					.postProcessWith(output -> Arrays.stream(output.split("\\R")).sorted().collect(Collectors.joining("\n")))
					.expect(expectedResult)
					.expectExit(expectedCode)
					.build()
					.runAndAssert();
		} else {
			AwkTestSupport
					.awkTest("BWK.t " + awkName)
					.script(Files.newInputStream(awkPath))
					.operand(inputPath.toString())
					.expectLines(okPath)
					.expectExit(expectedCode)
					.build()
					.runAndAssert();
		}
	}

	/**
	 * Finalizes the BWK.t integration suite.
	 *
	 * @throws Exception unused hook retained for suite symmetry
	 */
	@AfterClass
	public static void afterAll() throws Exception {}
}
