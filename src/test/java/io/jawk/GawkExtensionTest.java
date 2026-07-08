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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.jawk.ext.GawkExtension;

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
}
