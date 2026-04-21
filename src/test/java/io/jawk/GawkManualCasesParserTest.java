package io.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright 2006 - 2026 MetricsHub
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
import static org.junit.Assert.assertFalse;

import java.io.StringReader;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for the curated manifest of handwritten gawk compatibility cases.
 */
public class GawkManualCasesParserTest {

	/**
	 * Verifies that the handwritten manifest preserves raw CLI arguments, staged
	 * path operands, and leading whitespace in literal operands.
	 *
	 * @throws Exception when parsing the manifest snippet fails
	 */
	@Test
	public void parseManualCaseWithArgumentsAndOperands() throws Exception {
		GawkCompatibilityCase gawkCase = parseSingleCase(
				"cases = manualcase\n"
						+ "manualcase.arguments = -v|awk::I=fine\n"
						+ "manualcase.scripts = first.awk|second.awk\n"
						+ "manualcase.operands = @path:first.awk| /no/such/file\n"
						+ "manualcase.stdin = data.in\n"
						+ "manualcase.expected = data.ok\n");

		assertEquals("manualcase", gawkCase.name());
		assertEquals(2, gawkCase.arguments().size());
		assertEquals("-v", gawkCase.arguments().get(0));
		assertEquals("awk::I=fine", gawkCase.arguments().get(1));
		assertEquals(2, gawkCase.scriptFileNames().size());
		assertEquals("first.awk", gawkCase.scriptFileNames().get(0));
		assertEquals("second.awk", gawkCase.scriptFileNames().get(1));
		assertEquals(2, gawkCase.operands().size());
		assertEquals("@path:first.awk", gawkCase.operands().get(0));
		assertEquals(" /no/such/file", gawkCase.operands().get(1));
		assertEquals("data.in", gawkCase.stdinFileName());
		assertEquals("data.ok", gawkCase.expectedFileName());
		assertFalse(gawkCase.requiresExplicitSkip());
	}

	/**
	 * Verifies that the handwritten manifest rejects cases that do not declare a
	 * comparison target.
	 *
	 * @throws Exception when parsing unexpectedly succeeds
	 */
	@Test(expected = IllegalArgumentException.class)
	public void rejectMissingExpectedFile() throws Exception {
		GawkManualCasesParser
				.parse(
						new StringReader(
								"cases = broken\n"
										+ "broken.scripts = broken.awk\n"));
	}

	private static GawkCompatibilityCase parseSingleCase(String manifestSnippet) throws Exception {
		List<GawkCompatibilityCase> cases = GawkManualCasesParser.parse(new StringReader(manifestSnippet));
		assertEquals(1, cases.size());
		return cases.get(0);
	}
}
