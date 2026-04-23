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

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Explicit handwritten gawk compatibility cases transcribed from vendored
 * Makefile.am rules. The Java source is the runtime source of truth; many
 * shell-heavy cases are kept as explicit skipped placeholders until they are
 * worth expressing as AwkTestSupport.cliTest cases.
 */
public class GawkManualIT {

	private static final Path GAWK_DIRECTORY = CompatibilityTestResources.resourceDirectory(GawkManualIT.class, "gawk");
	private static final String MANUAL_SKIP_REASON = "Handwritten gawk case from Makefile.am not yet expressed as an AwkTestSupport.cliTest case.";

	private static Path gawkPath(String fileName) {
		return GAWK_DIRECTORY.resolve(fileName);
	}

	private static String gawkFile(String fileName) {
		return gawkPath(fileName).toString();
	}

	private static String gawkText(String fileName) throws IOException {
		return new String(Files.readAllBytes(gawkPath(fileName)), StandardCharsets.UTF_8);
	}

	private static void skip(String reason) {
		assumeTrue(reason, false);
	}

	@Test
	public void test_pma() throws Exception {
		skip(MANUAL_SKIP_REASON);
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
	public void test_manyfiles() throws Exception {
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
	public void test_rebuf() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rsglstdin() throws Exception {
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
	public void test_inetmesg() throws Exception {
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
	public void test_rsstart3() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_rtlenmb() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}

	@Test
	public void test_nofile() throws Exception {
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
	public void test_mbprintf5() throws Exception {
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
	public void test_profile5() throws Exception {
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
	public void test_jarebug() throws Exception {
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
	public void test_filefuncs() throws Exception {
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
	public void test_ignrcas3() throws Exception {
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
	public void test_eofsrc1() throws Exception {
		skip(
				"This handwritten case compares gawk parser diagnostics on stderr and is intentionally skipped in the explicit AwkTestSupport.cliTest suite.");
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
	public void test_nlstringtest() throws Exception {
		skip(MANUAL_SKIP_REASON);
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
	public void test_spacere() throws Exception {
		AwkTestSupport
				.cliTest("GAWK spacere")
				.argument("-f", gawkFile("spacere.awk"))
				.expectLines(gawkPath("spacere.ok"))
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

	@Test
	public void test_indirectbuiltin2() throws Exception {
		skip(MANUAL_SKIP_REASON);
	}
}
