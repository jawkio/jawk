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
 * Tests for the redirected forms of {@code getline}: {@code getline [var] <
 * file} and {@code cmd | getline [var]}. Each form sets exactly the variables
 * gawk documents for it — the {@code $0} forms set {@code $0} and {@code NF},
 * the {@code var} forms set only the variable — and no redirected form touches
 * NR, FNR, or FILENAME. A {@code getline} that reads nothing, because the
 * source is exhausted (return 0) or cannot be opened (return -1), leaves its
 * target untouched, sets ERRNO on -1, and never aborts the script.
 */
public class GetlineRedirectionTest {

	@Test
	public void getlineVarFromFileSetsOnlyTheVariable() throws Exception {
		AwkTestSupport.awkTest("getline var < file leaves $0, NF, NR, FNR, and FILENAME alone")
				.script(
						"{ getline extra < \"{{other}}\";"
								+ " print NR, FNR, (FILENAME == \"{{main}}\"), NF, \"[\" $0 \"]\", extra }")
				.file("main", "from file\n")
				.file("other", "from other\n")
				.operand("{{main}}")
				.expectLines("1 1 1 2 [from file] from other")
				.runAndAssert();
	}

	@Test
	public void getlineIntoRecordFromFileSetsOnlyRecordAndNF() throws Exception {
		AwkTestSupport.awkTest("getline < file replaces $0 and NF but leaves NR, FNR, and FILENAME alone")
				.script("{ getline < \"{{other}}\";" + " print NR, FNR, (FILENAME == \"{{main}}\"), NF, \"[\" $0 \"]\" }")
				.file("main", "from file\n")
				.file("other", "one two three\n")
				.operand("{{main}}")
				.expectLines("1 1 1 3 [one two three]")
				.runAndAssert();
	}

	@Test
	public void getlineFromFileInBeginLeavesNrAndFilenameAlone() throws Exception {
		AwkTestSupport.awkTest("a redirected getline in BEGIN does not invent NR or FILENAME")
				.script("BEGIN { getline x < \"{{f}}\"; print NR, FNR, \"[\" FILENAME \"]\", \"[\" x \"]\" }")
				.file("f", "a b c\n")
				.expectLines("0 0 [] [a b c]")
				.runAndAssert();
	}

	@Test
	public void getlineVarFromFileYieldsAStringOfNumbersComparableAsNumber() throws Exception {
		AwkTestSupport.awkTest("a record read by getline var < file compares as a number, like main input")
				.script("BEGIN { getline x < \"{{f}}\"; print (x == 12), (x == \"12\") }")
				.file("f", "12\n")
				.expectLines("1 1")
				.runAndAssert();
	}

	@Test
	public void commandGetlineIntoRecordLeavesCountersAndFilenameAlone() throws Exception {
		// gawk documents cmd | getline as setting $0 and NF only: unlike the
		// POSIX table, NR is not advanced, and FILENAME is not touched either.
		AwkTestSupport.awkTest("cmd | getline sets $0 and NF but leaves NR, FNR, and FILENAME alone")
				.script("{ \"echo c1\" | getline;" + " print NR, FNR, (FILENAME == \"{{main}}\"), NF, \"[\" $0 \"]\" }")
				.file("main", "from file\n")
				.operand("{{main}}")
				.expectLines("1 1 1 1 [c1]")
				.runAndAssert();
	}

	@Test
	public void commandGetlineVarSetsOnlyTheVariable() throws Exception {
		AwkTestSupport.awkTest("cmd | getline var leaves $0, NF, NR, FNR, and FILENAME alone")
				.script("{ \"echo c1\" | getline x;" + " print NR, FNR, (FILENAME == \"{{main}}\"), NF, \"[\" $0 \"]\", x }")
				.file("main", "from file\n")
				.operand("{{main}}")
				.expectLines("1 1 1 2 [from file] c1")
				.runAndAssert();
	}

	@Test
	public void getlineIntoRecordAtEndOfInputLeavesRecordUntouched() throws Exception {
		AwkTestSupport.awkTest("getline < file returning 0 leaves $0 and NF as they were")
				.script("BEGIN { $0 = \"a b c\"; print (getline < \"{{empty}}\"), NF, \"[\" $0 \"]\" }")
				.file("empty", "")
				.expectLines("0 3 [a b c]")
				.runAndAssert();
	}

