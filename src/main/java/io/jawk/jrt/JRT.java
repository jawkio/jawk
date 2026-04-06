package io.jawk.jrt;

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

// There must be NO imports to io.jawk.*,
// other than io.jawk.jrt which occurs by
// default. We wish to house all
// required runtime classes in jrt.jar,
// not have to refer to jawk.jar!

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import io.jawk.intermediate.UninitializedObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The Jawk runtime coordinator.
 * The JRT services interpreted and compiled Jawk scripts, mainly
 * for IO and other non-CPU bound tasks. The goal is to house
 * service functions into a Java-compiled class rather than
 * to hand-craft service functions in byte-code, or cut-paste
 * compiled JVM code into the compiled AWK script. Also,
 * since these functions are non-CPU bound, the need for
 * inlining is reduced.
 * <p>
 * Variable access is achieved through the VariableManager interface.
 * The constructor requires a VariableManager instance (which, in
 * this case, is the compiled Jawk class itself).
 * <p>
 * Main services include:
 * <ul>
 * <li>File and command output redirection via print(f).
 * <li>File and command input redirection via getline.
 * <li>Most built-in AWK functions, such as system(), sprintf(), etc.
 * <li>Automatic AWK type conversion routines.
 * <li>IO management for input rule processing.
 * <li>Random number engine management.
 * <li>Input field ($0, $1, ...) management.
 * </ul>
 * <p>
 * All static and non-static service methods should be package-private
 * to the resultant AWK script class rather than public. However,
 * the resultant script class is not in the <code>io.jawk.jrt</code> package
 * by default, and the user may reassign the resultant script class
 * to another package. Therefore, all accessed methods are public.
 *
 * @see VariableManager
 * @author Danny Daglas
 */
public class JRT {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").indexOf("Windows") >= 0;

	private final VariableManager vm;

	private IoState ioState;
	/** Output sink used for plain AWK print/printf output. */
	private AwkSink awkSink;
	/** PrintStream used for command error output */
	private PrintStream error;
	// Last input line consumed for getline-style transport.
	private String inputLine = null;
	// Current record state ($0, $1, $2, ...).
	private RecordState recordState;
	// The currently active InputSource (set during consumeInput calls).
	private InputSource activeSource;
	private static final UninitializedObject BLANK = new UninitializedObject();

	private static final Integer ONE = Integer.valueOf(1);
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer MINUS_ONE = Integer.valueOf(-1);
	private String jrtInputString;

	// JRT-managed special variables (runtime only)
	private long nr; // total record number
	private long fnr; // file record number
	private int rstart; // last match start (1-based)
	private int rlength; // last match length
	private String filename; // current input filename (or empty for stdin/pipe)
	private String fs; // field separator
	private String rs; // record separator (regexp)
	private String ofs; // output field separator
	private String ors; // output record separator
	private String convfmt; // number-to-string format
	private String ofmt; // number-to-string for output
	private String subsep; // subscript separator
	private final Locale locale; // locale for number formatting

	private static final class FileOutputState {

		private final AwkSink sink;

		private FileOutputState(AwkSink sinkParam) {
			this.sink = Objects.requireNonNull(sinkParam, "sink");
		}
	}

	private static final class CommandInputState {

		private final Process process;
		private final PartitioningReader reader;
		private final Thread errorPump;

		private CommandInputState(Process processParam, PartitioningReader readerParam, Thread errorPumpParam) {
			this.process = Objects.requireNonNull(processParam, "process");
			this.reader = Objects.requireNonNull(readerParam, "reader");
			this.errorPump = errorPumpParam;
		}
	}

	private static final class ProcessOutputState {

		private final Process process;
		private final AwkSink sink;
		private final PrintStream processOutput;
		private final Thread stdoutPump;
		private final Thread stderrPump;

		private ProcessOutputState(
				Process processParam,
				AwkSink sinkParam,
				PrintStream processOutputParam,
				Thread stdoutPumpParam,
				Thread stderrPumpParam) {
			this.process = Objects.requireNonNull(processParam, "process");
			this.sink = Objects.requireNonNull(sinkParam, "sink");
			this.processOutput = Objects.requireNonNull(processOutputParam, "processOutput");
			this.stdoutPump = stdoutPumpParam;
			this.stderrPump = stderrPumpParam;
		}
	}

	private static final class IoState {

		private final Map<String, PartitioningReader> fileReaders = new HashMap<String, PartitioningReader>();
		private final Map<String, CommandInputState> commandInputs = new HashMap<String, CommandInputState>();
		private final Map<String, FileOutputState> fileOutputs = new HashMap<String, FileOutputState>();
		private final Map<String, ProcessOutputState> processOutputs = new HashMap<String, ProcessOutputState>();
	}

	/**
	 * Create a JRT with explicit default output and error streams.
	 *
	 * @param vm The VariableManager to use with this JRT.
	 * @param locale The Locale to use for number formatting.
	 * @param awkSink default output sink used by plain AWK print operations
	 * @param error default error stream used for process stderr
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "JRT must hold the provided runtime collaborators for later use")
	public JRT(VariableManager vm, Locale locale, AwkSink awkSink, PrintStream error) {
		this.vm = vm;
		this.locale = locale == null ? Locale.US : locale;
		this.awkSink = Objects.requireNonNull(awkSink, "awkSink");
		this.error = error == null ? System.err : error;
		this.nr = 0L;
		this.fnr = 0L;
		this.rstart = 0;
		this.rlength = 0;
		this.filename = "";
		this.fs = " ";
		this.rs = "\n";
		this.ofs = " ";
		this.ors = "\n";
		this.convfmt = "%.6g";
		this.ofmt = "%.6g";
		this.subsep = String.valueOf((char) 28);
	}

	/**
	 * Sets the sink used by default {@code print} and {@code printf}
	 * operations.
	 *
	 * @param sink output sink to use
	 */
	public void setAwkSink(AwkSink sink) {
		awkSink = Objects.requireNonNull(sink, "awkSink");
	}

	/**
	 * Sets the stream used for the stderr output of spawned processes
	 * (e.g.&nbsp;{@code system("...")}).
	 *
	 * @param errorStream stream to receive process stderr
	 */
	public void setErrorStream(PrintStream errorStream) {
		this.error = Objects.requireNonNull(errorStream, "errorStream");
	}

	/**
	 * Returns the default output sink used by {@code print} and {@code printf}.
	 *
	 * @return the current AWK sink
	 */
	public AwkSink getAwkSink() {
		return awkSink;
	}

	/**
	 * Returns the locale used for number formatting in this runtime.
	 *
	 * @return the runtime locale
	 */
	public Locale getLocale() {
		return locale;
	}

	private IoState getIoState() {
		if (ioState == null) {
			ioState = new IoState();
		}
		return ioState;
	}

	/**
	 * Returns whether the supplied variable name is managed directly by JRT
	 * rather than through the AVM runtime stack.
	 *
	 * @param name variable name to inspect
	 * @return {@code true} when the variable is a JRT-managed special variable
	 */
	public static boolean isJrtManagedSpecialVariable(String name) {
		return "FS".equals(name)
				|| "RS".equals(name)
				|| "OFS".equals(name)
				|| "ORS".equals(name)
				|| "CONVFMT".equals(name)
				|| "OFMT".equals(name)
				|| "SUBSEP".equals(name)
				|| "FILENAME".equals(name)
				|| "NF".equals(name)
				|| "NR".equals(name)
				|| "FNR".equals(name)
				|| "ARGC".equals(name);
	}

