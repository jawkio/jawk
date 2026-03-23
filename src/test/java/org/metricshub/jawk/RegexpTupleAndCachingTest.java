package org.metricshub.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Test;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.Cli;

public class RegexpTupleAndCachingTest {

	@Test
	public void literalRegexpEmitsPrecompiledPattern() throws Exception {
		String script = "BEGIN { print (\"abc\" ~ /a.c/) }\n";

		AwkTestSupport
				.awkTest("literal regex matches and is precompiled")
				.script(script)
				.expect("1\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		String dump = dumpTuples(tuples);
		assertTrue(
				"Tuple dump should include precompiled pattern",
				dump.contains("REGEXP, \"a.c\", /a.c/"));
	}

	@Test
	public void variableRegexpCompilesAtRuntime() throws Exception {
		String script = "BEGIN { r = \"a.c\"; print (\"abc\" ~ r) }\n";

		AwkTestSupport
				.awkTest("variable regex compiles dynamically")
				.script(script)
				.expect("1\n")
				.runAndAssert();

		AwkTuples tuples = new Awk().compile(script);
		String dump = dumpTuples(tuples);
		assertFalse("Dynamic regex should not emit REGEXP tuple", dump.contains("REGEXP"));
	}

	@Test
	public void serializedTuplesPreservePrecompiledPattern() throws Exception {
		String script = "BEGIN { print (\"abc\" ~ /a.c/) }\n";
		AwkTuples tuples = new Awk().compile(script);

		File tmp = File.createTempFile("jawk", ".tpl");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp))) {
			oos.writeObject(tuples);
		}

		Cli cli = Cli.parseCommandLineArguments(new String[] { "-L", tmp.getAbsolutePath() });
		AwkSettings settings = cli.getSettings();
		settings.setDefaultRS("\n");
		settings.setDefaultORS("\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		settings.setOutputStream(new PrintStream(out, false, StandardCharsets.UTF_8.name()));

		new Awk(settings)
				.invoke(
						cli.getPrecompiledTuples(),
						new ByteArrayInputStream(new byte[0]),
						Collections.emptyList());

		// Should still match and print 1 using the serialized pattern
		assertEquals("1\n", out.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	public void serializedEvalTuplesPreserveSetNumGlobalsOptimization() throws Exception {
		AwkTuples tuples = new Awk().compileForEval("NF \":\" $2");
		assertFalse("Field-only eval tuples should omit SET_NUM_GLOBALS", dumpTuples(tuples).contains("SET_NUM_GLOBALS"));

		AwkTuples deserialized = roundTrip(tuples);

		assertFalse(
				"Serialized eval tuples should keep the SET_NUM_GLOBALS optimization",
				dumpTuples(deserialized).contains("SET_NUM_GLOBALS"));
		assertEquals("2:right", new Awk().eval(deserialized, "left right"));
	}

	private static String dumpTuples(AwkTuples tuples) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
			tuples.dump(ps);
		}
		return out.toString(StandardCharsets.UTF_8.name());
	}

	private static AwkTuples roundTrip(AwkTuples tuples) throws Exception {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(serialized)) {
			oos.writeObject(tuples);
		}
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized.toByteArray()))) {
			return (AwkTuples) ois.readObject();
		}
	}
}