	@Test
	public void commandGetlineIntoRecordAtEndOfInputLeavesRecordUntouched() throws Exception {
		AwkTestSupport.awkTest("cmd | getline returning 0 leaves $0 and NF as they were")
				.script("BEGIN { $0 = \"a b c\"; print (\"exit 0\" | getline), NF, \"[\" $0 \"]\" }")
				.expectLines("0 3 [a b c]")
				.runAndAssert();
	}

	@Test
	public void getlineVarAtEndOfInputLeavesVariableUntouched() throws Exception {
		AwkTestSupport.awkTest("getline var < file returning 0 leaves the variable as it was")
				.script("BEGIN { x = \"keep\"; print (getline x < \"{{empty}}\"), \"[\" x \"]\" }")
				.file("empty", "")
				.expectLines("0 [keep]")
				.runAndAssert();
	}

	@Test
	public void commandGetlineVarAtEndOfInputLeavesVariableUntouched() throws Exception {
		AwkTestSupport.awkTest("cmd | getline var returning 0 leaves the variable as it was")
				.script("BEGIN { x = \"keep\"; print (\"exit 0\" | getline x), \"[\" x \"]\" }")
				.expectLines("0 [keep]")
				.runAndAssert();
	}

	@Test
	public void unredirectedGetlineVarAtEndOfInputLeavesVariableUntouched() throws Exception {
		AwkTestSupport.awkTest("a plain getline var at the end of the main input leaves the variable as it was")
				.script("{ x = \"keep\"; print (getline x), \"[\" x \"]\" }")
				.stdin("one\n")
				.expectLines("0 [keep]")
				.runAndAssert();
	}

	@Test
	public void getlineIntoFieldAtEndOfInputLeavesFieldsUntouched() throws Exception {
		AwkTestSupport.awkTest("getline $2 < file returning 0 leaves the fields as they were")
				.script("BEGIN { $0 = \"a b c\"; print (getline $2 < \"{{empty}}\"), NF, \"[\" $0 \"]\" }")
				.file("empty", "")
				.expectLines("0 3 [a b c]")
				.runAndAssert();
	}

	@Test
	public void getlineIntoArrayElementAtEndOfInputLeavesElementUntouched() throws Exception {
		AwkTestSupport.awkTest("getline arr[i] < file returning 0 leaves the element as it was")
				.script("BEGIN { a[1] = \"keep\"; print (getline a[1] < \"{{empty}}\"), \"[\" a[1] \"]\" }")
				.file("empty", "")
				.expectLines("0 [keep]")
				.runAndAssert();
	}

	@Test
	public void getlineIntoFieldFromFileAssignsTheField() throws Exception {
		AwkTestSupport.awkTest("getline $n < file stores the record into field n and rebuilds $0")
				.script("BEGIN { $0 = \"x y\"; print (getline $2 < \"{{f}}\"), NF, \"[\" $0 \"]\" }")
				.file("f", "o1\n")
				.expectLines("1 2 [x o1]")
				.runAndAssert();
	}

	@Test
	public void getlineIntoFieldBeyondNFExtendsTheRecord() throws Exception {
		AwkTestSupport.awkTest("getline $n < file past NF extends the record with empty fields, like $n = v")
				.script("BEGIN { $0 = \"x y\"; print (getline $5 < \"{{f}}\"), NF, \"[\" $0 \"]\" }")
				.file("f", "o1\n")
				.expectLines("1 5 [x y   o1]")
				.runAndAssert();
	}

	@Test
	public void commandGetlineIntoFieldAssignsTheField() throws Exception {
		AwkTestSupport.awkTest("cmd | getline $n stores the record into field n")
				.script("BEGIN { $0 = \"x y\"; print (\"echo piped\" | getline $2), NF, \"[\" $0 \"]\" }")
				.expectLines("1 2 [x piped]")
				.runAndAssert();
	}

	@Test
	public void unredirectedGetlineIntoFieldAssignsTheField() throws Exception {
		AwkTestSupport.awkTest("a plain getline $n reads the next main-input record into field n")
				.script("NR == 1 { r = (getline $1); print r, NF, \"[\" $0 \"]\" }")
				.stdin("a b\nc d\n")
				.expectLines("1 2 [c d b]")
				.runAndAssert();
	}

	@Test
	public void subscriptOfArrayTargetIsEvaluatedWhenNothingIsRead() throws Exception {
		// The gawk >= 4.0 dark corner its getline5 test pins: the subscript of
		// an array target runs even when getline reads nothing, and the mere
		// reference creates the element — but the read assigns it nothing.
		AwkTestSupport.awkTest("getline arr[++c] < file at end of input still evaluates ++c and creates the element")
				.script(
						"BEGIN { while ((getline a[++c] < \"{{one}}\") > 0) { }"
								+ " print c, length(a), (1 in a), (2 in a), \"[\" a[1] \"]\" }")
				.file("one", "r1\n")
				.expectLines("2 2 1 1 [r1]")
				.runAndAssert();
	}

