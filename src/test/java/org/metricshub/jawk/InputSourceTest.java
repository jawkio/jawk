package org.metricshub.jawk;

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
import static org.metricshub.jawk.AwkTestSupport.awkTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.jrt.InputSource;
import org.metricshub.jawk.util.AwkSettings;

/**
 * Integration tests for {@link InputSource} support in the AWK runtime.
 */
public class InputSourceTest {

	@Test
	public void testBasicFieldAccess() throws Exception {
		awkTest("input source basic field access")
				.script("{ print $1, $2, $3 }")
				.withInputSource(
						new TableInputSource(
								Arrays
										.asList(
												Arrays.asList("a", "b", "c"),
												Arrays.asList("d", "e", "f"))))
				.expectLines("a b c", "d e f")
				.runAndAssert();
	}

	@Test
	public void testNfReflectsInputRowSize() throws Exception {
		awkTest("input source NF matches row width")
				.script("{ print NF }")
				.withInputSource(
						new TableInputSource(
								Arrays
										.asList(
												Arrays.asList("a", "b", "c"),
												Collections.singletonList("d"),
												Collections.<String>emptyList())))
				.expectLines("3", "1", "0")
				.runAndAssert();
	}

	@Test
	public void testNrCountsRecordsFromInputSource() throws Exception {
		awkTest("input source contributes to NR")
				.script("END { print NR }")
				.withInputSource(
						new TableInputSource(
								Arrays
										.asList(
												Collections.singletonList("r1"),
												Collections.singletonList("r2"),
												Collections.singletonList("r3"))))
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void testDollarZeroUsesInputSourceRecord() throws Exception {
		awkTest("input source controls dollar zero text")
				.script("{ print $0, $2 }")
				.withInputSource(
						new TableInputSource(
								Collections.singletonList(Arrays.asList("alice", "30", "engineering")),
								"|"))
				.expectLines("alice|30|engineering 30")
				.runAndAssert();
	}

	@Test
	public void testFieldOnlyAccessAvoidsRecordTextLookup() throws Exception {
		TrackingInputSource source = new TrackingInputSource(
				Collections.singletonList(new ExplicitRecord("left|right", Arrays.asList("left", "right"))));
		awkTest("field-only access avoids record text lookup")
				.script("{ print $2 }")
				.withInputSource(source)
				.expectLines("right")
				.runAndAssert();
		assertEquals(0, source.getRecordTextCalls());
		assertEquals(1, source.getFieldsCalls());
	}

	@Test
	public void testDynamicFieldAccessAvoidsRecordTextLookup() throws Exception {
		TrackingInputSource source = new TrackingInputSource(
				Collections.singletonList(new ExplicitRecord("left|right", Arrays.asList("left", "right"))));
		awkTest("dynamic field access avoids record text lookup")
				.script("{ field = 2; print $field }")
				.withInputSource(source)
				.expectLines("right")
				.runAndAssert();
		assertEquals(0, source.getRecordTextCalls());
		assertEquals(1, source.getFieldsCalls());
	}

	@Test
	public void testDollarZeroAccessAvoidsFieldLookup() throws Exception {
		TrackingInputSource source = new TrackingInputSource(
				Collections.singletonList(new ExplicitRecord("left|right", Arrays.asList("left", "right"))));
		awkTest("dollar-zero access avoids field lookup")
				.script("{ print }")
				.withInputSource(source)
				.expectLines("left|right")
				.runAndAssert();
		assertEquals(1, source.getRecordTextCalls());
		assertEquals(0, source.getFieldsCalls());
	}

	@Test
	public void testOfsUsedWhenDollarZeroIsRebuilt() throws Exception {
		awkTest("OFS applies when fields rebuild dollar zero")
				.script("BEGIN { OFS = \",\" } { $1 = $1; print $0 }")
				.withInputSource(new TableInputSource(Collections.singletonList(Arrays.asList("a", "b", "c"))))
				.expectLines("a,b,c")
				.runAndAssert();
	}

	@Test
	public void testFieldModificationRebuildsDollarZero() throws Exception {
		awkTest("field assignment rebuilds dollar zero")
				.script("{ $2 = \"X\"; print $0 }")
				.withInputSource(new TableInputSource(Collections.singletonList(Arrays.asList("a", "b", "c"))))
				.expectLines("a X c")
				.runAndAssert();
	}

	@Test
	public void testEmptyInputSourceRunsBeginAndEndOnly() throws Exception {
		awkTest("empty input source still runs begin and end")
				.script("BEGIN { print \"start\" } { print $0 } END { print \"end\" }")
				.withInputSource(new TableInputSource(Collections.<List<String>>emptyList()))
				.expectLines("start", "end")
				.runAndAssert();
	}

	@Test
	public void testNullFieldsFallsBackToFsSplitting() throws Exception {
		awkTest("input source fallback to FS splitting")
				.script("BEGIN { FS = \",\" } { print $2 }")
				.withInputSource(new RecordOnlyInputSource("a,b,c", "d,e,f"))
				.expectLines("b", "e")
				.runAndAssert();
	}

	@Test
	public void testGetlineConsumesNextRecord() throws Exception {
		awkTest("getline advances input source")
				.script("NR==1 { getline; print $0; exit }")
				.withInputSource(
						new TableInputSource(
								Arrays
										.asList(
												Collections.singletonList("first"),
												Collections.singletonList("second"),
												Collections.singletonList("third"))))
				.expectLines("second")
				.runAndAssert();
	}

	@Test
	public void testGetlineUsesPreSplitFieldsWhenProvided() throws Exception {
		awkTest("getline preserves pre-split fields from input source")
				.script("BEGIN { FS = \",\" } NR==1 { getline; print $1, $2; exit }")
				.withInputSource(
						new ExplicitRecordInputSource(
								Arrays
										.asList(
												new ExplicitRecord("ignored", Arrays.asList("first", "record")),
												new ExplicitRecord("left|right", Arrays.asList("left", "right")))))
				.expectLines("left right")
				.runAndAssert();
	}

	@Test
	public void testGetlineIntoVariableMaterializesCurrentRecordBeforeAdvance() throws Exception {
		TrackingInputSource source = new TrackingInputSource(
				Arrays
						.asList(
								new ExplicitRecord("trigger", Collections.singletonList("trigger")),
								new ExplicitRecord("left|right", Arrays.asList("left", "right"))));
		awkTest("getline into variable materializes current record before advance")
				.script("NR == 1 { getline line; print line; exit }")
				.withInputSource(source)
				.expectLines("left|right")
				.runAndAssert();
		assertEquals(2, source.getRecordTextCalls());
		assertEquals(1, source.getFieldsCalls());
	}

	@Test
	public void testFileListVariableAssignmentsAreAppliedWithInputSource() throws Exception {
		awkTest("input source still applies name=value operands")
				.script("{ print x }")
				.operand("x=42")
				.withInputSource(new TableInputSource(Collections.singletonList(Collections.singletonList("row"))))
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void testRedirectedGetlineDoesNotReuseInputSourceFields() throws Exception {
		awkTest("redirected getline parses fields from redirected stream")
				.script(
						"BEGIN { FS = \",\" } NR == 1 { getline x; getline < \"{{side.txt}}\"; print \"[\" $1 \"]\", \"[\" $2 \"]\"; exit }")
				.file("side.txt", "left|right\n")
				.withInputSource(
						new ExplicitRecordInputSource(
								Arrays
										.asList(
												new ExplicitRecord("trigger", Collections.singletonList("trigger")),
												new ExplicitRecord("left|right", Arrays.asList("left", "right")))))
				.expectLines("[left|right] []")
				.runAndAssert();
	}

	@Test
	public void testNfModificationTruncatesRecord() throws Exception {
		awkTest("NF assignment truncates pre-split records")
				.script("{ NF = 2; print $0 }")
				.withInputSource(new TableInputSource(Collections.singletonList(Arrays.asList("a", "b", "c", "d"))))
				.expectLines("a b")
				.runAndAssert();
	}

	@Test
	public void testFieldsOnlyInputSourceSupportsFieldAccessWithoutRecordText() throws Exception {
		awkTest("fields only input source supports field access")
				.script("{ print $2 }")
				.withInputSource(new FieldsOnlyInputSource(Collections.singletonList(Arrays.asList("left", "right"))))
				.expectLines("right")
				.runAndAssert();
	}

	@Test
	public void testFieldsOnlyInputSourceSynthesizesDollarZeroUsingLiteralFs() throws Exception {
		InputSource source = new FieldsOnlyInputSource(Collections.singletonList(Arrays.asList("a", "b", "c")));
		assertEquals("a,b,c", commaSeparatedAwk().eval("$0", source));
	}

	@Test
	public void testFieldsOnlyInputSourceSupportsBareRegexpRules() throws Exception {
		awkTest("fields only input source supports bare regexp conditions")
				.script("/right/ { print $2 }")
				.withInputSource(new FieldsOnlyInputSource(Collections.singletonList(Arrays.asList("left", "right"))))
				.expectLines("right")
				.runAndAssert();
	}

	@Test
	public void testFieldsOnlyInputSourceRegexpUsesCurrentDollarZeroAfterFieldMutation() throws Exception {
		awkTest("fields only input source regexp uses rebuilt dollar zero")
				.script("{ $1 = \"x\"; if (/x/) print \"hit\" }")
				.withInputSource(new FieldsOnlyInputSource(Collections.singletonList(Arrays.asList("left", "right"))))
				.expectLines("hit")
				.runAndAssert();
	}

	@Test
	public void testTextOnlyInputSourceUsesFsActiveWhenRecordWasRead() throws Exception {
		awkTest("text only input source keeps FS semantics at record read time")
				.script("{ FS = \":\"; print $1 }")
				.withInputSource(new RecordOnlyInputSource("a b"))
				.expectLines("a")
				.runAndAssert();
	}

	private static final class TableInputSource implements InputSource {

		private static final String DEFAULT_SEPARATOR = " ";

		private final List<List<String>> rows;
		private final String separator;
		private int index = -1;
		private List<String> currentFields;
		private String currentRecord;

		private TableInputSource(List<List<String>> rows) {
			this(rows, DEFAULT_SEPARATOR);
		}

		private TableInputSource(List<List<String>> rows, String separator) {
			this.rows = rows;
			this.separator = separator;
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
			currentFields = new ArrayList<String>(rows.get(index));
			currentRecord = String.join(separator, currentFields);
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

	private static final class ExplicitRecordInputSource implements InputSource {

		private final List<ExplicitRecord> records;
		private int index = -1;

		private ExplicitRecordInputSource(List<ExplicitRecord> records) {
			this.records = records;
		}

		@Override
		public boolean nextRecord() throws IOException {
			index++;
			return index < records.size();
		}

		@Override
		public String getRecordText() {
			return records.get(index).record;
		}

		@Override
		public List<String> getFields() {
			return records.get(index).fields;
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}
	}

	private static final class TrackingInputSource implements InputSource {

		private final List<ExplicitRecord> records;
		private int index = -1;
		private int recordTextCalls;
		private int fieldsCalls;

		private TrackingInputSource(List<ExplicitRecord> records) {
			this.records = records;
		}

		@Override
		public boolean nextRecord() throws IOException {
			index++;
			return index < records.size();
		}

		@Override
		public String getRecordText() {
			recordTextCalls++;
			return records.get(index).record;
		}

		@Override
		public List<String> getFields() {
			fieldsCalls++;
			List<String> fields = records.get(index).fields;
			return fields == null ? null : new ArrayList<String>(fields);
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}

		private int getRecordTextCalls() {
			return recordTextCalls;
		}

		private int getFieldsCalls() {
			return fieldsCalls;
		}
	}

	private static final class ExplicitRecord {

		private final String record;
		private final List<String> fields;

		private ExplicitRecord(String record, List<String> fields) {
			this.record = record;
			this.fields = fields;
		}
	}

	private static final class RecordOnlyInputSource implements InputSource {

		private final List<String> records;
		private int index = -1;

		private RecordOnlyInputSource(String... records) {
			this.records = Arrays.asList(records);
		}

		@Override
		public boolean nextRecord() throws IOException {
			index++;
			return index < records.size();
		}

		@Override
		public String getRecordText() {
			return records.get(index);
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

	@SuppressWarnings("deprecation")
	private static final class DeprecatedRecordInputSource implements InputSource {

		private final List<String> records;
		private int index = -1;

		private DeprecatedRecordInputSource(String... records) {
			this.records = Arrays.asList(records);
		}

		@Override
		public boolean nextRecord() throws IOException {
			index++;
			return index < records.size();
		}

		@Override
		public String getRecord() {
			return records.get(index);
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

	private static final class FieldsOnlyInputSource implements InputSource {

		private final List<List<String>> records;
		private int index = -1;

		private FieldsOnlyInputSource(List<List<String>> records) {
			this.records = records;
		}

		@Override
		public boolean nextRecord() throws IOException {
			index++;
			return index < records.size();
		}

		@Override
		public String getRecordText() {
			return null;
		}

		@Override
		public List<String> getFields() {
			return new ArrayList<String>(records.get(index));
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}
	}

	// ---- Tests for Awk.eval(expression, InputSource) ----

	private static final Awk AWK = new Awk();

	@Test
	public void testEvalWithInputSourceFieldAccess() throws Exception {
		InputSource source = new TableInputSource(
				Collections.singletonList(Arrays.asList("Alice", "30", "Engineering")));
		assertEquals("Alice-Engineering", AWK.eval("$1 \"-\" $3", source));
	}

	@Test
	public void testEvalWithInputSourcePreSplitFields() throws Exception {
		InputSource source = new TableInputSource(
				Collections.singletonList(Arrays.asList("x", "y", "z")));
		assertEquals(3, ((Number) AWK.eval("NF", source)).intValue());
	}

	@Test
	public void testEvalWithTextOnlyInputSourceAndFieldParsing() throws Exception {
		InputSource source = new RecordOnlyInputSource("one,two,three");
		assertEquals(3, ((Number) commaSeparatedAwk().eval("NF", source)).intValue());
	}

	@Test
	public void testEvalWithInputSourceNoInput() throws Exception {
		assertEquals(5L, AWK.eval("2 + 3"));
	}

	@Test
	public void testEvalWithInputSourceAndSettingsFieldSeparator() throws Exception {
		// When InputSource provides pre-split fields, FS is not used for splitting,
		// but it still comes from the Awk instance settings.
		InputSource source = new TableInputSource(
				Collections.singletonList(Arrays.asList("a", "b", "c")));
		assertEquals("a", commaSeparatedAwk().eval("$1", source));
	}

	@Test
	public void testEvalWithPrecompiledTuplesAndInputSource() throws Exception {
		InputSource source = new TableInputSource(
				Collections.singletonList(Arrays.asList("10", "20", "30")));
		AwkTuples tuples = AWK.compileForEval("$1 + $2 + $3");
		assertEquals(60, ((Number) AWK.eval(tuples, source)).intValue());
	}

	@Test
	public void testDeprecatedGetRecordCompatibilityStillWorks() throws Exception {
		awkTest("deprecated getRecord input source compatibility")
				.script("BEGIN { FS = \",\" } { print $2 }")
				.withInputSource(new DeprecatedRecordInputSource("a,b,c"))
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void testGetlineSynthesizesDollarZeroWithSanitizedNullFields() throws Exception {
		awkTest("getline synthesizes dollar zero with sanitized null fields")
				.script("NR == 1 { getline; print \"[\" $0 \"]\"; exit }")
				.withInputSource(
						new ExplicitRecordInputSource(
								Arrays
										.asList(
												new ExplicitRecord("trigger", Collections.singletonList("trigger")),
												new ExplicitRecord(null, Arrays.asList("left", null, "right")))))
				.expectLines("[left  right]")
				.runAndAssert();
	}

	private static Awk commaSeparatedAwk() {
		AwkSettings settings = new AwkSettings();
		settings.setFieldSeparator(",");
		return new Awk(settings);
	}
}
