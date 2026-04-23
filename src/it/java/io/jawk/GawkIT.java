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

import org.junit.Test;

/**
 * Core gawk compatibility cases mirrored from the basic and Unix test groups in the vendored gawk suite.
 */
public class GawkIT extends AbstractGawkSuite {

	@Test
	public void test_addcomma() throws Exception {
		AwkTestSupport
				.cliTest("GAWK addcomma")
				.argument("-f", gawkFile("addcomma.awk"))
				.stdin(gawkText("addcomma.in"))
				.expectLines(gawkPath("addcomma.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_anchgsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK anchgsub")
				.argument("-f", gawkFile("anchgsub.awk"))
				.stdin(gawkText("anchgsub.in"))
				.expectLines(gawkPath("anchgsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_anchor() throws Exception {
		AwkTestSupport
				.cliTest("GAWK anchor")
				.argument("-f", gawkFile("anchor.awk"))
				.stdin(gawkText("anchor.in"))
				.expectLines(gawkPath("anchor.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayind1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayind1")
				.argument("-f", gawkFile("arrayind1.awk"))
				.stdin(gawkText("arrayind1.in"))
				.expectLines(gawkPath("arrayind1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayind2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayind2")
				.argument("-f", gawkFile("arrayind2.awk"))
				.expectLines(gawkPath("arrayind2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayind3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayind3")
				.argument("-f", gawkFile("arrayind3.awk"))
				.expectLines(gawkPath("arrayind3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayparm() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_arrayprm2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayprm2")
				.argument("-f", gawkFile("arrayprm2.awk"))
				.expectLines(gawkPath("arrayprm2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayprm3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayprm3")
				.argument("-f", gawkFile("arrayprm3.awk"))
				.expectLines(gawkPath("arrayprm3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrayref() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrayref")
				.argument("-f", gawkFile("arrayref.awk"))
				.expectLines(gawkPath("arrayref.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arrymem1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arrymem1")
				.argument("-f", gawkFile("arrymem1.awk"))
				.expectLines(gawkPath("arrymem1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arryref2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arryref2")
				.argument("-f", gawkFile("arryref2.awk"))
				.expectLines(gawkPath("arryref2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arryref3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_arryref4() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_arryref5() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_arynasty() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arynasty")
				.argument("-f", gawkFile("arynasty.awk"))
				.expectLines(gawkPath("arynasty.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aryprm1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm4() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm5() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm6() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm7() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aryprm8() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aryprm8")
				.argument("-f", gawkFile("aryprm8.awk"))
				.expectLines(gawkPath("aryprm8.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aryprm9() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aryprm9")
				.argument("-f", gawkFile("aryprm9.awk"))
				.expectLines(gawkPath("aryprm9.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arysubnm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arysubnm")
				.argument("-f", gawkFile("arysubnm.awk"))
				.expectLines(gawkPath("arysubnm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aryunasgn() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aryunasgn")
				.argument("-f", gawkFile("aryunasgn.awk"))
				.expectLines(gawkPath("aryunasgn.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_asgext() throws Exception {
		AwkTestSupport
				.cliTest("GAWK asgext")
				.argument("-f", gawkFile("asgext.awk"))
				.stdin(gawkText("asgext.in"))
				.expectLines(gawkPath("asgext.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_assignnumfield() throws Exception {
		AwkTestSupport
				.cliTest("GAWK assignnumfield")
				.argument("-f", gawkFile("assignnumfield.awk"))
				.stdin(gawkText("assignnumfield.in"))
				.expectLines(gawkPath("assignnumfield.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_assignnumfield2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK assignnumfield2")
				.argument("-f", gawkFile("assignnumfield2.awk"))
				.expectLines(gawkPath("assignnumfield2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_back89() throws Exception {
		AwkTestSupport
				.cliTest("GAWK back89")
				.argument("-f", gawkFile("back89.awk"))
				.stdin(gawkText("back89.in"))
				.expectLines(gawkPath("back89.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_backgsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK backgsub")
				.argument("-f", gawkFile("backgsub.awk"))
				.stdin(gawkText("backgsub.in"))
				.expectLines(gawkPath("backgsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_badassign1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_badbuild() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_callparam() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_childin() throws Exception {
		AwkTestSupport
				.cliTest("GAWK childin")
				.argument("-f", gawkFile("childin.awk"))
				.stdin(gawkText("childin.in"))
				.expectLines(gawkPath("childin.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_closebad() throws Exception {
		AwkTestSupport
				.cliTest("GAWK closebad")
				.argument("-f", gawkFile("closebad.awk"))
				.expectLines(gawkPath("closebad.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_close_status() throws Exception {
		AwkTestSupport
				.cliTest("GAWK close_status")
				.argument("-f", gawkFile("close_status.awk"))
				.expectLines(gawkPath("close_status.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_clsflnam() throws Exception {
		AwkTestSupport
				.cliTest("GAWK clsflnam")
				.argument("-f", gawkFile("clsflnam.awk"))
				.stdin(gawkText("clsflnam.in"))
				.expectLines(gawkPath("clsflnam.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_compare2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK compare2")
				.argument("-f", gawkFile("compare2.awk"))
				.expectLines(gawkPath("compare2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_concat1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK concat1")
				.argument("-f", gawkFile("concat1.awk"))
				.stdin(gawkText("concat1.in"))
				.expectLines(gawkPath("concat1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_concat2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK concat2")
				.argument("-f", gawkFile("concat2.awk"))
				.expectLines(gawkPath("concat2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_cmdlinefsbacknl() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_concat3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK concat3")
				.argument("-f", gawkFile("concat3.awk"))
				.expectLines(gawkPath("concat3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_concat4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK concat4")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("concat4.awk"))
				.stdin(gawkText("concat4.in"))
				.expectLines(gawkPath("concat4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_concat5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK concat5")
				.argument("-f", gawkFile("concat5.awk"))
				.expectLines(gawkPath("concat5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_convfmt() throws Exception {
		AwkTestSupport
				.cliTest("GAWK convfmt")
				.argument("-f", gawkFile("convfmt.awk"))
				.expectLines(gawkPath("convfmt.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_datanonl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK datanonl")
				.argument("-f", gawkFile("datanonl.awk"))
				.stdin(gawkText("datanonl.in"))
				.expectLines(gawkPath("datanonl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_delargv() throws Exception {
		AwkTestSupport
				.cliTest("GAWK delargv")
				.argument("-f", gawkFile("delargv.awk"))
				.expectLines(gawkPath("delargv.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_delarpm2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK delarpm2")
				.argument("-f", gawkFile("delarpm2.awk"))
				.expectLines(gawkPath("delarpm2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_delarprm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK delarprm")
				.argument("-f", gawkFile("delarprm.awk"))
				.expectLines(gawkPath("delarprm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_delfunc() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_dfacheck2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK dfacheck2")
				.argument("-f", gawkFile("dfacheck2.awk"))
				.stdin(gawkText("dfacheck2.in"))
				.expectLines(gawkPath("dfacheck2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_dfamb1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK dfamb1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("dfamb1.awk"))
				.stdin(gawkText("dfamb1.in"))
				.expectLines(gawkPath("dfamb1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_dfastress() throws Exception {
		AwkTestSupport
				.cliTest("GAWK dfastress")
				.argument("-f", gawkFile("dfastress.awk"))
				.expectLines(gawkPath("dfastress.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_divzero() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_divzero2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK divzero2")
				.argument("-f", gawkFile("divzero2.awk"))
				.expectLines(gawkPath("divzero2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_dynlj() throws Exception {
		AwkTestSupport
				.cliTest("GAWK dynlj")
				.argument("-f", gawkFile("dynlj.awk"))
				.expectLines(gawkPath("dynlj.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_eofsplit() throws Exception {
		AwkTestSupport
				.cliTest("GAWK eofsplit")
				.argument("-f", gawkFile("eofsplit.awk"))
				.expectLines(gawkPath("eofsplit.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_exit2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK exit2")
				.argument("-f", gawkFile("exit2.awk"))
				.expectLines(gawkPath("exit2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_exitval2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK exitval2")
				.argument("-f", gawkFile("exitval2.awk"))
				.expectLines(gawkPath("exitval2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_exitval3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fcall_exit() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fcall_exit2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fieldassign() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fieldassign")
				.argument("-f", gawkFile("fieldassign.awk"))
				.stdin(gawkText("fieldassign.in"))
				.expectLines(gawkPath("fieldassign.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fldchg() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fldchg")
				.argument("-f", gawkFile("fldchg.awk"))
				.stdin(gawkText("fldchg.in"))
				.expectLines(gawkPath("fldchg.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fldchgnf() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fldchgnf")
				.argument("-f", gawkFile("fldchgnf.awk"))
				.stdin(gawkText("fldchgnf.in"))
				.expectLines(gawkPath("fldchgnf.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fldterm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fldterm")
				.argument("-f", gawkFile("fldterm.awk"))
				.stdin(gawkText("fldterm.in"))
				.expectLines(gawkPath("fldterm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fnamedat() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fnarray() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fnarray2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fnaryscl() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fnasgnm() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fnmisc() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fordel() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fordel")
				.argument("-f", gawkFile("fordel.awk"))
				.expectLines(gawkPath("fordel.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_forref() throws Exception {
		AwkTestSupport
				.cliTest("GAWK forref")
				.argument("-f", gawkFile("forref.awk"))
				.expectLines(gawkPath("forref.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_forsimp() throws Exception {
		AwkTestSupport
				.cliTest("GAWK forsimp")
				.argument("-f", gawkFile("forsimp.awk"))
				.expectLines(gawkPath("forsimp.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fsbs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fsbs")
				.argument("-f", gawkFile("fsbs.awk"))
				.stdin(gawkText("fsbs.in"))
				.expectLines(gawkPath("fsbs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fscaret() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fscaret")
				.argument("-f", gawkFile("fscaret.awk"))
				.stdin(gawkText("fscaret.in"))
				.expectLines(gawkPath("fscaret.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fsnul1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fsnul1")
				.argument("-f", gawkFile("fsnul1.awk"))
				.stdin(gawkText("fsnul1.in"))
				.expectLines(gawkPath("fsnul1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fsrs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fsrs")
				.argument("-f", gawkFile("fsrs.awk"))
				.stdin(gawkText("fsrs.in"))
				.expectLines(gawkPath("fsrs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fstabplus() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fstabplus")
				.argument("-f", gawkFile("fstabplus.awk"))
				.stdin(gawkText("fstabplus.in"))
				.expectLines(gawkPath("fstabplus.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_funsemnl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK funsemnl")
				.argument("-f", gawkFile("funsemnl.awk"))
				.expectLines(gawkPath("funsemnl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_funsmnam() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_funstack() throws Exception {
		AwkTestSupport
				.cliTest("GAWK funstack")
				.argument("-f", gawkFile("funstack.awk"))
				.stdin(gawkText("funstack.in"))
				.expectLines(gawkPath("funstack.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getline() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getline")
				.argument("-f", gawkFile("getline.awk"))
				.stdin(gawkText("getline.in"))
				.expectLines(gawkPath("getline.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getline3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getline3")
				.argument("-f", gawkFile("getline3.awk"))
				.expectLines(gawkPath("getline3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getline4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getline4")
				.argument("-f", gawkFile("getline4.awk"))
				.stdin(gawkText("getline4.in"))
				.expectLines(gawkPath("getline4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getline5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getline5")
				.argument("-f", gawkFile("getline5.awk"))
				.expectLines(gawkPath("getline5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getlnfa() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_getnr2tb() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getnr2tb")
				.argument("-f", gawkFile("getnr2tb.awk"))
				.stdin(gawkText("getnr2tb.in"))
				.expectLines(gawkPath("getnr2tb.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getnr2tm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getnr2tm")
				.argument("-f", gawkFile("getnr2tm.awk"))
				.stdin(gawkText("getnr2tm.in"))
				.expectLines(gawkPath("getnr2tm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubasgn() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_gsubtest() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtest")
				.argument("-f", gawkFile("gsubtest.awk"))
				.expectLines(gawkPath("gsubtest.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst2")
				.argument("-f", gawkFile("gsubtst2.awk"))
				.expectLines(gawkPath("gsubtst2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst4")
				.argument("-f", gawkFile("gsubtst4.awk"))
				.expectLines(gawkPath("gsubtst4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst5")
				.argument("-f", gawkFile("gsubtst5.awk"))
				.stdin(gawkText("gsubtst5.in"))
				.expectLines(gawkPath("gsubtst5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst6")
				.argument("-f", gawkFile("gsubtst6.awk"))
				.expectLines(gawkPath("gsubtst6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst7() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst7")
				.argument("-f", gawkFile("gsubtst7.awk"))
				.stdin(gawkText("gsubtst7.in"))
				.expectLines(gawkPath("gsubtst7.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst8() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst8")
				.argument("-f", gawkFile("gsubtst8.awk"))
				.stdin(gawkText("gsubtst8.in"))
				.expectLines(gawkPath("gsubtst8.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_hex() throws Exception {
		AwkTestSupport
				.cliTest("GAWK hex")
				.argument("-f", gawkFile("hex.awk"))
				.expectLines(gawkPath("hex.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_hex2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK hex2")
				.argument("-f", gawkFile("hex2.awk"))
				.stdin(gawkText("hex2.in"))
				.expectLines(gawkPath("hex2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_hsprint() throws Exception {
		AwkTestSupport
				.cliTest("GAWK hsprint")
				.argument("-f", gawkFile("hsprint.awk"))
				.expectLines(gawkPath("hsprint.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_inpref() throws Exception {
		AwkTestSupport
				.cliTest("GAWK inpref")
				.argument("-f", gawkFile("inpref.awk"))
				.stdin(gawkText("inpref.in"))
				.expectLines(gawkPath("inpref.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_inputred() throws Exception {
		AwkTestSupport
				.cliTest("GAWK inputred")
				.argument("-f", gawkFile("inputred.awk"))
				.expectLines(gawkPath("inputred.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_intest() throws Exception {
		AwkTestSupport
				.cliTest("GAWK intest")
				.argument("-f", gawkFile("intest.awk"))
				.expectLines(gawkPath("intest.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_intprec() throws Exception {
		AwkTestSupport
				.cliTest("GAWK intprec")
				.argument("-f", gawkFile("intprec.awk"))
				.expectLines(gawkPath("intprec.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_iobug1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK iobug1")
				.argument("-f", gawkFile("iobug1.awk"))
				.expectLines(gawkPath("iobug1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_leaddig() throws Exception {
		AwkTestSupport
				.cliTest("GAWK leaddig")
				.argument("-f", gawkFile("leaddig.awk"))
				.expectLines(gawkPath("leaddig.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_leadnl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK leadnl")
				.argument("-f", gawkFile("leadnl.awk"))
				.stdin(gawkText("leadnl.in"))
				.expectLines(gawkPath("leadnl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_longsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK longsub")
				.argument("-f", gawkFile("longsub.awk"))
				.stdin(gawkText("longsub.in"))
				.expectLines(gawkPath("longsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_manglprm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK manglprm")
				.argument("-f", gawkFile("manglprm.awk"))
				.stdin(gawkText("manglprm.in"))
				.expectLines(gawkPath("manglprm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_math() throws Exception {
		AwkTestSupport
				.cliTest("GAWK math")
				.argument("-f", gawkFile("math.awk"))
				.expectLines(gawkPath("math.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_membug1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK membug1")
				.argument("-f", gawkFile("membug1.awk"))
				.stdin(gawkText("membug1.in"))
				.expectLines(gawkPath("membug1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_memleak() throws Exception {
		AwkTestSupport
				.cliTest("GAWK memleak")
				.argument("-f", gawkFile("memleak.awk"))
				.expectLines(gawkPath("memleak.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_minusstr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK minusstr")
				.argument("-f", gawkFile("minusstr.awk"))
				.expectLines(gawkPath("minusstr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mmap8k() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mmap8k")
				.argument("-f", gawkFile("mmap8k.awk"))
				.stdin(gawkText("mmap8k.in"))
				.expectLines(gawkPath("mmap8k.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nasty() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nasty")
				.argument("-f", gawkFile("nasty.awk"))
				.expectLines(gawkPath("nasty.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nasty2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nasty2")
				.argument("-f", gawkFile("nasty2.awk"))
				.expectLines(gawkPath("nasty2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_negexp() throws Exception {
		AwkTestSupport
				.cliTest("GAWK negexp")
				.argument("-f", gawkFile("negexp.awk"))
				.expectLines(gawkPath("negexp.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_negrange() throws Exception {
		AwkTestSupport
				.cliTest("GAWK negrange")
				.argument("-f", gawkFile("negrange.awk"))
				.expectLines(gawkPath("negrange.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nested() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nested")
				.argument("-f", gawkFile("nested.awk"))
				.stdin(gawkText("nested.in"))
				.expectLines(gawkPath("nested.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nfldstr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nfldstr")
				.argument("-f", gawkFile("nfldstr.awk"))
				.stdin(gawkText("nfldstr.in"))
				.expectLines(gawkPath("nfldstr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nfloop() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nfloop")
				.argument("-f", gawkFile("nfloop.awk"))
				.expectLines(gawkPath("nfloop.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nfneg() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nfset() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nfset")
				.argument("-f", gawkFile("nfset.awk"))
				.stdin(gawkText("nfset.in"))
				.expectLines(gawkPath("nfset.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nlfldsep() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nlfldsep")
				.argument("-f", gawkFile("nlfldsep.awk"))
				.stdin(gawkText("nlfldsep.in"))
				.expectLines(gawkPath("nlfldsep.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nlinstr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nlinstr")
				.argument("-f", gawkFile("nlinstr.awk"))
				.stdin(gawkText("nlinstr.in"))
				.expectLines(gawkPath("nlinstr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nlstrina() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nlstrina")
				.argument("-f", gawkFile("nlstrina.awk"))
				.expectLines(gawkPath("nlstrina.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_noloop1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK noloop1")
				.argument("-f", gawkFile("noloop1.awk"))
				.stdin(gawkText("noloop1.in"))
				.expectLines(gawkPath("noloop1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_noloop2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK noloop2")
				.argument("-f", gawkFile("noloop2.awk"))
				.stdin(gawkText("noloop2.in"))
				.expectLines(gawkPath("noloop2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_noparms() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nulinsrc() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nulrsend() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nulrsend")
				.argument("-f", gawkFile("nulrsend.awk"))
				.stdin(gawkText("nulrsend.in"))
				.expectLines(gawkPath("nulrsend.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_numindex() throws Exception {
		AwkTestSupport
				.cliTest("GAWK numindex")
				.argument("-f", gawkFile("numindex.awk"))
				.stdin(gawkText("numindex.in"))
				.expectLines(gawkPath("numindex.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_numrange() throws Exception {
		AwkTestSupport
				.cliTest("GAWK numrange")
				.argument("-f", gawkFile("numrange.awk"))
				.expectLines(gawkPath("numrange.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_numstr1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK numstr1")
				.argument("-f", gawkFile("numstr1.awk"))
				.expectLines(gawkPath("numstr1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_numsubstr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK numsubstr")
				.argument("-f", gawkFile("numsubstr.awk"))
				.stdin(gawkText("numsubstr.in"))
				.expectLines(gawkPath("numsubstr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_octsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK octsub")
				.argument("-f", gawkFile("octsub.awk"))
				.expectLines(gawkPath("octsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmt() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmt")
				.argument("-f", gawkFile("ofmt.awk"))
				.stdin(gawkText("ofmt.in"))
				.expectLines(gawkPath("ofmt.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmta() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmta")
				.argument("-f", gawkFile("ofmta.awk"))
				.expectLines(gawkPath("ofmta.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmtbig() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmtbig")
				.argument("-f", gawkFile("ofmtbig.awk"))
				.stdin(gawkText("ofmtbig.in"))
				.expectLines(gawkPath("ofmtbig.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmtfidl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmtfidl")
				.argument("-f", gawkFile("ofmtfidl.awk"))
				.stdin(gawkText("ofmtfidl.in"))
				.expectLines(gawkPath("ofmtfidl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmts() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmts")
				.argument("-f", gawkFile("ofmts.awk"))
				.stdin(gawkText("ofmts.in"))
				.expectLines(gawkPath("ofmts.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofmtstrnum() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofmtstrnum")
				.argument("-f", gawkFile("ofmtstrnum.awk"))
				.expectLines(gawkPath("ofmtstrnum.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ofs1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ofs1")
				.argument("-f", gawkFile("ofs1.awk"))
				.stdin(gawkText("ofs1.in"))
				.expectLines(gawkPath("ofs1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_onlynl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK onlynl")
				.argument("-f", gawkFile("onlynl.awk"))
				.stdin(gawkText("onlynl.in"))
				.expectLines(gawkPath("onlynl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_opasnidx() throws Exception {
		AwkTestSupport
				.cliTest("GAWK opasnidx")
				.argument("-f", gawkFile("opasnidx.awk"))
				.expectLines(gawkPath("opasnidx.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_opasnslf() throws Exception {
		AwkTestSupport
				.cliTest("GAWK opasnslf")
				.argument("-f", gawkFile("opasnslf.awk"))
				.expectLines(gawkPath("opasnslf.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_paramdup() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_paramres() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_paramtyp() throws Exception {
		AwkTestSupport
				.cliTest("GAWK paramtyp")
				.argument("-f", gawkFile("paramtyp.awk"))
				.expectLines(gawkPath("paramtyp.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_paramuninitglobal() throws Exception {
		AwkTestSupport
				.cliTest("GAWK paramuninitglobal")
				.argument("-f", gawkFile("paramuninitglobal.awk"))
				.expectLines(gawkPath("paramuninitglobal.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_parse1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK parse1")
				.argument("-f", gawkFile("parse1.awk"))
				.stdin(gawkText("parse1.in"))
				.expectLines(gawkPath("parse1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_parsefld() throws Exception {
		AwkTestSupport
				.cliTest("GAWK parsefld")
				.argument("-f", gawkFile("parsefld.awk"))
				.stdin(gawkText("parsefld.in"))
				.expectLines(gawkPath("parsefld.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_parseme() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_pcntplus() throws Exception {
		AwkTestSupport
				.cliTest("GAWK pcntplus")
				.argument("-f", gawkFile("pcntplus.awk"))
				.expectLines(gawkPath("pcntplus.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_prdupval() throws Exception {
		AwkTestSupport
				.cliTest("GAWK prdupval")
				.argument("-f", gawkFile("prdupval.awk"))
				.stdin(gawkText("prdupval.in"))
				.expectLines(gawkPath("prdupval.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_prec() throws Exception {
		AwkTestSupport
				.cliTest("GAWK prec")
				.argument("-f", gawkFile("prec.awk"))
				.expectLines(gawkPath("prec.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_printf1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK printf1")
				.argument("-f", gawkFile("printf1.awk"))
				.expectLines(gawkPath("printf1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_printfchar() throws Exception {
		AwkTestSupport
				.cliTest("GAWK printfchar")
				.argument("-f", gawkFile("printfchar.awk"))
				.expectLines(gawkPath("printfchar.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_prmarscl() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_prmreuse() throws Exception {
		AwkTestSupport
				.cliTest("GAWK prmreuse")
				.argument("-f", gawkFile("prmreuse.awk"))
				.expectLines(gawkPath("prmreuse.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_prt1eval() throws Exception {
		AwkTestSupport
				.cliTest("GAWK prt1eval")
				.argument("-f", gawkFile("prt1eval.awk"))
				.expectLines(gawkPath("prt1eval.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_prtoeval() throws Exception {
		AwkTestSupport
				.cliTest("GAWK prtoeval")
				.argument("-f", gawkFile("prtoeval.awk"))
				.expectLines(gawkPath("prtoeval.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rand() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rand")
				.argument("-f", gawkFile("rand.awk"))
				.expectLines(gawkPath("rand.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_randtest() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_range1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK range1")
				.argument("-f", gawkFile("range1.awk"))
				.stdin(gawkText("range1.in"))
				.expectLines(gawkPath("range1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_range2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK range2")
				.argument("-f", gawkFile("range2.awk"))
				.expectLines(gawkPath("range2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_readbuf() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_rebrackloc() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rebrackloc")
				.argument("-f", gawkFile("rebrackloc.awk"))
				.stdin(gawkText("rebrackloc.in"))
				.expectLines(gawkPath("rebrackloc.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rebt8b1() throws Exception {
		skip(NON_UTF8_EXPECTED_REASON);
	}

	@Test
	public void test_rebuild() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rebuild")
				.argument("-f", gawkFile("rebuild.awk"))
				.stdin(gawkText("rebuild.in"))
				.expectLines(gawkPath("rebuild.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regeq() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regeq")
				.argument("-f", gawkFile("regeq.awk"))
				.stdin(gawkText("regeq.in"))
				.expectLines(gawkPath("regeq.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regex3minus() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regex3minus")
				.argument("-f", gawkFile("regex3minus.awk"))
				.expectLines(gawkPath("regex3minus.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regexpbad() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_regexpbrack() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regexpbrack")
				.argument("-f", gawkFile("regexpbrack.awk"))
				.stdin(gawkText("regexpbrack.in"))
				.expectLines(gawkPath("regexpbrack.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regexpbrack2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regexpbrack2")
				.argument("-f", gawkFile("regexpbrack2.awk"))
				.stdin(gawkText("regexpbrack2.in"))
				.expectLines(gawkPath("regexpbrack2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regexprange() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regexprange")
				.argument("-f", gawkFile("regexprange.awk"))
				.expectLines(gawkPath("regexprange.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regrange() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regrange")
				.argument("-f", gawkFile("regrange.awk"))
				.expectLines(gawkPath("regrange.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_reindops() throws Exception {
		AwkTestSupport
				.cliTest("GAWK reindops")
				.argument("-f", gawkFile("reindops.awk"))
				.stdin(gawkText("reindops.in"))
				.expectLines(gawkPath("reindops.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_reparse() throws Exception {
		AwkTestSupport
				.cliTest("GAWK reparse")
				.argument("-f", gawkFile("reparse.awk"))
				.stdin(gawkText("reparse.in"))
				.expectLines(gawkPath("reparse.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_resplit() throws Exception {
		AwkTestSupport
				.cliTest("GAWK resplit")
				.argument("-f", gawkFile("resplit.awk"))
				.stdin(gawkText("resplit.in"))
				.expectLines(gawkPath("resplit.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rri1() throws Exception {
		skip(NON_UTF8_STDIN_REASON);
	}

	@Test
	public void test_rs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rs")
				.argument("-f", gawkFile("rs.awk"))
				.stdin(gawkText("rs.in"))
				.expectLines(gawkPath("rs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rsnul1nl() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsnul1nl")
				.argument("-f", gawkFile("rsnul1nl.awk"))
				.stdin(gawkText("rsnul1nl.in"))
				.expectLines(gawkPath("rsnul1nl.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rsnullre() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsnullre")
				.argument("-f", gawkFile("rsnullre.awk"))
				.stdin(gawkText("rsnullre.in"))
				.expectLines(gawkPath("rsnullre.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rsnulw() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsnulw")
				.argument("-f", gawkFile("rsnulw.awk"))
				.stdin(gawkText("rsnulw.in"))
				.expectLines(gawkPath("rsnulw.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest1")
				.argument("-f", gawkFile("rstest1.awk"))
				.expectLines(gawkPath("rstest1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest2")
				.argument("-f", gawkFile("rstest2.awk"))
				.expectLines(gawkPath("rstest2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest3")
				.argument("-f", gawkFile("rstest3.awk"))
				.expectLines(gawkPath("rstest3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest4")
				.argument("-f", gawkFile("rstest4.awk"))
				.expectLines(gawkPath("rstest4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest5")
				.argument("-f", gawkFile("rstest5.awk"))
				.expectLines(gawkPath("rstest5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rswhite() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rswhite")
				.argument("-f", gawkFile("rswhite.awk"))
				.stdin(gawkText("rswhite.in"))
				.expectLines(gawkPath("rswhite.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_scalar() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_sclforin() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_sclifin() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_setrec0() throws Exception {
		AwkTestSupport
				.cliTest("GAWK setrec0")
				.argument("-f", gawkFile("setrec0.awk"))
				.stdin(gawkText("setrec0.in"))
				.expectLines(gawkPath("setrec0.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_setrec1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK setrec1")
				.argument("-f", gawkFile("setrec1.awk"))
				.expectLines(gawkPath("setrec1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sigpipe1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sigpipe1")
				.argument("-f", gawkFile("sigpipe1.awk"))
				.expectLines(gawkPath("sigpipe1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sortempty() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sortempty")
				.argument("-f", gawkFile("sortempty.awk"))
				.expectLines(gawkPath("sortempty.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sortglos() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sortglos")
				.argument("-f", gawkFile("sortglos.awk"))
				.stdin(gawkText("sortglos.in"))
				.expectLines(gawkPath("sortglos.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitargv() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitargv")
				.argument("-f", gawkFile("splitargv.awk"))
				.stdin(gawkText("splitargv.in"))
				.expectLines(gawkPath("splitargv.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitarr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitarr")
				.argument("-f", gawkFile("splitarr.awk"))
				.expectLines(gawkPath("splitarr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitdef() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitdef")
				.argument("-f", gawkFile("splitdef.awk"))
				.expectLines(gawkPath("splitdef.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitvar() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitvar")
				.argument("-f", gawkFile("splitvar.awk"))
				.stdin(gawkText("splitvar.in"))
				.expectLines(gawkPath("splitvar.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitwht() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitwht")
				.argument("-f", gawkFile("splitwht.awk"))
				.expectLines(gawkPath("splitwht.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strcat1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strcat1")
				.argument("-f", gawkFile("strcat1.awk"))
				.expectLines(gawkPath("strcat1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strfieldnum() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strfieldnum")
				.argument("-f", gawkFile("strfieldnum.awk"))
				.stdin(gawkText("strfieldnum.in"))
				.expectLines(gawkPath("strfieldnum.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strnum1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strnum1")
				.argument("-f", gawkFile("strnum1.awk"))
				.expectLines(gawkPath("strnum1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strnum2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strnum2")
				.argument("-f", gawkFile("strnum2.awk"))
				.expectLines(gawkPath("strnum2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strtod() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strtod")
				.argument("-f", gawkFile("strtod.awk"))
				.stdin(gawkText("strtod.in"))
				.expectLines(gawkPath("strtod.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_subamp() throws Exception {
		AwkTestSupport
				.cliTest("GAWK subamp")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("subamp.awk"))
				.stdin(gawkText("subamp.in"))
				.expectLines(gawkPath("subamp.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_subback() throws Exception {
		AwkTestSupport
				.cliTest("GAWK subback")
				.argument("-f", gawkFile("subback.awk"))
				.stdin(gawkText("subback.in"))
				.expectLines(gawkPath("subback.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_subi18n() throws Exception {
		AwkTestSupport
				.cliTest("GAWK subi18n")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("subi18n.awk"))
				.expectLines(gawkPath("subi18n.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_subsepnm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK subsepnm")
				.argument("-f", gawkFile("subsepnm.awk"))
				.expectLines(gawkPath("subsepnm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_subslash() throws Exception {
		AwkTestSupport
				.cliTest("GAWK subslash")
				.argument("-f", gawkFile("subslash.awk"))
				.expectLines(gawkPath("subslash.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_substr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK substr")
				.argument("-f", gawkFile("substr.awk"))
				.expectLines(gawkPath("substr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_swaplns() throws Exception {
		AwkTestSupport
				.cliTest("GAWK swaplns")
				.argument("-f", gawkFile("swaplns.awk"))
				.stdin(gawkText("swaplns.in"))
				.expectLines(gawkPath("swaplns.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_synerr1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_synerr2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_synerr3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_tailrecurse() throws Exception {
		AwkTestSupport
				.cliTest("GAWK tailrecurse")
				.argument("-f", gawkFile("tailrecurse.awk"))
				.expectLines(gawkPath("tailrecurse.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_trailbs() throws Exception {
		skip(NON_UTF8_EXPECTED_REASON);
	}

	@Test
	public void test_unterm() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_uparrfs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK uparrfs")
				.argument("-f", gawkFile("uparrfs.awk"))
				.stdin(gawkText("uparrfs.in"))
				.expectLines(gawkPath("uparrfs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_uplus() throws Exception {
		AwkTestSupport
				.cliTest("GAWK uplus")
				.argument("-f", gawkFile("uplus.awk"))
				.expectLines(gawkPath("uplus.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_wideidx() throws Exception {
		AwkTestSupport
				.cliTest("GAWK wideidx")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("wideidx.awk"))
				.stdin(gawkText("wideidx.in"))
				.expectLines(gawkPath("wideidx.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_wideidx2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK wideidx2")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("wideidx2.awk"))
				.expectLines(gawkPath("wideidx2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_widesub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK widesub")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("widesub.awk"))
				.expectLines(gawkPath("widesub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_widesub2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK widesub2")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("widesub2.awk"))
				.expectLines(gawkPath("widesub2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_widesub3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK widesub3")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("widesub3.awk"))
				.stdin(gawkText("widesub3.in"))
				.expectLines(gawkPath("widesub3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_widesub4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK widesub4")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("widesub4.awk"))
				.expectLines(gawkPath("widesub4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_wjposer1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK wjposer1")
				.argument("-f", gawkFile("wjposer1.awk"))
				.stdin(gawkText("wjposer1.in"))
				.expectLines(gawkPath("wjposer1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_zero2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK zero2")
				.argument("-f", gawkFile("zero2.awk"))
				.expectLines(gawkPath("zero2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_zeroe0() throws Exception {
		AwkTestSupport
				.cliTest("GAWK zeroe0")
				.argument("-f", gawkFile("zeroe0.awk"))
				.expectLines(gawkPath("zeroe0.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_zeroflag() throws Exception {
		AwkTestSupport
				.cliTest("GAWK zeroflag")
				.argument("-f", gawkFile("zeroflag.awk"))
				.expectLines(gawkPath("zeroflag.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fflush() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_getlnhd() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getlnhd")
				.argument("-f", gawkFile("getlnhd.awk"))
				.expectLines(gawkPath("getlnhd.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_localenl() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_rtlen() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_rtlen01() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_poundbang() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_messages() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_argarray() throws Exception {
		AwkTestSupport
				.cliTest("GAWK argarray")
				.argument("-f", gawkFile("argarray.awk"))
				.stdin("just a test\n")
				.operand(gawkFile("argarray.in"), "-")
				.postProcessWith(output -> output.replace(gawkFile("argarray.in"), "./argarray.input"))
				.expectLines(gawkPath("argarray.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regtest() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_compare() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inftest() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_getline2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_awkpath() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_tweakfld() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_pid() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_strftlng() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nors() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_pipeio1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_pipeio2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_clobber() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_arynocls() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_getlnbuf() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inetechu() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inetecht() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inetdayu() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inetdayt() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_redfilnm() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_space() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rsnulbig() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rsnulbig2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_exitval1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_fsspcoln() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nofile() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_ignrcas3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_eofsrc1() throws Exception {
		skip(
				"This handwritten case compares gawk parser diagnostics on stderr and is intentionally skipped in the explicit AwkTestSupport.cliTest suite.");
	}

	@Test
	public void test_longwrds() throws Exception {
		AwkTestSupport
				.cliTest("GAWK longwrds")
				.argument("-v", "SORT=sort")
				.argument("-f", gawkFile("longwrds.awk"))
				.stdin(gawkText("longwrds.in"))
				.expectLines(gawkPath("longwrds.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_argcasfile() throws Exception {
		AwkTestSupport
				.cliTest("GAWK argcasfile")
				.argument("-f", gawkFile("argcasfile.awk"))
				.stdin(gawkText("argcasfile.in"))
				.operand("ARGC=1", " /no/such/file")
				.expectLines(gawkPath("argcasfile.ok"))
				.expectExit(0)
				.runAndAssert();
	}

}
