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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.jawk.ext.GawkExtension;
import io.jawk.jrt.AwkRuntimeException;

/**
 * Behavioral tests for the default gawk compatibility extension: asort(),
 * asorti(), typeof(), isarray(), mkbool(), and PROCINFO["sorted_in"].
 */
public class GawkExtensionTest {

	@Test
	public void defaultExtensionSetIsPerEngine() throws Exception {
		Object first = new Awk().getExtensionInstances().get(GawkExtension.class.getName());
		Object second = new Awk().getExtensionInstances().get(GawkExtension.class.getName());
		assertNotSame(
				"each engine must get its own GawkExtension instance",
				first,
				second);
	}

	@Test
	public void explicitEmptyExtensionListReclaimsBuiltinNames() throws Exception {
		// An explicit empty extension list must free gawk builtin identifiers
		// such as typeof for use as ordinary variables.
		StringBuilder out = new StringBuilder();
		new Awk(java.util.Collections.<io.jawk.ext.JawkExtension>emptyList())
				.script("BEGIN { typeof = 3; print typeof }")
				.execute(out);
		assertEquals("3\n", out.toString());
	}

	@Test
	public void extraFunctionArgumentsAreEvaluatedForSideEffects() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("extra arguments are evaluated then discarded")
				.script("function f(a) { return a } BEGIN { f(1, x = 5); print x }")
				.expect("5\n")
				.run();
		result.assertExpected();
		assertTrue(
				"extra-argument warning should still be printed",
				result.errorOutput().contains("called with more arguments than declared"));
	}

	@Test
	public void gensubTreatsAnyGPrefixedSelectorAsGlobal() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("gensub global selector accepts any g-prefixed string")
				.script("BEGIN { print gensub(/o/, \"0\", \"gg\", \"foo\") }")
				.expect("f00\n")
				.run();
		result.assertExpected();
		assertTrue("g-prefixed selector must not warn", result.errorOutput().isEmpty());
	}

	@Test
	public void gensubHonorsIgnoreCase() throws Exception {
		AwkTestSupport
				.awkTest("IGNORECASE makes gensub matching case-insensitive")
				.script(
						"BEGIN { "
								+ "print gensub(/a/, \"x\", \"g\", \"A\"); "
								+ "IGNORECASE = 1; "
								+ "print gensub(/a/, \"x\", \"g\", \"A\") "
								+ "}")
				.expectLines("A", "x")
				.runAndAssert();
	}

	@Test
	public void rawValueExtensionCallsWorkInEvalExpressions() throws Exception {
		// The eval optimizer must keep the global frame alive for raw
		// dereferences and extension calls.
		Awk awk = new Awk();
		assertEquals("untyped", awk.eval("typeof(x)"));
		assertEquals(1L, ((Number) awk.eval("isarray(x) + 1")).longValue());
	}

	@Test
	public void gensubReplacesOnlyRequestedOccurrence() throws Exception {
		AwkTestSupport
				.awkTest("gensub numeric selector targets one occurrence")
				.script("BEGIN { print gensub(/o/, \"0\", 2, \"foo\") }")
				.expectLines("fo0")
				.runAndAssert();
	}

	@Test
	public void ignoreCasePreassignedOnCommandLineAffectsSorting() throws Exception {
		AwkTestSupport
				.cliTest("-v IGNORECASE=1 is honored even when unreferenced")
				.preassign("IGNORECASE", 1)
				.script("BEGIN { a[\"B\"] = 1; a[\"a\"] = 2; n = asorti(a, d); for (i = 1; i <= n; i++) print d[i] }")
				.expectLines("a", "B")
				.runAndAssert();
	}

	@Test
	public void incrementAndDecrementWorkOnSpecialVariables() throws Exception {
		// JRT-managed specials are read/written through dedicated opcodes, so
		// ++/-- must route through them instead of the slot-based INC/DEC.
		AwkTestSupport
				.awkTest("prefix and postfix inc/dec apply to special variables")
				.script(
						"BEGIN { "
								+ "NR = 5; print NR++, NR, ++NR, NR--, --NR; "
								+ "for (IGNORECASE = 0; IGNORECASE < 2; IGNORECASE++) printf \"%d\", IGNORECASE; "
								+ "print \"\" "
								+ "}")
				.expectLines("5 6 7 7 5", "01")
				.runAndAssert();
	}

	@Test
	public void ignoreCaseUsesAwkTruthiness() throws Exception {
		// gawk: IGNORECASE is active when "nonzero or non-null", so a
		// non-numeric string is truthy.
		AwkTestSupport
				.awkTest("IGNORECASE follows AWK truthiness")
				.script(
						"BEGIN { "
								+ "IGNORECASE = \"yes\"; print match(\"ABC\", \"b\"); "
								+ "IGNORECASE = 0; print match(\"ABC\", \"b\") "
								+ "}")
				.expectLines("2", "0")
				.runAndAssert();
	}

	@Test
	public void ignoreCaseOperandAssignmentAppliesBetweenFiles() throws Exception {
		AwkTestSupport
				.cliTest("IGNORECASE=1 operand takes effect for later files")
				.file("f1", "ABC\n")
				.file("f2", "ABC\n")
				.script("{ print match($0, \"b\") }")
				.operand("{{f1}}", "IGNORECASE=1", "{{f2}}")
				.expectLines("0", "2")
				.runAndAssert();
	}

	@Test
	public void fsOperandAssignmentAppliesBetweenFiles() throws Exception {
		// JRT-managed specials assigned as command-line operands must reach
		// the JRT, not a (nonexistent) global slot.
		AwkTestSupport
				.cliTest("FS=: operand takes effect for later files")
				.file("f1", "a:b\n")
				.script("{ print $2 }")
				.operand("FS=:", "{{f1}}")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void ignoreCaseAppliesToRegexpConstants() throws Exception {
		// gawk applies IGNORECASE to regexp constants at runtime: patterns,
		// match expressions, and sub/gsub.
		AwkTestSupport
				.awkTest("IGNORECASE affects precompiled regexp literals")
				.script(
						"BEGIN { IGNORECASE = 1; s = \"AAA\"; n = gsub(/a/, \"x\", s); print n, s } "
								+ "/foo/ { print \"pattern\" } "
								+ "$0 ~ /foo/ { print \"expr\" }")
				.stdin("FOO\n")
				.expectLines("3 xxx", "pattern", "expr")
				.runAndAssert();
	}

	@Test
	public void gensubMissingBackreferenceExpandsToEmpty() throws Exception {
		// gawk substitutes an empty string for \N beyond the pattern's groups;
		// a bare $N would make Java's Matcher throw
		AwkTestSupport
				.awkTest("gensub backreference beyond group count is empty")
				.script(
						"BEGIN { print \"x\" gensub(/a/, \"\\\\1\", \"g\", \"a\") \"y\"; print gensub(/(o)/, \"[\\\\1]\", \"g\", \"foo\") }")
				.expectLines("xy", "f[o][o]")
				.runAndAssert();
	}

	@Test
	public void splitAndFieldSplittingHonorIgnoreCase() throws Exception {
		AwkTestSupport
				.awkTest("IGNORECASE applies to split() delimiters and FS")
				.script(
						"BEGIN { "
								+ "n = split(\"aBa\", a, /b/); print n; "
								+ "IGNORECASE = 1; "
								+ "n = split(\"aBa\", a, /b/); print n, a[1], a[2]; "
								+ "n = split(\"aXBa\", a, \"xb\"); print n; "
								+ "FS = \"[bc]\" "
								+ "} "
								+ "{ print NF, $2 }")
				.stdin("xByCz\n")
				.expectLines("1", "2 a a", "2", "3 y")
				.runAndAssert();
	}

	@Test
	public void gensubSelectorUsesAwkNumericCoercion() throws Exception {
		// gawk converts the selector like any AWK number (" 2" is 2, "1e1" is
		// 10) and warns "treated as 1" only when the result is below 1
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("gensub occurrence selector follows AWK numeric conversion")
				.script(
						"BEGIN { "
								+ "print gensub(/o/, \"0\", \"1e1\", \"foo\"); "
								+ "print gensub(/o/, \"0\", \" 2\", \"foo\"); "
								+ "print gensub(/o/, \"0\", \"abc\", \"foo\"); "
								+ "print gensub(/o/, \"0\", 0, \"foo\") "
								+ "}")
				.expectLines("foo", "fo0", "f0o", "f0o")
				.run();
		result.assertExpected();
		assertTrue(
				"non-numeric selector must warn",
				result.errorOutput().contains("third argument `abc' treated as 1"));
		assertTrue(
				"selector below 1 must warn",
				result.errorOutput().contains("third argument `0' treated as 1"));
		assertFalse(
				"valid numeric selectors must not warn",
				result.errorOutput().contains("` 2'"));
	}

	@Test
	public void misspelledUnsortedModeIsFatal() throws Exception {
		// only the exact "@unsorted" skips sorting; anything else with an
		// @-prefix is an undefined comparison mode, fatal as in gawk
		AwkTestSupport
				.awkTest("@unsorted with a suffix is rejected")
				.script("BEGIN { a[1] = 2; asort(a, d, \"@unsortedx\") }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
		AwkTestSupport
				.awkTest("@unsorted with a suffix is rejected in for-in")
				.script("BEGIN { a[1] = 2; PROCINFO[\"sorted_in\"] = \"@unsorted_asc\"; for (k in a) print k }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void invalidSortModeIsFatal() throws Exception {
		// gawk treats undefined sort comparison names as a fatal error
		AwkTestSupport
				.awkTest("invalid asort mode is rejected")
				.script("BEGIN { a[1] = 2; asort(a, d, \"@bogus\") }")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void ignoreCaseIsLiveForMatchAndReadable() throws Exception {
		AwkTestSupport
				.awkTest("IGNORECASE assignment applies immediately and reads back")
				.script(
						"BEGIN { "
								+ "print match(\"ABC\", \"b\"); "
								+ "IGNORECASE = 1; "
								+ "print match(\"ABC\", \"b\"), IGNORECASE "
								+ "}")
				.expectLines("0", "2 1")
				.runAndAssert();
	}

	@Test
	public void argvWorksWithoutReferencingArgc() throws Exception {
		// ARGV population derives its count from the argument list itself and
		// must not depend on the ARGC slot being materialized first.
		AwkTestSupport
				.cliTest("ARGV is populated even when ARGC is unreferenced")
				.script("BEGIN { print ARGV[0] }")
				.expectLines("jawk")
				.runAndAssert();
	}

	@Test
	public void runtimeManagedGlobalsAreMaterializedOnlyWhenNeeded() throws Exception {
		String plain = tupleDump("BEGIN { x = 1 }");
		assertFalse("unreferenced ENVIRON must not be materialized", plain.contains("ENVIRON_OFFSET"));
		assertFalse("unreferenced ARGV must not be materialized", plain.contains("ARGV_OFFSET"));
		// ARGC stays materialized: its slot drives ARGC=n operand assignments
		String withSymtab = tupleDump("BEGIN { n = length(SYMTAB) }");
		assertTrue("SYMTAB requires ENVIRON", withSymtab.contains("ENVIRON_OFFSET"));
		assertTrue("SYMTAB requires ARGC", withSymtab.contains("ARGC_OFFSET"));
		assertTrue("SYMTAB requires ARGV", withSymtab.contains("ARGV_OFFSET"));
	}

	private static String tupleDump(String script) throws Exception {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		new Awk().compile(script).dump(new java.io.PrintStream(bytes, true, "UTF-8"));
		return bytes.toString("UTF-8");
	}

	@Test
	public void everySpecialVariableNameIsAnsweredByGetVariable() throws Exception {
		// getSpecialVariableNames() and the switch in AVM.getVariable() must
		// stay in sync: every listed name must be answered with a value.
		Awk awk = new Awk();
		AwkProgram program = awk.compile("BEGIN { }");
		try (io.jawk.backend.AVM avm = awk.createAvm()) {
			avm
					.execute(
							program,
							new io.jawk.jrt.StreamInputSource(
									new java.io.ByteArrayInputStream(new byte[0]),
									avm,
									avm.getJrt()),
							java.util.Collections.<String>emptyList(),
							null);
			for (String name : avm.getSpecialVariableNames()) {
				assertTrue(
						"getVariable must answer special variable " + name,
						avm.getVariable(name) != null);
			}
		}
	}

	@Test
	public void symtabReflectsInitialVariablesAndOperandAssignments() throws Exception {
		// gawk parity: -v variables without a compiled slot appear in SYMTAB,
		// and name=value operand assignments update it live between files.
		AwkTestSupport
				.cliTest("SYMTAB sees -v variables and live operand assignments")
				.preassign("toto", "tata")
				.file("f1", "a\n")
				.file("f2", "b\n")
				.script("{ print SYMTAB[\"toto\"], $1 }")
				.operand("{{f1}}", "toto=titi", "{{f2}}")
				.expectLines("tata a", "titi b")
				.runAndAssert();
	}

	@Test
	public void symtabAndFunctabAreNotMaterializedInPosixMode() throws Exception {
		AwkTestSupport
				.cliTest("POSIX mode leaves SYMTAB and FUNCTAB empty")
				.argument("--posix")
				.script("BEGIN { print length(SYMTAB), length(FUNCTAB) }")
				.expectLines("0 0")
				.runAndAssert();
	}

	@Test
	public void symtabSeesRuntimeManagedGlobals() throws Exception {
		// ARGC/ARGV are populated before the beforeStart hooks run, so the
		// SYMTAB snapshot must observe their real values.
		AwkTestSupport
				.awkTest("SYMTAB snapshots ARGC and ARGV after initialization")
				.script("BEGIN { print SYMTAB[\"ARGC\"], SYMTAB[\"ARGV\"][0] }")
				.expectLines("1 jawk")
				.runAndAssert();
	}

	@Test
	public void ignoreCaseAppliesToComparisonsAndIndex() throws Exception {
		// gawk's IGNORECASE covers string relational operators and index(),
		// not just regexp operations; numeric (strnum) comparisons stay numeric
		AwkTestSupport
				.awkTest("IGNORECASE folds case in ==, < and index()")
				.script(
						"BEGIN { "
								+ "IGNORECASE = 1; "
								+ "eq = (\"a\" == \"A\"); ix = index(\"Ab\", \"a\"); lt = (\"B\" < \"a\"); "
								+ "print eq, ix, lt; "
								+ "IGNORECASE = 0; "
								+ "eq = (\"a\" == \"A\"); ix = index(\"Ab\", \"a\"); lt = (\"B\" < \"a\"); "
								+ "print eq, ix, lt "
								+ "} "
								+ "{ e = ($1 == $2); print e }")
				.stdin("10 10.0\n")
				.expectLines("1 1 0", "0 0 1", "1")
				.runAndAssert();
	}

	@Test
	public void metaTablesCannotBeUsedAsScalars() throws Exception {
		// gawk: fatal "attempt to use array `SYMTAB' in a scalar context"; the
		// meta tables are array-typed from the moment they exist
		AwkTestSupport
				.awkTest("assigning a scalar to SYMTAB is a semantic error")
				.script("BEGIN { SYMTAB = 1 }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
		AwkTestSupport
				.awkTest("assigning a scalar to FUNCTAB is a semantic error")
				.script("BEGIN { FUNCTAB = 1 }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void perRunVariablesAreVisibleToSymtabAndExtensions() throws Exception {
		// Per-run overrides without a compiled slot must be observable through
		// SYMTAB and by-name lookups from extensions.
		StringBuilder out = new StringBuilder();
		new Awk()
				.script("BEGIN { print SYMTAB[\"toto\"] }")
				.variable("toto", "tata")
				.execute(out);
		assertEquals("tata\n", out.toString());

		// a host-supplied PROCINFO drives for-in ordering even when the script
		// never references PROCINFO itself
		java.util.Map<String, Object> procinfo = new java.util.HashMap<String, Object>();
		procinfo.put("sorted_in", "@ind_str_desc");
		out.setLength(0);
		new Awk()
				.script("BEGIN { a[\"x\"] = 1; a[\"y\"] = 2; for (k in a) print k }")
				.variable("PROCINFO", procinfo)
				.execute(out);
		assertEquals("y\nx\n", out.toString());
	}

	@Test
	public void symtabExcludesTheMetaTablesThemselves() throws Exception {
		// gawk keeps SYMTAB and FUNCTAB out of the SYMTAB snapshot
		AwkTestSupport
				.awkTest("SYMTAB does not contain SYMTAB or FUNCTAB entries")
				.script(
						"function foo() { return 1 } "
								+ "BEGIN { "
								+ "x = (\"SYMTAB\" in SYMTAB); "
								+ "y = (\"FUNCTAB\" in SYMTAB); "
								+ "z = (\"foo\" in FUNCTAB); "
								+ "print x, y, z "
								+ "}")
				.expectLines("0 0 1")
				.runAndAssert();
	}

	@Test
	public void indexNumberModeBreaksTiesAsText() throws Exception {
		// gawk's @ind_num_* breaks numeric ties (all non-numeric indexes
		// coerce to 0) with a string comparison, so the order is
		// deterministic regardless of insertion order
		AwkTestSupport
				.awkTest("@ind_num orders equal-valued indexes as text")
				.script(
						"BEGIN { "
								+ "a[\"b\"] = 1; a[\"a\"] = 1; a[\"0\"] = 1; a[\"B\"] = 1; "
								+ "n = asorti(a, d, \"@ind_num_asc\"); "
								+ "print d[1], d[2], d[3], d[4]; "
								+ "n = asorti(a, d, \"@ind_num_desc\"); "
								+ "print d[1], d[2], d[3], d[4] "
								+ "}")
				.expectLines("0 B a b", "b a B 0")
				.runAndAssert();
	}

	@Test
	public void symtabAndFunctabExposeRealNames() throws Exception {
		AwkTestSupport
				.awkTest("SYMTAB and FUNCTAB hold real symbol names")
				.script(
						"function myfunc() { return 1 } "
								+ "BEGIN { "
								+ "nr = (\"NR\" in SYMTAB); fs = (\"FS\" in SYMTAB); print nr, fs; "
								+ "mf = (\"myfunc\" in FUNCTAB); gs = (\"gensub\" in FUNCTAB); print mf, gs "
								+ "}")
				.expectLines("1 1", "1 1")
				.runAndAssert();
	}

	@Test
	public void gensubWarningGoesToErrorStreamNotOutput() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("gensub third-argument warning targets stderr")
				.script("BEGIN { print gensub(/o/, \"0\", \"a\", \"foo\") }")
				.expect("f0o\n")
				.run();
		result.assertExpected();
		assertTrue(
				"gensub warning should be printed to the error stream",
				result.errorOutput().contains("warning: gensub: third argument"));
	}

	@Test
	public void extraFunctionArgumentWarningGoesToErrorStreamNotOutput() throws Exception {
		AwkTestSupport.TestResult result = AwkTestSupport
				.cliTest("extra-argument warning targets stderr")
				.script("function f(a) { return a } BEGIN { print f(1, 2) }")
				.expect("1\n")
				.run();
		result.assertExpected();
		assertTrue(
				"extra-argument warning should be printed to the error stream",
				result.errorOutput().contains("called with more arguments than declared"));
	}

	@Test
	public void asortSortsValuesAndRenumbersIndices() throws Exception {
		AwkTestSupport
				.awkTest("asort sorts by value with integer indices from 1")
				.script(
						"BEGIN { "
								+ "a[\"x\"] = 30; a[\"y\"] = 10; a[\"z\"] = 20; "
								+ "n = asort(a); "
								+ "for (i = 1; i <= n; i++) print i, a[i] "
								+ "}")
				.expectLines("1 10", "2 20", "3 30")
				.runAndAssert();
	}

	@Test
	public void asortWithDestinationLeavesSourceUntouched() throws Exception {
		AwkTestSupport
				.awkTest("asort second argument receives the sorted copy")
				.script(
						"BEGIN { "
								+ "a[\"x\"] = \"b\"; a[\"y\"] = \"a\"; "
								+ "n = asort(a, dest); "
								+ "print n, dest[1], dest[2], a[\"x\"], a[\"y\"] "
								+ "}")
				.expectLines("2 a b b a")
				.runAndAssert();
	}

	@Test
	public void asortiSortsByIndex() throws Exception {
		AwkTestSupport
				.awkTest("asorti sorts array indices into values")
				.script(
						"BEGIN { "
								+ "a[\"banana\"] = 1; a[\"apple\"] = 1; a[\"cherry\"] = 1; "
								+ "n = asorti(a, dest); "
								+ "for (i = 1; i <= n; i++) print dest[i] "
								+ "}")
				.expectLines("apple", "banana", "cherry")
				.runAndAssert();
	}

	@Test
	public void asortHonorsPredefinedDescendingMode() throws Exception {
		AwkTestSupport
				.awkTest("asort third argument selects a predefined sort mode")
				.script(
						"BEGIN { "
								+ "a[1] = 10; a[2] = 30; a[3] = 20; "
								+ "n = asort(a, dest, \"@val_num_desc\"); "
								+ "for (i = 1; i <= n; i++) print dest[i] "
								+ "}")
				.expectLines("30", "20", "10")
				.runAndAssert();
	}

	@Test
	public void valueStringModeComparesAllValuesAsText() throws Exception {
		// gawk's @val_str_* compares string representations regardless of the
		// runtime type: no numbers-before-strings ranking
		AwkTestSupport
				.awkTest("@val_str_asc orders the number 2 after the string \"10\"")
				.script(
						"BEGIN { "
								+ "a[1] = 2; a[2] = \"10\"; "
								+ "n = asort(a, d, \"@val_str_asc\"); "
								+ "for (i = 1; i <= n; i++) print d[i] "
								+ "}")
				.expectLines("10", "2")
				.runAndAssert();
	}

	@Test
	public void valueNumberModeCoercesStringsAndBreaksTiesAsText() throws Exception {
		// gawk's @val_num_* coerces every scalar to a number (non-numeric
		// strings count as 0) and breaks numeric ties with a string comparison
		AwkTestSupport
				.awkTest("@val_num_asc sorts string values numerically")
				.script(
						"BEGIN { "
								+ "a[1] = \"10\"; a[2] = \"2\"; "
								+ "n = asort(a, d, \"@val_num_asc\"); "
								+ "print d[1], d[2]; "
								+ "b[1] = \"abc\"; b[2] = 5; b[3] = 0; b[4] = \"zz\"; "
								+ "n = asort(b, d2, \"@val_num_asc\"); "
								+ "print d2[1], d2[2], d2[3], d2[4] "
								+ "}")
				.expectLines("2 10", "0 abc zz 5")
				.runAndAssert();
	}

	@Test
	public void emptySortModeMeansDefault() throws Exception {
		AwkTestSupport
				.awkTest("an empty third argument behaves like an omitted one")
				.script(
						"BEGIN { "
								+ "a[1] = 2; a[2] = 1; "
								+ "n = asort(a, d, \"\"); "
								+ "print d[1], d[2] "
								+ "}")
				.expectLines("1 2")
				.runAndAssert();
	}

	@Test
	public void asortiDefaultsToStringIndexOrder() throws Exception {
		// gawk's default for asorti() is @ind_str_asc: numeric-looking indexes
		// stay in string order ("10" < "2")
		AwkTestSupport
				.awkTest("asorti default keeps string index order")
				.script(
						"BEGIN { "
								+ "a[\"10\"] = 1; a[\"2\"] = 1; a[\"b\"] = 1; "
								+ "n = asorti(a, d); "
								+ "print d[1], d[2], d[3] "
								+ "}")
				.expectLines("10 2 b")
				.runAndAssert();
	}

	@Test
	public void forInHonorsProcinfoSortedIn() throws Exception {
		AwkTestSupport
				.awkTest("for-in traversal follows PROCINFO[\"sorted_in\"]")
				.script(
						"BEGIN { "
								+ "a[3] = \"c\"; a[1] = \"a\"; a[2] = \"b\"; "
								+ "PROCINFO[\"sorted_in\"] = \"@ind_num_desc\"; "
								+ "for (i in a) print i, a[i] "
								+ "}")
				.expectLines("3 c", "2 b", "1 a")
				.runAndAssert();
	}

	@Test
	public void sortedInChangesApplyMidExecution() throws Exception {
		AwkTestSupport
				.awkTest("PROCINFO[\"sorted_in\"] is re-read by each for-in loop")
				.script(
						"BEGIN { "
								+ "a[1] = \"x\"; a[2] = \"y\"; a[3] = \"z\"; "
								+ "PROCINFO[\"sorted_in\"] = \"@ind_num_desc\"; "
								+ "s = \"\"; for (i in a) s = s i; print s; "
								+ "PROCINFO[\"sorted_in\"] = \"@ind_num_asc\"; "
								+ "s = \"\"; for (i in a) s = s i; print s "
								+ "}")
				.expectLines("321", "123")
				.runAndAssert();
	}

	@Test
	public void unsortedModeKeepsNaturalTraversalOrder() throws Exception {
		AwkTestSupport
				.awkTest("@unsorted traverses in the array's natural order")
				.script(
						"BEGIN { "
								+ "for (i = 1; i <= 20; i++) a[\"key\" i] = 21 - i; "
								+ "before = \"\"; "
								+ "for (k in a) before = before SUBSEP k; "
								+ "PROCINFO[\"sorted_in\"] = \"@unsorted\"; "
								+ "after = \"\"; "
								+ "for (k in a) after = after SUBSEP k; "
								+ "print (before == after ? \"same\" : \"different\") "
								+ "}")
				.expectLines("same")
				.runAndAssert();
	}

	@Test
	public void typeofReturnsGawkCategories() throws Exception {
		AwkTestSupport
				.awkTest("typeof reports gawk type categories")
				.script(
						"BEGIN { "
								+ "n = 42; s = \"text\"; arr[1] = 1; "
								+ "print typeof(n); "
								+ "print typeof(s); "
								+ "print typeof(arr); "
								+ "print typeof(unset) "
								+ "}")
				.expectLines("number", "string", "array", "untyped")
				.runAndAssert();
	}

	@Test
	public void typeofReportsStrnumForNumericInputFields() throws Exception {
		AwkTestSupport
				.awkTest("typeof reports strnum for numeric-looking input")
				.script("{ print typeof($1), typeof($2) }")
				.stdin("3.14 text\n")
				.expectLines("strnum string")
				.runAndAssert();
	}

	@Test
	public void typeofOnMissingElementCreatesItLikeGawk() throws Exception {
		AwkTestSupport
				.awkTest("typeof on a missing element brings it into existence")
				.script(
						"BEGIN { "
								+ "t = typeof(a[1]); present = (1 in a); "
								+ "print t, present "
								+ "}")
				.expectLines("untyped 1")
				.runAndAssert();
	}

	@Test
	public void isarrayOnMissingElementCreatesItLikeGawk() throws Exception {
		AwkTestSupport
				.awkTest("isarray on a missing element brings it into existence")
				.script(
						"BEGIN { "
								+ "r = isarray(a[1]); present = (1 in a); "
								+ "print r, present "
								+ "}")
				.expectLines("0 1")
				.runAndAssert();
	}

	@Test
	public void isarrayDistinguishesArraysFromScalars() throws Exception {
		AwkTestSupport
				.awkTest("isarray returns 1 only for arrays")
				.script(
						"BEGIN { "
								+ "arr[1] = 1; scalar = \"x\"; "
								+ "print isarray(arr), isarray(scalar) "
								+ "}")
				.expectLines("1 0")
				.runAndAssert();
	}

	@Test
	public void mkboolCreatesBooleanTypedNumbers() throws Exception {
		AwkTestSupport
				.awkTest("mkbool values are numbers flagged as booleans")
				.script(
						"BEGIN { "
								+ "t = mkbool(1); f = mkbool(0); "
								+ "print t, f, typeof(t), typeof(f) "
								+ "}")
				.expectLines("1 0 number|bool number|bool")
				.runAndAssert();
	}

	@Test
	public void mkboolUsesAwkTruthiness() throws Exception {
		// gawk applies ordinary AWK truth rules: any non-empty string
		// constant (even "0") is true; a strnum is judged numerically
		AwkTestSupport
				.awkTest("mkbool follows AWK truthiness, not numeric coercion")
				.script(
						"{ s = mkbool($1); print s } "
								+ "END { print mkbool(\"abc\"), mkbool(\"\"), mkbool(\"0\"), mkbool(0) }")
				.stdin("0\n")
				.expectLines("0", "1 0 1 0")
				.runAndAssert();
	}

	@Test
	public void systimeReturnsCurrentEpochSeconds() throws Exception {
		long before = System.currentTimeMillis() / 1000L;
		AwkTestSupport.TestResult result = AwkTestSupport
				.awkTest("systime reports the current epoch time")
				.script("BEGIN { print systime() }")
				.run();
		long after = System.currentTimeMillis() / 1000L;
		long reported = Long.parseLong(result.output().trim());
		assertTrue(
				"systime() must report the current time",
				reported >= before && reported <= after);
	}

	@Test
	public void mktimeConvertsUtcDatespecs() throws Exception {
		AwkTestSupport
				.awkTest("mktime converts and normalizes UTC date specifications")
				.script(
						"BEGIN { "
								+ "print mktime(\"2017 1 1 0 0 0\", 1); "
								+ "print mktime(\"2016 13 1 0 0 0\", 1); "
								+ "print mktime(\"2017 1 1 0 0 -30\", 1); "
								+ "print mktime(strftime(\"%Y %m %d %H %M %S\", 1483228800, 1), 1) "
								+ "}")
				.expectLines("1483228800", "1483228800", "1483228770", "1483228800")
				.runAndAssert();
	}

	@Test
	public void mktimeRejectsMalformedDatespecs() throws Exception {
		AwkTestSupport
				.awkTest("mktime returns -1 on malformed date specifications")
				.script(
						"BEGIN { "
								+ "print mktime(\"garbage\"); "
								+ "print mktime(\"2017 1 1 0 0\"); "
								+ "print mktime(\"2017 1 1 0 0 0 0 0\"); "
								+ "print mktime(\"2017 1 1 0 0 x\") "
								+ "}")
				.expectLines("-1", "-1", "-1", "-1")
				.runAndAssert();
	}

	@Test
	public void strftimeFormatsUtcTimestamps() throws Exception {
		// 1483228800 is Sun Jan 1 2017 00:00:00 UTC
		AwkTestSupport
				.awkTest("strftime supports the C conversion specifiers")
				.script(
						"BEGIN { "
								+ "t = 1483228800; "
								+ "print strftime(\"%Y-%m-%d %H:%M:%S\", t, 1); "
								+ "print strftime(\"%a %A %b %B %h\", t, 1); "
								+ "print strftime(\"%d|%e|%j|%u|%w|%C|%y\", t, 1); "
								+ "print strftime(\"%V %G %g %U %W\", t, 1); "
								+ "print strftime(\"%z %Z %s %%\", t, 1); "
								+ "print strftime(\"%c\", t, 1); "
								+ "print strftime(\"%D %F %x %X %r %R %T\", t, 1); "
								+ "t2 = mktime(\"2017 6 15 15 30 45\", 1); "
								+ "print strftime(\"%a %u %j %e %I:%M:%S %p\", t2, 1); "
								+ "print strftime(\"a%nb\", t, 1); "
								+ "print strftime(\"%q %E\", t, 1) "
								+ "}")
				.expectLines(
						"2017-01-01 00:00:00",
						"Sun Sunday Jan January Jan",
						"01| 1|001|7|0|20|17",
						"52 2016 16 01 00",
						"+0000 UTC 1483228800 %",
						"Sun Jan  1 00:00:00 2017",
						"01/01/17 2017-01-01 01/01/17 00:00:00 12:00:00 AM 00:00 00:00:00",
						"Thu 4 166 15 03:30:45 PM",
						"a",
						"b",
						"%q %E")
				.runAndAssert();
	}

	@Test
	public void timeFunctionsUseProlepticGregorianDates() throws Exception {
		// gawk's civil dates never switch to the Julian calendar: 1500-01-01
		// is 9 days away from what a default GregorianCalendar would compute
		AwkTestSupport
				.awkTest("mktime and strftime use the proleptic Gregorian calendar")
				.script(
						"BEGIN { "
								+ "t = mktime(\"1500 1 1 0 0 0\", 1); "
								+ "print t; "
								+ "print strftime(\"%F\", t, 1) "
								+ "}")
				.expectLines("-14831769600", "1500-01-01")
				.runAndAssert();
	}

	@Test
	public void strftimeSupportsGnuFlagsAndWidths() throws Exception {
		// the GNU extensions gawk exposes through glibc: '-' no padding,
		// '_' space padding, '0' zero padding, '^' upper case, '#' swapped
		// case, and a minimum field width
		AwkTestSupport
				.awkTest("strftime honors GNU padding and case flags")
				.script(
						"BEGIN { "
								+ "t = 1483228800; "
								+ "print \"[\" strftime(\"%-d\", t, 1) \"][\" strftime(\"%_d\", t, 1) \"][\" strftime(\"%5d\", t, 1) \"]\"; "
								+ "print \"[\" strftime(\"%^a\", t, 1) \"][\" strftime(\"%#p\", t, 1) \"][\" strftime(\"%_j\", t, 1) \"]\"; "
								+ "print \"[\" strftime(\"%08Y\", t, 1) \"][\" strftime(\"%-e\", t, 1) \"][\" strftime(\"%3a\", t, 1) \"]\" "
								+ "}")
				.expectLines("[1][ 1][00001]", "[SUN][am][  1]", "[00002017][1][Sun]")
				.runAndAssert();
	}

	@Test
	public void mktimeResolvesDstOverlapLikeGlibc() throws Exception {
		// 2021-11-07 01:30 happens twice in America/New_York; with no DST
		// hint glibc's mktime picks the first (daylight) occurrence
		java.util.TimeZone original = java.util.TimeZone.getDefault();
		try {
			java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
			AwkTestSupport
					.awkTest("ambiguous fall-back times pick the daylight occurrence")
					.script(
							"BEGIN { "
									+ "print mktime(\"2021 11 7 1 30 0\"); "
									+ "print mktime(\"2021 11 7 1 30 0 1\"); "
									+ "print mktime(\"2021 11 7 1 30 0 0\") "
									+ "}")
					.expectLines("1636263000", "1636263000", "1636266600")
					.runAndAssert();
		} finally {
			java.util.TimeZone.setDefault(original);
		}
	}

	@Test
	public void strftimeDefaultFormatComesFromProcinfo() throws Exception {
		AwkTestSupport
				.awkTest("strftime() without arguments uses PROCINFO[\"strftime\"]")
				.script("BEGIN { PROCINFO[\"strftime\"] = \"fixed-format\"; print strftime() }")
				.expectLines("fixed-format")
				.runAndAssert();
	}

	@Test
	public void strtonumConvertsNonDecimalStrings() throws Exception {
		AwkTestSupport
				.awkTest("strtonum understands hexadecimal and octal notation")
				.script(
						"BEGIN { "
								+ "print strtonum(\"0x13\"), strtonum(\"013\"), strtonum(\"13\"), strtonum(13); "
								+ "print strtonum(\"0x\"), strtonum(\"019\"), strtonum(\"0.5\"), strtonum(\"abc\"); "
								+ "print strtonum(\"0xff\"), strtonum(\"0X1A\"), strtonum(\"017 \"); "
								+ "print strtonum(\"011x\"), strtonum(\"077foo\"), strtonum(\"08x\"); "
								+ "print strtonum(\"077foo.5\"), strtonum(\"011E2\") "
								+ "}")
				.expectLines("19 11 13 13", "0 19 0.5 0", "255 26 15", "9 63 8", "63 1100")
				.runAndAssert();
	}

	@Test
	public void strtonumResolvesNumericInputFieldsAsDecimal() throws Exception {
		// gawk resolves numeric-looking strnums to plain numbers before
		// looking at the base, so an input field "011" is decimal, while a
		// script string constant "011" is octal (see the mixed case above)
		AwkTestSupport
				.awkTest("strtonum treats numeric strnum fields as decimal")
				.script("{ print strtonum($1), strtonum($2) }")
				.stdin("011 0x11\n")
				.expectLines("11 17")
				.runAndAssert();
	}

	@Test
	public void patsplitSplitsByContent() throws Exception {
		// fixture from gawk's own patsplit test: empty matches become empty
		// fields, and seps[0]..seps[n] frame every field. The quoted-field
		// alternative comes first because Java regexps pick the first matching
		// alternative, not the POSIX longest one.
		AwkTestSupport
				.awkTest("patsplit implements gawk's FPAT splitting rules")
				.script(
						"function dump(n, f, s,    i, line) { "
								+ "line = n \":\"; "
								+ "for (i = 1; i <= n; i++) line = line \"<\" f[i] \">\"; "
								+ "line = line \";\"; "
								+ "for (i = 0; i in s; i++) line = line \"<\" s[i] \">\"; "
								+ "print line "
								+ "} "
								+ "BEGIN { "
								+ "csv = \"(\\\"[^\\\"]+\\\")|([^,]*)\"; "
								+ "dump(patsplit(\"Robbins,,Arnold,\", f1, csv, s1), f1, s1); "
								+ "dump(patsplit(\"Smith,,\\\"1234 A Pretty Place, NE\\\",Sometown,NY,12345-6789,USA\", f2, csv, s2), f2, s2); "
								+ "dump(patsplit(\"bbbaaacccdddaaaaaqqqqa\", f3, \"aa+\", s3), f3, s3); "
								+ "dump(patsplit(\"qqq\", f4, \"aa+\", s4), f4, s4); "
								+ "dump(patsplit(\"\", f5, \"aa+\", s5), f5, s5); "
								+ "dump(patsplit(\"abc\", f6, \"$\", s6), f6, s6); "
								+ "dump(patsplit(\"abc\", f7, \"a+|$\", s7), f7, s7) "
								+ "}")
				.expectLines(
						"4:<Robbins><><Arnold><>;<><,><,><,><>",
						"7:<Smith><><\"1234 A Pretty Place, NE\"><Sometown><NY><12345-6789><USA>;<><,><,><,><,><,><,><>",
						"2:<aaa><aaaaa>;<bbb><cccddd><qqqqa>",
						"0:;<qqq>",
						"0:;",
						// anchored patterns match away from the scan position:
						// the single end-of-string match yields one empty field
						"1:<>;<abc><>",
						"2:<a><>;<><bc><>")
				.runAndAssert();
	}

	@Test
	public void patsplitDefaultsToFpat() throws Exception {
		AwkTestSupport
				.awkTest("patsplit falls back to FPAT, then to non-whitespace runs")
				.script(
						"BEGIN { "
								+ "n = patsplit(\"  a  b \", f); "
								+ "print n, f[1], f[2]; "
								+ "FPAT = \"[0-9]+\"; "
								+ "n = patsplit(\"a1b22c\", g); "
								+ "print n, g[1], g[2] "
								+ "}")
				.expectLines("2 a b", "2 1 22")
				.runAndAssert();
	}

	@Test
	public void patsplitRejectsEmptyFieldPatterns() throws Exception {
		// gawk: an explicitly empty field pattern is fatal; only an unset
		// FPAT stands in for gawk's built-in default
		AwkTestSupport
				.awkTest("an explicitly empty FPAT is a runtime error")
				.script("BEGIN { FPAT = \"\"; patsplit(\"a b\", f) }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
		AwkTestSupport
				.awkTest("an explicitly empty field pattern argument is a runtime error")
				.script("BEGIN { patsplit(\"a b\", f, \"\") }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void patsplitRejectsAliasedDestinationArrays() throws Exception {
		// gawk: fatal "cannot use the same array for second and fourth args"
		AwkTestSupport
				.awkTest("patsplit rejects the same array for fields and separators")
				.script("BEGIN { patsplit(\"abc\", a, \"b\", a) }")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void patsplitHonorsIgnoreCase() throws Exception {
		AwkTestSupport
				.awkTest("IGNORECASE applies to patsplit field patterns")
				.script(
						"BEGIN { "
								+ "print patsplit(\"xAx\", f, /a/); "
								+ "IGNORECASE = 1; "
								+ "print patsplit(\"xAx\", f, /a/), f[1] "
								+ "}")
				.expectLines("0", "1 A")
				.runAndAssert();
	}

	@Test
	public void gettextFunctionsReturnUntranslatedText() throws Exception {
		AwkTestSupport
				.awkTest("dcgettext and dcngettext behave like gawk without catalogs")
				.script(
						"BEGIN { "
								+ "print dcgettext(\"hello\"); "
								+ "print dcgettext(\"hello\", \"dom\", \"LC_MESSAGES\"); "
								+ "print dcngettext(\"one\", \"many\", 1), dcngettext(\"one\", \"many\", 2), dcngettext(\"one\", \"many\", 0) "
								+ "}")
				.expectLines("hello", "hello", "one many many")
				.runAndAssert();
	}

	@Test
	public void bindtextdomainTracksDirectoryBindings() throws Exception {
		AwkTestSupport
				.awkTest("bindtextdomain records and reports per-domain directories")
				.script(
						"BEGIN { "
								+ "print bindtextdomain(\"/opt/locale\", \"mydom\"); "
								+ "print bindtextdomain(\"\", \"mydom\"); "
								+ "print bindtextdomain(\"\"); "
								+ "TEXTDOMAIN = \"mydom\"; "
								+ "print bindtextdomain(\"\"); "
								+ "print \"<\" bindtextdomain(\"/elsewhere\", \"\") \">\"; "
								+ "print bindtextdomain(\"\", \"mydom\") "
								+ "}")
				.expectLines(
						"/opt/locale",
						"/opt/locale",
						"/usr/share/locale",
						"/opt/locale",
						// an explicitly empty domain is rejected: the result is
						// empty and no binding changes, as with C bindtextdomain()
						"<>",
						"/opt/locale")
				.runAndAssert();
	}

	@Test
	public void functabListsBuiltinAndExtensionFunctions() throws Exception {
		AwkTestSupport
				.awkTest("FUNCTAB lists standard built-ins and gawk extension functions")
				.script(
						"BEGIN { "
								+ "n = asorti(FUNCTAB, sorted); "
								+ "line = \"\"; "
								+ "for (i = 1; i <= n; i++) line = line (i > 1 ? \" \" : \"\") sorted[i]; "
								+ "print line "
								+ "}")
				.expectLines(
						"asort asorti atan2 bindtextdomain close cos dcgettext dcngettext exp gensub"
								+ " gsub index int isarray length log match mkbool mktime patsplit rand sin"
								+ " split sprintf sqrt srand strftime strtonum sub substr system systime"
								+ " tolower toupper typeof")
				.runAndAssert();
	}

	@Test
	public void asortiWritesIndicesAsStrings() throws Exception {
		// gawk: the destination values of asorti() are strings even when the
		// source index looks numeric
		AwkTestSupport
				.awkTest("asorti destination values are string-typed")
				.script(
						"BEGIN { "
								+ "a[1] = \"x\"; a[\"b\"] = \"y\"; "
								+ "n = asorti(a, d); "
								+ "for (i = 1; i <= n; i++) print d[i], typeof(d[i]) "
								+ "}")
				.expectLines("1 string", "b string")
				.runAndAssert();
	}
}
