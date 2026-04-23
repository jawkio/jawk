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
 * Extension-oriented gawk compatibility cases mirrored from the vendored
 * extension-style GNU Awk test groups.
 * Upstream source: {@code git://git.savannah.gnu.org/gawk.git}
 */
public class GawkExtensionIT extends AbstractGawkSuite {

	@Test
	public void test_aadelete1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aadelete1")
				.argument("-f", gawkFile("aadelete1.awk"))
				.expectLines(gawkPath("aadelete1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aadelete2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_aarray1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aarray1")
				.argument("-f", gawkFile("aarray1.awk"))
				.expectLines(gawkPath("aarray1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aasort() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aasort")
				.argument("-f", gawkFile("aasort.awk"))
				.expectLines(gawkPath("aasort.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_aasorti() throws Exception {
		AwkTestSupport
				.cliTest("GAWK aasorti")
				.argument("-f", gawkFile("aasorti.awk"))
				.expectLines(gawkPath("aasorti.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arraysort() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arraysort")
				.argument("-f", gawkFile("arraysort.awk"))
				.expectLines(gawkPath("arraysort.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arraysort2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arraysort2")
				.argument("-f", gawkFile("arraysort2.awk"))
				.expectLines(gawkPath("arraysort2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_arraytype() throws Exception {
		AwkTestSupport
				.cliTest("GAWK arraytype")
				.argument("-f", gawkFile("arraytype.awk"))
				.expectLines(gawkPath("arraytype.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_asortbool() throws Exception {
		AwkTestSupport
				.cliTest("GAWK asortbool")
				.argument("-f", gawkFile("asortbool.awk"))
				.expectLines(gawkPath("asortbool.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_backw() throws Exception {
		AwkTestSupport
				.cliTest("GAWK backw")
				.argument("-f", gawkFile("backw.awk"))
				.stdin(gawkText("backw.in"))
				.expectLines(gawkPath("backw.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_asortsymtab() throws Exception {
		AwkTestSupport
				.cliTest("GAWK asortsymtab")
				.argument("-f", gawkFile("asortsymtab.awk"))
				.expectLines(gawkPath("asortsymtab.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_clos1way() throws Exception {
		AwkTestSupport
				.cliTest("GAWK clos1way")
				.argument("-f", gawkFile("clos1way.awk"))
				.expectLines(gawkPath("clos1way.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_clos1way2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_clos1way3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_clos1way4() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_clos1way5() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_clos1way6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK clos1way6")
				.argument("-f", gawkFile("clos1way6.awk"))
				.expectLines(gawkPath("clos1way6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_commas() throws Exception {
		AwkTestSupport
				.cliTest("GAWK commas")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("commas.awk"))
				.expectLines(gawkPath("commas.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_crlf() throws Exception {
		AwkTestSupport
				.cliTest("GAWK crlf")
				.argument("-f", gawkFile("crlf.awk"))
				.expectLines(gawkPath("crlf.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_delsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK delsub")
				.argument("-f", gawkFile("delsub.awk"))
				.expectLines(gawkPath("delsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_dfacheck1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK dfacheck1")
				.argument("-f", gawkFile("dfacheck1.awk"))
				.stdin(gawkText("dfacheck1.in"))
				.expectLines(gawkPath("dfacheck1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_elemnew1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK elemnew1")
				.argument("-f", gawkFile("elemnew1.awk"))
				.expectLines(gawkPath("elemnew1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_elemnew2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK elemnew2")
				.argument("-f", gawkFile("elemnew2.awk"))
				.expectLines(gawkPath("elemnew2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_elemnew3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK elemnew3")
				.argument("-f", gawkFile("elemnew3.awk"))
				.expectLines(gawkPath("elemnew3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_exit() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_fieldwdth() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fieldwdth")
				.argument("-f", gawkFile("fieldwdth.awk"))
				.stdin(gawkText("fieldwdth.in"))
				.expectLines(gawkPath("fieldwdth.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat1")
				.argument("-f", gawkFile("fpat1.awk"))
				.stdin(gawkText("fpat1.in"))
				.expectLines(gawkPath("fpat1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat2")
				.argument("-f", gawkFile("fpat2.awk"))
				.expectLines(gawkPath("fpat2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat3")
				.argument("-f", gawkFile("fpat3.awk"))
				.stdin(gawkText("fpat3.in"))
				.expectLines(gawkPath("fpat3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat4")
				.argument("-f", gawkFile("fpat4.awk"))
				.expectLines(gawkPath("fpat4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat5")
				.argument("-f", gawkFile("fpat5.awk"))
				.stdin(gawkText("fpat5.in"))
				.expectLines(gawkPath("fpat5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat6")
				.argument("-f", gawkFile("fpat6.awk"))
				.stdin(gawkText("fpat6.in"))
				.expectLines(gawkPath("fpat6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat7() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat7")
				.argument("-f", gawkFile("fpat7.awk"))
				.stdin(gawkText("fpat7.in"))
				.expectLines(gawkPath("fpat7.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat8() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat8")
				.argument("-f", gawkFile("fpat8.awk"))
				.stdin(gawkText("fpat8.in"))
				.expectLines(gawkPath("fpat8.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpat9() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpat9")
				.argument("-f", gawkFile("fpat9.awk"))
				.stdin(gawkText("fpat9.in"))
				.expectLines(gawkPath("fpat9.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fpatnull() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fpatnull")
				.argument("-f", gawkFile("fpatnull.awk"))
				.stdin(gawkText("fpatnull.in"))
				.expectLines(gawkPath("fpatnull.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fsfwfs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fsfwfs")
				.argument("-f", gawkFile("fsfwfs.awk"))
				.stdin(gawkText("fsfwfs.in"))
				.expectLines(gawkPath("fsfwfs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_functab1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_functab2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_functab3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK functab3")
				.argument("-f", gawkFile("functab3.awk"))
				.expectLines(gawkPath("functab3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_functab6() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_funlen() throws Exception {
		AwkTestSupport
				.cliTest("GAWK funlen")
				.argument("-f", gawkFile("funlen.awk"))
				.stdin(gawkText("funlen.in"))
				.expectLines(gawkPath("funlen.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest")
				.argument("-f", gawkFile("fwtest.awk"))
				.stdin(gawkText("fwtest.in"))
				.expectLines(gawkPath("fwtest.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest2")
				.argument("-f", gawkFile("fwtest2.awk"))
				.stdin(gawkText("fwtest2.in"))
				.expectLines(gawkPath("fwtest2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest3")
				.argument("-f", gawkFile("fwtest3.awk"))
				.stdin(gawkText("fwtest3.in"))
				.expectLines(gawkPath("fwtest3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest4")
				.argument("-f", gawkFile("fwtest4.awk"))
				.stdin(gawkText("fwtest4.in"))
				.expectLines(gawkPath("fwtest4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest5")
				.argument("-f", gawkFile("fwtest5.awk"))
				.stdin(gawkText("fwtest5.in"))
				.expectLines(gawkPath("fwtest5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest6() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_fwtest7() throws Exception {
		AwkTestSupport
				.cliTest("GAWK fwtest7")
				.argument("-f", gawkFile("fwtest7.awk"))
				.stdin(gawkText("fwtest7.in"))
				.expectLines(gawkPath("fwtest7.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_fwtest8() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_gensub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gensub")
				.argument("-f", gawkFile("gensub.awk"))
				.stdin(gawkText("gensub.in"))
				.expectLines(gawkPath("gensub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gensub2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gensub2")
				.argument("-f", gawkFile("gensub2.awk"))
				.expectLines(gawkPath("gensub2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gensub3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gensub3")
				.argument("-f", gawkFile("gensub3.awk"))
				.stdin(gawkText("gensub3.in"))
				.expectLines(gawkPath("gensub3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gensub4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gensub4")
				.argument("-f", gawkFile("gensub4.awk"))
				.expectLines(gawkPath("gensub4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_getlndir() throws Exception {
		AwkTestSupport
				.cliTest("GAWK getlndir")
				.argument("-f", gawkFile("getlndir.awk"))
				.expectLines(gawkPath("getlndir.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gnuops2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gnuops2")
				.argument("-f", gawkFile("gnuops2.awk"))
				.expectLines(gawkPath("gnuops2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gnuops3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gnuops3")
				.argument("-f", gawkFile("gnuops3.awk"))
				.expectLines(gawkPath("gnuops3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gnureops() throws Exception {
		AwkTestSupport
				.cliTest("GAWK gnureops")
				.argument("-f", gawkFile("gnureops.awk"))
				.expectLines(gawkPath("gnureops.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_gsubind() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_icasefs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK icasefs")
				.argument("-f", gawkFile("icasefs.awk"))
				.expectLines(gawkPath("icasefs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_icasers() throws Exception {
		AwkTestSupport
				.cliTest("GAWK icasers")
				.argument("-f", gawkFile("icasers.awk"))
				.stdin(gawkText("icasers.in"))
				.expectLines(gawkPath("icasers.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_id() throws Exception {
		AwkTestSupport
				.cliTest("GAWK id")
				.argument("-f", gawkFile("id.awk"))
				.expectLines(gawkPath("id.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_igncdym() throws Exception {
		AwkTestSupport
				.cliTest("GAWK igncdym")
				.argument("-f", gawkFile("igncdym.awk"))
				.stdin(gawkText("igncdym.in"))
				.expectLines(gawkPath("igncdym.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_igncfs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK igncfs")
				.argument("-f", gawkFile("igncfs.awk"))
				.stdin(gawkText("igncfs.in"))
				.expectLines(gawkPath("igncfs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ignrcas2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ignrcas2")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("ignrcas2.awk"))
				.expectLines(gawkPath("ignrcas2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ignrcas4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ignrcas4")
				.argument("-f", gawkFile("ignrcas4.awk"))
				.expectLines(gawkPath("ignrcas4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_ignrcase() throws Exception {
		AwkTestSupport
				.cliTest("GAWK ignrcase")
				.argument("-f", gawkFile("ignrcase.awk"))
				.stdin(gawkText("ignrcase.in"))
				.expectLines(gawkPath("ignrcase.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_include() throws Exception {
		AwkTestSupport
				.cliTest("GAWK include")
				.argument("-f", gawkFile("include.awk"))
				.expectLines(gawkPath("include.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_indirectbuiltin() throws Exception {
		AwkTestSupport
				.cliTest("GAWK indirectbuiltin")
				.argument("-f", gawkFile("indirectbuiltin.awk"))
				.expectLines(gawkPath("indirectbuiltin.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_indirectcall() throws Exception {
		AwkTestSupport
				.cliTest("GAWK indirectcall")
				.argument("-f", gawkFile("indirectcall.awk"))
				.stdin(gawkText("indirectcall.in"))
				.expectLines(gawkPath("indirectcall.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_indirectcall2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK indirectcall2")
				.argument("-f", gawkFile("indirectcall2.awk"))
				.expectLines(gawkPath("indirectcall2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_indirectcall3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK indirectcall3")
				.argument("-f", gawkFile("indirectcall3.awk"))
				.expectLines(gawkPath("indirectcall3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_isarrayunset() throws Exception {
		AwkTestSupport
				.cliTest("GAWK isarrayunset")
				.argument("-f", gawkFile("isarrayunset.awk"))
				.expectLines(gawkPath("isarrayunset.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_lint() throws Exception {
		AwkTestSupport
				.cliTest("GAWK lint")
				.argument("-f", gawkFile("lint.awk"))
				.expectLines(gawkPath("lint.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_lintset() throws Exception {
		AwkTestSupport
				.cliTest("GAWK lintset")
				.argument("-f", gawkFile("lintset.awk"))
				.expectLines(gawkPath("lintset.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_match1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK match1")
				.argument("-f", gawkFile("match1.awk"))
				.expectLines(gawkPath("match1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_match2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_match3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK match3")
				.argument("-f", gawkFile("match3.awk"))
				.stdin(gawkText("match3.in"))
				.expectLines(gawkPath("match3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbstr1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mbstr1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("mbstr1.awk"))
				.expectLines(gawkPath("mbstr1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mbstr2() throws Exception {
		skip(NON_UTF8_STDIN_REASON);
	}

	@Test
	public void test_mdim1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_mdim2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim2")
				.argument("-f", gawkFile("mdim2.awk"))
				.expectLines(gawkPath("mdim2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mdim3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim3")
				.argument("-f", gawkFile("mdim3.awk"))
				.expectLines(gawkPath("mdim3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mdim4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim4")
				.argument("-f", gawkFile("mdim4.awk"))
				.stdin(gawkText("mdim4.in"))
				.expectLines(gawkPath("mdim4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mdim5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim5")
				.argument("-f", gawkFile("mdim5.awk"))
				.expectLines(gawkPath("mdim5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mdim6() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_mdim7() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim7")
				.argument("-f", gawkFile("mdim7.awk"))
				.expectLines(gawkPath("mdim7.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mdim8() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mdim8")
				.argument("-f", gawkFile("mdim8.awk"))
				.stdin(gawkText("mdim8.in"))
				.expectLines(gawkPath("mdim8.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_mktime() throws Exception {
		AwkTestSupport
				.cliTest("GAWK mktime")
				.argument("-f", gawkFile("mktime.awk"))
				.stdin(gawkText("mktime.in"))
				.expectLines(gawkPath("mktime.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_modifiers() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_nastyparm() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_next() throws Exception {
		skip(
				"Shell-script target generated by Maketests; the in-process Jawk harness does not execute external shell scripts.");
	}

	@Test
	public void test_nondec() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nondec")
				.argument("-f", gawkFile("nondec.awk"))
				.expectLines(gawkPath("nondec.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nonfatal2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nonfatal2")
				.argument("-f", gawkFile("nonfatal2.awk"))
				.expectLines(gawkPath("nonfatal2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nonfatal3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nonfatal3")
				.argument("-f", gawkFile("nonfatal3.awk"))
				.expectLines(gawkPath("nonfatal3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsbad() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nsbad2() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nsbad3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_nsforloop() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsforloop")
				.argument("-f", gawkFile("nsforloop.awk"))
				.expectLines(gawkPath("nsforloop.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsfuncrecurse() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsfuncrecurse")
				.argument("-f", gawkFile("nsfuncrecurse.awk"))
				.expectLines(gawkPath("nsfuncrecurse.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsindirect1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsindirect1")
				.argument("-f", gawkFile("nsindirect1.awk"))
				.expectLines(gawkPath("nsindirect1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsindirect2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsindirect2")
				.argument("-f", gawkFile("nsindirect2.awk"))
				.expectLines(gawkPath("nsindirect2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_octdec() throws Exception {
		AwkTestSupport
				.cliTest("GAWK octdec")
				.argument("-f", gawkFile("octdec.awk"))
				.expectLines(gawkPath("octdec.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_patsplit() throws Exception {
		AwkTestSupport
				.cliTest("GAWK patsplit")
				.argument("-f", gawkFile("patsplit.awk"))
				.expectLines(gawkPath("patsplit.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_posix() throws Exception {
		AwkTestSupport
				.cliTest("GAWK posix")
				.argument("-f", gawkFile("posix.awk"))
				.stdin(gawkText("posix.in"))
				.expectLines(gawkPath("posix.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_printfbad1() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_printfbad3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK printfbad3")
				.argument("-f", gawkFile("printfbad3.awk"))
				.expectLines(gawkPath("printfbad3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_printfbad4() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_printhuge() throws Exception {
		skip(NON_UTF8_EXPECTED_REASON);
	}

	@Test
	public void test_procinfs() throws Exception {
		AwkTestSupport
				.cliTest("GAWK procinfs")
				.argument("-f", gawkFile("procinfs.awk"))
				.expectLines(gawkPath("procinfs.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regexsub() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regexsub")
				.argument("-f", gawkFile("regexsub.awk"))
				.expectLines(gawkPath("regexsub.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regnul1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regnul1")
				.argument("-f", gawkFile("regnul1.awk"))
				.expectLines(gawkPath("regnul1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regnul2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK regnul2")
				.argument("-f", gawkFile("regnul2.awk"))
				.expectLines(gawkPath("regnul2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_regx8bit() throws Exception {
		skip(NON_UTF8_EXPECTED_REASON);
	}

	@Test
	public void test_rsgetline() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsgetline")
				.argument("-f", gawkFile("rsgetline.awk"))
				.stdin(gawkText("rsgetline.in"))
				.expectLines(gawkPath("rsgetline.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rsstart1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsstart1")
				.argument("-f", gawkFile("rsstart1.awk"))
				.stdin(gawkText("rsstart1.in"))
				.expectLines(gawkPath("rsstart1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rsstart2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rsstart2")
				.argument("-f", gawkFile("rsstart2.awk"))
				.stdin(gawkText("rsstart2.in"))
				.expectLines(gawkPath("rsstart2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_rstest6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK rstest6")
				.argument("-f", gawkFile("rstest6.awk"))
				.stdin(gawkText("rstest6.in"))
				.expectLines(gawkPath("rstest6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_shadowbuiltin() throws Exception {
		AwkTestSupport
				.cliTest("GAWK shadowbuiltin")
				.argument("-f", gawkFile("shadowbuiltin.awk"))
				.expectLines(gawkPath("shadowbuiltin.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sortfor() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sortfor")
				.argument("-f", gawkFile("sortfor.awk"))
				.stdin(gawkText("sortfor.in"))
				.expectLines(gawkPath("sortfor.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sortfor2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sortfor2")
				.argument("-f", gawkFile("sortfor2.awk"))
				.stdin(gawkText("sortfor2.in"))
				.expectLines(gawkPath("sortfor2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_sortu() throws Exception {
		AwkTestSupport
				.cliTest("GAWK sortu")
				.argument("-f", gawkFile("sortu.awk"))
				.expectLines(gawkPath("sortu.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_split_after_fpat() throws Exception {
		AwkTestSupport
				.cliTest("GAWK split_after_fpat")
				.argument("-f", gawkFile("split_after_fpat.awk"))
				.stdin(gawkText("split_after_fpat.in"))
				.expectLines(gawkPath("split_after_fpat.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_splitarg4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK splitarg4")
				.argument("-f", gawkFile("splitarg4.awk"))
				.stdin(gawkText("splitarg4.in"))
				.expectLines(gawkPath("splitarg4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strftfld() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strftfld")
				.argument("-f", gawkFile("strftfld.awk"))
				.stdin(gawkText("strftfld.in"))
				.expectLines(gawkPath("strftfld.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strtonum() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strtonum")
				.argument("-f", gawkFile("strtonum.awk"))
				.expectLines(gawkPath("strtonum.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_strtonum1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK strtonum1")
				.argument("-f", gawkFile("strtonum1.awk"))
				.expectLines(gawkPath("strtonum1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_stupid1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK stupid1")
				.argument("-f", gawkFile("stupid1.awk"))
				.expectLines(gawkPath("stupid1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_stupid2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK stupid2")
				.argument("-f", gawkFile("stupid2.awk"))
				.expectLines(gawkPath("stupid2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_stupid3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK stupid3")
				.argument("-f", gawkFile("stupid3.awk"))
				.expectLines(gawkPath("stupid3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_stupid4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK stupid4")
				.argument("-f", gawkFile("stupid4.awk"))
				.expectLines(gawkPath("stupid4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_stupid5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK stupid5")
				.argument("-f", gawkFile("stupid5.awk"))
				.expectLines(gawkPath("stupid5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_switch2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK switch2")
				.argument("-f", gawkFile("switch2.awk"))
				.expectLines(gawkPath("switch2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab1")
				.argument("-f", gawkFile("symtab1.awk"))
				.expectLines(gawkPath("symtab1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab2")
				.argument("-f", gawkFile("symtab2.awk"))
				.expectLines(gawkPath("symtab2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab3() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_symtab4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab4")
				.argument("-f", gawkFile("symtab4.awk"))
				.stdin(gawkText("symtab4.in"))
				.expectLines(gawkPath("symtab4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab5")
				.argument("-f", gawkFile("symtab5.awk"))
				.stdin(gawkText("symtab5.in"))
				.expectLines(gawkPath("symtab5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab7() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_symtab10() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_symtab11() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab11")
				.argument("-f", gawkFile("symtab11.awk"))
				.expectLines(gawkPath("symtab11.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_symtab12() throws Exception {
		skip(NON_ZERO_TRANSCRIPT_REASON);
	}

	@Test
	public void test_timeout() throws Exception {
		AwkTestSupport
				.cliTest("GAWK timeout")
				.argument("-f", gawkFile("timeout.awk"))
				.expectLines(gawkPath("timeout.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typedregex1")
				.argument("-f", gawkFile("typedregex1.awk"))
				.expectLines(gawkPath("typedregex1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typedregex2")
				.argument("-f", gawkFile("typedregex2.awk"))
				.expectLines(gawkPath("typedregex2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typedregex3")
				.argument("-f", gawkFile("typedregex3.awk"))
				.expectLines(gawkPath("typedregex3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typedregex5")
				.argument("-f", gawkFile("typedregex5.awk"))
				.stdin(gawkText("typedregex5.in"))
				.expectLines(gawkPath("typedregex5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typedregex6")
				.argument("-f", gawkFile("typedregex6.awk"))
				.stdin(gawkText("typedregex6.in"))
				.expectLines(gawkPath("typedregex6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof1")
				.argument("-f", gawkFile("typeof1.awk"))
				.expectLines(gawkPath("typeof1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof2() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof2")
				.argument("-f", gawkFile("typeof2.awk"))
				.expectLines(gawkPath("typeof2.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof3() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof3")
				.argument("-f", gawkFile("typeof3.awk"))
				.expectLines(gawkPath("typeof3.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof4() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof4")
				.argument("-f", gawkFile("typeof4.awk"))
				.expectLines(gawkPath("typeof4.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof5() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof5")
				.argument("-f", gawkFile("typeof5.awk"))
				.stdin(gawkText("typeof5.in"))
				.expectLines(gawkPath("typeof5.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typeof6() throws Exception {
		AwkTestSupport
				.cliTest("GAWK typeof6")
				.argument("-f", gawkFile("typeof6.awk"))
				.expectLines(gawkPath("typeof6.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_unicode1() throws Exception {
		AwkTestSupport
				.cliTest("GAWK unicode1")
				.argument("--locale", "en-US")
				.argument("-f", gawkFile("unicode1.awk"))
				.expectLines(gawkPath("unicode1.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_manyfiles() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_argtest() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_badargs() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_strftime() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_devfd() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_errno() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rebuf() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rsglstdin() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rsstart3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_binmode1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_devfd1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_devfd2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_mixed1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_beginfile1() throws Exception {
		skip("BEGINFILE and ENDFILE are not implemented by Jawk yet.");
	}

	@Test
	public void test_beginfile2() throws Exception {
		skip("BEGINFILE and ENDFILE are not implemented by Jawk yet.");
	}

	@Test
	public void test_dumpvars() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile0() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile6() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile7() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_profile12() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nsawk1a() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsawk1a")
				.argument("-f", gawkFile("nsawk1.awk"))
				.expectLines(gawkPath("nsawk1a.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsawk1b() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsawk1b")
				.argument("-v", "I=fine")
				.argument("-f", gawkFile("nsawk1.awk"))
				.expectLines(gawkPath("nsawk1b.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsawk1c() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsawk1c")
				.argument("-v", "awk::I=fine")
				.argument("-f", gawkFile("nsawk1.awk"))
				.expectLines(gawkPath("nsawk1c.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsawk2a() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsawk2a")
				.argument("-v", "I=fine")
				.argument("-f", gawkFile("nsawk2.awk"))
				.expectLines(gawkPath("nsawk2a.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_nsawk2b() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsawk2b")
				.argument("-v", "awk::I=fine")
				.argument("-f", gawkFile("nsawk2.awk"))
				.expectLines(gawkPath("nsawk2b.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_include2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe4() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe5() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe6() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_incdupe7() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_charasbytes() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_symtab6() throws Exception {
		skip(
				"This handwritten case compares a fatal gawk SYMTAB diagnostic on stderr and is intentionally skipped in the explicit AwkTestSupport.cliTest suite.");
	}

	@Test
	public void test_symtab8() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_symtab9() throws Exception {
		AwkTestSupport
				.cliTest("GAWK symtab9")
				.argument("-f", gawkFile("symtab9.awk"))
				.expectLines(gawkPath("symtab9.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_reginttrad() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_colonwarn() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_dbugeval() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_genpot() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_negtime() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_watchpoint1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_pty1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_pty2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_arrdbg() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_sourcesplit() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nsbad_cmd() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nonfatal1() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nsidentifier() throws Exception {
		AwkTestSupport
				.cliTest("GAWK nsidentifier")
				.argument("-v", "SORT=sort")
				.argument("-f", gawkFile("nsidentifier.awk"))
				.expectLines(gawkPath("nsidentifier.ok"))
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void test_typedregex4() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_iolint() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

}
