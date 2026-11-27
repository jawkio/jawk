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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import io.jawk.backend.AVM;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.GawkExtension;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.jrt.AwkNumberFormatException;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.JRT;
import io.jawk.util.AwkSettings;

/**
 * Tests of {@link AwkSettings#setStrictNumbers(boolean)}: arithmetic refuses a
 * value that is not a number, and nothing changes while the setting is off.
 * Each case runs with and without the tuple optimizer, as a script and as an
 * expression.
 */
public class StrictNumbersTest {

	/** Fields: number, empty, text, numeric prefix, blanks, zeros, exponent, comma. {@code $9} is beyond {@code NF}. */
	private static final String RECORD = "8;;N/A;12 W; 7 ;007;1e3;1,5";

	private static final String NOT_A_NUMBER = "N/A";

	/** Operands that are not numbers, with the value the exception reports ({@code null}: never assigned). */
	private static final Map<String, String> REFUSED_OPERANDS = new LinkedHashMap<String, String>();

	static {
		REFUSED_OPERANDS.put("$2", "");
		REFUSED_OPERANDS.put("$3", NOT_A_NUMBER);
		REFUSED_OPERANDS.put("$4", "12 W");
		REFUSED_OPERANDS.put("$8", "1,5");
		REFUSED_OPERANDS.put("$9", null);
		REFUSED_OPERANDS.put("y", null);
		REFUSED_OPERANDS.put("b[\"k\"]", null);
		REFUSED_OPERANDS.put("\"abc\"", "abc");
		REFUSED_OPERANDS.put("\"0x1A\"", "0x1A");
		REFUSED_OPERANDS.put("\"\"", "");
		REFUSED_OPERANDS.put("\" \"", " ");
	}

	/** Every operation of the issue, {@code %s} being the operand. */
	private static final List<String> OPERATIONS = Arrays.asList(
			"%s + 1",
			"1 + %s",
			"%s - 1",
			"1 - %s",
			"%s * 8",
			"8 * %s",
			"%s / 2",
			"100 / %s",
			"%s %% 3",
			"3 %% %s",
			"%s ^ 2",
			"2 ^ %s",
			"2 ** %s",
			"-%s",
			"+%s",
			"int(%s)",
			"sqrt(%s)",
			"exp(%s)",
			"log(%s)",
			"sin(%s)",
			"cos(%s)",
			"atan2(%s, 1)",
			"atan2(1, %s)",
			"srand(%s)",
			"(x = 1) && (x += %s)",
			"(x = 1) && (x -= %s)",
			"(x = 1) && (x *= %s)",
			"(x = 1) && (x /= %s)",
			"(x = 1) && (x %%= %s)",
			"(x = 1) && (x ^= %s)",
			"(x = 1) && (x **= %s)",
			"(a[1] += %s)",
			"(a[1][2] *= %s)",
			"($1 -= %s)");

	/** {@code ++}, {@code --} and the compound assignments, {@code %s} being the target. */
	private static final List<String> TARGET_OPERATIONS = Arrays
			.asList("%s++", "%s--", "++%s", "--%s", "%s += 1", "%s -= 1", "%s *= 2", "%s /= 2", "%s %%= 2", "%s ^= 2");

	private static final boolean[] BOTH = { true, false };

	/** Extension whose functions convert their argument themselves. */
	public static class NumberExtension extends AbstractExtension {

		/**
		 * Doubles its argument, converted the lenient way.
		 *
		 * @param value argument
		 * @return twice the numeric value of the argument
		 */
		@JawkFunction("twice")
		public Object twice(Object value) {
			return JRT.toDouble(value) * 2;
		}

		/**
		 * Doubles its argument, which must be a number.
		 *
		 * @param value argument
		 * @return twice the argument
		 */
		@JawkFunction("strictTwice")
		public Object strictTwice(Object value) {
			JRT.requireNumber(value);
			return JRT.toDouble(value) * 2;
		}
	}

	private static Awk awk(boolean strict, boolean optimize) {
		AwkSettings settings = new AwkSettings();
		settings.setFieldSeparator(";");
		settings.setStrictNumbers(strict);
		List<AbstractExtension> extensions = Arrays.asList(new GawkExtension(), new NumberExtension());
		if (optimize) {
			return new Awk(extensions, settings);
		}
		return new Awk(extensions, settings) {
			@Override
			public AwkProgram compile(String script) throws IOException {
				return compile(script, true);
			}
		};
	}

	private static String mode(boolean strict, boolean optimize) {
		return (strict ? " (strict" : " (lenient") + (optimize ? ", optimized)" : ", not optimized)");
	}

