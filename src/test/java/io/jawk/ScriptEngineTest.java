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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import io.jawk.util.JawkVersion;

public class ScriptEngineTest {

	@Test
	public void testJawkScriptEngine() throws Exception {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("jawk");
		assertNotNull("Jawk ScriptEngine not found", engine);

		String script = "{ print toupper($0) }";
		String input = "hello world";

		Bindings bindings = engine.createBindings();
		bindings.put("input", new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

		StringWriter result = new StringWriter();
		engine.getContext().setWriter(new PrintWriter(result));

		engine.eval(script, bindings);

		assertEquals("HELLO WORLD\n", result.toString());
	}

	@Test
	public void testJawkScriptEngineAcceptsListBinding() throws Exception {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("jawk");
		assertNotNull("Jawk ScriptEngine not found", engine);

		Bindings bindings = engine.createBindings();
		bindings.put("values", Arrays.asList("aaa", "bbb", "ccc"));

		StringWriter result = new StringWriter();
		engine.getContext().setWriter(new PrintWriter(result));

		engine.eval("BEGIN { print values[1] }", bindings);

		assertEquals("bbb\n", result.toString());
	}

	@Test
	public void factoryReportsTheRunningJawkVersion() {
		ScriptEngineFactory factory = factory();

		// Issues #598 and #599: the factory used to answer with a literal
		// frozen at the 3.x line, so every host was told it ran a Jawk that
		// had not shipped in eleven releases
		assertNotEquals("3.3.06-SNAPSHOT", factory.getEngineVersion());
		assertEquals(JawkVersion.getVersion(), factory.getEngineVersion());
		assertEquals(factory.getEngineVersion(), factory.getParameter(ScriptEngine.ENGINE_VERSION));
	}

	@Test
	public void factoryReportsPosixAsTheLanguageVersion() {
		ScriptEngineFactory factory = factory();

		assertEquals("awk", factory.getLanguageName());
		assertEquals("POSIX", factory.getLanguageVersion());
		assertEquals(factory.getLanguageVersion(), factory.getParameter(ScriptEngine.LANGUAGE_VERSION));
	}

	/**
	 * Returns the factory of the Jawk engine, as a JSR 223 host reaches it.
	 *
	 * @return the discovered Jawk {@link ScriptEngineFactory}
	 */
	private static ScriptEngineFactory factory() {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("jawk");
		assertNotNull("Jawk ScriptEngine not found", engine);
		return engine.getFactory();
	}
}
