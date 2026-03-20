package org.metricshub.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ••••••
 * Copyright (C) 2006 - 2026 MetricsHub
 * ••••••
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.jrt.InputSource;
import org.metricshub.jawk.jrt.JRT;
import org.metricshub.jawk.util.AwkSettings;

/**
 * Tests for {@link Awk#eval} execution paths.
 */
public class AwkEvalTest {

	@Test
	public void testReusableEvalResetsStructuredInputStateBetweenRuns() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("NR \":\" NF \":\" $1");

		assertEquals(
				"1:2:alpha",
				awk.eval(tuples, new TableInputSource(Collections.singletonList(Arrays.asList("alpha", "beta")))));
		assertEquals(
				"1:3:x",
				awk.eval(tuples, new TableInputSource(Collections.singletonList(Arrays.asList("x", "y", "z")))));
	}

	@Test
	public void testReusableEvalResetsStringInputStateBetweenRuns() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("NF \":\" $2");

		assertEquals("3:b", awk.eval(tuples, "a b c"));
		assertEquals("2:right", awk.eval(tuples, "left right"));
	}

	@Test
	public void testReadOnlyEvalEligibilityClassification() throws Exception {
		Awk awk = new Awk();

		assertTrue(awk.compileForEval("$2").isReadOnlyEvalEligible());
		assertFalse(awk.compileForEval("match($0, /a/)").isReadOnlyEvalEligible());
		assertFalse(awk.compileForEval("rand()").isReadOnlyEvalEligible());
	}

	@Test
	public void testCompileForEvalOmitsSetNumGlobalsForFieldOnlyExpression() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("NF \":\" $2");

		assertFalse(dumpTuples(tuples).contains("SET_NUM_GLOBALS"));
		assertFalse(tuples.getExecutionProfile().usesRuntimeStack());
		assertFalse(tuples.getExecutionProfile().needsGlobalVariableMetadata());
		assertEquals("3:b", awk.eval(tuples, "a b c"));
	}

	@Test
	public void testCompileForEvalKeepsSetNumGlobalsWhenGlobalMetadataIsNeeded() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("match($0, /a/)");

		assertTrue(dumpTuples(tuples).contains("SET_NUM_GLOBALS"));
		assertTrue(tuples.getExecutionProfile().usesRuntimeStack());
		assertTrue(tuples.getExecutionProfile().needsGlobalVariableMetadata());
		assertEquals(1, awk.eval(tuples, "a"));
	}

	@Test
	public void testReadOnlyEvalUsesFreshAvmPerInvocation() throws Exception {
		CountingAwk awk = new CountingAwk();
		AwkTuples tuples = awk.compileForEval("NF \":\" $2");

		assertEquals("3:b", awk.eval(tuples, "a b c"));
		assertEquals("2:right", awk.eval(tuples, "left right"));
		assertEquals(2, awk.getCreateAvmCount());
	}

	@Test
	public void testStatefulEvalUsesFreshAvmPerInvocation() throws Exception {
		CountingAwk awk = new CountingAwk();
		AwkTuples tuples = awk.compileForEval("match($0, /a/)");

		assertEquals(1, awk.eval(tuples, "a"));
		assertEquals(1, awk.eval(tuples, "a"));
		assertEquals(2, awk.getCreateAvmCount());
	}

	@Test
	public void testEvalRebuildsPrototypeWhenSettingsChange() throws Exception {
		AwkSettings settings = new AwkSettings();
		Awk awk = new Awk(settings);
		AwkTuples tuples = awk.compileForEval("$2");

		settings.setFieldSeparator(",");
		assertEquals("beta", awk.eval(tuples, "alpha,beta"));

		settings.setFieldSeparator(":");
		assertEquals("right", awk.eval(tuples, "left:right"));
	}

	@Test
	public void testEvalStringInputRepresentsSingleRecord() throws Exception {
		Awk awk = new Awk();

		assertEquals("left\nright", awk.eval("$0", "left\nright"));
	}

	@Test
	public void testEvalSourceAliasesStructuredInputEvaluation() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("NF \":\" $2");
		InputSource source = new TableInputSource(Collections.singletonList(Arrays.asList("left", "right")));

		assertEquals("2:right", awk.evalSource("NF \":\" $2", source));
		assertEquals(
				"2:right",
				awk
						.evalSource(
								tuples,
								new TableInputSource(Collections.singletonList(Arrays.asList("left", "right")))));
	}

	@Test
	public void testOptimizeRemainsIdempotentForExecutionProfile() throws Exception {
		AwkTuples tuples = new Awk().compileForEval("$2 + $3");
		AwkTuples.ExecutionProfile initialProfile = tuples.getExecutionProfile();

		tuples.optimize();

		assertSame(initialProfile, tuples.getExecutionProfile());
		assertTrue(tuples.isReadOnlyEvalEligible());
	}

	@Test
	public void testReusedAvmPreparesStatefulExecutionOnEachRun() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("match($0, /a/)");
		TrackingAVM avm = new TrackingAVM(new AwkSettings());

		assertEquals(1, avm.eval(tuples, new SingleRecordInputSource("a")));
		assertEquals(1, avm.eval(tuples, new SingleRecordInputSource("a")));
		assertEquals(2, avm.getPrepareForExecutionCount());
		assertEquals(0, avm.getLegacyInitializationCount());
	}

	@Test
	public void testReusedAvmEvalAutomaticallyUsesReadOnlyPreparation() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("NF \":\" $2");
		TrackingAVM avm = new TrackingAVM(new AwkSettings());

		assertEquals("3:b", avm.eval(tuples, new SingleRecordInputSource("a b c")));
		assertEquals("2:right", avm.eval(tuples, new SingleRecordInputSource("left right")));
		assertEquals(2, avm.getPrepareForExecutionCount());
		assertEquals(0, avm.getLegacyInitializationCount());
		assertEquals(0, avm.getCloseAllCount());
	}

	@Test
	public void testVariableOverridesDoNotLeakAcrossReusedAvmEvalCalls() throws Exception {
		Awk awk = new Awk();
		AwkTuples tuples = awk.compileForEval("x == \"\"");
		AVM avm = new AVM(new AwkSettings(), Collections.emptyMap());

		assertEquals(
				0,
				avm
						.eval(
								tuples,
								new SingleRecordInputSource((String) null),
								Collections.<String, Object>singletonMap("x", "set")));
		assertEquals(1, avm.eval(tuples, new SingleRecordInputSource((String) null)));
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
			trackingJrt = new TrackingJRT(this);
			return trackingJrt;
		}

		private int getPrepareForExecutionCount() {
			return trackingJrt.getPrepareForExecutionCount();
		}

		private int getLegacyInitializationCount() {
			return trackingJrt.getLegacyInitializationCount();
		}

		private int getCloseAllCount() {
			return trackingJrt.getCloseAllCount();
		}
	}

	private static final class TrackingJRT extends JRT {

		private int prepareForExecutionCount;
		private int legacyInitializationCount;
		private int closeAllCount;

		private TrackingJRT(AVM avm) {
			super(avm);
		}

		@Override
		public void prepareForExecution(String initialFsValue, String defaultRs, String defaultOrs) {
			prepareForExecutionCount++;
			super.prepareForExecution(initialFsValue, defaultRs, defaultOrs);
		}

		@Override
		public void initializeRuntimeState(String initialFsValue, String defaultRs, String defaultOrs) {
			legacyInitializationCount++;
			super.initializeRuntimeState(initialFsValue, defaultRs, defaultOrs);
		}

		@Override
		public void initializeFreshRuntimeState(String initialFsValue, String defaultRs, String defaultOrs) {
			legacyInitializationCount++;
			super.initializeFreshRuntimeState(initialFsValue, defaultRs, defaultOrs);
		}

		@Override
		public void jrtCloseAll() {
			closeAllCount++;
			super.jrtCloseAll();
		}

		private int getPrepareForExecutionCount() {
			return prepareForExecutionCount;
		}

		private int getLegacyInitializationCount() {
			return legacyInitializationCount;
		}

		private int getCloseAllCount() {
			return closeAllCount;
		}
	}
}
