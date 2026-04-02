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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Test Suite based on unit and non-regression tests from bwk. Each AWK script
 * in the src/test/resources/bwk.t/t.scripts directory will be executed against
 * the corresponding *.in input, and its output will be compared to the
 * corresponding *.ok file.
 *
 * @see <a href="https://github.com/onetrueawk/awk">One True Awk</a>
 */
@RunWith(Parameterized.class)
public class BwkMiscTest {

	private static final String BWK_MISC_PATH = "/bwk/misc";
	private static File bwkMiscDirectory;
	private static File scriptsDirectory;

	/**
	 * Initialization of the tests
	 *
	 * @throws Exception
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {}

	/**
	 * @return the list of awk scripts in /src/test/resources/gawk
	 * @throws Exception
	 */
	@Parameters(name = "BWK.misc {0}")
	public static Iterable<String> awkList() throws Exception {
		// Get the /bwk resource directory
		URL bwkTUrl = BwkTTest.class.getResource(BWK_MISC_PATH);
		if (bwkTUrl == null) {
			throw new IOException("Couldn't find resource " + BWK_MISC_PATH);
		}
		bwkMiscDirectory = new File(bwkTUrl.toURI());
		if (!bwkMiscDirectory.isDirectory()) {
			throw new IOException(BWK_MISC_PATH + " is not a directory");
		}
		scriptsDirectory = new File(bwkMiscDirectory, "scripts");
		if (!scriptsDirectory.isDirectory()) {
			throw new IOException("scripts is not a directory");
		}

		return Arrays
				.stream(scriptsDirectory.listFiles())
				.filter(sf -> sf.getName().endsWith(".awk"))
				.map(File::getName)
				.collect(Collectors.toList());
	}

	/** Path to the AWK test script to execute */
	@Parameter
	public String awkName;

	/**
	 * Execute the AWK script stored in {@link #awkName}
	 *
	 * @throws Exception
	 */
	@Test
	public void test() throws Exception {
		// Get the AWK script file
		File awkFile = new File(scriptsDirectory, awkName);
		String shortName = awkName.substring(0, awkName.length() - 4);

		// Get the input file (always the same)
		File inputFile = new File(bwkMiscDirectory, "inputs/" + shortName + ".in");

		// Get the file with the expected result
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
	 * Initialization of the tests (create a temporary directory for some of the
	 * scripts)
	 *
	 * @throws Exception
	 */
	@AfterClass
	public static void afterAll() throws Exception {}
}