	/** Runs a script on {@link #RECORD} and expects the given output, whatever the settings. */
	private static void assertSameOutputEverywhere(String description, String script, String... lines) throws Exception {
		for (boolean strict : BOTH) {
			for (boolean optimize : BOTH) {
				AwkTestSupport.awkTest(description + mode(strict, optimize))
						.script(script)
						.withAwk(awk(strict, optimize))
						.stdin(RECORD + "\n")
						.expectLines(lines)
						.runAndAssert();
			}
		}
	}

	/** Runs a script on {@link #RECORD} under strict numbers and expects it to refuse the given value. */
	private static AwkNumberFormatException assertScriptRefused(String script, String value) throws Exception {
		AwkNumberFormatException refusal = null;
		for (boolean optimize : BOTH) {
			String description = script + mode(true, optimize);
			AwkTestSupport.TestResult result = AwkTestSupport.awkTest("Strict numbers refuse " + description)
					.script(script)
					.withAwk(awk(true, optimize))
					.stdin(RECORD + "\n")
					.expectThrow(AwkNumberFormatException.class)
					.run();
			result.assertExpected();
			// Exactly this class, as it is: not wrapped, not a subclass
			assertSame(description, AwkNumberFormatException.class, result.thrownException().getClass());
			refusal = (AwkNumberFormatException) result.thrownException();
			assertEquals(description, value, refusal.getValue());
		}
		return refusal;
	}

	/** Evaluates an expression on {@link #RECORD} under strict numbers and expects it to refuse the given value. */
	private static AwkNumberFormatException assertExpressionRefused(String expression, String value) throws Exception {
		AwkNumberFormatException refusal = null;
		for (boolean optimize : BOTH) {
			String description = expression + mode(true, optimize);
			Awk awk = awk(true, optimize);
			try {
				Object result = awk.eval(awk.compileExpression(expression, !optimize), RECORD);
				fail(description + " must fail, but is " + result);
			} catch (AwkNumberFormatException e) {
				assertSame(description, AwkNumberFormatException.class, e.getClass());
				assertEquals(description, value, e.getValue());
				refusal = e;
			}
		}
		return refusal;
	}

	/** Evaluates an expression on {@link #RECORD} and expects the same result whatever the settings. */
	private static void assertSameValueEverywhere(String expression, Object expected) throws Exception {
		for (boolean strict : BOTH) {
			for (boolean optimize : BOTH) {
				Awk awk = awk(strict, optimize);
				assertEquals(
						expression + mode(strict, optimize),
						expected,
						awk.eval(awk.compileExpression(expression, !optimize), RECORD));
			}
		}
	}

	@Test
	public void testOffByDefault() {
		AwkSettings settings = new AwkSettings();
		assertFalse(settings.isStrictNumbers());
		assertFalse(AwkSettings.DEFAULT_SETTINGS.isStrictNumbers());
		assertTrue(settings.toDescriptionString().contains("strictNumbers = false"));

		long modifications = settings.getModificationCount();
		settings.setStrictNumbers(true);
		assertTrue(settings.isStrictNumbers());
		assertTrue(settings.toDescriptionString().contains("strictNumbers = true"));
		assertEquals(modifications + 1, settings.getModificationCount());

		try {
			AwkSettings.DEFAULT_SETTINGS.setStrictNumbers(true);
			fail("The default settings are immutable");
		} catch (UnsupportedOperationException e) {
			assertFalse(AwkSettings.DEFAULT_SETTINGS.isStrictNumbers());
		}
	}

	@Test
	public void testNothingChangesWhenOff() throws Exception {
		for (boolean optimize : BOTH) {
			Awk awk = awk(false, optimize);
			AwkTestSupport.awkTest("Without strict numbers, a text is zero or its numeric prefix" + mode(false, optimize))
					.script(
							"{ print $2 + 1, 1 - $3, $4 * 8, 100 / $5, $9 % 3, y ^ 2, -$4, +$4, int($4), sqrt($2), exp($3),"
									+ " log($5 \"x\"), sin($2), cos($3), atan2($2, 1), atan2(0, $9) }")
					.withAwk(awk)
					.stdin(RECORD + "\n")
					.expectLines("1 1 96 14.2857 0 0 -12 12 12 0 1 1.94591 0 1 0 0")
					.runAndAssert();
			AwkTestSupport
					.awkTest(
							"Without strict numbers, ++, -- and compound assignments convert their target" + mode(false, optimize))
					.script(
							"{ x = $3; x++; y = $4; y += 1; a[1] = $4; a[1]--; b[1] = $3; b[1] *= 2; c[1][2] = $4; c[1][2] ^= 2;"
									+ " $4++; $3 -= 1; print x, y, a[1], b[1], c[1][2], $4, $3, \"abc\" * 2, -\"abc\", +\"3x\","
									+ " (srand(\"x\") >= 0); f = \"int\"; print @f($8) }")
					.withAwk(awk)
					.stdin(RECORD + "\n")
					.expectLines("1 13 11 0 144 13 -1 0 0 3 1", "1")
					.runAndAssert();
			assertEquals(96L, awk.eval(awk.compileExpression("$4 * 8", !optimize), RECORD));
			assertEquals(0L, awk.eval(awk.compileExpression("$2 + $3 + $9 + y", !optimize), RECORD));
			assertEquals(0L, awk.eval(awk.compileExpression("\"abc\" * 2", !optimize), RECORD));
			assertEquals(3L, awk.eval(awk.compileExpression("+\"3x\"", !optimize), RECORD));
		}
	}