	/**
	 * Copies only the JRT-managed special variables from the supplied map.
	 *
	 * @param variableMap source variable map
	 * @return a new map containing only JRT-managed special variables
	 */
	public static Map<String, Object> copySpecialVariables(Map<String, Object> variableMap) {
		Map<String, Object> specialVariables = new HashMap<String, Object>();
		if (variableMap == null || variableMap.isEmpty()) {
			return specialVariables;
		}
		for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
			if (isJrtManagedSpecialVariable(entry.getKey())) {
				specialVariables.put(entry.getKey(), entry.getValue());
			}
		}
		return specialVariables;
	}

	/**
	 * Clears execution-specific state so the same JRT instance can be reused for
	 * another evaluation or interpretation run.
	 */
	public void resetRuntimeState() {
		jrtCloseAll();
		clearExecutionState();
	}

	/**
	 * Clears per-execution JRT state and re-applies the default runtime special
	 * variables for a new script or expression execution.
	 *
	 * @param initialFsValue initial field separator, or {@code null} to use the
	 *        AWK default
	 * @param defaultRs default record separator
	 * @param defaultOrs default output record separator
	 */
	public void prepareForExecution(String initialFsValue, String defaultRs, String defaultOrs) {
		clearExecutionState();
		applyDefaultRuntimeVariables(initialFsValue, defaultRs, defaultOrs);
	}

	/**
	 * Initializes JRT-managed special variables to their per-execution defaults
	 * after first closing all JRT-managed resources from the previous run.
	 *
	 * @param initialFsValue initial field separator, or {@code null} to use the
	 *        AWK default
	 * @param defaultRs default record separator
	 * @param defaultOrs default output record separator
	 */
	public void initializeRuntimeState(String initialFsValue, String defaultRs, String defaultOrs) {
		resetRuntimeState();
		applyDefaultRuntimeVariables(initialFsValue, defaultRs, defaultOrs);
	}

	/**
	 * Initializes JRT-managed special variables for a brand-new runtime instance.
	 * <p>
	 * Unlike {@link #initializeRuntimeState(String, String, String)}, this method
	 * assumes no prior execution state is present and therefore skips the full
	 * reset/close cycle.
	 * </p>
	 *
	 * @param initialFsValue initial field separator, or {@code null} to use the
	 *        AWK default
	 * @param defaultRs default record separator
	 * @param defaultOrs default output record separator
	 */
	public void initializeFreshRuntimeState(String initialFsValue, String defaultRs, String defaultOrs) {
		prepareForExecution(initialFsValue, defaultRs, defaultOrs);
	}

	/**
	 * Clears all JRT state that is specific to a single execution.
	 * <p>
	 * Resource closing is intentionally handled by the caller before this method
	 * is used, so the method only drops references and resets counters.
	 * </p>
	 */
	private void clearExecutionState() {
		ioState = null;
		inputLine = null;
		recordState = null;
		activeSource = null;
		jrtInputString = null;
		nr = 0L;
		fnr = 0L;
		rstart = 0;
		rlength = 0;
		filename = "";
	}

	/**
	 * Restores the runtime-managed special variables to their execution defaults.
	 *
	 * @param initialFsValue initial field separator, or {@code null} to use the
	 *        AWK default
	 * @param defaultRs default record separator
	 * @param defaultOrs default output record separator
	 */
	private void applyDefaultRuntimeVariables(String initialFsValue, String defaultRs, String defaultOrs) {
		setFS(initialFsValue == null ? " " : initialFsValue);
		setRS(defaultRs);
		setOFS(" ");
		setORS(defaultOrs);
		setCONVFMT("%.6g");
		setOFMT("%.6g");
		setSUBSEP(String.valueOf((char) 28));
		setFILENAMEViaJrt("");
		setNR(0);
		setFNR(0);
		setRSTART(0);
		setRLENGTH(0);
	}

	/**
	 * Assign all -v variables.
	 *
	 * @param initialVarMap A map containing all initial variable
	 *        names and their values.
	 */
	public final void assignInitialVariables(Map<String, Object> initialVarMap) {
		for (Map.Entry<String, Object> var : initialVarMap.entrySet()) {
			String name = var.getKey();
			Object value = var.getValue();
			if ("FS".equals(name)) {
				setFS(value);
				continue;
			}
			if ("RS".equals(name)) {
				setRS(value);
				continue;
			}
			if ("OFS".equals(name)) {
				setOFS(value);
				continue;
			}
			if ("ORS".equals(name)) {
				setORS(value);
				continue;
			}
			if ("CONVFMT".equals(name)) {
				setCONVFMT(value);
				continue;
			}
			if ("OFMT".equals(name)) {
				setOFMT(value);
				continue;
			}
			if ("SUBSEP".equals(name)) {
				setSUBSEP(value);
				continue;
			}
			if ("FILENAME".equals(name)) {
				setFILENAMEViaJrt(value == null ? "" : value.toString());
				continue;
			}
			if ("NF".equals(name)) {
				setNF(value);
				continue;
			}
			if ("NR".equals(name)) {
				setNR(value);
				continue;
			}
			if ("FNR".equals(name)) {
				setFNR(value);
				continue;
			}
			if ("ARGC".equals(name)) {
				setARGC(value);
				continue;
			}
			vm.assignVariable(name, value);
		}
	}

	/**
	 * Applies only the JRT-managed special variable assignments from the
	 * supplied map (FS, RS, OFS, ORS, CONVFMT, OFMT, SUBSEP, FILENAME, NF,
	 * NR, FNR, ARGC). Non-special variables are silently skipped because
	 * they require the runtime stack to be fully initialized (which happens
	 * during tuple execution).
	 *
	 * @param variableMap a map of variable names to values
	 */
	public final void applySpecialVariables(Map<String, Object> variableMap) {
		if (variableMap == null || variableMap.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> var : variableMap.entrySet()) {
			String name = var.getKey();
			Object value = var.getValue();
			if ("FS".equals(name)) {
				setFS(value);
			} else if ("RS".equals(name)) {
				setRS(value);
			} else if ("OFS".equals(name)) {
				setOFS(value);
			} else if ("ORS".equals(name)) {
				setORS(value);
			} else if ("CONVFMT".equals(name)) {
				setCONVFMT(value);
			} else if ("OFMT".equals(name)) {
				setOFMT(value);
			} else if ("SUBSEP".equals(name)) {
				setSUBSEP(value);
			} else if ("FILENAME".equals(name)) {
				setFILENAMEViaJrt(value == null ? "" : value.toString());
			} else if ("NF".equals(name)) {
				setNF(value);
			} else if ("NR".equals(name)) {
				setNR(value);
			} else if ("FNR".equals(name)) {
				setFNR(value);
			} else if ("ARGC".equals(name)) {
				setARGC(value);
			}
			// Non-special variables are skipped; they are assigned later
			// via the tuple instruction stream
		}
	}

	/**
	 * Called by AVM/compiled modules to assign local
	 * environment variables to an associative array
	 * (in this case, to ENVIRON).
	 *
	 * @param aa The associative array to populate with
	 *        environment variables. The module asserts that
	 *        the associative array is empty prior to population.
	 */
	public static void assignEnvironmentVariables(AssocArray aa) {
		Map<String, String> env = System.getenv();
		for (Map.Entry<String, String> var : env.entrySet()) {
			aa.put(var.getKey(), var.getValue());
		}
	}

	/**
	 * Creates an AWK-managed associative array and exposes it as a plain
	 * {@link Map} for callers that do not need the concrete runtime type.
	 *
	 * @param sortedArrayKeys {@code true} to keep keys sorted
	 * @return a new AWK associative array
	 */
	public static Map<Object, Object> createAwkMap(boolean sortedArrayKeys) {
		return AssocArray.create(sortedArrayKeys);
	}

	/**
	 * Checks key existence using AWK semantics when the supplied map is backed by
	 * an {@link AssocArray}, otherwise falling back to regular {@link Map}
	 * semantics.
	 *
	 * @param map map to inspect
	 * @param key key to look up
	 * @return {@code true} when the key exists
	 */
	public static boolean containsAwkKey(Map<Object, Object> map, Object key) {
		if (map instanceof AssocArray) {
			return ((AssocArray) map).isIn(key);
		}
		return map.containsKey(key);
	}

	/**
	 * Reads a map element using AWK semantics when the supplied map is backed by
	 * an {@link AssocArray}. For plain {@link Map} instances, missing or
	 * {@code null}-valued entries are exposed as the AWK blank value so later
	 * expression evaluation never receives a raw {@code null}.
	 *
	 * @param map map to inspect
	 * @param key key to look up
	 * @return the stored value, or the AWK blank value when no concrete value is
	 *         present
	 */
	public static Object getAwkValue(Map<Object, Object> map, Object key) {
		if (map instanceof AssocArray) {
			return map.get(key);
		}
		Object value = map.get(key);
		return value != null ? value : BLANK;
	}

	/**
	 * Convert Strings, Integers, and Doubles to Strings
	 * based on the CONVFMT variable contents and the stored Locale.
	 *
	 * @param o Object to convert.
	 * @return A String representation of o.
	 */
	public String toAwkString(Object o) {
		return AwkSink.formatOutputValue(o, this.convfmt, this.locale);
	}

	/**
	 * Convert a String, Integer, or Double to Double.
	 *
	 * @param o Object to convert.
	 * @return the "double" value of o, or 0 if invalid
	 */
	public static double toDouble(final Object o) {
		if (o == null) {
			return 0;
		}

		if (o instanceof Number) {
			return ((Number) o).doubleValue();
		}

		if (o instanceof Character) {
			return (double) ((Character) o).charValue();
		}

		// Try to convert the string to a number.
		String s = o.toString();
		int length = s.length();

		// Optimization: We don't need to handle strings that are longer than 26 chars
		// because a Double cannot be longer than 26 chars when converted to String.
		if (length > 26) {
			length = 26;
		}

		// Loop:
		// If convervsion fails, try with one character less.
		// 25fix will convert to 25 (any numeric prefix will work)
		while (length > 0) {
			try {
				return Double.parseDouble(s.substring(0, length));
			} catch (NumberFormatException nfe) {
				length--;
			}
		}

		// Failed (not even with one char)
		return 0;
	}

	/**
	 * Determines whether a double value actually represents a long integer
	 * within the limits of floating point precision.
	 *
	 * @param d the double value to examine
	 * @return {@code true} if {@code d} is effectively an integer
	 */
	public static boolean isActuallyLong(double d) {
		double r = Math.rint(d);
		return Math.abs(d - r) < Math.ulp(d);
	}

	/**
	 * Convert a String, Long, or Double to Long.
	 *
	 * @param o Object to convert.
	 * @return the "long" value of o, or 0 if invalid
	 */
	public static long toLong(final Object o) {
		if (o == null) {
			return 0;
		}

		if (o instanceof Number) {
			return ((Number) o).longValue();
		}

		if (o instanceof Character) {
			return (long) ((Character) o).charValue();
		}

		// Try to convert the string to a number.
		String s = o.toString();
		int length = s.length();

		// Optimization: We don't need to handle strings that are longer than 20 chars
		// because a Long cannot be longer than 20 chars when converted to String.
		if (length > 20) {
			length = 20;
		}

		// Loop:
		// If convervsion fails, try with one character less.
		// 25fix will convert to 25 (any numeric prefix will work)
		while (length > 0) {
			try {
				return Long.parseLong(s.substring(0, length));
			} catch (NumberFormatException nfe) {
				length--;
			}
		}
		// Failed (not even with one char)
		return 0;
	}

	/**
	 * Convert a field designator to a non-negative long, raising an AWK runtime
	 * exception when the value is invalid.
	 *
	 * @param obj the object identifying the field (for example, the result of a
	 *        numeric expression)
	 * @return the parsed field number as a long
	 */
	public static long parseFieldNumber(Object obj) {
		long num = toLong(obj);
		if (num < 0) {
			throw new AwkRuntimeException(
					"Field $(" + obj.toString()
							+ ") is incorrect.");
		}
		return num;
	}

	/**
	 * Compares two objects. Whether to employ less-than, equals, or
	 * greater-than checks depends on the mode chosen by the callee.
	 * It handles Awk variable rules and type conversion semantics.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode
	 *        <ul>
	 *        <li>&lt; 0 - Return true if o1 &lt; o2.
	 *        <li>0 - Return true if o1 == o2.
	 *        <li>&gt; 0 - Return true if o1 &gt; o2.
	 *        </ul>
	 * @return a boolean
	 */
	public static boolean compare2(Object o1, Object o2, int mode) {
		// Pre-compute String representations of o1 and o2
		String o1String = o1.toString();
		String o2String = o2.toString();

		// Special case of Uninitialized objects
		if (o1 instanceof UninitializedObject) {
			if (o2 instanceof UninitializedObject || "".equals(o2String) || "0".equals(o2String)) {
				return mode == 0;
			} else {
				return mode < 0;
			}
		}
		if (o2 instanceof UninitializedObject) {
			if ("".equals(o1String) || "0".equals(o1String)) {
				return mode == 0;
			} else {
				return mode > 0;
			}
		}

		if (!(o1 instanceof Number)) {
			try {
				o1 = new BigDecimal(o1String).doubleValue();
			} catch (NumberFormatException nfe) { // NOPMD - ignore invalid number
				// ignore invalid number, handled by subsequent logic
			}
		}
		if (!(o2 instanceof Number)) {
			try {
				o2 = new BigDecimal(o2String).doubleValue();
			} catch (NumberFormatException nfe) { // NOPMD - ignore invalid number
				// ignore invalid number, handled by subsequent logic
			}
		}

		if ((o1 instanceof Number) && (o2 instanceof Number)) {
			if (mode < 0) {
				return ((Number) o1).doubleValue() < ((Number) o2).doubleValue();
			} else if (mode == 0) {
				return ((Number) o1).doubleValue() == ((Number) o2).doubleValue();
			} else {
				return ((Number) o1).doubleValue() > ((Number) o2).doubleValue();
			}
		} else {
			// string equality usually occurs more often than natural ordering comparison
			if (mode == 0) {
				return o1String.equals(o2String);
			} else if (mode < 0) {
				return o1String.compareTo(o2String) < 0;
			} else {
				return o1String.compareTo(o2String) > 0;
			}
		}
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one plus a given object. For Integers and Doubles,
	 * this is similar to o+1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not represent a valid Double, 1 is returned.
	 *
	 * @param o The object to increase.
	 * @return o+1 if o is an Integer or Double object, or
	 *         if o is a String object and represents a double.
	 *         Otherwise, 1 is returned. If the return value
	 *         is an integer, an Integer object is returned.
	 *         Otherwise, a Double object is returned.
	 */
	public static Object inc(Object o) {
		double ans;
		if (o instanceof Number) {
			ans = ((Number) o).doubleValue() + 1;
		} else {
			try {
				ans = Double.parseDouble(o.toString()) + 1;
			} catch (NumberFormatException nfe) {
				ans = 1;
			}
		}
		if (isActuallyLong(ans)) {
			return (long) Math.rint(ans);
		} else {
			return ans;
		}
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one minus a given object. For Integers and Doubles,
	 * this is similar to o-1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not represent a valid Double, -1 is returned.
	 *
	 * @param o The object to increase.
	 * @return o-1 if o is an Integer or Double object, or
	 *         if o is a String object and represents a double.
	 *         Otherwise, -1 is returned. If the return value
	 *         is an integer, an Integer object is returned.
	 *         Otherwise, a Double object is returned.
	 */
	public static Object dec(Object o) {
		double ans;
		if (o instanceof Number) {
			ans = ((Number) o).doubleValue() - 1;
		} else {
			try {
				ans = Double.parseDouble(o.toString()) - 1;
			} catch (NumberFormatException nfe) {
				ans = 1;
			}
		}
		if (isActuallyLong(ans)) {
			return (long) Math.rint(ans);
		} else {
			return ans;
		}
	}

	// non-static to reference "inputLine"
	/**
	 * Converts an Integer, Double, String, Pattern,
	 * or ConditionPair to a boolean.
	 *
	 * @param o The object to convert to a boolean.
	 * @return For the following class types for o:
	 *         <ul>
	 *         <li><strong>Integer</strong> - o.intValue() != 0
	 *         <li><strong>Long</strong> - o.longValue() != 0
	 *         <li><strong>Double</strong> - o.doubleValue() != 0
	 *         <li><strong>String</strong> - o.length() &gt; 0
	 *         <li><strong>UninitializedObject</strong> - false
	 *         <li><strong>Pattern</strong> - $0 ~ o
	 *         </ul>
	 *         If o is none of these types, an error is thrown.
	 */
	public final boolean toBoolean(Object o) {
		boolean val;
		if (o instanceof Integer) {
			val = ((Integer) o).intValue() != 0;
		} else if (o instanceof Long) {
			val = ((Long) o).longValue() != 0;
		} else if (o instanceof Double) {
			val = ((Double) o).doubleValue() != 0;
		} else if (o instanceof String) {
			val = (o.toString().length() > 0);
		} else if (o instanceof UninitializedObject) {
			val = false;
		} else if (o instanceof Pattern) {
			// match against $0
			// ...
			Pattern pattern = (Pattern) o;
			Object inputField = jrtGetInputField(0);
			String s = inputField instanceof UninitializedObject ? "" : inputField.toString();
			Matcher matcher = pattern.matcher(s);
			val = matcher.find();
		} else {
			throw new Error("Unknown operand_stack type: " + o.getClass() + " for value " + o);
		}
		return val;
	}

	/**
	 * Splits the string into parts separated by one or more spaces;
	 * blank first and last fields are eliminated.
	 * This conforms to the 2-argument version of AWK's split function.
	 *
	 * @param array The array to populate.
	 * @param string The string to split.
	 * @return The number of parts resulting from this split operation.
	 */
	public int split(Object array, Object string) {
		return splitWorker(new StringTokenizer(toAwkString(string)), toArrayMap(array));
	}

	/**
	 * Splits the string into parts separated the regular expression fs.
	 * This conforms to the 3-argument version of AWK's split function.
	 * <p>
	 * If fs is blank, it behaves similar to the 2-arg version of
	 * AWK's split function.
	 *
	 * @param fieldSeparator Field separator regular expression.
	 * @param array The array to populate.
	 * @param string The string to split.
	 * @return The number of parts resulting from this split operation.
	 */
	public int split(Object fieldSeparator, Object array, Object string) {
		String fsString = toAwkString(fieldSeparator);
		if (fsString.equals(" ")) {
			return splitWorker(new StringTokenizer(toAwkString(string)), toArrayMap(array));
		} else if (fsString.equals("")) {
			return splitWorker(new CharacterTokenizer(toAwkString(string)), toArrayMap(array));
		} else if (fsString.length() == 1) {
			return splitWorker(
					new SingleCharacterTokenizer(toAwkString(string), fsString.charAt(0)),
					toArrayMap(array));
		} else {
			return splitWorker(new RegexTokenizer(toAwkString(string), fsString), toArrayMap(array));
		}
	}

	private static Map<Object, Object> toArrayMap(Object array) {
		if (!(array instanceof Map)) {
			throw new IllegalArgumentException("split target must be a Map.");
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> arrayMap = (Map<Object, Object>) array;
		return arrayMap;
	}

	private static int splitWorker(Enumeration<Object> e, Map<Object, Object> array) {
		int cnt = 0;
		array.clear();
		while (e.hasMoreElements()) {
			array.put(Long.valueOf(++cnt), e.nextElement());
		}
		array.put(0L, Long.valueOf(cnt));
		return cnt;
	}

	/**
	 * Returns the underlying {@link PartitioningReader} currently in use by
	 * the active {@link InputSource}, or {@code null} if the source is not
	 * stream-based.
	 *
	 * @return the active reader, or {@code null}
	 */
	public PartitioningReader getPartitioningReader() {
		if (activeSource instanceof StreamInputSource) {
			return ((StreamInputSource) activeSource).getPartitioningReader();
		}
		return null;
	}

	/**
	 * <p>
	 * Getter for the field <code>inputLine</code>.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getInputLine() {
		if (inputLine != null) {
			return inputLine;
		}
		if (recordState == null) {
			return null;
		}
		return recordState.getRecordText();
	}

	/**
	 * Retrieve the current value of NF. When fields are initialized this returns
	 * the number of fields in $0; otherwise 0.
	 *
	 * @return current NF value
	 */
	public Integer getNF() {
		if (recordState == null) {
			return Integer.valueOf(0);
		}
		return Integer.valueOf(recordState.getNF());
	}

	/**
	 * Set NF to the specified value and update $0 and fields accordingly.
	 *
	 * @param nfObject value to assign to NF
	 */
	public void setNF(Object nfObject) {
		jrtSetNF(nfObject);
	}

	/**
	 * Get the current NR value as tracked by JRT.
	 *
	 * @return current NR
	 */
	public Long getNR() {
		return Long.valueOf(nr);
	}

	/**
	 * Assign NR to a specific value; also updates the VariableManager copy.
	 *
	 * @param value value to assign
	 */
	public void setNR(Object value) {
		this.nr = toLong(value);
	}

	/**
	 * Get the current FNR value as tracked by JRT.
	 *
	 * @return current FNR
	 */
	public Long getFNR() {
		return Long.valueOf(fnr);
	}

	/**
	 * Assign FNR to a specific value; also updates the VariableManager copy.
	 *
	 * @param value value to assign
	 */
	public void setFNR(Object value) {
		this.fnr = toLong(value);
	}

	/**
	 * Get FS from the VariableManager.
	 *
	 * @return FS value
	 */
	public Object getFSVar() {
		return fs;
	}

	/**
	 * Returns the current FS value as a string.
	 *
	 * @return current field separator
	 */
	public String getFSString() {
		return fs;
	}

	/**
	 * Set FS via the VariableManager.
	 *
	 * @param value new FS value
	 */
	public void setFS(Object value) {
		this.fs = value == null ? "" : value.toString();
	}

	/**
	 * Get RS from the VariableManager.
	 *
	 * @return RS value
	 */
	public Object getRSVar() {
		return rs;
	}

	/**
	 * Returns the current RS value as a string.
	 *
	 * @return current record separator
	 */
	public String getRSString() {
		return rs;
	}

	/**
	 * Set RS via the VariableManager and apply it to the current reader if any.
	 *
	 * @param value new RS value
	 */
	public void setRS(Object value) {
		this.rs = value == null ? "" : value.toString();
		applyRS(this.rs);
	}

	/**
	 * Get OFS from the VariableManager.
	 *
	 * @return OFS value
	 */
	public Object getOFSVar() {
		return ofs;
	}

	/**
	 * Returns the current OFS value as a string.
	 *
	 * @return current output field separator
	 */
	public String getOFSString() {
		return ofs;
	}

	/**
	 * Set OFS via the VariableManager.
	 *
	 * @param value new OFS value
	 */
	public void setOFS(Object value) {
		this.ofs = value == null ? "" : value.toString();
	}

	/**
	 * Get ORS from the VariableManager.
	 *
	 * @return ORS value
	 */
	public Object getORSVar() {
		return ors;
	}

	/**
	 * Returns the current ORS value as a string.
	 *
	 * @return current output record separator
	 */
	public String getORSString() {
		return ors;
	}

	/**
	 * Set ORS via the VariableManager.
	 *
	 * @param value new ORS value
	 */
	public void setORS(Object value) {
		this.ors = value == null ? "" : value.toString();
	}

	/**
	 * Get RSTART tracked by JRT (1-based).
	 *
	 * @return current RSTART
	 */
	public Integer getRSTART() {
		return Integer.valueOf(rstart);
	}

	/**
	 * Set RSTART tracked by JRT (1-based) and mirror to VariableManager.
	 *
	 * @param value new RSTART
	 */
	public void setRSTART(Object value) {
		this.rstart = (int) toLong(value);
	}

	/**
	 * Get RLENGTH tracked by JRT.
	 *
	 * @return current RLENGTH
	 */
	public Integer getRLENGTH() {
		return Integer.valueOf(rlength);
	}

	/**
	 * Set RLENGTH tracked by JRT and mirror to VariableManager.
	 *
	 * @param value new RLENGTH
	 */
	public void setRLENGTH(Object value) {
		this.rlength = (int) toLong(value);
	}

	/**
	 * Get FILENAME as tracked by JRT.
	 *
	 * @return current FILENAME (empty string for stdin/pipe)
	 */
	public String getFILENAME() {
		return filename == null ? "" : filename;
	}

	/**
	 * Set FILENAME through VariableManager and update JRT mirror.
	 *
	 * @param name file name to set
	 */
	public void setFILENAMEViaJrt(String name) {
		this.filename = name == null ? "" : name;
	}

	/**
	 * Get SUBSEP from the VariableManager.
	 *
	 * @return SUBSEP value
	 */
	public Object getSUBSEPVar() {
		return subsep;
	}

	/**
	 * Returns the current SUBSEP value as a string.
	 *
	 * @return current multidimensional-array subscript separator
	 */
	public String getSUBSEPString() {
		return subsep;
	}

	/**
	 * Set SUBSEP via the VariableManager.
	 *
	 * @param value new SUBSEP value
	 */
	public void setSUBSEP(Object value) {
		this.subsep = value == null ? "" : value.toString();
	}

	/**
	 * Get CONVFMT from the VariableManager.
	 *
	 * @return CONVFMT value
	 */
	public Object getCONVFMTVar() {
		return convfmt;
	}

	/**
	 * Returns the current CONVFMT value as a string.
	 *
	 * @return current numeric conversion format
	 */
	public String getCONVFMTString() {
		return convfmt;
	}

	/**
	 * Set CONVFMT via the VariableManager.
	 *
	 * @param value new CONVFMT value
	 */
	public void setCONVFMT(Object value) {
		this.convfmt = value == null ? "" : value.toString();
	}

	/**
	 * Get OFMT from the VariableManager.
	 *
	 * @return OFMT value
	 */
	public String getOFMTString() {
		return ofmt;
	}

	/**
	 * Set OFMT via the VariableManager.
	 *
	 * @param value new OFMT value
	 */
	public void setOFMT(Object value) {
		this.ofmt = value == null ? "" : value.toString();
	}

	/**
	 * Get ARGC from the VariableManager.
	 *
	 * @return ARGC value
	 */
	public Object getARGCVar() {
		return vm.getARGC();
	}

	/**
	 * Set ARGC via the VariableManager.
	 *
	 * @param value new ARGC value
	 */
	public void setARGC(Object value) {
		vm.assignVariable("ARGC", value);
	}

	/**
	 * <p>
	 * Setter for the field <code>inputLine</code>.
	 * </p>
	 *
	 * @param inputLine a {@link java.lang.String} object
	 */
	public void setInputLine(String inputLine) {
		this.inputLine = inputLine;
		recordState = newRecordStateFromText(inputLine);
	}

	/**
	 * Assigns {@code $0} from a getline result and initializes {@code $1..$NF}.
	 *
	 * @param value getline result assigned to {@code $0}
	 */
	public void assignInputLineFromGetline(Object value) {
		String inputValue = value == null ? "" : value.toString();
		inputLine = inputValue;
		recordState = newRecordStateFromText(inputValue);
	}

	/**
	 * Attempt to consume one record from a structured input source and expose it
	 * as the current input record.
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return {@code true} if a record was consumed; {@code false} when the
	 *         source is exhausted
	 * @throws IOException if the source raises an I/O error
	 */
	public boolean consumeInput(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		activeSource = source;
		if (!source.nextRecord()) {
			return false;
		}

		inputLine = null;
		recordState = newRecordStateFromSource(source);

		this.nr++;
		if (source.isFromFilenameList()) {
			this.fnr++;
		}
		return true;
	}

	/**
	 * Attempt to consume one record from a structured input source for
	 * {@code getline target}, returning only the input text and leaving the
	 * current input record state untouched.
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return the consumed input text, or {@code null} when the source is
	 *         exhausted
	 * @throws IOException if the source raises an I/O error
	 */
	public String consumeInputToTarget(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		activeSource = source;
		materializeCurrentRecord();
		if (!source.nextRecord()) {
			return null;
		}

		String input = newRecordStateFromSource(source).getRecordText();
		this.nr++;
		if (source.isFromFilenameList()) {
			this.fnr++;
		}
		return input;
	}

	/**
	 * Consume at most one record from a structured source for expression
	 * evaluation.
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return {@code true} if a record was consumed, {@code false} otherwise
	 * @throws IOException if the source raises an I/O error
	 */
	public boolean consumeInputForEval(InputSource source) throws IOException {
		return consumeInput(source);
	}

	/**
	 * Initialize {@code $0..$NF} from a pre-split field list.
	 *
	 * @param record current {@code $0} text
	 * @param preFields current fields where index {@code 0} is {@code $1}
	 */
	protected void initializeInputFields(String record, List<String> preFields) {
		recordState = newRecordStateFromSource(record, preFields);
	}

	/**
	 * Splits $0 into $1, $2, etc.
	 * Called when an update to $0 has occurred.
	 */
	public void jrtParseFields() {
		RecordState state = ensureRecordStateForTextMutation();
		state.ensureFieldsMaterialized();
	}

	/**
	 * @return true if at least one input field has been initialized.
	 */
	public boolean hasInputFields() {
		return recordState != null;
	}

	/**
	 * Adjust the current input field list and $0 when NF is updated by the
	 * AWK script. Fields are either truncated or extended with empty values
	 * so that {@code NF} truly reflects the number of fields.
	 *
	 * @param nfObj New value for NF
	 */
	public void jrtSetNF(Object nfObj) {
		int nf = (int) toDouble(nfObj);
		if (nf < 0) {
			nf = 0;
		}

		RecordState state = ensureRecordStateForFieldMutation();
		int currentNF = state.getNF();

		if (nf < currentNF) {
			for (int i = currentNF; i > nf; i--) {
				state.removeField(i - 1);
			}
		} else if (nf > currentNF) {
			for (int i = currentNF + 1; i <= nf; i++) {
				state.addField("");
			}
		}

		state.markRecordTextDirty();
	}

	/**
	 * Retrieve the contents of a particular input field.
	 *
	 * @param fieldnumObj Object referring to the field number.
	 * @return Contents of the field.
	 */
	public Object jrtGetInputField(Object fieldnumObj) {
		return jrtGetInputField(parseFieldNumber(fieldnumObj));
	}

	/**
	 * <p>
	 * jrtGetInputField.
	 * </p>
	 *
	 * @param fieldnum a long
	 * @return a {@link java.lang.Object} object
	 */
	public Object jrtGetInputField(long fieldnum) {
		if (fieldnum < 0 || fieldnum > Integer.MAX_VALUE) {
			throw new AwkRuntimeException("Field $(" + Long.valueOf(fieldnum) + ") is incorrect.");
		}
		if (recordState == null) {
			return BLANK;
		}
		return recordState.getField((int) fieldnum);
	}

	/**
	 * Stores value_obj into an input field.
	 *
	 * @param valueObj The RHS of the assignment.
	 * @param fieldNum field number to update.
	 * @return A string representation of valueObj.
	 */
	public String jrtSetInputField(Object valueObj, long fieldNum) {
		if (fieldNum > Integer.MAX_VALUE) {
			throw new AwkRuntimeException("Field $(" + Long.valueOf(fieldNum) + ") is incorrect.");
		}
		String value = valueObj.toString();
		int fieldIndex = (int) fieldNum;
		RecordState state = ensureRecordStateForFieldMutation();
		if (valueObj instanceof UninitializedObject) {
			if (fieldIndex <= state.getNF()) {
				state.setField(fieldIndex - 1, "");
			}
		} else {
			while (state.getNF() < fieldIndex) {
				state.addField("");
			}
			state.setField(fieldIndex - 1, value);
		}
		state.markRecordTextDirty();
		return value;
	}

	protected void rebuildDollarZeroFromFields() {
		if (recordState != null) {
			recordState.markRecordTextDirty();
			inputLine = recordState.getRecordText();
		}
	}

	private void materializeCurrentRecord() {
		if (recordState != null) {
			recordState.materialize();
		}
	}

	private RecordState ensureRecordStateForTextMutation() {
		if (recordState == null) {
			recordState = newRecordStateFromText(inputLine == null ? "" : inputLine);
		}
		return recordState;
	}

	private RecordState ensureRecordStateForFieldMutation() {
		RecordState state = ensureRecordStateForTextMutation();
		state.ensureFieldsMaterialized();
		return state;
	}

	private static List<String> sanitizeFields(List<String> rawFields) {
		List<String> copy = new ArrayList<String>(rawFields.size());
		for (String field : rawFields) {
			copy.add(field == null ? "" : field);
		}
		return copy;
	}

	private List<String> splitRecordText(String recordText, String fieldSeparator) {
		List<String> fields = new ArrayList<String>();
		if (recordText == null || recordText.isEmpty()) {
			return fields;
		}

		Enumeration<Object> tokenizer;
		if (fieldSeparator.equals(" ")) {
			tokenizer = new StringTokenizer(recordText);
		} else if (fieldSeparator.length() == 1) {
			tokenizer = new SingleCharacterTokenizer(recordText, fieldSeparator.charAt(0));
		} else if (fieldSeparator.equals("")) {
			tokenizer = new CharacterTokenizer(recordText);
		} else {
			tokenizer = new RegexTokenizer(recordText, fieldSeparator);
		}

		while (tokenizer.hasMoreElements()) {
			fields.add((String) tokenizer.nextElement());
		}
		return fields;
	}

	private static String joinFieldsWithLiteralSeparator(List<String> fields, String separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				sb.append(separator);
			}
			sb.append(fields.get(i));
		}
		return sb.toString();
	}

	private String rebuildRecordTextFromFields(List<String> fields) {
		return joinFieldsWithLiteralSeparator(fields, ofs);
	}

	private RecordState newRecordStateFromText(String recordText) {
		return new RecordState(recordText, null);
	}

	private RecordState newRecordStateFromSource(InputSource source) {
		return new RecordState(source);
	}

	private RecordState newRecordStateFromSource(String recordText, List<String> rawFields) {
		return new RecordState(recordText, rawFields);
	}

	private final class RecordState {

		private final String fieldSeparatorAtRead;
		private final InputSource source;
		private String recordText;
		private List<String> fields;
		private boolean recordTextAvailable;
		private boolean fieldsAvailable;
		private boolean recordTextDirty;
		private boolean fieldsDirty;
		private boolean recordTextLoadedFromSource;
		private boolean fieldsLoadedFromSource;

		private RecordState(InputSource source) {
			this(null, null, source);
		}

		private RecordState(String recordText, List<String> rawFields) {
			this(recordText, rawFields, null);
		}

		private RecordState(String recordText, List<String> rawFields, InputSource source) {
			this.fieldSeparatorAtRead = fs;
			this.source = source;
			if (recordText != null) {
				this.recordText = recordText;
				this.recordTextAvailable = true;
			} else if (rawFields == null && source == null) {
				this.recordText = "";
				this.recordTextAvailable = true;
			}
			if (rawFields != null) {
				this.fields = sanitizeFields(rawFields);
				this.fieldsAvailable = true;
				this.fieldsDirty = false;
			} else {
				this.fieldsAvailable = false;
				this.fieldsDirty = true;
			}
			this.recordTextDirty = false;
		}

		private void ensureFieldsMaterialized() {
			if (fieldsAvailable && !fieldsDirty) {
				return;
			}
			if (!recordTextDirty) {
				loadFieldsFromSource();
				if (fieldsAvailable && !fieldsDirty) {
					return;
				}
			}
			fields = splitRecordText(getRecordText(), fieldSeparatorAtRead);
			fieldsAvailable = true;
			fieldsDirty = false;
		}

		private String getRecordText() {
			if (!recordTextAvailable || recordTextDirty) {
				if (recordTextDirty) {
					recordText = rebuildRecordTextFromFields(fields);
				} else {
					loadRecordTextFromSource();
					if (!recordTextAvailable) {
						loadFieldsFromSource();
						if (!fieldsAvailable) {
							throw new IllegalStateException(
									"InputSource must provide record text, fields, or both after nextRecord()");
						}
						recordText = joinFieldsWithLiteralSeparator(fields, fieldSeparatorAtRead);
					}
				}
				recordTextAvailable = true;
				recordTextDirty = false;
			}
			return recordText;
		}

		private int getNF() {
			ensureFieldsMaterialized();
			return fields.size();
		}

		private Object getField(int fieldIndex) {
			if (fieldIndex == 0) {
				return getRecordText();
			}
			ensureFieldsMaterialized();
			int zeroBasedIndex = fieldIndex - 1;
			if (zeroBasedIndex < 0 || zeroBasedIndex >= fields.size()) {
				return BLANK;
			}
			return fields.get(zeroBasedIndex);
		}

		private void setField(int zeroBasedIndex, String value) {
			ensureFieldsMaterialized();
			fields.set(zeroBasedIndex, value);
			markRecordTextDirty();
		}

		private void addField(String value) {
			ensureFieldsMaterialized();
			fields.add(value);
			markRecordTextDirty();
		}

		private void removeField(int zeroBasedIndex) {
			ensureFieldsMaterialized();
			fields.remove(zeroBasedIndex);
			markRecordTextDirty();
		}

		private void markRecordTextDirty() {
			recordTextDirty = true;
			recordTextAvailable = fieldsAvailable;
		}

		private void materialize() {
			getRecordText();
			ensureFieldsMaterialized();
		}

		private void loadRecordTextFromSource() {
			if (source == null || recordTextLoadedFromSource) {
				return;
			}
			recordText = source.getRecordText();
			recordTextAvailable = recordText != null;
			recordTextLoadedFromSource = true;
		}

		private void loadFieldsFromSource() {
			if (source == null || fieldsLoadedFromSource) {
				return;
			}
			List<String> rawFields = source.getFields();
			fieldsLoadedFromSource = true;
			if (rawFields != null) {
				fields = sanitizeFields(rawFields);
				fieldsAvailable = true;
				fieldsDirty = false;
			}
		}
	}

	/**
	 * <p>
	 * jrtConsumeFileInputForGetline.
	 * </p>
	 *
	 * @param fileNameParam a {@link java.lang.String} object
	 * @return a {@link java.lang.Integer} object
	 */
	public Integer jrtConsumeFileInputForGetline(String fileNameParam) {
		try {
			if (jrtConsumeFileInput(fileNameParam)) {
				return ONE;
			} else {
				jrtInputString = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrtInputString = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve the next line of output from a command, executing
	 * the command if necessary and store it to $0.
	 *
	 * @param cmdString The command to execute.
	 * @return Integer(1) if successful, Integer(0) if no more
	 *         input is available, Integer(-1) upon an IO error.
	 */
	public Integer jrtConsumeCommandInputForGetline(String cmdString) {
		try {
			if (jrtConsumeCommandInput(cmdString)) {
				return ONE;
			} else {
				jrtInputString = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrtInputString = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve $0.
	 *
	 * @return The contents of the $0 input field.
	 */
	public String jrtGetInputString() {
		return jrtInputString;
	}

	/**
	 * <p>
	 * Getter for the field <code>outputFiles</code>.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, PrintStream> getOutputFiles() {
		Map<String, PrintStream> outputFiles = new HashMap<String, PrintStream>();
		for (Map.Entry<String, FileOutputState> entry : getIoState().fileOutputs.entrySet()) {
			outputFiles.put(entry.getKey(), entry.getValue().sink.getPrintStream());
		}
		return outputFiles;
	}

	/**
	 * Resolves the sink used by file redirection.
	 *
	 * @param fileNameParam target file name
	 * @param append whether output should be appended
	 * @return the sink that writes to the requested file
	 */
	protected AwkSink getFileAwkSink(String fileNameParam, boolean append) {
		return getOrCreateFileOutputState(fileNameParam, append).sink;
	}

	/**
	 * Resolves the sink used by pipe redirection.
	 *
	 * @param cmd command to execute
	 * @return the sink connected to the process stdin
	 */
	protected AwkSink getPipeAwkSink(String cmd) {
		return getOrCreateProcessOutputState(cmd).sink;
	}

	/**
	 * Writes a standard AWK {@code print} operation to the default output.
	 *
	 * @param values values to print
	 * @throws IOException if the sink cannot be written to
	 */
	public void printDefault(Object[] values) throws IOException {
		awkSink.print(ofs, ors, ofmt, values);
	}

	/**
	 * Writes a standard AWK {@code print} operation to a redirected file.
	 *
	 * @param fileNameParam target file name
	 * @param append whether output should be appended
	 * @param values values to print; an empty array prints {@code $0}
	 * @throws IOException if the sink cannot be written to
	 */
	public void printToFile(String fileNameParam, boolean append, Object[] values) throws IOException {
		getFileAwkSink(fileNameParam, append).print(ofs, ors, ofmt, values);
	}

	/**
	 * Writes a standard AWK {@code print} operation to a redirected process.
	 *
	 * @param cmd command to execute
	 * @param values values to print; an empty array prints {@code $0}
	 * @throws IOException if the sink cannot be written to
	 */
	public void printToProcess(String cmd, Object[] values) throws IOException {
		getPipeAwkSink(cmd).print(ofs, ors, ofmt, values);
	}

	/**
	 * Writes a formatted AWK output string to the specified sink.
	 *
	 * @param format format string passed to {@code printf}
	 * @param values values supplied after the format string
	 * @throws IOException if the sink cannot be written to
	 */
	public void printfDefault(String format, Object[] values) throws IOException {
		awkSink.printf(ofs, ors, ofmt, format, values);
	}

	/**
	 * Writes formatted AWK output to a redirected file.
	 *
	 * @param fileNameParam target file name
	 * @param append whether output should be appended
	 * @param format format string passed to {@code printf}
	 * @param values values supplied after the format string
	 * @throws IOException if the sink cannot be written to
	 */
	public void printfToFile(String fileNameParam, boolean append, String format, Object[] values)
			throws IOException {
		AwkSink sink = getFileAwkSink(fileNameParam, append);
		sink.printf(ofs, ors, ofmt, format, values);
	}

	/**
	 * Writes formatted AWK output to a redirected process.
	 *
	 * @param cmd command to execute
	 * @param format format string passed to {@code printf}
	 * @param values values supplied after the format string
	 * @throws IOException if the sink cannot be written to
	 */
	public void printfToProcess(String cmd, String format, Object[] values) throws IOException {
		AwkSink sink = getPipeAwkSink(cmd);
		sink.printf(ofs, ors, ofmt, format, values);
	}

	/**
	 * Retrieve the PrintStream which writes to a particular file,
	 * creating the PrintStream if necessary.
	 *
	 * @param fileNameParam The file which to write the contents of the PrintStream.
	 * @param append true to append to the file, false to overwrite the file.
	 * @return a {@link java.io.PrintStream} object
	 */
	public PrintStream jrtGetPrintStream(String fileNameParam, boolean append) {
		return getFileAwkSink(fileNameParam, append).getPrintStream();
	}

	/**
	 * <p>
	 * jrtConsumeFileInput.
	 * </p>
	 *
	 * @param fileNameParam a {@link java.lang.String} object
	 * @return a boolean
	 * @throws java.io.IOException if any.
	 */
	public boolean jrtConsumeFileInput(String fileNameParam) throws IOException {
		Map<String, PartitioningReader> fileReaders = getIoState().fileReaders;
		PartitioningReader pr = fileReaders.get(fileNameParam);
		if (pr == null) {
			try {
				pr = new PartitioningReader(
						new InputStreamReader(new FileInputStream(fileNameParam), StandardCharsets.UTF_8),
						this.rs);
				fileReaders.put(fileNameParam, pr);
				this.filename = fileNameParam;
			} catch (IOException ioe) {
				fileReaders.remove(fileNameParam);
				throw ioe;
			}
		}

		inputLine = pr.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrtInputString = inputLine;
			this.nr++;
			return true;
		}
	}

	private static Process spawnProcess(String cmd) throws IOException {
		Process p;

		if (IS_WINDOWS) {
			// spawn the process using the Windows shell
			ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
			p = pb.start();
		} else {
			// spawn the process using the default POSIX shell
			ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
			p = pb.start();
		}

		return p;
	}

	/**
	 * <p>
	 * jrtConsumeCommandInput.
	 * </p>
	 *
	 * @param cmd a {@link java.lang.String} object
	 * @return a boolean
	 * @throws java.io.IOException if any.
	 */
	public boolean jrtConsumeCommandInput(String cmd) throws IOException {
		CommandInputState commandInput = getOrCreateCommandInputState(cmd);
		inputLine = commandInput.reader.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrtInputString = inputLine;
			this.nr++;
			return true;
		}
	}

	/**
	 * Retrieve the PrintStream which shuttles data to stdin for a process,
	 * executing the process if necessary. Threads are created to shuttle the
	 * data to/from the process.
	 *
	 * @param cmd The command to execute.
	 * @return The PrintStream which to write to provide
	 *         input data to the process.
	 */
	public PrintStream jrtSpawnForOutput(String cmd) {
		return getPipeAwkSink(cmd).getPrintStream();
	}

	private FileOutputState getOrCreateFileOutputState(String fileNameParam, boolean append) {
		IoState state = getIoState();
		FileOutputState outputState = state.fileOutputs.get(fileNameParam);
		if (outputState == null) {
			outputState = createFileOutputState(fileNameParam, append);
			state.fileOutputs.put(fileNameParam, outputState);
		}
		return outputState;
	}

	private FileOutputState createFileOutputState(String fileNameParam, boolean append) {
		try {
			PrintStream printStream = new PrintStream(
					new FileOutputStream(fileNameParam, append),
					true,
					StandardCharsets.UTF_8.name());
			return new FileOutputState(new OutputStreamAwkSink(printStream, locale));
		} catch (IOException ioe) {
			throw new AwkRuntimeException("Cannot open " + fileNameParam + " for writing: " + ioe);
		}
	}

	private CommandInputState getOrCreateCommandInputState(String cmd) throws IOException {
		IoState state = getIoState();
		CommandInputState commandInput = state.commandInputs.get(cmd);
		if (commandInput == null) {
			commandInput = createCommandInputState(cmd);
			state.commandInputs.put(cmd, commandInput);
			this.filename = "";
		}
		return commandInput;
	}

	private CommandInputState createCommandInputState(String cmd) throws IOException {
		Process process = null;
		Thread errorPump = null;
		try {
			process = spawnProcess(cmd);
			process.getOutputStream().close();
			errorPump = DataPump.dumpAndReturnThread(cmd + " stderr", process.getErrorStream(), error);
			PartitioningReader reader = new PartitioningReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8),
					this.rs);
			return new CommandInputState(process, reader, errorPump);
		} catch (IOException ioe) {
			if (process != null) {
				process.destroy();
			}
			joinDataPump(errorPump);
			throw ioe;
		}
	}

	private ProcessOutputState getOrCreateProcessOutputState(String cmd) {
		IoState state = getIoState();
		ProcessOutputState outputState = state.processOutputs.get(cmd);
		if (outputState == null) {
			outputState = createProcessOutputState(cmd);
			state.processOutputs.put(cmd, outputState);
		}
		return outputState;
	}

	private ProcessOutputState createProcessOutputState(String cmd) {
		Process process = null;
		Thread stderrPump = null;
		Thread stdoutPump = null;
		PrintStream processOutput = null;
		try {
			processOutput = awkSink.getPrintStream();
			process = spawnProcess(cmd);
			stderrPump = DataPump.dumpAndReturnThread(cmd + " stderr", process.getErrorStream(), error);
			stdoutPump = DataPump.dumpAndReturnThread(cmd + " stdout", process.getInputStream(), processOutput);
			PrintStream processInput = new PrintStream(process.getOutputStream(), true, StandardCharsets.UTF_8.name());
			return new ProcessOutputState(
					process,
					new OutputStreamAwkSink(processInput, locale),
					processOutput,
					stdoutPump,
					stderrPump);
		} catch (IOException ioe) {
			if (process != null) {
				process.destroy();
			}
			joinDataPump(stdoutPump);
			joinDataPump(stderrPump);
			throw new AwkRuntimeException("Can't spawn " + cmd + ": " + ioe);
		}
	}

	/**
	 * Attempt to close an open stream, whether it is
	 * an input file, output file, input process, or output
	 * process.
	 * <p>
	 * The specification did not describe AWK behavior
	 * when attempting to close streams/processes with
	 * the same file/command name. In this case,
	 * <em>all</em> open streams with this name
	 * are closed.
	 *
	 * @param fileNameParam The filename/command process to close.
	 * @return Integer(0) upon a successful close, Integer(-1)
	 *         otherwise.
	 */
	public Integer jrtClose(String fileNameParam) {
		boolean b1 = jrtCloseFileReader(fileNameParam);
		boolean b2 = jrtCloseCommandReader(fileNameParam);
		boolean b3 = jrtCloseOutputFile(fileNameParam);
		boolean b4 = jrtCloseOutputStream(fileNameParam);
		// either close will do
		return (b1 || b2 || b3 || b4) ? ZERO : MINUS_ONE;
	}

	/**
	 * <p>
	 * jrtCloseAll.
	 * </p>
	 */
	public void jrtCloseAll() {
		IoState state = ioState;
		if (state == null) {
			return;
		}
		Set<String> set = new HashSet<String>();
		for (String s : state.fileReaders.keySet()) {
			set.add(s);
		}
		for (String s : state.commandInputs.keySet()) {
			set.add(s);
		}
		for (String s : state.fileOutputs.keySet()) {
			set.add(s);
		}
		for (String s : state.processOutputs.keySet()) {
			set.add(s);
		}
		for (String s : set) {
			jrtClose(s);
		}
	}

	private boolean jrtCloseOutputFile(String fileNameParam) {
		IoState state = ioState;
		if (state == null) {
			return false;
		}
		FileOutputState outputState = state.fileOutputs.remove(fileNameParam);
		if (outputState != null) {
			outputState.sink.getPrintStream().close();
		}
		return outputState != null;
	}

	private boolean jrtCloseOutputStream(String cmd) {
		IoState state = ioState;
		if (state == null) {
			return false;
		}
		ProcessOutputState outputState = state.processOutputs.remove(cmd);
		if (outputState == null) {
			return false;
		}
		outputState.sink.getPrintStream().close();
		try {
			// wait for the spawned process to finish to make sure
			// all output has been flushed and captured
			outputState.process.waitFor();
			outputState.process.exitValue();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			outputState.process.destroyForcibly();
			throw new AwkRuntimeException(
					"Caught exception while waiting for process exit: " + ie);
		} finally {
			joinDataPump(outputState.stdoutPump);
			joinDataPump(outputState.stderrPump);
			outputState.processOutput.flush();
			error.flush();
		}
		return true;
	}

	private boolean jrtCloseFileReader(String fileNameParam) {
		IoState state = ioState;
		if (state == null) {
			return false;
		}
		PartitioningReader pr = state.fileReaders.get(fileNameParam);
		if (pr == null) {
			return false;
		}
		state.fileReaders.remove(fileNameParam);
		try {
			pr.close();
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	private boolean jrtCloseCommandReader(String cmd) {
		IoState state = ioState;
		if (state == null) {
			return false;
		}
		CommandInputState commandInput = state.commandInputs.remove(cmd);
		if (commandInput == null) {
			return false;
		}
		try {
			commandInput.reader.close();
			try {
				// wait for the process to complete so that all
				// data pumped from the command is captured
				commandInput.process.waitFor();
				commandInput.process.exitValue();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				commandInput.process.destroyForcibly();
				throw new AwkRuntimeException(
						"Caught exception while waiting for process exit: " + ie);
			}
			return true;
		} catch (IOException ioe) {
			return false;
		} finally {
			joinDataPump(commandInput.errorPump);
			error.flush();
		}
	}

	/**
	 * Executes the command specified by cmd and waits
	 * for termination, returning an Integer object
	 * containing the return code.
	 * stdin to this process is closed while
	 * threads are created to shuttle stdout and
	 * stderr of the command to stdout/stderr
	 * of the calling process.
	 *
	 * @param cmd The command to execute.
	 * @return Integer(return_code) of the created
	 *         process. Integer(-1) is returned on an IO error.
	 */
	public Integer jrtSystem(String cmd) {
		try {
			PrintStream processOutput = awkSink.getPrintStream();
			Process p = spawnProcess(cmd);
			// no input to this process!
			p.getOutputStream().close();
			Thread errorPump = DataPump.dumpAndReturnThread(cmd + " stderr", p.getErrorStream(), error);
			Thread outputPump = DataPump.dumpAndReturnThread(cmd + " stdout", p.getInputStream(), processOutput);
			boolean interrupted = false;
			int retcode;
			while (true) {
				try {
					retcode = p.waitFor();
					break;
				} catch (InterruptedException ie) {
					// Preserve interrupt and keep waiting so process pipes can close.
					interrupted = true;
				}
			}
			joinDataPump(outputPump);
			joinDataPump(errorPump);
			processOutput.flush();
			error.flush();
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
			return Integer.valueOf(retcode);
		} catch (IOException ioe) {
			return MINUS_ONE;
		}
	}

	private static void joinDataPump(Thread pump) {
		if (pump == null) {
			return;
		}
		boolean interrupted = false;
		while (true) {
			try {
				pump.join();
				break;
			} catch (InterruptedException ie) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * <p>
	 * sprintfFunctionNoCatch.
	 * </p>
	 *
	 * @param locale a {@link java.util.Locale} object
	 * @param fmtArg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 * @return a {@link java.lang.String} object
	 * @throws java.util.IllegalFormatException if any.
	 */
	public static String sprintfNoCatch(Locale locale, String fmtArg, Object... arr) throws IllegalFormatException {
		return String.format(locale, fmtArg, arr);
	}

	/**
	 * <p>
	 * printfFunctionNoCatch.
	 * </p>
	 *
	 * @param locale a {@link java.util.Locale} object
	 * @param fmtArg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 */
	public static void printfNoCatch(Locale locale, String fmtArg, Object... arr) {
		System.out.print(sprintfNoCatch(locale, fmtArg, arr));
	}

	/**
	 * <p>
	 * printfFunctionNoCatch.
	 * </p>
	 *
	 * @param ps a {@link java.io.PrintStream} object
	 * @param locale a {@link java.util.Locale} object
	 * @param fmtArg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 */
	public static void printfNoCatch(PrintStream ps, Locale locale, String fmtArg, Object... arr) {
		ps.print(sprintfNoCatch(locale, fmtArg, arr));
	}

	/**
	 * <p>
	 * substr.
	 * </p>
	 *
	 * @param startposObj a {@link java.lang.Object} object
	 * @param str a {@link java.lang.String} object
	 * @return a {@link java.lang.String} object
	 */
	public static String substr(Object startposObj, String str) {
		int startpos = (int) toDouble(startposObj);
		if (startpos <= 0) {
			throw new AwkRuntimeException("2nd arg to substr must be a positive integer");
		}
		if (startpos > str.length()) {
			return "";
		} else {
			return str.substring(startpos - 1);
		}
	}

	/**
	 * <p>
	 * substr.
	 * </p>
	 *
	 * @param sizeObj a {@link java.lang.Object} object
	 * @param startposObj a {@link java.lang.Object} object
	 * @param str a {@link java.lang.String} object
	 * @return a {@link java.lang.String} object
	 */
	public static String substr(Object sizeObj, Object startposObj, String str) {
		int startpos = (int) toDouble(startposObj);
		if (startpos <= 0) {
			throw new AwkRuntimeException("2nd arg to substr must be a positive integer");
		}
		if (startpos > str.length()) {
			return "";
		}
		int size = (int) toDouble(sizeObj);
		if (size < 0) {
			throw new AwkRuntimeException("3nd arg to substr must be a non-negative integer");
		}
		if (startpos + size > str.length()) {
			return str.substring(startpos - 1);
		} else {
			return str.substring(startpos - 1, startpos + size - 1);
		}
	}

	/**
	 * <p>
	 * timeSeed.
	 * </p>
	 *
	 * @return a int
	 */
	public static int timeSeed() {
		long l = new Date().getTime();
		long l2 = l % (1000 * 60 * 60 * 24);
		int seed = (int) l2;
		return seed;
	}

	/**
	 * <p>
	 * newRandom.
	 * </p>
	 *
	 * @param seed a int
	 * @return a {@link java.util.Random} object
	 */
	public static BSDRandom newRandom(int seed) {
		return new BSDRandom(seed);
	}

	/**
	 * <p>
	 * applyRS.
	 * </p>
	 *
	 * @param rsObj a {@link java.lang.Object} object
	 */
	public void applyRS(Object rsObj) {
		if (activeSource instanceof StreamInputSource) {
			((StreamInputSource) activeSource).setRecordSeparator(rsObj.toString());
		}
	}
}
