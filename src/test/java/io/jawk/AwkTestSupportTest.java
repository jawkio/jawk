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
import static org.junit.Assert.assertNull;

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
	 * Verifies that CLI transcript parsing splits the synthetic exit-code trailer
	 * from the expected output content.
	 */
	@Test
	public void readExpectedCliTranscriptWithExitCode() throws Exception {
		Path transcript = Files.createTempFile("jawk-support-transcript", ".ok");
		try {
			Files.write(transcript, "fail\r\nEXIT CODE: 2\r\n".getBytes(StandardCharsets.UTF_8));
			AwkTestSupport.ExpectedCliTranscript expected = AwkTestSupport
					.readExpectedCliTranscript(transcript, "EXIT CODE: ");
			assertEquals("fail\n", expected.output());
			assertEquals(Integer.valueOf(2), expected.exitCode());
		} finally {
			AwkTestSupport.deleteRecursively(transcript);
		}
	}

	/**
	 * Verifies that CLI transcript parsing leaves the exit code unset when the
	 * expected file contains only output.
	 *
	 * @throws Exception when preparing or cleaning the temporary transcript path
	 *         fails
	 */
	@Test
	public void readExpectedCliTranscriptWithoutExitCode() throws Exception {
		Path transcript = Files.createTempFile("jawk-support-transcript", ".ok");
		try {
			Files.write(transcript, "ok\r\n".getBytes(StandardCharsets.UTF_8));
			AwkTestSupport.ExpectedCliTranscript expected = AwkTestSupport
					.readExpectedCliTranscript(transcript, "EXIT CODE: ");
			assertEquals("ok\n", expected.output());
			assertNull(expected.exitCode());
		} finally {
			AwkTestSupport.deleteRecursively(transcript);
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
