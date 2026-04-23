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

import io.jawk.AwkTestSupport;
import org.junit.Test;

/**
 * Locale- and charset-sensitive gawk compatibility cases mirrored from the
 * vendored GNU Awk locale test groups.
 * Upstream source: {@code git://git.savannah.gnu.org/gawk.git}
 */
public class GawkLocaleIT extends AbstractGawkSuite {

	@Test
	public void test_asort() throws Exception {
		AwkTestSupport
				.cliTest("GAWK asort")
				.argument("-f", gawkFile("asort.awk"))
				.expectLines(gawkPath("asort.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_asorti() throws Exception {
		AwkTestSupport
				.cliTest("GAWK asorti")
				.argument("-f", gawkFile("asorti.awk"))
				.expectLines(gawkPath("asorti.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_backbigs1() throws Exception {
		skip(NON_UTF8_STDIN_REASON);
	}

	@Test
	public void test_backsmalls1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK backsmalls1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("backsmalls1.awk"))
				.stdin(gawkText("backsmalls1.in"))
				.expectLines(gawkPath("backsmalls1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_backsmalls2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK backsmalls2")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("backsmalls2.awk"))
				.expectLines(gawkPath("backsmalls2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fmttest() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fmttest")
				.argument("-f", gawkFile("fmttest.awk"))
				.expectLines(gawkPath("fmttest.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fnarydel() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fnarydel")
				.argument("-f", gawkFile("fnarydel.awk"))
				.expectLines(gawkPath("fnarydel.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fnparydl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fnparydl")
				.argument("-f", gawkFile("fnparydl.awk"))
				.expectLines(gawkPath("fnparydl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_lc_num1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK lc_num1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("lc_num1.awk"))
				.expectLines(gawkPath("lc_num1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbfw1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mbfw1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("mbfw1.awk"))
				.stdin(gawkText("mbfw1.in"))
				.expectLines(gawkPath("mbfw1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbprintf1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mbprintf1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("mbprintf1.awk"))
				.stdin(gawkText("mbprintf1.in"))
				.expectLines(gawkPath("mbprintf1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbprintf2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mbprintf2")
				.argument("--locale", "ja-JP")
				.argument("-f", gawkFile("mbprintf2.awk"))
				.expectLines(gawkPath("mbprintf2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbprintf3() throws Exception {
		skip(NON_UTF8_EXPECTED_REASON);
	}

	@Test
	public void test_mbprintf4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mbprintf4")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("mbprintf4.awk"))
				.stdin(gawkText("mbprintf4.in"))
				.expectLines(gawkPath("mbprintf4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mtchi18n() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mtchi18n")
				.argument("--locale", "ru-RU")
				.argument("-f", gawkFile("mtchi18n.awk"))
				.stdin(gawkText("mtchi18n.in"))
				.expectLines(gawkPath("mtchi18n.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rebt8b2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rebt8b2")
				.argument("-f", gawkFile("rebt8b2.awk"))
				.expectLines(gawkPath("rebt8b2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sort1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sort1")
				.argument("-f", gawkFile("sort1.awk"))
				.expectLines(gawkPath("sort1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sprintfc() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sprintfc")
				.argument("-f", gawkFile("sprintfc.awk"))
				.stdin(gawkText("sprintfc.in"))
				.expectLines(gawkPath("sprintfc.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rtlenmb() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mbprintf5() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_jarebug() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nlstringtest() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_spacere() throws Exception {
		AwkTestSupport
				.cliTest("GAWK spacere")
				.argument("-f", gawkFile("spacere.awk"))
				.expectLines(gawkPath("spacere.ok"))
				.expectExit(0)
				.runAndAssert();
	}

}
