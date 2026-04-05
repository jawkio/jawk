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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import io.jawk.backend.AVM;
import io.jawk.frontend.ast.ParserException;
import io.jawk.jrt.AppendableAwkSink;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.OutputStreamAwkSink;
import io.jawk.util.AwkSettings;
import io.jawk.Cli;
import io.jawk.AwkSandboxException;
import io.jawk.SandboxedAwk;

public class AwkTest {

	private static final boolean IS_WINDOWS = (System.getProperty("os.name").contains("Windows"));

	@SafeVarargs
	static <T> T[] array(T... vals) {
		return vals;
	}

	static String[] monotoneArray(final String val, final int num) {
		return Collections.nCopies(num, val).toArray(new String[num]);
	}

	static File classpathFile(final Class<?> c, String path) {
		final URL resource = c.getResource(path);
		try {
			final File relative = resource == null ? new File(path) : Paths.get(resource.toURI()).toFile();
			return relative.getAbsoluteFile();
		} catch (final URISyntaxException e) {
			throw new IllegalStateException("Illegal URL " + resource, e);
		}
	}

	static String pathTo(String name) throws IOException {
		final File file = classpathFile(AwkTest.class, name);
		if (!file.exists())
			throw new FileNotFoundException(file.toString());
		return file.getPath();
	}

	private static final Awk AWK = new Awk();

	/**
	 * Tests the program
	 *
	 * <pre>
	 * $ awk 1 /dev/null
	 * </pre>
	 *
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Names>A Rose by Any Other Name</a>
	 */
	@Test
	public void test1() throws Exception {
		final String devnull = IS_WINDOWS ? pathTo("empty.txt") : "/dev/null";
		AwkTestSupport
				.cliTest("awk 1 /dev/null")
				.script("1")
				.operand(devnull)
				.runAndAssert();
	}

	/**
	 * Tests the program
	 *
	 * <pre>
	 * $ awk 'BEGIN { print "Don\47t Panic!" }'
	 * </pre>
	 *
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Read-Terminal">Running awk Without Input Files</a>
	 */
	@Test
	public void testDontPanic() throws Exception {
		AwkTestSupport
				.cliTest("print don't panic")
				.script("BEGIN { print \"Don\\47t Panic!\" }")
				.expectLines("Don't Panic!")
				.runAndAssert();
	}

	/**
	 * Tests the program
	 *
	 * <pre>
	 * $ awk -f advice.awk
	 * </pre>
	 *
	 * It should output
	 *
	 * <pre>
	 * Don't Panic!
	 * </pre>
	 *
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Read-Terminal>Running awk Without Input Files</a>
	 */
	@Test
	public void testDontPanicAdvice() throws Exception {
		AwkTestSupport
				.cliTest("advice.awk script")
				.argument("-f", pathTo("advice.awk"))
				.expectLines("Don't Panic!")
				.runAndAssert();
	}

	/**
	 * Tests the program
	 *
	 * <pre>
	 * awk '/li/ { print $0 }' mail-list
	 * </pre>
	 *
	 * It should output 4 records containing the string "li".
	 *
	 * @see <a href="http://www.gnu.org/software/gawk/manual/gawk.html#Very-Simple>Some Simple Examples</a>
	 */
	@Test
	public void testMailListLiList() throws Exception {
		AwkTestSupport
				.cliTest("mail-list li filter")
				.script("/li/ {print $0}")
				.operand(pathTo("mail-list"))
				.expectLines(
						"Amelia       555-5553     amelia.zodiacusque@gmail.com    F",
						"Broderick    555-0542     broderick.aliquotiens@yahoo.com R",
						"Julie        555-6699     julie.perscrutabor@skeeve.com   F",
						"Samuel       555-3430     samuel.lanceolis@shu.edu        A")
				.runAndAssert();
	}

	/**
	 * @see <a hef="http://www.gnu.org/software/gawk/manual/gawk.html#Two-Rules">Two Rules</a>
	 */
	@Test
	public void testTwoRules() throws Exception {
		AwkTestSupport
				.cliTest("two-rule filter")
				.script("/12/ {print $0} /21/ {print $0}")
				.operand(pathTo("mail-list"), pathTo("inventory-shipped"))
				.expectLines(
						"Anthony      555-3412     anthony.asserturo@hotmail.com   A",
						"Camilla      555-2912     camilla.infusarum@skynet.be     R",
						"Fabius       555-1234     fabius.undevicesimus@ucb.edu    F",
						"Jean-Paul    555-2127     jeanpaul.campanorum@nyu.edu     R",
						"Jean-Paul    555-2127     jeanpaul.campanorum@nyu.edu     R",
						"Jan  21  36  64 620",
						"Apr  21  70  74 514")
				.runAndAssert();
	}

