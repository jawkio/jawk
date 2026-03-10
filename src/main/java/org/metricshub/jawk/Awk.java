package org.metricshub.jawk;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.ExtensionFunction;
import org.metricshub.jawk.ext.ExtensionRegistry;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AstNode;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.jrt.InputSource;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;

/**
 * Entry point into the parsing, analysis, and execution
 * of a Jawk script.
 * This entry point is used both when Jawk is executed as a library and when
 * invoked from the command line.
 * <p>
 * The overall process to execute a Jawk script is as follows:
 * <ul>
 * <li>Parse the Jawk script, producing an abstract syntax tree.
 * <li>Traverse the abstract syntax tree, producing a list of
 * instruction tuples for the interpreter.
 * <li>Traverse the list of tuples, providing a runtime which
 * ultimately executes the Jawk script, <strong>or</strong>
 * Command-line parameters dictate which action is to take place.
 * </ul>
 * Two additional semantic checks on the syntax tree are employed
 * (both to resolve function calls for defined functions).
 * As a result, the syntax tree is traversed three times.
 * And the number of times tuples are traversed is depends
 * on whether interpretation or compilation takes place.
 * <p>
 * The engine does not enable any extensions automatically. Extensions can be
 * provided programmatically via the {@link Awk#Awk(Collection)} constructors or
 * via the command line when using the CLI entry point.
 *
 * @see org.metricshub.jawk.backend.AVM
 * @author Danny Daglas
 */
public class Awk {

	private final Map<String, ExtensionFunction> extensionFunctions;

	private final Map<String, JawkExtension> extensionInstances;

	/**
	 * The last parsed {@link AstNode} produced during compilation.
	 */
	private AstNode lastAst;

	/**
	 * Create a new instance of Awk without extensions
	 */
	public Awk() {
		this(ExtensionSetup.EMPTY);
	}

	/**
	 * Create a new instance of Awk with the specified extension instances.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 */
	public Awk(Collection<? extends JawkExtension> extensions) {
		this(createExtensionSetup(extensions));
	}

	/**
	 * Create a new instance of Awk with the specified extension instances.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 */
	@SafeVarargs
	public Awk(JawkExtension... extensions) {
		this(createExtensionSetup(Arrays.asList(extensions)));
	}

	protected Awk(ExtensionSetup setup) {
		this.extensionFunctions = setup.functions;
		this.extensionInstances = setup.instances;
	}

	protected Map<String, ExtensionFunction> getExtensionFunctions() {
		return extensionFunctions;
	}

	protected Map<String, JawkExtension> getExtensionInstances() {
		return extensionInstances;
	}

	static Map<String, ExtensionFunction> createExtensionFunctionMap(Collection<? extends JawkExtension> extensions) {
		return createExtensionSetup(extensions).functions;
	}

	static Map<String, JawkExtension> createExtensionInstanceMap(Collection<? extends JawkExtension> extensions) {
		return createExtensionSetup(extensions).instances;
	}

	static Map<String, ExtensionFunction> createExtensionFunctionMap(JawkExtension... extensions) {
		if (extensions == null || extensions.length == 0) {
			return ExtensionSetup.EMPTY.functions;
		}
		return createExtensionFunctionMap(Arrays.asList(extensions));
	}

	static Map<String, JawkExtension> createExtensionInstanceMap(JawkExtension... extensions) {
		if (extensions == null || extensions.length == 0) {
			return ExtensionSetup.EMPTY.instances;
		}
		return createExtensionInstanceMap(Arrays.asList(extensions));
	}

	private static ExtensionSetup createExtensionSetup(Collection<? extends JawkExtension> extensions) {
		if (extensions == null || extensions.isEmpty()) {
			return ExtensionSetup.EMPTY;
		}
		Map<String, ExtensionFunction> keywordMap = new LinkedHashMap<String, ExtensionFunction>();
		Map<String, JawkExtension> instanceMap = new LinkedHashMap<String, JawkExtension>();
		for (JawkExtension extension : extensions) {
			if (extension == null) {
				throw new IllegalArgumentException("Extension instance must not be null");
			}
			String className = extension.getClass().getName();
			JawkExtension previousInstance = instanceMap.putIfAbsent(className, extension);
			if (previousInstance != null) {
				throw new IllegalArgumentException(
						"Extension class '" + className + "' was provided multiple times");
			}
			for (Map.Entry<String, ExtensionFunction> entry : extension.getExtensionFunctions().entrySet()) {
				String keyword = entry.getKey();
				ExtensionFunction previous = keywordMap.putIfAbsent(keyword, entry.getValue());
				if (previous != null) {
					throw new IllegalArgumentException(
							"Keyword '" + keyword + "' already provided by another extension");
				}
			}
		}
		return new ExtensionSetup(
				Collections.unmodifiableMap(keywordMap),
				Collections.unmodifiableMap(instanceMap));
	}

