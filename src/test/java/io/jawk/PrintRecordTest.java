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

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import io.jawk.jrt.AwkSink;
import org.junit.Test;

/**
 * Tests for {@link AwkSink#printRecord(String, String, String, List, Object)},
 * which a {@code print} without arguments to the default output calls with the
 * current fields.
 */
public class PrintRecordTest {

	/**
	 * Text sink that writes the fields of every bare {@code print} as
	 * {@code <$1|$2|...>} before the record itself.
	 */
	private static final class FieldsSink extends AwkSink {

		private final Appendable out;
		private final AwkSink text;

		FieldsSink(Appendable outParam) {
			this.out = outParam;
			this.text = AwkSink.from(outParam);
		}

		@Override
		public void print(String ofs, String ors, String ofmt, Object... values) throws IOException {
			text.print(ofs, ors, ofmt, values);
		}

		@Override
		public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values)
				throws IOException {
			text.printf(ofs, ors, ofmt, convfmt, format, values);
		}

		@Override
		public void printRecord(String ofs, String ors, String ofmt, List<Object> fields, Object record)
				throws IOException {
			StringBuilder cells = new StringBuilder("<");
			for (int i = 0; i < fields.size(); i++) {
				if (i > 0) {
					cells.append('|');
				}
				cells.append(formatPrintArgument(fields.get(i), ofmt));
			}
			out.append(cells.append('>'));
			super.printRecord(ofs, ors, ofmt, fields, record);
		}

		@Override
		public void flush() throws IOException {
			text.flush();
		}

		@Override
		public PrintStream getPrintStream() {
			return text.getPrintStream();
		}
	}

	private static AwkTestSupport.AwkTestBuilder fieldsTest(String description) {
		return AwkTestSupport.awkTest(description).withSink(FieldsSink::new);
	}

	@Test
	public void barePrintPassesTheFieldsAsRead() throws Exception {
		fieldsTest("bare print passes the fields as read").script("{ print }")
				.stdin("a b c\n  d   e  \n")
				.expect("<a|b|c>a b c\n<d|e>  d   e  \n")
				.runAndAssert();
	}

	@Test
	public void patternWithoutActionPassesTheFields() throws Exception {
		fieldsTest("pattern without action").script("NF > 1").stdin("a b\nc\n").expect("<a|b>a b\n").runAndAssert();
	}

	@Test
	public void barePrintToDevStdoutPrintsTheRecord() throws Exception {
		fieldsTest("bare print > \"/dev/stdout\" prints $0").script("{ print > \"/dev/stdout\" }")
				.stdin("a b\n")
				.expect("a b\n")
				.runAndAssert();
	}

	@Test
	public void barePrintPassesAnAssignedField() throws Exception {
		fieldsTest("bare print after $3 = \"x\"").script("{ $3 = \"x\"; print }")
				.stdin("a b c d\n")
				.expect("<a|b|x|d>a b x d\n")
				.runAndAssert();
	}

	@Test
	public void barePrintPassesTheFieldsUpToAReducedNF() throws Exception {
		fieldsTest("bare print after NF = 2").script("{ NF = 2; print }")
				.stdin("a b c d\n")
				.expect("<a|b>a b\n")
				.runAndAssert();
	}

	@Test
	public void barePrintPassesEmptyFieldsUpToAFieldAssignedBeyondNF() throws Exception {
		fieldsTest("bare print after $5 assigned beyond NF").script("{ $5 = \"e\"; print }")
				.stdin("a b c\n")
				.expect("<a|b|c||e>a b c  e\n")
				.runAndAssert();
	}

	@Test
	public void barePrintRendersNumericFieldsLikePrintField() throws Exception {
		// A field assigned a number renders with OFMT, as "print $2" does, while
		// $0 is rebuilt with CONVFMT.
		fieldsTest("bare print renders numeric fields with OFMT")
				.script("BEGIN { OFMT = \"%.2f\" } { $2 = 3.14159; $3 = 2 * 21; print; print $2 }")
				.stdin("a b c\n")
				.expect("<a|3.14|42>a 3.14159 42\n3.14\n")
				.runAndAssert();
	}

	@Test
	public void barePrintInEndPassesTheLastRecord() throws Exception {
		fieldsTest("bare print in END").script("END { print }")
				.stdin("a b\nc d e\n")
				.expect("<c|d|e>c d e\n")
				.runAndAssert();
	}

	@Test
	public void barePrintFollowsPlainGetlineButNotGetlineVar() throws Exception {
		fieldsTest("bare print after getline var and plain getline")
				.script("NR == 1 { getline line; print; getline; print }")
				.stdin("a b\nc d e\nf\n")
				.expect("<a|b>a b\n<f>f\n")
				.runAndAssert();
	}

	@Test
	public void barePrintInBeginPassesNoFields() throws Exception {
		fieldsTest("bare print in BEGIN").script("BEGIN { print }").expect("<>\n").runAndAssert();
	}

	@Test
	public void printWithArgumentsPassesTheArguments() throws Exception {
		fieldsTest("print $0 and print with arguments do not pass the fields")
				.script("{ print $0; print $1, $2; print($0) }")
				.stdin("a b c\n")
				.expect("a b c\na b\na b c\n")
				.runAndAssert();
	}

	@Test
	public void barePrintToAFilePrintsTheRecord() throws Exception {
		fieldsTest("bare print > file prints $0").script(
				"{ $2 = \"x\"; f = TEMPDIR \"/printRecordFile\"; print > f; close(f); " + "getline l < f; print \"file:\" l }")
				.withTempDir()
				.stdin("a b c\n")
				.expect("file:a x c\n")
				.runAndAssert();
	}

	@Test
	public void barePrintToAPipePrintsTheRecord() throws Exception {
		fieldsTest("bare print | cmd prints $0").script("{ $2 = \"x\"; print | \"cat\"; close(\"cat\") }")
				.posixOnly()
				.stdin("a b c\n")
				.expect("a x c\n")
				.runAndAssert();
	}

	@Test
	public void defaultSinkOutputOfBarePrintIsUnchanged() throws Exception {
		AwkTestSupport.awkTest("default sink: bare print writes $0")
				.script("BEGIN { OFS = \"-\"; ORS = \"|\\n\" } { print } { $5 = 1.5; NF = 6; print; print $0 } END { print }")
				.stdin("a  b c\n")
				.expect("a  b c|\na-b-c--1.5-|\na-b-c--1.5-|\na-b-c--1.5-|\n")
				.runAndAssert();
	}
}
