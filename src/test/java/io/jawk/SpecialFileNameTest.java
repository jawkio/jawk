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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the gawk special filenames {@code /dev/stdin}, {@code /dev/stdout},
 * {@code /dev/stderr}, and their {@code /dev/fd/N} spellings, which designate
 * the streams the process already holds open rather than files of those names.
 */
public class SpecialFileNameTest {

	@Test
	public void printToDevStdoutSharesTheDefaultOutput() throws Exception {
		// The captured output is an Appendable, not a file descriptor: every
		// record below reaches it only because /dev/stdout resolves to the sink
		// plain print writes to, in the order the script produced them.
		AwkTestSupport.awkTest("print > \"/dev/stdout\" interleaves with plain print")
				.script(
						"BEGIN {" + " print \"one\";" + " print \"two\" > \"/dev/stdout\";" + " print \"three\";"
								+ " printf \"%s\\n\", \"four\" > \"/dev/stdout\";" + " print \"five\" >> \"/dev/stdout\"" + " }")
				.expectLines("one", "two", "three", "four", "five")
				.runAndAssert();
	}

	@Test
	public void printToDevFd1SharesTheDefaultOutput() throws Exception {
		AwkTestSupport.awkTest("/dev/fd/1 is a spelling of /dev/stdout")
				.script("BEGIN { print \"one\"; print \"two\" > \"/dev/fd/1\" }")
				.expectLines("one", "two")
				.runAndAssert();
	}

	@Test
	public void printToDevStdoutIsNotTruncatedBetweenWrites() throws Exception {
		AwkTestSupport.awkTest("successive writes to /dev/stdout accumulate")
				.script("BEGIN { for (i = 1; i <= 3; i++) print i > \"/dev/stdout\" }")
				.expectLines("1", "2", "3")
				.runAndAssert();
	}

