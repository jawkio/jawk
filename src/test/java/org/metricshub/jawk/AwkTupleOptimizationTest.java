package org.metricshub.jawk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.intermediate.Opcode;
import org.metricshub.jawk.intermediate.PositionTracker;

public class AwkTupleOptimizationTest {

	@Test
	public void removesTrailingPlaceholderNop() throws Exception {
		String script = "BEGIN { print \"hello\" }\n";
		AwkTestSupport
				.awkTest("placeholder nop removal")
				.script(script)
				.expect("hello\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Tuple list should not be empty", opcodes.isEmpty());
		assertNotEquals("Optimizer should remove trailing NOP", Opcode.NOP, opcodes.get(opcodes.size() - 1));
	}

	@Test
	public void foldsLiteralBinaryArithmetic() throws Exception {
		String script = "BEGIN { print 1 + 2 }\n";
		AwkTestSupport
				.awkTest("folds literal binary arithmetic")
				.script(script)
				.expect("3\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Binary literal should eliminate ADD tuple", opcodes.contains(Opcode.ADD));
		assertTrue("Expected folded literal push of 3", hasLiteralPush(tuples, Long.valueOf(3)));
	}

	@Test
	public void foldsLiteralUnaryArithmetic() throws Exception {
		String script = "BEGIN { print -5 }\n";
		AwkTestSupport
				.awkTest("folds literal unary arithmetic")
				.script(script)
				.expect("-5\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Unary literal should eliminate NEGATE tuple", opcodes.contains(Opcode.NEGATE));
		assertFalse("Unary literal should eliminate SUBTRACT tuple", opcodes.contains(Opcode.SUBTRACT));
		assertTrue("Expected folded literal push of -5", hasLiteralPush(tuples, Long.valueOf(-5)));
	}

	@Test
	public void foldsLiteralComparison() throws Exception {
		String script = "BEGIN { print (4 < 7) }\n";
		AwkTestSupport
				.awkTest("folds literal comparison")
				.script(script)
				.expect("1\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Literal comparison should eliminate CMP_LT tuple", opcodes.contains(Opcode.CMP_LT));
		assertTrue("Expected folded literal push of 1", hasLiteralPush(tuples, Long.valueOf(1)));
	}

	@Test
	public void foldsNestedLiteralArithmetic() throws Exception {
		String script = "BEGIN { print 1 + 2 + 3 }\n";
		AwkTestSupport
				.awkTest("folds nested literal arithmetic")
				.script(script)
				.expect("6\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Nested literals should eliminate ADD tuples", opcodes.contains(Opcode.ADD));
		assertTrue("Expected folded literal push of 6", hasLiteralPush(tuples, Long.valueOf(6)));
	}

