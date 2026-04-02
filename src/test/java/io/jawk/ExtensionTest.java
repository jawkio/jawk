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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import org.junit.Test;
import io.jawk.AwkTestSupport.TestResult;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.IllegalAwkArgumentException;

/**
 * Tests the integration of a custom {@link JawkExtension} implementation with
 * the interpreter.
 */
public class ExtensionTest {

	/**
	 * Verifies that an extension can be registered and invoked from an AWK
	 * script.
	 */
	@Test
	public void testExtension() throws Exception {
		AwkTestSupport
				.awkTest("extension invocation")
				.script("BEGIN { ab[1] = \"a\"; ab[2] = \"b\"; printf myExtensionFunction(3, ab) }")
				.withExtensions(new TestExtension())
				.expect("ababab")
				.runAndAssert();
	}

	@Test
	public void testAnnotatedExtensionArgumentValidation() throws Exception {
		AwkTestSupport
				.awkTest("annotated extension argument validation")
				.script("BEGIN { myExtensionFunction() }")
				.withExtensions(new TestExtension())
				.expectThrow(IllegalAwkArgumentException.class)
				.runAndAssert();
	}

	@Test
	public void testVarArgAssocArrayInvocation() throws Exception {
		AwkTestSupport
				.awkTest("var arg assoc array invocation")
				.script("BEGIN { first[1] = 1; second[1] = 2; print varArgAssoc(first, second) }")
				.withExtensions(new TestExtension())
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void testVarArgAssocArraySemanticValidation() throws Exception {
		TestResult result = AwkTestSupport
				.awkTest("var arg assoc array semantic validation")
				.script("BEGIN { array[1] = 1; scalar = 1; print varArgAssoc(array, scalar) }")
				.withExtensions(new TestExtension())
				.expectThrow(RuntimeException.class)
				.run();
		result.assertExpected();
		assertTrue(result.thrownException().getMessage().contains("associative array"));
	}

	@Test
	public void testVarArgAssocArrayRuntimeValidation() throws Exception {
		TestResult result = AwkTestSupport
				.awkTest("var arg assoc array runtime validation")
				.script("BEGIN { array[1] = 1; print varArgAssoc(array, 1) }")
				.withExtensions(new TestExtension())
				.expectThrow(RuntimeException.class)
				.run();
		result.assertExpected();
		Throwable thrown = result.thrownException();
		if (thrown instanceof IllegalAwkArgumentException) {
			return;
		}
		if (thrown instanceof AwkRuntimeException) {
			assertTrue(thrown.getCause() instanceof IllegalAwkArgumentException);
			return;
		}
		fail("Expected IllegalAwkArgumentException but got " + thrown.getClass().getName());
	}

	@Test
	public void testAnnotatedAssocArrayMustAcceptMap() {
		class InvalidExtension extends AbstractExtension implements JawkExtension {

			@Override
			public String getExtensionName() {
				return "invalid";
			}

			@JawkFunction("invalid")
			public int invalid(@JawkAssocArray Number value) {
				return value.intValue();
			}
		}
		try {
			new InvalidExtension().getExtensionFunctions();
			fail("Expected IllegalStateException");
		} catch (IllegalStateException ex) {
			assertTrue(ex.getMessage().contains("java.util.Map"));
		}
	}

	@Test
	public void testAnnotatedAssocArrayMustNotUseConcreteMapImplementation() {
		class InvalidExtension extends AbstractExtension implements JawkExtension {

			@Override
			public String getExtensionName() {
				return "invalid-map";
			}

			@JawkFunction("invalid")
			public int invalid(@JawkAssocArray HashMap<Object, Object> value) {
				return value.size();
			}
		}
		try {
			new InvalidExtension().getExtensionFunctions();
			fail("Expected IllegalStateException");
		} catch (IllegalStateException ex) {
			assertTrue(ex.getMessage().contains("AssocArray"));
		}
	}
}