	@Test
	public void testNumbersAreAccepted() throws Exception {
		assertSameOutputEverywhere(
				"Numbers and texts that are entirely numbers compute as usual",
				"{ print $1 * 2, $5 + 1, $6 + 1, $7 / 10, -$1, +$5, int(\"3.7\"), \"3\" * 2, \" 4 \" + 1, 1.5e1 % 4,"
						+ " 2 ^ \"1e1\", \"-.5\" + 1, \"1.\" + 1; x = $7; x++; a[1] = $6; a[1] += 1; $5--;"
						+ " print x, a[1], $5, sqrt($1 + 8), atan2(\"0\", $7) }",
				"16 8 8 100 -8 7 3 6 5 3 1024 0.5 2",
				"1001 8 6 4 0");
		assertSameOutputEverywhere(
				"The results of comparisons, boolean tests and functions are numbers",
				"{ print length($3) * 2, substr($4, 1, 2) + 1, (1 == 1) + ($3 ~ /N/) + !$2 + (1 in none), NR + NF + FNR,"
						+ " RSTART + RLENGTH, index($4, \"W\") + match($4, /W/), split($4, parts, \" \") + sub(/N/, \"n\", $3),"
						+ " (rand() < 1) + (srand(1) >= 0), strtonum($4) * 8, tolower(\"12\") + 1 }",
				"6 13 3 10 0 8 3 2 96 13");
		assertSameOutputEverywhere(
				"Infinities and NaN are numbers, as values and as complete signed texts",
				"BEGIN { inf = -log(0); nan = log(-1); print inf + 1, -inf, \"+inf\" + 1, \"-INF\" * 2, \" +Inf \" - 1, int(inf),"
						+ " (nan + 1 == nan + 1), ((\"+nan\" * 2) == (\"+nan\" * 2)), ((-\"-NaN\") == 0) }",
				"inf -inf inf -inf inf inf 0 0 0");
		assertSameValueEverywhere("\"+inf\" + 1", Double.POSITIVE_INFINITY);
		assertSameValueEverywhere("-\"-inf\"", Double.POSITIVE_INFINITY);
		assertSameValueEverywhere("\"-nan\" + 1", Double.NaN);
		assertSameValueEverywhere("log(-1) * 2", Double.NaN);
		assertSameValueEverywhere("$7 * 2", 2000L);
		assertSameValueEverywhere("$6 + $5", 14L);
		assertSameValueEverywhere("$1 == \"\" ? 0 : $1 * 2", 16L);
		assertSameValueEverywhere("$2 == \"\" ? 0 : $2 * 2", 0L);
		for (String field : Arrays.asList("+inf", "-inf", " +INF ", "+nan", "-NaN")) {
			for (boolean optimize : BOTH) {
				Awk awk = awk(true, optimize);
				Object result = awk.eval(awk.compileExpression("$1 + 0", !optimize), field);
				assertEquals(field, Double.class, result.getClass());
				assertEquals(field, field.toLowerCase().contains("nan"), ((Double) result).isNaN());
				assertEquals(field, field.toLowerCase().contains("inf"), ((Double) result).isInfinite());
			}
		}
	}

	@Test
	public void testEveryOperationRefusesWhatIsNotANumber() throws Exception {
		for (Map.Entry<String, String> operand : REFUSED_OPERANDS.entrySet()) {
			for (String operation : OPERATIONS) {
				String expression = String.format(operation, operand.getKey());
				assertScriptRefused("{ print " + expression + " }", operand.getValue());
				assertExpressionRefused(expression, operand.getValue());
				assertScriptRefused(
						"function f(v) { return " + String.format(operation, "v") + " } { print f(" + operand.getKey() + ") }",
						operand.getValue());
			}
		}
	}

