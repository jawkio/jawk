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

import java.io.File;

import org.junit.Test;

/**
 * Tests for the filename {@code /dev/null}, which designates the platform's
 * null device on every platform: it discards everything written to it and
 * reports end of input when read. It is a real device on Unix systems, and
 * Jawk maps it to {@code NUL} on Windows, as gawk's Windows port does.
 */
public class NullDeviceTest {

	@Test
	public void printToDevNullDiscardsTheOutput() throws Exception {
		AwkTestSupport.awkTest("print and printf to /dev/null produce no output")
				.script(
						"BEGIN {" + " print \"one\" > \"/dev/null\";" + " printf \"%s\\n\", \"two\" > \"/dev/null\";"
								+ " print \"three\" >> \"/dev/null\";" + " print \"survived\"" + " }")
				.expectLines("survived")
				.runAndAssert();
	}

	@Test
	public void outputWrittenToDevNullCannotBeReadBack() throws Exception {
		// The strongest cross-platform statement that the records were discarded
		// rather than kept in a file of that name: reading the same name back
		// reports end of input and leaves the variable untouched.
		AwkTestSupport.awkTest("what /dev/null swallowed cannot be read back")
				.script(
						"BEGIN {" + " print \"written\" > \"/dev/null\";" + " close(\"/dev/null\");"
								+ " print (getline line < \"/dev/null\"), \"[\" line \"]\"" + " }")
				.expectLines("0 []")
				.runAndAssert();
	}

	@Test
	public void appendToDevNullDiscardsTheOutput() throws Exception {
		AwkTestSupport.awkTest("an appending redirection to /dev/null accumulates nothing")
				.script(
						"BEGIN {" + " for (i = 1; i <= 3; i++) print i >> \"/dev/null\";" + " close(\"/dev/null\");"
								+ " print (getline line < \"/dev/null\")" + " }")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void getlineFromDevNullReportsEndOfInput() throws Exception {
		AwkTestSupport.awkTest("getline from /dev/null reports end of input, not an error")
				.script("BEGIN { print (getline x < \"/dev/null\"), \"[\" x \"]\" }")
				.expectLines("0 []")
				.runAndAssert();
	}

	@Test
	public void getlineFromDevNullIntoDollarZeroReportsEndOfInput() throws Exception {
		AwkTestSupport.awkTest("a plain getline from /dev/null reports end of input and advances nothing")
				.script("BEGIN { print (getline < \"/dev/null\"), NR, NF }")
				.expectLines("0 0 0")
				.runAndAssert();
	}

	@Test
	public void closingDevNullSucceedsAndTheNameStaysUsable() throws Exception {
		// As with any other redirection: closing an open one reports success,
		// closing it again reports that none is open, and the name can be used
		// afterwards.
		AwkTestSupport.awkTest("close() of /dev/null succeeds and the name stays usable")
				.script(
						"BEGIN {" + " print \"one\" > \"/dev/null\";" + " print close(\"/dev/null\");"
								+ " print close(\"/dev/null\");" + " print \"two\" > \"/dev/null\";" + " print \"survived\"" + " }")
				.expectLines("0", "-1", "survived")
				.runAndAssert();
	}

	@Test
	public void devNullAsAnOperandReadsAsAnEmptyFile() throws Exception {
		AwkTestSupport.awkTest("/dev/null in the file list contributes no record")
				.script("{ print \"record:\" $0 } END { print NR, \"[\" FILENAME \"]\" }")
				.operand("/dev/null")
				.expectLines("0 [/dev/null]")
				.runAndAssert();
	}

	@Test
	public void devNullAsAnOperandDoesNotInterruptTheFileList() throws Exception {
		AwkTestSupport.awkTest("/dev/null between two files is traversed like an empty file")
				.script("{ print FNR \":\" $0 } END { print \"NR=\" NR }")
				.file("first", "a\n")
				.file("second", "b\n")
				.operand("{{first}}", "/dev/null", "{{second}}")
				.expectLines("1:a", "1:b", "NR=2")
				.runAndAssert();
	}

	@Test
	public void devNullAsAnOperandRunsBeginFileAndEndFileWithoutError() throws Exception {
		// The per-file main input loop, which BEGINFILE and ENDFILE select,
		// inspects each operand before opening it. The null device must not be
		// mistaken for a missing file there: Windows does not report it as an
		// existing file.
		AwkTestSupport.awkTest("BEGINFILE and ENDFILE see /dev/null as a readable empty file")
				.script(
						"BEGINFILE { print \"bf[\" FILENAME \"] errno=[\" ERRNO \"]\" }"
								+ " ENDFILE { print \"ef[\" FILENAME \"]\", FNR }" + " END { print \"NR=\" NR }")
				.operand("/dev/null")
				.expectLines("bf[/dev/null] errno=[]", "ef[/dev/null] 0", "NR=0")
				.runAndAssert();
	}

	@Test
	public void nulAsAnOperandReadsAsAnEmptyFileOnWindows() throws Exception {
		// NUL needs no translation — Windows opens that name as the device — but
		// it is no more stat-able than /dev/null is, so the per-file main input
		// loop must recognize it too instead of reporting a missing file. The
		// name is an ordinary filename on POSIX platforms, hence windowsOnly().
		AwkTestSupport.awkTest("the native NUL spelling is a readable empty file in the file list")
				.script(
						"BEGINFILE { print \"bf[\" FILENAME \"] errno=[\" ERRNO \"]\" }"
								+ " ENDFILE { print \"ef[\" FILENAME \"]\", FNR }" + " END { print \"NR=\" NR }")
				.operand("NUL")
				.windowsOnly()
				.expectLines("bf[NUL] errno=[]", "ef[NUL] 0", "NR=0")
				.runAndAssert();
	}

	@Test
	public void nulIsTheNullDeviceInRedirectionsOnWindows() throws Exception {
		AwkTestSupport.awkTest("the native NUL spelling discards output and reads as empty")
				.script(
						"BEGIN {" + " print \"written\" > \"NUL\";" + " print close(\"NUL\");"
								+ " print (getline line < \"NUL\"), \"[\" line \"]\"" + " }")
				.windowsOnly()
				.expectLines("0", "0 []")
				.runAndAssert();
	}

	@Test
	public void writingToDevNullCreatesNoRegularFile() throws Exception {
		// A redirection that fails to reach the device writes to whatever regular
		// file the name resolves to: on Windows that is dev\null on the current
		// drive. A device is not a regular file, so this state holds on POSIX too,
		// and comparing it before and after tolerates a file left behind by an
		// earlier run of a broken build.
		File regularFile = new File("/dev/null");
		String before = describeRegularFile(regularFile);
		AwkTestSupport.awkTest("a redirection to /dev/null creates no regular file")
				.script("BEGIN { print \"a record long enough to change any file length\" > \"/dev/null\" }")
				.expect("")
				.runAndAssert();
		assertEquals(
				"Redirecting to /dev/null wrote to the regular file " + regularFile.getAbsolutePath(),
				before,
				describeRegularFile(regularFile));
	}

	/**
	 * Describes whether the given path is a regular file and, if so, how long it
	 * is, so that two observations can be compared.
	 *
	 * @param file the path to describe
	 * @return a description of the path's regular-file state
	 */
	private static String describeRegularFile(File file) {
		return file.isFile() ? "regular file of " + file.length() + " byte(s)" : "not a regular file";
	}
}
