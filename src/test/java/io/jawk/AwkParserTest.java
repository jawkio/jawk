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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import io.jawk.frontend.ast.LexerException;
import io.jawk.frontend.ast.ParserException;
import io.jawk.util.AwkSettings;

public class AwkParserTest {

	private static final Awk AWK = new Awk();

	private static InputStream scriptResource(String resource) throws IOException {
		InputStream stream = AwkParserTest.class.getResourceAsStream(resource);
		if (stream == null) {
			throw new IOException("Resource not found: " + resource);
		}
		return stream;
	}

	@Test
	public void testStringParsing() throws Exception {
		assertEquals("'\\\\' must become \\", "\\", AWK.eval("\"\\\\\" "));
		assertEquals("'\\a' must become BEL", "\u0007", AWK.eval("\"\\a\" "));
		assertEquals("'\\b' must become BS", "\u0008", AWK.eval("\"\\b\" "));
		assertEquals("'\\f' must become FF", "\014", AWK.eval("\"\\f\" "));
		assertEquals("'\\n' must become LF", "\n", AWK.eval("\"\\n\" "));
		assertEquals("'\\r' must become CR", "\r", AWK.eval("\"\\r\" "));
		assertEquals("'\\t' must become TAB", "\t", AWK.eval("\"\\t\" "));
		assertEquals("'\\v' must become VT", "\u000B", AWK.eval("\"\\v\" "));
		assertEquals("'\\33' must become ESC", "\u001B", AWK.eval("\"\\33\" "));
		assertEquals("'\\1!' must become {0x01, 0x21}", "\u0001!", AWK.eval("\"\\1!\" "));
		assertEquals("'\\19' must become {0x01, 0x39}", "\u00019", AWK.eval("\"\\19\" "));
		assertEquals("'\\38' must become {0x03, 0x38}", "\u00038", AWK.eval("\"\\38\" "));
		assertEquals("'\\132' must become Z", "Z", AWK.eval("\"\\132\" "));
		assertEquals("'\\1320' must become Z0", "Z0", AWK.eval("\"\\1320\" "));
		assertEquals("'\\\"' must become \"", "\"", AWK.eval("\"\\\"\" "));
		assertEquals("'\\x1B' must become ESC", "\u001B", AWK.eval("\"\\x1B\" "));
		assertEquals("'\\x1b' must become ESC", "\u001B", AWK.eval("\"\\x1b\" "));
		assertEquals("'\\x1!' must become {0x01, 0x21}", "\u0001!", AWK.eval("\"\\x1!\" "));
		assertEquals("'\\x1G' must become {0x01, 0x47}", "\u0001G", AWK.eval("\"\\x1G\" "));
		assertEquals("'\\x21A' must become !A", "!A", AWK.eval("\"\\x21A\" "));
		assertEquals("'\\x!' must become x!", "x!", AWK.eval("\"\\x!\" "));
		AwkTestSupport
				.awkTest("Unfinished string by EOF must throw")
				.script("BEGIN { printf \"unfinished")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows(
				"Unfinished string by EOL must throw",
				LexerException.class,
				() -> AWK.eval("\"unfinished\n\""));
		AwkTestSupport
				.awkTest("Interrupted octal number in string by EOF must throw")
				.script("BEGIN { printf \"unfinished\\0")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows(
				"Interrupted octal number in string by EOL must throw",
				LexerException.class,
				() -> AWK.eval("\"unfinished\\0\n\""));
		AwkTestSupport
				.awkTest("Interrupted hex number in string by EOF must throw")
				.script("BEGIN { printf \"unfinished\\xF")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows(
				"Interrupted hex number in string by EOL must throw",
				LexerException.class,
				() -> AWK.eval("\"unfinished\\xf\n\""));
	}