	@Test
	public void testIncrementDecrementAndCompoundAssignmentRefuseTheirTarget() throws Exception {
		Map<String, String> targets = new LinkedHashMap<String, String>();
		targets.put("x", "x = $3; ");
		targets.put("a[1]", "a[1] = $3; ");
		targets.put("a[1][2]", "a[1][2] = $3; ");
		targets.put("$3", "");
		for (Map.Entry<String, String> target : targets.entrySet()) {
			for (String operation : TARGET_OPERATIONS) {
				String statement = String.format(operation, target.getKey());
				assertScriptRefused("{ " + target.getValue() + statement + " }", NOT_A_NUMBER);
				assertScriptRefused("{ " + target.getValue() + "print " + statement + " }", NOT_A_NUMBER);
				assertScriptRefused(
						"function f(t) { " + String.format(operation, "t[\"k\"]") + " } { arr[\"k\"] = $3; f(arr) }",
						NOT_A_NUMBER);
				if (operation.contains("= ")) {
					// The target is examined first, then the right side, an operand like any other
					String assignment = String.format(operation.substring(0, operation.indexOf('=') + 1), target.getKey());
					assertScriptRefused("{ " + target.getValue() + assignment + " $4 }", NOT_A_NUMBER);
					assertScriptRefused("{ " + assignment.replace("$3", "$12") + " $4 }", "12 W");
				}
			}
		}
		for (String operation : TARGET_OPERATIONS) {
			assertExpressionRefused("(" + String.format(operation, "$3") + ")", NOT_A_NUMBER);
			assertExpressionRefused("(x = $4) && (" + String.format(operation, "x") + ")", "12 W");
			assertExpressionRefused("(a[1] = $4) && (" + String.format(operation, "a[1]") + ")", "12 W");
		}
		// An empty text was assigned: it is not a target that starts from zero
		assertScriptRefused("{ x = \"\"; x++ }", "");
		assertScriptRefused("{ a[1] = $2; a[1] += 1 }", "");
		assertScriptRefused("{ $2++ }", "");
	}

	@Test
	public void testUnassignedTargetsStartFromZero() throws Exception {
		assertSameOutputEverywhere(
				"The unassigned target of ++, -- and of a compound assignment starts from zero",
				"function bump(t) { t[\"k\"]++; t[\"l\"] += 4; locals() }"
						+ " function locals(v, w) { v++; w += 2; total = v + w }"
						+ " { n++; sum += $1; count[$1]++; b[$1] += 2; --m; $12++; $13 += 5; o = c[1]--; p = d[1]++; ++e[1];"
						+ " f[1][2]++; g[1][2] -= 3; h *= 2; i /= 2; j %= 2; k ^= 2; l -= 1; q = r++ + r--; bump(arr) }"
						+ " END { print n, sum, count[8], b[8], m, $12, $13, o, c[1], p, d[1], e[1], f[1][2], g[1][2],"
						+ " h, i, j, k, l, q, arr[\"k\"], arr[\"l\"], total }",
				"1 8 1 2 -1 1 5 0 -1 0 1 1 1 -3 0 0 0 0 -1 1 1 4 3");
		assertSameOutputEverywhere(
				"The usual accumulators work",
				"{ for (i = 1; i <= NF; i++) { if ($i ~ /^[0-9]+$/) { sum += $i; seen[$i]++ } else { others++ } } }"
						+ " END { print sum, seen[8], seen[\"007\"], others, length(seen) }",
				"15 1 1 6 2");
		for (String expression : Arrays.asList("n++", "n--", "count[$1]++", "count[$1]--", "t[1][2]++", "$12++", "$12--")) {
			for (boolean strict : BOTH) {
				for (boolean optimize : BOTH) {
					Awk awk = awk(strict, optimize);
					Object result = awk.eval(awk.compileExpression(expression, !optimize), RECORD);
					assertEquals(expression + mode(strict, optimize), 0.0, ((Number) result).doubleValue(), 0.0);
				}
			}
		}
		assertSameValueEverywhere("++n", 1L);
		assertSameValueEverywhere("--count[$1]", -1L);
		assertSameValueEverywhere("(sum += $1)", 8L);
		assertSameValueEverywhere("(count[$1] -= $6)", -7L);
		assertSameValueEverywhere("(t[1][2] += 3)", 3L);
		assertSameValueEverywhere("($12 += $5)", 7L);
	}