	@Test
	public void foldsLiteralStringConcatenation() throws Exception {
		String script = "BEGIN { print \"foo\" \"bar\" }\n";
		AwkTestSupport
				.awkTest("folds literal string concatenation")
				.script(script)
				.expect("foobar\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertFalse("Literal concatenation should eliminate CONCAT tuple", opcodes.contains(Opcode.CONCAT));
		assertTrue("Expected folded literal push of foobar", hasLiteralPush(tuples, "foobar"));
	}

	@Test
	public void doesNotFoldNumericConcatenation() throws Exception {
		String script = "BEGIN { CONVFMT=\"%.2f\"; print 1 \"x\" }\n";
		AwkTestSupport
				.awkTest("skips numeric literal concatenation folding")
				.script(script)
				.expect("1x\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertTrue("Numeric literal concatenation should preserve CONCAT tuple", opcodes.contains(Opcode.CONCAT));
		assertFalse("Optimizer should not fold numeric/string concatenation", hasLiteralPush(tuples, "1x"));
	}

	@Test
	public void removesInstructionsAfterExit() throws Exception {
		String script = "" + "BEGIN { print \"before\"; exit; print \"after\" }\n" + "END { print \"done\" }\n";
		AwkTestSupport
				.awkTest("exit removes trailing code")
				.script(script)
				.expect("before\ndone\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		String dump = dumpTuples(tuples);
		assertFalse("Optimizer should remove unreachable print after exit", dump.contains("\"after\""));
	}

	@Test
	public void removesUnconditionalRuleWrapperAndLeadingGoto() throws Exception {
		String script = "{ print $0 }\n";
		AwkTestSupport
				.awkTest("unconditional rule wrapper removed")
				.script(script)
				.stdin("value")
				.expectLines("value")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);

		assertFalse("Tuple list should not be empty", opcodes.isEmpty());
		assertNotEquals("Leading GOTO should be removed when main starts immediately", Opcode.GOTO, opcodes.get(0));
		assertFalse(
				"Unconditional rule should not compile a synthetic conditional branch",
				opcodes.contains(Opcode.IFFALSE));
		assertFalse("No placeholder NOP should remain in the optimized script", opcodes.contains(Opcode.NOP));
	}

	@Test
	public void retainsRecursiveFunctionBodies() throws Exception {
		String script = "function fact(n){return n? n*fact(n-1):1}\n" + "BEGIN{print fact(5)}\n";
		AwkTestSupport
				.awkTest("recursive function survives optimization")
				.script(script)
				.expect("120\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		Set<Integer> callTargets = new HashSet<>();

		PositionTracker tracker = tuples.top();
		while (!tracker.isEOF()) {
			if (tracker.opcode() == Opcode.CALL_FUNCTION) {
				Address address = tracker.addressArg();
				assertTrue("Call target should be assigned", address.index() >= 0);
				callTargets.add(Integer.valueOf(address.index()));
			}
			tracker.next();
		}

		assertFalse("Expected at least one function call", callTargets.isEmpty());
		for (Integer target : callTargets) {
			assertTrue("Call target index should refer to existing tuple", target.intValue() < opcodes.size());
			Opcode opcode = opcodes.get(target.intValue());
			assertNotEquals("Call target should not be optimized away", Opcode.NOP, opcode);
		}
	}

	@Test
	public void skipsOptimizationWhenDisabled() throws Exception {
		String script = "BEGIN { print \"before\"; exit; print \"after\" }\n";
		AwkTuples tuples = new Awk().compile(script, true);
		String dump = dumpTuples(tuples);

		assertTrue("Unreachable code should remain when optimization disabled", dump.contains("\"after\""));
	}

	@Test
	public void optimizeSkipsPostProcessingWhenPointersPersist() throws Exception {
		String script = "BEGIN { print \"hello\" }\n";
		AwkTuples tuples = new Awk().compile(script, true);

		Field postProcessedField = AwkTuples.class.getDeclaredField("postProcessed");
		postProcessedField.setAccessible(true);
		postProcessedField.setBoolean(tuples, false);

		tuples.optimize();

		assertTrue("optimize() should mark tuples as post-processed", postProcessedField.getBoolean(tuples));

		Field optimizedField = AwkTuples.class.getDeclaredField("optimized");
		optimizedField.setAccessible(true);
		assertTrue(
				"optimize() should still run on tuples with existing next pointers",
				optimizedField.getBoolean(tuples));
	}

	@Test
	public void compilesLiteralInputFieldWithoutPush() throws Exception {
		String script = "{ print $2 }\n";
		assertLiteralFieldUsesConstOpcode(script, "alpha beta", new String[] { "beta" }, 2);
	}

	@Test
	public void compilesDollarZeroWithConstOpcode() throws Exception {
		String script = "{ print $0 }\n";
		assertLiteralFieldUsesConstOpcode(script, "alpha beta", new String[] { "alpha beta" }, 0);
	}

	@Test
	public void compilesExpressionBasedFieldWithConstOpcode() throws Exception {
		String script = "{ print $(1 + 1) }\n";
		assertLiteralFieldUsesConstOpcode(script, "alpha beta", new String[] { "beta" }, 2);
	}

	@Test
	public void retargetsBranchesAwayFromPlaceholderTuples() throws Exception {
		String script = "$1 { print $2 }\n";
		AwkTestSupport
				.awkTest("conditional rule bypass retargeted")
				.script(script)
				.stdin("alpha beta")
				.expectLines("beta")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		Set<Integer> branchTargets = new HashSet<>();

		PositionTracker tracker = tuples.top();
		while (!tracker.isEOF()) {
			if (usesAddress(tracker.opcode())) {
				branchTargets.add(Integer.valueOf(tracker.addressArg().index()));
			}
			tracker.next();
		}

		for (Integer target : branchTargets) {
			Opcode targetOpcode = opcodeAt(tuples, target.intValue());
			assertNotEquals("Branch target should not remain on a NOP placeholder", Opcode.NOP, targetOpcode);
			assertNotEquals("Branch target should bypass pure GOTO trampolines", Opcode.GOTO, targetOpcode);
		}
	}

	@Test
	public void removesGotoImmediatelyBeforeFunctionReturn() throws Exception {
		String script = "function inc(x){ return x + 1 }\nBEGIN { print inc(41) }\n";
		AwkTestSupport
				.awkTest("function return goto removed")
				.script(script)
				.expect("42\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		for (int i = 0; i < opcodes.size() - 1; i++) {
			assertFalse(
					"GOTO should not remain immediately before RETURN_FROM_FUNCTION",
					opcodes.get(i) == Opcode.GOTO && opcodes.get(i + 1) == Opcode.RETURN_FROM_FUNCTION);
		}
	}

	@Test
	public void rejectsNegativeLiteralFieldIndex() throws Exception {
		AwkTestSupport
				.awkTest("negative field literal is invalid")
				.script("BEGIN { print $-1 }\n")
				.expectThrow(RuntimeException.class)
				.runAndAssert();
	}

	@Test
	public void emitsArgcOffsetButNotArgvOffsetWhenUnreferenced() throws Exception {
		String script = "{ print $0 }\n";
		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertTrue("ARGC offset should always be emitted", opcodes.contains(Opcode.ARGC_OFFSET));
		assertFalse("ARGV offset should not be emitted when ARGV is unreferenced", opcodes.contains(Opcode.ARGV_OFFSET));
	}

	@Test
	public void emitsArgcOffsetWhenArgcReferencedOnly() throws Exception {
		String script = "BEGIN { print ARGC }\n";
		AwkTestSupport
				.awkTest("argc offset emitted when argc referenced")
				.script(script)
				.expect("1\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertTrue("ARGC offset should be emitted when ARGC is referenced", opcodes.contains(Opcode.ARGC_OFFSET));
		assertFalse("ARGV offset should not be emitted when ARGV is unreferenced", opcodes.contains(Opcode.ARGV_OFFSET));
	}

	@Test
	public void emitsArgcAndArgvOffsetsWhenArgvReferenced() throws Exception {
		String script = "BEGIN { print ARGV[0] }\n";
		AwkTestSupport
				.awkTest("argc and argv offsets emitted when argv referenced")
				.script(script)
				.expect("jawk\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertTrue("ARGC offset should be emitted when ARGV is referenced", opcodes.contains(Opcode.ARGC_OFFSET));
		assertTrue("ARGV offset should be emitted when ARGV is referenced", opcodes.contains(Opcode.ARGV_OFFSET));
	}

	@Test
	public void consumesFileOperandsWithoutArgvOffset() throws Exception {
		String script = "{ print FILENAME \":\" $0 }\n";
		AwkTestSupport
				.awkTest("file operands consumed without argv offset")
				.file("file1", "a\n")
				.file("file2", "b\n")
				.script(script)
				.operand("{{file1}}", "{{file2}}")
				.expectLines("{{file1}}:a", "{{file2}}:b")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		List<Opcode> opcodes = collectOpcodes(tuples);
		assertTrue("ARGC offset should always be emitted", opcodes.contains(Opcode.ARGC_OFFSET));
		assertFalse("ARGV offset should not be emitted when ARGV is unreferenced", opcodes.contains(Opcode.ARGV_OFFSET));
	}

	@Test
	public void keepsArgcSynchronizedForPreIncrement() throws Exception {
		String script = "BEGIN { ++ARGC; print ARGC }\n";
		AwkTestSupport
				.awkTest("argc preincrement stays synchronized")
				.script(script)
				.expect("2\n")
				.runAndAssert();
	}

	private static List<Opcode> collectOpcodes(AwkTuples tuples) {
		List<Opcode> opcodes = new ArrayList<>();
		PositionTracker tracker = tuples.top();
		while (!tracker.isEOF()) {
			opcodes.add(tracker.opcode());
			tracker.next();
		}
		return opcodes;
	}

	private static String dumpTuples(AwkTuples tuples) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
			tuples.dump(ps);
		}
		return out.toString(StandardCharsets.UTF_8.name());
	}

	private static void assertLiteralFieldUsesConstOpcode(
			String script,
			String input,
			String[] expectedLines,
			int fieldIndex)
			throws Exception {
		AwkTestSupport
				.awkTest("literal field optimization " + fieldIndex)
				.script(script)
				.stdin(input)
				.expectLines(expectedLines)
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		String dump = dumpTuples(tuples);
		assertTrue(
				"Expected GET_INPUT_FIELD_CONST in tuple dump",
				dump.contains("GET_INPUT_FIELD_CONST, " + fieldIndex));
		assertFalse(
				"Literal field index should not be pushed separately",
				dump.contains("PUSH_LONG, " + fieldIndex));
	}

	private static boolean hasLiteralPush(AwkTuples tuples, Object expected) {
		PositionTracker tracker = tuples.top();
		while (!tracker.isEOF()) {
			Opcode opcode = tracker.opcode();
			if (opcode == Opcode.PUSH_LONG
					|| opcode == Opcode.PUSH_DOUBLE
					|| opcode == Opcode.PUSH_STRING) {
				Object value = tracker.arg(0);
				if (expected instanceof Number && value instanceof Number) {
					double actual = ((Number) value).doubleValue();
					if (Double.compare(actual, ((Number) expected).doubleValue()) == 0) {
						return true;
					}
				} else if (expected instanceof String && value instanceof String) {
					if (value.equals(expected)) {
						return true;
					}
				}
			}
			tracker.next();
		}
		return false;
	}

	private static boolean usesAddress(Opcode opcode) {
		switch (opcode) {
		case IFFALSE:
		case IFTRUE:
		case GOTO:
		case IS_EMPTY_KEYLIST:
		case CONSUME_INPUT:
		case CALL_FUNCTION:
		case SET_EXIT_ADDRESS:
			return true;
		default:
			return false;
		}
	}

	private static Opcode opcodeAt(AwkTuples tuples, int index) {
		PositionTracker tracker = tuples.top();
		while (!tracker.isEOF()) {
			if (tracker.current() == index) {
				return tracker.opcode();
			}
			tracker.next();
		}
		throw new AssertionError("No tuple at index " + index);
	}
}
