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
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.ExtensionFunction;
import org.metricshub.jawk.ext.ExtensionRegistry;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.frontend.AwkParser;
import org.metricshub.jawk.frontend.AstNode;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.jrt.InputSource;
import org.metricshub.jawk.jrt.StreamInputSource;
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
	 * The behavioral settings used by this engine instance.
	 */
	private final AwkSettings settings;

	/**
	 * The last parsed {@link AstNode} produced during compilation.
	 */
	private AstNode lastAst;

	/**
	 * Create a new instance of Awk without extensions.
	 */
	public Awk() {
		this(new AwkSettings());
	}

	/**
	 * Create a new instance of Awk with the specified settings.
	 *
	 * @param settings behavioral configuration for this engine
	 */
	public Awk(AwkSettings settings) {
		this(ExtensionSetup.EMPTY, settings);
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
	 * Create a new instance of Awk with the specified extension instances
	 * and settings.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 * @param settings behavioral configuration for this engine
	 */
	public Awk(Collection<? extends JawkExtension> extensions, AwkSettings settings) {
		this(createExtensionSetup(extensions), settings);
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
		this(setup, new AwkSettings());
	}

	protected Awk(ExtensionSetup setup, AwkSettings settings) {
		this.extensionFunctions = setup.functions;
		this.extensionInstances = setup.instances;
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	protected Map<String, ExtensionFunction> getExtensionFunctions() {
		return extensionFunctions;
	}

	protected Map<String, JawkExtension> getExtensionInstances() {
		return extensionInstances;
	}

	/**
	 * Returns the behavioral settings associated with this engine instance.
	 *
	 * @return the {@link AwkSettings} used by this instance, never {@code null}
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP")
	public AwkSettings getSettings() {
		return settings;
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
	 * Compiles and invokes a single {@link ScriptSource} using the given
	 * {@link InputSource}.
	 *
	 * @param script script source to compile and run
	 * @param inputSource the input source providing records
	 * @throws IOException if an I/O error occurs during compilation or
	 *         execution
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void invoke(ScriptSource script, InputSource inputSource)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		AwkTuples tuples = compile(Collections.singletonList(script));
		invoke(tuples, inputSource, Collections.emptyList(), null);
	}

	/**
	 * Compiles and invokes a single {@link ScriptSource} reading input from the
	 * supplied {@link InputStream}.
	 *
	 * @param script script source to compile and run
	 * @param inputStream the input stream to read from
	 * @throws IOException if an I/O error occurs during compilation or execution
	 * @throws ClassNotFoundException if intermediate code cannot be loaded
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void invoke(ScriptSource script, InputStream inputStream)
			throws IOException,
			ClassNotFoundException,
			ExitException {
		AwkTuples tuples = compile(Collections.singletonList(script));
		invoke(tuples, inputStream, Collections.emptyList(), null);
	}

	/**
	 * Interprets precompiled {@link AwkTuples} using the supplied
	 * {@link InputSource}, command-line arguments, and per-call variable
	 * overrides.
	 *
	 * @param tuples precompiled tuples to interpret
	 * @param inputSource the input source providing records
	 * @param arguments name=value or filename entries (ARGV)
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @throws IOException upon an IO error
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void invoke(
			AwkTuples tuples,
			InputSource inputSource,
			List<String> arguments,
			Map<String, Object> variableOverrides)
			throws IOException,
			ExitException {
		if (tuples == null) {
			return;
		}
		Objects.requireNonNull(inputSource, "inputSource");

		try (AVM avm = createAvm()) {
			avm.interpret(tuples, inputSource, arguments, variableOverrides);
		}
	}

	/**
	 * Interprets precompiled {@link AwkTuples} reading input from the supplied
	 * {@link InputStream}. The stream is automatically wrapped in a
	 * {@link StreamInputSource} that provides standard AWK file-list traversal.
	 *
	 * @param tuples precompiled tuples to interpret
	 * @param inputStream the input stream to read from
	 * @param arguments name=value or filename entries (ARGV)
	 * @throws IOException upon an IO error
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void invoke(AwkTuples tuples, InputStream inputStream, List<String> arguments)
			throws IOException,
			ExitException {
		invoke(tuples, inputStream, arguments, null);
	}

	/**
	 * Interprets precompiled {@link AwkTuples} reading input from the supplied
	 * {@link InputStream}, with per-call variable overrides. The stream is
	 * automatically wrapped in a {@link StreamInputSource} that provides
	 * standard AWK file-list traversal.
	 *
	 * @param tuples precompiled tuples to interpret
	 * @param inputStream the input stream to read from (must not be {@code null};
	 *        pass {@code System.in} for standard input or
	 *        {@code new ByteArrayInputStream(new byte[0])} for no input)
	 * @param arguments name=value or filename entries (ARGV)
	 * @param variableOverrides additional variable assignments (may be {@code null})
	 * @throws IOException upon an IO error
	 * @throws ExitException if the script terminates with a non-zero exit code
	 */
	public void invoke(
			AwkTuples tuples,
			InputStream inputStream,
			List<String> arguments,
			Map<String, Object> variableOverrides)
			throws IOException,
			ExitException {
		if (tuples == null) {
			return;
		}

		Objects.requireNonNull(inputStream, "inputStream");

		try (AVM avm = createAvm()) {
			InputSource source = new StreamInputSource(inputStream, avm, avm.getJrt());
			avm.interpret(tuples, source, arguments, variableOverrides);
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
	 * Internal method that creates an input source from the stream and executes
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

		InputStream effectiveInput = inputStream != null ? inputStream : new ByteArrayInputStream(new byte[0]);

		AwkSettings runSettings = new AwkSettings();
		if (textInput) {
			runSettings.setDefaultRS("\n");
			runSettings.setDefaultORS("\n");
		}
		runSettings
				.setOutputStream(
						new PrintStream(
								outputStream,
								false,
								StandardCharsets.UTF_8.name()));

		ScriptSource script = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				scriptReader);

		// Use a temporary Awk instance with the run-specific settings
		// while keeping the same extensions
		Awk runAwk = new Awk(new ExtensionSetup(this.extensionFunctions, this.extensionInstances), runSettings);
		AwkTuples tuples = runAwk.compile(Collections.singletonList(script));
		try {
			runAwk.invoke(tuples, effectiveInput, Collections.emptyList());
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
	AwkTuples compile(String script, boolean disableOptimizeParam) throws IOException {
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
	AwkTuples compile(Reader script, boolean disableOptimizeParam) throws IOException {
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
	AwkTuples compile(List<ScriptSource> scripts, boolean disableOptimizeParam)
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
				ast.populateTuples(tuples);
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
		return eval(compileForEval(expression));
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
		return eval(compileForEval(expression), input);
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
		return eval(compileForEval(expression), source);
	}

	/**
	 * Evaluates pre-compiled tuples without input.
	 *
	 * @param tuples tuples returned by {@link Awk#compileForEval(String)}
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(AwkTuples tuples) throws IOException {
		return eval(tuples, (String) null);
	}

	/**
	 * Evaluates pre-compiled tuples using a text input value exposed as {@code $0}.
	 * <p>
	 * Each invocation creates, prepares, and closes a fresh runtime so the
	 * evaluation is isolated from previous calls.
	 * </p>
	 *
	 * @param tuples Tuples returned by {@link Awk#compileForEval(String)}
	 * @param input Optional text input (that will be available as $0, and tokenized
	 *        as $1, $2, etc.)
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(AwkTuples tuples, String input) throws IOException {
		AwkTuples compiledTuples = Objects.requireNonNull(tuples, "tuples");
		try (AVM activeEvalAvm = createAvm(settings)) {
			InputSource source = new SingleRecordInputSource(input);
			return activeEvalAvm.eval(compiledTuples, source);
		}
	}

	/**
	 * Evaluates pre-compiled tuples using a structured {@link InputSource} to
	 * populate {@code $0}, {@code $1}, etc.
	 * <p>
	 * Each invocation creates, prepares, and closes a fresh runtime so the
	 * evaluation is isolated from previous calls.
	 * </p>
	 *
	 * @param tuples Tuples returned by {@link Awk#compileForEval(String)}
	 * @param source structured input source providing the current record
	 * @return the value of the specified expression
	 * @throws IOException if anything goes wrong with the evaluation
	 */
	public Object eval(AwkTuples tuples, InputSource source) throws IOException {
		AwkTuples compiledTuples = Objects.requireNonNull(tuples, "tuples");
		InputSource resolvedSource = Objects.requireNonNull(source, "source");
		try (AVM activeEvalAvm = createAvm(settings)) {
			return activeEvalAvm.eval(compiledTuples, resolvedSource);
		}
	}

	/**
	 * Prepares one text record for repeated expression evaluation and returns the
	 * mutable {@link AVM} that will execute those expressions.
	 * <p>
	 * The returned {@link AVM} is created using the current runtime
	 * configuration of this {@link Awk} instance and binds the provided record
	 * once. Later calls to
	 * {@link AVM#eval(AwkTuples)} reuse the same AVM state without resetting it
	 * between expressions, so mutations intentionally leak across evaluations.
	 * This is the high-level convenience wrapper around direct
	 * {@link AVM#prepareForEval(String)} and {@link AVM#eval(AwkTuples)} usage.
	 * </p>
	 *
	 * @param input non-null text record to expose as {@code $0}
	 *        Call {@link AVM#close()} when you are done with the returned interpreter.
	 * @return prepared AVM ready for repeated {@link AVM#eval(AwkTuples)} calls
	 * @throws IOException if binding the record fails
	 */
	public AVM prepareEval(String input) throws IOException {
		String resolvedInput = Objects.requireNonNull(input, "input");
		AVM evalAvm = createAvm(settings);
		try {
			evalAvm.prepareForEval(resolvedInput);
			return evalAvm;
		} catch (IOException | RuntimeException e) {
			try {
				evalAvm.close();
			} catch (IOException closeException) {
				e.addSuppressed(closeException);
			}
			throw e;
		}
	}

	/**
	 * Prepares the first available record from a structured {@link InputSource}
	 * for repeated expression evaluation and returns the mutable {@link AVM}
	 * that will execute those expressions.
	 * <p>
	 * The returned AVM remains attached to the provided source, so later
	 * {@code getline} operations and repeated {@link AVM#prepareForEval(InputSource)}
	 * calls continue from that source's current position. Later
	 * {@link AVM#eval(AwkTuples)} calls reuse the same AVM state without
	 * resetting it between expressions, so mutations intentionally leak across
	 * evaluations. Close the returned AVM when you are done with it to release
	 * any bound input or runtime I/O resources.
	 * </p>
	 *
	 * @param source structured source providing the record to bind
	 * @return prepared AVM ready for repeated {@link AVM#eval(AwkTuples)} calls
	 * @throws IOException if reading the record fails or the source is exhausted
	 */
	public AVM prepareEval(InputSource source) throws IOException {
		InputSource resolvedSource = Objects.requireNonNull(source, "source");
		AVM evalAvm = createAvm(settings);
		try {
			if (!evalAvm.prepareForEval(resolvedSource)) {
				throw new IOException("No record available from source.");
			}
			return evalAvm;
		} catch (IOException | RuntimeException e) {
			try {
				evalAvm.close();
			} catch (IOException closeException) {
				e.addSuppressed(closeException);
			}
			throw e;
		}
	}

	protected AwkTuples createTuples() {
		return new AwkTuples();
	}

	protected AVM createAvm() {
		return createAvm(this.settings);
	}

	protected AVM createAvm(AwkSettings settingsParam) {
		return new AVM(settingsParam, this.extensionInstances);
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
	 * Lists metadata for the {@link JawkExtension} implementations discovered on
	 * the class path.
	 *
	 * @return list of discovered extension descriptors
	 */
	public static Map<String, JawkExtension> listAvailableExtensions() {
		return ExtensionRegistry.listExtensions();
	}

	private static final class SingleRecordInputSource implements InputSource {

		private final String record;

		private boolean consumed;

		private SingleRecordInputSource(String record) {
			this.record = record;
		}

		@Override
		public boolean nextRecord() {
			if (consumed || record == null) {
				return false;
			}
			consumed = true;
			return true;
		}

		@Override
		public String getRecordText() {
			return consumed ? record : null;
		}

		@Override
		public List<String> getFields() {
			return null;
		}

		@Override
		public boolean isFromFilenameList() {
			return false;
		}
	}

}