	@Test
	public void testIndirectCalls() throws Exception {
		for (String function : Arrays.asList("int", "sqrt", "exp", "log", "sin", "cos", "srand")) {
			assertScriptRefused("{ f = \"" + function + "\"; print @f($3) }", NOT_A_NUMBER);
			assertScriptRefused("{ f = \"" + function + "\"; print @f(y) }", null);
		}
		assertScriptRefused("{ f = \"atan2\"; print @f(1, $4) }", "12 W");
		assertScriptRefused("{ f = \"atan2\"; print @f($2, 1) }", "");
		assertScriptRefused("function g(v) { return v * 2 } { f = \"g\"; print @f($3) }", NOT_A_NUMBER);
		assertSameOutputEverywhere(
				"Numeric functions called indirectly accept numbers",
				"{ f = \"int\"; g = \"atan2\"; h = \"sqrt\"; l = \"length\"; print @f($7 / 3), @g(0, $1), @h($1 + 8), @l($3) + 1 }",
				"333 0 4 4");
		Awk awk = awk(true, true);
		try (AVM avm = awk.createAvm()) {
			try {
				avm.eval(
						awk.compileExpression("@f($3)"),
						new RecordSource(RECORD),
						Collections.<String, Object>singletonMap("f", "int"));
				fail("An indirect int() of N/A must fail");
			} catch (AwkNumberFormatException e) {
				assertEquals(NOT_A_NUMBER, e.getValue());
			}
		}
	}

	@Test
	public void testComparisonsConcatenationBooleanTestsAndStringFunctionsAreUntouched() throws Exception {
		assertSameOutputEverywhere(
				"Comparisons stay string comparisons on a text",
				"{ print ($3 > 5), ($3 < 5), ($3 == \"N/A\"), ($2 == 0), ($9 == 0), (y == 0), (y == \"\"), ($4 < $3), ($5 == 7) }",
				"1 0 1 0 1 1 1 1 1");
		assertSameOutputEverywhere("Concatenation", "{ print $3 \"-\" $9 y \"-\" $4 8 }", "N/A--12 W8");
		assertSameOutputEverywhere(
				"Boolean tests",
				"{ print !$2, !$3, ($3 && 1), ($2 || y), ($3 ? \"t\" : \"f\"), (y ? \"t\" : \"f\");"
						+ " if ($4) print \"then\"; while ($2) { } }",
				"1 0 1 0 t f",
				"then");
		assertSameOutputEverywhere(
				"String functions",
				"{ print length($9), length($4), substr($3, 2), index($4, \"W\"), toupper($4), split($4, parts, \" \"),"
						+ " match($3, \"/\"), tolower($3) }",
				"0 4 /A 4 12 W 2 2 n/a");
		assertSameValueEverywhere("$3 > 5", 1);
		assertSameValueEverywhere("$3 \"\" y", NOT_A_NUMBER);
		assertSameValueEverywhere("!$2 && !y", 1);
		assertSameValueEverywhere("length($4)", 4L);
	}

	@Test
	public void testPrintfAndStrtonumAreUntouched() throws Exception {
		assertSameOutputEverywhere(
				"printf and sprintf convert as usual",
				"{ printf \"%d %i %.1f %e|%5s|%c\\n\", $3, $4, $2, y, $3, $4;"
						+ " print sprintf(\"%d\", $3), sprintf(\"%d\", $4), sprintf(\"%*d\", $3, 5), sprintf(\"%x\", $9) }",
				"0 12 0.0 0.000000e+00|  N/A|1",
				"0 12 5 0");
		assertSameOutputEverywhere(
				"strtonum() is the explicit lenient conversion",
				"{ print strtonum($4), strtonum($3), strtonum($2), strtonum(\"0x1A\"), strtonum($3) * 8, strtonum($4) + 1 }",
				"12 0 0 26 0 13");
		assertSameValueEverywhere("sprintf(\"%d\", $3)", "0");
		assertSameValueEverywhere("strtonum($4) * 8", 96L);
		// The arithmetic inside the argument is not formatting
		assertScriptRefused("{ print sprintf(\"%d\", $2 + 0) }", "");
		assertExpressionRefused("sprintf(\"%d\", $3 + 0)", NOT_A_NUMBER);
		assertScriptRefused("{ printf \"%d\\n\", $4 * 1 }", "12 W");
	}

	@Test
	public void testPositionsCountsAndDivisionByZeroAreUntouched() throws Exception {
		assertSameOutputEverywhere(
				"Positions and counts of substr() and $(i)",
				"{ print substr(\"hello\", $3), substr(\"hello\", $4), substr(\"hello\", \"2x\", $2) \"|\", substr(\"hello\", y, 3),"
						+ " ($(y) == $0), $(\"1x\"), $($2) == $0 }",
				"hello  | hel 1 8 1");
		for (boolean strict : BOTH) {
			for (boolean optimize : BOTH) {
				AwkTestSupport.awkTest("exit converts its code as usual" + mode(strict, optimize))
						.script("{ exit $4 }")
						.withAwk(awk(strict, optimize))
						.stdin(RECORD + "\n")
						.expectExit(12)
						.runAndAssert();
				AwkTestSupport.awkTest("exit of a text is exit 0" + mode(strict, optimize))
						.script("{ exit $3 }")
						.withAwk(awk(strict, optimize))
						.stdin(RECORD + "\n")
						.expectExit(0)
						.runAndAssert();
			}
		}
		assertSameOutputEverywhere(
				"Division by zero",
				"{ print 1 / 0, -1 / 0, $1 % 0, $1 / \"0\", (x = 4) / (0 * $1) }",
				"inf -inf nan inf inf");
		assertSameValueEverywhere("$1 / 0", Double.POSITIVE_INFINITY);
		assertSameValueEverywhere("substr($3, $2)", NOT_A_NUMBER);
	}

