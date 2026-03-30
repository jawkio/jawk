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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import io.jawk.jrt.InputSource;

/**
 * Unit tests for an example {@link InputSource} implementation based on
 * {@code List<List<String>>}.
 */
public class ListInputSourceTest {

	@Test
	public void testNextRecordReturnsExpectedSequence() throws Exception {
		ListInputSource source = new ListInputSource(
				Arrays
						.asList(
								Collections.singletonList("a"),
								Arrays.asList("b", "c")));

		assertTrue(source.nextRecord());
		assertEquals("a", source.getRecordText());
		assertTrue(source.nextRecord());
		assertEquals("b c", source.getRecordText());
		assertFalse(source.nextRecord());
	}

	@Test
	public void testGetFieldsReturnsSnapshotOfCurrentRow() throws Exception {
		List<String> mutableRow = new ArrayList<String>(Arrays.asList("alpha", "beta"));
		ListInputSource source = new ListInputSource(Collections.singletonList(mutableRow));

		assertTrue(source.nextRecord());
		mutableRow.set(1, "changed");

		assertEquals(Arrays.asList("alpha", "beta"), source.getFields());
		assertEquals("alpha beta", source.getRecordText());
	}

	@Test
	public void testGetRecordUsesConfiguredSeparator() throws Exception {
		ListInputSource source = new ListInputSource(Collections.singletonList(Arrays.asList("a", "b", "c")), "|");

		assertTrue(source.nextRecord());
		assertEquals("a|b|c", source.getRecordText());
		assertEquals(Arrays.asList("a", "b", "c"), source.getFields());
	}

	@Test
	public void testIsFromFilenameListAlwaysFalse() throws Exception {
		ListInputSource source = new ListInputSource(Collections.singletonList(Collections.singletonList("x")));
		assertTrue(source.nextRecord());
		assertFalse(source.isFromFilenameList());
	}

	private static final class ListInputSource implements InputSource {

		private static final String DEFAULT_SEPARATOR = " ";

		private final List<List<String>> rows;
		private final String separator;
		private int index = -1;
		private List<String> currentFields;
		private String currentRecord;

		private ListInputSource(List<List<String>> rows) {
			this(rows, DEFAULT_SEPARATOR);
		}

		private ListInputSource(List<List<String>> rows, String separator) {
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
			currentFields = Collections.unmodifiableList(new ArrayList<String>(rows.get(index)));
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
}
