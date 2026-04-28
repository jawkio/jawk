package io.jawk.site;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ڇڇڇڇڇڇ
 * Copyright (C) 2006 - 2026 MetricsHub
 * ڇڇڇڇڇڇ
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Generates an aggregate compatibility summary XML file from Failsafe XML reports.
 */
public final class CompatibilitySummaryGenerator {

	private CompatibilitySummaryGenerator() {
	}

	/**
	 * Generates a compatibility summary XML file.
	 *
	 * @param args The input report directory and output summary file path.
	 * @throws Exception if the summary cannot be generated.
	 */
	public static void main(final String[] args) throws Exception {

		if (args.length != 2) {
			throw new IllegalArgumentException("Expected arguments: <failsafe-report-dir> <output-file>");
		}

		final Path reportDirectory = Path.of(args[0]);
		final Path outputFile = Path.of(args[1]);

		Files.createDirectories(outputFile.getParent());

		final SuiteSummary posix = summarize(reportDirectory, "TEST-io.jawk.posix.");
		final SuiteSummary bwk = summarize(reportDirectory, "TEST-io.jawk.onetrueawk.");
		final SuiteSummary gawk = summarize(reportDirectory, "TEST-io.jawk.gawk.");

		final String xml = ""
				+ "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<compatibility-summary generatedAt=\"" + escape(Instant.now().toString()) + "\">\n"
				+ suiteXml("posix", "POSIX", posix)
				+ suiteXml("bwk", "BWK / One True Awk", bwk)
				+ suiteXml("gawk", "gawk", gawk)
				+ "</compatibility-summary>\n";

		Files.writeString(outputFile, xml, StandardCharsets.UTF_8);
	}

	private static SuiteSummary summarize(final Path reportDirectory, final String prefix) throws Exception {

		final File[] reports = reportDirectory.toFile().listFiles(
				file -> file.isFile() && file.getName().startsWith(prefix) && file.getName().endsWith(".xml"));

		if (reports == null || reports.length == 0) {
			throw new IOException("No Failsafe XML reports found for prefix " + prefix + " in " + reportDirectory);
		}

		Arrays.sort(reports, Comparator.comparing(File::getName));

		int tests = 0;
		int failures = 0;
		int errors = 0;
		int skipped = 0;
		final List<TestCaseSummary> testCases = new ArrayList<>();

		for (final File report : reports) {
			final Element root = parse(report.toPath()).getDocumentElement();
			tests += getIntAttribute(root, "tests");
			failures += getIntAttribute(root, "failures");
			errors += getIntAttribute(root, "errors");
			skipped += getIntAttribute(root, "skipped");
			collectTestCases(root, testCases);
		}

		return new SuiteSummary(tests, failures, errors, skipped,
				testCases,
				Arrays.stream(reports).map(File::getName).collect(Collectors.joining("|")));
	}

	private static void collectTestCases(final Element root, final List<TestCaseSummary> testCases) {
		for (int index = 0; index < root.getElementsByTagName("testcase").getLength(); index++) {
			final Element testCase = (Element) root.getElementsByTagName("testcase").item(index);
			testCases.add(new TestCaseSummary(testCase.getAttribute("classname"), testCase.getAttribute("name"),
					getStatus(testCase)));
		}
	}

	private static String getStatus(final Element testCase) {
		if (testCase.getElementsByTagName("error").getLength() > 0) {
			return "error";
		}
		if (testCase.getElementsByTagName("failure").getLength() > 0) {
			return "failure";
		}
		if (testCase.getElementsByTagName("skipped").getLength() > 0) {
			return "skipped";
		}
		return "pass";
	}

	private static Document parse(final Path file) throws Exception {
		final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setNamespaceAware(false);
		return documentBuilderFactory.newDocumentBuilder().parse(file.toFile());
	}

	private static int getIntAttribute(final Element element, final String attributeName) {
		return Integer.parseInt(element.getAttribute(attributeName));
	}

	private static String suiteXml(final String id, final String title, final SuiteSummary summary) {
		final StringBuilder builder = new StringBuilder();
		builder.append(String.format(Locale.ROOT,
				"  <suite id=\"%s\" title=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"%d\" skipped=\"%d\" passed=\"%d\" percent=\"%d\" files=\"%s\">%n",
				escape(id), escape(title), summary.tests, summary.failures, summary.errors, summary.skipped,
				summary.getPassed(), summary.getPercent(), escape(summary.files)));
		for (final TestCaseSummary testCase : summary.testCases) {
			builder.append(String.format(Locale.ROOT,
					"    <testcase classname=\"%s\" name=\"%s\" status=\"%s\"/>%n", escape(testCase.className),
					escape(testCase.name), escape(testCase.status)));
		}
		builder.append("  </suite>\n");
		return builder.toString();
	}

	private static String escape(final String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static final class SuiteSummary {
		private final int tests;
		private final int failures;
		private final int errors;
		private final int skipped;
		private final List<TestCaseSummary> testCases;
		private final String files;

		private SuiteSummary(final int tests, final int failures, final int errors, final int skipped,
				final List<TestCaseSummary> testCases,
				final String files) {
			this.tests = tests;
			this.failures = failures;
			this.errors = errors;
			this.skipped = skipped;
			this.testCases = testCases;
			this.files = files;
		}

		private int getPassed() {
			return tests - failures - errors - skipped;
		}

		private int getPercent() {
			return tests == 0 ? 0 : (getPassed() * 100) / tests;
		}
	}

	private static final class TestCaseSummary {
		private final String className;
		private final String name;
		private final String status;

		private TestCaseSummary(final String className, final String name, final String status) {
			this.className = className;
			this.name = name;
			this.status = status;
		}
	}
}
