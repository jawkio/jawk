package org.metricshub.jawk.backend;

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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.metricshub.jawk.ExitException;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.frontend.AwkSyntaxTree;
import org.metricshub.jawk.intermediate.Address;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.intermediate.Position;
import org.metricshub.jawk.intermediate.PositionForInterpretation;
import org.metricshub.jawk.intermediate.UninitializedObject;
import org.metricshub.jawk.jrt.AssocArray;
import org.metricshub.jawk.jrt.AwkRuntimeException;
import org.metricshub.jawk.jrt.BlockManager;
import org.metricshub.jawk.jrt.BlockObject;
import org.metricshub.jawk.jrt.CharacterTokenizer;
import org.metricshub.jawk.jrt.ConditionPair;
import org.metricshub.jawk.jrt.JRT;
import org.metricshub.jawk.jrt.KeyList;
import org.metricshub.jawk.jrt.KeyListImpl;
import org.metricshub.jawk.jrt.RegexTokenizer;
import org.metricshub.jawk.jrt.SingleCharacterTokenizer;
import org.metricshub.jawk.jrt.VariableManager;
import org.metricshub.jawk.util.ArrayStackImpl;
import org.metricshub.jawk.util.AwkLogger;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.LinkedListStackImpl;
import org.metricshub.jawk.util.MyStack;
import org.metricshub.jawk.util.ScriptSource;
import org.metricshub.printf4j.Printf4J;
import org.slf4j.Logger;

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
 * the AwkSettings, which can contain options pertinent to
 * the interpreter. For example, the interpreter must know about
 * the -v command line argument values, as well as the file/variable list
 * parameter values (ARGC/ARGV) after the script on the command line.
 * However, if programmatic access to the AVM is required, meaningful
 * AwkSettings are not required.
 * <p>
 * Semantic analysis has occurred prior to execution of the interpreter.
 * Therefore, the interpreter throws AwkRuntimeExceptions upon most
 * errors/conditions. It can also throw a <code>java.lang.Error</code> if an
 * interpreter error is encountered.
 *
 * @author Danny Daglas
 */
public class AVM implements AwkInterpreter, VariableManager {

	private static final Logger LOG = AwkLogger.getLogger(AVM.class);
	private static final boolean IS_WINDOWS = System.getProperty("os.name").indexOf("Windows") >= 0;

	private RuntimeStack runtime_stack = new RuntimeStack();

	// 16 slots by default
	// (could be a parameter)
	// private Deque<Object> operand_stack = new ArrayDeque<Object>(16);
	// private MyStack<Object> operand_stack = new LinkedListStackImpl<Object>();
	private MyStack<Object> operand_stack = new ArrayStackImpl<Object>();
	private List<String> arguments;
	private boolean sorted_array_keys;
	private Map<String, Object> initial_variables;
	private String initial_fs_value;
	private boolean trap_illegal_format_exceptions;
	private JRT jrt;
	private final Locale locale;
	private Map<String, JawkExtension> extensions;

	// stack methods
	// private Object pop() { return operand_stack.removeFirst(); }
	// private void push(Object o) { operand_stack.addLast(o); }
	private Object pop() {
		return operand_stack.pop();
	}

	private void push(Object o) {
		operand_stack.push(o);
	}

	private final AwkSettings settings;

	/**
	 * Construct the interpreter.
	 * <p>
	 * Provided to allow programmatic construction of the interpreter
	 * outside of the framework which is used by Jawk.
	 */
	public AVM() {
		settings = null;
		arguments = new ArrayList<String>();
		sorted_array_keys = false;
		initial_variables = new HashMap<String, Object>();
		initial_fs_value = null;
		trap_illegal_format_exceptions = false;
		jrt = new JRT(this); // this = VariableManager
		locale = Locale.getDefault();
		this.extensions = Collections.emptyMap();
	}

	/**
	 * Construct the interpreter, accepting parameters which may have been
	 * set on the command-line arguments to the JVM.
	 *
	 * @param parameters The parameters affecting the behavior of the
	 *        interpreter.
	 * @param extensions Map of the extensions to load
	 */
	public AVM(final AwkSettings parameters, final Map<String, JawkExtension> extensions) {
		if (parameters != null) {
			this.settings = parameters;
			locale = settings.getLocale();
			arguments = parameters.getNameValueOrFileNames();
			sorted_array_keys = parameters.isUseSortedArrayKeys();
			initial_variables = parameters.getVariables();
			initial_fs_value = parameters.getFieldSeparator();
			trap_illegal_format_exceptions = parameters.isCatchIllegalFormatExceptions();
			this.extensions = extensions;
		} else {
			this.settings = null;
			locale = Locale.getDefault();
			arguments = new ArrayList<String>();
			sorted_array_keys = false;
			initial_variables = new HashMap<String, Object>();
			initial_fs_value = null;
			trap_illegal_format_exceptions = false;
			this.extensions = Collections.emptyMap();
		}

		jrt = new JRT(this); // this = VariableManager
		for (JawkExtension ext : this.extensions.values()) {
			ext.init(this, jrt, settings); // this = VariableManager
		}
	}

