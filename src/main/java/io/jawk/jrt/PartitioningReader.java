package io.jawk.jrt;

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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reader which consumes one record at a time from
 * an underlying input reader.
 * <h2>Greedy Regex Matching</h2>
 * The current implementation matches setRecordSeparator against
 * contents of an input buffer (the underlying input
 * stream filling the input buffer). Records are
 * split against the matched regular expression
 * input, treating the regular expression as a
 * record separator.
 * <p>
 * By default, greedy regular expression matching
 * for setRecordSeparator is turned off. It is assumed
 * the user will employ a non-ambiguous regex for setRecordSeparator.
 * For example, ab*c is a non-ambiguous regex,
 * but ab?c?b is an ambiguous regex because
 * it can match ab or abc, and the reader may
 * accept either one, depending on input buffer boundaries.
 * The implemented way to employ greedy regex matching
 * is to consume subsequent input until the match
 * does not occur at the end of the input buffer,
 * or no input is available. However, this behavior
 * is not desirable in all cases (i.e., interactive
 * input against some sort of ambiguous newline
 * regex). To enable greedy setRecordSeparator regex consumption,
 * use <code>-Djawk.forceGreedyRS=true</code>.
 *
 * @author Danny Daglas
 */
public class PartitioningReader extends FilterReader {

	private static final boolean FORCE_GREEDY_RS;

	static {
		String grs = System.getProperty("jawk.forceGreedyRS", "0").trim();
		FORCE_GREEDY_RS = grs.equals("1") || grs.equalsIgnoreCase("yes") || grs.equalsIgnoreCase("true");
	}

	private Pattern rs;
	private Matcher matcher;
	private boolean fromFileNameList;

	/**
	 * Construct the partitioning reader.
	 *
	 * @param reader The reader containing the input data stream.
	 * @param recordSeparator The record separator, as a regular expression.
	 */
	public PartitioningReader(Reader reader, String recordSeparator) {
		this(reader, recordSeparator, false);
	}

	/**
	 * Construct the partitioning reader.
	 *
	 * @param r The reader containing the input data stream.
	 * @param recordSeparator The record separator, as a regular expression.
	 * @param fromFileNameList Whether the underlying input reader
	 *        is a file from the filename list (the parameters passed
	 *        into AWK after the script argument).
	 */
	public PartitioningReader(Reader r, String recordSeparator, boolean fromFileNameList) {
		super(r);
		this.fromFileNameList = fromFileNameList;
		setRecordSeparator(recordSeparator);
	}

	private String recordSeparator = null;
	private boolean consumeAll = false;

	/**
	 * Assign a new record separator for this partitioning reader.
	 *
	 * @param recordSeparator The new record separator, as a regular expression.
	 */
	public final void setRecordSeparator(String recordSeparator) {
		if (!recordSeparator.equals(this.recordSeparator)) {
			if ("".equals(recordSeparator)) {
				consumeAll = true;
				rs = Pattern.compile("\\z", Pattern.DOTALL | Pattern.MULTILINE);
			} else if ("\n".equals(recordSeparator)) {
				// Match \r?\n so that CRLF inputs produce clean records without trailing \r
				consumeAll = false;
				rs = Pattern.compile("\\r?\\n");
			} else if ("\r\n".equals(recordSeparator) || "\r".equals(recordSeparator)) {
				consumeAll = false;
				rs = Pattern.compile(recordSeparator, Pattern.LITERAL);
			} else {
				consumeAll = false;
				rs = Pattern.compile(recordSeparator, Pattern.DOTALL | Pattern.MULTILINE);
			}
			this.recordSeparator = recordSeparator;
		}
	}

	/**
	 * <p>
	 * fromFilenameList.
	 * </p>
	 *
	 * @return true whether the underlying input reader is from a
	 *         filename list argument; false otherwise
	 */
	public boolean fromFilenameList() {
		return fromFileNameList;
	}

	private StringBuilder remaining = new StringBuilder();
	private char[] readBuffer = new char[4096];

	/** {@inheritDoc} */
	@Override
	public int read(char[] b, int start, int len) throws IOException {
		int readChars = super.read(b, start, len);
		if (readChars >= 0) {
			remaining.append(b, start, readChars);
		}
		return readChars;
	}

	private boolean eof = false;

	/**
	 * Consume one record from the reader.
	 * It uses the record separator regular
	 * expression to mark start/end of records.
	 *
	 * @return the next record, null if no more records exist
	 * @throws java.io.IOException upon an IO error
	 */
	public String readRecord() throws IOException {
		while (consumeAll || eof || remaining.length() == 0 || !findRecordSeparator()) {
			int len = read(readBuffer, 0, readBuffer.length);
			if (eof || (len < 0)) {
				eof = true;
				String retVal = remaining.toString();
				remaining.setLength(0);
				if (retVal.length() == 0) {
					return null;
				} else {
					return retVal;
				}
			} else if (len == 0) {
				throw new RuntimeException("len == 0 ?!");
			}
		}

		// if force greedy regex consumption:
		if (FORCE_GREEDY_RS) {
			// attempt to move last match away from the end of the input
			// so that buffer bounderies landing in the middle of
			// regexp matches that *could* match the regexp if more chars
			// were read
			// (one char at a time!)
			while (matcher.find() && matcher.end() == remaining.length() && matcher.requireEnd()) {
				if (read(readBuffer, 0, 1) >= 0) {
					if (!findRecordSeparator()) {
						break;
					}
				} else {
					break;
				}
			}
		}

		// we have a record separator!

		String retVal = remaining.substring(0, matcher.start());
		remaining.delete(0, matcher.end());
		return retVal;
	}

	/**
	 * Finds the next record separator match that consumes at least one
	 * character. Zero-length regular-expression matches are ignored because
	 * using them as separators would not advance the reader and would loop
	 * forever on nullable regexes such as {@code ()}.
	 *
	 * @return {@code true} when a non-empty record separator match was found
	 */
	private boolean findRecordSeparator() {
		if (matcher == null) {
			matcher = rs.matcher(remaining);
		} else {
			matcher.reset(remaining);
		}
		while (matcher.find()) {
			if (matcher.start() != matcher.end()) {
				return true;
			}
		}
		return false;
	}
}
