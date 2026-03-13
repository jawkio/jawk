package org.metricshub.jawk.jsr223;

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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import org.metricshub.jawk.Awk;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;

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
				input = new ByteArrayInputStream(new byte[0]);
			}
			AwkSettings settings = new AwkSettings();
			settings.setDefaultRS("\n");
			settings.setDefaultORS("\n");
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			try {
				settings.setOutputStream(new PrintStream(result, false, StandardCharsets.UTF_8.name()));
			} catch (java.io.UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
			Awk awk = new Awk(settings);
			// Execute the AWK script using the configured settings
			awk
					.invoke(
							new ScriptSource(
									ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
									scriptReader),
							input);
			String out = result.toString(StandardCharsets.UTF_8.name());
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
}
