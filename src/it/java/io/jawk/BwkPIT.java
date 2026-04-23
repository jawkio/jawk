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
import java.nio.file.Path;
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
 * Integration suite based on BWK pattern tests. Each AWK script in the BWK
 * compatibility resources executes against the shared BWK input file and its
 * output is compared with the recorded result.
 *
 * @see <a href="https://github.com/onetrueawk/awk">One True Awk</a>
 */
@RunWith(Parameterized.class)
public class BwkPIT {

	private static Path bwkPDirectory;
	private static Path scriptsDirectory;

	/**
	 * Initializes the BWK.p integration suite.
	 *
	 * @throws Exception when resource discovery fails
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {}

	/**
	 * Returns the BWK.p script names discovered from the integration-test
	 * resources.
	 *
	 * @return the parameter values for this suite
	 * @throws Exception when resource discovery fails
	 */
	@Parameters(name = "BWK.p {0}")
	public static Iterable<String> awkList() throws Exception {
		bwkPDirectory = CompatibilityTestResources.resourceDirectory(BwkPIT.class, "bwk", "p");
		if (!bwkPDirectory.toFile().isDirectory()) {
			throw new IOException(bwkPDirectory + " is not a directory");
		}
		scriptsDirectory = bwkPDirectory.resolve("scripts");
		if (!scriptsDirectory.toFile().isDirectory()) {
			throw new IOException("scripts is not a directory");
		}
		File[] scriptFiles = scriptsDirectory.toFile().listFiles();
		if (scriptFiles == null) {
			throw new IOException("Couldn't list files in " + scriptsDirectory);
		}

		return Arrays
				.stream(scriptFiles)
				.filter(scriptFile -> scriptFile.getName().startsWith("p."))
				.map(File::getName)
				.sorted()
				.collect(Collectors.toList());
	}

	/** Path to the AWK test script to execute. */
	@Parameter
	public String awkName;

	/**
	 * Executes one BWK.p script and compares its output with the expected result.
	 *
	 * @throws Exception when the test setup or execution fails unexpectedly
	 */
	@Test
	public void test() throws Exception {
		Path awkScriptPath = scriptsDirectory.resolve(awkName);
		Path okFilePath = bwkPDirectory.resolve("results/" + awkName + ".ok");
		Path inputFilePath = bwkPDirectory.resolve("inputs/test.countries");

		AwkTestSupport
				.cliTest("BWK.p " + awkName)
				.argument("-f", awkScriptPath.toString())
				.operand(inputFilePath.toString())
				.postProcessWith(output -> output.replace(inputFilePath.toString(), inputFilePath.getFileName().toString()))
				.expectLines(okFilePath)
				.build()
				.runAndAssert();
	}

	/**
	 * Finalizes the BWK.p integration suite.
	 *
	 * @throws Exception unused hook retained for suite symmetry
	 */
	@AfterClass
	public static void afterAll() throws Exception {}
}
