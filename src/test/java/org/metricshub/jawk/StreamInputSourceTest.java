package org.metricshub.jawk;

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

import static org.metricshub.jawk.AwkTestSupport.awkTest;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Tests that confirm the default {@code StreamInputSource} path works
 * identically to the original stream-based input handling. These tests
 * exercise the unified input pipeline without a custom {@code InputSource}.
 */
public class StreamInputSourceTest {

	@Test
	public void testStdinReadsThroughStreamInputSource() throws Exception {
		awkTest("StreamInputSource reads stdin")
				.script("{ print $1, $2 }")
				.stdin("hello world\nfoo bar\n")
				.expectLines("hello world", "foo bar")
				.runAndAssert();
	}

	@Test
	public void testNrCountsRecordsFromStdin() throws Exception {
		awkTest("StreamInputSource NR counts stdin lines")
				.script("END { print NR }")
				.stdin("a\nb\nc\n")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void testFileInputThroughArgv() throws Exception {
		awkTest("StreamInputSource reads file from ARGV")
				.script("{ print $0 }")
				.file("data.txt", "line1\nline2\n")
				.operand("{{data.txt}}")
				.expectLines("line1", "line2")
				.runAndAssert();
	}

	@Test
	public void testFileInputThroughInjectedLongKeyArgvMap() throws Exception {
		Path file = Files.createTempFile(AwkTestSupport.sharedTempDirectory(), "argv-long-", ".txt");
		Files.write(file, "mapped".getBytes(StandardCharsets.UTF_8));
		Map<Object, Object> argv = new LinkedHashMap<>();
		argv.put(0L, "jawk");
		argv.put(1L, file.toString());

		awkTest("StreamInputSource reads injected ARGV map with Long keys")
				.script("BEGIN { _ = ARGV[0] } { print $0 }")
				.preassign("ARGV", argv)
				.file("operand.txt", "operand\n")
				.operand("{{operand.txt}}")
				.expectLines("mapped")
				.runAndAssert();
	}

	@Test
	public void testMultipleFilesWithFnrReset() throws Exception {
		awkTest("StreamInputSource resets FNR per file")
				.script("{ print NR, FNR, $0 }")
				.file("a.txt", "A1\nA2\n")
				.file("b.txt", "B1\n")
				.operand("{{a.txt}}")
				.operand("{{b.txt}}")
				.expectLines("1 1 A1", "2 2 A2", "3 1 B1")
				.runAndAssert();
	}

	@Test
	public void testVariableAssignmentInArgv() throws Exception {
		awkTest("StreamInputSource applies name=value operands")
				.script("{ print x, $0 }")
				.file("data.txt", "row1\n")
				.operand("x=42")
				.operand("{{data.txt}}")
				.expectLines("42 row1")
				.runAndAssert();
	}

	@Test
	public void testGetlineFromStdin() throws Exception {
		awkTest("StreamInputSource getline from stdin")
				.script("NR==1 { getline; print $0; exit }")
				.stdin("first\nsecond\nthird\n")
				.expectLines("second")
				.runAndAssert();
	}

	@Test
	public void testFsSplittingWorksWithStreamInputSource() throws Exception {
		awkTest("StreamInputSource respects FS")
				.script("BEGIN { FS = \",\" } { print $2 }")
				.stdin("a,b,c\nd,e,f\n")
				.expectLines("b", "e")
				.runAndAssert();
	}

	@Test
	public void testEmptyStdinRunsBeginAndEnd() throws Exception {
		awkTest("StreamInputSource empty stdin runs BEGIN/END")
				.script("BEGIN { print \"start\" } { print $0 } END { print \"end\" }")
				.stdin("")
				.expectLines("start", "end")
				.runAndAssert();
	}

	@Test
	public void testRsChangeAppliedToStreamInputSource() throws Exception {
		awkTest("RS change propagated to StreamInputSource")
				.script("BEGIN { RS = \";\" } { print NR, $0 }")
				.stdin("a;b;c")
				.expectLines("1 a", "2 b", "3 c")
				.runAndAssert();
	}

	@Test
	public void testExpressionEvalThroughStreamInputSource() throws Exception {
		awkTest("expression evaluation through StreamInputSource")
				.script("$1 > 1 { print $0 }")
				.stdin("1 low\n2 high\n3 higher\n")
				.expectLines("2 high", "3 higher")
				.runAndAssert();
	}

	@Test
	public void testMissingArgvFilePropagatesFileNotFoundException() throws Exception {
		awkTest("missing ARGV file propagates FileNotFoundException")
				.script("{ print $0 }")
				.path("missing.txt")
				.operand("{{missing.txt}}")
				.expectThrow(FileNotFoundException.class)
				.runAndAssert();
	}
}
