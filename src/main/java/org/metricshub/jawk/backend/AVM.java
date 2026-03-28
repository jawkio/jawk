package org.metricshub.jawk.backend;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.metricshub.jawk.AwkSandboxException;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.ext.ExtensionFunction;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.intermediate.Opcode;
import org.metricshub.jawk.intermediate.PositionTracker;
import org.metricshub.jawk.intermediate.UninitializedObject;
import org.metricshub.jawk.jrt.AwkRuntimeException;
import org.metricshub.jawk.jrt.BlockManager;
import org.metricshub.jawk.jrt.BlockObject;
import org.metricshub.jawk.jrt.CharacterTokenizer;
import org.metricshub.jawk.jrt.ConditionPair;
import org.metricshub.jawk.jrt.InputSource;
import org.metricshub.jawk.jrt.StreamInputSource;
import org.metricshub.jawk.jrt.JRT;
import org.metricshub.jawk.jrt.RegexTokenizer;
import org.metricshub.jawk.jrt.SingleCharacterTokenizer;
import org.metricshub.jawk.jrt.VariableManager;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.jrt.BSDRandom;
import org.metricshub.printf4j.Printf4J;

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

	private static final boolean IS_WINDOWS = System.getProperty("os.name").indexOf("Windows") >= 0;

	private RuntimeStack runtimeStack = new RuntimeStack();

	// operand stack
	private Deque<Object> operandStack = new ArrayDeque<Object>();
	private List<String> arguments;
	private boolean sortedArrayKeys;
	private final Map<String, Object> baseInitialVariables;
	private final Map<String, Object> baseSpecialVariables;
	private Map<String, Object> executionInitialVariables;
	private Map<String, Object> executionSpecialVariables;
	private String initialFsValue;
	private boolean trapIllegalFormatExceptions;
	private JRT jrt;
	private final Locale locale;
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
	private boolean inputSourceFilelistAssignmentsApplied;
	private InputSource resolvedInputSource;
	private AwkTuples installedEvalTuples;

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
		boolean hasProvidedSettings = parameters != null;
		this.settings = hasProvidedSettings ? parameters : AwkSettings.DEFAULT_SETTINGS;
		this.extensionInstances = extensionInstances == null ?
				Collections.<String, JawkExtension>emptyMap() : extensionInstances;

		locale = this.settings.getLocale();
		arguments = Collections.emptyList();
		sortedArrayKeys = this.settings.isUseSortedArrayKeys();
		baseInitialVariables = new HashMap<String, Object>(this.settings.getVariables());
		baseSpecialVariables = JRT.copySpecialVariables(baseInitialVariables);
		executionInitialVariables = baseInitialVariables;
		executionSpecialVariables = baseSpecialVariables;
		initialFsValue = this.settings.getFieldSeparator();
		trapIllegalFormatExceptions = hasProvidedSettings
				&& this.settings.isCatchIllegalFormatExceptions();

		jrt = createJrt();
		jrt.setStreams(settings.getOutputStream(), System.err);
		initExtensions();
	}

	protected JRT createJrt() {
		return new JRT(this, settings.getLocale());
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
	 * Evaluate the provided tuples as an AWK expression.
	 *
	 * @param tuples Tuples representing the expression
	 * @param inputSource the input source providing records for the evaluation
	 * @return The resulting value of the expression
	 * @throws IOException if an IO error occurs during evaluation
	 */
	public Object eval(AwkTuples tuples, InputSource inputSource) throws IOException {
		return eval(tuples, inputSource, null);
	}

	/**
	 * Evaluate the provided tuples against the AVM state exactly as it currently
	 * stands. This method does not reset variables, stacks, random state, special
	 * variables, or input fields.
	 * <p>
	 * Use {@link #prepareForEval(InputSource)} before calling this method when you
	 * want a fresh eval state for a new record. Repeated calls to this method
	 * intentionally reuse the same mutable runtime state and therefore form an
	 * expert-only "footgun" API.
	 * </p>
	 *
	 * @param tuples Tuples representing the expression
	 * @return The resulting value of the expression
	 * @throws IOException if an IO error occurs during evaluation
	 */
	public Object eval(AwkTuples tuples) throws IOException {
		AwkTuples compiledTuples = Objects.requireNonNull(tuples, "tuples");
		installEvalTupleMetadata(compiledTuples);

		try {
			executeTuples(compiledTuples.top());
		} catch (ExitException e) {
			// Expression tuples must never contain EXIT opcodes. If callers pass a
			// script tuple stream by mistake, fail fast without poisoning later evals.
			throwExitException = false;
			exitCode = 0;
			throw new IllegalStateException("eval(AwkTuples) cannot execute EXIT opcodes.", e);
		}
		return operandStack.isEmpty() ? null : pop();
	}

	/**
	 * Evaluate the provided tuples as an AWK expression with per-call variable
	 * overrides.
	 * <p>
	 * This method prepares the AVM for the supplied input and then executes the
	 * tuples on the same mutable runtime. It does not automatically release any
	 * bound input or runtime I/O resources afterwards; callers using AVM directly
	 * are responsible for eventually calling {@link #close()}.
	 * </p>
	 *
	 * @param tuples Tuples representing the expression
	 * @param inputSource the input source providing records for the evaluation
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @return The resulting value of the expression
	 * @throws IOException if an IO error occurs during evaluation
	 */
	public Object eval(
			AwkTuples tuples,
			InputSource inputSource,
			Map<String, Object> variableOverrides)
			throws IOException {
		prepareForEval(inputSource, Collections.<String>emptyList(), variableOverrides);
		return eval(tuples);
	}

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
		return prepareForEval(input, Collections.<String>emptyList(), null);
	}

	/**
	 * Resets the interpreter to a fresh eval state and binds one text record as
	 * the current input.
	 *
	 * @param input text record to expose as {@code $0}
	 * @param runtimeArguments CLI-style runtime arguments visible through
	 *        {@code ARGC}/{@code ARGV}; use {@code variableOverrides} for Java
	 *        SDK variable assignments
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @return {@code true} when a record was prepared, {@code false} when the
	 *         provided text represents no input
	 * @throws IOException if binding the input fails
	 */
	public boolean prepareForEval(
			String input,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws IOException {
		return prepareForEval(new SingleRecordInputSource(input), runtimeArguments, variableOverrides);
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

	/**
	 * Resets the interpreter to a fresh eval state and binds at most one record
	 * from the provided input source as the current input. Calling this method
	 * again on the same source advances to the next available record.
	 *
	 * @param inputSource source providing the record to bind
	 * @param runtimeArguments CLI-style runtime arguments visible through
	 *        {@code ARGC}/{@code ARGV}; use {@code variableOverrides} for Java
	 *        SDK variable assignments
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @return {@code true} when a record was prepared, {@code false} when the
	 *         source is exhausted
	 * @throws IOException if reading the input fails
	 */
	public boolean prepareForEval(
			InputSource inputSource,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws IOException {
		InputSource resolvedSource = Objects.requireNonNull(inputSource, "inputSource");
		resetRuntimeState(runtimeArguments, variableOverrides);
		rebindResolvedInputSource(resolvedSource);

		jrt.jrtCloseAll();
		jrt.prepareForExecution(initialFsValue, settings.getDefaultRS(), settings.getDefaultORS());
		if (!executionSpecialVariables.isEmpty()) {
			jrt.applySpecialVariables(executionSpecialVariables);
		}
		return jrt.consumeInputForEval(resolvedInputSource);
	}

	/**
	 * Traverse the tuples, executing their associated opcodes to provide
	 * an execution platform for Jawk scripts.
	 * <p>
	 * When AVM is used directly, the caller is responsible for eventually
	 * calling {@link #close()} to release any bound input and runtime I/O
	 * resources.
	 * </p>
	 *
	 * @param tuples the compiled tuple instructions to execute
	 * @param inputSource the input source providing records
	 * @throws ExitException when the AWK program terminates via {@code exit}
	 * @throws IOException in case of I/O problems (with getline typically)
	 */
	public void interpret(AwkTuples tuples, InputSource inputSource) throws ExitException, IOException {
		interpret(tuples, inputSource, Collections.emptyList());
	}

	/**
	 * Traverse the tuples, executing their associated opcodes to provide
	 * an execution platform for Jawk scripts.
	 * <p>
	 * When AVM is used directly, the caller is responsible for eventually
	 * calling {@link #close()} to release any bound input and runtime I/O
	 * resources.
	 * </p>
	 *
	 * @param tuples the compiled tuple instructions to execute
	 * @param inputSource the input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @throws ExitException when the AWK program terminates via {@code exit}
	 * @throws IOException in case of I/O problems (with getline typically)
	 */
	public void interpret(
			AwkTuples tuples,
			InputSource inputSource,
			List<String> runtimeArguments)
			throws ExitException,
			IOException {
		interpret(tuples, inputSource, runtimeArguments, null);
	}

	/**
	 * Traverse the tuples, executing their associated opcodes to provide
	 * an execution platform for Jawk scripts.
	 * <p>
	 * When AVM is used directly, the caller is responsible for eventually
	 * calling {@link #close()} to release any bound input and runtime I/O
	 * resources.
	 * </p>
	 *
	 * @param tuples the compiled tuple instructions to execute
	 * @param inputSource the input source providing records
	 * @param runtimeArguments name=value or filename entries from the command line
	 * @param variableOverrides additional variable assignments applied on top of
	 *        the settings-level variables (may be {@code null})
	 * @throws ExitException when the AWK program terminates via {@code exit}
	 * @throws IOException in case of I/O problems (with getline typically)
	 */
	public void interpret(
			AwkTuples tuples,
			InputSource inputSource,
			List<String> runtimeArguments,
			Map<String, Object> variableOverrides)
			throws ExitException,
			IOException {
		AwkTuples compiledTuples = Objects.requireNonNull(tuples, "tuples");
		InputSource resolvedSource = Objects.requireNonNull(inputSource, "inputSource");
		resetRuntimeState(runtimeArguments, variableOverrides);
		globalVariableOffsets = compiledTuples.getGlobalVariableOffsetMap();
		globalVariableArrays = compiledTuples.getGlobalVariableAarrayMap();
		functionNames = compiledTuples.getFunctionNameSet();

		jrt.prepareForExecution(initialFsValue, settings.getDefaultRS(), settings.getDefaultORS());
		if (!executionSpecialVariables.isEmpty()) {
			jrt.applySpecialVariables(executionSpecialVariables);
		}
		rebindResolvedInputSource(resolvedSource);
		executeTuples(tuples.top());
	}

	private void resetRuntimeState(List<String> runtimeArguments, Map<String, Object> variableOverrides) {
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
		installedEvalTuples = null;
		runtimeStack.reset();
		randomNumberGenerator.setSeed(1);
		oldseed = 1;

		this.arguments = runtimeArguments != null ? new ArrayList<>(runtimeArguments) : Collections.<String>emptyList();

		if (variableOverrides == null || variableOverrides.isEmpty()) {
			executionInitialVariables = baseInitialVariables;
			executionSpecialVariables = baseSpecialVariables;
		} else {
			executionInitialVariables = new HashMap<String, Object>(baseInitialVariables);
			executionInitialVariables.putAll(variableOverrides);

			Map<String, Object> specialOverrides = JRT.copySpecialVariables(variableOverrides);
			if (specialOverrides.isEmpty()) {
				executionSpecialVariables = baseSpecialVariables;
			} else {
				executionSpecialVariables = new HashMap<String, Object>(baseSpecialVariables);
				executionSpecialVariables.putAll(specialOverrides);
			}
		}
	}

	private void installEvalTupleMetadata(AwkTuples compiledTuples) {
		if (installedEvalTuples == compiledTuples) {
			return;
		}
		globalVariableOffsets = compiledTuples.getGlobalVariableOffsetMap();
		globalVariableArrays = compiledTuples.getGlobalVariableAarrayMap();
		functionNames = compiledTuples.getFunctionNameSet();
		installedEvalTuples = compiledTuples;
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
		try {
			while (!position.isEOF()) {
				// System_out.println("--> "+position);
				Opcode opcode = position.opcode();
				// switch on OPCODE
				switch (opcode) {
				case PRINT: {
					// arg[0] = # of items to print on the stack
					// stack[0] = item 1
					// stack[1] = item 2
					// etc.
					long numArgs = position.intArg(0);
					jrt.printDefault(popArguments(numArgs));
					position.next();
					break;
				}
				case PRINT_TO_FILE: {
					// arg[0] = # of items to print on the stack
					// arg[1] = true=append, false=overwrite
					// stack[0] = output filename
					// stack[1] = item 1
					// stack[2] = item 2
					// etc.
					long numArgs = position.intArg(0);
					boolean append = position.boolArg(1);
					String key = jrt.toAwkString(pop());
					jrt.printToFile(key, append, popArguments(numArgs));
					position.next();
					break;
				}
				case PRINT_TO_PIPE: {
					// arg[0] = # of items to print on the stack
					// stack[0] = command to execute
					// stack[1] = item 1
					// stack[2] = item 2
					// etc.
					long numArgs = position.intArg(0);
					String cmd = jrt.toAwkString(pop());
					jrt.printToProcess(cmd, popArguments(numArgs));
					position.next();
					break;
				}
				case PRINTF: {
					// arg[0] = # of items to print on the stack (includes format string)
					// stack[0] = format string
					// stack[1] = item 1
					// etc.
					long numArgs = position.intArg(0);
					jrt.printfDefault(sprintfFunction(numArgs), IS_WINDOWS);
					position.next();
					break;
				}
				case PRINTF_TO_FILE: {
					// arg[0] = # of items to print on the stack (includes format string)
					// arg[1] = true=append, false=overwrite
					// stack[0] = output filename
					// stack[1] = format string
					// stack[2] = item 1
					// etc.
					long numArgs = position.intArg(0);
					boolean append = position.boolArg(1);
					String key = jrt.toAwkString(pop());
					jrt.printfToFile(key, append, sprintfFunction(numArgs), IS_WINDOWS);
					position.next();
					break;
				}
				case PRINTF_TO_PIPE: {
					// arg[0] = # of items to print on the stack (includes format string)
					// stack[0] = command to execute
					// stack[1] = format string
					// stack[2] = item 1
					// etc.
					long numArgs = position.intArg(0);
					String cmd = jrt.toAwkString(pop());
					jrt.printfToProcess(cmd, sprintfFunction(numArgs), IS_WINDOWS);
					position.next();
					break;
				}
				case SPRINTF: {
					// arg[0] = # of sprintf arguments
					// stack[0] = arg1 (format string)
					// stack[1] = arg2
					// etc.
					long numArgs = position.intArg(0);
					push(sprintfFunction(numArgs));
					position.next();
					break;
				}
				case LENGTH: {
					// arg[0] = 0==use $0, otherwise, use the stack element
					// stack[0] = element to measure (only if arg[0] != 0)

					// print items from the top of the stack
					// # of items
					long num = position.intArg(0);
					if (num == 0) {
						// display $0
						push(jrt.jrtGetInputField(0).toString().length());
					} else {
						push(pop().toString().length());
					}
					position.next();
					break;
				}
				case PUSH_LONG: {
					// arg[0] = long constant to push onto the stack
					push(position.intArg(0));
					position.next();
					break;
				}
				case PUSH_DOUBLE: {
					// arg[0] = double constant to push onto the stack
					push(position.doubleArg(0));
					position.next();
					break;
				}
				case PUSH_STRING: {
					// arg[0] = string constant to push onto the stack
					push(position.stringArg(0));
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
						position.jump(position.addressArg());
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
						position.jump(position.addressArg());
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
					if (JRT.isActuallyLong(d)) {
						push((long) -Math.rint(d));
					} else {
						push(-d);
					}
					position.next();
					break;
				}
				case UNARY_PLUS: {
					// stack[0] = item to convert to a number
					double d = JRT.toDouble(pop());
					if (JRT.isActuallyLong(d)) {
						push((long) Math.rint(d));
					} else {
						push(d);
					}
					position.next();
					break;
				}
				case GOTO: {
					// arg[0] = address

					position.jump(position.addressArg());
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
				case ASSIGN: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = value
					Object value = pop();
					boolean isGlobal = position.boolArg(1);
					assign(position.intArg(0), value, isGlobal, position);
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
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);
					assignArray(offset, arrIdx, rhs, isGlobal);
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
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);

					double val = JRT.toDouble(rhs);

					Map<Object, Object> array = ensureMapVariable(offset, isGlobal);
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

					if (JRT.isActuallyLong(newVal)) {
						assignArray(offset, arrIdx, (long) Math.rint(newVal), isGlobal);
					} else {
						assignArray(offset, arrIdx, newVal, isGlobal);
					}
					position.next();
					break;
				}

				case ASSIGN_AS_INPUT: {
					// stack[0] = value
					jrt.assignInputLineFromGetline(pop());
					push(jrt.getInputLine());
					position.next();
					break;
				}

				case ASSIGN_AS_INPUT_FIELD: {
					// stack[0] = field number
					// stack[1] = value
					Object fieldNumObj = pop();
					long fieldNum = JRT.parseFieldNumber(fieldNumObj);
					String value = pop().toString();
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
					boolean isGlobal = position.boolArg(1);
					Object o1 = runtimeStack.getVariable(position.intArg(0), isGlobal);
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
					if (JRT.isActuallyLong(ans)) {
						long integral = (long) Math.rint(ans);
						push(integral);
						runtimeStack.setVariable(position.intArg(0), integral, isGlobal);
					} else {
						push(ans);
						runtimeStack.setVariable(position.intArg(0), ans, isGlobal);
					}
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
					inc(position.intArg(0), position.boolArg(1));
					position.next();
					break;
				}
				case DEC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					dec(position.intArg(0), position.boolArg(1));
					position.next();
					break;
				}
				case POSTINC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					pop();
					push(inc(position.intArg(0), position.boolArg(1)));
					position.next();
					break;
				}
				case POSTDEC: {
					// arg[0] = offset
					// arg[1] = isGlobal
					pop();
					push(dec(position.intArg(0), position.boolArg(1)));
					position.next();
					break;
				}
				case INC_ARRAY_REF: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					boolean isGlobal = position.boolArg(1);
					Map<Object, Object> aa = ensureMapVariable(position.intArg(0), isGlobal);
					Object key = pop();
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) + 1;
					if (JRT.isActuallyLong(ans)) {
						aa.put(key, (long) Math.rint(ans));
					} else {
						aa.put(key, ans);
					}
					position.next();
					break;
				}
				case DEC_ARRAY_REF: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					boolean isGlobal = position.boolArg(1);
					Map<Object, Object> aa = ensureMapVariable(position.intArg(0), isGlobal);
					Object key = pop();
					Object o = aa.get(key);
					double ans = JRT.toDouble(o) - 1;
					if (JRT.isActuallyLong(ans)) {
						aa.put(key, (long) Math.rint(ans));
					} else {
						aa.put(key, ans);
					}
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

					if (JRT.isActuallyLong(original)) {
						push((long) Math.rint(original));
					} else {
						push(Double.valueOf(original));
					}

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

					if (JRT.isActuallyLong(original)) {
						push((long) Math.rint(original));
					} else {
						push(Double.valueOf(original));
					}

					position.next();
					break;
				}
				case DEREFERENCE: {
					// arg[0] = offset
					// arg[1] = isGlobal
					boolean isGlobal = position.boolArg(2);
					Object o = runtimeStack.getVariable(position.intArg(0), isGlobal);
					if (o == null) {
						if (position.boolArg(1)) {
							// is_array
							push(runtimeStack.setVariable(position.intArg(0), newAwkArray(), isGlobal));
						} else {
							push(runtimeStack.setVariable(position.intArg(0), BLANK, isGlobal));
						}
					} else {
						push(o);
					}
					position.next();
					break;
				}
				case DEREF_ARRAY: {
					// stack[0] = array index
					Object idx = pop(); // idx
					Object array = pop(); // map
					if (!(array instanceof Map)) {
						throw new AwkRuntimeException("Attempting to index a non-associative-array.");
					}
					@SuppressWarnings("unchecked")
					Map<Object, Object> map = (Map<Object, Object>) array;
					Object o = JRT.getAwkValue(map, idx);
					push(o);
					position.next();
					break;
				}
				case SRAND: {
					// arg[0] = numArgs (where 0 = no args, anything else = one argument)
					// stack[0] = seed (only if numArgs != 0)
					long numArgs = position.intArg(0);
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
					// stack[0] = 2nd arg to match() function
					// stack[1] = 1st arg to match() function
					String ere = jrt.toAwkString(pop());
					String s = jrt.toAwkString(pop());

					// check if IGNORECASE set
					int flags = 0;

					if (globalVariableOffsets.containsKey("IGNORECASE")) {
						Integer offsetObj = globalVariableOffsets.get("IGNORECASE");
						Object ignorecase = runtimeStack.getVariable(offsetObj, true);

						if (JRT.toDouble(ignorecase) != 0) {
							flags |= Pattern.CASE_INSENSITIVE;
						}
					}

					Pattern pattern = Pattern.compile(ere, flags);
					Matcher matcher = pattern.matcher(s);
					boolean result = matcher.find();
					if (result) {
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
					// arg[0] = isGlobal
					// stack[0] = replacement string
					// stack[1] = ere
					boolean isGsub = position.boolArg(0);
					String repl = jrt.toAwkString(pop());
					String ere = jrt.toAwkString(pop());
					String orig = jrt.toAwkString(jrt.jrtGetInputField(0));
					String newstring;
					if (isGsub) {
						newstring = replaceAll(orig, ere, repl);
					} else {
						newstring = replaceFirst(orig, ere, repl);
					}
					// assign it to "$0"
					jrt.setInputLine(newstring);
					jrt.jrtParseFields();
					position.next();
					break;
				}
				case SUB_FOR_DOLLAR_REFERENCE: {
					// arg[0] = isGlobal
					// stack[0] = field num
					// stack[1] = original field value
					// stack[2] = replacement string
					// stack[3] = ere
					boolean isGsub = position.boolArg(0);
					long fieldNum = JRT.parseFieldNumber(pop());
					String orig = jrt.toAwkString(pop());
					String repl = jrt.toAwkString(pop());
					String ere = jrt.toAwkString(pop());
					String newstring;
					if (isGsub) {
						newstring = replaceAll(orig, ere, repl);
					} else {
						newstring = replaceFirst(orig, ere, repl);
					}
					// assign it to "$0"
					if (fieldNum == 0) {
						jrt.setInputLine(newstring);
						jrt.jrtParseFields();
					} else {
						jrt.jrtSetInputField(newstring, fieldNum);
					}
					position.next();
					break;
				}
				case SUB_FOR_VARIABLE: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// arg[2] = isGsub
					// stack[0] = original variable value
					// stack[1] = replacement string
					// stack[2] = ere
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);
					String newString = execSubOrGSub(position, 2);
					// assign it to "offset/global"
					assign(offset, newString, isGlobal, position);
					pop();
					position.next();
					break;
				}
				case SUB_FOR_ARRAY_REFERENCE: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// arg[2] = isGsub
					// stack[0] = original variable value
					// stack[1] = replacement string
					// stack[2] = ere
					// stack[3] = array index
					// ARRAY reference offset/isGlobal
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);
					Object arrIdx = pop();
					String newString = execSubOrGSub(position, 2);
					// assign it to "offset/arrIdx/global"
					assignArray(offset, arrIdx, newString, isGlobal);
					pop();
					position.next();
					break;
				}
				case SPLIT: {
					// arg[0] = num args
					// stack[0] = field_sep (only if num args == 3)
					// stack[1] = array
					// stack[2] = string
					long numArgs = position.intArg(0);
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
						assocArray.put(++cnt, tokenizer.nextElement());
					}
					push(cnt);
					position.next();
					break;
				}
				case SUBSTR: {
					// arg[0] = num args
					// stack[0] = length (only if num args == 3)
					// stack[1] = start pos
					// stack[2] = string
					long numArgs = position.intArg(0);
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
					} else {
						if (startPos + length > s.length()) {
							push(s.substring(startPos - 1));
						} else {
							push(s.substring(startPos - 1, startPos + length - 1));
						}
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (JRT.isActuallyLong(ans)) {
						push((long) Math.rint(ans));
					} else {
						push(ans);
					}
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
					if (!(o instanceof Map)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a key list (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					@SuppressWarnings("unchecked")
					Map<Object, Object> map = (Map<Object, Object>) o;
					push(new ArrayDeque<>(map.keySet()));
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
						position.jump(position.addressArg());
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
					Object o = pop();
					if (!position.classArg().isInstance(o)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Verification failed. Top-of-stack = " + o.getClass() + " isn't an instance of " + position.classArg());
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
						position.jump(position.addressArg());
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
					String input = jrt.consumeInputToTarget(resolvedInputSource);
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
					// stack[0] = offset
					//// assignArray(offset, arrIdx, newstring, isGlobal);
					environOffset = position.intArg(0);
					// set the initial variables
					Map<String, String> env = System.getenv();
					for (Map.Entry<String, String> var : env.entrySet()) {
						assignArray(environOffset, var.getKey(), var.getValue(), true);
						pop(); // clean up the stack after the assignment
					}
					position.next();
					break;
				}
				case ARGC_OFFSET: {
					// stack[0] = offset
					argcOffset = position.intArg(0);
					// assign(argcOffset, arguments.size(), true, position); // true = global
					// +1 to include the "jawk" program name (ARGV[0])
					assign(argcOffset, arguments.size() + 1, true, position); // true = global
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case ARGV_OFFSET: {
					// stack[0] = offset
					argvOffset = position.intArg(0);
					// consume argv (looping from 1 to argc)
					int argc = (int) JRT.toDouble(runtimeStack.getVariable(argcOffset, true)); // true = global
					assignArray(argvOffset, 0, "jawk", true);
					pop();
					for (int i = 1; i < argc; i++) {
						// assignArray(argvOffset, i+1, arguments.get(i), true);
						assignArray(argvOffset, i, arguments.get(i - 1), true);
						pop(); // clean up the stack after the assignment
					}
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
					long fieldnum = position.intArg(0);
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
					Address funcAddr = position.addressArg();
					// String func_name = position.arg(1).toString();
					long numFormalParams = position.intArg(2);
					long numActualParams = position.intArg(3);
					runtimeStack.pushFrame(numFormalParams, position.current());
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
					// arg[0] = # of globals
					Object[] globals = runtimeStack.getNumGlobals();
					if (globals == null) {
						runtimeStack.setNumGlobals(position.intArg(0));
						initializedEvalGlobalVariableOffsets = globalVariableOffsets;
						initializedEvalGlobalVariableArrays = globalVariableArrays;

						// now that we have the global variable size,
						// we can allocate the initial variables

						// assign -v variables and per-call overrides prepared for this execution
						for (Map.Entry<String, Object> entry : executionInitialVariables.entrySet()) {
							String key = entry.getKey();
							if (functionNames.contains(key)) {
								throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + key + ").");
							}
							Integer offsetObj = globalVariableOffsets.get(key);
							Boolean arrayObj = globalVariableArrays.get(key);
							if (offsetObj != null) {
								Object obj = entry.getValue();
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
					} else if (!hasCompatibleEvalGlobalLayout(position.intArg(0))) {
						throw new IllegalStateException(
								"AVM globals are already initialized for a different eval layout. Call prepareForEval(...) first.");
					}

					position.next();
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
					// arg[0] = # of elements for SUBSEP application
					// stack[0] = first element
					// stack[1] = second element
					// etc.
					long count = position.intArg(0);
					// String s;
					if (count == 1) {
						push(jrt.toAwkString(pop()));
					} else {
						StringBuilder sb = new StringBuilder();
						sb.append(jrt.toAwkString(pop()));
						String subsep = jrt.toAwkString(jrt.getSUBSEPVar());
						for (int i = 1; i < count; i++) {
							sb.insert(0, subsep);
							sb.insert(0, jrt.toAwkString(pop()));
						}
						push(sb.toString());
					}
					position.next();
					break;
				}
				case DELETE_ARRAY_ELEMENT: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// stack[0] = array index
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);
					Map<Object, Object> aa = getMapVariable(offset, isGlobal);
					Object key = pop();
					if (aa != null) {
						aa.remove(key);
					}
					position.next();
					break;
				}
				case DELETE_ARRAY: {
					// arg[0] = offset
					// arg[1] = isGlobal
					// (nothing on the stack)
					long offset = position.intArg(0);
					boolean isGlobal = position.boolArg(1);
					Map<Object, Object> array = getMapVariable(offset, isGlobal);
					if (array != null) {
						array.clear();
					}
					position.next();
					break;
				}
				case SET_EXIT_ADDRESS: {
					// arg[0] = exit address
					exitAddress = position.addressArg();
					position.next();
					break;
				}
				case SET_WITHIN_END_BLOCKS: {
					// arg[0] = whether within the END blocks section
					withinEndBlocks = position.boolArg(0);
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
					Pattern pattern = position.patternArg(1);
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
					ConditionPair cp = conditionPairs.get(position.current());
					if (cp == null) {
						cp = new ConditionPair();
						conditionPairs.put(position.current(), cp);
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
					ExtensionFunction function = position.extensionFunctionArg();
					long numArgs = position.intArg(1);
					boolean isInitial = position.boolArg(2);

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
						if (!(retval instanceof Integer
								||
								retval instanceof Long
								||
								retval instanceof Double
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
					jrt.setFILENAMEViaJrt(v == null ? "" : v.toString());
					push(v == null ? "" : v.toString());
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
			}

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
	 * Releases any prepared input source and runtime I/O resources owned by this
	 * AVM.
	 * <p>
	 * Call this when you are done with an AVM returned by
	 * {@link org.metricshub.jawk.Awk#prepareEval(String)} or
	 * {@link org.metricshub.jawk.Awk#prepareEval(org.metricshub.jawk.jrt.InputSource)},
	 * or after direct {@link #eval(AwkTuples, InputSource)} /
	 * {@link #interpret(AwkTuples, InputSource)} usage.
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
		// Silly case
		if (numArgs == 0) {
			return "";
		}

		// all but the format argument
		Object[] argArray = new Object[(int) (numArgs - 1)];

		Object[] args = popArguments(numArgs - 1);
		System.arraycopy(args, 0, argArray, 0, args.length);

		// the format argument!
		String fmt = jrt.toAwkString(pop());

		if (trapIllegalFormatExceptions) {
			return Printf4J.sprintf(locale, fmt, argArray);
		} else {
			return JRT.sprintfNoCatch(locale, fmt, argArray);
		}
	}

	private void setNumOnJRT(long fieldNum, double num) {
		String numString;
		if (JRT.isActuallyLong(num)) {
			numString = Long.toString((long) Math.rint(num));
		} else {
			numString = Double.toString(num);
		}

		// same code as ASSIGN_AS_INPUT_FIELD
		if (fieldNum == 0) {
			jrt.setInputLine(numString.toString());
			jrt.jrtParseFields();
		} else {
			jrt.jrtSetInputField(numString, fieldNum);
		}
	}

	private String execSubOrGSub(PositionTracker position, int gsubArgPos) {
		String newString;

		// arg[gsubArgPos] = isGsub
		// stack[0] = original field value
		// stack[1] = replacement string
		// stack[2] = ere
		boolean isGsub = position.boolArg(gsubArgPos);
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
	private void assign(long l, Object value, boolean isGlobal, PositionTracker position) {
		// check if curr value already refers to an array
		if (runtimeStack.getVariable(l, isGlobal) instanceof Map) {
			throw new AwkRuntimeException(position.lineNumber(), "cannot assign anything to an unindexed associative array");
		}
		push(value);
		runtimeStack.setVariable(l, value, isGlobal);
		// When specials are compiled correctly, they use ASSIGN_* and skip this path.
	}

	/**
	 * Awk array element assignment functionality.
	 */
	private void assignArray(long offset, Object arrIdx, Object rhs, boolean isGlobal) {
		Map<Object, Object> array = ensureMapVariable(offset, isGlobal);
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

	public final Object getORS() {
		return jrt.getORSVar();
	}

	/** {@inheritDoc} */
	@Override
	public final Object getSUBSEP() {
		return jrt.getSUBSEPVar();
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
		int eqIdx = nameValue.indexOf('=');
		// variable name should be non-blank
		if (eqIdx == 0) {
			throw new IllegalArgumentException(
					"Must have a non-blank variable name in a name=value variable assignment argument.");
		}
		String name = nameValue.substring(0, eqIdx);
		String value = nameValue.substring(eqIdx + 1);
		Object obj;
		try {
			obj = Integer.parseInt(value);
		} catch (NumberFormatException nfe) {
			try {
				obj = Double.parseDouble(value);
			} catch (NumberFormatException nfe2) {
				obj = value;
			}
		}

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
		}
		// otherwise, do nothing
	}

	/** {@inheritDoc} */
	@Override
	public final void assignVariable(String name, Object obj) {
		// When offsets are not available yet, treat the assignment as part of this
		// AVM's baseline initial-variable snapshot.
		if (globalVariableOffsets == null || globalVariableArrays == null) {
			baseInitialVariables.put(name, obj);
			if (JRT.isJrtManagedSpecialVariable(name)) {
				baseSpecialVariables.put(name, obj);
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
			if (arrayObj.booleanValue()) {
				throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + name + ").");
			} else {
				runtimeStack.setFilelistVariable(offsetObj.intValue(), obj);
			}
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
		jrt.setFILENAMEViaJrt(filename);
	}

	/** {@inheritDoc} */
	@Override
	public Object getARGV() {
		if (argvOffset == NULL_OFFSET) {
			Map<Object, Object> argv = newAwkArray();
			argv.put(0L, "jawk");
			for (int i = 0; i < arguments.size(); i++) {
				argv.put(Long.valueOf(i + 1L), arguments.get(i));
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

	private Map<Object, Object> toMap(Object value) {
		if (!(value instanceof Map)) {
			throw new AwkRuntimeException("Attempting to treat a scalar as an array.");
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> map = (Map<Object, Object>) value;
		return map;
	}

	private static final UninitializedObject BLANK = new UninitializedObject();

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
