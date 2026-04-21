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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for the metadata parser that converts gawk's generated
 * {@code Maketests} rules into Jawk compatibility cases.
 */
public class GawkMaketestsParserTest {

	/**
	 * Verifies that a standard generated AWK rule preserves stdin redirection and
	 * the default expected output file.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseStandardRuleWithStdin() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"Gt-dummy:\n"
						+ "# file Maketests\n\n"
						+ "addcomma:\n"
						+ "\t@echo $@\n"
						+ "\t@-AWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk  < \"$(srcdir)\"/$@.in >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertEquals("addcomma", gawkCase.name());
		assertEquals("awk", gawkCase.scriptMode());
		assertEquals("addcomma.awk", gawkCase.scriptFileName());
		assertEquals("addcomma.in", gawkCase.stdinFileName());
		assertEquals("addcomma.ok", gawkCase.expectedFileName());
		assertFalse(gawkCase.requiresExplicitSkip());
	}

	/**
	 * Verifies that locale directives and supported CLI flags are preserved for
	 * later execution by the Jawk harness.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseLocaleAndSupportedFlags() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"concat4:\n"
						+ "\t@echo $@\n"
						+ "\t@-[ -z \"$$GAWKLOCALE\" ] && GAWKLOCALE=en_US.UTF-8; export GAWKLOCALE; \\\n"
						+ "\tAWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk  --posix >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertEquals("en-US", gawkCase.localeTag());
		assertEquals(1, gawkCase.runnableFlags().size());
		assertEquals("--posix", gawkCase.runnableFlags().get(0));
		assertFalse(gawkCase.requiresExplicitSkip());
	}

	/**
	 * Verifies that the POSIX {@code C} locale does not force a JVM locale
	 * override in the Jawk harness.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseCLocaleAsNoOverride() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"localecase:\n"
						+ "\t@echo $@\n"
						+ "\t@-[ -z \"$$GAWKLOCALE\" ] && GAWKLOCALE=C; export GAWKLOCALE; \\\n"
						+ "\tAWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertNull(gawkCase.localeTag());
	}

	/**
	 * Verifies that shell-script rules are identified as explicit skips because
	 * the Jawk harness does not execute external shell scripts in-process.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseShellRuleAsUnsupported() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"randtest:\n"
						+ "\t@echo $@\n"
						+ "\t@-$(LOCALES) AWK=\"$(AWKPROG)\" \"$(srcdir)\"/$@.sh  > _$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertEquals("sh", gawkCase.scriptMode());
		assertTrue(gawkCase.requiresExplicitSkip());
		assertTrue(gawkCase.unsupportedFlags().isEmpty());
	}

	/**
	 * Verifies that unsupported gawk-only CLI flags are surfaced for explicit
	 * skip coverage.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseUnsupportedFlags() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"csv1:\n"
						+ "\t@echo $@\n"
						+ "\t@-AWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk  --csv < \"$(srcdir)\"/$@.in >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertEquals(1, gawkCase.unsupportedFlags().size());
		assertEquals("--csv", gawkCase.unsupportedFlags().get(0));
		assertTrue(gawkCase.requiresExplicitSkip());
	}

	/**
	 * Verifies that MPFR-specific expected-output switching is retained in the
	 * parsed metadata even when Jawk does not execute the test in bignum mode.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseMpfrExpectedVariant() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"arraytype:\n"
						+ "\t@echo $@\n"
						+ "\t@-AWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk  >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-if echo \"$$GAWK_TEST_ARGS\" | egrep -q -e '-M|--bignum' > /dev/null ; \\\n"
						+ "\tthen $(CMP) \"$(srcdir)\"/$@-mpfr.ok _$@ && rm -f _$@ ; \\\n"
						+ "\telse $(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@ ; fi\n");

		assertTrue(gawkCase.hasMpfrExpectedVariant());
		assertEquals("arraytype.ok", gawkCase.expectedFileName());
	}

	/**
	 * Verifies that gawk CLI flags that Jawk implements implicitly can be parsed
	 * without forcing an explicit skip entry.
	 *
	 * @throws Exception when parsing the sample metadata fails
	 */
	@Test
	public void parseNoOpCompatibilityFlags() throws Exception {
		GawkMaketestsParser.GawkCase gawkCase = parseSingleCase(
				"reint:\n"
						+ "\t@echo $@\n"
						+ "\t@-AWKPATH=\"$(srcdir)\" $(AWK) -f $@.awk  --re-interval < \"$(srcdir)\"/$@.in >_$@ 2>&1 || echo EXIT CODE: $$? >>_$@\n"
						+ "\t@-$(CMP) \"$(srcdir)\"/$@.ok _$@ && rm -f _$@\n");

		assertTrue(gawkCase.flags().contains("--re-interval"));
		assertTrue(gawkCase.unsupportedFlags().isEmpty());
		assertFalse(gawkCase.requiresExplicitSkip());
	}

	private static GawkMaketestsParser.GawkCase parseSingleCase(String maketestsSnippet) throws Exception {
		List<GawkMaketestsParser.GawkCase> cases = GawkMaketestsParser.parse(new StringReader(maketestsSnippet));
		assertEquals(1, cases.size());
		return cases.get(0);
	}
}
