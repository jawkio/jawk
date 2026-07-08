package io.jawk.backend;

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

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.AwkExpression;
import io.jawk.AwkProgram;
import io.jawk.AwkSandboxException;
import io.jawk.ExitException;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.ExtensionFunction;
import io.jawk.ext.ForInKeyOrder;
import io.jawk.ext.JawkExtension;
import io.jawk.intermediate.Address;
import io.jawk.intermediate.Opcode;
import io.jawk.intermediate.PositionTracker;
import io.jawk.intermediate.Tuple;
import io.jawk.intermediate.Tuple.BooleanTuple;
import io.jawk.intermediate.Tuple.CallFunctionTuple;
import io.jawk.intermediate.Tuple.ClassTuple;
import io.jawk.intermediate.Tuple.CountAndAppendTuple;
import io.jawk.intermediate.Tuple.CountTuple;
import io.jawk.intermediate.Tuple.DereferenceTuple;
import io.jawk.intermediate.Tuple.ExtensionTuple;
import io.jawk.intermediate.Tuple.InputFieldTuple;
import io.jawk.intermediate.Tuple.LongTuple;
import io.jawk.intermediate.Tuple.PushDoubleTuple;
import io.jawk.intermediate.Tuple.PushLongTuple;
import io.jawk.intermediate.Tuple.PushStringTuple;
import io.jawk.intermediate.Tuple.RegexTuple;
import io.jawk.intermediate.Tuple.SubstitutionVariableTuple;
import io.jawk.intermediate.Tuple.VariableTuple;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.intermediate.UntypedObject;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.AwkRuntimeException;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.BlockManager;
import io.jawk.jrt.BlockObject;
import io.jawk.jrt.CharacterTokenizer;
import io.jawk.jrt.ConditionPair;
import io.jawk.jrt.InputSource;
import io.jawk.jrt.StreamInputSource;
import io.jawk.jrt.JRT;
import io.jawk.jrt.RegexTokenizer;
import io.jawk.jrt.SingleCharacterTokenizer;
import io.jawk.jrt.VariableManager;
import io.jawk.util.AwkSettings;
import io.jawk.jrt.BSDRandom;

/**
 * The Jawk interpreter.
 * <p>
 * It takes tuples constructed by the intermediate step
 * and executes each tuple in accordance to their instruction semantics.
 * The tuples correspond to the Awk script compiled by the parser.
 * The interpreter consists of an instruction processor (interpreter),
 * a runtime stack, and machinery to support the instruction set
 * contained within the tuples.
 * <p>
 * The interpreter runs completely independent of the frontend/intermediate step.
 * In fact, an intermediate file produced by Jawk is sufficient to
 * execute on this interpreter. The binding data-structure is
 * the {@link AwkSettings}, which can contain options pertinent to
 * the interpreter. For example, the interpreter must know about
 * the -v command line argument values, as well as the file/variable list
 * parameter values (ARGC/ARGV) after the script on the command line.
 * However, if programmatic access to the AVM is required, meaningful
 * {@link AwkSettings} are not required.
 * <p>
 * Semantic analysis has occurred prior to execution of the interpreter.
 * Therefore, the interpreter throws AwkRuntimeExceptions upon most
 * errors/conditions. It can also throw a <code>java.lang.Error</code> if an
 * interpreter error is encountered.
 * <p>
 * AVM instances are reusable, but they are not thread-safe. Reuse the same
 * interpreter only sequentially, or create one AVM per concurrent execution.
 * When AVM is used directly, callers own its lifecycle and must
 * {@link #close()} it when done.
 *
 * @author Danny Daglas
 */
public class AVM implements VariableManager, Closeable {

	private RuntimeStack runtimeStack = new RuntimeStack();

	// operand stack
	private Deque<Object> operandStack = new ArrayDeque<Object>();
	private List<String> arguments;
	private boolean sortedArrayKeys;
	private final Map<String, Object> baseInitialVariables;
	private final Map<String, Object> baseSpecialVariables;
	private Map<String, Object> executionInitialVariables;
	private Map<String, Object> executionSpecialVariables;
	private JRT jrt;
	private Map<String, JawkExtension> extensionInstances;

	private static final Object NULL_OPERAND = new Object();

	// stack methods
	// private Object pop() { return operandStack.removeFirst(); }
	// private void push(Object o) { operandStack.addLast(o); }
	private Object pop() {
		Object value = operandStack.pop();
		return value == NULL_OPERAND ? null : value;
	}

	private void push(Object o) {
		operandStack.push(o == null ? NULL_OPERAND : o);
	}

	private final AwkSettings settings;
	private final boolean profiling;
	private final Map<Opcode, ProfilingReport.Accumulator> tupleProfilingStats;
	private final Map<String, ProfilingReport.Accumulator> functionProfilingStats;
	private final Deque<ActiveFunction> activeProfilingFunctions;
	private boolean inputSourceFilelistAssignmentsApplied;
	private InputSource resolvedInputSource;
	private AwkExpression installedEvalExpression;
	private boolean mergedGlobalLayoutActive;

	/** Optional extension-provided ordering for {@code for (index in array)} traversal. */
	private ForInKeyOrder forInKeyOrder;

	/** Description of the primary script source, for runtime diagnostics. */
	private String sourceDescription;

	/** Script line of the extension call currently being dispatched. */
	private int currentLineNumber;

	/**
	 * Construct the interpreter.
	 * <p>
	 * Provided to allow programmatic construction of the interpreter
	 * outside of the framework which is used by Jawk.
	 */
	public AVM() {
		this(null, Collections.<String, JawkExtension>emptyMap());
	}

	/**
	 * Construct the interpreter, accepting parameters which may have been
	 * set on the command-line arguments to the JVM.
	 *
	 * @param parameters The parameters affecting the behavior of the
	 *        interpreter.
	 * @param extensionInstances Map of the extensions to load
	 */
	public AVM(final AwkSettings parameters,
			final Map<String, JawkExtension> extensionInstances) {
		this(parameters, extensionInstances, false);
	}

	/**
	 * Construct the interpreter, optionally enabling runtime profiling.
	 *
	 * @param parameters The parameters affecting the behavior of the
	 *        interpreter.
	 * @param extensionInstances Map of the extensions to load
	 * @param profilingEnabled Whether to collect profiling statistics
	 */
	public AVM(
			final AwkSettings parameters,
			final Map<String, JawkExtension> extensionInstances,
			final boolean profilingEnabled) {
		this.settings = parameters != null ? parameters : AwkSettings.DEFAULT_SETTINGS;
		this.extensionInstances = extensionInstances == null ?
				Collections.<String, JawkExtension>emptyMap() : extensionInstances;
		this.profiling = profilingEnabled;
		if (profilingEnabled) {
			this.tupleProfilingStats = new java.util.EnumMap<Opcode, ProfilingReport.Accumulator>(Opcode.class);
			this.functionProfilingStats = new LinkedHashMap<String, ProfilingReport.Accumulator>();
			this.activeProfilingFunctions = new ArrayDeque<ActiveFunction>();
		} else {
			this.tupleProfilingStats = null;
			this.functionProfilingStats = null;
			this.activeProfilingFunctions = null;
		}

		arguments = Collections.emptyList();
		sortedArrayKeys = this.settings.isUseSortedArrayKeys();
		baseInitialVariables = new HashMap<String, Object>(this.settings.getVariables());
		baseSpecialVariables = JRT.copySpecialVariables(baseInitialVariables);
		executionInitialVariables = baseInitialVariables;
		executionSpecialVariables = baseSpecialVariables;

		jrt = createJrt();
		initExtensions();
	}

	protected JRT createJrt() {
		return new JRT(this, this.settings.getLocale(), AwkSink.NOP_SINK, null);
	}

	/**
	 * Returns the runtime settings associated with this interpreter.
	 *
	 * @return the settings, never {@code null}
	 */
	protected AwkSettings getSettings() {
		return settings;
	}

	/**
	 * Returns the JRT (Jawk Runtime) instance associated with this interpreter.
	 *
	 * @return the JRT instance, never {@code null}
	 */
	@SuppressFBWarnings("EI_EXPOSE_REP")
	public JRT getJrt() {
		return jrt;
	}

	/**
	 * Sets the sink used by default {@code print} and {@code printf}
	 * operations on this runtime.
	 *
	 * @param sink sink to use
	 */
	public void setAwkSink(AwkSink sink) {
		jrt.setAwkSink(Objects.requireNonNull(sink, "sink"));
	}

	/**
	 * Sets the stream used for the stderr output of spawned processes
	 * (e.g.&nbsp;{@code system("...")}).
	 *
	 * @param errorStream stream to receive process stderr
	 */
	public void setErrorStream(PrintStream errorStream) {
		jrt.setErrorStream(errorStream);
	}

	/**
	 * Sets the stream that receives runtime warning messages (gawk-style
	 * diagnostics). Warnings default to {@link System#err}.
	 *
	 * @param warningStream stream to receive runtime warnings
	 */
	public void setWarningStream(PrintStream warningStream) {
		jrt.setWarningStream(warningStream);
	}

	/**
	 * Registers the hook that decides the key traversal order of
	 * {@code for (index in array)} statements.
	 * <p>
	 * When no hook is registered, iteration uses the array's natural key order.
	 * Extensions typically register a hook from their {@code beforeStart}
	 * method; the last registration wins.
	 * </p>
	 *
	 * @param keyOrder traversal-order provider, or {@code null} to restore the
	 *        natural key order
	 */
	public void setForInKeyOrder(ForInKeyOrder keyOrder) {
		forInKeyOrder = keyOrder;
	}

	/**
	 * Returns the default sink used by this runtime.
	 *
	 * @return the current AWK sink
	 */
	public AwkSink getAwkSink() {
		return jrt.getAwkSink();
	}

	/**
	 * Returns the locale configured for this runtime.
	 *
	 * @return runtime locale
	 */
	protected Locale getLocale() {
		return jrt.getLocale();
	}

	/**
	 * Evaluates a compiled expression against the AVM state exactly as it
	 * currently stands.
	 *
	 * @param expression compiled expression to evaluate
	 * @return the resulting value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(AwkExpression expression) throws IOException {
		AwkExpression compiledExpression = Objects.requireNonNull(expression, "expression");
		installExpressionMetadata(compiledExpression);

		try {
			executeTuples(compiledExpression.top());
		} catch (ExitException e) {
			// Expression tuples must never contain EXIT opcodes. If callers pass an
			// invalid compiled expression, fail fast without poisoning later evals.
			throwExitException = false;
			exitCode = 0;
			throw new IllegalStateException("eval(AwkExpression) cannot execute EXIT opcodes.", e);
		}
		return operandStack.isEmpty() ? null : JRT.toJavaScalar(pop());
	}

	/**
	 * Evaluates a compiled expression against the supplied input source.
	 *
	 * @param expression compiled expression to evaluate
	 * @param inputSource input source providing the current record
	 * @return the resulting value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(AwkExpression expression, InputSource inputSource) throws IOException {
		return eval(expression, inputSource, null);
	}

	/**
	 * Evaluates a compiled expression against the supplied input source with
	 * per-call variable overrides.
	 *
	 * @param expression compiled expression to evaluate
	 * @param inputSource input source providing the current record
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @return the resulting value
	 * @throws IOException if evaluation fails
	 */
	public Object eval(
			AwkExpression expression,
			InputSource inputSource,
			Map<String, Object> variableOverrides)
			throws IOException {
		prepareForEval(inputSource, Collections.<String>emptyList(), variableOverrides);
		return eval(expression);
	}

