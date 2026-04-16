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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.backend.AVM;
import io.jawk.ext.ExtensionFunction;
import io.jawk.ext.ExtensionRegistry;
import io.jawk.ext.JawkExtension;
import io.jawk.frontend.AwkParser;
import io.jawk.frontend.AstNode;
import io.jawk.jrt.AppendableAwkSink;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.OutputStreamAwkSink;
import io.jawk.jrt.StreamInputSource;
import io.jawk.util.AwkSettings;
import io.jawk.util.ScriptSource;

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
 * @see io.jawk.backend.AVM
 * @author Danny Daglas
 */
public class Awk {

	/** POSIX default field separator ({@code " "}). */
	public static final String DEFAULT_FS = " ";

	/** POSIX default record separator ({@code "\n"}). */
	public static final String DEFAULT_RS = "\n";

	/** POSIX default output field separator ({@code " "}). */
	public static final String DEFAULT_OFS = " ";

	/** POSIX default output record separator ({@code "\n"}). */
	public static final String DEFAULT_ORS = "\n";

	/** POSIX default number-to-string conversion format ({@code "%.6g"}). */
	public static final String DEFAULT_CONVFMT = "%.6g";

	/** POSIX default output number format ({@code "%.6g"}). */
	public static final String DEFAULT_OFMT = "%.6g";

	/** POSIX default subscript separator ({@code "\034"}). */
	public static final String DEFAULT_SUBSEP = String.valueOf((char) 28);

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
	 * Returns the last parsed AST produced by the most recent program compilation.
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
	 * Compiles a full AWK program.
	 *
	 * @param script AWK program source
	 * @return compiled immutable program
	 * @throws IOException if compilation fails
	 */
	public AwkProgram compile(String script) throws IOException {
		return compile(script, false);
	}

	/**
	 * Compiles a full AWK program.
	 *
	 * @param script AWK program source
	 * @return compiled immutable program
	 * @throws IOException if compilation fails
	 */
	public AwkProgram compile(Reader script) throws IOException {
		return compile(script, false);
	}

	/**
	 * Creates a reusable runtime backed by one {@link AVM} instance.
	 *
	 * @return reusable AVM
	 */
	public AVM createAvm() {
		return createAvm(this.settings);
	}

	/**
	 * Starts building a run request for a compiled AWK program.
	 * <p>
	 * Use the returned {@link AwkRunBuilder} to configure input, arguments,
	 * variables, and output, then call one of the terminal methods to execute.
	 * </p>
	 *
	 * <pre>{@code
	 * awk.script(program).input(stream).execute(mySink);
	 * String out = awk.script(program).input("hello").execute();
	 * }</pre>
	 *
	 * @param program compiled program to execute
	 * @return a builder for configuring and executing the run
	 */
	public AwkRunBuilder script(AwkProgram program) {
		return new AwkRunBuilder(Objects.requireNonNull(program, "program"));
	}

	/**
	 * Starts building a run request from an AWK script string.
	 * <p>
	 * The script is compiled and executed when a terminal method is called.
	 * Additional scripts can be appended by calling {@link AwkRunBuilder#script(String)}
	 * on the returned builder.
	 * </p>
	 *
	 * <pre>{@code
	 * String result = awk.script("{ print toupper($0) }").input("hello").execute();
	 * }</pre>
	 *
	 * @param scriptText AWK program source
	 * @return a builder for configuring and executing the run
	 */
	public AwkRunBuilder script(String scriptText) {
		return new AwkRunBuilder().script(Objects.requireNonNull(scriptText, "script"));
	}

