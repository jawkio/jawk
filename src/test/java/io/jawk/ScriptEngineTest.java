package io.jawk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.junit.Test;

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
}