	@Test
	public void testMultiLineStatement() throws Exception {
		AwkTestSupport
				.awkTest("|| must allow eol")
				.script("BEGIN { if (0 || \n    1) { printf \"success\" } }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("&& must allow eol")
				.script("BEGIN { if (1 && \n    1) { printf \"success\" } }")
				.expect("success")
				.runAndAssert();
		assertEquals("? must allow eol", "success", AWK.eval("1 ?\n\"success\" : \"failed\" "));
		assertEquals(": must allow eol", "success", AWK.eval("1 ? \"success\" :\n\"failed\" "));
		AwkTestSupport
				.awkTest(", must allow eol")
				.script("BEGIN { printf(\"%s\", \n\"success\") }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("do must allow eol")
				.script("BEGIN { do\n printf \"success\"; while (0) }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("else must allow eol")
				.script("BEGIN { if (0) { printf \"failure\" } else \n printf \"success\" }")
				.expect("success")
				.runAndAssert();
	}

	@Test
	public void testUnaryPlus() throws Exception {
		assertEquals("+a must convert a to number", 0L, AWK.eval("+a "));
	}

	@Test
	public void testTernaryExpression() throws Exception {
		AwkTestSupport
				.awkTest("Ternary expression must allow string concatenations")
				.script("BEGIN { printf( a \"1\" b ? \"suc\" \"cess\" : \"failure\" ) }")
				.expect("success")
				.runAndAssert();
	}

	@Test
	public void testNestedTernaryExpression() throws Exception {
		assertEquals(
				"Nested ternary must parse correctly",
				2L,
				AWK.eval("1 ? 2 : 3 ? 4 : 5 "));
	}

	@Test
	public void testTernaryAfterPrintParentheses() throws Exception {
		AwkTestSupport
				.awkTest("Ternary after print parentheses must parse")
				.script("BEGIN { print (1>2) ? 10 : 20 }")
				.expectLines("20")
				.runAndAssert();
	}

	@Test
	public void testGron() throws Exception {
		AwkTestSupport
				.awkTest("gron.awk must not trigger any parser exception")
				.script(scriptResource("/xonixx/gron.awk"))
				.stdin("[]")
				.expectLines("json=[]")
				.runAndAssert();
		AwkTestSupport
				.awkTest("gron.awk must work")
				.script(scriptResource("/xonixx/gron.awk"))
				.stdin("[{\"a\": 1},\n{\"b\": \"2\"}]")
				.expectLines("json=[]", "json[0]={}", "json[0].a=1", "json[1]={}", "json[1].b=\"2\"")
				.runAndAssert();
	}

	@Test
	public void testPow() throws Exception {
		assertEquals("^ (pow) operator must be supported", 256L, AWK.eval("2^8 "));
		assertEquals("** (pow) operator must be supported", 256L, AWK.eval("2**8 "));
	}

	@Test
	public void testPowAssignment() throws Exception {
		AwkTestSupport
				.awkTest("^= must be supported")
				.script("BEGIN { n = 2; n ^= 2; print n }")
				.expectLines("4")
				.runAndAssert();
		AwkTestSupport
				.awkTest("**= must be supported")
				.script("BEGIN { n = 2; n **= 2; print n }")
				.expectLines("4")
				.runAndAssert();
	}

	@Test
	public void testArraysOfArraysCanBeDisabled() {
		AwkSettings settings = new AwkSettings();
		settings.setAllowArraysOfArrays(false);
		Awk awk = new Awk(settings);

		assertThrows(ParserException.class, () -> awk.compile("BEGIN { a[1][2] = 42 }"));
	}

	@Test
	public void testOperatorPrecedence() throws Exception {
		AwkTestSupport
				.awkTest("$a precedes a++")
				.script("{ a = 1; printf $a++ ; printf a ; printf $(a++) ; printf a }")
				.stdin("1 2 3")
				.expect("1122")
				.runAndAssert();
		AwkTestSupport
				.awkTest("$a precedes ++a")
				.script("{ a = 1; printf $++a ; printf a ; printf $(++a) ; printf a }")
				.stdin("1 2 3")
				.expect("2233")
				.runAndAssert();
		AwkTestSupport
				.awkTest("$a precedes a--")
				.script("{ a = 3; printf $a-- ; printf a ; printf $(a--) ; printf a }")
				.stdin("1 2 3")
				.expect("3322")
				.runAndAssert();
		AwkTestSupport
				.awkTest("$a precedes --a")
				.script("{ a = 3; printf $--a ; printf a ; printf $(--a) ; printf a }")
				.stdin("1 2 3")
				.expect("2211")
				.runAndAssert();
		AwkTestSupport
				.awkTest("++ precedes ^")
				.script("BEGIN { a = 1; printf(2^a++); printf a }")
				.expect("22")
				.runAndAssert();
		assertEquals("^ precedes unary -", -1L, AWK.eval("-1^2"));
		assertEquals("^ precedes unary !", 1, AWK.eval("!0^2"));
		assertEquals("Unary - precedes *", -2L, AWK.eval("0 + -1 * 2"));
		assertEquals("* precedes +", 5L, AWK.eval("1 + 2 * 2"));
		assertEquals("+ precedes string concat", "33", AWK.eval("1 + 2 3"));
	}

	@Test
	public void testRegExpConstant() throws Exception {
		AwkTestSupport
				.awkTest("/\\\\/ must be supported")
				.script("/\\\\/ { printf \"success\" }")
				.stdin("a\\b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("/\\// must be supported")
				.script("/\\// { printf \"success\" }")
				.stdin("a/b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("/=1/ must be supported")
				.script("/=1/ { printf \"success\" }")
				.stdin("a=1\n1\n=")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("/\\057/ must be supported")
				.script("/\\057/ { printf \"success\" }")
				.stdin("a/b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport
				.awkTest("Unfinished regexp by EOF must throw")
				.script("/unfinished { print $0 }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows(
				"Unfinished regexp by EOL must throw",
				LexerException.class,
				() -> AWK.eval("/unfinished\n/"));
	}
}
