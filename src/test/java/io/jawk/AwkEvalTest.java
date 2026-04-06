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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import io.jawk.backend.AVM;
import io.jawk.intermediate.AwkTuples;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.JRT;
import io.jawk.util.AwkSettings;

/**
 * Tests for {@link Awk#eval} execution paths.
 */
public class AwkEvalTest {

	@Test
	public void testReusableEvalResetsStructuredInputStateBetweenRuns() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("NR \":\" NF \":\" $1");

		assertEquals(
				"1:2:alpha",
				awk.eval(expression, new TableInputSource(Collections.singletonList(Arrays.asList("alpha", "beta")))));
		assertEquals(
				"1:3:x",
				awk.eval(expression, new TableInputSource(Collections.singletonList(Arrays.asList("x", "y", "z")))));
	}

	@Test
	public void testReusableEvalResetsStringInputStateBetweenRuns() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("NF \":\" $2");

		assertEquals("3:b", awk.eval(expression, "a b c"));
		assertEquals("2:right", awk.eval(expression, "left right"));
	}

	@Test
	public void testCompileExpressionOmitsSetNumGlobalsForFieldOnlyExpression() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("NF \":\" $2");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertFalse(dump.contains("SET_NUM_GLOBALS"));
		assertEquals("3:b", awk.eval(expression, "a b c"));
	}

	@Test
	public void testCompileExpressionSimpleFieldExpressionFoldsToConstFieldAccess() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("$2");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertTrue(dump.contains("GET_INPUT_FIELD_CONST, 2"));
		assertFalse(dump.contains("PUSH_LONG, 2"));
		assertEquals("beta", awk.eval(expression, "alpha beta"));
	}

	@Test
	public void testCompileExpressionNegativeFieldBranchStillFailsOnlyAtRuntime() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("$1 == \"x\" ? $2 : $-1");
		String dump = dumpTuples(expression);

		assertTrue(dump.contains("GET_INPUT_FIELD_CONST, -1"));
		assertEquals("b", awk.eval(expression, "x b c"));

		AwkRuntimeException thrown = assertThrows(AwkRuntimeException.class, () -> awk.eval(expression, "y b c"));
		assertEquals("Field $(-1) is incorrect.", thrown.getMessage());
	}

	@Test
	public void testCompileExpressionKeepsSetNumGlobalsWhenGlobalMetadataIsNeeded() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("match($0, /a/)");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertTrue(dump.contains("SET_NUM_GLOBALS"));
		assertEquals(1, awk.eval(expression, "a"));
	}

	@Test
	public void testCompileExpressionStatefulGlobalExpressionStartsWithoutGoto() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("a++");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertTrue(dump.contains("SET_NUM_GLOBALS"));
		assertEquals(0.0, JRT.toDouble(awk.eval(expression, "alpha")), 0.0);
	}

	@Test
	public void testCompileExpressionTernaryFoldsAllLiteralFieldAccessesIncludingLabeledEntries() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("$1 == \"x\" ? $2 : $3");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertTrue(dump.contains("GET_INPUT_FIELD_CONST, 1"));
		assertTrue(dump.contains("GET_INPUT_FIELD_CONST, 2"));
		assertTrue(dump.contains("GET_INPUT_FIELD_CONST, 3"));
		assertFalse(dump.contains("PUSH_LONG, 1"));
		assertFalse(dump.contains("PUSH_LONG, 2"));
		assertFalse(dump.contains("PUSH_LONG, 3"));
		assertEquals("b", awk.eval(expression, "x b c"));
		assertEquals("c", awk.eval(expression, "y b c"));
	}

	@Test
	public void testCompileExpressionTernaryFoldsLabeledBinaryLiteralBranch() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("$1 == \"x\" ? (1 + 2) : (4 + 5)");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertTrue(dump.contains("PUSH_LONG, 3"));
		assertTrue(dump.contains("PUSH_LONG, 9"));
		assertFalse(dump.contains("PUSH_LONG, 1"));
		assertFalse(dump.contains("PUSH_LONG, 2"));
		assertFalse(dump.contains("PUSH_LONG, 4"));
		assertFalse(dump.contains("PUSH_LONG, 5"));
		assertEquals(3.0, JRT.toDouble(awk.eval(expression, "x b c")), 0.0);
		assertEquals(9.0, JRT.toDouble(awk.eval(expression, "y b c")), 0.0);
	}

	@Test
	public void testCompileExpressionTernaryExpressionStartsWithoutGoto() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("($1 + 0) ? $2 : $3");
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertEquals("b", awk.eval(expression, "1 b c"));
		assertEquals("c", awk.eval(expression, "0 b c"));
	}

	@Test
	public void testCompileExpressionUnoptimizedTernaryExpressionResolvesBranchTargets() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("($1 + 0) ? $2 : $3", true);
		String dump = dumpTuples(expression);

		assertFalse(startsWithGoto(dump));
		assertEquals("b", awk.eval(expression, "1 b c"));
		assertEquals("c", awk.eval(expression, "0 b c"));
	}

	@Test
	public void testFieldOnlyEvalUsesFreshAvmPerInvocation() throws Exception {
		CountingAwk awk = new CountingAwk();
		AwkExpression expression = awk.compileExpression("NF \":\" $2");

		assertEquals("3:b", awk.eval(expression, "a b c"));
		assertEquals("2:right", awk.eval(expression, "left right"));
		assertEquals(2, awk.getCreateAvmCount());
	}

	@Test
	public void testStatefulEvalUsesFreshAvmPerInvocation() throws Exception {
		CountingAwk awk = new CountingAwk();
		AwkExpression expression = awk.compileExpression("match($0, /a/)");

		assertEquals(1, awk.eval(expression, "a"));
		assertEquals(1, awk.eval(expression, "a"));
		assertEquals(2, awk.getCreateAvmCount());
	}

	@Test
	public void testEvalHonorsSettingsChangesBetweenCalls() throws Exception {
		AwkSettings settings = new AwkSettings();
		Awk awk = new Awk(settings);
		AwkExpression expression = awk.compileExpression("$2");

		settings.setFieldSeparator(",");
		assertEquals("beta", awk.eval(expression, "alpha,beta"));

		settings.setFieldSeparator(":");
		assertEquals("right", awk.eval(expression, "left:right"));
	}

	@Test
	public void testEvalStringInputRepresentsSingleRecord() throws Exception {
		Awk awk = new Awk();

		assertEquals("left\nright", awk.eval("$0", "left\nright"));
	}

	@Test
	public void testEvalSourceAliasesStructuredInputEvaluation() throws Exception {
		Awk awk = new Awk();
		InputSource source = new TableInputSource(Collections.singletonList(Arrays.asList("left", "right")));

		assertEquals("2:right", awk.eval("NF \":\" $2", source));
	}

	@Test
	public void testPrepareEvalReusesOneStringRecordAcrossSeveralExpressions() throws Exception {
		Awk awk = new Awk();
		AwkExpression secondField = awk.compileExpression("$2");
		AwkExpression sizeAndLastField = awk.compileExpression("NF \":\" $NF");

		try (AVM prepared = awk.prepareEval("alpha beta gamma")) {
			assertEquals("beta", prepared.eval(secondField));
			assertEquals("3:gamma", prepared.eval(sizeAndLastField));
		}
	}

	@Test
	public void testPrepareEvalReusesOneStructuredRecordAcrossSeveralExpressions() throws Exception {
		Awk awk = new Awk();
		AwkExpression secondField = awk.compileExpression("$2");
		AwkExpression sizeAndLastField = awk.compileExpression("NF \":\" $NF");

		try (
				AVM prepared = awk
						.prepareEval(
								new TableInputSource(Collections.singletonList(Arrays.asList("left", "right", "tail"))))) {
			assertEquals("right", prepared.eval(secondField));
			assertEquals("3:tail", prepared.eval(sizeAndLastField));
		}
	}

	@Test
	public void testPrepareEvalIntentionallyCarriesJrtStateAcrossExpressions() throws Exception {
		Awk awk = new Awk();
		AwkExpression matcher = awk.compileExpression("match($0, /alpha/)");
		AwkExpression startAndLength = awk.compileExpression("RSTART \":\" RLENGTH");

		try (AVM prepared = awk.prepareEval("alpha beta")) {
			assertEquals(1, prepared.eval(matcher));
			assertEquals("1:5", prepared.eval(startAndLength));
		}
	}

	@Test
	public void testPrepareEvalIntentionallyCarriesStateAcrossRepeatedTupleRuns() throws Exception {
		Awk awk = new Awk();
		AwkExpression increment = awk.compileExpression("a++");

		try (AVM prepared = awk.prepareEval("alpha beta")) {
			assertEquals(0.0, JRT.toDouble(prepared.eval(increment)), 0.0);
			assertEquals(1.0, JRT.toDouble(prepared.eval(increment)), 0.0);
		}
	}

	@Test
	public void testPrepareForEvalCanAdvanceAcrossRecordsOnSameSource() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("NF \":\" $2");
		TableInputSource source = new TableInputSource(
				Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("left", "right")));

		try (AVM avm = new AVM(new AwkSettings(), Collections.emptyMap())) {
			assertTrue(avm.prepareForEval(source));
			assertEquals("3:b", avm.eval(expression));
			assertTrue(avm.prepareForEval(source));
			assertEquals("2:right", avm.eval(expression));
			assertFalse(avm.prepareForEval(source));
		}
	}

	@Test
	public void testPrepareEvalRejectsDifferentStatefulTupleLayoutsWithoutReprepare() throws Exception {
		Awk awk = new Awk();
		AwkExpression firstIncrement = awk.compileExpression("a++");
		AwkExpression secondIncrement = awk.compileExpression("b++");

		try (AVM prepared = awk.prepareEval("alpha beta")) {
			assertEquals(0.0, JRT.toDouble(prepared.eval(firstIncrement)), 0.0);
			AwkRuntimeException thrown = assertThrows(AwkRuntimeException.class, () -> prepared.eval(secondIncrement));
			assertTrue(thrown.getCause() instanceof IllegalStateException);
			assertEquals(
					"AVM globals are already initialized for a different eval layout. Call prepareForEval(...) first.",
					thrown.getCause().getMessage());
		}
	}

	@Test
	public void testPrepareEvalRejectsExhaustedSource() {
		Awk awk = new Awk();
		TableInputSource emptySource = new TableInputSource(Collections.<List<String>>emptyList());

		IOException thrown = assertThrows(IOException.class, () -> awk.prepareEval(emptySource));

		assertEquals("No record available from source.", thrown.getMessage());
	}

	@Test
	public void testPrepareEvalClosesSourceWhenPreparationThrows() {
		Awk awk = new Awk();
		ThrowingCloseTrackingInputSource source = new ThrowingCloseTrackingInputSource();

		IOException thrown = assertThrows(IOException.class, () -> awk.prepareEval(source));

		assertEquals("boom", thrown.getMessage());
		assertTrue(source.isClosed());
	}

	@Test
	public void testPreparedAvmCanCloseBoundInputSource() throws Exception {
		Awk awk = new Awk();
		AwkExpression secondField = awk.compileExpression("$2");
		CloseTrackingInputSource source = new CloseTrackingInputSource(
				Collections.singletonList(Arrays.asList("left", "right")));

		try (AVM prepared = awk.prepareEval(source)) {
			assertEquals("right", prepared.eval(secondField));
		}

		assertTrue(source.isClosed());
	}

	@Test
	public void testDirectAvmEvalLeavesBoundInputSourceOpenUntilClose() throws Exception {
		Awk awk = new Awk();
		AwkExpression secondField = awk.compileExpression("$2");
		CloseTrackingInputSource source = new CloseTrackingInputSource(
				Collections.singletonList(Arrays.asList("left", "right")));

		try (AVM avm = new AVM(new AwkSettings(), Collections.emptyMap())) {
			assertEquals("right", avm.eval(secondField, source));
			assertFalse(source.isClosed());
		}

		assertTrue(source.isClosed());
	}

	@Test
	public void testAwkEvalClosesStructuredInputSourceAfterOneShotEval() throws Exception {
		Awk awk = new Awk();
		AwkExpression secondField = awk.compileExpression("$2");
		CloseTrackingInputSource source = new CloseTrackingInputSource(
				Collections.singletonList(Arrays.asList("left", "right")));

		assertEquals("right", awk.eval(secondField, source));
		assertTrue(source.isClosed());
	}

	@Test
	public void testPrepareForEvalClosesPreviousCloseableInputSourceWhenRebinding() throws Exception {
		CloseTrackingInputSource first = new CloseTrackingInputSource(Collections.singletonList(Arrays.asList("a", "b")));
		CloseTrackingInputSource second = new CloseTrackingInputSource(Collections.singletonList(Arrays.asList("x", "y")));

		try (AVM avm = new AVM(new AwkSettings(), Collections.emptyMap())) {
			assertTrue(avm.prepareForEval(first));
			assertFalse(first.isClosed());
			assertTrue(avm.prepareForEval(second));
			assertTrue(first.isClosed());
			assertFalse(second.isClosed());
		}

		assertTrue(second.isClosed());
	}

	@Test
	public void testInterpretClosesPreviousCloseableInputSourceWhenRebinding() throws Exception {
		Awk awk = new Awk();
		AwkProgram program = awk.compile("{ }");
		CloseTrackingInputSource first = new CloseTrackingInputSource(Collections.singletonList(Arrays.asList("a", "b")));
		CloseTrackingInputSource second = new CloseTrackingInputSource(Collections.singletonList(Arrays.asList("x", "y")));
		AwkSettings settings = new AwkSettings();

		try (AVM avm = new AVM(settings, Collections.emptyMap())) {
			avm.execute(program, first);
			assertFalse(first.isClosed());
			avm.execute(program, second);
			assertTrue(first.isClosed());
			assertFalse(second.isClosed());
		}

		assertTrue(second.isClosed());
	}

	@Test
	public void testOptimizeRemainsIdempotentForEvalTuples() throws Exception {
		AwkExpression expression = new Awk().compileExpression("$2 + $3");
		AwkTuples tuples = rawTuples(expression);
		String before = dumpTuples(tuples);

		tuples.optimize();
		String after = dumpTuples(tuples);

		assertEquals(before, after);
		assertEquals(5.0, JRT.toDouble(new Awk().eval(expression, "x 2 3")), 0.0);
	}

	@Test
	public void testReusedAvmPreparesStatefulExecutionOnEachRun() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("match($0, /a/)");
		TrackingAVM avm = new TrackingAVM(new AwkSettings());

		try (TrackingAVM ignored = avm) {
			assertEquals(1, avm.eval(expression, new SingleRecordInputSource("a")));
			assertEquals(1, avm.eval(expression, new SingleRecordInputSource("a")));
			assertEquals(2, avm.getPrepareForExecutionCount());
		}
		assertEquals(3, avm.getCloseAllCount());
	}

	@Test
	public void testReusedAvmEvalUsesPreparedRuntimeSetup() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("NF \":\" $2");
		TrackingAVM avm = new TrackingAVM(new AwkSettings());

		try (TrackingAVM ignored = avm) {
			assertEquals("3:b", avm.eval(expression, new SingleRecordInputSource("a b c")));
			assertEquals("2:right", avm.eval(expression, new SingleRecordInputSource("left right")));
			assertEquals(2, avm.getPrepareForExecutionCount());
		}
		assertEquals(3, avm.getCloseAllCount());
	}

	@Test
	public void testVariableOverridesDoNotLeakAcrossReusedAvmEvalCalls() throws Exception {
		Awk awk = new Awk();
		AwkExpression expression = awk.compileExpression("x == \"\"");
		AVM avm = new AVM(new AwkSettings(), Collections.emptyMap());

		assertEquals(
				0,
				avm
						.eval(
								expression,
								new SingleRecordInputSource((String) null),
								Collections.<String, Object>singletonMap("x", "set")));
		assertEquals(1, avm.eval(expression, new SingleRecordInputSource((String) null)));
	}

	private static final class TableInputSource implements InputSource {

		private final List<List<String>> rows;
		private int index = -1;
		private List<String> currentFields;
		private String currentRecord;

		private TableInputSource(List<List<String>> rows) {
			this.rows = rows;
		}

		@Override
		public boolean nextRecord() throws IOException {
			int nextIndex = index + 1;
			if (nextIndex >= rows.size()) {
				currentFields = null;
				currentRecord = null;
				return false;
			}
			index = nextIndex;
			currentFields = Collections.unmodifiableList(new ArrayList<String>(rows.get(index)));
			currentRecord = String.join(" ", currentFields);
			return true;
		}

		@Override
		public String getRecordText() {
			return currentRecord;
		}

		@Override
		public List<String> getFields() {
			return currentFields;
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}
	}

	private static String dumpTuples(AwkTuples tuples) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
			tuples.dump(ps);
		}
		return out.toString(StandardCharsets.UTF_8.name());
	}

	private static String dumpTuples(AwkExpression expression) throws IOException {
		return dumpTuples(rawTuples(expression));
	}

	private static AwkTuples rawTuples(AwkExpression expression) {
		return expression;
	}

	private static boolean startsWithGoto(String dump) {
		if (dump == null || dump.isEmpty()) {
			return false;
		}

		int length = dump.length();
		int start = 0;
		while (start < length) {
			int end = dump.indexOf('\n', start);
			if (end < 0) {
				end = length;
			}

			String line = dump.substring(start, end).trim();
			if (!line.isEmpty()) {
				int idx = 0;
				while (idx < line.length() && Character.isDigit(line.charAt(idx))) {
					idx++;
				}
				if (idx > 0 && idx + 3 <= line.length() && line.startsWith(" : ", idx)) {
					return line.contains("GOTO");
				}
			}

			start = end + 1;
		}
		return false;
	}

	private static final class SingleRecordInputSource implements InputSource {

		private final String recordText;
		private boolean consumed;

		private SingleRecordInputSource(String recordText) {
			this.recordText = recordText;
		}

		@Override
		public boolean nextRecord() {
			if (consumed || recordText == null) {
				return false;
			}
			consumed = true;
			return true;
		}

		@Override
		public String getRecordText() {
			return consumed ? recordText : null;
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

	private static final class CountingAwk extends Awk {

		private final AtomicInteger createAvmCount = new AtomicInteger();

		private CountingAwk() {
			super(new AwkSettings());
		}

		@Override
		protected AVM createAvm(AwkSettings settingsParam) {
			createAvmCount.incrementAndGet();
			return super.createAvm(settingsParam);
		}

		private int getCreateAvmCount() {
			return createAvmCount.get();
		}
	}

	private static final class TrackingAVM extends AVM {

		private TrackingJRT trackingJrt;

		private TrackingAVM(AwkSettings settings) {
			super(settings, Collections.emptyMap());
		}

		@Override
		protected JRT createJrt() {
			AwkSettings s = getSettings();
			trackingJrt = new TrackingJRT(this, s.getLocale(), AwkSink.from(System.out, s.getLocale()));
			return trackingJrt;
		}

		private int getPrepareForExecutionCount() {
			return trackingJrt.getPrepareForExecutionCount();
		}

		private int getCloseAllCount() {
			return trackingJrt.getCloseAllCount();
		}
	}

	private static final class TrackingJRT extends JRT {

		private int prepareForExecutionCount;
		private int closeAllCount;

		private TrackingJRT(AVM avm, Locale locale, AwkSink awkSink) {
			super(avm, locale, awkSink, System.err);
		}

		@Override
		public void prepareForExecution(String defaultFs, String defaultRs, String defaultOrs) {
			prepareForExecutionCount++;
			super.prepareForExecution(defaultFs, defaultRs, defaultOrs);
		}

		@Override
		public void jrtCloseAll() {
			closeAllCount++;
			super.jrtCloseAll();
		}

		private int getPrepareForExecutionCount() {
			return prepareForExecutionCount;
		}

		private int getCloseAllCount() {
			return closeAllCount;
		}
	}

	private static class CloseTrackingInputSource implements InputSource, Closeable {

		private final TableInputSource delegate;
		private boolean closed;

		private CloseTrackingInputSource(List<List<String>> rows) {
			delegate = new TableInputSource(rows);
		}

		@Override
		public boolean nextRecord() throws IOException {
			return delegate.nextRecord();
		}

		@Override
		public String getRecordText() {
			return delegate.getRecordText();
		}

		@Override
		public List<String> getFields() {
			return delegate.getFields();
		}

		@Override
		public boolean isFromFilenameList() {
			return delegate.isFromFilenameList();
		}

		@Override
		public void close() {
			closed = true;
		}

		boolean isClosed() {
			return closed;
		}
	}

	private static final class ThrowingCloseTrackingInputSource extends CloseTrackingInputSource {

		private ThrowingCloseTrackingInputSource() {
			super(Collections.<List<String>>emptyList());
		}

		@Override
		public boolean nextRecord() throws IOException {
			throw new IOException("boom");
		}
	}
}