	@Test
	public void testEmptyPattern() throws Exception {
		AwkTestSupport
				.cliTest("empty pattern")
				.script("//")
				.operand(pathTo("inventory-shipped"))
				.expectLines(
						"Jan  13  25  15 115",
						"Feb  15  32  24 226",
						"Mar  15  24  34 228",
						"Apr  31  52  63 420",
						"May  16  34  29 208",
						"Jun  31  42  75 492",
						"Jul  24  34  67 436",
						"Aug  15  34  47 316",
						"Sep  13  55  37 277",
						"Oct  29  54  68 525",
						"Nov  20  87  82 577",
						"Dec  17  35  61 401",
						"",
						"Jan  21  36  64 620",
						"Feb  26  58  80 652",
						"Mar  24  75  70 495",
						"Apr  21  70  74 514")
				.runAndAssert();
	}

	@Test
	public void testUninitializedVarible() throws Exception {
		AwkTestSupport
				.cliTest("uninitialized variable prints message")
				.script("//{ if (v == 0) {print \"uninitialize variable\"} else {print}}")
				.operand(pathTo("inventory-shipped"))
				.expectLines(monotoneArray("uninitialize variable", 17))
				.runAndAssert();
	}

	@Test
	public void testUninitializedVarible2() throws Exception {
		AwkTestSupport
				.cliTest("initialized variable prints record")
				.script("//{ v = 1; if (v == 0) {print \"uninitialize variable\"} else {print}}")
				.operand(pathTo("inventory-shipped"))
				.expectLines(
						"Jan  13  25  15 115",
						"Feb  15  32  24 226",
						"Mar  15  24  34 228",
						"Apr  31  52  63 420",
						"May  16  34  29 208",
						"Jun  31  42  75 492",
						"Jul  24  34  67 436",
						"Aug  15  34  47 316",
						"Sep  13  55  37 277",
						"Oct  29  54  68 525",
						"Nov  20  87  82 577",
						"Dec  17  35  61 401",
						"",
						"Jan  21  36  64 620",
						"Feb  26  58  80 652",
						"Mar  24  75  70 495",
						"Apr  21  70  74 514")
				.runAndAssert();
	}

	@Test
	public void testArrayStringKey() throws Exception {
		AwkTestSupport
				.cliTest("array string key")
				.script("//{i=1; j=\"1\"; v[i] = 100; print v[i] v[j];}")
				.operand(pathTo("inventory-shipped"))
				.expectLines(monotoneArray("100100", 17))
				.runAndAssert();
	}

	@Test
	public void testArrayStringKey2() throws Exception {
		AwkTestSupport
				.cliTest("array string key via j")
				.script("//{i=1; j=\"1\"; v[j] = 100; print v[i] v[j];}")
				.operand(pathTo("inventory-shipped"))
				.expectLines(monotoneArray("100100", 17))
				.runAndAssert();
	}

	@Test
	public void testNot() throws Exception {
		assertEquals("!0 must return 1", 1, AWK.eval("!0"));
		assertEquals("!1 must return 0", 0, AWK.eval("!1"));
		assertEquals("!0.0 must return 1", 1, AWK.eval("!0.0"));
		assertEquals("!0.1 must return 0", 0, AWK.eval("!0.1"));
		assertEquals("!2^31 must return 0", 0, AWK.eval("!2^31"));
		assertEquals("!2^33 must return 0", 0, AWK.eval("!2^33"));
		assertEquals("!\"\" must return 1", 1, AWK.eval("!\"\""));
		assertEquals("!\"a\" must return 0", 0, AWK.eval("!\"a\""));
		assertEquals("!uninitialized must return true", 1, AWK.eval("!uninitialized"));
	}

	@Test
	public void testExit() throws Exception {
		AwkTestSupport
				.awkTest("exit with code")
				.script("BEGIN { exit 17 }")
				.stdin("")
				.expectExit(17)
				.runAndAssert();

		AwkTestSupport
				.awkTest("exit in BEGIN prevents rules")
				.script("BEGIN { exit 0 }\n{ print $0 }")
				.stdin("failure")
				.expect("")
				.runAndAssert();

		AwkTestSupport
				.awkTest("exit jumps to END")
				.script("BEGIN { exit 0 ; printf \"failure\" }\nEND { printf \"success\" }")
				.stdin("")
				.expect("success")
				.runAndAssert();

		AwkTestSupport
				.awkTest("exit in END stops immediately")
				.script("END { printf \"success\" ; exit 0 ; printf \"failure\" }")
				.stdin("")
				.expect("success")
				.runAndAssert();

		AwkTestSupport
				.awkTest("exit without code")
				.script("BEGIN { exit }\n{ print $0 }")
				.stdin("failure")
				.expect("")
				.runAndAssert();

		AwkTestSupport
				.awkTest("exit retains previous code")
				.script("BEGIN { exit 2 }\nEND { exit }")
				.stdin("")
				.expectExit(2)
				.runAndAssert();
	}

