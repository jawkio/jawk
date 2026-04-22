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
 * Integration suite based on BWK miscellaneous compatibility tests. Each AWK
 * script in the BWK compatibility resources executes against its corresponding
 * input file and its output is compared with the recorded result.
 *
 * @see <a href="https://github.com/onetrueawk/awk">One True Awk</a>
 */
@RunWith(Parameterized.class)
public class BwkMiscIT {

	private static final String BWK_MISC_PATH = "/bwk/misc";
	private static File bwkMiscDirectory;
	private static File scriptsDirectory;

	/**
	 * Initializes the BWK miscellaneous integration suite.
	 *
	 * @throws Exception when resource discovery fails
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {}

	/**
	 * Returns the BWK miscellaneous script names discovered from the
	 * integration-test resources.
	 *
	 * @return the parameter values for this suite
	 * @throws Exception when resource discovery fails
	 */
	@Parameters(name = "BWK.misc {0}")
	public static Iterable<String> awkList() throws Exception {
		URL bwkMiscUrl = BwkMiscIT.class.getResource(BWK_MISC_PATH);
		if (bwkMiscUrl == null) {
			throw new IOException("Couldn't find resource " + BWK_MISC_PATH);
		}
		bwkMiscDirectory = new File(bwkMiscUrl.toURI());
		if (!bwkMiscDirectory.isDirectory()) {
			throw new IOException(BWK_MISC_PATH + " is not a directory");
		}
		scriptsDirectory = new File(bwkMiscDirectory, "scripts");
		if (!scriptsDirectory.isDirectory()) {
			throw new IOException("scripts is not a directory");
		}
		File[] scriptFiles = scriptsDirectory.listFiles();
		if (scriptFiles == null) {
			throw new IOException("Couldn't list files in " + scriptsDirectory);
		}

		return Arrays
				.stream(scriptFiles)
				.filter(scriptFile -> scriptFile.getName().endsWith(".awk"))
				.map(File::getName)
				.sorted()
				.collect(Collectors.toList());
	}

	/** Path to the AWK test script to execute. */
	@Parameter
	public String awkName;

	/**
	 * Executes one BWK miscellaneous script and compares its output with the
	 * expected result.
	 *
	 * @throws Exception when the test setup or execution fails unexpectedly
	 */
	@Test
	public void test() throws Exception {
		File awkFile = new File(scriptsDirectory, awkName);
		String shortName = awkName.substring(0, awkName.length() - 4);
		File inputFile = new File(bwkMiscDirectory, "inputs/" + shortName + ".in");
		File okFile = new File(bwkMiscDirectory, "results/" + shortName + ".ok");

		AwkTestSupport
				.cliTest("BWK.misc " + awkName)
				.argument("-f", awkFile.getAbsolutePath())
				.operand(inputFile.getAbsolutePath())
				.expectLines(okFile)
				.build()
				.runAndAssert();
	}

	/**
	 * Finalizes the BWK miscellaneous integration suite.
	 *
	 * @throws Exception unused hook retained for suite symmetry
	 */
	@AfterClass
	public static void afterAll() throws Exception {}
}
