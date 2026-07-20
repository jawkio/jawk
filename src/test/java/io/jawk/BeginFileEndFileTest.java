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
import io.jawk.jrt.AwkRuntimeException;

/**
 * Tests for the gawk-specific BEGINFILE and ENDFILE special patterns, the
 * {@code nextfile} statement, and the ERRNO / ARGIND special variables that
 * support them. BEGINFILE and ENDFILE are gawk extensions and are therefore
 * not special in POSIX mode.
 */
public class BeginFileEndFileTest {

	@Test
	public void beginFileAndEndFileFireOncePerFile() throws Exception {
		AwkTestSupport
				.awkTest("BEGINFILE/ENDFILE fire once per input file, in order")
				.script(
						"BEGIN { print \"begin\" }"
								+ " BEGINFILE { print \"bf\", FNR }"
								+ " { print \"r:\" $0 }"
								+ " ENDFILE { print \"ef\", FNR }"
								+ " END { print \"end\", NR }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines(
						"begin",
						"bf 0",
						"r:a1",
						"r:a2",
						"ef 2",
						"bf 0",
						"r:b1",
						"ef 1",
						"end 3")
				.runAndAssert();
	}

	@Test
	public void endFileRunsForEmptyFiles() throws Exception {
		AwkTestSupport
				.awkTest("ENDFILE runs even for empty input files")
				.script("BEGINFILE { print \"bf\" } ENDFILE { print \"ef\", FNR }")
				.file("empty", "")
				.operand("{{empty}}")
				.expectLines("bf", "ef 0")
				.runAndAssert();
	}

	@Test
	public void beginFileSeesFilenameAndClearedRecord() throws Exception {
		AwkTestSupport
				.awkTest("BEGINFILE sees FILENAME, FNR=0, and a cleared $0")
				.script(
						"BEGINFILE { ok = (FILENAME == \"{{f1}}\"); print ok, FNR, \"[\" $0 \"]\", NF }"
								+ " { x = $0 }")
				.file("f1", "one two\n")
				.operand("{{f1}}")
				.expectLines("1 0 [] 0")
				.runAndAssert();
	}

	@Test
	public void beginFileAndEndFileFireForStandardInput() throws Exception {
		AwkTestSupport
				.awkTest("BEGINFILE/ENDFILE fire for standard input")
				.script(
						"BEGINFILE { print \"bf[\" FILENAME \"]\", FNR }"
								+ " { print \"r:\" $0 }"
								+ " ENDFILE { print \"ef[\" FILENAME \"]\", FNR }")
				.stdin("s1\ns2\n")
				.expectLines("bf[] 0", "r:s1", "r:s2", "ef[] 2")
				.runAndAssert();
	}

