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

public class DynamicFieldTest {

	@Test
	public void testEmptyStringFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest empty string field index")
				.script("BEGIN{idx=\"\"}{print $(idx)}")
				.stdin("a b c")
				.expectLines("a b c")
				.runAndAssert();
	}

	@Test
	public void testNonNumericFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest non numeric field index")
				.script("BEGIN{idx=\"foo\"}{print $(idx)}")
				.stdin("a b")
				.expectLines("a b")
				.runAndAssert();
	}

	@Test
	public void testUninitializedVariableFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest uninitialized variable field index")
				.script("{print $(idx)}")
				.stdin("a b")
				.expectLines("a b")
				.runAndAssert();
	}

	@Test
	public void testNumericStringFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest numeric string field index")
				.script("BEGIN{idx=\"2\"}{print $(idx)}")
				.stdin("a b c")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void testFloatStringFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest float string field index")
				.script("BEGIN{idx=\"2.7\"}{print $(idx)}")
				.stdin("a b c")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void testFloatVariableFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest float variable field index")
				.script("BEGIN{idx=2.3}{print $(idx)}")
				.stdin("a b c")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void testExponentStringFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest exponent string field index")
				.script("BEGIN{idx=\"3e0\"}{print $(idx)}")
				.stdin("1 2 3 4")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void testNegativeFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("DynamicFieldTest negative field index")
				.script("BEGIN{idx=-1}{print $(idx)}")
				.stdin("a b")
				.expectThrow(AwkRuntimeException.class)
				.runAndAssert();
	}
}