	@Test
	public void testExtensionFunctionsConvertTheirArgumentsThemselves() throws Exception {
		assertSameOutputEverywhere(
				"The arguments of an extension function are not checked",
				"{ print twice($4), twice($3), twice(y), twice($9), twice($1), strictTwice($5) }",
				"24 0 0 0 16 14");
		// JRT.requireNumber() is the same check, whatever the setting
		for (boolean strict : BOTH) {
			for (boolean optimize : BOTH) {
				AwkTestSupport.TestResult result = AwkTestSupport
						.awkTest("An extension refuses a text" + mode(strict, optimize))
						.script("BEGIN { x = 1 }\n{ print strictTwice($4) }")
						.withAwk(awk(strict, optimize))
						.stdin(RECORD + "\n")
						.expectThrow(AwkNumberFormatException.class)
						.run();
				result.assertExpected();
				AwkNumberFormatException refusal = (AwkNumberFormatException) result.thrownException();
				assertEquals("12 W", refusal.getValue());
				assertEquals(2, refusal.getLineNumber());
				assertSame(AwkNumberFormatException.class, refusal.getCause().getClass());

				// An unassigned variable reaches a plain parameter as an empty text
				result = AwkTestSupport.awkTest("An extension refuses an unassigned variable" + mode(strict, optimize))
						.script("{ print strictTwice(y) }")
						.withAwk(awk(strict, optimize))
						.stdin(RECORD + "\n")
						.expectThrow(AwkNumberFormatException.class)
						.run();
				result.assertExpected();
				assertEquals("", ((AwkNumberFormatException) result.thrownException()).getValue());
			}
		}
	}

	@Test
	public void testFieldInTheLocaleOfTheRuntimeIsANumber() throws Exception {
		// A field is a number when the runtime recognizes it as one, here with a decimal comma
		AwkSettings settings = new AwkSettings();
		settings.setFieldSeparator(";");
		settings.setLocale(Locale.FRANCE);
		for (boolean strict : BOTH) {
			settings.setStrictNumbers(strict);
			Awk awk = new Awk(settings);
			assertEquals(3L, awk.eval("$8 * 2", RECORD));
			assertEquals(16L, awk.eval("$1 * 2", RECORD));
			assertEquals(3L, awk.eval("$1 * 2", "1.5"));
		}
		assertExpressionRefused("$8 * 2", "1,5");
		assertFalse(JRT.isNumber("1,5"));
	}

	@Test
	public void testExceptionCarriesTheValueAndTheLine() throws Exception {
		AwkNumberFormatException text = assertExpressionRefused("$4 * 8", "12 W");
		assertEquals("\"12 W\" is not a number", text.getMessage());
		assertTrue(AwkRuntimeException.class.isInstance(text));

		AwkNumberFormatException empty = assertExpressionRefused("$2 * 8", "");
		assertEquals("\"\" is not a number", empty.getMessage());

		AwkNumberFormatException unassigned = assertExpressionRefused("1 + $9", null);
		assertEquals("An unassigned value is not a number", unassigned.getMessage());
		assertNull(unassigned.getValue());

		AwkNumberFormatException located = assertScriptRefused("BEGIN { x = 1\n y = -\"abc\"; z = 3\n }", "abc");
		assertEquals(2, located.getLineNumber());
		assertEquals("\"abc\" is not a number", located.getMessage());

		AwkNumberFormatException inFunction = assertScriptRefused(
				"function f(v) {\n w = v + 1; return w\n }\n { print f($3) }",
				NOT_A_NUMBER);
		assertEquals(2, inFunction.getLineNumber());

		assertEquals(-1, new AwkNumberFormatException("x").getLineNumber());
		assertEquals(7, new AwkNumberFormatException(7, null).getLineNumber());
		assertEquals("An unassigned value is not a number", new AwkNumberFormatException(null).getMessage());
	}