	@Test
	public void fieldIndexOfFieldTargetIsEvaluatedWhenNothingIsRead() throws Exception {
		AwkTestSupport.awkTest("getline $(++n) < file at end of input still evaluates ++n and keeps the fields")
				.script("BEGIN { $0 = \"x y\"; n = 1;" + " print (getline $(++n) < \"{{empty}}\"), n, NF, \"[\" $0 \"]\" }")
				.file("empty", "")
				.expectLines("0 2 2 [x y]")
				.runAndAssert();
	}

	@Test
	public void negativeFieldTargetIsRejectedEvenWhenNothingIsRead() throws Exception {
		// gawk rejects the invalid field target whether or not a record was
		// available; the target reference is evaluated either way.
		AwkTestSupport.awkTest("getline $(-1) < file is fatal at end of input too")
				.script("BEGIN { r = (getline $(-1) < \"{{empty}}\"); print r }")
				.file("empty", "")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void emptyFileNameIsFatal() throws Exception {
		// gawk: fatal: expression for `<' redirection has null string value.
		// The empty name reports a fatal error rather than -1, so an unset
		// filename variable is not mistaken for a merely missing file.
		AwkTestSupport.awkTest("getline from an empty filename is a fatal error, as in gawk")
				.script("BEGIN { r = (getline x < \"\"); print r }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void emptyCommandIsFatal() throws Exception {
		// gawk: fatal: expression for `|' redirection has null string value.
		AwkTestSupport.awkTest("getline from an empty command is a fatal error, as in gawk")
				.script("BEGIN { r = (\"\" | getline x); print r }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void getlineFromMissingFileReturnsMinusOneAndSetsErrno() throws Exception {
		AwkTestSupport.awkTest("getline var from a missing file returns -1, sets ERRNO, and the script continues")
				.script(
						"BEGIN { $0 = \"a b c\"; x = \"keep\";" + " r = (getline x < (TEMPDIR \"/no-such-file.txt\"));"
								+ " print r, NF, \"[\" $0 \"]\", \"[\" x \"]\", \"[\" ERRNO \"]\";" + " print \"still running\" }")
				.withTempDir()
				.expectLines("-1 3 [a b c] [keep] [No such file or directory]", "still running")
				.runAndAssert();
	}

	@Test
	public void getlineIntoRecordFromMissingFileReturnsMinusOne() throws Exception {
		AwkTestSupport.awkTest("getline into $0 from a missing file returns -1 and leaves the record alone")
				.script("BEGIN { $0 = \"a b c\";" + " print (getline < (TEMPDIR \"/no-such-file.txt\")), NF, \"[\" $0 \"]\" }")
				.withTempDir()
				.expectLines("-1 3 [a b c]")
				.runAndAssert();
	}

	@Test
	public void getlineFromDirectoryReturnsMinusOneWithIsADirectory() throws Exception {
		AwkTestSupport.awkTest("getline from a directory returns -1 with the gawk ERRNO description")
				.script("BEGIN { r = (getline x < TEMPDIR);" + " print r, \"[\" ERRNO \"]\" }")
				.withTempDir()
				.expectLines("-1 [Is a directory]")
				.runAndAssert();
	}

	@Test
	public void getlineAtEndOfInputLeavesErrnoAlone() throws Exception {
		AwkTestSupport.awkTest("a getline that merely reaches end of input does not set ERRNO")
				.script("BEGIN { print (getline < \"{{empty}}\"), \"[\" ERRNO \"]\" }")
				.file("empty", "")
				.expectLines("0 []")
				.runAndAssert();
	}

	@Test
	public void failedOpenIsRetriedOnceTheFileAppears() throws Exception {
		// gawk does not cache a failed open: the same getline succeeds once the
		// file exists.
		AwkTestSupport.awkTest("a getline that failed to open retries instead of reporting -1 forever")
				.script(
						"BEGIN { f = (TEMPDIR \"/late.txt\");" + " r1 = (getline x < f);" + " print \"created\" > f; close(f);"
								+ " r2 = (getline x < f);" + " print r1, r2, \"[\" x \"]\" }")
				.withTempDir()
				.expectLines("-1 1 [created]")
				.runAndAssert();
	}
}
