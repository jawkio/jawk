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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test Suite based on unit and non-regression tests from gawk.
 * Each AWK script in the src/test/resources/gawk directory will be
 * executed against the corresponding *.in input, and its output will
 * be compared to the corresponding *.ok file.
 */
@RunWith(Parameterized.class)
public class GawkTest {

	/**
	 * Initialization of the tests (create a temporary directory for some of the scripts)
	 *
	 * @throws Exception
	 */
	@BeforeClass
	public static void beforeAll() throws Exception {}

	/**
	 * @return the list of awk scripts in /src/test/resources/gawk
	 * @throws Exception
	 */
	@Parameters(name = "GAWK {0}")
	public static Iterable<String> awkList() throws Exception {
		// Get the /gawk resource directory
		URL scriptsUrl = GawkTest.class.getResource("/gawk");
		if (scriptsUrl == null) {
			throw new IOException("Couldn't find resource /gawk");
		}

		File scriptsDir = new File(scriptsUrl.toURI());
		if (!scriptsDir.isDirectory()) {
			throw new IOException("/gawk is not a directory");
		}

		return Arrays
				.stream(scriptsDir.listFiles())
				.filter(sf -> sf.getName().toLowerCase().endsWith(".awk"))
				.map(File::getAbsolutePath)
				.collect(Collectors.toList());
	}

	/** Path to the AWK test script to execute */
	@Parameter
	public String awkPath;

	/**
	 * Execute the AWK script stored in {@link #awkPath}
	 *
	 * @throws Exception
	 */
	@Test
	public void test() throws Exception {
		// Get the AWK script file and parent directory
		File awkFile = new File(awkPath);
		String shortName = awkFile.getName().substring(0, awkFile.getName().length() - 4);
		File parent = awkFile.getParentFile();
		// Get the file with the expected result
		File okFile = new File(parent, shortName + ".ok");

		// Load the file with the expected result
		String expectedResult = loadExpectedResult(okFile);

		// Get the list of input files (usually *.in, but could be *.in1, *.in2, etc.)
		List<File> inputFileList = IntStream
				.range(0, 10)
				.mapToObj(i -> (i == 0 ? "" : String.valueOf(i)))
				.map(i -> new File(parent, shortName + ".in" + i))
				.filter(File::isFile)
				.collect(Collectors.toList());

		AwkTestSupport.CliTestBuilder builder = AwkTestSupport
				.cliTest("GAWK " + shortName)
				.argument("-f", awkFile.getAbsolutePath())
				.withTempDir()
				.expect(expectedResult);
		for (File input : inputFileList) {
			builder.operand(input.getAbsolutePath());
		}
		builder.build().run().assertExpected();
	}

	private static String loadExpectedResult(File okFile) throws IOException {
		String expectedResult = new String(Files.readAllBytes(okFile.toPath()), StandardCharsets.UTF_8);
		return normalizeExpectedResult(expectedResult);
	}

	private static String normalizeExpectedResult(String expectedResult) {
		return expectedResult.replace("\r\n", "\n");
	}

}