	@Test
	public void testRanges() throws Exception {
		String input = "aa\nbb\ncc\ndd\nee\naa\nbb\ncc\ndd\nee";
		AwkTestSupport
				.awkTest("range of regexp")
				.script("/b/, /d/")
				.stdin(input)
				.expect("bb\ncc\ndd\nbb\ncc\ndd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("range of conditions")
				.script("NR == 2, NR == 4")
				.stdin(input)
				.expect("bb\ncc\ndd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("non matching start range")
				.script("/zz/, /cc/")
				.stdin(input)
				.expect("")
				.runAndAssert();

		AwkTestSupport
				.awkTest("non matching end range")
				.script("/cc/, /zz/")
				.stdin(input)
				.expect("cc\ndd\nee\naa\nbb\ncc\ndd\nee\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("mixed range")
				.script("NR == 2, /d/")
				.stdin(input)
				.expect("bb\ncc\ndd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("invalid range")
				.script("/a/, /b/, NR == 4")
				.stdin(input)
				.expectThrow(ParserException.class)
				.runAndAssert();

		AwkTestSupport
				.awkTest("enter and leave range")
				.script("/b/, /b/")
				.stdin(input)
				.expect("bb\nbb\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("range comma precedence")
				.script("/b/, /d/ || /c/")
				.stdin(input)
				.expect("bb\ncc\nbb\ncc\n")
				.runAndAssert();
	}

	@Test
	public void testDavideBrini() throws Exception {
		AwkTestSupport
				.awkTest("Davide Brini signature")
				.script(
						"BEGIN{O=\"~\"~\"~\";o=\"==\"==\"==\";o+=+o;x=O\"\"O;while(X++<=x+o+o){c=c\"%c\";}"
								+
								"printf c,(x-O)*(x-O),x*(x-o)-o,x*(x-O)+x-O-o,+x*(x-O)-x+o,X*(o*o+O)+x-O,"
								+
								"X*(X-x)-o*o,(x+X)*o*o+o,x*(X-x)-O-O,x-O+(O+o+X+x)*(o+O),X*X-X*(x-O)-x+O,"
								+
								"O+X*(o*(o+O)+O),+x+O+X*o,x*(x-o),(o+X+x)*o*o-(x-O-O),O+(X-x)*(X+O),x-O}")
				.expect("dave_br@gmx.com\n")
				.runAndAssert();
	}

	@Test
	public void testIncDec() throws Exception {
		AwkTestSupport
				.awkTest("pre increment outputs 2")
				.script("BEGIN { a = 1; printf ++a }")
				.expect("2")
				.runAndAssert();

		AwkTestSupport
				.awkTest("post increment twice")
				.script("BEGIN { a = 1; printf a++ ; printf a++; }")
				.expect("12")
				.runAndAssert();

		AwkTestSupport
				.awkTest("pre decrement outputs 0")
				.script("BEGIN { a = 1; printf --a }")
				.expect("0")
				.runAndAssert();

		AwkTestSupport
				.awkTest("post decrement twice")
				.script("BEGIN { a = 1; printf a-- ; printf a--; }")
				.expect("10")
				.runAndAssert();

		AwkTestSupport
				.awkTest("pre increment uninitialized")
				.script("BEGIN { printf ++a }")
				.expect("1")
				.runAndAssert();

		AwkTestSupport
				.awkTest("post increment uninitialized twice")
				.script("BEGIN { printf a++ ; printf a++; }")
				.expect("01")
				.runAndAssert();
	}

	@Test
	public void testPrintfC() throws Exception {
		assertEquals("A", AWK.eval("sprintf(\"%c\", 65)"));
	}

	@Test
	public void testConcatenationLeftAssociativity() throws Exception {
		assertEquals("Concatenated elements must be eval'ed from left to right", "0123", AWK.eval("a++ a++ a++ a++"));
	}

	@Test
	public void testFunctionArgumentsLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("function arguments left to right")
				.script("BEGIN { print a++, a++, a++, a++ }")
				.expect("0 1 2 3\n")
				.runAndAssert();
	}

	@Test
	public void testAtan2ArgumentsLeftAssociativity() throws Exception {
		assertEquals("atan2 arguments must be eval'ed from left to right", 0.0, AWK.eval("atan2(a++, a++)"));
	}

	@Test
	public void testComparisonArgumentsLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("comparison arguments left to right")
				.script("BEGIN { r = (a++ < a++); printf r }")
				.expect("1")
				.runAndAssert();
	}

	@Test
	public void testAssignmentRightToLeft() throws Exception {
		AwkTestSupport
				.awkTest("assignment evaluated right to left")
				.script("BEGIN { arr[a++] = a++; printf arr[1] }")
				.expect("0")
				.runAndAssert();
	}

	@Test
	public void testBinaryExpressionLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("binary expression left to right")
				.script("BEGIN { a = 1; printf a++ / a++ }")
				.expect("0.5")
				.runAndAssert();
	}

	@Test
	public void testChainedAdditionsAndSubtractionsLeftAssociativity() throws Exception {
		assertEquals(
				"Chained additions and subtractions must be eval'ed from left to right",
				6L,
				AWK.eval("10 - 3 - 2 + 1"));
	}

	@Test
	public void testChainedMultiplicationsAndDivisionsLeftAssociativity() throws Exception {
		assertEquals("Chained multiplies and divides must be eval'ed from left to right", 5L, AWK.eval("12 / 3 / 4 * 5"));
	}

	@Test
	public void testChainedExponentiationRightAssociativity() throws Exception {
		assertEquals("Chained powers must be eval'ed from right to left", 4L, AWK.eval("256 ^ 0.5 ^ 4 ^ 0.5"));
	}

	// Additional tests to further cover left associativity:

	@Test
	public void testChainedLogicalAndLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("logical and left to right")
				.script("BEGIN { a = 0; r = (a++ && a++ && a++); printf r }")
				.expect("0")
				.runAndAssert();
	}

	@Test
	public void testChainedLogicalOrLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("logical or left to right")
				.script("BEGIN { a = 1; r = (a++ || a++ || a++); printf r }")
				.expect("1")
				.runAndAssert();
	}