	@Test
	public void testAvmStaysUsableAfterARefusal() throws Exception {
		Awk awk = awk(true, true);
		AwkExpression refused = awk.compileExpression("1 + 2 * (3 - $2)");
		AwkExpression doubled = awk.compileExpression("$1 * 2");
		AwkExpression counter = awk.compileExpression("++n + $1");
		try (AVM avm = awk.createAvm()) {
			for (String record : Arrays.asList(RECORD, "5;", "0.5;;x")) {
				assertTrue(avm.prepareForEval(record));
				try {
					avm.eval(refused);
					fail("An empty field must be refused");
				} catch (AwkNumberFormatException e) {
					assertEquals("", e.getValue());
				}
				// The operands left by the failed evaluation are gone, the record is still there
				Object expected = awk.eval(doubled, record);
				assertEquals(expected, avm.eval(doubled));
				assertEquals(expected, avm.eval(doubled, new RecordSource(record)));
				assertEquals(
						JRT.toDouble(record.substring(0, record.indexOf(';'))) + 1,
						((Number) avm.eval(counter)).doubleValue(),
						0.0);
			}
			assertTrue(avm.prepareForEval(";"));
			try {
				avm.eval(doubled);
				fail("An empty field must be refused");
			} catch (AwkNumberFormatException e) {
				assertEquals("", e.getValue());
			}
			assertTrue(avm.prepareForEval("21"));
			assertEquals(42L, avm.eval(doubled));
		}
		// One Awk goes on too, for scripts and for expressions
		try {
			awk.script("{ print $1 * 2 }").input("x\n").execute();
			fail("A text must be refused");
		} catch (AwkNumberFormatException e) {
			assertEquals("x", e.getValue());
		}
		assertEquals(16L, awk.eval(doubled, RECORD));
	}

	@Test
	public void testVariablesOfAnEvaluationBehaveLikeFields() throws Exception {
		Awk awk = awk(true, true);
		AwkExpression ratio = awk.compileExpression("used / total * 100");
		try (AVM avm = awk.createAvm()) {
			assertEquals(25L, avm.eval(ratio, new RecordSource(""), variables("50", "200")));
			assertEquals(25L, avm.eval(ratio, new RecordSource(""), variables(" 50 ", "2e2")));
			assertEquals(25L, avm.eval(ratio, new RecordSource(""), variables(50, new BigDecimal("200"))));
			assertEquals(12.5, avm.eval(ratio, new RecordSource(""), variables(0.25, 2L)));
			for (Object used : Arrays.<Object>asList(NOT_A_NUMBER, "", "12 W")) {
				try {
					avm.eval(ratio, new RecordSource(""), variables(used, "200"));
					fail(used + " must be refused");
				} catch (AwkNumberFormatException e) {
					assertEquals(used, e.getValue());
				}
			}
			try {
				avm.eval(ratio, new RecordSource(""), variables("50", null));
				fail("A variable that is not passed must be refused");
			} catch (AwkNumberFormatException e) {
				assertNull(e.getValue());
			}
			// Still usable, and what does not compute still does not fail
			assertEquals(25L, avm.eval(ratio, new RecordSource(""), variables("50", "200")));
			assertEquals(
					"N/A of 200",
					avm.eval(awk.compileExpression("used \" of \" total"), new RecordSource(""), variables(NOT_A_NUMBER, "200")));
		}

		AwkSettings settings = new AwkSettings();
		settings.setStrictNumbers(true);
		settings.putVariable("limit", "12 W");
		settings.putVariable("count", "12");
		AwkTestSupport.awkTest("A variable of the settings that is a number")
				.script("BEGIN { print count * 2 }")
				.withAwk(new Awk(settings))
				.expectLines("24")
				.runAndAssert();
		AwkTestSupport.awkTest("A variable of the settings that is not a number")
				.script("BEGIN { print limit * 2 }")
				.withAwk(new Awk(settings))
				.expectThrow(AwkNumberFormatException.class)
				.runAndAssert();
	}