	/**
	 * Executes a compiled AWK program with the current runtime defaults.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void execute(AwkProgram program, InputSource inputSource) throws ExitException, IOException {
		execute(program, inputSource, Collections.<String>emptyList(), null);
	}

	/**
	 * Executes a compiled AWK program with explicit runtime arguments.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void execute(AwkProgram program, InputSource inputSource, List<String> runtimeArguments)
			throws ExitException,
			IOException {
		execute(program, inputSource, runtimeArguments, null);
	}

	/**
	 * Executes a compiled AWK program with explicit runtime arguments and
	 * variable overrides.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void execute(
			AwkProgram program,
			InputSource inputSource,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws ExitException,
			IOException {
		AwkProgram compiledProgram = Objects.requireNonNull(program, "program");
		InputSource resolvedSource = Objects.requireNonNull(inputSource, "inputSource");
		resetRuntimeState(runtimeArguments, variableOverrides);
		installProgramMetadata(compiledProgram);

		jrt.prepareForExecution(settings.getFieldSeparator(), settings.getDefaultRS());
		if (!executionSpecialVariables.isEmpty()) {
			jrt.applySpecialVariables(executionSpecialVariables);
		}
		rebindResolvedInputSource(resolvedSource);
		executeTuples(compiledProgram.top());
	}

	/**
	 * Executes a compiled AWK program while persisting user-defined global
	 * variables across repeated executions on this AVM instance.
	 * <p>
	 * Before the new program starts, this method imports any user-defined
	 * globals currently materialized in the AVM and remaps them onto the
	 * incoming program's compiled global slots.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void executePersistingGlobals(AwkProgram program, InputSource inputSource)
			throws ExitException,
			IOException {
		executePersistingGlobals(program, inputSource, Collections.<String>emptyList(), null);
	}

	/**
	 * Executes a compiled AWK program while persisting user-defined global
	 * variables across repeated executions on this AVM instance.
	 * <p>
	 * Before the new program starts, this method imports any user-defined
	 * globals currently materialized in the AVM and remaps them onto the
	 * incoming program's compiled global slots.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void executePersistingGlobals(
			AwkProgram program,
			InputSource inputSource,
			List<String> runtimeArguments)
			throws ExitException,
			IOException {
		executePersistingGlobals(program, inputSource, runtimeArguments, null);
	}

	/**
	 * Executes a compiled AWK program while persisting user-defined global
	 * variables across repeated executions on this AVM instance.
	 * <p>
	 * Before the new program starts, this method imports any user-defined
	 * globals currently materialized in the AVM and remaps them onto the
	 * incoming program's compiled global slots.
	 *
	 * @param program compiled program to execute
	 * @param inputSource input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @throws ExitException when the program terminates via {@code exit}
	 * @throws IOException if execution fails
	 */
	public void executePersistingGlobals(
			AwkProgram program,
			InputSource inputSource,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws ExitException,
			IOException {
		AwkProgram compiledProgram = Objects.requireNonNull(program, "program");
		InputSource resolvedSource = Objects.requireNonNull(inputSource, "inputSource");
		mergeRuntimeState(runtimeArguments, variableOverrides, compiledProgram);

		jrt.prepareForExecution(settings.getFieldSeparator(), settings.getDefaultRS());
		if (!executionSpecialVariables.isEmpty()) {
			jrt.applySpecialVariables(executionSpecialVariables);
		}
		rebindResolvedInputSource(resolvedSource);
		executeTuples(compiledProgram.top());
	}

	/**
	 * Clears the user-defined globals retained in the current runtime stack.
	 * <p>
	 * The next {@link #executePersistingGlobals(AwkProgram, InputSource, List, Map)}
	 * call will therefore start from an empty persistent global bank.
	 */
	public void clearPersistentGlobals() {
		runtimeStack.clearGlobals();
		mergedGlobalLayoutActive = false;
	}

	/**
	 * Captures the user-defined globals currently retained by this AVM for
	 * persistent execution.
	 * <p>
	 * The returned snapshot is serializable and can later be fed back into
	 * {@link #restorePersistentMemory(Map)} on this or another AVM instance.
	 *
	 * @return serializable snapshot of the persistent user-global bank
	 */
	public Map<String, Object> snapshotPersistentMemory() {
		return new LinkedHashMap<>(collectPersistentGlobalValues());
	}

	/**
	 * Restores the user-defined globals retained by this AVM from a previously
	 * captured persistent-memory snapshot.
	 * <p>
	 * Restoring a snapshot replaces the current retained global bank. The next
	 * {@link #executePersistingGlobals(AwkProgram, InputSource, List, Map)} call
	 * will merge these globals into the compiled layout of the incoming program.
	 *
	 * @param snapshot snapshot to restore
	 */
	public void restorePersistentMemory(Map<String, Object> snapshot) {
		Map<String, Object> restoredSnapshot = Objects.requireNonNull(snapshot, "snapshot");
		Map<String, Object> restoredGlobals = filterToPersistentEligible(restoredSnapshot);
		runtimeStack.clearGlobals();
		if (!restoredGlobals.isEmpty()) {
			runtimeStack.rebindGlobals(new ArrayList<>(restoredGlobals.keySet()));
			applyGlobalsToStack(restoredGlobals);
		}
		mergedGlobalLayoutActive = false;
	}

	private void initExtensions() {
		if (extensionInstances.isEmpty()) {
			return;
		}
		Set<JawkExtension> initialized = new LinkedHashSet<JawkExtension>();
		for (JawkExtension extension : extensionInstances.values()) {
			if (initialized.add(extension)) {
				extension.init(this, jrt, settings); // this = VariableManager
			}
		}
	}

	// Offsets for globals that remain runtime-managed by the tuple stream.
	// ARGC is always materialized; ENVIRON and ARGV are emitted on demand.
	private long environOffset = NULL_OFFSET;
	private long argcOffset = NULL_OFFSET;
	private long argvOffset = NULL_OFFSET;

	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);

	/** Random number generator used for rand() */
	private final BSDRandom randomNumberGenerator = new BSDRandom(1);

	/**
	 * Last seed value used with {@code srand()}.
	 * <p>
	 * The default seed for {@code rand()} in One True Awk is {@code 1}, so
	 * we initialize {@code oldseed} with this value to mimic that
	 * behaviour. This ensures deterministic sequences until the user
	 * explicitly calls {@code srand()}.
	 */
	private int oldseed = 1;

	private Address exitAddress = null;

	/**
	 * <code>true</code> if execution position is within an END block;
	 * <code>false</code> otherwise.
	 */
	private boolean withinEndBlocks = false;

	/**
	 * Exit code set by the <code>exit NN</code> command (0 by default)
	 */
	private int exitCode = 0;

	/**
	 * Whether <code>exit</code> has been called and we should throw ExitException
	 */
	private boolean throwExitException = false;

	/**
	 * Maps global variable names to their global array offsets.
	 * It is useful when passing variable assignments from the file-list
	 * portion of the command-line arguments.
	 */
	private Map<String, Integer> globalVariableOffsets;
	/**
	 * Indicates whether the variable, by name, is a scalar
	 * or not. If not, then it is an Associative Array.
	 */
	private Map<String, Boolean> globalVariableArrays;
	private Set<String> functionNames = Collections.emptySet();
	private Map<String, Integer> initializedEvalGlobalVariableOffsets;
	private Map<String, Boolean> initializedEvalGlobalVariableArrays;

	/**
	 * Resets the interpreter to a fresh eval state and binds one text record as
	 * the current input.
	 *
	 * @param input text record to expose as {@code $0}
	 * @return {@code true} when a record was prepared, {@code false} when the
	 *         provided text represents no input
	 * @throws IOException if binding the input fails
	 */
	public boolean prepareForEval(String input) throws IOException {
		return prepareForEval(new SingleRecordInputSource(input), Collections.<String>emptyList(), null);
	}

	/**
	 * Resets the interpreter to a fresh eval state and binds at most one record
	 * from the provided input source as the current input. Calling this method
	 * again on the same source advances to the next available record.
	 *
	 * @param inputSource source providing the record to bind
	 * @return {@code true} when a record was prepared, {@code false} when the
	 *         source is exhausted
	 * @throws IOException if reading the input fails
	 */
	public boolean prepareForEval(InputSource inputSource) throws IOException {
		return prepareForEval(inputSource, Collections.<String>emptyList(), null);
	}