	private static final class ExtensionSetup {

		private static final ExtensionSetup EMPTY = new ExtensionSetup(
				Collections.<String, ExtensionFunction>emptyMap(),
				Collections.<String, JawkExtension>emptyMap());

		private final Map<String, ExtensionFunction> functions;
		private final Map<String, JawkExtension> instances;

		private ExtensionSetup(Map<String, ExtensionFunction> functionsParam,
				Map<String, JawkExtension> instancesParam) {
			this.functions = functionsParam;
			this.instances = instancesParam;
		}
	}

	/**
	 * Returns the last parsed AST produced by {@link #compile(List)}.
	 *
	 * @return the last {@link AstNode}, or {@code null} if no compilation occurred
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP")
	public AstNode getLastAst() {
		return lastAst;
	}

	/**
	 * Final empty finalizer to mitigate finalizer attacks flagged by SpotBugs.
	 * This prevents subclasses from introducing a finalizer that could run on a
	 * partially constructed instance if a constructor throws.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected final void finalize() { /* no-op */ }

	/**
	 * <p>
	 * invoke.
	 * </p>
	 *
	 * @param settings This tells AWK what to do
	 *        (where to get input from, where to write it to, in what mode to run,
	 *        ...)
	 * @throws java.io.IOException upon an IO error.
	 * @throws java.lang.ClassNotFoundException if intermediate code is specified
	 *         but deserialization fails to load in the JVM
	 * @throws org.metricshub.jawk.ExitException if interpretation is requested,
	 *         and a specific exit code is requested.
	 */
	public void invoke(String script, AwkSettings settings)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		invoke(
				new ScriptSource(
						ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
						new StringReader(script)),
				settings);
	}

	/**
	 * Compiles and invokes a single {@link ScriptSource} using the provided
	 * {@link AwkSettings}. This is a convenience overload for callers who have
	 * a single script to execute.
	 *
	 * @param script script source to compile and run
	 * @param settings runtime settings such as input and output streams
	 * @throws IOException if an I/O error occurs during compilation or
	 *         execution
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit
	 *         code
	 */
	public void invoke(ScriptSource script, AwkSettings settings)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		// Delegate to the List-based overload for the actual work
		invoke(Collections.singletonList(script), settings);
	}

	/**
	 * Compiles and invokes the specified list of {@link ScriptSource}s using the
	 * provided {@link AwkSettings}.
	 *
	 * @param scripts list of script sources to compile and run
	 * @param settings runtime settings such as input and output streams
	 * @throws IOException if an I/O error occurs during compilation or
	 *         execution
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit
	 *         code
	 */
	public void invoke(List<ScriptSource> scripts, AwkSettings settings)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		// Compile the scripts into tuples then execute them
		AwkTuples tuples = compile(scripts);
		invoke(tuples, settings);
	}

	/**
	 * Interprets the specified precompiled {@link AwkTuples} using the provided
	 * {@link AwkSettings}.
	 *
	 * @param tuples precompiled tuples to interpret
	 * @param settings runtime settings
	 * @throws IOException upon an IO error
	 * @throws ExitException if interpretation is requested, and a specific exit
	 *         code is requested
	 */
	public void invoke(AwkTuples tuples, AwkSettings settings)
			throws IOException,
			ExitException {
		if (tuples == null) {
			return;
		}

		AVM avm = null;
		try {
// interpret!
			avm = createAvm(settings);
			avm.interpret(tuples);
		} finally {
			if (avm != null) {
				avm.waitForIO();
			}
		}
	}

	/**
	 * Executes the specified AWK script against the given input and returns the
	 * printed output as a {@link String}.
	 *
	 * @param script AWK script to execute
	 * @param input text to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(String script, String input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the given input and writes the
	 * result to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute
	 * @param input text to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(String script, String input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(new StringReader(script), toInputStream(input), output, true);
	}

	/**
	 * Executes the specified AWK script against the given input and returns the
	 * printed output as a {@link String}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input text to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(Reader script, String input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the given input and writes the
	 * result to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input text to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(Reader script, String input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(script, toInputStream(input), output, true);
	}

	/**
	 * Executes the specified AWK script against the given input and returns the
	 * printed output as a {@link String}.
	 *
	 * @param script AWK script to execute
	 * @param input text reader to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(String script, Reader input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the given input and writes the
	 * result to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute
	 * @param input text reader to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(String script, Reader input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(new StringReader(script), toInputStream(input), output, true);
	}

	/**
	 * Executes the specified AWK script against the given input and returns the
	 * printed output as a {@link String}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input text reader to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(Reader script, Reader input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the given input and writes the
	 * result to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input text reader to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(Reader script, Reader input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(script, toInputStream(input), output, true);
	}

	/**
	 * Executes the specified AWK script against the given input file and returns
	 * the printed output as a {@link String}.
	 *
	 * @param script AWK script to execute
	 * @param input file containing text to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(String script, File input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		try (InputStream in = new FileInputStream(input)) {
			return run(script, in);
		}
	}

	/**
	 * Executes the specified AWK script against the given input file and writes
	 * the printed output to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute
	 * @param input file containing text to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(String script, File input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		try (InputStream in = new FileInputStream(input)) {
			run(script, in, output);
		}
	}

	/**
	 * Executes the specified AWK script against the given input file and returns
	 * the printed output as a {@link String}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input file containing text to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(Reader script, File input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		try (InputStream in = new FileInputStream(input)) {
			return run(script, in);
		}
	}

	/**
	 * Executes the specified AWK script against the given input file and writes
	 * the printed output to the provided {@link OutputStream}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input file containing text to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(Reader script, File input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		try (InputStream in = new FileInputStream(input)) {
			run(script, in, output);
		}
	}

	/**
	 * Executes the specified AWK script against the provided input stream and
	 * returns the printed output as a {@link String}.
	 *
	 * @param script AWK script to execute
	 * @param input stream to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(String script, InputStream input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the provided input stream and
	 * writes the result to the given {@link OutputStream}.
	 *
	 * @param script AWK script to execute
	 * @param input stream to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(String script, InputStream input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(new StringReader(script), input, output, false);
	}

	/**
	 * Executes the specified AWK script against the provided input stream and
	 * returns the printed output as a {@link String}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input stream to process
	 * @return result of the execution as a String
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public String run(Reader script, InputStream input)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		run(script, input, out);
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Executes the specified AWK script against the provided input stream and
	 * writes the result to the given {@link OutputStream}.
	 *
	 * @param script AWK script to execute (as a {@link Reader})
	 * @param input stream to process
	 * @param output destination for the printed output
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void run(Reader script, InputStream input, OutputStream output)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		run(script, input, output, false);
	}

	/**
	 * Internal method that configures default {@link AwkSettings} and executes
	 * the AWK script.
	 */
	private void run(
			Reader scriptReader,
			InputStream inputStream,
			OutputStream outputStream,
			boolean textInput)
			throws IOException,
			ClassNotFoundException,
			ExitException {

		AwkSettings settings = new AwkSettings();
		if (inputStream != null) {
			settings.setInput(inputStream);
		}

		if (textInput) {
			settings.setDefaultRS("\n");
			settings.setDefaultORS("\n");
		}

		settings
				.setOutputStream(
						new PrintStream(
								outputStream,
								false,
								StandardCharsets.UTF_8.name()));

		ScriptSource script = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				scriptReader);

		try {
			invoke(script, settings);
		} catch (ExitException e) {
			if (e.getCode() != 0) {
				throw e;
			}
		}
	}

	/**
	 * Compiles the specified AWK script and returns the intermediate representation
	 * as {@link AwkTuples}.
	 *
	 * @param script AWK script to compile
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs during compilation
	 */
	public AwkTuples compile(String script) throws IOException {
		return compile(script, false);
	}

	/**
	 * Compiles the specified AWK script and returns the intermediate representation
	 * as {@link AwkTuples}.
	 *
	 * @param script AWK script to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs during compilation
	 */
	public AwkTuples compile(String script, boolean disableOptimizeParam) throws IOException {
		ScriptSource source = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				new StringReader(script));
		return compile(Collections.singletonList(source), disableOptimizeParam);
	}

	/**
	 * Compiles the specified AWK script and returns the intermediate representation
	 * as {@link AwkTuples}.
	 *
	 * @param script AWK script to compile (as a {@link Reader})
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs during compilation
	 */
	public AwkTuples compile(Reader script) throws IOException {
		return compile(script, false);
	}

	/**
	 * Compiles the specified AWK script and returns the intermediate representation
	 * as {@link AwkTuples}.
	 *
	 * @param script AWK script to compile (as a {@link Reader})
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs during compilation
	 */
	public AwkTuples compile(Reader script, boolean disableOptimizeParam) throws IOException {
		ScriptSource source = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				script);
		return compile(Collections.singletonList(source), disableOptimizeParam);
	}

	/**
	 * Compiles a list of script sources into {@link AwkTuples} that can be
	 * interpreted by the {@link AVM} runtime.
	 *
	 * @param scripts script sources to compile
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs while reading the
	 *         scripts
	 */
	public AwkTuples compile(List<ScriptSource> scripts)
			throws IOException {
		return compile(scripts, false);
	}

	/**
	 * Compiles a list of script sources into {@link AwkTuples} that can be
	 * interpreted by the {@link AVM} runtime.
	 *
	 * @param scripts script sources to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled {@link AwkTuples}
	 * @throws IOException if an I/O error occurs while reading the
	 *         scripts
	 */
	public AwkTuples compile(List<ScriptSource> scripts, boolean disableOptimizeParam)
			throws IOException {

		lastAst = null;
		AwkTuples tuples = createTuples();
		if (!scripts.isEmpty()) {
			// Parse all script sources into a single AST
			AwkParser parser = new AwkParser(this.extensionFunctions);
			AstNode ast = parser.parse(scripts);
			lastAst = ast;
			if (ast != null) {
				// Perform semantic checks twice to resolve forward references
				ast.semanticAnalysis();
				ast.semanticAnalysis();
				// Build tuples from the AST
				int result = ast.populateTuples(tuples);
				assert result == 0;
				// Assign addresses and prepare tuples for interpretation
				tuples.postProcess();
				if (!disableOptimizeParam) {
					tuples.optimize();
				}
				// Record global variable offset mappings for the interpreter
				parser.populateGlobalVariableNameToOffsetMappings(tuples);
			}
		}

		return tuples;
	}

	/**
	 * Compile an expression to evaluate (not a full script).
	 *
	 * @param expression AWK expression to compile to AwkTuples
	 * @return AwkTuples to be interpreted by AVM
	 * @throws IOException if anything goes wrong with the compilation
	 */
	public AwkTuples compileForEval(String expression) throws IOException {
		return compileForEval(expression, false);
	}

	/**
	 * Compile an expression to evaluate (not a full script).
	 *
	 * @param expression AWK expression to compile to AwkTuples
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return AwkTuples to be interpreted by AVM
	 * @throws IOException if anything goes wrong with the compilation
	 */
	public AwkTuples compileForEval(String expression, boolean disableOptimizeParam) throws IOException {

		// Create a ScriptSource
		ScriptSource expressionSource = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				new StringReader(expression));

		// Parse the expression
		AwkParser parser = new AwkParser(this.extensionFunctions);
		AstNode ast = parser.parseExpression(expressionSource);

		// Create the tuples that we will return
		AwkTuples tuples = createTuples();

		// Attempt to traverse the syntax tree and build
		// the intermediate code
		if (ast != null) {
			// 1st pass to tie actual parameters to back-referenced formal parameters
			ast.semanticAnalysis();
			// 2nd pass to tie actual parameters to forward-referenced formal parameters
			ast.semanticAnalysis();
			// build tuples
			ast.populateTuples(tuples);
			// Calls touch(...) per Tuple so that addresses can be normalized/assigned/allocated
			tuples.postProcess();
			if (!disableOptimizeParam) {
				tuples.optimize();
			}
			// record global_var -> offset mapping into the tuples
			// so that the interpreter can assign variables
			parser.populateGlobalVariableNameToOffsetMappings(tuples);
		}

		return tuples;
	}

	/**
	 * Evaluates the specified AWK expression (not a full script, just an expression)
	 * and returns the value of this expression.
	 *
	 * @param expression Expression to evaluate (e.g. <code>2+3</code>)
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(String expression) throws IOException {
		return eval(expression, (String) null, null);
	}

	/**
	 * Evaluates the specified AWK expression (not a full script, just an expression)
	 * and returns the value of this expression.
	 *
	 * @param expression Expression to evaluate (e.g. <code>2+3</code> or <code>$2 "-" $3</code>
	 * @param input Optional text input (that will be available as $0, and tokenized as $1, $2, etc.)
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(String expression, String input) throws IOException {
		return eval(expression, input, null);
	}

	/**
	 * Evaluates the specified AWK expression (not a full script, just an expression)
	 * and returns the value of this expression.
	 *
	 * @param expression Expression to evaluate (e.g. <code>2+3</code> or <code>$2 "-" $3</code>
	 * @param input Optional text input (that will be available as $0, and tokenized as $1, $2, etc.)
	 * @param fieldSeparator Value of the FS global variable used for parsing the input
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(String expression, String input, String fieldSeparator) throws IOException {
		return eval(compileForEval(expression), input, fieldSeparator);
	}

	/**
	 * Evaluates the specified AWK tuples, i.e. the result of the execution of the
	 * TERNARY_EXPRESSION AST (the value that has been pushed in the stack).
	 *
	 * @param tuples Tuples returned by {@link Awk#compileForEval(String)}
	 * @param input Optional text input (that will be available as $0, and tokenized as $1, $2, etc.)
	 * @param fieldSeparator Value of the FS global variable used for parsing the input
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(AwkTuples tuples, String input, String fieldSeparator) throws IOException {

		AwkSettings settings = new AwkSettings();
		if (input != null) {
			settings.setInput(toInputStream(input));
		} else {
			settings.setInput(toInputStream(""));
		}

		settings.setDefaultRS("\n");
		settings.setDefaultORS("\n");
		settings.setFieldSeparator(fieldSeparator);

		settings
				.setOutputStream(
						new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8.name()));

		AVM avm = createAvm(settings);
		return avm.eval(tuples, input);
	}

	/**
	 * Evaluates the specified AWK expression using a structured {@link InputSource}
	 * to populate {@code $0}, {@code $1}, etc.
	 *
	 * @param expression Expression to evaluate (e.g. {@code $2 "-" $3})
	 * @param source structured input source providing the current record
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(String expression, InputSource source) throws IOException {
		return eval(expression, source, null);
	}

	/**
	 * Evaluates the specified AWK expression using a structured {@link InputSource}
	 * to populate {@code $0}, {@code $1}, etc.
	 *
	 * @param expression Expression to evaluate (e.g. {@code $2 "-" $3})
	 * @param source structured input source providing the current record
	 * @param fieldSeparator Value of the FS global variable (may be {@code null})
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(String expression, InputSource source, String fieldSeparator) throws IOException {
		return eval(compileForEval(expression), source, fieldSeparator);
	}

	/**
	 * Evaluates pre-compiled AWK tuples using a structured {@link InputSource}
	 * to populate {@code $0}, {@code $1}, etc.
	 *
	 * @param tuples Tuples returned by {@link Awk#compileForEval(String)}
	 * @param source structured input source providing the current record
	 * @param fieldSeparator Value of the FS global variable (may be {@code null})
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(AwkTuples tuples, InputSource source, String fieldSeparator) throws IOException {

		AwkSettings settings = new AwkSettings();
		settings.setInputSource(source);
		settings.setInput(new ByteArrayInputStream(new byte[0]));

		settings.setDefaultRS("\n");
		settings.setDefaultORS("\n");
		settings.setFieldSeparator(fieldSeparator);

		settings
				.setOutputStream(
						new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8.name()));

		AVM avm = createAvm(settings);
		return avm.eval(tuples, null);
	}

	protected AwkTuples createTuples() {
		return new AwkTuples();
	}

	protected AVM createAvm(AwkSettings settings) {
		return new AVM(settings, this.extensionInstances, this.extensionFunctions);
	}

	/**
	 * Converts a text input into an {@link InputStream} using UTF-8 encoding.
	 */
	private static InputStream toInputStream(String input) {
		if (input == null) {
			return new ByteArrayInputStream(new byte[0]);
		}
		return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Reads all characters from the supplied {@link Reader} and returns an
	 * {@link InputStream} containing the same data using UTF-8 encoding.
	 */
	private static InputStream toInputStream(Reader reader) throws IOException {
		if (reader == null) {
			return new ByteArrayInputStream(new byte[0]);
		}
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[4096];
		int len;
		while ((len = reader.read(buf)) != -1) {
			sb.append(buf, 0, len);
		}
		return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Lists metadata for the {@link JawkExtension} implementations discovered on
	 * the class path.
	 *
	 * @return list of discovered extension descriptors
	 */
	public static Map<String, JawkExtension> listAvailableExtensions() {
		return ExtensionRegistry.listExtensions();
	}

}
