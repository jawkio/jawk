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
 * Optional-feature and environment-specific gawk compatibility cases mirrored
 * from the vendored GNU Awk optional test groups.
 * Upstream source: {@code git://git.savannah.gnu.org/gawk.git}
 */
public class GawkOptionalFeatureIT extends AbstractGawkSuite {

	@Test
	public void test_defref() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_escapebrace() throws Exception {
		AwkTestSupport
				.cliTest("GAWK escapebrace")
				.argument("--posix")
				.argument("-f", gawkFile("escapebrace.awk"))
				.stdin(gawkText("escapebrace.in"))
				.expectLines(gawkPath("escapebrace.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubtst3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gsubtst3")
				.argument("-f", gawkFile("gsubtst3.awk"))
				.stdin(gawkText("gsubtst3.in"))
				.expectLines(gawkPath("gsubtst3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_litoct() throws Exception {
		skip("gawk's --traditional mode is not implemented by Jawk.");
	}

	@Test
	public void test_noeffect() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_nofmtch() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_nonl() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_paramasfunc1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_paramasfunc2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_posix2008sub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK posix2008sub")
				.argument("--posix")
				.argument("-f", gawkFile("posix2008sub.awk"))
				.expectLines(gawkPath("posix2008sub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_posix_compare() throws Exception {
		AwkTestSupport
				.cliTest("GAWK posix_compare")
				.argument("--locale", "en-US")
				.argument("--posix")
				.argument("-f", gawkFile("posix_compare.awk"))
				.expectLines(gawkPath("posix_compare.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_printf0() throws Exception {
		AwkTestSupport
				.cliTest("GAWK printf0")
				.argument("--posix")
				.argument("-f", gawkFile("printf0.awk"))
				.expectLines(gawkPath("printf0.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rscompat() throws Exception {
		skip("gawk's --traditional mode is not implemented by Jawk.");
	}

	@Test
	public void test_status_close() throws Exception {
		AwkTestSupport
				.cliTest("GAWK status-close")
				.argument("-f", gawkFile("status-close.awk"))
				.expectLines(gawkPath("status-close.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_tradanch() throws Exception {
		skip("gawk's --traditional mode is not implemented by Jawk.");
	}

	@Test
	public void test_uninit2() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_uninit3() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_uninit4() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_uninit5() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_uninitialized() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_csv1() throws Exception {
		skip("gawk's --csv mode is not implemented by Jawk.");
	}

	@Test
	public void test_csv2() throws Exception {
		skip("gawk's --csv mode is not implemented by Jawk.");
	}

	@Test
	public void test_csv3() throws Exception {
		skip("gawk's --csv mode is not implemented by Jawk.");
	}

	@Test
	public void test_csvodd() throws Exception {
		skip("gawk's --csv mode is not implemented by Jawk.");
	}

	@Test
	public void test_dbugeval2() throws Exception {
		skip("gawk's --debug mode is not implemented by Jawk.");
	}

	@Test
	public void test_dbugeval3() throws Exception {
		skip("gawk's --debug mode is not implemented by Jawk.");
	}

	@Test
	public void test_dbugeval4() throws Exception {
		skip("gawk's --debug mode is not implemented by Jawk.");
	}

	@Test
	public void test_dbugtypedre1() throws Exception {
		skip("gawk's --debug mode is not implemented by Jawk.");
	}

	@Test
	public void test_dbugtypedre2() throws Exception {
		skip("gawk's --debug mode is not implemented by Jawk.");
	}

	@Test
	public void test_forcenum() throws Exception {
		AwkTestSupport
				.cliTest("GAWK forcenum")
				.argument("-f", gawkFile("forcenum.awk"))
				.expectLines(gawkPath("forcenum.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_intarray() throws Exception {
		AwkTestSupport
				.cliTest("GAWK intarray")
				.argument("-f", gawkFile("intarray.awk"))
				.expectLines(gawkPath("intarray.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_lintexp() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintindex() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintint() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintlength() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintold() throws Exception {
		skip("gawk's --lint-old diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintplus() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_lintwarn() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_muldimposix() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nondec2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nondec2")
				.argument("-f", gawkFile("nondec2.awk"))
				.expectLines(gawkPath("nondec2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsprof1() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_nsprof2() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile4() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile8() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile9() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile10() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile11() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile13() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile14() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile15() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile16() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_profile17() throws Exception {
		skip("gawk's --pretty-print mode is not implemented by Jawk.");
	}

	@Test
	public void test_reint() throws Exception {
		AwkTestSupport
				.cliTest("GAWK reint")
				.argument("-f", gawkFile("reint.awk"))
				.stdin(gawkText("reint.in"))
				.expectLines(gawkPath("reint.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_reint2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK reint2")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("reint2.awk"))
				.stdin(gawkText("reint2.in"))
				.expectLines(gawkPath("reint2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sandbox1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_shadow() throws Exception {
		skip("gawk's --lint diagnostics are not implemented by Jawk.");
	}

	@Test
	public void test_double1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK double1")
				.argument("-f", gawkFile("double1.awk"))
				.expectLines(gawkPath("double1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_double2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK double2")
				.argument("-f", gawkFile("double2.awk"))
				.expectLines(gawkPath("double2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_inf_nan_torture() throws Exception {
		AwkTestSupport
				.cliTest("GAWK inf-nan-torture")
				.argument("-f", gawkFile("inf-nan-torture.awk"))
				.stdin(gawkText("inf-nan-torture.in"))
				.expectLines(gawkPath("inf-nan-torture.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_intformat() throws Exception {
		AwkTestSupport
				.cliTest("GAWK intformat")
				.argument("-f", gawkFile("intformat.awk"))
				.expectLines(gawkPath("intformat.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_apiterm() throws Exception {
		AwkTestSupport
				.cliTest("GAWK apiterm")
				.argument("-f", gawkFile("apiterm.awk"))
				.stdin(gawkText("apiterm.in"))
				.expectLines(gawkPath("apiterm.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fnmatch() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fnmatch")
				.argument("-f", gawkFile("fnmatch.awk"))
				.expectLines(gawkPath("fnmatch.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fork() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fork")
				.argument("-f", gawkFile("fork.awk"))
				.expectLines(gawkPath("fork.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fork2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fork2")
				.argument("-f", gawkFile("fork2.awk"))
				.expectLines(gawkPath("fork2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_functab4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK functab4")
				.argument("-f", gawkFile("functab4.awk"))
				.expectLines(gawkPath("functab4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_functab5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK functab5")
				.argument("-f", gawkFile("functab5.awk"))
				.expectLines(gawkPath("functab5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ordchr() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ordchr")
				.argument("-f", gawkFile("ordchr.awk"))
				.expectLines(gawkPath("ordchr.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_revout() throws Exception {
		AwkTestSupport
				.cliTest("GAWK revout")
				.argument("-f", gawkFile("revout.awk"))
				.expectLines(gawkPath("revout.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_revtwoway() throws Exception {
		AwkTestSupport
				.cliTest("GAWK revtwoway")
				.argument("-f", gawkFile("revtwoway.awk"))
				.expectLines(gawkPath("revtwoway.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rwarray() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rwarray")
				.argument("-f", gawkFile("rwarray.awk"))
				.stdin(gawkText("rwarray.in"))
				.expectLines(gawkPath("rwarray.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_time() throws Exception {
		AwkTestSupport
				.cliTest("GAWK time")
				.argument("-f", gawkFile("time.awk"))
				.expectLines(gawkPath("time.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mpfrbigint() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrbigint2() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrcase() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrcase2() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrfield() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrnegzero() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrnegzero2() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrnonum() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrnr() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrrem() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrrndeval() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpfrstrtonum() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_mpgforcenum() throws Exception {
		skip("gawk's -M bignum mode is not implemented by Jawk.");
	}

	@Test
	public void test_pma() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inetmesg() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile5() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrieee() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrexprange() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrrnd() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrsort() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfruplus() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfranswer42() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrmemok1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mpfrsqrt() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_ordchr2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readfile() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readfile2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK readfile2")
				.argument("-f", gawkFile("readfile2.awk"))
				.operand(gawkFile("readfile2.awk"), gawkFile("readdir.awk"))
				.expectLines(gawkPath("readfile2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_inplace1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inplace2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inplace2bcomp() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inplace3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_inplace3bcomp() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_testext() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_getfile() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readdir() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readdir_test() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readdir_retest() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_readall() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_fts() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_filefuncs() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_indirectbuiltin2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

}