	@Test
	public void errnoAllowsSkippingUnreadableFile() throws Exception {
		AwkTestSupport
				.cliTest("ERRNO + nextfile skips a file that cannot be opened")
				.script(
						"BEGINFILE { if (ERRNO != \"\") { print \"skipping\"; nextfile } print \"bf\" }"
								+ " { print \"r:\" $0 }"
								+ " ENDFILE { print \"ef\" }")
				.file("f1", "a1\n")
				.operand("{{f1}}", "no/such/file", "{{f1}}")
				.expectLines("bf", "r:a1", "ef", "skipping", "bf", "r:a1", "ef")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void unreadableFileIsFatalWhenBeginFileDoesNotSkipIt() throws Exception {
		AwkTestSupport
				.awkTest("open failure stays fatal when BEGINFILE does not call nextfile")
				.script("BEGINFILE { print \"bf\" } { print }")
				.operand("no/such/file")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextfileInBeginFileRunsEndFileForReadableFile() throws Exception {
		AwkTestSupport
				.awkTest("nextfile in BEGINFILE still runs ENDFILE for a readable file")
				.script(
						"BEGINFILE { print \"bf\"; nextfile }"
								+ " { print \"r:\" $0 }"
								+ " ENDFILE { print \"ef\" }")
				.file("f1", "a1\n")
				.operand("{{f1}}")
				.expectLines("bf", "ef")
				.runAndAssert();
	}

	@Test
	public void nextfileSkipsRestOfCurrentFile() throws Exception {
		AwkTestSupport
				.awkTest("nextfile skips the rest of the current file")
				.script("{ print FILENAME \":\" $0; if (FNR == 1) nextfile }")
				.file("f1", "A1\nA2\n")
				.file("f2", "B1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("{{f1}}:A1", "{{f2}}:B1")
				.runAndAssert();
	}

	@Test
	public void nextfileWorksFromUserDefinedFunction() throws Exception {
		AwkTestSupport
				.awkTest("nextfile unwinds user-defined function calls")
				.script(
						"function skip() { nextfile; print \"unreached\" }"
								+ " BEGINFILE { print \"bf\" }"
								+ " { print \"r:\" $0; skip() }"
								+ " ENDFILE { print \"ef\" }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("bf", "r:a1", "ef", "bf", "r:b1", "ef")
				.runAndAssert();
	}

	@Test
	public void nextfileWithoutBeginFileOrEndFileRules() throws Exception {
		AwkTestSupport
				.awkTest("nextfile works without any BEGINFILE/ENDFILE rules")
				.script("function f() { nextfile } { print $0; f() } END { print \"nr=\" NR }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("a1", "b1", "nr=2")
				.runAndAssert();
	}

	@Test
	public void nextIsRejectedInsideBeginFile() throws Exception {
		AwkTestSupport
				.awkTest("next cannot be called from a BEGINFILE rule")
				.script("BEGINFILE { next } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextIsRejectedInsideEndFile() throws Exception {
		AwkTestSupport
				.awkTest("next cannot be called from an ENDFILE rule")
				.script("ENDFILE { next } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextfileIsRejectedInsideEndFile() throws Exception {
		AwkTestSupport
				.awkTest("nextfile cannot be called from an ENDFILE rule")
				.script("ENDFILE { nextfile } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextfileIsRejectedInsideBeginAndEnd() throws Exception {
		AwkTestSupport
				.awkTest("nextfile cannot be called from a BEGIN rule")
				.script("BEGIN { nextfile } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
		AwkTestSupport
				.awkTest("nextfile cannot be called from an END rule")
				.script("{ print } END { nextfile }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextfileInEndViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport
				.awkTest("nextfile reached from an END rule through a function is fatal")
				.script("function f() { nextfile } { } END { f() }")
				.stdin("x\n")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nonRedirectedGetlineIsRejectedInsideBeginFile() throws Exception {
		// Direct use is a compile-time error; see the ViaFunction cases for
		// the runtime detection.
		AwkTestSupport
				.awkTest("non-redirected getline is invalid inside BEGINFILE")
				.script("BEGINFILE { getline } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nonRedirectedGetlineIsRejectedInsideEndFile() throws Exception {
		// Direct use of the "getline var" form is a compile-time error too.
		AwkTestSupport
				.awkTest("non-redirected getline is invalid inside ENDFILE")
				.script("ENDFILE { getline line } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nonRedirectedGetlineIsFatalInsideBeginFileViaFunction() throws Exception {
		AwkTestSupport
				.awkTest("non-redirected getline through a function is invalid inside BEGINFILE")
				.script("function g() { getline } BEGINFILE { g() } { print }")
				.stdin("x\n")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nonRedirectedGetlineIsFatalInsideEndFileViaFunction() throws Exception {
		AwkTestSupport
				.awkTest("non-redirected getline through a function is invalid inside ENDFILE")
				.script("function g() { getline } ENDFILE { g() } { print }")
				.stdin("x\n")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void getlineInActionStopsAtFileBoundaryWhenHooksArePresent() throws Exception {
		// The per-file loop is the only place allowed to cross file
		// boundaries when BEGINFILE/ENDFILE rules exist, so no file's hooks
		// are ever skipped: getline reports end-of-input at the end of the
		// current file instead of silently opening the next one.
		AwkTestSupport
				.awkTest("getline in an action does not cross file boundaries behind the hooks")
				.script(
						"BEGINFILE { print \"bf:\" FILENAME }"
								+ " { print \"r:\" $0; if ((getline x) > 0) print \"g:\" x; else print \"g:eof\" }"
								+ " ENDFILE { print \"ef:\" FILENAME }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines(
						"bf:{{f1}}",
						"r:a1",
						"g:a2",
						"ef:{{f1}}",
						"bf:{{f2}}",
						"r:b1",
						"g:eof",
						"ef:{{f2}}")
				.runAndAssert();
	}

	@Test
	public void getlineInActionStillCrossesFilesWithoutHooks() throws Exception {
		// Without BEGINFILE/ENDFILE rules there are no per-file hooks to
		// protect, so getline keeps the classic AWK behavior of streaming
		// across input files — even when nextfile forces the per-file loop.
		AwkTestSupport
				.awkTest("getline in an action crosses file boundaries without hooks")
				.script(
						"function unused() { nextfile }"
								+ " { print \"r:\" $0; if ((getline x) > 0) print \"g:\" x }")
				.file("f1", "a1\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("r:a1", "g:b1")
				.runAndAssert();
	}

	@Test
	public void redirectedGetlineIsAllowedInsideBeginFile() throws Exception {
		AwkTestSupport
				.awkTest("redirected getline is allowed inside BEGINFILE")
				.script(
						"BEGINFILE { if ((getline line < \"{{f2}}\") > 0) print \"got:\" line;"
								+ " close(\"{{f2}}\") }"
								+ " { print \"r:\" $0 }")
				.file("f1", "a1\n")
				.file("f2", "b1\n")
				.operand("{{f1}}")
				.expectLines("got:b1", "r:a1")
				.runAndAssert();
	}

	@Test
	public void argindTracksTheArgvIndexOfTheCurrentFile() throws Exception {
		AwkTestSupport
				.awkTest("ARGIND designates the ARGV entry of the current file")
				.script("BEGINFILE { print \"bf\", ARGIND } END { print \"end\", ARGIND }")
				.file("f1", "a1\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "x=1", "{{f2}}")
				.expectLines("bf 1", "bf 3", "end 3")
				.runAndAssert();
	}

	@Test
	public void errnoIsAnAssignableSpecialVariable() throws Exception {
		AwkTestSupport
				.awkTest("ERRNO starts empty and is assignable")
				.script("BEGIN { print \"[\" ERRNO \"]\"; ERRNO = \"boom\"; print ERRNO }")
				.expectLines("[]", "boom")
				.runAndAssert();
	}

	@Test
	public void errnoIsClearedWhenMainInputOpensAFile() throws Exception {
		// gawk clears ERRNO whenever the main input advances successfully,
		// even without BEGINFILE/ENDFILE rules.
		AwkTestSupport
				.awkTest("ERRNO is cleared when an input file opens successfully")
				.script("BEGIN { ERRNO = \"boom\" } { print \"[\" ERRNO \"]\" }")
				.file("f1", "a1\n")
				.operand("{{f1}}")
				.expectLines("[]")
				.runAndAssert();
	}

	@Test
	public void errnoIsClearedWhenMainInputReadsStandardInput() throws Exception {
		AwkTestSupport
				.awkTest("ERRNO is cleared when standard input is selected as main input")
				.script("BEGIN { ERRNO = \"boom\" } { print \"[\" ERRNO \"]\" }")
				.stdin("s1\n")
				.expectLines("[]")
				.runAndAssert();
	}

	@Test
	public void exitInsideBeginFileRunsEndRules() throws Exception {
		AwkTestSupport
				.cliTest("exit in BEGINFILE skips ENDFILE and runs END")
				.script(
						"BEGINFILE { print \"bf\"; exit(3) }"
								+ " { print \"r\" }"
								+ " ENDFILE { print \"ef\" }"
								+ " END { print \"end\" }")
				.file("f1", "a1\n")
				.operand("{{f1}}")
				.expectLines("bf", "end")
				.expectExit(3)
				.runAndAssert();
	}

	@Test
	public void beginFileIsNotSpecialInPosixMode() throws Exception {
		AwkTestSupport
				.cliTest("--posix treats BEGINFILE/ENDFILE as plain identifiers")
				.argument("--posix")
				.script("BEGINFILE { print \"bf\" } ENDFILE { print \"ef\" } { print \"r:\" $0 }")
				.stdin("x\n")
				.expectLines("r:x")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void errnoAndArgindAreOrdinaryVariablesInPosixMode() throws Exception {
		// Like gawk --posix: ERRNO and ARGIND are plain identifiers, usable
		// as function parameters and as ordinary globals.
		AwkTestSupport
				.cliTest("--posix treats ERRNO and ARGIND as ordinary identifiers")
				.argument("--posix")
				.script(
						"function f(ARGIND) { return ARGIND * 2 }"
								+ " function g(ERRNO) { return ERRNO \"x\" }"
								+ " BEGIN { ERRNO = \"a\"; print f(3), g(ERRNO) }")
				.expectLines("6 ax")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void argindIsNotUpdatedAtFileBoundariesInPosixMode() throws Exception {
		// Like gawk --posix: reading input files does not touch a
		// user-assigned ARGIND.
		AwkTestSupport
				.cliTest("--posix keeps a user-assigned ARGIND across input files")
				.argument("--posix")
				.script("BEGIN { ARGIND = 99 } { print ARGIND \":\" $0 }")
				.file("f1", "a1\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("99:a1", "99:b1")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void errnoIsAssignableThroughDashVInPosixMode() throws Exception {
		// -v ERRNO=... must reach the ordinary global slot in POSIX mode.
		AwkTestSupport
				.cliTest("--posix -v ERRNO seeds the ordinary global")
				.argument("--posix", "-v", "ERRNO=foo")
				.script("BEGIN { print ERRNO }")
				.expectLines("foo")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void beginFileIsUsableAsVariableInPosixMode() throws Exception {
		AwkTestSupport
				.cliTest("--posix allows BEGINFILE as an ordinary variable name")
				.argument("--posix")
				.script("BEGIN { BEGINFILE = 42; ENDFILE = 8; print BEGINFILE + ENDFILE }")
				.expectLines("50")
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void regexpBracketExpressionMayContainSlash() throws Exception {
		AwkTestSupport
				.awkTest("a slash inside a bracket expression does not end the regexp")
				.script("BEGIN { s = \"a/b\"; gsub(/[/]/, \"-\", s); print s }")
				.expectLines("a-b")
				.runAndAssert();
	}

	@Test
	public void regexpBracketExpressionMayContainSlashAfterCharacterClass() throws Exception {
		// The ']' closing a POSIX character class such as [:digit:] must not
		// be taken as the end of the enclosing bracket expression, so the
		// slash that follows still belongs to the regexp literal.
		AwkTestSupport
				.awkTest("a slash after a POSIX character class does not end the regexp")
				.script("BEGIN { s = \"a/b\"; gsub(/[[:digit:]/]/, \"-\", s); print s }")
				.expectLines("a-b")
				.runAndAssert();
	}

	@Test
	public void assignmentOperandsDoNotConsumeRecordsInPerFileLoop() throws Exception {
		// A name=value operand between input files applies its assignment
		// without reading a record, so NR stays contiguous across files.
		AwkTestSupport
				.awkTest("assignment operands leave NR untouched in the per-file loop")
				.script(
						"BEGINFILE { print \"bf\", NR }"
								+ " { print NR \":\" $0 }"
								+ " END { print \"end\", NR }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "x=1", "{{f2}}")
				.expectLines("bf 0", "1:a1", "2:a2", "bf 2", "3:b1", "end 3")
				.runAndAssert();
	}
}
