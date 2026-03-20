package org.metricshub.jawk.jrt;

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

import java.io.IOException;
import java.util.List;

/**
 * Strategy for sourcing input records for the AWK main loop.
 * <p>
 * The default AWK behavior reads records from an {@link java.io.InputStream}
 * and splits them using the field separator (FS). Implementations of this
 * interface can bypass that text-based flow and supply pre-structured records
 * directly (for example, rows from an in-memory table).
 * </p>
 */
public interface InputSource {

	/**
	 * Advances to the next input record.
	 *
	 * @return {@code true} when a record is available, {@code false} when input
	 *         is exhausted
	 * @throws IOException if an I/O error occurs
	 */
	boolean nextRecord() throws IOException;

	/**
	 * Returns the current record text ({@code $0}), or {@code null} when the
	 * source only exposes pre-split fields for the current record.
	 * <p>
	 * When both {@link #getRecordText()} and {@link #getFields()} return
	 * non-null values, the field list is authoritative for field/NF access while
	 * the record text is authoritative for the initial {@code $0} value.
	 * </p>
	 *
	 * @return current record text, or {@code null} when unavailable
	 */
	default String getRecordText() {
		return getRecord();
	}

	/**
	 * Returns the current record text ({@code $0}).
	 * <p>
	 * Deprecated compatibility alias for embedders that still implement the
	 * historic {@code getRecord()} method instead of {@link #getRecordText()}.
	 * New implementations should override {@link #getRecordText()} directly.
	 * </p>
	 *
	 * @return current record text, or {@code null} when unavailable
	 * @deprecated use {@link #getRecordText()}
	 */
	@Deprecated
	default String getRecord() {
		return null;
	}

	/**
	 * Returns pre-split fields for the current record, or {@code null} when the
	 * runtime should split {@code $0} using FS.
	 * <p>
	 * When non-null, element {@code 0} corresponds to {@code $1}, element
	 * {@code 1} to {@code $2}, and so on.
	 * </p>
	 *
	 * @return field values for {@code $1..$NF}, or {@code null} to request
	 *         standard FS-based splitting
	 */
	List<String> getFields();

	/**
	 * Indicates whether the current record originates from a named file in the
	 * argument list.
	 *
	 * @return {@code true} when sourced from a filename argument
	 */
	boolean isFromFilenameList();
}
