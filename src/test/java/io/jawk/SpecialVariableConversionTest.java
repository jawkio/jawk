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

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;

/**
 * Tests that a number assigned to a special variable stored as text
 * ({@code FS}, {@code RS}, {@code OFS}, {@code ORS}, {@code SUBSEP},
 * {@code CONVFMT} and {@code OFMT}) is converted with {@code CONVFMT}, an
 * integral value as an integer (#672). Expected outputs are gawk's.
 */
public class SpecialVariableConversionTest {

	@Test
	public void ofsAndSubsepUseConvfmt() throws Exception {
		AwkTestSupport.awkTest("a number assigned to OFS or SUBSEP is converted with CONVFMT (#672)")
				.script(
						"BEGIN { OFS = 0.1 + 0.2; print \"a\", \"b\"; OFS = 3; OFS++; print \"a\", \"b\";"
								+ " SUBSEP = 2 / 2.0; SUBSEP += 0; a[1, 2]; for (k in a) print k }")
				.expectLines("a0.3b", "a4b", "112")
				.runAndAssert();
	}

	@Test
	public void fsIncrementedFromTextIsAnInteger() throws Exception {
		AwkTestSupport.awkTest("FS += 1 and FS++ on a text leave an integer in FS (#672)")
				.script("BEGIN { FS = \"3x\"; FS += 1; print FS; FS = \"3x\"; FS++; print FS } { print $2 }")
				.stdin("a4b\n")
				.expectLines("4", "4", "b")
				.runAndAssert();
	}

	@Test
	public void rsAndOrsUseConvfmt() throws Exception {
		AwkTestSupport.awkTest("a number assigned to RS or ORS is converted with CONVFMT (#672)")
				.script("BEGIN { RS = 6 / 2; ORS = 4 / 2 } { print } END { ORS = \"\\n\"; print \"\" }")
				.stdin("a3b")
				.expectLines("a2b2")
				.runAndAssert();
	}

	@Test
	public void convfmtAndOfmtUseConvfmt() throws Exception {
		AwkTestSupport.awkTest("a number assigned to CONVFMT or OFMT is converted with CONVFMT (#672)")
				.script("BEGIN { CONVFMT = 4 / 2; OFMT = 6 / 2; print CONVFMT, OFMT }")
				.expectLines("2 3")
				.runAndAssert();
	}

	@Test
	public void assignedNumberKeepsItsNumericValue() throws Exception {
		AwkTestSupport.awkTest("FS and OFS keep the number assigned, and their text from the CONVFMT at that time")
				.script(
						"BEGIN { CONVFMT = \"%.0f\"; FS = 3.6; OFS = 0.1 + 0.2;"
								+ " printf \"%.17g %.17g\\n\", FS + 0, OFS + 0; print OFS; CONVFMT = \"%.6g\" }"
								+ " { print $2; print $1, $2 }")
				.stdin("a4b\n")
				.expectLines("3.6000000000000001 0.30000000000000004", "0.3", "b", "a0b")
				.runAndAssert();
	}

	@Test
	public void preassignedConvfmtConvertsPreassignedSeparators() throws Exception {
		AwkTestSupport.awkTest("a preassigned CONVFMT converts the numbers preassigned to the other variables")
				.script("{ print }")
				.preassign("RS", Double.valueOf(1.0 / 3))
				.preassign("CONVFMT", "%.2f")
				.stdin("a0.33b")
				.expectLines("a", "b")
				.runAndAssert();
	}

	@Test
	public void preassignedBigNumbersKeepTheirText() throws Exception {
		AwkTestSupport.awkTest("a BigDecimal or BigInteger preassigned to OFS or SUBSEP keeps its text")
				.script("BEGIN { print \"a\", \"b\"; x[1, 2]; for (k in x) print k }")
				.preassign("OFS", new BigDecimal("12.50"))
				.preassign("SUBSEP", new BigInteger("123456789012345678901234567890"))
				.expectLines("a12.50b", "11234567890123456789012345678902")
				.runAndAssert();
	}
}