	private boolean prepareForEval(
			InputSource inputSource,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws IOException {
		InputSource resolvedSource = Objects.requireNonNull(inputSource, "inputSource");
		resetRuntimeState(runtimeArguments, variableOverrides);
		rebindResolvedInputSource(resolvedSource);

		jrt.jrtCloseAll();
		jrt.prepareForExecution(settings.getFieldSeparator(), settings.getDefaultRS());
		if (!executionSpecialVariables.isEmpty()) {
			jrt.applySpecialVariables(executionSpecialVariables);
		}
		return jrt.consumeInputForEval(resolvedInputSource);
	}

	private void resetRuntimeState(List<String> runtimeArguments, Map<String, Object> variableOverrides) {
		resetTransientRuntimeState(runtimeArguments, variableOverrides);
		runtimeStack.clearGlobals();
	}

	private void resetTransientRuntimeState(List<String> runtimeArguments, Map<String, Object> variableOverrides) {
		// Reset the AVM-owned state that must not leak across executions.
		operandStack.clear();
		environOffset = NULL_OFFSET;
		argcOffset = NULL_OFFSET;
		argvOffset = NULL_OFFSET;
		exitAddress = null;
		withinEndBlocks = false;
		exitCode = 0;
		throwExitException = false;
		inputSourceFilelistAssignmentsApplied = false;
		globalVariableOffsets = null;
		globalVariableArrays = null;
		functionNames = Collections.emptySet();
		initializedEvalGlobalVariableOffsets = null;
		initializedEvalGlobalVariableArrays = null;
		installedEvalExpression = null;
		mergedGlobalLayoutActive = false;
		runtimeStack.resetTransientState();
		randomNumberGenerator.setSeed(1);
		oldseed = 1;

		prepareExecutionInputs(runtimeArguments, variableOverrides);
	}

	private void installExpressionMetadata(AwkExpression compiledExpression) {
		if (installedEvalExpression == compiledExpression) {
			return;
		}
		globalVariableOffsets = compiledExpression.getGlobalVariableOffsetMap();
		globalVariableArrays = compiledExpression.getGlobalVariableAarrayMap();
		functionNames = compiledExpression.getFunctionNameSet();
		installedEvalExpression = compiledExpression;
	}

	private void installProgramMetadata(AwkProgram compiledProgram) {
		globalVariableOffsets = compiledProgram.getGlobalVariableOffsetMap();
		globalVariableArrays = compiledProgram.getGlobalVariableAarrayMap();
		functionNames = compiledProgram.getFunctionNameSet();
		sourceDescription = compiledProgram.getSourceDescription();
	}

	private void rebindResolvedInputSource(InputSource resolvedSource) {
		InputSource previousResolvedSource = resolvedInputSource;
		if (previousResolvedSource != null && previousResolvedSource != resolvedSource) {
			closeInputSource(previousResolvedSource);
		}
		resolvedInputSource = resolvedSource;
	}

	private boolean hasCompatibleEvalGlobalLayout(long numGlobals) {
		Object[] globals = runtimeStack.getNumGlobals();
		return globals != null
				&& globals.length == numGlobals
				&& Objects.equals(initializedEvalGlobalVariableOffsets, globalVariableOffsets)
				&& Objects.equals(initializedEvalGlobalVariableArrays, globalVariableArrays);
	}

	/**
	 * Resets transient execution state, installs the new program metadata, and
	 * merges the previously retained user globals into the new compiled global
	 * layout.
	 *
	 * @param runtimeArguments name=value or filename entries for this execution
	 * @param variableOverrides per-call variable overrides for this execution
	 * @param compiledProgram program whose global layout should become active
	 */
	private void mergeRuntimeState(
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides,
			AwkProgram compiledProgram) {
		Map<String, Object> carriedGlobals = collectPersistentGlobalValues();
		resetTransientRuntimeState(runtimeArguments, variableOverrides);
		installProgramMetadata(compiledProgram);

		Map<String, Object> basePersistentSeeds = collectBasePersistentGlobalSeeds();
		Map<String, Object> executionUserSeeds = collectExecutionUserGlobalSeeds(variableOverrides);
		List<String> mergedGlobalNamesByOffset = buildMergedGlobalNamesByOffset(
				carriedGlobals,
				basePersistentSeeds,
				executionUserSeeds);

		runtimeStack.rebindGlobals(mergedGlobalNamesByOffset);
		applyGlobalsToStack(carriedGlobals);
		applyGlobalsToStack(basePersistentSeeds);
		applyGlobalsToStack(executionUserSeeds);
		mergedGlobalLayoutActive = true;
	}

	/**
	 * Returns whether the current runtime stack already contains a merged global
	 * layout for the compiled program about to execute.
	 * <p>
	 * Persistent execution may append previously retained globals after the
	 * compiled globals of the incoming program. The tuple stream only dereferences
	 * the prefix defined by {@code SET_NUM_GLOBALS}, so appended globals are valid
	 * as long as the compiled prefix still matches name-for-name and offset-for-offset.
	 *
	 * @param numGlobals number of globals compiled into the active program
	 * @return {@code true} when the merged layout is compatible with the active
	 *         program
	 */
	private boolean hasCompatiblePersistentGlobalLayout(long numGlobals) {
		Object[] globals = runtimeStack.getNumGlobals();
		if (!mergedGlobalLayoutActive
				|| globals == null
				|| globalVariableOffsets == null
				|| globals.length < numGlobals) {
			return false;
		}
		for (Map.Entry<String, Integer> entry : globalVariableOffsets.entrySet()) {
			int offset = entry.getValue().intValue();
			if (offset < 0 || offset >= globals.length || !entry.getKey().equals(runtimeStack.getGlobalName(offset))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Applies execution-level initial variables to stack-managed globals.
	 * <p>
	 * Plain {@link #execute(AwkProgram, InputSource, List, Map)} calls apply all
	 * compatible globals here. Persistent executions only reapply non-persistent
	 * globals so carried user globals are not overwritten unless they were
	 * explicitly supplied for the current run.
	 *
	 * @param skipPersistentEligibleGlobals whether persistent user globals should
	 *        be skipped by this application pass
	 */
	private void applyExecutionInitialVariablesToGlobalSlots(boolean skipPersistentEligibleGlobals) {
		for (Map.Entry<String, Object> entry : executionInitialVariables.entrySet()) {
			String key = entry.getKey();
			if (skipPersistentEligibleGlobals && isPersistentEligibleGlobal(key)) {
				continue;
			}
			if (functionNames.contains(key)) {
				throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + key + ").");
			}
			Integer offsetObj = globalVariableOffsets.get(key);
			Boolean arrayObj = globalVariableArrays.get(key);
			if (offsetObj != null) {
				Object obj = normalizeExternalVariableValue(entry.getValue());
				if (arrayObj.booleanValue()) {
					if (obj instanceof Map) {
						runtimeStack.setFilelistVariable(offsetObj.intValue(), obj);
					} else {
						throw new IllegalArgumentException(
								"Cannot assign a scalar to a non-scalar variable (" + key + ").");
					}
				} else {
					runtimeStack.setFilelistVariable(offsetObj.intValue(), obj);
				}
			}
		}
	}

	/**
	 * Prepares the per-execution runtime arguments and variable overrides.
	 * <p>
	 * Base settings-level variables remain the default source. Per-call overrides
	 * are layered on top without mutating the base snapshot held by this AVM.
	 *
	 * @param runtimeArguments name=value or filename entries for this execution
	 * @param variableOverrides per-call variable overrides for this execution
	 */
	private void prepareExecutionInputs(
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides) {
		this.arguments = runtimeArguments != null ? new ArrayList<>(runtimeArguments) : Collections.<String>emptyList();

		if (variableOverrides == null || variableOverrides.isEmpty()) {
			executionInitialVariables = baseInitialVariables;
			executionSpecialVariables = baseSpecialVariables;
		} else {
			executionInitialVariables = new HashMap<>(baseInitialVariables);
			executionInitialVariables.putAll(variableOverrides);

			Map<String, Object> specialOverrides = JRT.copySpecialVariables(variableOverrides);
			if (specialOverrides.isEmpty()) {
				executionSpecialVariables = baseSpecialVariables;
			} else {
				executionSpecialVariables = new HashMap<>(baseSpecialVariables);
				executionSpecialVariables.putAll(specialOverrides);
			}
		}
	}

	/**
	 * Filters the given map to retain only entries whose keys are
	 * persistent-eligible globals.
	 *
	 * @param source source map to filter
	 * @return insertion-ordered map containing only persistent-eligible entries
	 */
	private Map<String, Object> filterToPersistentEligible(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			if (isPersistentEligibleGlobal(entry.getKey())) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	/**
	 * Collects the current user-defined globals retained in the runtime stack.
	 *
	 * @return retained user globals keyed by name, in current runtime order
	 */
	private Map<String, Object> collectPersistentGlobalValues() {
		return filterToPersistentEligible(runtimeStack.snapshotGlobalVariables());
	}

	/**
	 * Collects the AVM-wide baseline variables that should be reapplied before
	 * each persistent execution.
	 *
	 * @return baseline user globals keyed by name
	 */
	private Map<String, Object> collectBasePersistentGlobalSeeds() {
		Map<String, Object> basePersistentSeeds = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : baseInitialVariables.entrySet()) {
			String name = entry.getKey();
			if (isPersistentEligibleGlobal(name)) {
				validateSeededGlobalName(name);
				Object value = normalizeExternalVariableValue(entry.getValue());
				validateSeededGlobalValue(name, value);
				basePersistentSeeds.put(name, value);
			}
		}
		return basePersistentSeeds;
	}

	/**
	 * Collects the user-defined variables that should override the retained
	 * global bank for the current persistent execution.
	 * <p>
	 * Only user globals are included here. JRT-managed special variables still
	 * flow through the normal execution setup.
	 *
	 * @param variableOverrides per-call variable overrides for this execution
	 * @return insertion-ordered overriding seed values keyed by variable name
	 */
	private Map<String, Object> collectExecutionUserGlobalSeeds(Map<String, Object> variableOverrides) {
		Map<String, Object> executionUserSeeds = new LinkedHashMap<>();
		if (variableOverrides != null) {
			for (Map.Entry<String, Object> entry : variableOverrides.entrySet()) {
				String name = entry.getKey();
				if (isPersistentEligibleGlobal(name)) {
					validateSeededGlobalName(name);
					Object value = normalizeExternalVariableValue(entry.getValue());
					validateSeededGlobalValue(name, value);
					executionUserSeeds.put(name, value);
				}
			}
		}
		return executionUserSeeds;
	}

	/**
	 * Builds the slot order for the next persistent execution.
	 * <p>
	 * The compiled globals are always installed first in their compiled offset
	 * order. Retained globals and seeded user globals that are not compiled by
	 * the incoming program are appended afterwards so future runs can still reuse
	 * them without changing the current program's compiled offsets.
	 *
	 * @param carriedGlobals retained user globals from the previous execution
	 * @param basePersistentSeeds baseline user globals coming from the AVM settings
	 * @param executionUserSeeds per-call user overrides for this execution
	 * @return merged slot-to-name layout for the next persistent run
	 */
	private List<String> buildMergedGlobalNamesByOffset(
			Map<String, Object> carriedGlobals,
			Map<String, Object> basePersistentSeeds,
			Map<String, Object> executionUserSeeds) {
		LinkedHashSet<String> orderedNames = new LinkedHashSet<>();
		List<Map.Entry<String, Integer>> compiledGlobals = new ArrayList<>(globalVariableOffsets.entrySet());
		compiledGlobals.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
		for (Map.Entry<String, Integer> entry : compiledGlobals) {
			orderedNames.add(entry.getKey());
		}
		orderedNames.addAll(carriedGlobals.keySet());
		orderedNames.addAll(basePersistentSeeds.keySet());
		orderedNames.addAll(executionUserSeeds.keySet());
		return new ArrayList<>(orderedNames);
	}

	/**
	 * Writes each entry from {@code globals} into the corresponding named slot in
	 * the runtime stack.
	 *
	 * @param globals map of variable name to value to apply
	 */
	private void applyGlobalsToStack(Map<String, Object> globals) {
		for (Map.Entry<String, Object> entry : globals.entrySet()) {
			runtimeStack.setGlobalVariable(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Returns whether the given global name should participate in persistent
	 * memory.
	 *
	 * @param name global variable name
	 * @return {@code true} when the variable should persist across runs
	 */
	private boolean isPersistentEligibleGlobal(String name) {
		return name != null
				&& !JRT.isJrtManagedSpecialVariable(name)
				&& !NON_PERSISTENT_GLOBALS.contains(name);
	}

	/**
	 * Validates that a seeded global name is compatible with the compiled
	 * metadata of the current program before any value normalization can mutate a
	 * caller-provided object graph.
	 *
	 * @param name variable name to validate
	 */
	private void validateSeededGlobalName(String name) {
		if (functionNames.contains(name)) {
			throw new IllegalArgumentException("Cannot assign a value to a function name (" + name + ").");
		}
	}

	/**
	 * Validates that a normalized seeded global value is compatible with the
	 * compiled metadata of the current program.
	 *
	 * @param name variable name to validate
	 * @param value proposed seeded value after normalization
	 */
	private void validateSeededGlobalValue(String name, Object value) {
		Boolean arrayObj = globalVariableArrays.get(name);
		if (Boolean.TRUE.equals(arrayObj) && !(value instanceof Map)) {
			throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + name + ").");
		}
	}

	/**
	 * Parses a runtime {@code name=value} assignment.
	 *
	 * @param nameValue raw assignment text
	 * @return parsed assignment
	 */
	private NameValueAssignment parseNameValueAssignment(String nameValue) {
		int eqIdx = nameValue.indexOf('=');
		if (eqIdx == 0) {
			throw new IllegalArgumentException(
					"Must have a non-blank variable name in a name=value variable assignment argument.");
		}
		String name = nameValue.substring(0, eqIdx);
		String value = nameValue.substring(eqIdx + 1);
		return new NameValueAssignment(name, jrt.toInputScalar(value));
	}

	/**
	 * Executes the tuple stream after the runtime has been fully prepared.
	 *
	 * @param position current position in the tuple stream
	 * @throws ExitException when the AWK program executes {@code exit}
	 * @throws IOException when runtime input operations fail
	 */
	private void executeTuples(PositionTracker position)
			throws ExitException,
			IOException {
		Map<Integer, ConditionPair> conditionPairs = null;
		Opcode opcode = null;
		long tupleStartNanos = 0L;
		try {
			while (!position.isEOF()) {
				// System_out.println("--> "+position);
				Tuple tuple = position.current();
				opcode = tuple.getOpcode();
				if (profiling) {
					tupleStartNanos = beforeProfiledTuple(tuple, opcode);
				}
				// switch on OPCODE
				switch (opcode) {
				case PRINT: {
					execPrint((CountTuple) tuple);
					position.next();
					break;
				}
				case PRINT_TO_FILE: {
					execPrintToFile((CountAndAppendTuple) tuple);
					position.next();
					break;
				}
				case PRINT_TO_PIPE: {
					execPrintToPipe((CountTuple) tuple);
					position.next();
					break;
				}
				case PRINTF: {
					execPrintf((CountTuple) tuple);
					position.next();
					break;
				}
				case PRINTF_TO_FILE: {
					execPrintfToFile((CountAndAppendTuple) tuple);
					position.next();
					break;
				}
				case PRINTF_TO_PIPE: {
					execPrintfToPipe((CountTuple) tuple);
					position.next();
					break;
				}
				case SPRINTF: {
					// arg[0] = # of sprintf arguments
					// stack[0] = arg1 (format string)
					// stack[1] = arg2
					// etc.
					CountTuple countTuple = (CountTuple) tuple;
					long numArgs = countTuple.getCount();
					push(sprintfFunction(numArgs));
					position.next();
					break;
				}
				case LENGTH: {
					execLength((CountTuple) tuple);
					position.next();
					break;
				}
				case PUSH_LONG: {
					// arg[0] = long constant to push onto the stack
					PushLongTuple pushTuple = (PushLongTuple) tuple;
					push(pushTuple.getValue());
					position.next();
					break;
				}
				case PUSH_DOUBLE: {
					// arg[0] = double constant to push onto the stack
					PushDoubleTuple pushTuple = (PushDoubleTuple) tuple;
					push(pushTuple.getValue());
					position.next();
					break;
				}
				case PUSH_STRING: {
					// arg[0] = string constant to push onto the stack
					PushStringTuple pushTuple = (PushStringTuple) tuple;
					push(pushTuple.getValue());
					position.next();
					break;
				}
				case POP: {
					// stack[0] = item to pop from the stack
					pop();
					position.next();
					break;
				}
				case IFFALSE: {
					// arg[0] = address to jump to if top of stack is false
					// stack[0] = item to check

					// if int, then check for 0
					// if double, then check for 0
					// if String, then check for "" or double value of "0"
					boolean jump = !jrt.toBoolean(pop());
					if (jump) {
						position.jump(tuple.getAddress());
					} else {
						position.next();
					}
					break;
				}
				case TO_NUMBER: {
					// stack[0] = item to convert to a number

					// if int, then check for 0
					// if double, then check for 0
					// if String, then check for "" or double value of "0"
					boolean val = jrt.toBoolean(pop());
					push(val ? ONE : ZERO);
					position.next();
					break;
				}
				case IFTRUE: {
					// arg[0] = address to jump to if top of stack is true
					// stack[0] = item to check

					// if int, then check for 0
					// if double, then check for 0
					// if String, then check for "" or double value of "0"
					boolean jump = jrt.toBoolean(pop());
					if (jump) {
						position.jump(tuple.getAddress());
					} else {
						position.next();
					}
					break;
				}
				case NOT: {
					// stack[0] = item to logically negate

					Object o = pop();

					boolean result = jrt.toBoolean(o);

					if (result) {
						push(0);
					} else {
						push(1);
					}
					position.next();
					break;
				}
				case NEGATE: {
					// stack[0] = item to numerically negate

					double d = JRT.toDouble(pop());
					push(-d);
					position.next();
					break;
				}
				case UNARY_PLUS: {
					// stack[0] = item to convert to a number
					double d = JRT.toDouble(pop());
					push(d);
					position.next();
					break;
				}
				case GOTO: {
					// arg[0] = address

					position.jump(tuple.getAddress());
					break;
				}
				case NOP: {
					// do nothing, just advance the position
					position.next();
					break;
				}
				case CONCAT: {
					// stack[0] = string1
					// stack[1] = string2
					String s2 = jrt.toAwkString(pop());
					String s1 = jrt.toAwkString(pop());
					String resultString = s1 + s2;
					push(resultString);
					position.next();
					break;
				}
				case MULTI_CONCAT: {
					// arg[0] = number of stack items to concatenate
					// stack[0] = last concatenation operand
					CountTuple countTuple = (CountTuple) tuple;
					int count = (int) countTuple.getCount();
					// Store String references so appends run left-to-right. Converting
					// operands to char[] would copy them once before StringBuilder
					// copies them again, and front-inserting would shift existing
					// content on each operand.
					String[] values = new String[count];
					int resultLength = 0;
					for (int i = count - 1; i >= 0; i--) {
						values[i] = jrt.toAwkString(pop());
						resultLength += values[i].length();
					}
					StringBuilder resultString = new StringBuilder(resultLength);
					for (String value : values) {
						resultString.append(value);
					}
					push(resultString.toString());
					position.next();
					break;
				}
				case ASSIGN:
				case ASSIGN_NOPUSH: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = value
					VariableTuple variableTuple = (VariableTuple) tuple;
					Object value = pop();
					assign(
							variableTuple.getVariableOffset(),
							value,
							variableTuple.isGlobal(),
							position,
							opcode == Opcode.ASSIGN);
					position.next();
					break;
				}
				case ASSIGN_ARRAY: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					// stack[1] = value
					Object arrIdx = pop();
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}
					VariableTuple variableTuple = (VariableTuple) tuple;
					long offset = variableTuple.getVariableOffset();
					boolean isGlobal = variableTuple.isGlobal();
					assignArray(offset, arrIdx, rhs, isGlobal);
					position.next();
					break;
				}
				case ASSIGN_MAP_ELEMENT: {
					// stack[0] = array index
					// stack[1] = associative array
					// stack[2] = value
					Object arrIdx = pop();
					Map<Object, Object> array = toMap(pop());
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}
					assignMapElement(array, arrIdx, rhs);
					position.next();
					break;
				}
				case PLUS_EQ_ARRAY:
				case MINUS_EQ_ARRAY:
				case MULT_EQ_ARRAY:
				case DIV_EQ_ARRAY:
				case MOD_EQ_ARRAY:
				case POW_EQ_ARRAY: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					// stack[1] = value
					Object arrIdx = pop();
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}
					VariableTuple variableTuple = (VariableTuple) tuple;
					long offset = variableTuple.getVariableOffset();
					boolean isGlobal = variableTuple.isGlobal();

					double val = JRT.toDouble(rhs);

					Map<Object, Object> array = ensureMapVariable(offset, isGlobal);
					checkScalar(arrIdx);
					Object o = array.get(arrIdx);
					double origVal = JRT.toDouble(o);

					double newVal;

					switch (opcode) {
					case PLUS_EQ_ARRAY:
						newVal = origVal + val;
						break;
					case MINUS_EQ_ARRAY:
						newVal = origVal - val;
						break;
					case MULT_EQ_ARRAY:
						newVal = origVal * val;
						break;
					case DIV_EQ_ARRAY:
						newVal = origVal / val;
						break;
					case MOD_EQ_ARRAY:
						newVal = origVal % val;
						break;
					case POW_EQ_ARRAY:
						newVal = Math.pow(origVal, val);
						break;
					default:
						throw new Error("Invalid op code here: " + opcode);
					}

					assignArray(offset, arrIdx, newVal, isGlobal);
					position.next();
					break;
				}
				case PLUS_EQ_MAP_ELEMENT:
				case MINUS_EQ_MAP_ELEMENT:
				case MULT_EQ_MAP_ELEMENT:
				case DIV_EQ_MAP_ELEMENT:
				case MOD_EQ_MAP_ELEMENT:
				case POW_EQ_MAP_ELEMENT: {
					// stack[0] = array index
					// stack[1] = associative array
					// stack[2] = value
					Object arrIdx = pop();
					Map<Object, Object> array = toMap(pop());
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}

					double val = JRT.toDouble(rhs);
					checkScalar(arrIdx);
					Object o = array.get(arrIdx);
					double origVal = JRT.toDouble(o);
					double newVal;

					switch (opcode) {
					case PLUS_EQ_MAP_ELEMENT:
						newVal = origVal + val;
						break;
					case MINUS_EQ_MAP_ELEMENT:
						newVal = origVal - val;
						break;
					case MULT_EQ_MAP_ELEMENT:
						newVal = origVal * val;
						break;
					case DIV_EQ_MAP_ELEMENT:
						newVal = origVal / val;
						break;
					case MOD_EQ_MAP_ELEMENT:
						newVal = origVal % val;
						break;
					case POW_EQ_MAP_ELEMENT:
						newVal = Math.pow(origVal, val);
						break;
					default:
						throw new Error("Invalid op code here: " + opcode);
					}

					assignMapElement(array, arrIdx, newVal);
					position.next();
					break;
				}

				case ASSIGN_AS_INPUT: {
					// stack[0] = value
					jrt.setInputLine(pop());
					push(jrt.getInputLine());
					position.next();
					break;
				}

				case ASSIGN_AS_INPUT_FIELD: {
					// stack[0] = field number
					// stack[1] = value
					Object fieldNumObj = pop();
					long fieldNum = JRT.parseFieldNumber(fieldNumObj);
					Object value = pop();
					push(value); // leave the result on the stack
					if (fieldNum == 0) {
						jrt.setInputLine(value);
						jrt.jrtParseFields();
					} else {
						jrt.jrtSetInputField(value, fieldNum);
					}
					position.next();
					break;
				}
				case PLUS_EQ:
				case MINUS_EQ:
				case MULT_EQ:
				case DIV_EQ:
				case MOD_EQ:
				case POW_EQ: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = value
					VariableTuple variableTuple = (VariableTuple) tuple;
					long offset = variableTuple.getVariableOffset();
					boolean isGlobal = variableTuple.isGlobal();
					Object o1 = runtimeStack.getVariable(offset, isGlobal);
					if (o1 == null) {
						o1 = BLANK;
					}
					Object o2 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans;
					switch (opcode) {
					case PLUS_EQ:
						ans = d1 + d2;
						break;
					case MINUS_EQ:
						ans = d1 - d2;
						break;
					case MULT_EQ:
						ans = d1 * d2;
						break;
					case DIV_EQ:
						ans = d1 / d2;
						break;
					case MOD_EQ:
						ans = d1 % d2;
						break;
					case POW_EQ:
						ans = Math.pow(d1, d2);
						break;
					default:
						throw new Error("Invalid opcode here: " + opcode);
					}
					push(ans);
					runtimeStack.setVariable(offset, ans, isGlobal);
					position.next();
					break;
				}
				case PLUS_EQ_INPUT_FIELD:
				case MINUS_EQ_INPUT_FIELD:
				case MULT_EQ_INPUT_FIELD:
				case DIV_EQ_INPUT_FIELD:
				case MOD_EQ_INPUT_FIELD:
				case POW_EQ_INPUT_FIELD: {
					// stack[0] = dollar_fieldNumber
					// stack[1] = inc value

					// same code as GET_INPUT_FIELD:
					long fieldnum = JRT.parseFieldNumber(pop());
					double incval = JRT.toDouble(pop());

					// except here, get the number, and add the incvalue
					Object numObj = jrt.jrtGetInputField(fieldnum);
					double num;
					switch (opcode) {
					case PLUS_EQ_INPUT_FIELD:
						num = JRT.toDouble(numObj) + incval;
						break;
					case MINUS_EQ_INPUT_FIELD:
						num = JRT.toDouble(numObj) - incval;
						break;
					case MULT_EQ_INPUT_FIELD:
						num = JRT.toDouble(numObj) * incval;
						break;
					case DIV_EQ_INPUT_FIELD:
						num = JRT.toDouble(numObj) / incval;
						break;
					case MOD_EQ_INPUT_FIELD:
						num = JRT.toDouble(numObj) % incval;
						break;
					case POW_EQ_INPUT_FIELD:
						num = Math.pow(JRT.toDouble(numObj), incval);
						break;
					default:
						throw new Error("Invalid opcode here: " + opcode);
					}
					setNumOnJRT(fieldnum, num);

					// put the result value on the stack
					push(num);
					position.next();

					break;
				}
				case INC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					VariableTuple variableTuple = (VariableTuple) tuple;
					inc(variableTuple.getVariableOffset(), variableTuple.isGlobal());
					position.next();
					break;
				}
				case DEC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					VariableTuple variableTuple = (VariableTuple) tuple;
					dec(variableTuple.getVariableOffset(), variableTuple.isGlobal());
					position.next();
					break;
				}
				case POSTINC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					pop();
					VariableTuple variableTuple = (VariableTuple) tuple;
					push(inc(variableTuple.getVariableOffset(), variableTuple.isGlobal()));
					position.next();
					break;
				}
				case POSTDEC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					pop();
					VariableTuple variableTuple = (VariableTuple) tuple;
					push(dec(variableTuple.getVariableOffset(), variableTuple.isGlobal()));
					position.next();
					break;
				}
				case INC_ARRAY_REF: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					VariableTuple variableTuple = (VariableTuple) tuple;
					boolean isGlobal = variableTuple.isGlobal();
					Map<Object, Object> aa = ensureMapVariable(variableTuple.getVariableOffset(), isGlobal);
					Object key = pop();
					checkScalar(key);
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) + 1;
					aa.put(key, ans);
					position.next();
					break;
				}
				case DEC_ARRAY_REF: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					VariableTuple variableTuple = (VariableTuple) tuple;
					boolean isGlobal = variableTuple.isGlobal();
					Map<Object, Object> aa = ensureMapVariable(variableTuple.getVariableOffset(), isGlobal);
					Object key = pop();
					checkScalar(key);
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) - 1;
					aa.put(key, ans);
					position.next();
					break;
				}
				case INC_MAP_REF: {
					// stack[0] = array index
					// stack[1] = associative array
					Object key = pop();
					checkScalar(key);
					Map<Object, Object> aa = toMap(pop());
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) + 1;
					aa.put(key, ans);
					position.next();
					break;
				}
				case DEC_MAP_REF: {
					// stack[0] = array index
					// stack[1] = associative array
					Object key = pop();
					checkScalar(key);
					Map<Object, Object> aa = toMap(pop());
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) - 1;
					aa.put(key, ans);
					position.next();
					break;
				}
				case INC_DOLLAR_REF: {
					// stack[0] = dollar index (field number)
					long fieldnum = JRT.parseFieldNumber(pop());

					Object numObj = jrt.jrtGetInputField(fieldnum);
					double original = JRT.toDouble(numObj);
					double num = original + 1;
					setNumOnJRT(fieldnum, num);

					push(Double.valueOf(original));

					position.next();
					break;
				}
				case DEC_DOLLAR_REF: {
					// stack[0] = dollar index (field number)
					// same code as GET_INPUT_FIELD:
					long fieldnum = JRT.parseFieldNumber(pop());

					Object numObj = jrt.jrtGetInputField(fieldnum);
					double original = JRT.toDouble(numObj);
					double num = original - 1;
					setNumOnJRT(fieldnum, num);

					push(Double.valueOf(original));

					position.next();
					break;
				}
				case DEREFERENCE: {
					// arg[0] = offset
					// arg[1] = isGlobal
					DereferenceTuple dereferenceTuple = (DereferenceTuple) tuple;
					boolean isGlobal = dereferenceTuple.isGlobal();
					long offset = dereferenceTuple.getVariableOffset();
					Object o = runtimeStack.getVariable(offset, isGlobal);
					if (o == null) {
						if (dereferenceTuple.isArray()) {
							// is_array
							push(runtimeStack.setVariable(offset, newAwkArray(), isGlobal));
						} else {
							push(runtimeStack.setVariable(offset, BLANK, isGlobal));
						}
					} else {
						push(o);
					}
					position.next();
					break;
				}
				case PEEK_DEREFERENCE: {
					VariableTuple variableTuple = (VariableTuple) tuple;
					push(runtimeStack.getVariable(variableTuple.getVariableOffset(), variableTuple.isGlobal()));
					position.next();
					break;
				}
				case DEREF_ARRAY: {
					// stack[0] = array index
					Object idx = pop(); // idx
					checkScalar(idx);
					Map<Object, Object> map = toMap(pop());
					Object o = JRT.getAssocArrayValue(map, idx);
					push(o);
					position.next();
					break;
				}
				case ENSURE_ARRAY_ELEMENT: {
					// stack[0] = array index
					// stack[1] = associative array
					Object idx = pop();
					Map<Object, Object> map = toMap(pop());
					push(ensureArrayInArray(map, idx));
					position.next();
					break;
				}
				case PEEK_ARRAY_ELEMENT: {
					// stack[0] = array index
					Object idx = pop();
					checkScalar(idx);
					Map<Object, Object> map = toMap(pop());
					if (map instanceof AssocArray && !JRT.containsAwkKey(map, idx)) {
						push(BLANK);
					} else {
						Object value = map.get(idx);
						push(value != null ? value : BLANK);
					}
					position.next();
					break;
				}
				case SRAND: {
					// arg[0] = numArgs (where 0 = no args, anything else = one argument)
					// stack[0] = seed (only if numArgs != 0)
					CountTuple countTuple = (CountTuple) tuple;
					long numArgs = countTuple.getCount();
					int seed;
					if (numArgs == 0) {
						// use the time of day for the seed
						seed = JRT.timeSeed();
					} else {
						Object o = pop();
						if (o instanceof Double) {
							seed = ((Double) o).intValue();
						} else if (o instanceof Long) {
							seed = ((Long) o).intValue();
						} else if (o instanceof Integer) {
							seed = ((Integer) o).intValue();
						} else {
							try {
								seed = Integer.parseInt(o.toString());
							} catch (NumberFormatException nfe) {
								seed = 0;
							}
						}
					}
					randomNumberGenerator.setSeed(seed);
					push(oldseed);
					oldseed = seed;
					position.next();
					break;
				}
				case RAND: {
					push(randomNumberGenerator.nextDouble());
					position.next();
					break;
				}
				case INTFUNC: {
					// stack[0] = arg to int() function
					push((long) JRT.toDouble(pop()));
					position.next();
					break;
				}
				case SQRT: {
					// stack[0] = arg to sqrt() function
					push(Math.sqrt(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case LOG: {
					// stack[0] = arg to log() function
					push(Math.log(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case EXP: {
					// stack[0] = arg to exp() function
					push(Math.exp(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case SIN: {
					// stack[0] = arg to sin() function
					push(Math.sin(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case COS: {
					// stack[0] = arg to cos() function
					push(Math.cos(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case ATAN2: {
					// stack[0] = 2nd arg to atan2() function
					// stack[1] = 1st arg to atan2() function
					double d2 = JRT.toDouble(pop());
					double d1 = JRT.toDouble(pop());
					push(Math.atan2(d1, d2));
					position.next();
					break;
				}
				case MATCH: {
					execMatch();
					position.next();
					break;
				}
				case INDEX: {
					// stack[0] = 2nd arg to index() function
					// stack[1] = 1st arg to index() function
					String s2 = jrt.toAwkString(pop());
					String s1 = jrt.toAwkString(pop());
					push(s1.indexOf(s2) + 1);
					position.next();
					break;
				}
				case SUB_FOR_DOLLAR_0: {
					execSubForDollar0((BooleanTuple) tuple);
					position.next();
					break;
				}
				case SUB_FOR_DOLLAR_REFERENCE: {
					execSubForDollarReference((BooleanTuple) tuple);
					position.next();
					break;
				}
				case SUB_FOR_VARIABLE: {
					execSubForVariable((SubstitutionVariableTuple) tuple, position);
					position.next();
					break;
				}
				case SUB_FOR_ARRAY_REFERENCE: {
					execSubForArrayReference((SubstitutionVariableTuple) tuple);
					position.next();
					break;
				}
				case SUB_FOR_MAP_REFERENCE: {
					execSubForMapReference((BooleanTuple) tuple);
					position.next();
					break;
				}
				case SPLIT: {
					execSplit((CountTuple) tuple, position);
					position.next();
					break;
				}
				case SUBSTR: {
					execSubstr((CountTuple) tuple);
					position.next();
					break;
				}
				case TOLOWER: {
					// stack[0] = string
					push(jrt.toAwkString(pop()).toLowerCase());
					position.next();
					break;
				}
				case TOUPPER: {
					// stack[0] = string
					push(jrt.toAwkString(pop()).toUpperCase());
					position.next();
					break;
				}
				case SYSTEM: {
					// stack[0] = command string
					String s = jrt.toAwkString(pop());
					push(jrt.jrtSystem(s));
					position.next();
					break;
				}
				case SWAP: {
					// stack[0] = item1
					// stack[1] = item2
					Object o1 = pop();
					Object o2 = pop();
					push(o1);
					push(o2);
					position.next();
					break;
				}
				case CMP_EQ: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, 0) ? ONE : ZERO);
					position.next();
					break;
				}
				case CMP_LT: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, -1) ? ONE : ZERO);
					position.next();
					break;
				}
				case CMP_GT: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, 1) ? ONE : ZERO);
					position.next();
					break;
				}
				case MATCHES: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					// use o1's string value
					String s = o1.toString();
					// assume o2 is a regexp
					if (o2 instanceof Pattern) {
						Pattern p = (Pattern) o2;
						Matcher m = p.matcher(s);
						// m.matches() matches the ENTIRE string
						// m.find() is more appropriate
						boolean result = m.find();
						push(result ? 1 : 0);
					} else {
						String r = jrt.toAwkString(o2);
						boolean result = Pattern.compile(r).matcher(s).find();
						push(result ? 1 : 0);
					}
					position.next();
					break;
				}
				case ADD: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = d1 + d2;
					push(ans);
					position.next();
					break;
				}
				case SUBTRACT: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = d1 - d2;
					push(ans);
					position.next();
					break;
				}
				case MULTIPLY: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = d1 * d2;
					push(ans);
					position.next();
					break;
				}
				case DIVIDE: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = d1 / d2;
					push(ans);
					position.next();
					break;
				}
				case MOD: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = d1 % d2;
					push(ans);
					position.next();
					break;
				}
				case POW: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans = Math.pow(d1, d2);
					push(ans);
					position.next();
					break;
				}
				case DUP: {
					// stack[0] = top of stack item
					Object o = pop();
					push(o);
					push(o);
					position.next();
					break;
				}
				case KEYLIST: {
					Object o = pop();
					if (o == null || o instanceof UninitializedObject) {
						push(new ArrayDeque<>());
						position.next();
						break;
					}
					if (!(o instanceof Map)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a key list (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					@SuppressWarnings("unchecked")
					Map<Object, Object> map = (Map<Object, Object>) o;
					push(new ArrayDeque<>(forInKeyOrder == null ? map.keySet() : forInKeyOrder.order(map)));
					position.next();
					break;
				}
				case IS_EMPTY_KEYLIST: {
					// arg[0] = address
					// stack[0] = Deque
					Object o = pop();
					if (o == null || !(o instanceof Deque)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a key list (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					Deque<?> keylist = (Deque<?>) o;
					if (keylist.isEmpty()) {
						position.jump(tuple.getAddress());
					} else {
						position.next();
					}
					break;
				}
				case GET_FIRST_AND_REMOVE_FROM_KEYLIST: {
					// stack[0] = Deque
					Object o = pop();
					if (o == null || !(o instanceof Deque)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a key list (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					// pop off and return the head of the key set
					Deque<?> keylist = (Deque<?>) o;
					push(keylist.removeFirst());
					position.next();
					break;
				}
				case CHECK_CLASS: {
					// arg[0] = class object
					// stack[0] = item to check
					ClassTuple checkTuple = (ClassTuple) tuple;
					Object o = pop();
					if (!checkTuple.getType().isInstance(o)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Verification failed. Top-of-stack = " + o.getClass() + " isn't an instance of "
										+ checkTuple.getType());
					}
					push(o);
					position.next();
					break;
				}
				case CONSUME_INPUT: {
					// arg[0] = address
					// store the next record into $0, $1, ...
					applyInputSourceFilelistAssignmentsIfNeeded();
					if (jrt.consumeInput(resolvedInputSource)) {
						position.next();
					} else {
						position.jump(tuple.getAddress());
					}
					break;
				}

				case GETLINE_INPUT: {
					applyInputSourceFilelistAssignmentsIfNeeded();
					push(jrt.consumeInput(resolvedInputSource) ? 1 : 0);
					position.next();
					break;
				}
				case GETLINE_INPUT_TO_TARGET: {
					applyInputSourceFilelistAssignmentsIfNeeded();
					Object input = jrt.consumeInputToTarget(resolvedInputSource);
					if (input != null) {
						push(1);
						push(input);
					} else {
						push(0);
						push("");
					}
					position.next();
					break;
				}
				case USE_AS_FILE_INPUT: {
					// stack[0] = filename
					String s = jrt.toAwkString(pop());
					if (jrt.jrtConsumeFileInput(s)) {
						push(1);
						push(jrt.getInputLine());
					} else {
						push(0);
						push("");
					}
					position.next();
					break;
				}
				case USE_AS_COMMAND_INPUT: {
					// stack[0] = command line
					String s = jrt.toAwkString(pop());
					if (jrt.jrtConsumeCommandInput(s)) {
						push(1);
						push(jrt.getInputLine());
					} else {
						push(0);
						push("");
					}
					position.next();
					break;
				}
				case ENVIRON_OFFSET: {
					// arg[0] = offset; already populated from SET_NUM_GLOBALS so
					// beforeStart hooks observe the real value
					populateEnviron(((LongTuple) tuple).getValue());
					position.next();
					break;
				}
				case ARGC_OFFSET: {
					// arg[0] = offset; already populated from SET_NUM_GLOBALS
					populateArgc(((LongTuple) tuple).getValue());
					position.next();
					break;
				}
				case ARGV_OFFSET: {
					// arg[0] = offset; already populated from SET_NUM_GLOBALS
					populateArgv(((LongTuple) tuple).getValue());
					position.next();
					break;
				}
				case GET_INPUT_FIELD: {
					// stack[0] = field number
					Object fieldNumber = pop();
					push(jrt.jrtGetInputField(fieldNumber));
					position.next();
					break;
				}
				case GET_INPUT_FIELD_CONST: {
					InputFieldTuple inputFieldTuple = (InputFieldTuple) tuple;
					long fieldnum = inputFieldTuple.getFieldIndex();
					push(jrt.jrtGetInputField(fieldnum));
					position.next();
					break;
				}
				case APPLY_RS: {
					jrt.applyRS(jrt.getRSVar());
					position.next();
					break;
				}
				case CALL_FUNCTION: {
					// arg[0] = function address
					// arg[1] = function name
					// arg[2] = # of formal parameters
					// arg[3] = # of actual parameters
					// stack[0] = last actual parameter
					// stack[1] = before-last actual parameter
					// ...
					// stack[n-1] = first actual parameter
					// etc.
					CallFunctionTuple callTuple = (CallFunctionTuple) tuple;
					Address funcAddr = callTuple.getAddress();
					long numFormalParams = callTuple.getNumFormalParams();
					long numActualParams = callTuple.getNumActualParams();
					runtimeStack.pushFrame(numFormalParams, position.currentIndex());
					// Arguments are stacked, so first in the stack is the last for the function
					for (long i = numActualParams - 1; i >= 0; i--) {
						runtimeStack.setVariable(i, pop(), false); // false = local
					}
					position.jump(funcAddr);
					// position.next();
					break;
				}
				case FUNCTION: {
					// important for compilation,
					// not needed for interpretation
					// arg[0] = function name
					// arg[1] = # of formal parameters
					position.next();
					break;
				}
				case WARNING: {
					jrt.printWarning(((Tuple.WarningTuple) tuple).getMessage());
					position.next();
					break;
				}
				case SET_RETURN_RESULT: {
					// stack[0] = return result
					runtimeStack.setReturnValue(pop());
					position.next();
					break;
				}
				case RETURN_FROM_FUNCTION: {
					position.jump(runtimeStack.popFrame());
					push(runtimeStack.getReturnValue());
					position.next();
					break;
				}
				case SET_NUM_GLOBALS: {
					execSetNumGlobals((CountTuple) tuple);
					position.next();
					/*
					 * The runtime-managed preamble (ENVIRON/ARGC/ARGV offsets) follows
					 * immediately; consume it before running the beforeStart hooks so
					 * they observe the real values (e.g. the gawk extension
					 * snapshotting SYMTAB). Sandboxed programs deliberately omit some
					 * of these tuples, which this loop honors by construction.
					 */
					boolean inPreamble = true;
					while (inPreamble && !position.isEOF()) {
						Tuple preambleTuple = position.current();
						switch (preambleTuple.getOpcode()) {
						case ENVIRON_OFFSET:
							populateEnviron(((LongTuple) preambleTuple).getValue());
							position.next();
							break;
						case ARGC_OFFSET:
							populateArgc(((LongTuple) preambleTuple).getValue());
							position.next();
							break;
						case ARGV_OFFSET:
							populateArgv(((LongTuple) preambleTuple).getValue());
							position.next();
							break;
						default:
							inPreamble = false;
							break;
						}
					}
					runBeforeStartHooks();
					break;
				}
				case CLOSE: {
					// stack[0] = file or command line to close
					String s = jrt.toAwkString(pop());
					push(jrt.jrtClose(s));
					position.next();
					break;
				}
				case APPLY_SUBSEP: {
					execApplySubsep((CountTuple) tuple);
					position.next();
					break;
				}
				case DELETE_ARRAY_ELEMENT: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					VariableTuple variableTuple = (VariableTuple) tuple;
					long offset = variableTuple.getVariableOffset();
					boolean isGlobal = variableTuple.isGlobal();
					Map<Object, Object> aa = getMapVariable(offset, isGlobal);
					Object key = pop();
					checkScalar(key);
					if (aa != null) {
						aa.remove(key);
					}
					position.next();
					break;
				}
				case DELETE_MAP_ELEMENT: {
					// stack[0] = array index
					// stack[1] = associative array
					Object key = pop();
					checkScalar(key);
					Map<Object, Object> aa = toMap(pop());
					aa.remove(key);
					position.next();
					break;
				}
				case DELETE_ARRAY: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// (nothing on the stack)
					VariableTuple variableTuple = (VariableTuple) tuple;
					long offset = variableTuple.getVariableOffset();
					boolean isGlobal = variableTuple.isGlobal();
					Map<Object, Object> array = getMapVariable(offset, isGlobal);
					if (array != null) {
						array.clear();
					}
					position.next();
					break;
				}
				case SET_EXIT_ADDRESS: {
					// arg[0] = exit address
					exitAddress = tuple.getAddress();
					position.next();
					break;
				}
				case SET_WITHIN_END_BLOCKS: {
					// arg[0] = whether within the END blocks section
					BooleanTuple endBlocksTuple = (BooleanTuple) tuple;
					withinEndBlocks = endBlocksTuple.getValue();
					position.next();
					break;
				}
				case EXIT_WITHOUT_CODE:
				case EXIT_WITH_CODE: {
					if (opcode == Opcode.EXIT_WITH_CODE) {
						// stack[0] = exit code
						exitCode = (int) JRT.toDouble(pop());
					}
					throwExitException = true;

					// If in BEGIN or in a rule, jump to the END section
					if (!withinEndBlocks && exitAddress != null) {
						// clear runtime stack
						runtimeStack.popAllFrames();
						// clear operand stack
						operandStack.clear();
						position.jump(exitAddress);
					} else {
						// Exit immediately with ExitException
						// clear operand stack
						operandStack.clear();
						throw new ExitException(exitCode, "The AWK script requested an exit");
						// position.next();
					}
					break;
				}
				case REGEXP: {
					// Literal regex tuples must provide a precompiled Pattern as arg[1]
					RegexTuple regexTuple = (RegexTuple) tuple;
					Pattern pattern = regexTuple.getPattern();
					push(pattern);
					position.next();
					break;
				}
				case CONDITION_PAIR: {
					// stack[0] = End condition
					// stack[1] = Start condition
					if (conditionPairs == null) {
						conditionPairs = new HashMap<Integer, ConditionPair>();
					}
					int currentIndex = position.currentIndex();
					ConditionPair cp = conditionPairs.get(currentIndex);
					if (cp == null) {
						cp = new ConditionPair();
						conditionPairs.put(currentIndex, cp);
					}
					boolean end = jrt.toBoolean(pop());
					boolean start = jrt.toBoolean(pop());
					push(cp.update(start, end) ? ONE : ZERO);
					position.next();
					break;
				}
				case IS_IN: {
					// stack[1] = key to check
					Object arr = pop();
					Object arg = pop();
					checkScalar(arg);
					if (arr == null || arr instanceof UninitializedObject) {
						push(ZERO);
						position.next();
						break;
					}
					if (!(arr instanceof Map)) {
						throw new AwkRuntimeException("Attempting to test membership on a non-associative-array.");
					}
					@SuppressWarnings("unchecked")
					Map<Object, Object> aa = (Map<Object, Object>) arr;
					boolean result = JRT.containsAwkKey(aa, arg);
					push(result ? ONE : ZERO);
					position.next();
					break;
				}
				case THIS: {
					// this is in preparation for a function
					// call for the JVM-COMPILED script, only
					// therefore, do NOTHING for the interpreted
					// version
					position.next();
					break;
				}
				case EXTENSION: {
					// arg[0] = extension function metadata
					// arg[1] = # of args on the stack
					// arg[2] = true if parent is NOT an extension function call
					// (i.e., initial extension in calling expression)
					// stack[0] = first actual parameter
					// stack[1] = second actual parameter
					// etc.
					ExtensionTuple extensionTuple = (ExtensionTuple) tuple;
					ExtensionFunction function = extensionTuple.getFunction();
					long numArgs = extensionTuple.getArgCount();
					boolean isInitial = extensionTuple.isInitial();
					// let extensions report diagnostics at the call location
					currentLineNumber = position.lineNumber();

					Object[] args = new Object[(int) numArgs];
					for (int i = (int) numArgs - 1; i >= 0; i--) {
						args[i] = pop();
					}

					String extensionClassName = function.getExtensionClassName();
					JawkExtension extension = extensionInstances.get(extensionClassName);
					if (extension == null) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Extension instance for class '" + extensionClassName
										+ "' is not registered");
					}
					if (!(extension instanceof AbstractExtension)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Extension instance for class '" + extensionClassName
										+ "' does not extend "
										+ AbstractExtension.class.getName());
					}

					Object retval = function.invoke((AbstractExtension) extension, args);

					// block if necessary
					// (convert retval into the return value
					// from the block operation ...)
					if (isInitial && retval != null && retval instanceof BlockObject) {
						retval = new BlockManager().block((BlockObject) retval);
					}
					// (... and proceed)

					if (retval == null) {
						retval = "";
					} else
						if (!(retval instanceof Number
								||
								retval instanceof String
								||
								retval instanceof Map
								||
								retval instanceof BlockObject)) {
									// all other extension results are converted
									// to a string (via Object.toString())
									retval = retval.toString();
								}
					push(retval);

					position.next();
					break;
				}
				case ASSIGN_NF: {
					Object v = pop();
					jrt.setNF(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_NF: {
					push(jrt.getNF());
					position.next();
					break;
				}
				case ASSIGN_NR: {
					Object v = pop();
					jrt.setNR(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_NR: {
					push(jrt.getNR());
					position.next();
					break;
				}
				case ASSIGN_FNR: {
					Object v = pop();
					jrt.setFNR(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_FNR: {
					push(jrt.getFNR());
					position.next();
					break;
				}
				case ASSIGN_FS: {
					Object v = pop();
					jrt.setFS(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_FS: {
					push(jrt.getFSVar());
					position.next();
					break;
				}
				case ASSIGN_RS: {
					Object v = pop();
					jrt.setRS(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_RS: {
					push(jrt.getRSVar());
					position.next();
					break;
				}
				case ASSIGN_OFS: {
					Object v = pop();
					jrt.setOFS(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_OFS: {
					push(jrt.getOFSVar());
					position.next();
					break;
				}
				case ASSIGN_ORS: {
					Object v = pop();
					jrt.setORS(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_ORS: {
					push(jrt.getORSVar());
					position.next();
					break;
				}
				case ASSIGN_RSTART: {
					Object v = pop();
					jrt.setRSTART(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_RSTART: {
					push(jrt.getRSTART());
					position.next();
					break;
				}
				case ASSIGN_RLENGTH: {
					Object v = pop();
					jrt.setRLENGTH(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_RLENGTH: {
					push(jrt.getRLENGTH());
					position.next();
					break;
				}
				case ASSIGN_FILENAME: {
					Object v = pop();
					jrt.setFILENAMEViaJrt(v);
					push(v == null ? "" : v);
					position.next();
					break;
				}
				case PUSH_FILENAME: {
					push(jrt.getFILENAME());
					position.next();
					break;
				}
				case ASSIGN_SUBSEP: {
					Object v = pop();
					jrt.setSUBSEP(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_SUBSEP: {
					push(jrt.getSUBSEPVar());
					position.next();
					break;
				}
				case ASSIGN_CONVFMT: {
					Object v = pop();
					jrt.setCONVFMT(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_CONVFMT: {
					push(jrt.getCONVFMTVar());
					position.next();
					break;
				}
				case ASSIGN_OFMT: {
					Object v = pop();
					jrt.setOFMT(v);
					push(v);
					position.next();
					break;
				}
				case PUSH_OFMT: {
					push(getOFMT());
					position.next();
					break;
				}
				case ASSIGN_ARGC: {
					Object v = pop();
					if (argcOffset == NULL_OFFSET) {
						throw new AwkRuntimeException("ARGC is read-only (not materialized).");
					}
					runtimeStack.setVariable(argcOffset, v, true);
					push(v);
					position.next();
					break;
				}
				case PUSH_ARGC: {
					if (argcOffset == NULL_OFFSET) {
						push(getARGC());
					} else {
						push(runtimeStack.getVariable(argcOffset, true));
					}
					position.next();
					break;
				}
				default:
					throw new Error("invalid opcode: " + position.opcode());
				}
				if (profiling) {
					afterProfiledTuple(opcode, tupleStartNanos);
				}
			}

		} catch (ExitException ee) {
			if (profiling && (opcode == Opcode.EXIT_WITH_CODE || opcode == Opcode.EXIT_WITHOUT_CODE)) {
				afterProfiledTuple(opcode, tupleStartNanos);
			}
			throw ee;
		} catch (IOException ioe) {
			// clear runtime stack
			runtimeStack.popAllFrames();
			// clear operand stack
			operandStack.clear();
			throw ioe;
		} catch (RuntimeException re) {
			// clear runtime stack
			runtimeStack.popAllFrames();
			// clear operand stack
			operandStack.clear();
			if (re instanceof AwkSandboxException) {
				throw re;
			}
			throw new AwkRuntimeException(position.lineNumber(), re.getMessage(), re);
		} catch (AssertionError ae) {
			// clear runtime stack
			runtimeStack.popAllFrames();
			// clear operand stack
			operandStack.clear();
			throw ae;
		}

		// If <code>exit</code> was called, throw an ExitException
		if (throwExitException) {
			throw new ExitException(exitCode, "The AWK script requested an exit");
		}
	}

	/**
	 * Clears all collected profiling statistics.
	 */
	public void resetProfiling() {
		if (!profiling) {
			return;
		}
		tupleProfilingStats.clear();
		functionProfilingStats.clear();
		activeProfilingFunctions.clear();
	}

	/**
	 * Returns an immutable snapshot of the collected profiling statistics.
	 *
	 * @return profiling report snapshot
	 */
	public ProfilingReport getProfilingReport() {
		if (!profiling) {
			return ProfilingReport.empty();
		}
		return new ProfilingReport(tupleProfilingStats, functionProfilingStats);
	}

	private void execPrint(CountTuple tuple) throws IOException {
		long numArgs = tuple.getCount();
		jrt.printDefault(numArgs == 0 ? new Object[] { jrt.jrtGetInputField(0) } : popArguments(numArgs));
	}

	private void execPrintToFile(CountAndAppendTuple tuple) throws IOException {
		String key = jrt.toAwkString(pop());
		long numArgs = tuple.getCount();
		jrt
				.printToFile(
						key,
						tuple.isAppend(),
						numArgs == 0 ? new Object[]
						{ jrt.jrtGetInputField(0) } : popArguments(numArgs));
	}

	private void execPrintToPipe(CountTuple tuple) throws IOException {
		String cmd = jrt.toAwkString(pop());
		long numArgs = tuple.getCount();
		jrt.printToProcess(cmd, numArgs == 0 ? new Object[] { jrt.jrtGetInputField(0) } : popArguments(numArgs));
	}

	private void execPrintf(CountTuple tuple) throws IOException {
		long numArgs = tuple.getCount();
		Object[] values = popArguments(numArgs - 1);
		String format = jrt.toAwkString(pop());
		jrt.printfDefault(format, values);
	}

	private void execPrintfToFile(CountAndAppendTuple tuple) throws IOException {
		String key = jrt.toAwkString(pop());
		long numArgs = tuple.getCount();
		Object[] values = popArguments(numArgs - 1);
		String format = jrt.toAwkString(pop());
		jrt.printfToFile(key, tuple.isAppend(), format, values);
	}

	private void execPrintfToPipe(CountTuple tuple) throws IOException {
		String cmd = jrt.toAwkString(pop());
		long numArgs = tuple.getCount();
		Object[] values = popArguments(numArgs - 1);
		String format = jrt.toAwkString(pop());
		jrt.printfToProcess(cmd, format, values);
	}

	private void execLength(CountTuple tuple) {
		long num = tuple.getCount();
		if (num == 0) {
			push(jrt.jrtGetInputField(0).toString().length());
			return;
		}
		Object value = pop();
		if (value instanceof Map) {
			push((long) ((Map<?, ?>) value).size());
		} else {
			push(jrt.toAwkString(value).length());
		}
	}

	private void execMatch() {
		String ere = jrt.toAwkString(pop());
		String s = jrt.toAwkString(pop());
		int flags = 0;
		if (JRT.toDouble(getVariable("IGNORECASE")) != 0) {
			flags |= Pattern.CASE_INSENSITIVE;
		}
		Pattern pattern = Pattern.compile(ere, flags);
		Matcher matcher = pattern.matcher(s);
		if (matcher.find()) {
			int start = matcher.start() + 1;
			int len = matcher.end() - matcher.start();
			jrt.setRSTART(start);
			jrt.setRLENGTH(len);
			push(start);
		} else {
			jrt.setRSTART(0);
			jrt.setRLENGTH(-1);
			push(0);
		}
	}

	private void execSubForDollar0(BooleanTuple tuple) {
		boolean isGsub = tuple.getValue();
		String repl = jrt.toAwkString(pop());
		String ere = jrt.toAwkString(pop());
		String orig = jrt.toAwkString(jrt.jrtGetInputField(0));
		String newstring = isGsub ? replaceAll(orig, ere, repl) : replaceFirst(orig, ere, repl);
		jrt.setInputLine(newstring);
		jrt.jrtParseFields();
	}

	private void execSubForDollarReference(BooleanTuple tuple) {
		boolean isGsub = tuple.getValue();
		long fieldNum = JRT.parseFieldNumber(pop());
		String orig = jrt.toAwkString(pop());
		String repl = jrt.toAwkString(pop());
		String ere = jrt.toAwkString(pop());
		String newstring = isGsub ? replaceAll(orig, ere, repl) : replaceFirst(orig, ere, repl);
		if (fieldNum == 0) {
			jrt.setInputLine(newstring);
			jrt.jrtParseFields();
		} else {
			jrt.jrtSetInputField(newstring, fieldNum);
		}
	}

	private void execSubForVariable(SubstitutionVariableTuple tuple, PositionTracker position) {
		String newString = execSubOrGSub(tuple.isGlobalSubstitution());
		assign(tuple.getVariableOffset(), newString, tuple.isGlobal(), position, false);
	}

	private void execSubForArrayReference(SubstitutionVariableTuple tuple) {
		Object arrIdx = pop();
		String newString = execSubOrGSub(tuple.isGlobalSubstitution());
		assignArray(tuple.getVariableOffset(), arrIdx, newString, tuple.isGlobal());
		pop();
	}

	private void execSubForMapReference(BooleanTuple tuple) {
		Object arrIdx = pop();
		Map<Object, Object> array = toMap(pop());
		String newString = execSubOrGSub(tuple.getValue());
		assignMapElement(array, arrIdx, newString);
		pop();
	}

	private void execSplit(CountTuple tuple, PositionTracker position) {
		long numArgs = tuple.getCount();
		String fsString;
		if (numArgs == 2) {
			fsString = jrt.toAwkString(jrt.getFSVar());
		} else if (numArgs == 3) {
			fsString = jrt.toAwkString(pop());
		} else {
			throw new Error("Invalid # of args. split() requires 2 or 3. Got: " + numArgs);
		}
		Object o = pop();
		if (!(o instanceof Map)) {
			throw new AwkRuntimeException(position.lineNumber(), o + " is not an array.");
		}
		String s = jrt.toAwkString(pop());
		Enumeration<Object> tokenizer;
		if (fsString.equals(" ")) {
			tokenizer = new StringTokenizer(s);
		} else if (fsString.length() == 1) {
			tokenizer = new SingleCharacterTokenizer(s, fsString.charAt(0));
		} else if (fsString.isEmpty()) {
			tokenizer = new CharacterTokenizer(s);
		} else {
			tokenizer = new RegexTokenizer(s, fsString);
		}

		@SuppressWarnings("unchecked")
		Map<Object, Object> assocArray = (Map<Object, Object>) o;
		assocArray.clear();
		long cnt = 0;
		while (tokenizer.hasMoreElements()) {
			Object value = tokenizer.nextElement();
			assocArray.put(++cnt, jrt.toInputScalar(value));
		}
		push(cnt);
	}

	private void execSubstr(CountTuple tuple) {
		long numArgs = tuple.getCount();
		int startPos, length;
		String s;
		if (numArgs == 3) {
			length = (int) JRT.toLong(pop());
			startPos = (int) JRT.toDouble(pop());
			s = jrt.toAwkString(pop());
		} else if (numArgs == 2) {
			startPos = (int) JRT.toDouble(pop());
			s = jrt.toAwkString(pop());
			length = s.length() - startPos + 1;
		} else {
			throw new Error("numArgs for SUBSTR must be 2 or 3. It is " + numArgs);
		}
		if (startPos <= 0) {
			startPos = 1;
		}
		if (length <= 0 || startPos > s.length()) {
			push(BLANK);
		} else if (startPos + length > s.length()) {
			push(s.substring(startPos - 1));
		} else {
			push(s.substring(startPos - 1, startPos + length - 1));
		}
	}

	private void execSetNumGlobals(CountTuple tuple) {
		long numGlobals = tuple.getCount();
		Object[] globals = runtimeStack.getNumGlobals();
		if (mergedGlobalLayoutActive) {
			if (!hasCompatiblePersistentGlobalLayout(numGlobals)) {
				throw new IllegalStateException(
						"AVM globals are already initialized for an incompatible persistent layout.");
			}
			applyExecutionInitialVariablesToGlobalSlots(true);
		} else if (globals == null) {
			runtimeStack.setNumGlobals(numGlobals, globalVariableOffsets);
			initializedEvalGlobalVariableOffsets = globalVariableOffsets;
			initializedEvalGlobalVariableArrays = globalVariableArrays;
			applyExecutionInitialVariablesToGlobalSlots(false);
		} else if (!hasCompatibleEvalGlobalLayout(numGlobals)) {
			throw new IllegalStateException(
					"AVM globals are already initialized for a different eval layout. Call prepareForEval(...) first.");
		}
	}

	private void populateEnviron(long offset) {
		environOffset = offset;
		for (Map.Entry<String, String> var : System.getenv().entrySet()) {
			assignArray(environOffset, var.getKey(), jrt.toInputScalar(var.getValue()), true);
			pop(); // clean up the stack after the assignment
		}
	}

	private void populateArgc(long offset) {
		argcOffset = offset;
		// +1 to include the "jawk" program name (ARGV[0])
		runtimeStack.setVariable(argcOffset, JRT.toAssignedScalar(Integer.valueOf(arguments.size() + 1)), true);
	}

	private void populateArgv(long offset) {
		argvOffset = offset;
		// consume argv (looping from 1 to argc)
		int argc = (int) JRT.toDouble(runtimeStack.getVariable(argcOffset, true)); // true = global
		assignArray(argvOffset, 0, "jawk", true);
		pop();
		for (int i = 1; i < argc; i++) {
			assignArray(argvOffset, i, jrt.toInputScalar(arguments.get(i - 1)), true);
			pop(); // clean up the stack after the assignment
		}
	}

	private void runBeforeStartHooks() {
		if (extensionInstances.isEmpty()) {
			return;
		}
		Set<JawkExtension> started = new LinkedHashSet<JawkExtension>();
		for (JawkExtension extension : extensionInstances.values()) {
			if (started.add(extension)) {
				extension.beforeStart(this, jrt);
			}
		}
	}

	private void execApplySubsep(CountTuple tuple) {
		long count = tuple.getCount();
		if (count == 1) {
			Object value = pop();
			checkScalar(value);
			push(jrt.toAwkString(value));
			return;
		}
		StringBuilder sb = new StringBuilder();
		Object value = pop();
		checkScalar(value);
		sb.append(jrt.toAwkString(value));
		String subsep = jrt.toAwkString(jrt.getSUBSEPVar());
		for (int i = 1; i < count; i++) {
			sb.insert(0, subsep);
			value = pop();
			checkScalar(value);
			sb.insert(0, jrt.toAwkString(value));
		}
		push(sb.toString());
	}

	private long beforeProfiledTuple(Tuple tuple, Opcode opcode) {
		long now = System.nanoTime();
		if (opcode == Opcode.CALL_FUNCTION) {
			CallFunctionTuple callTuple = (CallFunctionTuple) tuple;
			activeProfilingFunctions.push(new ActiveFunction(callTuple.getFunctionName(), now));
		} else if (opcode == Opcode.EXTENSION) {
			ExtensionTuple extensionTuple = (ExtensionTuple) tuple;
			ExtensionFunction function = extensionTuple.getFunction();
			activeProfilingFunctions.push(new ActiveFunction(function.getKeyword(), now));
		}
		return now;
	}

	private void afterProfiledTuple(Opcode opcode, long tupleStartNanos) {
		long now = System.nanoTime();
		statisticsFor(tupleProfilingStats, opcode).add(now - tupleStartNanos);
		if (opcode == Opcode.EXIT_WITH_CODE || opcode == Opcode.EXIT_WITHOUT_CODE) {
			recordAllFunctionExits(now);
		} else if (opcode == Opcode.EXTENSION || opcode == Opcode.RETURN_FROM_FUNCTION) {
			recordFunctionExit(now);
		}
	}

	private static <K> ProfilingReport.Accumulator statisticsFor(
			Map<K, ProfilingReport.Accumulator> stats,
			K key) {
		ProfilingReport.Accumulator accumulator = stats.get(key);
		if (accumulator == null) {
			accumulator = new ProfilingReport.Accumulator();
			stats.put(key, accumulator);
		}
		return accumulator;
	}

	private void recordFunctionExit(long now) {
		if (activeProfilingFunctions.isEmpty()) {
			return;
		}
		ActiveFunction function = activeProfilingFunctions.pop();
		statisticsFor(functionProfilingStats, function.name).add(now - function.startNanos);
	}

	private void recordAllFunctionExits(long now) {
		while (!activeProfilingFunctions.isEmpty()) {
			recordFunctionExit(now);
		}
	}

	private static final class ActiveFunction {
		private final String name;
		private final long startNanos;

		private ActiveFunction(String name, long startNanos) {
			this.name = name;
			this.startNanos = startNanos;
		}
	}

	/**
	 * Releases any prepared input source and runtime I/O resources owned by this
	 * AVM.
	 * <p>
	 * Call this when you are done with an AVM obtained through expert-level
	 * integration, or after direct {@link #eval(AwkExpression, InputSource)} /
	 * {@link #execute(AwkProgram, InputSource)} usage.
	 * The AVM may be prepared again afterwards, but callers should treat a closed
	 * instance as end-of-use unless they intentionally reinitialize it.
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		jrt.jrtCloseAll();
		closeResolvedInputSource();
		resolvedInputSource = null;
		inputSourceFilelistAssignmentsApplied = false;
	}

	/**
	 * Close the resolved {@link InputSource} if it implements {@link Closeable}.
	 * This is used by {@link #close()} and by explicit rebind operations such as
	 * {@link #prepareForEval(InputSource)} when the AVM switches to a different
	 * source instance.
	 */
	private void closeResolvedInputSource() {
		closeInputSource(resolvedInputSource);
	}

	private void closeInputSource(InputSource inputSource) {
		if (!(inputSource instanceof Closeable)) {
			return;
		}
		try {
			((Closeable) inputSource).close();
		} catch (IOException ignored) {
			// Best-effort close.
		}
	}

	private Object[] popArguments(long numArgs) {
		Object[] args = new Object[(int) numArgs];
		for (int i = (int) numArgs - 1; i >= 0; i--) {
			args[i] = pop();
		}
		return args;
	}

	/**
	 * sprintf() functionality
	 */
	private String sprintfFunction(long numArgs) {
		Object[] argArray = popArguments(numArgs - 1);
		String fmt = jrt.toAwkString(pop());
		return jrt.getAwkSink().sprintf(fmt, argArray);
	}

	private void setNumOnJRT(long fieldNum, double num) {
		String numString = jrt.toAwkString(Double.valueOf(num));

		// same code as ASSIGN_AS_INPUT_FIELD
		if (fieldNum == 0) {
			jrt.setInputLine(numString);
			jrt.jrtParseFields();
		} else {
			jrt.jrtSetInputField(numString, fieldNum);
		}
	}

	private String execSubOrGSub(boolean isGsub) {
		String newString;

		// stack[0] = original field value
		// stack[1] = replacement string
		// stack[2] = ere
		String orig = jrt.toAwkString(pop());
		String repl = jrt.toAwkString(pop());
		String ere = jrt.toAwkString(pop());
		if (isGsub) {
			newString = replaceAll(orig, ere, repl);
		} else {
			newString = replaceFirst(orig, ere, repl);
		}

		return newString;
	}

	private StringBuffer replaceFirstSb = new StringBuffer();

	/**
	 * sub() functionality
	 */
	private String replaceFirst(String orig, String ere, String repl) {
		push(RegexRuntimeSupport.replaceFirst(orig, repl, ere, replaceFirstSb));
		return replaceFirstSb.toString();
	}

	private StringBuffer replaceAllSb = new StringBuffer();

	/**
	 * gsub() functionality
	 */
	private String replaceAll(String orig, String ere, String repl) {
		push(RegexRuntimeSupport.replaceAll(orig, repl, ere, replaceAllSb));
		return replaceAllSb.toString();
	}

	/**
	 * Awk variable assignment functionality.
	 */
	private void assign(long l, Object value, boolean isGlobal, PositionTracker position, boolean push) {
		value = JRT.toAssignedScalar(value);
		// check if curr value already refers to an array
		if (runtimeStack.getVariable(l, isGlobal) instanceof Map) {
			throw new AwkRuntimeException(position.lineNumber(), "cannot assign anything to an unindexed associative array");
		}
		if (push) {
			push(value);
		}
		runtimeStack.setVariable(l, value, isGlobal);
		// When specials are compiled correctly, they use ASSIGN_* and skip this path.
	}

	/**
	 * Awk array element assignment functionality.
	 */
	private void assignArray(long offset, Object arrIdx, Object rhs, boolean isGlobal) {
		assignMapElement(ensureMapVariable(offset, isGlobal), arrIdx, rhs);
	}

	private void assignMapElement(Map<Object, Object> array, Object arrIdx, Object rhs) {
		checkScalar(arrIdx);
		rhs = JRT.toAssignedScalar(rhs);
		array.put(arrIdx, rhs);
		push(rhs);
	}

	/**
	 * Numerically increases an Awk variable by one; the result
	 * is placed back into that variable.
	 */
	private Object inc(long l, boolean isGlobal) {
		Object o = runtimeStack.getVariable(l, isGlobal);
		if (o == null || o instanceof UninitializedObject) {
			o = ZERO;
			runtimeStack.setVariable(l, o, isGlobal);
		}
		Object updated = JRT.inc(o);
		runtimeStack.setVariable(l, updated, isGlobal);
		return o;
	}

	/**
	 * Numerically decreases an Awk variable by one; the result
	 * is placed back into that variable.
	 */
	private Object dec(long l, boolean isGlobal) {
		Object o = runtimeStack.getVariable(l, isGlobal);
		if (o == null || o instanceof UninitializedObject) {
			o = ZERO;
			runtimeStack.setVariable(l, o, isGlobal);
		}
		Object updated = JRT.dec(o);
		runtimeStack.setVariable(l, updated, isGlobal);
		return o;
	}

	/** {@inheritDoc} */
	@Override
	public final Object getRS() {
		return jrt.getRSVar();
	}

	/** {@inheritDoc} */
	@Override
	public final Object getOFS() {
		return jrt.getOFSVar();
	}

	/** {@inheritDoc} */
	@Override
	public final Object getORS() {
		return jrt.getORSVar();
	}

	/** {@inheritDoc} */
	@Override
	public final Object getSUBSEP() {
		return jrt.getSUBSEPVar();
	}

	/**
	 * Returns the names of the global variables declared by the compiled
	 * program.
	 *
	 * @return unmodifiable set of global variable names, empty when no program
	 *         metadata is installed
	 */
	public Set<String> getGlobalVariableNames() {
		return globalVariableOffsets == null ?
				Collections.<String>emptySet() : Collections.unmodifiableSet(globalVariableOffsets.keySet());
	}

	/**
	 * Returns the names of the user-defined functions of the compiled program.
	 *
	 * @return unmodifiable set of function names, empty when no program metadata
	 *         is installed
	 */
	public Set<String> getFunctionNames() {
		return functionNames == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(functionNames);
	}

	/**
	 * The special variables this interpreter can answer by name, mapped to their
	 * accessors. Single source of truth for {@link #getVariable(String)} and
	 * {@link #getSpecialVariableNames()}.
	 */
	private final Map<String, Supplier<Object>> specialVariables = buildSpecialVariables();

	private Map<String, Supplier<Object>> buildSpecialVariables() {
		Map<String, Supplier<Object>> map = new LinkedHashMap<String, Supplier<Object>>();
		map.put("FS", this::getFS);
		map.put("RS", this::getRS);
		map.put("OFS", this::getOFS);
		map.put("ORS", this::getORS);
		map.put("FILENAME", () -> jrt.getFILENAME());
		map.put("SUBSEP", this::getSUBSEP);
		map.put("CONVFMT", this::getCONVFMT);
		map.put("OFMT", () -> jrt.getOFMTString());
		map.put("NF", () -> jrt.getNF());
		map.put("NR", () -> jrt.getNR());
		map.put("FNR", () -> jrt.getFNR());
		map.put("RSTART", () -> jrt.getRSTART());
		map.put("RLENGTH", () -> jrt.getRLENGTH());
		// lazily-materialized globals answered through their synthetic accessors
		map.put("ARGC", this::getARGC);
		map.put("ARGV", this::getARGV);
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Returns the names of the special variables that
	 * {@link #getVariable(String)} answers directly.
	 *
	 * @return unmodifiable set of special variable names
	 */
	public Set<String> getSpecialVariableNames() {
		return specialVariables.keySet();
	}

	/** {@inheritDoc} */
	@Override
	public final Object getVariable(String name) {
		if (name == null) {
			return null;
		}
		Supplier<Object> special = specialVariables.get(name);
		if (special != null) {
			return special.get();
		}
		if (globalVariableOffsets == null) {
			return baseInitialVariables.get(name);
		}
		Integer offsetObj = globalVariableOffsets.get(name);
		if (offsetObj != null) {
			return runtimeStack.getVariable(offsetObj.intValue(), true);
		}
		// Variables supplied through -v or the Java API but never referenced in
		// the script have no compiled offset; they are still observable (e.g.
		// IGNORECASE read by the gawk extension).
		return baseInitialVariables == null ? null : baseInitialVariables.get(name);
	}

	/**
	 * Returns the description of the primary script source (typically its file
	 * name), for extension-emitted diagnostics.
	 *
	 * @return script source description, or {@code null} when unknown
	 */
	public String getSourceDescription() {
		return sourceDescription;
	}

	/**
	 * Returns the script line of the extension call currently being dispatched,
	 * for extension-emitted diagnostics.
	 *
	 * @return current script line number
	 */
	public int getCurrentLineNumber() {
		return currentLineNumber;
	}

	/**
	 * Performs the global variable assignment within the runtime environment.
	 * These assignments come from the ARGV list (bounded by ARGC), which, in
	 * turn, come from the command-line arguments passed into Awk.
	 *
	 * @param nameValue The variable assignment in <i>name=value</i> form.
	 */
	@SuppressWarnings("unused")
	private void setFilelistVariable(String nameValue) {
		NameValueAssignment assignment = parseNameValueAssignment(nameValue);
		String name = assignment.name;
		Object obj = assignment.value;

		// make sure we're not receiving funcname=value assignments
		if (functionNames.contains(name)) {
			throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + name + ").");
		}

		Integer offsetObj = globalVariableOffsets.get(name);
		Boolean arrayObj = globalVariableArrays.get(name);

		if (offsetObj != null) {
			if (arrayObj.booleanValue()) {
				throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + name + ").");
			} else {
				runtimeStack.setFilelistVariable(offsetObj.intValue(), obj);
			}
		} else if (runtimeStack.hasGlobalVariable(name)) {
			runtimeStack.setGlobalVariable(name, obj);
		}
	}

	/** {@inheritDoc} */
	@Override
	public final void assignVariable(String name, Object obj) {
		// When offsets are not available yet, treat the assignment as part of this
		// AVM's baseline initial-variable snapshot.
		if (globalVariableOffsets == null || globalVariableArrays == null) {
			Object normalized = normalizeExternalVariableValue(obj);
			baseInitialVariables.put(name, normalized);
			if (JRT.isJrtManagedSpecialVariable(name)) {
				baseSpecialVariables.put(name, normalized);
			}
			return;
		}

		// make sure we're not receiving funcname=value assignments
		if (functionNames.contains(name)) {
			throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + name + ").");
		}

		Integer offsetObj = globalVariableOffsets.get(name);
		Boolean arrayObj = globalVariableArrays.get(name);

		if (offsetObj != null) {
			Object normalized = normalizeExternalVariableValue(obj);
			if (arrayObj.booleanValue()) {
				if (normalized instanceof Map) {
					runtimeStack.setFilelistVariable(offsetObj.intValue(), normalized);
				} else {
					throw new IllegalArgumentException(
							"Cannot assign a scalar to a non-scalar variable (" + name + ").");
				}
			} else {
				runtimeStack.setFilelistVariable(offsetObj.intValue(), normalized);
			}
		} else if (runtimeStack.hasGlobalVariable(name)) {
			Object normalized = normalizeExternalVariableValue(obj);
			runtimeStack.setGlobalVariable(name, normalized);
		}
	}

	private void applyInputSourceFilelistAssignmentsIfNeeded() {
		if (inputSourceFilelistAssignmentsApplied || resolvedInputSource instanceof StreamInputSource) {
			return;
		}
		for (String argument : arguments) {
			if (argument.indexOf('=') > 0) {
				setFilelistVariable(argument);
			}
		}
		inputSourceFilelistAssignmentsApplied = true;
	}

	/** {@inheritDoc} */
	@Override
	public Object getFS() {
		return jrt.getFSVar();
	}

	/** {@inheritDoc} */
	@Override
	public Object getCONVFMT() {
		return jrt.getCONVFMTString();
	}

	/** {@inheritDoc} */
	@Override
	public void resetFNR() {
		jrt.setFNR(0);
	}

	/** {@inheritDoc} */
	@Override
	public void incFNR() {
		long v = jrt.getFNR();
		jrt.setFNR(v + 1);
	}

	/** {@inheritDoc} */
	@Override
	public void incNR() {
		long v = jrt.getNR();
		jrt.setNR(v + 1);
	}

	/** {@inheritDoc} */
	@Override
	public void setNF(Integer newNf) {
		jrt.setNF(newNf);
	}

	/** {@inheritDoc} */
	@Override
	public void setFILENAME(String filename) {
		jrt.setFILENAMEViaJrt(jrt.toInputScalar(filename));
	}

	/** {@inheritDoc} */
	@Override
	public Object getARGV() {
		if (argvOffset == NULL_OFFSET) {
			Map<Object, Object> argv = newAwkArray();
			argv.put(0L, "jawk");
			for (int i = 0; i < arguments.size(); i++) {
				argv.put(Long.valueOf(i + 1L), jrt.toInputScalar(arguments.get(i)));
			}
			return argv;
		}
		return runtimeStack.getVariable(argvOffset, true);
	}

	/** {@inheritDoc} */
	@Override
	public Object getARGC() {
		if (argcOffset == NULL_OFFSET) {
			return Long.valueOf(arguments.size() + 1);
		}
		return runtimeStack.getVariable(argcOffset, true);
	}

	private String getOFMT() {
		return jrt.getOFMTString();
	}

	private Map<Object, Object> newAwkArray() {
		return JRT.createAwkMap(sortedArrayKeys);
	}

	private Map<Object, Object> ensureMapVariable(long offset, boolean isGlobal) {
		Object value = runtimeStack.getVariable(offset, isGlobal);
		if (value == null || value.equals(BLANK) || value instanceof UninitializedObject) {
			Map<Object, Object> map = newAwkArray();
			runtimeStack.setVariable(offset, map, isGlobal);
			return map;
		}
		return toMap(value);
	}

	private Map<Object, Object> getMapVariable(long offset, boolean isGlobal) {
		Object value = runtimeStack.getVariable(offset, isGlobal);
		if (value == null || value.equals(BLANK) || value instanceof UninitializedObject) {
			return null;
		}
		return toMap(value);
	}

	/**
	 * Casts an AWK value to an associative array.
	 *
	 * @param value value to validate
	 * @return the associative array value
	 * @throws AwkRuntimeException when {@code value} is scalar
	 */
	private Map<Object, Object> toMap(Object value) {
		if (!(value instanceof Map)) {
			throw new AwkRuntimeException("Attempting to treat a scalar as an array.");
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> map = (Map<Object, Object>) value;
		return map;
	}

	/**
	 * Ensures a value is scalar before using it in a scalar-only context such as
	 * a subscript component.
	 *
	 * @param value value to validate
	 * @throws AwkRuntimeException when {@code value} is an array
	 */
	private void checkScalar(Object value) {
		if (value instanceof Map) {
			throw new AwkRuntimeException("Attempting to use an array in a scalar context.");
		}
	}

	/**
	 * Returns the nested associative array stored in {@code map[key]}, creating it
	 * when the key is undefined.
	 *
	 * @param map containing array
	 * @param key nested-array key
	 * @return the nested associative array stored at {@code key}
	 * @throws AwkRuntimeException when {@code key} is scalar-incompatible or when
	 *         the existing slot contains a scalar
	 */
	private Map<Object, Object> ensureArrayInArray(Map<Object, Object> map, Object key) {
		checkScalar(key);
		boolean existingKey = JRT.containsAwkKey(map, key);
		Object value = JRT.getAssocArrayValue(map, key);
		if (!existingKey || value == null || value instanceof UntypedObject) {
			Map<Object, Object> nested = newAwkArray();
			map.put(key, nested);
			return nested;
		}
		if (!(value instanceof Map)) {
			throw new AwkRuntimeException("Attempting to use a scalar as an array.");
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> nested = (Map<Object, Object>) value;
		return nested;
	}

	private Object normalizeExternalVariableValue(Object value) {
		if (value instanceof String) {
			return jrt.toInputScalar(value);
		}
		if (!(value instanceof Map) && !(value instanceof List)) {
			return value;
		}
		return AssocArray.normalizeValue(value, sortedArrayKeys);
	}

	private static final UninitializedObject BLANK = new UninitializedObject();

	/**
	 * Global names that must not participate in persistent memory even though they
	 * are technically user-visible variables.
	 */
	private static final Set<String> NON_PERSISTENT_GLOBALS = new HashSet<>(
			Arrays.asList("ARGV", "ARGC", "ENVIRON", "RSTART", "RLENGTH", "IGNORECASE"));

	private static final class NameValueAssignment {
		private final String name;
		private final Object value;

		private NameValueAssignment(String name, Object value) {
			this.name = name;
			this.value = value;
		}
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

	/**
	 * The value of an address which is not yet assigned a tuple index.
	 */
	public static final int NULL_OFFSET = -1;

}