	/**
	 * Evaluates a compiled expression using a fresh isolated runtime.
	 *
	 * @param expression compiled expression
	 * @return evaluated value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(AwkExpression expression) throws IOException {
		AwkExpression compiledExpression = Objects.requireNonNull(expression, "expression");
		try (AVM activeEvalAvm = createAvm(settings)) {
			return activeEvalAvm.eval(compiledExpression, new SingleRecordInputSource(null));
		}
	}

	/**
	 * Evaluates a compiled expression against one text record using a fresh
	 * isolated runtime.
	 *
	 * @param expression compiled expression
	 * @param input record exposed as {@code $0}
	 * @return evaluated value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(AwkExpression expression, String input) throws IOException {
		AwkExpression compiledExpression = Objects.requireNonNull(expression, "expression");
		try (AVM activeEvalAvm = createAvm(settings)) {
			return activeEvalAvm.eval(compiledExpression, new SingleRecordInputSource(input));
		}
	}

	/**
	 * Evaluates a compiled expression against one structured record source using a
	 * fresh isolated runtime.
	 *
	 * @param expression compiled expression
	 * @param source structured record source
	 * @return evaluated value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(AwkExpression expression, InputSource source) throws IOException {
		AwkExpression compiledExpression = Objects.requireNonNull(expression, "expression");
		InputSource resolvedSource = Objects.requireNonNull(source, "source");
		try (AVM activeEvalAvm = createAvm(settings)) {
			return activeEvalAvm.eval(compiledExpression, resolvedSource);
		}
	}

	/**
	 * Compiles the specified AWK script and returns an immutable AWK program.
	 *
	 * @param script AWK script to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled immutable program
	 * @throws IOException if an I/O error occurs during compilation
	 */
	AwkProgram compile(String script, boolean disableOptimizeParam) throws IOException {
		ScriptSource source = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				new StringReader(script));
		return compile(Collections.singletonList(source), disableOptimizeParam);
	}

	/**
	 * Compiles the specified AWK script and returns an immutable AWK program.
	 *
	 * @param script AWK script to compile (as a {@link Reader})
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled immutable program
	 * @throws IOException if an I/O error occurs during compilation
	 */
	AwkProgram compile(Reader script, boolean disableOptimizeParam) throws IOException {
		ScriptSource source = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				script);
		return compile(Collections.singletonList(source), disableOptimizeParam);
	}

	/**
	 * Compiles a list of script sources into an immutable AWK program that can be
	 * executed by the {@link AVM} runtime.
	 *
	 * @param scripts script sources to compile
	 * @return compiled immutable program
	 * @throws IOException if an I/O error occurs while reading the
	 *         scripts
	 */
	public AwkProgram compile(List<ScriptSource> scripts)
			throws IOException {
		return compile(scripts, false);
	}

	/**
	 * Compiles a list of script sources into an immutable AWK program that can be
	 * executed by the {@link AVM} runtime.
	 *
	 * @param scripts script sources to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled immutable program
	 * @throws IOException if an I/O error occurs while reading the
	 *         scripts
	 */
	public AwkProgram compile(List<ScriptSource> scripts, boolean disableOptimizeParam)
			throws IOException {
		return compileProgram(scripts, disableOptimizeParam, new AwkProgram());
	}

	/**
	 * Compiles a full AWK program into the supplied tuple implementation.
	 *
	 * @param scripts script sources to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @param tuples destination tuple implementation
	 * @param <T> concrete tuple type to populate
	 * @return the populated compiled program
	 * @throws IOException if reading script sources fails
	 */
	protected final <T extends AwkProgram> T compileProgram(
			List<ScriptSource> scripts,
			boolean disableOptimizeParam,
			T tuples)
			throws IOException {
		lastAst = null;
		if (!scripts.isEmpty()) {
			// Parse all script sources into a single AST
			AwkParser parser = new AwkParser(this.extensionFunctions, settings.isAllowArraysOfArrays());
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
		tuples.freezeMetadata();

		return tuples;
	}

	/**
	 * Compile an expression to evaluate (not a full script).
	 *
	 * @param expression AWK expression to compile
	 * @return compiled immutable expression
	 * @throws IOException if anything goes wrong with the compilation
	 */
	public AwkExpression compileExpression(String expression) throws IOException {
		return compileExpression(expression, false);
	}

	/**
	 * Compile an expression to evaluate (not a full script).
	 *
	 * @param expression AWK expression to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @return compiled immutable expression
	 * @throws IOException if anything goes wrong with the compilation
	 */
	public AwkExpression compileExpression(String expression, boolean disableOptimizeParam) throws IOException {
		return compileExpression(expression, disableOptimizeParam, new AwkExpression());
	}

	/**
	 * Compiles an AWK expression into the supplied tuple implementation.
	 *
	 * @param expression expression source to compile
	 * @param disableOptimizeParam {@code true} to skip tuple optimization
	 * @param tuples destination tuple implementation
	 * @param <T> concrete tuple type to populate
	 * @return the populated compiled expression
	 * @throws IOException if reading the expression fails
	 */
	protected final <T extends AwkExpression> T compileExpression(
			String expression,
			boolean disableOptimizeParam,
			T tuples)
			throws IOException {
		// Create a ScriptSource
		ScriptSource expressionSource = new ScriptSource(
				ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
				new StringReader(expression));

		// Parse the expression
		AwkParser parser = new AwkParser(this.extensionFunctions, settings.isAllowArraysOfArrays());
		AstNode ast = parser.parseExpression(expressionSource);

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
		tuples.freezeMetadata();

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
		return eval(compileExpression(expression));
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
		return eval(compileExpression(expression), input);
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
		return eval(compileExpression(expression), source);
	}

	/**
	 * Prepares one text record for repeated expression evaluation and returns the
	 * mutable {@link AVM} that will execute those expressions.
	 * <p>
	 * The returned {@link AVM} is created using the current runtime
	 * configuration of this {@link Awk} instance and binds the provided record
	 * once. Later calls to
	 * {@link AVM#eval(AwkExpression)} reuse the same AVM state without resetting it
	 * between expressions, so mutations intentionally leak across evaluations.
	 * This is the high-level convenience wrapper around direct
	 * {@link AVM#prepareForEval(String)} and {@link AVM#eval(AwkExpression)} usage.
	 * </p>
	 *
	 * @param input non-null text record to expose as {@code $0}
	 *        Call {@link AVM#close()} when you are done with the returned interpreter.
	 * @return prepared AVM ready for repeated {@link AVM#eval(AwkExpression)} calls
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
	 * {@link AVM#eval(AwkExpression)} calls reuse the same AVM state without
	 * resetting it between expressions, so mutations intentionally leak across
	 * evaluations. Close the returned AVM when you are done with it to release
	 * any bound input or runtime I/O resources.
	 * </p>
	 *
	 * @param source structured source providing the record to bind
	 * @return prepared AVM ready for repeated {@link AVM#eval(AwkExpression)} calls
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

	/**
	 * Creates an {@link AVM} using the provided runtime settings.
	 *
	 * @param settingsParam runtime settings to apply
	 * @return reusable AVM
	 */
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
	 * Fluent builder for configuring and executing an AWK script or program.
	 * <p>
	 * Obtain an instance through {@link Awk#script(String)} or
	 * {@link Awk#script(AwkProgram)}, configure input, arguments, and
	 * variables, then call one of the terminal methods to execute.
	 * </p>
	 *
	 * <pre>{@code
	 * // Execute and capture printed output as a String
	 * String result = awk.script("{ print toupper($0) }").input("hello").execute();
	 *
	 * // Execute to a specific stream
	 * awk.script(program).input(stream).execute(outputStream);
	 *
	 * // Execute with a custom sink
	 * awk.script("{ print $1 }").input(source).execute(mySink);
	 *
	 * // Execute to an appendable
	 * awk.script("{ print $1 }").input(source).execute(appendable);
	 * }</pre>
	 */
	public final class AwkRunBuilder {

		private AwkProgram compiledProgram;
		private List<String> scripts;
		private InputStream inputStream;
		private InputSource inputSource;
		private List<String> arguments;
		private Map<String, Object> variableOverrides;
		private PrintStream errorStream;

		AwkRunBuilder() {}

		AwkRunBuilder(AwkProgram program) {
			this.compiledProgram = program;
		}

		/**
		 * Appends an additional AWK script to compile and execute.
		 * Multiple scripts are concatenated, like multiple {@code -f} options
		 * in the CLI.
		 *
		 * @param scriptText AWK program source
		 * @return this builder
		 * @throws IllegalStateException if a precompiled program was already set
		 */
		public AwkRunBuilder script(String scriptText) {
			if (compiledProgram != null) {
				throw new IllegalStateException("Cannot add scripts when a precompiled program is set");
			}
			if (scripts == null) {
				scripts = new ArrayList<String>();
			}
			scripts.add(Objects.requireNonNull(scriptText, "script"));
			return this;
		}

		/**
		 * Sets the text input to process.
		 *
		 * @param input text input (encoded as UTF-8 internally)
		 * @return this builder
		 */
		public AwkRunBuilder input(String input) {
			this.inputStream = toInputStream(input);
			return this;
		}

		/**
		 * Sets the byte-stream input to process.
		 *
		 * @param input byte stream, or {@code null} for no input
		 * @return this builder
		 */
		public AwkRunBuilder input(InputStream input) {
			this.inputStream = input;
			return this;
		}

		/**
		 * Sets a structured {@link InputSource} to process.
		 *
		 * @param source structured record source
		 * @return this builder
		 */
		public AwkRunBuilder input(InputSource source) {
			this.inputSource = source;
			return this;
		}

		/**
		 * Sets runtime arguments visible through {@code ARGC}/{@code ARGV}.
		 *
		 * @param args runtime arguments
		 * @return this builder
		 */
		@SuppressFBWarnings("EI_EXPOSE_REP2")
		public AwkRunBuilder arguments(List<String> args) {
			this.arguments = args;
			return this;
		}

		/**
		 * Sets runtime arguments visible through {@code ARGC}/{@code ARGV}.
		 *
		 * @param args runtime arguments
		 * @return this builder
		 */
		public AwkRunBuilder arguments(String... args) {
			this.arguments = Arrays.asList(args);
			return this;
		}

		/**
		 * Adds a single runtime argument visible through {@code ARGC}/{@code ARGV}.
		 *
		 * @param arg runtime argument
		 * @return this builder
		 */
		public AwkRunBuilder argument(String arg) {
			if (this.arguments == null) {
				this.arguments = new ArrayList<String>();
			}
			this.arguments.add(Objects.requireNonNull(arg, "arg"));
			return this;
		}

		/**
		 * Sets the stream used for the stderr output of spawned processes
		 * (e.g.&nbsp;{@code system("...")}).
		 * <p>
		 * When not set, process stderr is merged into the main output sink.
		 * The CLI sets this explicitly to {@code System.err} so that command
		 * errors appear on the console rather than being mixed with normal output.
		 *
		 * @param stream stream to receive process stderr
		 * @return this builder
		 */
		public AwkRunBuilder errorStream(PrintStream stream) {
			this.errorStream = Objects.requireNonNull(stream, "errorStream");
			return this;
		}

		/**
		 * Sets per-call variable overrides applied on top of the settings-level
		 * variables.
		 *
		 * @param overrides variable assignments (may be {@code null})
		 * @return this builder
		 */
		@SuppressFBWarnings("EI_EXPOSE_REP2")
		public AwkRunBuilder variables(Map<String, Object> overrides) {
			this.variableOverrides = overrides;
			return this;
		}

		/**
		 * Sets a single per-call variable override.
		 *
		 * @param name variable name
		 * @param value variable value
		 * @return this builder
		 */
		public AwkRunBuilder variable(String name, Object value) {
			if (this.variableOverrides == null) {
				this.variableOverrides = new LinkedHashMap<String, Object>();
			}
			this.variableOverrides
					.put(
							Objects.requireNonNull(name, "name"),
							value);
			return this;
		}

		/**
		 * Executes the script and returns the printed output as a {@link String}.
		 *
		 * @return printed output
		 * @throws IOException if compilation or execution fails
		 * @throws ExitException if the script terminates with a non-zero exit code
		 */
		public String execute() throws IOException, ExitException {
			StringBuilder output = new StringBuilder();
			doExecute(new AppendableAwkSink(output, settings.getLocale()));
			return output.toString();
		}

		/**
		 * Executes the script, sending output to the specified {@link AwkSink}.
		 *
		 * @param sink output sink
		 * @throws IOException if compilation or execution fails
		 * @throws ExitException if the script terminates with a non-zero exit code
		 */
		public void execute(AwkSink sink) throws IOException, ExitException {
			doExecute(Objects.requireNonNull(sink, "sink"));
		}

		/**
		 * Executes the script, sending output to the specified {@link PrintStream}.
		 *
		 * @param out print stream (e.g. {@code System.out})
		 * @throws IOException if compilation or execution fails
		 * @throws ExitException if the script terminates with a non-zero exit code
		 */
		public void execute(PrintStream out) throws IOException, ExitException {
			Objects.requireNonNull(out, "out");
			doExecute(new OutputStreamAwkSink(out, settings.getLocale()));
		}

		/**
		 * Executes the script, sending output to the specified {@link OutputStream}.
		 *
		 * @param out output stream
		 * @throws IOException if compilation or execution fails
		 * @throws ExitException if the script terminates with a non-zero exit code
		 */
		public void execute(OutputStream out) throws IOException, ExitException {
			doExecute(new OutputStreamAwkSink(toPrintStream(out), settings.getLocale()));
		}

		/**
		 * Executes the script, sending output to the specified {@link Appendable}
		 * (such as {@link StringBuilder} or {@link java.io.StringWriter}).
		 *
		 * @param appendable output destination
		 * @throws IOException if compilation or execution fails
		 * @throws ExitException if the script terminates with a non-zero exit code
		 */
		public void execute(Appendable appendable) throws IOException, ExitException {
			doExecute(
					new AppendableAwkSink(
							Objects.requireNonNull(appendable, "appendable"),
							settings.getLocale()));
		}

		private void doExecute(AwkSink sink) throws IOException, ExitException {
			AwkProgram program = resolveProgram();
			List<String> resolvedArguments = arguments == null ? Collections.<String>emptyList() : arguments;
			try (AVM avm = createAvm(settings)) {
				avm.setAwkSink(sink);
				avm.setErrorStream(errorStream != null ? errorStream : sink.getPrintStream());
				try {
					InputSource resolvedSource;
					if (inputSource != null) {
						resolvedSource = inputSource;
					} else {
						InputStream in = inputStream != null ? inputStream : new ByteArrayInputStream(new byte[0]);
						resolvedSource = new StreamInputSource(in, avm, avm.getJrt());
					}
					avm.execute(program, resolvedSource, resolvedArguments, variableOverrides);
				} catch (ExitException e) {
					if (e.getCode() != 0) {
						throw e;
					}
				} finally {
					sink.flush();
				}
			}
		}

		private AwkProgram resolveProgram() throws IOException {
			if (compiledProgram != null) {
				return compiledProgram;
			}
			if (scripts == null || scripts.isEmpty()) {
				throw new IllegalStateException("No script or program specified");
			}
			if (scripts.size() == 1) {
				return compile(scripts.get(0));
			}
			List<ScriptSource> sources = new ArrayList<ScriptSource>(scripts.size());
			for (int i = 0; i < scripts.size(); i++) {
				sources
						.add(
								new ScriptSource(
										ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
										new StringReader(scripts.get(i))));
			}
			return compile(sources);
		}
	}

	private static PrintStream toPrintStream(OutputStream out) {
		Objects.requireNonNull(out, "outputStream");
		if (out instanceof PrintStream) {
			return (PrintStream) out;
		}
		try {
			return new PrintStream(out, false, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
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
