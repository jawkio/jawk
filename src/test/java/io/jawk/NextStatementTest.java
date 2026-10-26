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
 * Tests for the {@code next} statement executed from user-defined functions.
 * gawk (including POSIX mode), mawk, and BWK awk all accept {@code next}
 * inside a function: when the function is called from an input rule, it
 * abandons the current record, unwinds the active function calls, and resumes
 * the main input loop; when the caller is a BEGIN, END, BEGINFILE, or ENDFILE
 * rule, it is a runtime fatal error. Direct uses in rules are unaffected:
 * a plain jump in input rules, a compile-time error in special rules.
 */
public class NextStatementTest {

	@Test
	public void nextInFunctionSkipsToTheNextRecord() throws Exception {
		AwkTestSupport.awkTest("next in a function abandons the current record")
				.script(
						"function f() { next; print \"in-f\" }" + " { f(); print \"unreached\" }" + " END { print \"end\", NR }")
				.stdin("a\nb\n")
				.expectLines("end 2")
				.runAndAssert();
	}

	@Test
	public void nextInFunctionUnwindsNestedCalls() throws Exception {
		AwkTestSupport.awkTest("next in a function unwinds the whole call chain")
				.script(
						"function g() { f(); print \"g\" }" + " function f() { next }" + " { g(); print \"r\" }"
								+ " END { print NR }")
				.stdin("a\nb\n")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void nextInFunctionSkipsTheRemainingRules() throws Exception {
		AwkTestSupport.awkTest("next in a function bypasses the later rules for the record")
				.script("function f() { next } NR == 1 { f() } { print $0 }")
				.stdin("a\nb\n")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void nextInFunctionWorksWithPerFileRules() throws Exception {
		AwkTestSupport.awkTest("next in a function resumes the per-file input loop")
				.script(
						"function f() { if ($0 ~ /2/) next }" + " BEGINFILE { print \"bf\" }" + " { f(); print \"r:\" $0 }"
								+ " ENDFILE { print \"ef\" }")
				.file("f1", "a1\na2\n")
				.file("f2", "b1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("bf", "r:a1", "ef", "bf", "r:b1", "ef")
				.runAndAssert();
	}

	@Test
	public void nextInBeginViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport.awkTest("next reached from a BEGIN rule through a function is fatal")
				.script("function f() { next } BEGIN { f() } { print }")
				.stdin("x\n")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextWithoutMainInputLoopViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport.awkTest("next in a function is fatal when the program consumes no input")
				.script("function f() { next } BEGIN { f() }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextInEndViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport.awkTest("next reached from an END rule through a function is fatal")
				.script("function f() { next } { } END { f() }")
				.stdin("x\n")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextInBeginFileViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport.awkTest("next reached from a BEGINFILE rule through a function is fatal")
				.script("function f() { next } BEGINFILE { f() } { print }")
				.file("f1", "x\n")
				.operand("{{f1}}")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void nextInEndFileViaFunctionIsARuntimeError() throws Exception {
		AwkTestSupport.awkTest("next reached from an ENDFILE rule through a function is fatal")
				.script("function f() { next } ENDFILE { f() } { print }")
				.file("f1", "x\n")
				.operand("{{f1}}")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void directNextInBeginAndEndRemainsACompileTimeError() throws Exception {
		AwkTestSupport.awkTest("next directly inside a BEGIN rule stays a compile-time error")
				.script("BEGIN { next } { print }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("next directly inside an END rule stays a compile-time error")
				.script("{ print } END { next }")
				.stdin("x\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}
}