	@Test
	public void printToDevStderrGoesToStandardError() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("print > \"/dev/stderr\" writes to standard error")
				.script("BEGIN { print \"out\"; print \"err\" > \"/dev/stderr\" }")
				.run();
		result.assertExpected();
		assertEquals("out\n", result.output());
		assertEquals("err\n", result.errorOutput());
	}

	@Test
	public void printToDevFd2GoesToStandardError() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport.cliTest("/dev/fd/2 is a spelling of /dev/stderr")
				.script("BEGIN { print \"err\" > \"/dev/fd/2\" }")
				.run();
		result.assertExpected();
		assertEquals("", result.output());
		assertEquals("err\n", result.errorOutput());
	}

	@Test
	public void devStdoutAndDevStderrShareOneRedirectedStream() throws Exception {
		// With standard error redirected onto standard output, as a shell does
		// with 2>&1, the two special filenames must write through that single
		// stream in script order instead of clobbering each other.
		AwkTestSupport.cliTest("/dev/stdout and /dev/stderr write through a shared stream in order")
				.redirectErrorStream()
				.script(
						"BEGIN {" + " print \"first\" > \"/dev/stdout\";" + " print \"second\" > \"/dev/stderr\";"
								+ " print \"third\" > \"/dev/stdout\";" + " print \"fourth\" > \"/dev/stderr\"" + " }")
				.expectLines("first", "second", "third", "fourth")
				.runAndAssert();
	}

	@Test
	public void writesToDevStderrInterleaveWithRuntimeDiagnostics() throws Exception {
		// Calling a function with more arguments than it declares makes the
		// runtime print a gawk-style warning to standard error: the records the
		// script sends to /dev/stderr must surround it instead of going through
		// an independent stream that restarts at offset zero.
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("script writes and runtime diagnostics share standard error")
				.script(
						"function f(a) { return a }" + " BEGIN {" + " print \"before\" > \"/dev/stderr\";" + " x = f(1, 2);"
								+ " print \"after\" > \"/dev/stderr\"" + " }")
				.run();
		result.assertExpected();
		String[] errorLines = result.errorOutput().split("\\R");
		assertEquals("Unexpected standard error: " + result.errorOutput(), 3, errorLines.length);
		assertEquals("before", errorLines[0]);
		assertTrue(
				"Expected the extra-argument warning, got: " + errorLines[1],
				errorLines[1].contains("called with more arguments than declared"));
		assertEquals("after", errorLines[2]);
	}

	@Test
	public void partialRecordsWrittenToDevStderrAreFlushed() throws Exception {
		// gawk keeps /dev/stderr unbuffered, so a printf that does not end the
		// record is visible before whatever the runtime writes next.
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("a partial record written to /dev/stderr is flushed immediately")
				.script(
						"function f(a) { return a }" + " BEGIN {" + " printf \"partial\" > \"/dev/stderr\";" + " x = f(1, 2);"
								+ " printf \"|end\\n\" > \"/dev/stderr\"" + " }")
				.run();
		result.assertExpected();
		assertTrue(
				"Expected the partial record before the warning, got: " + result.errorOutput(),
				result.errorOutput().startsWith("partial"));
		assertTrue(
				"Expected the trailing record last, got: " + result.errorOutput(),
				result.errorOutput().endsWith("|end\n"));
	}

	@Test
	public void closingDevStdoutLeavesItUsable() throws Exception {
		// As in gawk: closing an open redirection reports success and the name
		// stays usable, while closing it twice reports the second close as an
		// error because no redirection is open on that name any more.
		AwkTestSupport.awkTest("close() of /dev/stdout succeeds without closing the stream")
				.script(
						"BEGIN {" + " print \"one\" > \"/dev/stdout\";" + " print close(\"/dev/stdout\");"
								+ " print close(\"/dev/stdout\");" + " print \"two\" > \"/dev/stdout\"" + " }")
				.expectLines("one", "0", "-1", "two")
				.runAndAssert();
	}

	@Test
	public void closingAnUnusedSpecialFileNameFails() throws Exception {
		AwkTestSupport.awkTest("close() of a special filename no redirection is open on fails")
				.script("BEGIN { print close(\"/dev/stderr\"), close(\"/dev/stdout\"), close(\"/dev/stdin\") }")
				.expectLines("-1 -1 -1")
				.runAndAssert();
	}

	@Test
	public void closingDevStderrLeavesItUsable() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("close() of /dev/stderr succeeds and the name stays usable")
				.script(
						"BEGIN {" + " print \"one\" > \"/dev/stderr\";" + " print close(\"/dev/stderr\");"
								+ " print \"two\" > \"/dev/stderr\"" + " }")
				.run();
		result.assertExpected();
		assertEquals("0\n", result.output());
		assertEquals("one\ntwo\n", result.errorOutput());
	}

	@Test
	public void getlineFromDevStdinReadsStandardInput() throws Exception {
		AwkTestSupport.awkTest("getline < \"/dev/stdin\" reads the standard input")
				.stdin("alpha\nbeta\n")
				.script("BEGIN { while ((getline line < \"/dev/stdin\") > 0) print \"got:\" line }")
				.expectLines("got:alpha", "got:beta")
				.runAndAssert();
	}

	@Test
	public void getlineFromDevFd0ReadsStandardInput() throws Exception {
		AwkTestSupport.awkTest("/dev/fd/0 is a spelling of /dev/stdin")
				.stdin("alpha\n")
				.script("BEGIN { getline line < \"/dev/fd/0\"; print \"got:\" line }")
				.expectLines("got:alpha")
				.runAndAssert();
	}

	@Test
	public void getlineFromDevStdinSplitsFields() throws Exception {
		AwkTestSupport.awkTest("getline from /dev/stdin into $0 splits fields with FS")
				.stdin("a b c\n")
				.script("BEGIN { getline < \"/dev/stdin\"; print NF, $2, $0 }")
				.expectLines("3 b a b c")
				.runAndAssert();
	}

	@Test
	public void closingDevStdinReportsTheOpenRedirection() throws Exception {
		// close() reports the open redirection, then reports that there is none
		// left, exactly as gawk does. Whether a later getline still sees data is
		// deliberately not asserted: as in gawk, the records the record reader had
		// already buffered are gone with it.
		AwkTestSupport.awkTest("close() of /dev/stdin reports the open redirection")
				.stdin("alpha\nbeta\n")
				.script(
						"BEGIN {" + " getline first < \"/dev/stdin\";" + " print first;" + " print close(\"/dev/stdin\");"
								+ " print close(\"/dev/stdin\")" + " }")
				.expectLines("alpha", "0", "-1")
				.runAndAssert();
	}

	@Test
	public void readingDevStdinAfterCloseReportsEndOfInputNotAnError() throws Exception {
		// The stream behind /dev/stdin is never closed, so reopening it is not an
		// error: the reopened redirection simply reports end of input once the
		// stream has no more data, which is what gawk reports too.
		AwkTestSupport.awkTest("/dev/stdin can be reopened after close()")
				.stdin("alpha\n")
				.script(
						"BEGIN {" + " getline first < \"/dev/stdin\";" + " close(\"/dev/stdin\");"
								+ " print first, (getline second < \"/dev/stdin\")" + " }")
				.expectLines("alpha 0")
				.runAndAssert();
	}

	@Test
	public void getlineFromDevStdinIsIndependentOfTheMainInputFile() throws Exception {
		AwkTestSupport.awkTest("/dev/stdin stays readable while an operand file feeds the main loop")
				.stdin("from stdin\n")
				.file("operand", "from file\n")
				.operand("{{operand}}")
				.script("{ record = $0; getline extra < \"/dev/stdin\"; print record, \"+\", extra }")
				.expectLines("from file + from stdin")
				.runAndAssert();
	}

	@Test
	public void devStdinReadsTheCliStandardInput() throws Exception {
		AwkTestSupport.cliTest("the CLI wires /dev/stdin to its standard input")
				.stdin("piped\n")
				.script("BEGIN { getline line < \"/dev/stdin\"; print line }")
				.expectLines("piped")
				.runAndAssert();
	}

	@Test
	public void devStdinFollowsTheInputSourceOfEachExecution() throws Exception {
		// A reused runtime must bind /dev/stdin to the input source of the
		// execution that is starting, never to the stream of a previous one.
		AwkTestSupport.awkTest("/dev/stdin follows the input source of each execution")
				.script("BEGIN { getline line < \"/dev/stdin\"; print line }")
				.executionsOnSharedRuntime("one\n", "two\n")
				.expectLines("one", "two")
				.runAndAssert();
	}

	@Test
	public void sandboxRejectsOutputRedirectionToSpecialFileNames() {
		Awk awk = new SandboxedAwk();
		assertThrows(AwkSandboxException.class, () -> awk.compile("BEGIN { print \"hi\" > \"/dev/stderr\" }"));
		assertThrows(AwkSandboxException.class, () -> awk.compile("BEGIN { print \"hi\" > \"/dev/stdout\" }"));
		assertThrows(AwkSandboxException.class, () -> awk.compile("BEGIN { printf \"%s\", \"hi\" > \"/dev/fd/2\" }"));
	}

	@Test
	public void sandboxRejectsInputRedirectionFromSpecialFileNames() {
		Awk awk = new SandboxedAwk();
		assertThrows(AwkSandboxException.class, () -> awk.compile("BEGIN { getline x < \"/dev/stdin\" }"));
		assertThrows(AwkSandboxException.class, () -> awk.compile("BEGIN { getline x < \"/dev/fd/0\" }"));
	}
}