	@Test
	public void testCompiledTuplesDoNotDependOnTheSetting() throws Exception {
		Awk lenient = awk(false, true);
		Awk strict = awk(true, true);

		// Arithmetic on a literal that is not a number is left to the interpreter
		for (String text : Arrays
				.asList("\"abc\" * 2", "2 + \"\"", "-\"abc\"", "+\"3x\"", "\"0x1A\" ^ 2", "1 + 2 * \"x\"")) {
			AwkExpression byLenient = lenient.compileExpression(text);
			AwkExpression byStrict = strict.compileExpression(text);
			assertFalse(text, byLenient.isConstant());
			assertFalse(text, byStrict.isConstant());
			assertTrue(text, lenient.eval(byStrict, RECORD) instanceof Number);
			try {
				strict.eval(serialized(byLenient), RECORD);
				fail(text + " compiled without strict numbers must fail with them");
			} catch (AwkNumberFormatException e) {
				assertFalse(text, JRT.isNumber(e.getValue()));
			}
		}
		// A literal that is a number is still folded
		assertEquals(6L, strict.compileExpression("\"3\" * 2").getConstantValue());
		assertEquals(6L, lenient.compileExpression("\" 3 \" * 2").getConstantValue());
		assertEquals(-1000L, strict.compileExpression("-\"1e3\"").getConstantValue());
		assertEquals(Double.POSITIVE_INFINITY, lenient.compileExpression("\"+inf\" + 1").getConstantValue());
		assertEquals(7L, strict.compileExpression("1 + 2 * 3").getConstantValue());

		// A program compiled by one engine and serialized runs on the other
		String script = "{ count[$1]++; total += $1; old = seen[$1]--; print count[$1], total, old, \"abc\" * 1 }";
		assertEquals("1 8 0 0\n", lenient.script(serialized(strict.compile(script))).input(RECORD + "\n").execute());
		try {
			strict.script(serialized(lenient.compile(script))).input(RECORD + "\n").execute();
			fail("\"abc\" * 1 compiled without strict numbers must fail with them");
		} catch (AwkNumberFormatException e) {
			assertEquals("abc", e.getValue());
		}
		String accumulators = "{ count[$1]++; total += $1; old = seen[$1]--; print count[$1], total, old }";
		assertEquals("1 8 0\n", strict.script(serialized(lenient.compile(accumulators))).input(RECORD + "\n").execute());
	}

	@Test
	public void testIsNumberAndRequireNumber() throws Exception {
		List<Object> numbers = Arrays.<Object>asList(
				1L,
				2,
				1.5,
				Double.NaN,
				Double.NEGATIVE_INFINITY,
				new BigDecimal("1.5"),
				'7',
				"3",
				" 12 ",
				"\t12\n",
				"007",
				"1e3",
				"1E+3",
				"-.5",
				"1.",
				"+7",
				"+inf",
				"-INF",
				" -NaN ",
				"+nan");
		for (Object number : numbers) {
			assertTrue(String.valueOf(number), JRT.isNumber(number));
			JRT.requireNumber(number);
		}
		List<String> texts = Arrays.asList(
				"",
				" ",
				NOT_A_NUMBER,
				"12 W",
				"1,5",
				"0x1A",
				"1e",
				"1e+",
				".",
				"+",
				"-",
				"inf",
				"nan",
				"+infinity",
				"+inf1",
				"1 2",
				"--1",
				"1.2.3");
		for (String text : texts) {
			assertFalse(text, JRT.isNumber(text));
			try {
				JRT.requireNumber(text);
				fail(text + " is not a number");
			} catch (AwkNumberFormatException e) {
				assertEquals(text, e.getValue());
				assertEquals("\"" + text + "\" is not a number", e.getMessage());
				assertEquals(-1, e.getLineNumber());
			}
		}
		for (Object unassigned : Arrays.asList(null, new UninitializedObject())) {
			assertFalse(JRT.isNumber(unassigned));
			try {
				JRT.requireNumber(unassigned);
				fail("An unassigned value is not a number");
			} catch (AwkNumberFormatException e) {
				assertNull(e.getValue());
			}
		}

		// The same grammar as the operators, for a field and for a string
		Awk awk = awk(true, true);
		AwkExpression field = awk.compileExpression("$0 + 0");
		AwkExpression string = awk.compileExpression("($0 \"\") + 0");
		for (Object value : numbers) {
			if (value instanceof String) {
				assertEquals(
						(String) value,
						JRT.toDouble(value),
						((Number) awk.eval(field, (String) value)).doubleValue(),
						0.0);
				assertEquals(
						(String) value,
						JRT.toDouble(value),
						((Number) awk.eval(string, (String) value)).doubleValue(),
						0.0);
			}
		}
		for (String text : texts) {
			for (AwkExpression expression : Arrays.asList(field, string)) {
				try {
					Object result = awk.eval(expression, text);
					fail("\"" + text + "\" must be refused, but is " + result);
				} catch (AwkNumberFormatException e) {
					assertEquals(text, e.getValue());
				}
			}
		}
	}

	private static Map<String, Object> variables(Object used, Object total) {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put("used", used);
		if (total != null) {
			variables.put("total", total);
		}
		return variables;
	}

	@SuppressWarnings("unchecked")
	private static <T> T serialized(T tuples) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(tuples);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return (T) in.readObject();
		}
	}

	/** Input source of one record, whose fields the runtime splits. */
	private static final class RecordSource implements InputSource {

		private final String record;
		private boolean pending = true;

		private RecordSource(String record) {
			this.record = record;
		}

		@Override
		public boolean nextRecord() {
			boolean available = pending;
			pending = false;
			return available;
		}

		@Override
		public String getRecordText() {
			return record;
		}

		@Override
		public List<String> getFields() {
			return null;
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}
	}
}
