package io.jawk.jsr223;

import java.io.ByteArrayInputStream;
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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import io.jawk.Awk;
import io.jawk.ExitException;
import io.jawk.backend.AVM;
import io.jawk.jrt.AppendableAwkSink;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.StreamInputSource;
import io.jawk.util.AwkSettings;

/**
 * Simple JSR-223 script engine for Jawk that delegates execution to the
 * {@link Awk} runtime.
 */
public class JawkScriptEngine extends AbstractScriptEngine {

	private final ScriptEngineFactory factory;

	/**
	 * Creates a new script engine instance.
	 *
	 * @param factory the owning {@link ScriptEngineFactory}
	 */
	public JawkScriptEngine(ScriptEngineFactory factory) {
		this.factory = factory;
	}

	/** {@inheritDoc} */
	@Override
	public Object eval(Reader scriptReader, ScriptContext context) throws ScriptException {
		try {
			InputStream input;
			Object inObj = context.getAttribute("input");
			if (inObj instanceof InputStream) {
				input = (InputStream) inObj;
			} else if (inObj instanceof String) {
				input = new ByteArrayInputStream(((String) inObj).getBytes(StandardCharsets.UTF_8));
			} else {
				// No explicit input provided; default to System.in to preserve
				// the standard AWK behavior of reading from stdin.
				input = System.in;
			}
			AwkSettings settings = new AwkSettings();
			settings.setDefaultRS("\n");
			settings.setDefaultORS("\n");
			StringWriter result = new StringWriter();
			Awk awk = new Awk(settings);
			Map<String, Object> variableOverrides = extractVariableOverrides(context);
			try (AVM avm = awk.createAvm()) {
				avm.setAwkSink(new AppendableAwkSink(result, settings.getLocale()));
				InputSource inputSource = new StreamInputSource(input, avm, avm.getJrt());
				avm
						.execute(
								awk.compile(scriptReader),
								inputSource,
								Collections.<String>emptyList(),
								variableOverrides);
			}
			String out = result.toString();
			Writer writer = context.getWriter();
			if (writer != null) {
				// Write result to the script context's writer if provided
				writer.write(out);
				writer.flush();
			}
			return out;
		} catch (ExitException e) {
			if (e.getCode() != 0) {
				throw new ScriptException(e);
			}
			return "";
		} catch (Exception e) {
			throw new ScriptException(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public Object eval(String script, ScriptContext context) throws ScriptException {
		return eval(new StringReader(script), context);
	}

	/** {@inheritDoc} */
	@Override
	public Bindings createBindings() {
		return new SimpleBindings();
	}

	/** {@inheritDoc} */
	@Override
	public ScriptEngineFactory getFactory() {
		return factory;
	}

	private static Map<String, Object> extractVariableOverrides(ScriptContext context) {
		Map<String, Object> variables = new HashMap<String, Object>();
		mergeBindings(variables, context, ScriptContext.GLOBAL_SCOPE);
		mergeBindings(variables, context, ScriptContext.ENGINE_SCOPE);
		variables.remove("input");
		return variables;
	}

	private static void mergeBindings(Map<String, Object> target, ScriptContext context, int scope) {
		Bindings bindings = context.getBindings(scope);
		if (bindings != null) {
			for (Map.Entry<String, Object> entry : bindings.entrySet()) {
				if (isSupportedBindingValue(entry.getValue())) {
					target.put(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	private static boolean isSupportedBindingValue(Object value) {
		return value instanceof String || value instanceof Number || value instanceof Map;
	}
}
