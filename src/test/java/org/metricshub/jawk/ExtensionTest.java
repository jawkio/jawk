package org.metricshub.jawk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import org.junit.Test;
import org.metricshub.jawk.AwkTestSupport.TestResult;
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.ext.annotations.JawkAssocArray;
import org.metricshub.jawk.ext.annotations.JawkFunction;
import org.metricshub.jawk.jrt.AwkRuntimeException;
import org.metricshub.jawk.jrt.IllegalAwkArgumentException;

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