	private long nf_offset = NULL_OFFSET;
	private long nr_offset = NULL_OFFSET;
	private long fnr_offset = NULL_OFFSET;
	private long fs_offset = NULL_OFFSET;
	private long rs_offset = NULL_OFFSET;
	private long ofs_offset = NULL_OFFSET;
	private long ors_offset = NULL_OFFSET;
	private long rstart_offset = NULL_OFFSET;
	private long rlength_offset = NULL_OFFSET;
	private long filename_offset = NULL_OFFSET;
	private long subsep_offset = NULL_OFFSET;
	private long convfmt_offset = NULL_OFFSET;
	private long ofmt_offset = NULL_OFFSET;
	private long environ_offset = NULL_OFFSET;
	private long argc_offset = NULL_OFFSET;
	private long argv_offset = NULL_OFFSET;

	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);

	private final Random random_number_generator = new Random();
	private int oldseed;

	private Address exit_address = null;

	/**
	 * <code>true</code> if execution position is within an END block;
	 * <code>false</code> otherwise.
	 */
	private boolean within_end_blocks = false;

	/**
	 * Exit code set by the <code>exit NN</code> command (0 by default)
	 */
	private int exit_code = 0;

	/**
	 * Whether <code>exit</code> has been called and we should throw ExitException
	 */
	private boolean throw_exit_exception = false;

	/**
	 * Maps global variable names to their global array offsets.
	 * It is useful when passing variable assignments from the file-list
	 * portion of the command-line arguments.
	 */
	private Map<String, Integer> global_variable_offsets;
	/**
	 * Indicates whether the variable, by name, is a scalar
	 * or not. If not, then it is an Associative Array.
	 */
	private Map<String, Boolean> global_variable_arrays;
	private Set<String> function_names;

	private static int parseIntField(Object obj, PositionForInterpretation position) {
		int fieldVal;

		if (obj instanceof Number) {
			fieldVal = ((Number) obj).intValue();
		} else {
			try {
				fieldVal = (int) Double.parseDouble(obj.toString());
			} catch (NumberFormatException nfe) {
				throw new AwkRuntimeException(position.lineNumber(), "Field $(" + obj.toString() + ") is incorrect.");
			}
		}

		return fieldVal;
	}

	private void setNumOnJRT(int fieldNum, double num) {
		String numString;
		if (JRT.isActuallyLong(num)) {
			numString = Long.toString((long) Math.rint(num));
		} else {
			numString = Double.toString(num);
		}

		// same code as _ASSIGN_AS_INPUT_FIELD_
		if (fieldNum == 0) {
			jrt.setInputLine(numString.toString());
			jrt.jrtParseFields();
		} else {
			jrt.jrtSetInputField(numString, fieldNum);
		}
	}

	private String execSubOrGSub(PositionForInterpretation position, int gsubArgPos) {
		String newString;

		// arg[gsubArgPos] = is_gsub
		// stack[0] = original field value
		// stack[1] = replacement string
		// stack[2] = ere
		boolean is_gsub = position.boolArg(gsubArgPos);
		String convfmt = getCONVFMT().toString();
		String orig = JRT.toAwkString(pop(), convfmt, locale);
		String repl = JRT.toAwkString(pop(), convfmt, locale);
		String ere = JRT.toAwkString(pop(), convfmt, locale);
		if (is_gsub) {
			newString = replaceAll(orig, ere, repl);
		} else {
			newString = replaceFirst(orig, ere, repl);
		}

		return newString;
	}

	/**
	 * {@inheritDoc}
	 * Traverse the tuples, executing their associated opcodes to provide
	 * an execution platform for Jawk scripts.
	 *
	 * @throws IOException in case of I/O problems (with getline typically)
	 */
	@Override
	public void interpret(AwkTuples tuples) throws ExitException, IOException {
		Map<String, Pattern> regexps = new HashMap<String, Pattern>();
		Map<Integer, ConditionPair> condition_pairs = new HashMap<Integer, ConditionPair>();

		global_variable_offsets = tuples.getGlobalVariableOffsetMap();
		global_variable_arrays = tuples.getGlobalVariableAarrayMap();
		function_names = tuples.getFunctionNameSet();

		PositionForInterpretation position = (PositionForInterpretation) tuples.top();

		try {
			while (!position.isEOF()) {
				// System_out.println("--> "+position);
				int opcode = position.opcode();
				// switch on OPCODE
				switch (opcode) {
				case AwkTuples._PRINT_: {
					// arg[0] = # of items to print on the stack
					// stack[0] = item 1
					// stack[1] = item 2
					// etc.
					long num_args = position.intArg(0);
					printTo(settings.getOutputStream(), num_args);
					position.next();
					break;
				}
				case AwkTuples._PRINT_TO_FILE_: {
					// arg[0] = # of items to print on the stack
					// arg[1] = true=append, false=overwrite
					// stack[0] = output filename
					// stack[1] = item 1
					// stack[2] = item 2
					// etc.
					long num_args = position.intArg(0);
					boolean append = position.boolArg(1);
					String key = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					PrintStream ps = jrt.getOutputFiles().get(key);
					if (ps == null) {
						try {
							jrt
									.getOutputFiles()
									.put(
											key,
											ps = new PrintStream(new FileOutputStream(key, append), true, StandardCharsets.UTF_8.name())); // true
							// =
							// autoflush
						} catch (IOException ioe) {
							throw new AwkRuntimeException(position.lineNumber(), "Cannot open " + key + " for writing: " + ioe);
						}
					}
					printTo(ps, num_args);
					position.next();
					break;
				}
				case AwkTuples._PRINT_TO_PIPE_: {
					// arg[0] = # of items to print on the stack
					// stack[0] = command to execute
					// stack[1] = item 1
					// stack[2] = item 2
					// etc.
					long num_args = position.intArg(0);
					String cmd = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					PrintStream ps = jrt.jrtSpawnForOutput(cmd);
					printTo(ps, num_args);
					position.next();
					break;
				}
				case AwkTuples._PRINTF_: {
					// arg[0] = # of items to print on the stack (includes format string)
					// stack[0] = format string
					// stack[1] = item 1
					// etc.
					long num_args = position.intArg(0);
					printfTo(settings.getOutputStream(), num_args);
					position.next();
					break;
				}
				case AwkTuples._PRINTF_TO_FILE_: {
					// arg[0] = # of items to print on the stack (includes format string)
					// arg[1] = true=append, false=overwrite
					// stack[0] = output filename
					// stack[1] = format string
					// stack[2] = item 1
					// etc.
					long num_args = position.intArg(0);
					boolean append = position.boolArg(1);
					String key = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					PrintStream ps = jrt.getOutputFiles().get(key);
					if (ps == null) {
						try {
							jrt
									.getOutputFiles()
									.put(
											key,
											ps = new PrintStream(new FileOutputStream(key, append), true, StandardCharsets.UTF_8.name())); // true
							// =
							// autoflush
						} catch (IOException ioe) {
							throw new AwkRuntimeException(position.lineNumber(), "Cannot open " + key + " for writing: " + ioe);
						}
					}
					printfTo(ps, num_args);
					position.next();
					break;
				}
				case AwkTuples._PRINTF_TO_PIPE_: {
					// arg[0] = # of items to print on the stack (includes format string)
					// stack[0] = command to execute
					// stack[1] = format string
					// stack[2] = item 1
					// etc.
					long num_args = position.intArg(0);
					String cmd = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					PrintStream ps = jrt.jrtSpawnForOutput(cmd);
					printfTo(ps, num_args);
					position.next();
					break;
				}
				case AwkTuples._SPRINTF_: {
					// arg[0] = # of sprintf arguments
					// stack[0] = arg1 (format string)
					// stack[1] = arg2
					// etc.
					long num_args = position.intArg(0);
					push(sprintfFunction(num_args));
					position.next();
					break;
				}
				case AwkTuples._LENGTH_: {
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
				case AwkTuples._PUSH_: {
					// arg[0] = constant to push onto the stack
					push(position.arg(0));
					position.next();
					break;
				}
				case AwkTuples._POP_: {
					// stack[0] = item to pop from the stack
					pop();
					position.next();
					break;
				}
				case AwkTuples._IFFALSE_: {
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
				case AwkTuples._TO_NUMBER_: {
					// stack[0] = item to convert to a number

					// if int, then check for 0
					// if double, then check for 0
					// if String, then check for "" or double value of "0"
					boolean val = jrt.toBoolean(pop());
					push(val ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._IFTRUE_: {
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
				case AwkTuples._NOT_: {
					// stack[0] = item to logically negate

					// if int, then check for 0
					// if double, then check for 0
					// if String, then check for "" or double value of "0"
					Object o = pop();
					boolean result;
					if (o instanceof Integer) {
						result = ((Integer) o).intValue() != 0;
					} else if (o instanceof Long) {
						result = ((Long) o).longValue() != 0;
					} else if (o instanceof Double) {
						result = ((Double) o).doubleValue() != 0;
					} else if (o instanceof String) {
						result = (o.toString().length() > 0);
					} else if (o instanceof UninitializedObject) {
						result = false;
					} else {
						throw new Error("Unknown operand_stack type: " + o.getClass() + " for value " + o);
					}
					if (result) {
						push(0);
					} else {
						push(1);
					}
					position.next();
					break;
				}
				case AwkTuples._NEGATE_: {
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
				case AwkTuples._UNARY_PLUS_: {
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
				case AwkTuples._GOTO_: {
					// arg[0] = address

					position.jump(position.addressArg());
					break;
				}
				case AwkTuples._NOP_: {
					// do nothing, just advance the position
					position.next();
					break;
				}
				case AwkTuples._CONCAT_: {
					// stack[0] = string1
					// stack[1] = string2
					String convfmt = getCONVFMT().toString();
					String s2 = JRT.toAwkString(pop(), convfmt, locale);
					String s1 = JRT.toAwkString(pop(), convfmt, locale);
					String result_string = s1 + s2;
					push(result_string);
					position.next();
					break;
				}
				case AwkTuples._ASSIGN_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = value
					Object value = pop();
					boolean is_global = position.boolArg(1);
					assign(position.intArg(0), value, is_global, position);
					position.next();
					break;
				}
				case AwkTuples._ASSIGN_ARRAY_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = array index
					// stack[1] = value
					Object arr_idx = pop();
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);
					assignArray(offset, arr_idx, rhs, is_global);
					position.next();
					break;
				}
				case AwkTuples._PLUS_EQ_ARRAY_:
				case AwkTuples._MINUS_EQ_ARRAY_:
				case AwkTuples._MULT_EQ_ARRAY_:
				case AwkTuples._DIV_EQ_ARRAY_:
				case AwkTuples._MOD_EQ_ARRAY_:
				case AwkTuples._POW_EQ_ARRAY_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = array index
					// stack[1] = value
					Object arr_idx = pop();
					Object rhs = pop();
					if (rhs == null) {
						rhs = BLANK;
					}
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);

					double val = JRT.toDouble(rhs);

					// from _DEREF_ARRAY_
					// stack[0] = AssocArray
					// stack[1] = array index
					Object o1 = runtime_stack.getVariable(offset, is_global); // map
					if (o1 == null || o1 instanceof UninitializedObject) {
						runtime_stack.setVariable(offset, o1 = new AssocArray(sorted_array_keys), is_global);
					} else {
						assert o1 instanceof AssocArray;
					}

					AssocArray array = (AssocArray) o1;
					Object o = array.get(arr_idx);
					assert o != null;
					double orig_val = JRT.toDouble(o);

					double new_val;

					switch (opcode) {
					case AwkTuples._PLUS_EQ_ARRAY_:
						new_val = orig_val + val;
						break;
					case AwkTuples._MINUS_EQ_ARRAY_:
						new_val = orig_val - val;
						break;
					case AwkTuples._MULT_EQ_ARRAY_:
						new_val = orig_val * val;
						break;
					case AwkTuples._DIV_EQ_ARRAY_:
						new_val = orig_val / val;
						break;
					case AwkTuples._MOD_EQ_ARRAY_:
						new_val = orig_val % val;
						break;
					case AwkTuples._POW_EQ_ARRAY_:
						new_val = Math.pow(orig_val, val);
						break;
					default:
						throw new Error("Invalid op code here: " + opcode);
					}

					if (JRT.isActuallyLong(new_val)) {
						assignArray(offset, arr_idx, (long) Math.rint(new_val), is_global);
					} else {
						assignArray(offset, arr_idx, new_val, is_global);
					}
					position.next();
					break;
				}
				case AwkTuples._ASSIGN_AS_INPUT_: {
					// stack[0] = value
					jrt.setInputLine(pop().toString());
					jrt.jrtParseFields();
					push(jrt.getInputLine());
					position.next();
					break;
				}
				case AwkTuples._ASSIGN_AS_INPUT_FIELD_: {
					// stack[0] = field number
					// stack[1] = value
					Object field_num_obj = pop();
					int field_num;
					if (field_num_obj instanceof Number) {
						field_num = ((Number) field_num_obj).intValue();
					} else {
						try {
							field_num = Integer.parseInt(field_num_obj.toString());
						} catch (NumberFormatException nfe) {
							field_num = 0;
						}
					}
					String value = pop().toString();
					push(value); // leave the result on the stack
					if (field_num == 0) {
						jrt.setInputLine(value);
						jrt.jrtParseFields();
					} else {
						jrt.jrtSetInputField(value, field_num);
					}
					position.next();
					break;
				}
				case AwkTuples._PLUS_EQ_:
				case AwkTuples._MINUS_EQ_:
				case AwkTuples._MULT_EQ_:
				case AwkTuples._DIV_EQ_:
				case AwkTuples._MOD_EQ_:
				case AwkTuples._POW_EQ_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = value
					boolean is_global = position.boolArg(1);
					Object o1 = runtime_stack.getVariable(position.intArg(0), is_global);
					if (o1 == null) {
						o1 = BLANK;
					}
					Object o2 = pop();
					double d1 = JRT.toDouble(o1);
					double d2 = JRT.toDouble(o2);
					double ans;
					switch (opcode) {
					case AwkTuples._PLUS_EQ_:
						ans = d1 + d2;
						break;
					case AwkTuples._MINUS_EQ_:
						ans = d1 - d2;
						break;
					case AwkTuples._MULT_EQ_:
						ans = d1 * d2;
						break;
					case AwkTuples._DIV_EQ_:
						ans = d1 / d2;
						break;
					case AwkTuples._MOD_EQ_:
						ans = d1 % d2;
						break;
					case AwkTuples._POW_EQ_:
						ans = Math.pow(d1, d2);
						break;
					default:
						throw new Error("Invalid opcode here: " + opcode);
					}
					if (JRT.isActuallyLong(ans)) {
						long integral = (long) Math.rint(ans);
						push(integral);
						runtime_stack.setVariable(position.intArg(0), integral, is_global);
					} else {
						push(ans);
						runtime_stack.setVariable(position.intArg(0), ans, is_global);
					}
					position.next();
					break;
				}
				case AwkTuples._PLUS_EQ_INPUT_FIELD_:
				case AwkTuples._MINUS_EQ_INPUT_FIELD_:
				case AwkTuples._MULT_EQ_INPUT_FIELD_:
				case AwkTuples._DIV_EQ_INPUT_FIELD_:
				case AwkTuples._MOD_EQ_INPUT_FIELD_:
				case AwkTuples._POW_EQ_INPUT_FIELD_: {
					// stack[0] = dollar_field_number
					// stack[1] = inc value

					// same code as _GET_INPUT_FIELD_:
					int fieldnum = parseIntField(pop(), position);
					double incval = JRT.toDouble(pop());

					// except here, get the number, and add the incvalue
					Object num_obj = jrt.jrtGetInputField(fieldnum);
					double num;
					switch (opcode) {
					case AwkTuples._PLUS_EQ_INPUT_FIELD_:
						num = JRT.toDouble(num_obj) + incval;
						break;
					case AwkTuples._MINUS_EQ_INPUT_FIELD_:
						num = JRT.toDouble(num_obj) - incval;
						break;
					case AwkTuples._MULT_EQ_INPUT_FIELD_:
						num = JRT.toDouble(num_obj) * incval;
						break;
					case AwkTuples._DIV_EQ_INPUT_FIELD_:
						num = JRT.toDouble(num_obj) / incval;
						break;
					case AwkTuples._MOD_EQ_INPUT_FIELD_:
						num = JRT.toDouble(num_obj) % incval;
						break;
					case AwkTuples._POW_EQ_INPUT_FIELD_:
						num = Math.pow(JRT.toDouble(num_obj), incval);
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
				case AwkTuples._INC_: {
					// arg[0] = offset
					// arg[1] = is_global
					inc(position.intArg(0), position.boolArg(1));
					position.next();
					break;
				}
				case AwkTuples._DEC_: {
					// arg[0] = offset
					// arg[1] = is_global
					dec(position.intArg(0), position.boolArg(1));
					position.next();
					break;
				}
				case AwkTuples._POSTINC_: {
					// arg[0] = offset
					// arg[1] = is_global
					pop();
					push(inc(position.intArg(0), position.boolArg(1)));
					position.next();
					break;
				}
				case AwkTuples._POSTDEC_: {
					// arg[0] = offset
					// arg[1] = is_global
					pop();
					push(dec(position.intArg(0), position.boolArg(1)));
					position.next();
					break;
				}
				case AwkTuples._INC_ARRAY_REF_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = array index
					boolean is_global = position.boolArg(1);
					Object o1 = runtime_stack.getVariable(position.intArg(0), is_global);
					if (o1 == null || o1 instanceof UninitializedObject) {
						runtime_stack.setVariable(position.intArg(0), o1 = new AssocArray(sorted_array_keys), is_global);
					}
					AssocArray aa = (AssocArray) o1;
					Object key = pop();
					Object o = aa.get(key);
					assert o != null;
					double ans = JRT.toDouble(o) + 1;
					if (JRT.isActuallyLong(ans)) {
						aa.put(key, (long) Math.rint(ans));
					} else {
						aa.put(key, ans);
					}
					position.next();
					break;
				}
				case AwkTuples._DEC_ARRAY_REF_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = array index
					boolean is_global = position.boolArg(1);
					Object o1 = runtime_stack.getVariable(position.intArg(0), is_global);
					if (o1 == null || o1 instanceof UninitializedObject) {
						runtime_stack.setVariable(position.intArg(0), o1 = new AssocArray(sorted_array_keys), is_global);
					}
					AssocArray aa = (AssocArray) o1;
					Object key = pop();
					Object o = aa.get(key);
					assert o != null;
					double ans = JRT.toDouble(o) - 1;
					if (JRT.isActuallyLong(ans)) {
						aa.put(key, (long) Math.rint(ans));
					} else {
						aa.put(key, ans);
					}
					position.next();
					break;
				}
				case AwkTuples._INC_DOLLAR_REF_: {
					// stack[0] = dollar index (field number)
					// same code as _GET_INPUT_FIELD_:
					int fieldnum = parseIntField(pop(), position);
					// except here, get the number, and add one
					// push(avmGetInputField(fieldnum));
					Object num_obj = jrt.jrtGetInputField(fieldnum);
					double num = JRT.toDouble(num_obj) + 1;
					setNumOnJRT(fieldnum, num);

					position.next();
					break;
				}
				case AwkTuples._DEC_DOLLAR_REF_: {
					// stack[0] = dollar index (field number)
					// same code as _GET_INPUT_FIELD_:
					int fieldnum = parseIntField(pop(), position);
					// except here, get the number, and add one
					// push(avmGetInputField(fieldnum));
					Object num_obj = jrt.jrtGetInputField(fieldnum);
					double num = JRT.toDouble(num_obj) - 1;
					setNumOnJRT(fieldnum, num);

					position.next();
					break;
				}
				case AwkTuples._DEREFERENCE_: {
					// arg[0] = offset
					// arg[1] = is_global
					boolean is_global = position.boolArg(2);
					Object o = runtime_stack.getVariable(position.intArg(0), is_global);
					if (o == null) {
						if (position.boolArg(1)) {
							// is_array
							push(runtime_stack.setVariable(position.intArg(0), new AssocArray(sorted_array_keys), is_global));
						} else {
							push(runtime_stack.setVariable(position.intArg(0), BLANK, is_global));
						}
					} else {
						push(o);
					}
					position.next();
					break;
				}
				case AwkTuples._DEREF_ARRAY_: {
					// stack[0] = array index
					// stack[1] = AssocArray
					Object idx = pop(); // idx
					Object array = pop(); // map
					if (!(array instanceof AssocArray)) {
						throw new AwkRuntimeException("Attempting to index a non-associative-array.");
					}
					Object o = ((AssocArray) array).get(idx);
					assert o != null;
					push(o);
					position.next();
					break;
				}
				case AwkTuples._SRAND_: {
					// arg[0] = num_args (where 0 = no args, anything else = one argument)
					// stack[0] = seed (only if num_args != 0)
					long numargs = position.intArg(0);
					int seed;
					if (numargs == 0) {
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
					random_number_generator.setSeed(seed);
					push(oldseed);
					oldseed = seed;
					position.next();
					break;
				}
				case AwkTuples._RAND_: {
					push(random_number_generator.nextDouble());
					position.next();
					break;
				}
				case AwkTuples._INTFUNC_:
				case AwkTuples._CAST_INT_: {
					// stack[0] = arg to int() function
					push((long) JRT.toDouble(pop()));
					position.next();
					break;
				}
				case AwkTuples._SQRT_: {
					// stack[0] = arg to sqrt() function
					push(Math.sqrt(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case AwkTuples._LOG_: {
					// stack[0] = arg to log() function
					push(Math.log(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case AwkTuples._EXP_: {
					// stack[0] = arg to exp() function
					push(Math.exp(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case AwkTuples._SIN_: {
					// stack[0] = arg to sin() function
					push(Math.sin(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case AwkTuples._COS_: {
					// stack[0] = arg to cos() function
					push(Math.cos(JRT.toDouble(pop())));
					position.next();
					break;
				}
				case AwkTuples._ATAN2_: {
					// stack[0] = 2nd arg to atan2() function
					// stack[1] = 1st arg to atan2() function
					double d2 = JRT.toDouble(pop());
					double d1 = JRT.toDouble(pop());
					push(Math.atan2(d1, d2));
					position.next();
					break;
				}
				case AwkTuples._MATCH_: {
					// stack[0] = 2nd arg to match() function
					// stack[1] = 1st arg to match() function
					String convfmt = getCONVFMT().toString();
					String ere = JRT.toAwkString(pop(), convfmt, locale);
					String s = JRT.toAwkString(pop(), convfmt, locale);

					// check if IGNORECASE set
					int flags = 0;

					if (global_variable_offsets.containsKey("IGNORECASE")) {
						Integer offset_obj = global_variable_offsets.get("IGNORECASE");
						Object ignorecase = runtime_stack.getVariable(offset_obj, true);

						if (JRT.toDouble(ignorecase) != 0) {
							flags |= Pattern.CASE_INSENSITIVE;
						}
					}

					Pattern pattern = Pattern.compile(ere, flags);
					Matcher matcher = pattern.matcher(s);
					boolean result = matcher.find();
					if (result) {
						assign(rstart_offset, matcher.start() + 1, true, position);
						assign(rlength_offset, matcher.end() - matcher.start(), true, position);
						pop();
						// end up with RSTART on the stack
					} else {
						assign(rstart_offset, ZERO, true, position);
						assign(rlength_offset, -1, true, position);
						pop();
						// end up with RSTART on the stack
					}
					position.next();
					break;
				}
				case AwkTuples._INDEX_: {
					// stack[0] = 2nd arg to index() function
					// stack[1] = 1st arg to index() function
					String convfmt = getCONVFMT().toString();
					String s2 = JRT.toAwkString(pop(), convfmt, locale);
					String s1 = JRT.toAwkString(pop(), convfmt, locale);
					push(s1.indexOf(s2) + 1);
					position.next();
					break;
				}
				case AwkTuples._SUB_FOR_DOLLAR_0_: {
					// arg[0] = is_global
					// stack[0] = replacement string
					// stack[1] = ere
					boolean is_gsub = position.boolArg(0);
					String convfmt = getCONVFMT().toString();
					String repl = JRT.toAwkString(pop(), convfmt, locale);
					String ere = JRT.toAwkString(pop(), convfmt, locale);
					String orig = JRT.toAwkString(jrt.jrtGetInputField(0), convfmt, locale);
					String newstring;
					if (is_gsub) {
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
				case AwkTuples._SUB_FOR_DOLLAR_REFERENCE_: {
					// arg[0] = is_global
					// stack[0] = field num
					// stack[1] = original field value
					// stack[2] = replacement string
					// stack[3] = ere
					boolean is_gsub = position.boolArg(0);
					String convfmt = getCONVFMT().toString();
					int fieldNum = (int) JRT.toDouble(pop());
					String orig = JRT.toAwkString(pop(), convfmt, locale);
					String repl = JRT.toAwkString(pop(), convfmt, locale);
					String ere = JRT.toAwkString(pop(), convfmt, locale);
					String newstring;
					if (is_gsub) {
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
				case AwkTuples._SUB_FOR_VARIABLE_: {
					// arg[0] = offset
					// arg[1] = is_global
					// arg[2] = is_gsub
					// stack[0] = original variable value
					// stack[1] = replacement string
					// stack[2] = ere
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);
					String newString = execSubOrGSub(position, 2);
					// assign it to "offset/global"
					assign(offset, newString, is_global, position);
					pop();
					position.next();
					break;
				}
				case AwkTuples._SUB_FOR_ARRAY_REFERENCE_: {
					// arg[0] = offset
					// arg[1] = is_global
					// arg[2] = is_gsub
					// stack[0] = original variable value
					// stack[1] = replacement string
					// stack[2] = ere
					// stack[3] = array index
					// ARRAY reference offset/is_global
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);
					Object arr_idx = pop();
					String newString = execSubOrGSub(position, 2);
					// assign it to "offset/arr_idx/global"
					assignArray(offset, arr_idx, newString, is_global);
					pop();
					position.next();
					break;
				}
				case AwkTuples._SPLIT_: {
					// arg[0] = num args
					// stack[0] = field_sep (only if num args == 3)
					// stack[1] = array
					// stack[2] = string
					String convfmt = getCONVFMT().toString();
					long numargs = position.intArg(0);
					String fs_string;
					if (numargs == 2) {
						fs_string = JRT.toAwkString(getFS(), convfmt, locale);
					} else if (numargs == 3) {
						fs_string = JRT.toAwkString(pop(), convfmt, locale);
					} else {
						throw new Error("Invalid # of args. split() requires 2 or 3. Got: " + numargs);
					}
					Object o = pop();
					if (!(o instanceof AssocArray)) {
						throw new AwkRuntimeException(position.lineNumber(), o + " is not an array.");
					}
					String s = JRT.toAwkString(pop(), convfmt, locale);
					Enumeration<Object> tokenizer;
					if (fs_string.equals(" ")) {
						tokenizer = new StringTokenizer(s);
					} else if (fs_string.length() == 1) {
						tokenizer = new SingleCharacterTokenizer(s, fs_string.charAt(0));
					} else if (fs_string.isEmpty()) {
						tokenizer = new CharacterTokenizer(s);
					} else {
						tokenizer = new RegexTokenizer(s, fs_string);
					}

					AssocArray assoc_array = (AssocArray) o;
					assoc_array.clear();
					int cnt = 0;
					while (tokenizer.hasMoreElements()) {
						assoc_array.put(++cnt, tokenizer.nextElement());
					}
					push(cnt);
					position.next();
					break;
				}
				case AwkTuples._SUBSTR_: {
					// arg[0] = num args
					// stack[0] = length (only if num args == 3)
					// stack[1] = start pos
					// stack[2] = string
					long numargs = position.intArg(0);
					int startPos, length;
					String s;
					if (numargs == 3) {
						length = (int) JRT.toLong(pop());
						startPos = (int) JRT.toDouble(pop());
						s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					} else if (numargs == 2) {
						startPos = (int) JRT.toDouble(pop());
						s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
						length = s.length() - startPos + 1;
					} else {
						throw new Error("numargs for _SUBSTR_ must be 2 or 3. It is " + numargs);
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
				case AwkTuples._TOLOWER_: {
					// stack[0] = string
					push(JRT.toAwkString(pop(), getCONVFMT().toString(), locale).toLowerCase());
					position.next();
					break;
				}
				case AwkTuples._TOUPPER_: {
					// stack[0] = string
					push(JRT.toAwkString(pop(), getCONVFMT().toString(), locale).toUpperCase());
					position.next();
					break;
				}
				case AwkTuples._SYSTEM_: {
					// stack[0] = command string
					String s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					push(JRT.jrtSystem(s));
					position.next();
					break;
				}
				case AwkTuples._SWAP_: {
					// stack[0] = item1
					// stack[1] = item2
					swapOnStack();
					position.next();
					break;
				}
				case AwkTuples._CMP_EQ_: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, 0) ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._CMP_LT_: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, -1) ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._CMP_GT_: {
					// stack[0] = item2
					// stack[1] = item1
					Object o2 = pop();
					Object o1 = pop();
					push(JRT.compare2(o1, o2, 1) ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._MATCHES_: {
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
						String r = JRT.toAwkString(o2, getCONVFMT().toString(), locale);
						boolean result = Pattern.compile(r).matcher(s).find();
						push(result ? 1 : 0);
					}
					position.next();
					break;
				}
				case AwkTuples._SLEEP_: {
					// arg[0] = num_args
					// if (num_args==1)
					// stack[0] = # of seconds
					// else
					// nothing on the stack
					// int seconds = (int) JRT.toDouble(pop());
					long seconds;
					long numargs = position.intArg(0);
					if (numargs == 0) {
						seconds = 1;
					} else {
						seconds = (long) JRT.toDouble(pop());
					}
					try {
						Thread.sleep(seconds * 1000);
					} catch (InterruptedException ie) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Caught exception while waiting for process exit: " + ie);
					}
					position.next();
					break;
				}
				case AwkTuples._DUMP_: {
					// arg[0] = num_args
					// if (num_args==0)
					// all Jawk global variables
					// else
					// args are assoc arrays to display
					// int seconds = (int) JRT.toDouble(pop());
					long numargs = position.intArg(0);
					AssocArray[] aa_array;
					if (numargs == 0) {
						aa_array = null;
					} else {
						aa_array = new AssocArray[(int) numargs];
						for (int i = 0; i < numargs; ++i) {
							aa_array[i] = (AssocArray) pop();
						}
					}
					avmDump(aa_array);
					position.next();
					break;
				}
				case AwkTuples._ADD_: {
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
				case AwkTuples._SUBTRACT_: {
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
				case AwkTuples._MULTIPLY_: {
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
				case AwkTuples._DIVIDE_: {
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
				case AwkTuples._MOD_: {
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
				case AwkTuples._POW_: {
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
				case AwkTuples._DUP_: {
					// stack[0] = top of stack item
					Object o = pop();
					push(o);
					push(o);
					position.next();
					break;
				}
				case AwkTuples._KEYLIST_: {
					// stack[0] = AssocArray
					Object o = pop();
					assert o != null;
					if (!(o instanceof AssocArray)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a keylist (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					AssocArray aa = (AssocArray) o;
					push(new KeyListImpl(aa.keySet()));
					position.next();
					break;
				}
				case AwkTuples._IS_EMPTY_KEYLIST_: {
					// arg[0] = address
					// stack[0] = KeyList
					Object o = pop();
					if (o == null || !(o instanceof KeyList)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a keylist (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					KeyList keylist = (KeyList) o;
					if (keylist.size() == 0) {
						position.jump(position.addressArg());
					} else {
						position.next();
					}
					break;
				}
				case AwkTuples._GET_FIRST_AND_REMOVE_FROM_KEYLIST_: {
					// stack[0] = KeyList
					Object o = pop();
					if (o == null || !(o instanceof KeyList)) {
						throw new AwkRuntimeException(
								position.lineNumber(),
								"Cannot get a keylist (via 'in') of a non associative array. arg = " + o.getClass() + ", " + o);
					}
					// pop off and return the head of the key set
					KeyList keylist = (KeyList) o;
					assert keylist.size() > 0;
					push(keylist.getFirstAndRemove());
					position.next();
					break;
				}
				case AwkTuples._CHECK_CLASS_: {
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
				case AwkTuples._CONSUME_INPUT_: {
					// arg[0] = address
					// false = do NOT put result on stack...
					// instead, put it in field vars ($0, $1, ...)
					if (avmConsumeInput(false)) {
						position.next();
					} else {
						position.jump(position.addressArg());
					}
					break;
				}
				case AwkTuples._GETLINE_INPUT_: {
					avmConsumeInputForGetline();
					position.next();
					break;
				}
				case AwkTuples._USE_AS_FILE_INPUT_: {
					// stack[0] = filename
					String s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					avmConsumeFileInputForGetline(s);
					position.next();
					break;
				}
				case AwkTuples._USE_AS_COMMAND_INPUT_: {
					// stack[0] = command line
					String s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					avmConsumeCommandInputForGetline(s);
					position.next();
					break;
				}
				case AwkTuples._NF_OFFSET_: {
					// stack[0] = offset
					nf_offset = position.intArg(0);
					assert nf_offset != NULL_OFFSET;
					assign(nf_offset, 0, true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._NR_OFFSET_: {
					// stack[0] = offset
					nr_offset = position.intArg(0);
					assert nr_offset != NULL_OFFSET;
					assign(nr_offset, 0, true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._FNR_OFFSET_: {
					// stack[0] = offset
					fnr_offset = position.intArg(0);
					assert fnr_offset != NULL_OFFSET;
					assign(fnr_offset, 0, true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._FS_OFFSET_: {
					// stack[0] = offset
					fs_offset = position.intArg(0);
					assert fs_offset != NULL_OFFSET;
					if (initial_fs_value == null) {
						assign(fs_offset, " ", true, position);
					} else {
						assign(fs_offset, initial_fs_value, true, position);
					}
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._RS_OFFSET_: {
					// stack[0] = offset
					rs_offset = position.intArg(0);
					assert rs_offset != NULL_OFFSET;
					assign(rs_offset, settings.getDefaultRS(), true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._OFS_OFFSET_: {
					// stack[0] = offset
					ofs_offset = position.intArg(0);
					assert ofs_offset != NULL_OFFSET;
					assign(ofs_offset, " ", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._ORS_OFFSET_: {
					// stack[0] = offset
					ors_offset = position.intArg(0);
					assert ors_offset != NULL_OFFSET;
					assign(ors_offset, settings.getDefaultORS(), true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._RSTART_OFFSET_: {
					// stack[0] = offset
					rstart_offset = position.intArg(0);
					assert rstart_offset != NULL_OFFSET;
					assign(rstart_offset, "", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._RLENGTH_OFFSET_: {
					// stack[0] = offset
					rlength_offset = position.intArg(0);
					assert rlength_offset != NULL_OFFSET;
					assign(rlength_offset, "", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._FILENAME_OFFSET_: {
					// stack[0] = offset
					filename_offset = position.intArg(0);
					assert filename_offset != NULL_OFFSET;
					assign(filename_offset, "", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._SUBSEP_OFFSET_: {
					// stack[0] = offset
					subsep_offset = position.intArg(0);
					assert subsep_offset != NULL_OFFSET;
					assign(subsep_offset, String.valueOf((char) 28), true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._CONVFMT_OFFSET_: {
					// stack[0] = offset
					convfmt_offset = position.intArg(0);
					assert convfmt_offset != NULL_OFFSET;
					assign(convfmt_offset, "%.6g", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._OFMT_OFFSET_: {
					// stack[0] = offset
					ofmt_offset = position.intArg(0);
					assert ofmt_offset != NULL_OFFSET;
					assign(ofmt_offset, "%.6g", true, position);
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._ENVIRON_OFFSET_: {
					// stack[0] = offset
					//// assignArray(offset, arr_idx, newstring, is_global);
					environ_offset = position.intArg(0);
					assert environ_offset != NULL_OFFSET;
					// set the initial variables
					Map<String, String> env = System.getenv();
					for (Map.Entry<String, String> var : env.entrySet()) {
						assignArray(environ_offset, var.getKey(), var.getValue(), true);
						pop(); // clean up the stack after the assignment
					}
					position.next();
					break;
				}
				case AwkTuples._ARGC_OFFSET_: {
					// stack[0] = offset
					argc_offset = position.intArg(0);
					assert argc_offset != NULL_OFFSET;
					// assign(argc_offset, arguments.size(), true, position); // true = global
					// +1 to include the "java Awk" (ARGV[0])
					assign(argc_offset, arguments.size() + 1, true, position); // true = global
					pop(); // clean up the stack after the assignment
					position.next();
					break;
				}
				case AwkTuples._ARGV_OFFSET_: {
					// stack[0] = offset
					argv_offset = position.intArg(0);
					assert argv_offset != NULL_OFFSET;
					// consume argv (looping from 1 to argc)
					int argc = (int) JRT.toDouble(runtime_stack.getVariable(argc_offset, true)); // true = global
					assignArray(argv_offset, 0, "java Awk", true);
					pop();
					for (int i = 1; i < argc; i++) {
						// assignArray(argv_offset, i+1, arguments.get(i), true);
						assignArray(argv_offset, i, arguments.get(i - 1), true);
						pop(); // clean up the stack after the assignment
					}
					position.next();
					break;
				}
				case AwkTuples._GET_INPUT_FIELD_: {
					// stack[0] = field number
					int fieldnum = parseIntField(pop(), position);
					push(jrt.jrtGetInputField(fieldnum));
					position.next();
					break;
				}
				case AwkTuples._APPLY_RS_: {
					assert rs_offset != NULL_OFFSET;
					Object rs_obj = runtime_stack.getVariable(rs_offset, true); // true = global
					if (jrt.getPartitioningReader() != null) {
						jrt.getPartitioningReader().setRecordSeparator(rs_obj.toString());
					}
					position.next();
					break;
				}
				case AwkTuples._CALL_FUNCTION_: {
					// arg[0] = function address
					// arg[1] = function name
					// arg[2] = # of formal parameters
					// arg[3] = # of actual parameters
					// stack[0] = last actual parameter
					// stack[1] = before-last actual parameter
					// ...
					// stack[n-1] = first actual parameter
					// etc.
					Address func_addr = position.addressArg();
					// String func_name = position.arg(1).toString();
					long num_formal_params = position.intArg(2);
					long num_actual_params = position.intArg(3);
					assert num_formal_params >= num_actual_params;
					runtime_stack.pushFrame(num_formal_params, position.current());
					// Arguments are stacked, so first in the stack is the last for the function
					for (long i = num_actual_params - 1; i >= 0; i--) {
						runtime_stack.setVariable(i, pop(), false); // false = local
					}
					position.jump(func_addr);
					// position.next();
					break;
				}
				case AwkTuples._FUNCTION_: {
					// important for compilation,
					// not needed for interpretation
					// arg[0] = function name
					// arg[1] = # of formal parameters
					position.next();
					break;
				}
				case AwkTuples._SET_RETURN_RESULT_: {
					// stack[0] = return result
					runtime_stack.setReturnValue(pop());
					position.next();
					break;
				}
				case AwkTuples._RETURN_FROM_FUNCTION_: {
					position.jump(runtime_stack.popFrame());
					push(runtime_stack.getReturnValue());
					position.next();
					break;
				}
				case AwkTuples._SET_NUM_GLOBALS_: {
					// arg[0] = # of globals
					assert position.intArg(0) == global_variable_offsets.size();
					runtime_stack.setNumGlobals(position.intArg(0));

					// now that we have the global variable size,
					// we can allocate the initial variables

					// assign -v variables (from initial_variables container)
					for (Map.Entry<String, Object> entry : initial_variables.entrySet()) {
						String key = entry.getKey();
						if (function_names.contains(key)) {
							throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + key + ").");
						}
						Integer offset_obj = global_variable_offsets.get(key);
						Boolean array_obj = global_variable_arrays.get(key);
						if (offset_obj != null) {
							assert array_obj != null;
							if (array_obj.booleanValue()) {
								throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + key + ").");
							} else {
								Object obj = entry.getValue();
								runtime_stack.setFilelistVariable(offset_obj.intValue(), obj);
							}
						}
					}

					position.next();
					break;
				}
				case AwkTuples._CLOSE_: {
					// stack[0] = file or command line to close
					String s = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					push(jrt.jrtClose(s));
					position.next();
					break;
				}
				case AwkTuples._APPLY_SUBSEP_: {
					// arg[0] = # of elements for SUBSEP application
					// stack[0] = first element
					// stack[1] = second element
					// etc.
					long count = position.intArg(0);
					assert count >= 1;
					// String s;
					String convfmt = getCONVFMT().toString();
					if (count == 1) {
						push(JRT.toAwkString(pop(), convfmt, locale));
					} else {
						StringBuilder sb = new StringBuilder();
						sb.append(JRT.toAwkString(pop(), convfmt, locale));
						String subsep = JRT.toAwkString(runtime_stack.getVariable(subsep_offset, true), convfmt, locale);
						for (int i = 1; i < count; i++) {
							sb.insert(0, subsep);
							sb.insert(0, JRT.toAwkString(pop(), convfmt, locale));
						}
						push(sb.toString());
					}
					position.next();
					break;
				}
				case AwkTuples._DELETE_ARRAY_ELEMENT_: {
					// arg[0] = offset
					// arg[1] = is_global
					// stack[0] = array index
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);
					AssocArray aa = (AssocArray) runtime_stack.getVariable(offset, is_global);
					Object key = pop();
					if (aa != null) {
						aa.remove(key);
					}
					position.next();
					break;
				}
				case AwkTuples._DELETE_ARRAY_: {
					// arg[0] = offset
					// arg[1] = is_global
					// (nothing on the stack)
					long offset = position.intArg(0);
					boolean is_global = position.boolArg(1);
					runtime_stack.removeVariable(offset, is_global);
					position.next();
					break;
				}
				case AwkTuples._SET_EXIT_ADDRESS_: {
					// arg[0] = exit address
					exit_address = position.addressArg();
					position.next();
					break;
				}
				case AwkTuples._SET_WITHIN_END_BLOCKS_: {
					// arg[0] = whether within the END blocks section
					within_end_blocks = position.boolArg(0);
					position.next();
					break;
				}
				case AwkTuples._EXIT_WITHOUT_CODE_:
				case AwkTuples._EXIT_WITH_CODE_: {
					if (opcode == AwkTuples._EXIT_WITH_CODE_) {
						// stack[0] = exit code
						exit_code = (int) JRT.toDouble(pop());
					}
					throw_exit_exception = true;

					// If in BEGIN or in a rule, jump to the END section
					if (!within_end_blocks) {
						// clear runtime stack
						runtime_stack.popAllFrames();
						// clear operand stack
						operand_stack.clear();
						position.jump(exit_address);
					} else {
						// Exit immediately with ExitException
						jrt.jrtCloseAll();
						// clear operand stack
						operand_stack.clear();
						throw new ExitException(exit_code, "The AWK script requested an exit");
						// position.next();
					}
					break;
				}
				case AwkTuples._REGEXP_: {
					// arg[0] = string representation of regexp
					String key = JRT.toAwkString(position.arg(0), getCONVFMT().toString(), locale);
					Pattern pattern = regexps.get(key);
					if (pattern == null) {
						regexps.put(key, pattern = Pattern.compile(key));
					}
					push(pattern);
					position.next();
					break;
				}
				case AwkTuples._CONDITION_PAIR_: {
					// stack[0] = End condition
					// stack[1] = Start condition
					ConditionPair cp = condition_pairs.get(position.current());
					if (cp == null) {
						cp = new ConditionPair();
						condition_pairs.put(position.current(), cp);
					}
					boolean end = jrt.toBoolean(pop());
					boolean start = jrt.toBoolean(pop());
					push(cp.update(start, end) ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._IS_IN_: {
					// stack[0] = AssocArray
					// stack[1] = key to check
					Object arr = pop();
					Object arg = pop();
					AssocArray aa = (AssocArray) arr;
					boolean result = aa.isIn(arg);
					push(result ? ONE : ZERO);
					position.next();
					break;
				}
				case AwkTuples._CAST_DOUBLE_: {
					push(JRT.toDouble(pop()));
					position.next();
					break;
				}
				case AwkTuples._CAST_STRING_: {
					push(pop().toString());
					position.next();
					break;
				}
				case AwkTuples._THIS_: {
					// this is in preparation for a function
					// call for the JVM-COMPILED script, only
					// therefore, do NOTHING for the interpreted
					// version
					position.next();
					break;
				}
				case AwkTuples._EXEC_: {
					// stack[0] = Jawk code

					// TODO FIXME First attempt. It is not complete by a long-shot. Use at your own risk.

					String awk_code = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);
					List<ScriptSource> scriptSources = new ArrayList<ScriptSource>(1);
					scriptSources
							.add(new ScriptSource(ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT, new StringReader(awk_code), false));

					org.metricshub.jawk.frontend.AwkParser ap = new org.metricshub.jawk.frontend.AwkParser(
							// true, true, true, extensions
							settings.isAdditionalFunctions(),
							settings.isAdditionalTypeFunctions(),
							extensions);
					try {
						AwkSyntaxTree ast = ap.parse(scriptSources);
						if (ast != null) {
							ast.semanticAnalysis();
							ast.semanticAnalysis();
							AwkTuples new_tuples = new AwkTuples();
							int result = ast.populateTuples(new_tuples);
							assert result == 0;
							new_tuples.postProcess();
							ap.populateGlobalVariableNameToOffsetMappings(new_tuples);
							AVM new_avm = new AVM(settings, extensions);
							int subScriptExitCode = 0;
							try {
								new_avm.interpret(new_tuples);
							} catch (ExitException ex) {
								subScriptExitCode = ex.getCode();
							}
							push(subScriptExitCode);
						} else {
							push(-1);
						}
					} catch (IOException ioe) {
						throw new AwkRuntimeException(position.lineNumber(), "IO Exception caught : " + ioe);
					}

					position.next();
					break;
				}
				case AwkTuples._EXTENSION_: {
					// arg[0] = extension keyword
					// arg[1] = # of args on the stack
					// arg[2] = true if parent is NOT an extension function call
					// (i.e., initial extension in calling expression)
					// stack[0] = first actual parameter
					// stack[1] = second actual parameter
					// etc.
					String extension_keyword = position.arg(0).toString();
					long num_args = position.intArg(1);
					boolean is_initial = position.boolArg(2);

					JawkExtension extension = extensions.get(extension_keyword);
					if (extension == null) {
						throw new AwkRuntimeException("Extension for '" + extension_keyword + "' not found.");
					}

					Object[] args = new Object[(int) num_args];
					for (int i = (int) num_args - 1; i >= 0; i--) {
						args[i] = pop();
					}

					Object retval = extension.invoke(extension_keyword, args);

					// block if necessary
					// (convert retval into the return value
					// from the block operation ...)
					if (is_initial && retval != null && retval instanceof BlockObject) {
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
								retval instanceof AssocArray
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
				default:
					throw new Error("invalid opcode: " + AwkTuples.toOpcodeString(position.opcode()));
				}
			}

			// End of the instructions
			jrt.jrtCloseAll();
		} catch (RuntimeException re) {
			LOG.error("", re);
			LOG.error("operand_stack = {}", operand_stack);
			LOG.error("position = {}", position);
			LOG.error("line number = {}", position.lineNumber());

			// clear runtime stack
			runtime_stack.popAllFrames();
			// clear operand stack
			operand_stack.clear();

			throw re;
		} catch (AssertionError ae) {
			LOG.error("", ae);
			LOG.error("operand_stack = {}", operand_stack);
			try {
				LOG.error("position = {}", position);
			} catch (Throwable t) {
				LOG.error("{ could not report on position", t);
			}
			try {
				LOG.error("line number = {}", position.lineNumber());
			} catch (Throwable t) {
				LOG.error("{ could not report on line number", t);
			}
			throw ae;
		}

		// If <code>exit</code> was called, throw an ExitException
		if (throw_exit_exception) {
			throw new ExitException(exit_code, "The AWK script requested an exit");
		}
	}

	/**
	 * Close all streams in the runtime
	 */
	public void waitForIO() {
		jrt.jrtCloseAll();
	}

	private void avmDump(AssocArray[] aa_array) {
		if (aa_array == null) {
			// dump the runtime stack
			Object[] globals = runtime_stack.getNumGlobals();
			for (Map.Entry<String, Integer> e : global_variable_offsets.entrySet()) {
				String name = e.getKey();
				int idx = e.getValue();
				Object value = globals[idx];
				if (value instanceof AssocArray) {
					AssocArray aa = (AssocArray) value;
					value = aa.mapString();
				}
				LOG.info("{} = {}", name, value);
			}
		} else {
			// dump associative arrays
			for (AssocArray aa : aa_array) {
				LOG.info(aa.mapString());
			}
		}
	}

	private void printTo(PrintStream ps, long num_args) {
		// print items from the top of the stack
		// # of items
		if (num_args == 0) {
			// display $0
			ps.print(jrt.jrtGetInputField(0));
			ps.print(getORS().toString());
		} else {
			// cache $OFS to separate fields below
			// (no need to execute getOFS for each field)
			String ofs_string = getOFS().toString();

			// Arguments are stacked, so we need to reverse order
			Object[] args = new Object[(int) num_args];
			for (int i = (int) num_args - 1; i >= 0; i--) {
				args[i] = pop();
			}

			// Now print
			for (int i = 0; i < num_args; i++) {
				ps.print(JRT.toAwkStringForOutput(args[i], getOFMT().toString(), locale));
				// if more elements, display $FS
				if (i < num_args - 1) {
					// use $OFS to separate fields
					ps.print(ofs_string);
				}
			}
			ps.print(getORS().toString());
		}
		// for now, since we are not using Process.waitFor()
		if (IS_WINDOWS) {
			ps.flush();
		}
	}

	private void printfTo(PrintStream ps, long num_args) {
		// assert num_args > 0;
		ps.print(sprintfFunction(num_args));
		// for now, since we are not using Process.waitFor()
		if (IS_WINDOWS) {
			ps.flush();
		}
	}

	/**
	 * sprintf() functionality
	 */
	private String sprintfFunction(long num_args) {
		// Silly case
		if (num_args == 0)
			return "";

		// all but the format argument
		Object[] arg_array = new Object[(int) (num_args - 1)];

		// for each sprintf argument, put it into an
		// array used in the String.format method
		// Arguments are stacked, so we need to reverse their order
		for (int i = (int) num_args - 2; i >= 0; i--) {
			arg_array[i] = pop();
		}

		// the format argument!
		String fmt = JRT.toAwkString(pop(), getCONVFMT().toString(), locale);

		if (trap_illegal_format_exceptions) {
			return Printf4J.sprintf(locale, fmt, arg_array);
		} else {
			return JRT.sprintfNoCatch(locale, fmt, arg_array);
		}
	}

	private StringBuffer replace_first_sb = new StringBuffer();

	/**
	 * sub() functionality
	 */
	private String replaceFirst(String orig, String ere, String repl) {
		push(JRT.replaceFirst(orig, repl, ere, replace_first_sb));
		return replace_first_sb.toString();
	}

	private StringBuffer replace_all_sb = new StringBuffer();

	/**
	 * gsub() functionality
	 */
	private String replaceAll(String orig, String ere, String repl) {
		push(JRT.replaceAll(orig, repl, ere, replace_all_sb));
		return replace_all_sb.toString();
	}

	/**
	 * Awk variable assignment functionality.
	 */
	private void assign(long l, Object value, boolean is_global, Position position) {
		// check if curr value already refers to an array
		if (runtime_stack.getVariable(l, is_global) instanceof AssocArray) {
			throw new AwkRuntimeException(position.lineNumber(), "cannot assign anything to an unindexed associative array");
		}
		push(value);
		runtime_stack.setVariable(l, value, is_global);
	}

	/**
	 * Awk array element assignment functionality.
	 */
	private void assignArray(long offset, Object arr_idx, Object rhs, boolean is_global) {
		Object o1 = runtime_stack.getVariable(offset, is_global);
		if (o1 == null || o1.equals(BLANK)) {
			runtime_stack.setVariable(offset, o1 = new AssocArray(sorted_array_keys), is_global);
		}
		assert o1 != null;
		// The only (conceivable) way to contradict
		// the assertion (below) is by passing in
		// a scalar to an unindexed associative array
		// via a -v argument without safeguards to
		// prohibit this.
		// Therefore, guard against this elsewhere, not here.
		// if (! (o1 instanceof AssocArray))
		// throw new AwkRuntimeException("Attempting to treat a scalar as an array.");
		assert o1 instanceof AssocArray;
		AssocArray array = (AssocArray) o1;

		// Convert arr_idx to a true integer if it is one
		// String indexString = JRT.toAwkStringForOutput(arr_idx, getCONVFMT().toString());
		array.put(arr_idx, rhs);
		push(rhs);
	}

	/**
	 * Numerically increases an Awk variable by one; the result
	 * is placed back into that variable.
	 */
	private Object inc(long l, boolean is_global) {
		Object o = runtime_stack.getVariable(l, is_global);
		if (o == null || o instanceof UninitializedObject) {
			runtime_stack.setVariable(l, o = ZERO, is_global);
		}
		runtime_stack.setVariable(l, JRT.inc(o), is_global);
		return o;
	}

	/**
	 * Numerically decreases an Awk variable by one; the result
	 * is placed back into that variable.
	 */
	private Object dec(long l, boolean is_global) {
		Object o = runtime_stack.getVariable(l, is_global);
		if (o == null) {
			runtime_stack.setVariable(l, o = ZERO, is_global);
		}
		runtime_stack.setVariable(l, JRT.dec(o), is_global);
		return o;
	}

	/** {@inheritDoc} */
	@Override
	public final Object getRS() {
		assert rs_offset != NULL_OFFSET;
		Object rs_obj = runtime_stack.getVariable(rs_offset, true); // true = global
		return rs_obj;
	}

	/** {@inheritDoc} */
	@Override
	public final Object getOFS() {
		assert ofs_offset != NULL_OFFSET;
		Object ofs_obj = runtime_stack.getVariable(ofs_offset, true); // true = global
		return ofs_obj;
	}

	public final Object getORS() {
		return runtime_stack.getVariable(ors_offset, true); // true = global
	}

	/** {@inheritDoc} */
	@Override
	public final Object getSUBSEP() {
		assert subsep_offset != NULL_OFFSET;
		Object subsep_obj = runtime_stack.getVariable(subsep_offset, true); // true = global
		return subsep_obj;
	}

	/**
	 * Performs the global variable assignment within the runtime environment.
	 * These assignments come from the ARGV list (bounded by ARGC), which, in
	 * turn, come from the command-line arguments passed into Awk.
	 *
	 * @param name_value The variable assignment in <i>name=value</i> form.
	 */
	@SuppressWarnings("unused")
	private void setFilelistVariable(String name_value) {
		int eq_idx = name_value.indexOf('=');
		// variable name should be non-blank
		assert eq_idx >= 0;
		if (eq_idx == 0) {
			throw new IllegalArgumentException(
					"Must have a non-blank variable name in a name=value variable assignment argument.");
		}
		String name = name_value.substring(0, eq_idx);
		String value = name_value.substring(eq_idx + 1);
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
		if (function_names.contains(name)) {
			throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + name + ").");
		}

		Integer offset_obj = global_variable_offsets.get(name);
		Boolean array_obj = global_variable_arrays.get(name);

		if (offset_obj != null) {
			assert array_obj != null;
			if (array_obj.booleanValue()) {
				throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + name + ").");
			} else {
				runtime_stack.setFilelistVariable(offset_obj.intValue(), obj);
			}
		}
		// otherwise, do nothing
	}

	/** {@inheritDoc} */
	@Override
	public final void assignVariable(String name, Object obj) {
		// make sure we're not receiving funcname=value assignments
		if (function_names.contains(name)) {
			throw new IllegalArgumentException("Cannot assign a scalar to a function name (" + name + ").");
		}

		Integer offset_obj = global_variable_offsets.get(name);
		Boolean array_obj = global_variable_arrays.get(name);

		if (offset_obj != null) {
			assert array_obj != null;
			if (array_obj.booleanValue()) {
				throw new IllegalArgumentException("Cannot assign a scalar to a non-scalar variable (" + name + ").");
			} else {
				runtime_stack.setFilelistVariable(offset_obj.intValue(), obj);
			}
		}
	}

	private void swapOnStack() {
		Object o1 = pop();
		Object o2 = pop();
		push(o1);
		push(o2);
	}

	private void avmConsumeInputForGetline() throws IOException {
		if (avmConsumeInput(true)) {
			push(1);
		} else {
			push("");
			push(0);
		}
		swapOnStack();
	}

	private void avmConsumeFileInputForGetline(String filename) throws IOException {
		if (avmConsumeFileInput(filename)) {
			push(1);
		} else {
			push(0);
		}
		swapOnStack();
	}

	private void avmConsumeCommandInputForGetline(String cmd) throws IOException {
		if (avmConsumeCommandInput(cmd)) {
			push(1);
		} else {
			push(0);
		}
		swapOnStack();
	}

	private boolean avmConsumeFileInput(String filename) throws IOException {
		boolean retval = jrt.jrtConsumeFileInput(filename);
		if (retval) {
			push(jrt.getInputLine());
		} else {
			push("");
		}
		return retval;
	}

	private boolean avmConsumeCommandInput(String cmd) throws IOException {
		boolean retval = jrt.jrtConsumeCommandInput(cmd);
		if (retval) {
			push(jrt.getInputLine());
		} else {
			push("");
		}
		return retval;
	}

	private boolean avmConsumeInput(boolean for_getline) throws IOException {
		boolean retval = jrt.jrtConsumeInput(settings.getInput(), for_getline, locale);
		if (retval && for_getline) {
			push(jrt.getInputLine());
		}
		return retval;
	}

	/** {@inheritDoc} */
	@Override
	public Object getFS() {
		assert fs_offset != NULL_OFFSET;
		Object fs_string = runtime_stack.getVariable(fs_offset, true); // true = global
		return fs_string;
	}

	/** {@inheritDoc} */
	@Override
	public Object getCONVFMT() {
		assert convfmt_offset != NULL_OFFSET : "convfmt_offset not defined";
		Object convfmt_string = runtime_stack.getVariable(convfmt_offset, true); // true = global
		return convfmt_string;
	}

	/** {@inheritDoc} */
	@Override
	public void resetFNR() {
		runtime_stack.setVariable(fnr_offset, ZERO, true);
	}

	/** {@inheritDoc} */
	@Override
	public void incFNR() {
		inc(fnr_offset, true);
	}

	/** {@inheritDoc} */
	@Override
	public void incNR() {
		inc(nr_offset, true);
	}

	/** {@inheritDoc} */
	@Override
	public void setNF(Integer I) {
		runtime_stack.setVariable(nf_offset, I, true);
	}

	/** {@inheritDoc} */
	@Override
	public void setFILENAME(String filename) {
		runtime_stack.setVariable(filename_offset, filename, true);
	}

	/** {@inheritDoc} */
	@Override
	public Object getARGV() {
		return runtime_stack.getVariable(argv_offset, true);
	}

	/** {@inheritDoc} */
	@Override
	public Object getARGC() {
		return runtime_stack.getVariable(argc_offset, true);
	}

	private String getOFMT() {
		assert ofmt_offset != NULL_OFFSET;
		String ofmt_string = runtime_stack.getVariable(ofmt_offset, true).toString(); // true = global
		return ofmt_string;
	}

	private static final UninitializedObject BLANK = new UninitializedObject();

	/**
	 * The value of an address which is not yet assigned a tuple index.
	 */
	public static final int NULL_OFFSET = -1;

	private static class RuntimeStack {

		private Object[] globals = null;
		private Object[] locals = null;
		private MyStack<Object[]> locals_stack = new ArrayStackImpl<Object[]>();
		private MyStack<Integer> return_indexes = new LinkedListStackImpl<Integer>();

		@SuppressWarnings("unused")
		public void dump() {
			LOG.info("globals = " + Arrays.toString(globals));
			LOG.info("locals = " + Arrays.toString(locals));
			LOG.info("locals_stack = " + locals_stack);
			LOG.info("return_indexes = " + return_indexes);
		}

		Object[] getNumGlobals() {
			return globals;
		}

		/**
		 * Must be one of the first methods executed.
		 */
		void setNumGlobals(long l) {
			assert l >= 0;
			assert globals == null;
			globals = new Object[(int) l];
			for (int i = 0; i < l; i++) {
				globals[i] = null;
			}
			// must accept multiple executions
			// expandFrameIfNecessary(num_globals);
		}

		/*
		 * // this assumes globals = Object[0] upon initialization
		 * private void expandFrameIfNecessary(int num_globals) {
		 * if (num_globals == globals.length)
		 * // no need for expansion;
		 * // do nothing
		 * return;
		 * Object[] new_frame = new Object[num_globals];
		 * for (int i=0;i<globals.length;++i)
		 * new_frame[i] = globals[i];
		 * globals = new_frame;
		 * }
		 */

		Object getVariable(long offset, boolean is_global) {
			assert globals != null;
			assert offset != NULL_OFFSET;
			if (is_global) {
				return globals[(int) offset];
			} else {
				return locals[(int) offset];
			}
		}

		Object setVariable(long offset, Object val, boolean is_global) {
			assert globals != null;
			assert offset != NULL_OFFSET;
			if (is_global) {
				return globals[(int) offset] = val;
			} else {
				return locals[(int) offset] = val;
			}
		}

		// for _DELETE_ARRAY_
		void removeVariable(long offset, boolean is_global) {
			assert globals != null;
			assert offset != NULL_OFFSET;
			if (is_global) {
				assert globals[(int) offset] == null || globals[(int) offset] instanceof AssocArray;
				globals[(int) offset] = null;
			} else {
				assert locals[(int) offset] == null || locals[(int) offset] instanceof AssocArray;
				locals[(int) offset] = null;
			}
		}

		void setFilelistVariable(int offset, Object value) {
			assert globals != null;
			assert offset != NULL_OFFSET;
			globals[offset] = value;
		}

		void pushFrame(long num_formal_params, int position_idx) {
			locals_stack.push(locals);
			locals = new Object[(int) num_formal_params];
			return_indexes.push(position_idx);
		}

		/** returns the position index */
		int popFrame() {
			locals = locals_stack.pop();
			return return_indexes.pop();
		}

		void popAllFrames() {
			int sz = locals_stack.size();
			while (--sz >= 0) {
				locals = locals_stack.pop();
				return_indexes.pop();
			}
		}

		private Object return_value;

		void setReturnValue(Object obj) {
			assert return_value == null;
			return_value = obj;
		}

		Object getReturnValue() {
			Object retval;
			if (return_value == null) {
				retval = BLANK;
			} else {
				retval = return_value;
			}
			return_value = null;
			return retval;
		}
	}
}
