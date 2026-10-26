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
		AwkTestSupport.awkTest("Unfinished string by EOF must throw")
				.script("BEGIN { printf \"unfinished")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows("Unfinished string by EOL must throw", LexerException.class, () -> AWK.eval("\"unfinished\n\""));
		AwkTestSupport.awkTest("Interrupted octal number in string by EOF must throw")
				.script("BEGIN { printf \"unfinished\\0")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows(
				"Interrupted octal number in string by EOL must throw",
				LexerException.class,
				() -> AWK.eval("\"unfinished\\0\n\""));
		AwkTestSupport.awkTest("Interrupted hex number in string by EOF must throw")
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
		AwkTestSupport.awkTest("|| must allow eol")
				.script("BEGIN { if (0 || \n    1) { printf \"success\" } }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("&& must allow eol")
				.script("BEGIN { if (1 && \n    1) { printf \"success\" } }")
				.expect("success")
				.runAndAssert();
		assertEquals("? must allow eol", "success", AWK.eval("1 ?\n\"success\" : \"failed\" "));
		assertEquals(": must allow eol", "success", AWK.eval("1 ? \"success\" :\n\"failed\" "));
		AwkTestSupport.awkTest(", must allow eol")
				.script("BEGIN { printf(\"%s\", \n\"success\") }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("do must allow eol")
				.script("BEGIN { do\n printf \"success\"; while (0) }")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("else must allow eol")
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
		AwkTestSupport.awkTest("Ternary expression must allow string concatenations")
				.script("BEGIN { printf( a \"1\" b ? \"suc\" \"cess\" : \"failure\" ) }")
				.expect("success")
				.runAndAssert();
	}

	@Test
	public void testNestedTernaryExpression() throws Exception {
		assertEquals("Nested ternary must parse correctly", 2L, AWK.eval("1 ? 2 : 3 ? 4 : 5 "));
	}

	@Test
	public void testTernaryAfterPrintParentheses() throws Exception {
		AwkTestSupport.awkTest("Ternary after print parentheses must parse")
				.script("BEGIN { print (1>2) ? 10 : 20 }")
				.expectLines("20")
				.runAndAssert();
	}

	@Test
	public void testPrintParenthesizedExpressionList() throws Exception {
		AwkTestSupport.awkTest("A parenthesized print argument followed by a comma continues the list")
				.script("BEGIN { i = \"\"; print (i==0), (i==\"\") }")
				.expectLines("0 1")
				.runAndAssert();
		AwkTestSupport.awkTest("Uninitialized variables compare equal to both 0 and the null string")
				.script("BEGIN { print (i==0), (i==\"\") }")
				.expectLines("1 1")
				.runAndAssert();
		AwkTestSupport.awkTest("Several parenthesized print arguments must parse")
				.script("BEGIN { print (1), (2), (3) }")
				.expectLines("1 2 3")
				.runAndAssert();
		AwkTestSupport.awkTest("A concatenated parenthesized print argument can continue the list")
				.script("BEGIN { print (1) (2), 3 }")
				.expectLines("12 3")
				.runAndAssert();
		AwkTestSupport.awkTest("A newline may follow the comma after a parenthesized print argument")
				.script("BEGIN { print (1),\n2 }")
				.expectLines("1 2")
				.runAndAssert();
		AwkTestSupport.awkTest("printf also continues the list after a parenthesized first argument")
				.script("BEGIN { printf (\"%s-%s\\n\"), \"a\", \"b\" }")
				.expect("a-b\n")
				.runAndAssert();
		AwkTestSupport.awkTest("A parenthesized group of several expressions cannot continue the list")
				.script("BEGIN { print (1,2), 3 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Extra parentheses around a comma group cannot continue the list either")
				.script("BEGIN { print ((1,2)), 3 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group consumed by 'in' can continue the list")
				.script("BEGIN { a[1,2]=5; print ((1,2) in a), \"x\" }")
				.expectLines("1 x")
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group wrapped by an operator cannot continue the list")
				.script("BEGIN { print ((1,2)+0), 3 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group wrapped by a concatenation cannot continue the list")
				.script("BEGIN { print ((1,2) \"\"), 3 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void testCommaGroupOnlyValidBeforeIn() throws Exception {
		AwkTestSupport.awkTest("A comma group cannot be assigned")
				.script("BEGIN { x = (1,2) }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group cannot be a function argument")
				.script("function f(x) { return x }\nBEGIN { print f((1,2)) }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A doubly parenthesized comma group cannot precede 'in'")
				.script("BEGIN { a[1,2]=5; print (((1,2)) in a) }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group before 'in' works in a condition")
				.script("BEGIN { a[1,2]=5; if ((1,2) in a) print \"yes\" }")
				.expectLines("yes")
				.runAndAssert();
		AwkTestSupport.awkTest("A comma group cannot be an assignment right-hand side")
				.script("BEGIN { x = (y = 1, 2); print x, y }")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("An assignment can be the first element of a comma group before 'in'")
				.script("BEGIN { a[1,2]=5; if ((y=1,2) in a) print \"yes\", y }")
				.expectLines("yes 1")
				.runAndAssert();
		AwkTestSupport.awkTest("An assignment can be a later element of a comma group before 'in'")
				.script("BEGIN { a[1,2]=5; if ((1, y=2) in a) print \"yes\", y }")
				.expectLines("yes 2")
				.runAndAssert();
		AwkTestSupport.awkTest("A parenthesized 'in' membership test is a valid expression")
				.script("BEGIN { a[1,2]=1; x = ((1,2) in a); print x }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A doubly parenthesized 'in' membership test can be printed")
				.script("BEGIN { a[1,2]=1; print (((1,2) in a)) }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A nested comma group is rejected")
				.script("BEGIN { a[1,2,3]=5; if ((1, (2,3)) in a) print \"yes\" }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void testPrintMembershipAfterParenthesizedGroup() throws Exception {
		AwkTestSupport.awkTest("A parenthesized print group followed by 'in' is a membership key")
				.script("BEGIN { a[1,2]=9; print (1,2) in a }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A three-element group followed by 'in' is a membership key")
				.script("BEGIN { a[1,2,3]=9; print (1,2,3) in a }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A membership test after a group can continue the output list")
				.script("BEGIN { a[1,2]=9; print (1,2) in a, \"x\" }")
				.expectLines("1 x")
				.runAndAssert();
		AwkTestSupport.awkTest("A single parenthesized expression followed by 'in' is a membership key")
				.script("BEGIN { a[1]=9; print (1) in a }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("printf also accepts a membership test after a parenthesized group")
				.script("BEGIN { a[1,2]=9; printf (1,2) in a }")
				.expect("1")
				.runAndAssert();
	}

	@Test
	public void testGron() throws Exception {
		AwkTestSupport.awkTest("gron.awk must not trigger any parser exception")
				.script(scriptResource("/xonixx/gron.awk"))
				.stdin("[]")
				.expectLines("json=[]")
				.runAndAssert();
		AwkTestSupport.awkTest("gron.awk must work")
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
		AwkTestSupport.awkTest("^= must be supported")
				.script("BEGIN { n = 2; n ^= 2; print n }")
				.expectLines("4")
				.runAndAssert();
		AwkTestSupport.awkTest("**= must be supported")
				.script("BEGIN { n = 2; n **= 2; print n }")
				.expectLines("4")
				.runAndAssert();
	}

	@Test
	public void testArraysOfArraysCanBeDisabled() {
		AwkSettings settings = new AwkSettings();
		settings.setPosix(true);
		Awk awk = new Awk(settings);

		assertThrows(ParserException.class, () -> awk.compile("BEGIN { a[1][2] = 42 }"));
		assertThrows(RuntimeException.class, () -> awk.compile("BEGIN { print ((\"x\" in a[1]) ? 1 : 0) }"));
		assertThrows(RuntimeException.class, () -> awk.compile("BEGIN { for (k in a[1]) print k }"));
	}

	@Test
	public void testIndirectFunctionCalls() throws Exception {
		AwkTestSupport.awkTest("Indirect calls dispatch user functions and builtins")
				.script(
						"function twice(value) { return value * 2 }\n"
								+ "BEGIN { user = \"twice\"; builtin = \"length\"; print @user(21), @builtin(\"jawk\") }")
				.expectLines("42 4")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect length without arguments measures the current record")
				.script("{ callback = \"length\"; print @callback() }")
				.stdin("abcde\n")
				.expectLines("5")
				.runAndAssert();
		AwkTestSupport.awkTest("Qualified variables can select indirect functions")
				.script(
						"@namespace \"ns\"\n" + "function twice(value) { return value * 2 }\n"
								+ "BEGIN { callback = \"ns::twice\"; print @ns::callback(5) }")
				.expectLines("10")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect srand converts a fractional string like direct srand")
				.script(
						"BEGIN { callback = \"srand\"; first = @callback(\"3.5\"); a = rand(); "
								+ "second = srand(\"3.5\"); b = rand(); print first, second, (a == b) }")
				.expectLines("1 3 1")
				.runAndAssert();
		AwkTestSupport.awkTest("srand returns the previously requested seed")
				.script("BEGIN { print srand(0), srand(5), srand(0) }")
				.expectLines("1 0 5")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect split materializes an untyped array destination")
				.script(
						"BEGIN { callback = \"split\"; count = @callback(\"a b\", parts); " + "print count, parts[1], parts[2] }")
				.expectLines("2 a b")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect user calls preserve array parameters")
				.script(
						"function fill(values) { values[1] = 42; return values[1] }\n"
								+ "BEGIN { callback = \"fill\"; print @callback(parts), parts[1] }")
				.expectLines("42 42")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect user calls materialize subarray parameters")
				.script(
						"function fill(values) { values[1] = 42 }\n" + "BEGIN { callback = \"fill\"; @callback(parts[\"nested\"]); "
								+ "print parts[\"nested\"][1] }")
				.expectLines("42")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect split materializes subarray parameters")
				.script(
						"BEGIN { callback = \"split\"; count = @callback(\"a b\", parts[\"nested\"]); "
								+ "print count, parts[\"nested\"][1], parts[\"nested\"][2] }")
				.expectLines("2 a b")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect selectors are evaluated before arguments")
				.script(
						"function f(a, b) { print \"f\", a, b }\n" + "function g(a, b) { print \"g\", a, b }\n"
								+ "BEGIN { callback = \"f\"; @callback(1, callback = \"g\") }")
				.expectLines("f 1 g")
				.runAndAssert();
		AwkTestSupport.awkTest("Indirect scalar arguments retain their evaluation-time values")
				.script("function f(a, b) { print a, b }\n" + "BEGIN { x = 1; callback = \"f\"; @callback(x, x = 2) }")
				.expectLines("1 2")
				.runAndAssert();
		AwkTestSupport.awkTest("Keywords cannot be literal indirect-call selectors")
				.script("BEGIN { print @if(1) }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Builtins cannot be literal indirect-call selectors")
				.script("BEGIN { print @length(1) }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Keywords cannot be qualified literal indirect-call selectors")
				.script("BEGIN { print @ns::if(1) }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Extension names remain valid qualified selector variables")
				.script("BEGIN { ns::typeof = \"length\"; print @ns::typeof(\"abc\") }")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void testNamespaces() throws Exception {
		AwkTestSupport.awkTest("Namespaces qualify functions and indirect call targets")
				.script(
						"@namespace \"lib\"\n" + "function value() { return 42 }\n" + "@namespace \"app\"\n"
								+ "function value() { return 7 }\n"
								+ "BEGIN { target = \"app::value\"; print lib::value(), @target() }")
				.expectLines("42 7")
				.runAndAssert();
		AwkTestSupport.awkTest("Unqualified indirect targets stay in the awk namespace")
				.script(
						"@namespace \"app\"\n" + "function value() { return 7 }\n"
								+ "BEGIN { target = \"value\"; print @target() }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Only identifiers made entirely of uppercase letters stay in awk")
				.script(
						"@namespace \"ns\"\n" + "BEGIN { MY_VAR = 1; F1 = 2; ABC = 3; "
								+ "print ns::MY_VAR, ns::F1, awk::ABC, awk::MY_VAR == \"\" }")
				.expectLines("1 2 3 1")
				.runAndAssert();
		AwkTestSupport.awkTest("Standard builtins remain unqualified inside namespaces")
				.script("@namespace \"ns\"\nBEGIN { print length(\"abc\"), int(3.5) }")
				.expectLines("3 3")
				.runAndAssert();
		AwkTestSupport.awkTest("Builtin names cannot be redefined inside namespaces")
				.script("@namespace \"ns\"\nfunction length(value) { return value }\n")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Builtin names cannot be used as namespaces")
				.script("@namespace \"length\"\nBEGIN { print 1 }\n")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Extension names cannot be used as namespaces")
				.script("@namespace \"typeof\"\nBEGIN { print 1 }\n")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Reserved words cannot follow namespace separators")
				.script("BEGIN { ns::if = 3 }\n")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Builtin names cannot follow namespace separators")
				.script("BEGIN { ns::length = 3 }\n")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("A parameter may follow an unrelated function with the same name")
				.script(
						"function awk::f() { return 1 }\n" + "@namespace \"ns\"\n" + "function g(f) { return f }\n"
								+ "BEGIN { print g(7), awk::f() }\n")
				.expectLines("7 1")
				.runAndAssert();
		AwkTestSupport.awkTest("A parameter may precede an unrelated function with the same name")
				.script(
						"@namespace \"ns\"\n" + "function g(f) { return f }\n" + "function awk::f() { return 1 }\n"
								+ "BEGIN { print g(7), awk::f() }\n")
				.expectLines("7 1")
				.runAndAssert();
		AwkTestSupport.awkTest("A parameter cannot match its own function name")
				.script("function f(f) { return f }\n")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.cliTest("The awk namespace is accepted in command-line assignments")
				.argument("-v", "awk::VALUE=42")
				.script("BEGIN { print awk::VALUE }")
				.expectLines("42")
				.runAndAssert();
		AwkTestSupport.cliTest("Qualified command-line operand assignments are accepted")
				.file("input.txt", "record\n")
				.script("@namespace \"ns\"\n{ print value, $0 }")
				.operand("ns::value=42", "{{input.txt}}")
				.expectLines("42 record")
				.runAndAssert();
		AwkTestSupport.cliTest("The awk namespace is accepted in operand assignments")
				.file("input.txt", "record\n")
				.script("@namespace \"ns\"\n{ print awk::value, $0 }")
				.operand("awk::value=42", "{{input.txt}}")
				.expectLines("42 record")
				.runAndAssert();
	}

	@Test
	public void testTernaryIdentifiersAdjacentToColon() throws Exception {
		AwkTestSupport.awkTest("A tight ternary can end its true branch with an identifier")
				.script("BEGIN { x = 1; y = 2; print (x < y?x:y) }")
				.expectLines("1")
				.runAndAssert();
		AwkTestSupport.awkTest("A qualified identifier can precede a ternary colon")
				.script("@namespace \"ns\"\nBEGIN { cond = 1; x = 7; y = 9; print (cond?ns::x:y) }")
				.expectLines("7")
				.runAndAssert();
		AwkTestSupport.awkTest("A tight ternary false branch can start with a parenthesis")
				.script("BEGIN { x = 5; print (1?x:(2)) }")
				.expectLines("5")
				.runAndAssert();
		AwkTestSupport.awkTest("A qualified tight ternary false branch can start with a parenthesis")
				.script("@namespace \"ns\"\nBEGIN { x = 7; print (1?ns::x:(9)) }")
				.expectLines("7")
				.runAndAssert();
	}

	@Test
	public void testIncludeDirective() throws Exception {
		AwkTestSupport.cliTest("Include resolves relative to the main source and restores its namespace")
				.file(
						"main.awk",
						"@namespace \"main\"\n" + "@include \"empty.awk\"\n" + "@include \"lib.awk\"\n"
								+ "function answer() { return 7 }\n" + "BEGIN { print lib::answer(), answer() }\n")
				.file("lib.awk", "@namespace \"lib\"\n" + "function answer() { return 42 }\n")
				.file("empty.awk", "")
				.argument("-f", "{{main.awk}}")
				.expectLines("42 7")
				.runAndAssert();
		AwkTestSupport.cliTest("An include without a final newline restores its parent's namespace")
				.file(
						"main.awk",
						"@namespace \"main\"\n" + "@include \"lib.awk\"\n" + "function answer() { return 7 }\n"
								+ "BEGIN { print main::answer() }\n")
				.file("lib.awk", "@namespace \"lib\"")
				.argument("-f", "{{main.awk}}")
				.expectLines("7")
				.runAndAssert();
		AwkTestSupport.cliTest("A top-level source cannot include itself")
				.file("main.awk", "@include \"main.awk\"\nBEGIN { print 1 }\n")
				.argument("-f", "{{main.awk}}")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.cliTest("An include cycle cannot include its top-level source")
				.file("main.awk", "@include \"lib.awk\"\nBEGIN { print 1 }\n")
				.file("lib.awk", "@include \"main.awk\"\nBEGIN { print 2 }\n")
				.argument("-f", "{{main.awk}}")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.cliTest("An include directive requires a statement terminator")
				.file("main.awk", "@include \"lib.awk\" BEGIN { print 1 }\n")
				.file("lib.awk", "BEGIN { print 2 }\n")
				.argument("-f", "{{main.awk}}")
				.expectThrow(ParserException.class)
				.runAndAssert();
		AwkTestSupport.cliTest("A semicolon terminates an include directive")
				.file("main.awk", "@include \"lib.awk\"; BEGIN { print 1 }\n")
				.file("lib.awk", "BEGIN { print 2 }\n")
				.argument("-f", "{{main.awk}}")
				.expectLines("2", "1")
				.runAndAssert();
	}

	@Test
	public void testIncludeCanonicalizesSymlinkAliases() throws Exception {
		AwkTestSupport.cliTest("Symlink aliases include the underlying file only once")
				.file("main.awk", "@include \"lib.awk\"\n" + "@include \"alias.awk\"\n")
				.file("lib.awk", "BEGIN { print \"included\" }\n")
				.symlink("alias.awk", "lib.awk")
				.argument("-f", "{{main.awk}}")
				.expectLines("included")
				.runAndAssert();
	}

	@Test
	public void testAtSyntaxCanBeDisabled() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.setPosix(true);

		AwkTestSupport.awkTest("gawk @ syntax is unavailable in POSIX mode")
				.withAwk(new Awk(settings))
				.script("@namespace \"example\"\nBEGIN { print 1 }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("gawk namespace-qualified names are unavailable in POSIX mode")
				.withAwk(new Awk(settings))
				.script("BEGIN { example::value = 1 }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		AwkTestSupport.awkTest("Ternary colons remain available in POSIX mode")
				.withAwk(new Awk(settings))
				.script("BEGIN { yes = 1; no = 2; print (1?yes:no) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void testUnsupportedAtDirective() throws Exception {
		AwkTestSupport.awkTest("Unsupported gawk @ directives fail intentionally")
				.script("@load \"example\"\nBEGIN { print 1 }")
				.expectThrow(ParserException.class)
				.runAndAssert();
	}

	@Test
	public void testOperatorPrecedence() throws Exception {
		AwkTestSupport.awkTest("$a precedes a++")
				.script("{ a = 1; printf $a++ ; printf a ; printf $(a++) ; printf a }")
				.stdin("1 2 3")
				.expect("1122")
				.runAndAssert();
		AwkTestSupport.awkTest("$a precedes ++a")
				.script("{ a = 1; printf $++a ; printf a ; printf $(++a) ; printf a }")
				.stdin("1 2 3")
				.expect("2233")
				.runAndAssert();
		AwkTestSupport.awkTest("$a precedes a--")
				.script("{ a = 3; printf $a-- ; printf a ; printf $(a--) ; printf a }")
				.stdin("1 2 3")
				.expect("3322")
				.runAndAssert();
		AwkTestSupport.awkTest("$a precedes --a")
				.script("{ a = 3; printf $--a ; printf a ; printf $(--a) ; printf a }")
				.stdin("1 2 3")
				.expect("2211")
				.runAndAssert();
		AwkTestSupport.awkTest("++ precedes ^")
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
		AwkTestSupport.awkTest("/\\\\/ must be supported")
				.script("/\\\\/ { printf \"success\" }")
				.stdin("a\\b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("/\\// must be supported")
				.script("/\\// { printf \"success\" }")
				.stdin("a/b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("/=1/ must be supported")
				.script("/=1/ { printf \"success\" }")
				.stdin("a=1\n1\n=")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("/\\057/ must be supported")
				.script("/\\057/ { printf \"success\" }")
				.stdin("a/b")
				.expect("success")
				.runAndAssert();
		AwkTestSupport.awkTest("Unfinished regexp by EOF must throw")
				.script("/unfinished { print $0 }")
				.expectThrow(LexerException.class)
				.runAndAssert();
		assertThrows("Unfinished regexp by EOL must throw", LexerException.class, () -> AWK.eval("/unfinished\n/"));
	}
}
