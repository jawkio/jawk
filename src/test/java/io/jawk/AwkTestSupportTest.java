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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Unit tests for generic helpers in {@link AwkTestSupport}.
 */
public class AwkTestSupportTest {

	/**
	 * Verifies that newline normalization collapses both CRLF and lone CR
	 * sequences to LF.
	 */
	@Test
	public void normalizeNewlines() {
		assertEquals("a\nb\nc\n", AwkTestSupport.normalizeNewlines("a\r\nb\rc\r\n"));
	}

	/**
	 * Verifies that CLI-style exit-code rendering only appends the synthetic
	 * footer when execution fails.
	 */
	@Test
	public void appendExitCode() {
		assertEquals("ok\n", AwkTestSupport.appendExitCode("ok\r\n", 0, "EXIT CODE: "));
		assertEquals("fail\nEXIT CODE: 2\n", AwkTestSupport.appendExitCode("fail\r\n", 2, "EXIT CODE: "));
	}

	/**
	 * Verifies that mismatch assertions write the actual output artifact and
	 * report a useful failure message.
	 *
	 * @throws Exception when preparing or cleaning the temporary artifact path
	 *         fails
	 */
	@Test
	public void assertOutputMatchesWritesActualArtifact() throws Exception {
		Path tempDirectory = Files.createTempDirectory("jawk-support-assert");
		Path actualOutputPath = tempDirectory.resolve("case.actual");
		try {
			try {
				AwkTestSupport
						.assertOutputMatches(
								"fixture",
								"abc\n",
								"adc\n",
								actualOutputPath,
								"Expected file: fixture.ok");
				fail("Expected an AssertionError");
			} catch (AssertionError ex) {
				assertTrue(ex.getMessage().contains("Unexpected output for fixture"));
				assertTrue(ex.getMessage().contains("Expected file: fixture.ok"));
				assertTrue(ex.getMessage().contains(actualOutputPath.toString()));
				assertEquals("adc\n", new String(Files.readAllBytes(actualOutputPath), StandardCharsets.UTF_8));
			}
		} finally {
			AwkTestSupport.deleteRecursively(tempDirectory);
		}
	}

	/**
	 * Verifies that resource staging copies nested fixture trees into a fresh
	 * temporary directory.
	 *
	 * @throws Exception when preparing or cleaning the temporary directories
	 *         fails
	 */
	@Test
	public void stageDirectoryCopiesNestedFixtures() throws Exception {
		Path sourceDirectory = Files.createTempDirectory("jawk-support-source");
		Path nestedFile = sourceDirectory.resolve("nested").resolve("fixture.txt");
		Files.createDirectories(nestedFile.getParent());
		Files.write(nestedFile, "hello".getBytes(StandardCharsets.UTF_8));

		Path stagedDirectory = null;
		try {
			stagedDirectory = AwkTestSupport.stageDirectory(sourceDirectory, "jawk-support-stage-");
			assertEquals(
					"hello",
					new String(
							Files.readAllBytes(stagedDirectory.resolve("nested").resolve("fixture.txt")),
							StandardCharsets.UTF_8));
		} finally {
			AwkTestSupport.deleteRecursively(stagedDirectory);
			AwkTestSupport.deleteRecursively(sourceDirectory);
		}
	}
}