	@Test
	public void testChainedComparisonLeftAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("comparison chaining left to right")
				.script("BEGIN { a = 1; r = (a++ < a++ < a++); printf r }")
				.expect("0")
				.runAndAssert();
	}

	@Test
	public void testChainedStringConcatenationLeftAssociativity() throws Exception {
		assertEquals(
				"Chained string concatenation must be eval'ed from left to right",
				"abcde",
				AWK.eval("\"a\" \"b\" \"c\" \"d\" \"e\""));
	}

	@Test
	public void testComplexExpressionLeftAssociativity() throws Exception {
		assertEquals(
				"Complex expression with mixed operators must be eval'ed from left to right",
				8L,
				AWK.eval("10 + 12 / 3 * 2 - 6 / 3 * 5"));
	}

	@Test
	public void testSubstr() throws Exception {
		assertEquals("234", AWK.eval("substr(\"12345\", 2, 3)"));
		assertEquals("2345", AWK.eval("substr(\"12345\", 2, 10)"));
		assertEquals("123", AWK.eval("substr(\"12345\", 0, 3)"));
		assertEquals("123", AWK.eval("substr(\"12345\", -1, 3)"));
		assertEquals("", AWK.eval("substr(\"12345\", 2, 0)").toString());
		assertEquals("", AWK.eval("substr(\"12345\", 2, -1)").toString());
		assertEquals("", AWK.eval("substr(\"12345\", -1, -1)").toString());
		assertEquals("", AWK.eval("substr(\"12345\", 10, 3)").toString());
		assertEquals("2345", AWK.eval("substr(\"12345\", 2)"));
		assertEquals("12345", AWK.eval("substr(\"12345\", 0)"));
		assertEquals("12345", AWK.eval("substr(\"12345\", -1)"));
	}

	@Test
	public void testPrintComparison() throws Exception {
		AwkTestSupport
				.awkTest("print comparison less than")
				.script("BEGIN { print 1 < \"2\" }")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print comparison greater equal")
				.script("BEGIN { print 1 >= \"2\" }")
				.expect("0\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print redirection suppresses stdout")
				.script("BEGIN { print 1 > TEMPDIR\"/printRedirect\" }")
				.withTempDir()
				.expect("")
				.runAndAssert();
		assertTrue(
				"> in a print statement must write to the specified file",
				Files.exists(Paths.get(AwkTestSupport.sharedTempDirectory().toString(), "printRedirect")));
		AwkTestSupport
				.awkTest("print parenthesized comparison")
				.script("BEGIN { print(1 > 0) }")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print concatenated comparison")
				.script("BEGIN { print \"test\" (1 > 0) \"test\" }")
				.expect("test1test\n")
				.runAndAssert();
	}

	@Test
	public void testPrintParenthesizedExpressionConcatenation() throws Exception {
		AwkTestSupport
				.awkTest("print parenthesized concatenation")
				.script("{ print ($1 - $2) \";\" $3 }")
				.stdin("10 5 99")
				.expect("5;99\n")
				.runAndAssert();
	}

	@Test
	public void testPrintParenthesizedExpressionOperators() throws Exception {
		AwkTestSupport
				.awkTest("print arithmetic multiplication")
				.script("{ print ($1 - $2) * $3 }")
				.stdin("10 5 4\n")
				.expect("20\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print equality comparison")
				.script("{ print ($1 - $2) == $3 }")
				.stdin("10 5 5\n")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print ternary expression")
				.script("{ print ($1 - $2) ? \"yes\" : \"no\" }")
				.stdin("10 5\n")
				.expect("yes\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print regex match")
				.script("{ print ($1) ~ /foo/ }")
				.stdin("foo\n")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print in operator")
				.script("BEGIN { arr[\"foo\"] = 1 } { print ($1) in arr }")
				.stdin("foo\n")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print logical expression")
				.script("{ print ($1) && ($2) }")
				.stdin("1 1\\n0 1\\n")
				.expect("1\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("print redirection with parentheses")
				.script("{ print ($1) > TEMPDIR\"/printParenRedirect\" }")
				.stdin("value\n")
				.withTempDir()
				.expect("")
				.runAndAssert();
		assertTrue(
				"Parenthesized expressions must redirect output to files",
				Files.exists(Paths.get(AwkTestSupport.sharedTempDirectory().toString(), "printParenRedirect")));
	}

	@Test
	public void testSubArray() throws Exception {
		AwkTestSupport
				.awkTest("sub modifies array element")
				.script("BEGIN { a[1] = \"ab:c:d\"; sub(/:/, \"\", a[1]); print a[1]; }")
				.expect("abc:d\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub modifies array element")
				.script("BEGIN { a[1] = \"ab:c:d\"; gsub(/:/, \"\", a[1]); print a[1]; }")
				.expect("abcd\n")
				.runAndAssert();
	}

	@Test
	public void testSubDollarReference() throws Exception {
		AwkTestSupport
				.awkTest("sub modifies field")
				.script("{ sub(/d/, \"Z\", $4); print $1, $2, $3, $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc Zd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub modifies field")
				.script("{ gsub(/d/, \"Z\", $4); print $1, $2, $3, $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc ZZ\n")
				.runAndAssert();
	}

	@Test
	public void testSubDollarZero() throws Exception {
		AwkTestSupport
				.awkTest("sub modifies $0")
				.script("{ sub(/d/, \"Z\"); print $0; print $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc Zd\nZd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub modifies $0")
				.script("{ gsub(/d/, \"Z\"); print $0; print $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc ZZ\nZZ\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("sub modifies $0 with target")
				.script("{ sub(/d/, \"Z\", $0); print $0; print $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc Zd\nZd\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub modifies $0 with target")
				.script("{ gsub(/d/, \"Z\", $0); print $0; print $4; }")
				.stdin("aa bb cc dd")
				.expect("aa bb cc ZZ\nZZ\n")
				.runAndAssert();
	}

	@Test
	public void testSubVariable() throws Exception {
		AwkTestSupport
				.awkTest("sub modifies variable")
				.script("BEGIN { v = \"aa bb cc dd\"; sub(/d/, \"Z\", v); print v; }")
				.expect("aa bb cc Zd\n")
				.runAndAssert();
		AwkTestSupport
				.awkTest("gsub modifies variable")
				.script("BEGIN { v = \"aa bb cc dd\"; gsub(/d/, \"Z\", v); print v; }")
				.expect("aa bb cc ZZ\n")
				.runAndAssert();
	}

	@Test
	public void testSubReplaceWithMatchReference() throws Exception {
		AwkTestSupport
				.awkTest("gsub replaces ampersand")
				.script("{ gsub(/[b-c]/, \"_&_\"); print $0; }")
				.stdin("a b c d e")
				.expect("a _b_ _c_ d e\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub escapes ampersand")
				.script("{ gsub(/[b-c]/, \"_\\\\&_\"); print $0; }")
				.stdin("a b c d e")
				.expect("a _&_ _&_ d e\n")
				.runAndAssert();

		AwkTestSupport
				.awkTest("gsub replaces dollar zero")
				.script("{ gsub(/[b-c]/, \"_$0_\"); print $0; }")
				.stdin("a b c d e")
				.expect("a _$0_ _$0_ d e\n")
				.runAndAssert();
	}

	@Test
	public void testMultiDimensionalArrayWithSubsep() throws Exception {
		AwkTestSupport
				.awkTest("multi-dimensional array with subsep")
				.script("BEGIN { SUBSEP=\"@\"; a[1,2]=42; print a[1 SUBSEP 2]; }")
				.expect("42\n")
				.runAndAssert();
	}

	@Test
	public void testSubsepChangeAfterIndexCreation() throws Exception {
		AwkTestSupport
				.awkTest("subsep change after index creation")
				.script("BEGIN { SUBSEP=\"@\"; idx = 1 SUBSEP 2; a[idx]=42; SUBSEP=\":\"; print a[idx]; print a[1,2]; }")
				.expect("42\n\n")
				.runAndAssert();
	}

	@Test
	public void testGetlineDefaultVariable() throws Exception {
		String script = "BEGIN { while (getline && n++ < 2) print; exit }";
		AwkTestSupport
				.awkTest("getline default variable")
				.script(script)
				.stdin("a\nb\nc\n")
				.expect("a\nb\n")
				.runAndAssert();
	}

	@Test
	public void testEvalNumericExpression() throws Exception {
		Object result = AWK.eval("1 + 2", (String) null);
		assertTrue(result instanceof Number);
		assertEquals(3, ((Number) result).intValue());
	}

	@Test
	public void testEvalFieldExtraction() throws Exception {
		Object result = AWK.eval("$2", "my text input");
		assertEquals("text", result);
	}

	@Test
	public void testEvalNF() throws Exception {
		Object result = AWK.eval("NF", "a b c");
		assertEquals(3, ((Number) result).intValue());
	}

	@Test
	public void testEvalFailsStatement() throws Exception {
		assertThrows(ParserException.class, () -> AWK.eval("print 3.14", (String) null));
		assertThrows(ParserException.class, () -> AWK.eval("1 + 2, 3", (String) null));
		assertThrows(ParserException.class, () -> AWK.eval("1 + 2 ; 3 + 4", (String) null));
		assertThrows(ParserException.class, () -> AWK.eval("BEGIN { print 5 }", (String) null));
	}

	@Test
	public void compileRejectsEmptyParenthesizedPrintStatements() {
		assertThrows(ParserException.class, () -> AWK.compile("BEGIN { print() }"));
		assertThrows(ParserException.class, () -> AWK.compile("BEGIN { printf() }"));
	}

	/**
	 * Verifies that a script provided as a {@link String} can be compiled to an
	 * {@link AwkProgram} and executed.
	 */
	@Test
	public void compileFromString() throws Exception {
		String script = "{ print $0 }";
		AwkProgram program = AWK.compile(script);

		AwkSettings settings = new AwkSettings();
		settings.setDefaultRS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		new Awk(settings)
				.script(program)
				.input(new ByteArrayInputStream("foo\nbar\n".getBytes(StandardCharsets.UTF_8)))
				.variable("ORS", "\n")
				.execute(out);

		assertEquals("foo\nbar\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	public void captureCollectsOutput() throws Exception {
		Awk awk = new Awk();
		String result = awk
				.script("BEGIN { print \"alpha\"; printf(\"beta\") }")
				.variable("ORS", "\n")
				.execute();

		assertEquals("alpha\nbeta", result);
	}

	@Test
	public void localeAffectsCapturedOutput() throws Exception {
		AwkSettings settings = new AwkSettings();
		settings.setLocale(java.util.Locale.FRANCE);

		Awk awk = new Awk(settings);
		String result = awk
				.script("BEGIN { print 1.5 }")
				.variable("ORS", "\n")
				.execute();

		assertEquals("1,5\n", result);
	}

	@Test
	public void executeWithSinkCapturesStructuredPrintArguments() throws Exception {
		StructuredOutputSink sink = new StructuredOutputSink();
		Awk awk = new Awk();

		awk
				.script("BEGIN { print 1, \"two\", 3; printf(\"<%s>\", \"done\") }")
				.execute(sink);

		assertEquals(1, sink.printedValues.size());
		assertEquals(3, sink.printedValues.get(0).size());
		assertTrue(sink.printedValues.get(0).get(0) instanceof Number);
		assertEquals(1, ((Number) sink.printedValues.get(0).get(0)).intValue());
		assertEquals("two", sink.printedValues.get(0).get(1));
		assertTrue(sink.printedValues.get(0).get(2) instanceof Number);
		assertEquals(3, ((Number) sink.printedValues.get(0).get(2)).intValue());
		assertEquals(Collections.singletonList("<%s>"), sink.printfFormats);
		assertEquals(Collections.singletonList(Collections.<Object>singletonList("done")), sink.printfValues);
	}

	@Test
	public void executeToOutputStream() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		new Awk()
				.script("BEGIN { print \"alpha\" }")
				.variable("ORS", "\n")
				.execute(output);

		assertEquals("alpha\n", output.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	public void executeToAppendable() throws Exception {
		StringBuilder output = new StringBuilder();

		new Awk()
				.script("BEGIN { print \"alpha\" }")
				.variable("ORS", "\n")
				.execute(output);

		assertEquals("alpha\n", output.toString());
	}

	@Test
	public void perExecutionSinkOverridesDefault() throws Exception {
		StringBuilder perCallOutput = new StringBuilder();
		Awk awk = new Awk();

		awk
				.script("BEGIN { print \"alpha\" }")
				.variable("ORS", "\n")
				.execute(new AppendableAwkSink(perCallOutput, java.util.Locale.US));

		assertEquals("alpha\n", perCallOutput.toString());
	}

	@Test
	public void executeWithStructuredOutputSink() throws Exception {
		StructuredOutputSink sink = new StructuredOutputSink();
		Awk awk = new Awk();

		awk.script("BEGIN { print 1, \"two\", 3; printf(\"<%s>\", \"done\") }").execute(sink);

		assertEquals(1, sink.printedValues.size());
		assertEquals(3, sink.printedValues.get(0).size());
		assertTrue(sink.printedValues.get(0).get(0) instanceof Number);
		assertEquals(1, ((Number) sink.printedValues.get(0).get(0)).intValue());
		assertEquals("two", sink.printedValues.get(0).get(1));
		assertTrue(sink.printedValues.get(0).get(2) instanceof Number);
		assertEquals(3, ((Number) sink.printedValues.get(0).get(2)).intValue());
		assertEquals(Collections.singletonList("<%s>"), sink.printfFormats);
		assertEquals(Collections.singletonList(Collections.<Object>singletonList("done")), sink.printfValues);
	}

	@Test
	public void executeToOutputStreamUsesSystemLineSeparator() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		new Awk()
				.script("BEGIN { print \"alpha\" }")
				.execute(output);

		assertEquals("alpha" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	public void publicRunSuppressesZeroExitCode() throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		new Awk()
				.script("BEGIN { print \"alpha\"; exit 0 }")
				.execute(output);

		assertEquals("alpha" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	public void avmCanReuseSameRuntimeAcrossProgramRunsWithPerExecutionOutput() throws Exception {
		Awk awk = new Awk();
		AwkProgram program = awk.compile("BEGIN { print \"value\" }");
		InputSource emptyInput = new InputSource() {
			@Override
			public boolean nextRecord() {
				return false;
			}

			@Override
			public String getRecordText() {
				return null;
			}

			@Override
			public List<String> getFields() {
				return null;
			}

			@Override
			public boolean isFromFilenameList() {
				return false;
			}
		};

		StringBuilder firstOutput = new StringBuilder();
		StringBuilder secondOutput = new StringBuilder();
		try (AVM avm = awk.createAvm()) {
			avm.setAwkSink(new AppendableAwkSink(firstOutput, java.util.Locale.US));
			Map<String, Object> orsOverride = Collections.<String, Object>singletonMap("ORS", "\n");
			avm
					.execute(
							program,
							emptyInput,
							Collections.<String>emptyList(),
							orsOverride);
			avm.setAwkSink(new AppendableAwkSink(secondOutput, java.util.Locale.US));
			avm.execute(program, emptyInput, Collections.<String>emptyList(), orsOverride);
		}

		assertEquals("value\n", firstOutput.toString());
		assertEquals("value\n", secondOutput.toString());
	}

	/**
	 * Verifies that a script provided as a {@link java.io.Reader} can be compiled
	 * and executed.
	 */
	@Test
	public void compileFromReader() throws Exception {
		String script = "{ print $0 }";
		AwkProgram program = AWK.compile(new StringReader(script));

		AwkSettings settings = new AwkSettings();
		settings.setDefaultRS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		new Awk(settings)
				.script(program)
				.input(new ByteArrayInputStream("one\n".getBytes(StandardCharsets.UTF_8)))
				.variable("ORS", "\n")
				.execute(out);

		assertEquals("one\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	/**
	 * Ensures that providing explicit extensions to the {@link Awk} constructor
	 * does not interfere with tuple execution.
	 */
	@Test
	public void invokeWithExplicitExtensions() throws Exception {
		String script = "{ print $0 }";
		AwkProgram program = AWK.compile(script);

		AwkSettings settings = new AwkSettings();
		settings.setDefaultRS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		new Awk(settings)
				.script(program)
				.input(new ByteArrayInputStream("value\n".getBytes(StandardCharsets.UTF_8)))
				.variable("ORS", "\n")
				.execute(out);

		assertEquals("value\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	/**
	 * Loads a precompiled program from disk via the <code>-L</code> option and
	 * executes it through the CLI.
	 */
	@Test
	public void loadSerializedProgram() throws Exception {
		String script = "{ print toupper($0) }";
		AwkProgram program = AWK.compile(script);

		File tmp = File.createTempFile("jawk", ".tpl");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp))) {
			oos.writeObject(program);
		}

		Cli cli = Cli.parseCommandLineArguments(new String[] { "-L", tmp.getAbsolutePath() });
		AwkSettings settings = cli.getSettings();
		settings.setDefaultRS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		new Awk(settings)
				.script(cli.getPrecompiledProgram())
				.input(new ByteArrayInputStream("abc\n".getBytes(StandardCharsets.UTF_8)))
				.variable("ORS", "\n")
				.execute(out);

		assertEquals("ABC\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	/**
	 * Compiles a script to tuples using the <code>-K</code> option and then loads
	 * the generated file for execution.
	 */
	@Test
	public void compileTuplesViaCLI() throws Exception {
		File tmp = File.createTempFile("jawk", ".tpl");
		Cli.main(new String[] { "-K", tmp.getAbsolutePath(), "{ print toupper($0) }" });

		Cli cli = Cli.parseCommandLineArguments(new String[] { "-L", tmp.getAbsolutePath() });
		AwkSettings settings = cli.getSettings();
		settings.setDefaultRS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		new Awk(settings)
				.script(cli.getPrecompiledProgram())
				.input(new ByteArrayInputStream("abc\n".getBytes(StandardCharsets.UTF_8)))
				.variable("ORS", "\n")
				.execute(out);

		assertEquals("ABC\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	/**
	 * Ensures that the CLI rejects variable assignments with invalid identifiers.
	 */
	@Test
	public void rejectsInvalidVariableName() {
		assertThrows(
				IllegalArgumentException.class,
				() -> Cli.parseCommandLineArguments(new String[]
				{ "-v", "1foo=bar", "{ print }" }));
	}

	/**
	 * The <code>-K</code> option must be accompanied by a filename; otherwise the
	 * CLI should throw an {@link IllegalArgumentException}.
	 */
	@Test
	public void compileTuplesRequiresFilename() {
		assertThrows(
				IllegalArgumentException.class,
				() -> Cli
						.create(
								new String[]
								{ "-K" },
								new ByteArrayInputStream(new byte[0]),
								new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8.name()),
								new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8.name())));
	}

	@Test
	public void sandboxRejectsSystemFunction() {
		Awk awk = new SandboxedAwk();
		assertThrows(
				AwkSandboxException.class,
				() -> awk.compile("BEGIN { system(\"true\") }"));
	}

	@Test
	public void sandboxRejectsOutputRedirectionDuringCompile() {
		Awk awk = new SandboxedAwk();
		assertThrows(
				AwkSandboxException.class,
				() -> awk.compile("{ print \"hi\" > \"file\" }"));
	}

	@Test
	public void sandboxRejectsInputRedirectionDuringCompile() {
		Awk awk = new SandboxedAwk();
		assertThrows(
				AwkSandboxException.class,
				() -> awk.compile("BEGIN { getline x < \"file\" }"));
	}

	@Test
	public void sandboxRejectsRedirectionAtRuntime() throws Exception {
		Awk nonSandboxAwk = new Awk();
		AwkProgram program = nonSandboxAwk.compile("BEGIN { print \"hi\" > \"sandbox_out.txt\" } ");

		AwkSettings settings = new AwkSettings();
		settings.setDefaultRS("\n");

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Awk sandboxAwk = new SandboxedAwk(settings);
		assertThrows(
				AwkSandboxException.class,
				() -> sandboxAwk
						.script(program)
						.variable("ORS", "\n")
						.input(new ByteArrayInputStream(new byte[0]))
						.execute(out));
	}

	@Test
	public void cliEnablesSandboxMode() {
		Cli cli = Cli.parseCommandLineArguments(new String[] { "-S", "{ print }" });
		assertTrue(cli.isSandbox());
	}

	@Test
	public void sandboxRejectsArgcAssignment() {
		Awk awk = new SandboxedAwk();
		assertThrows(
				AwkSandboxException.class,
				() -> awk.compile("BEGIN { ARGC = 5 }"));
	}

	@Test
	public void sandboxAllowsReadingArgc() throws Exception {
		AwkTestSupport
				.awkTest("sandbox allows reading ARGC")
				.withAwk(new SandboxedAwk())
				.script("BEGIN { print ARGC }")
				.expect("1\n")
				.runAndAssert();
	}

	@Test
	public void sandboxAllowsReadingArgv() throws Exception {
		// In sandbox mode ARGV is not materialized as a global variable, so
		// direct references to ARGV[n] in the script see an empty array.
		// The JRT input traversal still works correctly through the
		// AVM.getARGV() fallback.
		AwkTestSupport
				.awkTest("sandbox ARGV reads return empty strings")
				.withAwk(new SandboxedAwk())
				.script("BEGIN { print ARGV[0] }")
				.expect("\n")
				.runAndAssert();
	}

	private static final class StructuredOutputSink extends AwkSink {

		private final List<List<Object>> printedValues = new ArrayList<>();
		private final List<String> printfFormats = new ArrayList<>();
		private final List<List<Object>> printfValues = new ArrayList<>();
		private final ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
		private final PrintStream printStream = new PrintStream(rawOutput, true);

		@Override
		public void print(String ofs, String ors, String ofmt, Object... values) {
			printedValues.add(Arrays.asList(Arrays.copyOf(values, values.length)));
		}

		@Override
		public void printf(String ofs, String ors, String ofmt, String format, Object... values) {
			printfFormats.add(format);
			printfValues.add(Arrays.asList(Arrays.copyOf(values, values.length)));
		}

		@Override
		public PrintStream getPrintStream() {
			return printStream;
		}
	}

	@Test
	public void sandboxSkipsArgcAndArgvOffsetsWhenUnreferenced() throws Exception {
		AwkTestSupport
				.awkTest("sandbox with unreferenced ARGC/ARGV still processes stdin")
				.withAwk(new SandboxedAwk())
				.script("{ print \"got:\" $0 }")
				.stdin("hello\n")
				.expect("got:hello\n")
				.runAndAssert();
	}
}
