package io.jawk.gawk;

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

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import io.jawk.CompatibilityTestResources;

/**
 * Shared helpers for the explicit gawk compatibility integration suites
 * transcribed from the vendored GNU Awk test suite.
 */
abstract class AbstractGawkSuite {

	protected static final Path GAWK_DIRECTORY = CompatibilityTestResources
			.resourceDirectory(AbstractGawkSuite.class, "gawk");
	protected static final String NON_ZERO_TRANSCRIPT_REASON = "This case compares a non-zero gawk CLI transcript, which is intentionally skipped in the explicit AwkTestSupport.cliTest suite.";
	protected static final String NON_UTF8_STDIN_REASON = "This case redirects stdin that is not valid UTF-8, which is intentionally skipped in the explicit AwkTestSupport.cliTest suite.";
	protected static final String NON_UTF8_EXPECTED_REASON = "This case uses an expected .ok file that is not valid UTF-8, which is intentionally skipped in the explicit AwkTestSupport.cliTest suite.";
	protected static final String MANUAL_SKIP_REASON = "Handwritten gawk case from Makefile.am not yet expressed as an AwkTestSupport.cliTest case.";

	protected static Path gawkPath(String fileName) {
		return GAWK_DIRECTORY.resolve(fileName);
	}

	protected static String gawkFile(String fileName) {
		return gawkPath(fileName).toString();
	}

	protected static String gawkText(String fileName) throws IOException {
		return new String(Files.readAllBytes(gawkPath(fileName)), StandardCharsets.UTF_8);
	}

	protected static void skip(String reason) {
		assumeTrue(reason, false);
	}
}
