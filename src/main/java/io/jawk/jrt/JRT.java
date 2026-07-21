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

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import io.jawk.Awk;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.intermediate.UntypedObject;
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

	/**
	 * Ceiling for {@link #dynamicPatterns} and {@link #dynamicPatternsIgnoreCase}:
	 * scripts can synthesize an unbounded number of distinct dynamic regexps from
	 * input data, so a cache is dumped wholesale when it fills instead of growing
	 * without bound. Real scripts use a handful of dynamic regexps, so the limit
	 * is effectively never reached.
	 */
	private static final int DYNAMIC_PATTERN_CACHE_LIMIT = 256;

	private final VariableManager vm;

	private IoState ioState;
	/** Output sink used for plain AWK print/printf output. */
	private AwkSink awkSink;
	/** PrintStream used for command error output */
	private PrintStream error;
	/** PrintStream used for runtime warning messages, stderr by default. */
	private PrintStream warning = System.err;
	/** Current IGNORECASE value, as assigned by the script or the host. */
	private Object ignorecase = Long.valueOf(0L);
	/** Precomputed truth of IGNORECASE, consulted by every regexp operation. */
	private boolean ignoreCase;
	/** Case-insensitive twins of precompiled patterns; created on first use. */
	private Map<Pattern, Pattern> caseInsensitivePatterns;
	/** Compiled case-sensitive dynamic (string) regexps, keyed by expression text; created on first use. */
	private Map<String, Pattern> dynamicPatterns;
	/** Compiled case-insensitive dynamic (string) regexps, keyed by expression text; created on first use. */
	private Map<String, Pattern> dynamicPatternsIgnoreCase;
	/** Reused buffer holding the result of the last sub()/gsub() replacement. */
	private final StringBuffer replaceResult = new StringBuffer();
	// Last input line consumed for getline-style transport.
	private Object inputLine = null;
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
	private Object filename; // current input filename scalar (or empty for stdin/pipe)
	private Object errno; // last input I/O error description (gawk ERRNO)
	private Object argind; // ARGV index of the current input file (gawk ARGIND)
	private boolean syntheticFilePresented; // custom InputSource already presented as a single "file"
	private String fs; // field separator
	private String rs; // record separator (regexp)
	private String ofs; // output field separator
	private String ors; // output record separator
	private String convfmt; // number-to-string format
	private String ofmt; // number-to-string for output
	private String subsep; // subscript separator
	private final Locale locale; // locale for number formatting
	private final char decimalSeparator; // locale decimal separator for strnum recognition

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
	@SuppressFBWarnings(value = {
			"EI_EXPOSE_REP2",
			"CT_CONSTRUCTOR_THROW" }, justification = "JRT must hold the provided runtime collaborators for later use;"
					+ " fail-fast argument validation with no security-sensitive state to protect from finalizer attacks")
	public JRT(VariableManager vm, Locale locale, AwkSink awkSink, PrintStream error) {
		this.vm = vm;
		this.locale = locale == null ? Locale.US : locale;
		this.decimalSeparator = DecimalFormatSymbols.getInstance(this.locale).getDecimalSeparator();
		this.awkSink = Objects.requireNonNull(awkSink, "awkSink");
		this.error = error == null ? System.err : error;
		this.nr = 0L;
		this.fnr = 0L;
		this.rstart = 0;
		this.rlength = 0;
		this.filename = "";
		this.fs = Awk.DEFAULT_FS;
		this.rs = Awk.DEFAULT_RS;
		this.ofs = Awk.DEFAULT_OFS;
		this.ors = Awk.DEFAULT_ORS;
		this.convfmt = Awk.DEFAULT_CONVFMT;
		this.ofmt = Awk.DEFAULT_OFMT;
		this.subsep = Awk.DEFAULT_SUBSEP;
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
	 * Sets the stream that receives runtime warning messages. Warnings default
	 * to {@link System#err}, mirroring where gawk sends its diagnostics, and are
	 * deliberately kept apart from the process-stderr stream so they can never
	 * leak into a captured script output.
	 *
	 * @param warningStream stream to receive runtime warnings
	 */
	public void setWarningStream(PrintStream warningStream) {
		this.warning = Objects.requireNonNull(warningStream, "warningStream");
	}

	/**
	 * Prints a runtime warning message to the warning stream (stderr by
	 * default), mirroring where gawk sends its diagnostics.
	 *
	 * @param message warning text to print
	 */
	public void printWarning(String message) {
		warning.println(message);
		warning.flush();
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
		switch (name) {
		case "FS":
		case "RS":
		case "OFS":
		case "ORS":
		case "CONVFMT":
		case "OFMT":
		case "SUBSEP":
		case "FILENAME":
		case "NF":
		case "NR":
		case "FNR":
		case "ARGC":
		case "IGNORECASE":
		case "ERRNO":
		case "ARGIND":
			return true;
		default:
			return false;
		}
	}

	/**
	 * Returns whether the name is a gawk-only special variable that POSIX
	 * mode treats as an ordinary identifier, like {@code gawk --posix} does.
	 * Shared by the parser and the interpreter so both stay in sync.
	 *
	 * @param name variable name to inspect
	 * @return {@code true} when POSIX mode must treat the name as ordinary
	 */
	public static boolean isGawkOnlySpecialVariable(String name) {
		return "ERRNO".equals(name) || "ARGIND".equals(name);
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
	 * Resets per-execution JRT state and re-applies the default runtime special
	 * variables for a new script or expression execution.
	 * <p>
	 * The {@code defaultFs} and {@code defaultRs} parameters allow the caller
	 * to configure the initial field and record separators. Other special variables
	 * ({@code OFS}, {@code ORS}, {@code CONVFMT}, {@code OFMT}, {@code SUBSEP})
	 * use their POSIX-mandated defaults (see {@link Awk} constants) which are
	 * platform-independent and therefore not parameterized. Platform-specific
	 * end-of-line handling is the responsibility of the {@link AwkSink}.
	 *
	 * @param defaultFs default field separator, or {@code null} for
	 *        {@link Awk#DEFAULT_FS}
	 * @param defaultRs default record separator
	 */
	public void prepareForExecution(String defaultFs, String defaultRs) {
		// Close any previously opened IO resources before resetting state.
		jrtCloseAll();

		// Clear per-execution state (IO handles, counters, input state).
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
		errno = "";
		argind = ZERO;
		syntheticFilePresented = false;

		// Apply default runtime special variables.
		setFS(defaultFs == null ? Awk.DEFAULT_FS : defaultFs);
		setRS(defaultRs);
		setOFS(Awk.DEFAULT_OFS);
		setORS(Awk.DEFAULT_ORS);
		setCONVFMT(Awk.DEFAULT_CONVFMT);
		setOFMT(Awk.DEFAULT_OFMT);
		setSUBSEP(Awk.DEFAULT_SUBSEP);
		setFILENAMEViaJrt("");
		setNR(0);
		setFNR(0);
		setRSTART(0);
		setRLENGTH(0);
		setIGNORECASE(Long.valueOf(0L));
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
			if (!applySpecialVariable(name, value)) {
				vm.assignVariable(name, value);
			}
		}
	}

	/**
	 * Applies the assignment of a single JRT-managed special variable.
	 *
	 * @param name variable name
	 * @param value value to assign
	 * @return {@code true} when the name was a JRT-managed special variable,
	 *         {@code false} when the assignment was not handled
	 */
	public boolean applySpecialVariable(String name, Object value) {
		switch (name) {
		case "FS":
			setFS(value);
			return true;
		case "RS":
			setRS(value);
			return true;
		case "OFS":
			setOFS(value);
			return true;
		case "ORS":
			setORS(value);
			return true;
		case "CONVFMT":
			setCONVFMT(value);
			return true;
		case "OFMT":
			setOFMT(value);
			return true;
		case "SUBSEP":
			setSUBSEP(value);
			return true;
		case "FILENAME":
			setFILENAMEViaJrt(value);
			return true;
		case "NF":
			setNF(value);
			return true;
		case "NR":
			setNR(value);
			return true;
		case "FNR":
			setFNR(value);
			return true;
		case "ARGC":
			setARGC(value);
			return true;
		case "IGNORECASE":
			setIGNORECASE(value);
			return true;
		case "ERRNO":
			setERRNO(value);
			return true;
		case "ARGIND":
			setARGIND(value);
			return true;
		default:
			return false;
		}
	}

	/**
	 * Applies only the JRT-managed special variable assignments from the
	 * supplied map (FS, RS, OFS, ORS, CONVFMT, OFMT, SUBSEP, FILENAME, NF,
	 * NR, FNR, ARGC, IGNORECASE). Non-special variables are silently skipped because
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
			// Non-special variables are skipped; they are assigned later
			// via the tuple instruction stream
			applySpecialVariable(var.getKey(), var.getValue());
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
			aa.put(var.getKey(), new StrNum(var.getValue()));
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
	public static Object getAssocArrayValue(Map<Object, Object> map, Object key) {
		if (map instanceof AssocArray) {
			return map.get(key);
		}
		Object value = map.get(key);
		return value != null ? value : BLANK;
	}

	/**
	 * Returns the AWK string value of an associative array entry, or
	 * {@code null} when the array has no such key. This is the common way to
	 * read optional settings out of AWK arrays, such as
	 * {@code PROCINFO["sorted_in"]} or {@code ENVIRON["TZ"]}.
	 *
	 * @param map associative array to read
	 * @param key entry key
	 * @return the entry value converted with {@code CONVFMT}, or {@code null}
	 *         when the key is absent
	 */
	public String getAwkStringEntry(Map<Object, Object> map, Object key) {
		if (!containsAwkKey(map, key)) {
			return null;
		}
		return toAwkString(getAssocArrayValue(map, key));
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

		if (o instanceof StrNum) {
			StrNum strNum = (StrNum) o;
			if (strNum.isNumber()) {
				return strNum.doubleValue();
			}
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
		return compare2(o1, o2, mode, false);
	}

	/**
	 * Compares two objects like {@link #compare2(Object, Object, int)}, folding
	 * case in string comparisons when {@code ignoreCase} is set: gawk's
	 * {@code IGNORECASE} applies to string relational operators, not only to
	 * regexp operations.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode the comparison mode, as in {@link #compare2(Object, Object, int)}
	 * @param ignoreCase whether string comparisons ignore case
	 * @return a boolean
	 */
	public static boolean compare2(Object o1, Object o2, int mode, boolean ignoreCase) {
		if (o1 instanceof Number && o2 instanceof Number) {
			return compareNumbers(((Number) o1).doubleValue(), ((Number) o2).doubleValue(), mode);
		}

		String o1String = o1 == null ? "" : o1.toString();
		String o2String = o2 == null ? "" : o2.toString();

		if (o1 instanceof UninitializedObject) {
			if (isBlankOrZero(o2, o2String)) {
				return mode == 0;
			} else {
				return mode < 0;
			}
		}
		if (o2 instanceof UninitializedObject) {
			if (isBlankOrZero(o1, o1String)) {
				return mode == 0;
			} else {
				return mode > 0;
			}
		}

		if (isNumericComparisonOperand(o1) && isNumericComparisonOperand(o2)) {
			return compareNumbers(getDoubleForComparison(o1), getDoubleForComparison(o2), mode);
		}

		if (mode == 0) {
			return ignoreCase ? o1String.equalsIgnoreCase(o2String) : o1String.equals(o2String);
		}
		int comparison = ignoreCase ? o1String.compareToIgnoreCase(o2String) : o1String.compareTo(o2String);
		return mode < 0 ? comparison < 0 : comparison > 0;
	}

	/**
	 * Implements the {@code index()} builtin: the 1-based position of
	 * {@code needle} within {@code haystack}, or 0 when absent, folding case
	 * when {@code IGNORECASE} is set.
	 *
	 * @param haystack text to search
	 * @param needle text to find
	 * @return 1-based match position, 0 when not found
	 */
	public int index(String haystack, String needle) {
		if (!ignoreCase) {
			return haystack.indexOf(needle) + 1;
		}
		int max = haystack.length() - needle.length();
		for (int i = 0; i <= max; i++) {
			if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
				return i + 1;
			}
		}
		return 0;
	}

	private static boolean isBlankOrZero(Object value, String stringValue) {
		if (value instanceof UninitializedObject) {
			return true;
		}
		if (value instanceof Number) {
			return ((Number) value).doubleValue() == 0.0D;
		}
		if (value instanceof StrNum && ((StrNum) value).isNumber()) {
			return ((StrNum) value).doubleValue() == 0.0D;
		}
		return "".equals(stringValue) || "0".equals(stringValue);
	}

	private static boolean isNumericComparisonOperand(Object value) {
		return value instanceof Number || value instanceof StrNum && ((StrNum) value).isNumber();
	}

	private static double getDoubleForComparison(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		return ((StrNum) value).doubleValue();
	}

	private static boolean compareNumbers(double o1Number, double o2Number, int mode) {
		if (mode < 0) {
			return o1Number < o2Number;
		} else if (mode == 0) {
			return o1Number == o2Number;
		} else {
			return o1Number > o2Number;
		}
	}

	/**
	 * Converts an internal runtime scalar to the value exposed through Java APIs.
	 *
	 * @param value internal scalar value
	 * @return plain Java scalar value
	 */
	public static Object toJavaScalar(Object value) {
		if (value instanceof StrNum) {
			return value.toString();
		}
		if (value instanceof Double || value instanceof Float) {
			double number = ((Number) value).doubleValue();
			if (isActuallyLong(number)) {
				return Long.valueOf((long) Math.rint(number));
			}
		}
		return value;
	}

	/**
	 * Returns whether the supplied text parses as an AWK number under this
	 * runtime's locale, as used for strnum recognition.
	 *
	 * @param value text to test
	 * @return {@code true} when {@code value} is an input numeric string
	 */
	public boolean isParseableNumber(String value) {
		return isParseableNumber(value, decimalSeparator);
	}

	/**
	 * Replaces the untyped marker by AWK's assigned blank scalar. Reading a
	 * missing array element creates and returns the untyped marker (so
	 * {@code typeof()} can see it), but an assignment must not propagate it:
	 * after {@code x = a[missing]}, {@code x} is an assigned blank scalar
	 * ({@code typeof(x) == "unassigned"}), exactly as in gawk. This is a single
	 * {@code instanceof} on the assignment paths.
	 *
	 * @param value value about to be stored by an assignment
	 * @return the assigned blank scalar when the value was the untyped marker,
	 *         otherwise the original value
	 */
	public static Object untypedToBlank(Object value) {
		return value instanceof UntypedObject ? BLANK : value;
	}

	static boolean isParseableNumber(String value, char decimalSeparator) {
		int index = 0;
		int length = value.length();

		if (length == 0) {
			return false;
		}

		char current = value.charAt(index);
		if (current == '+' || current == '-') {
			index++;
			if (index == length) {
				return false;
			}
		}

		boolean digitFound = false;
		while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9') {
			index++;
			digitFound = true;
		}

		if (index < length && value.charAt(index) == decimalSeparator) {
			index++;
			while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9') {
				index++;
				digitFound = true;
			}
		}

		if (!digitFound) {
			return false;
		}

		if (index < length && (value.charAt(index) == 'e' || value.charAt(index) == 'E')) {
			index++;
			if (index < length && (value.charAt(index) == '+' || value.charAt(index) == '-')) {
				index++;
			}

			boolean exponentDigitFound = false;
			while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9') {
				index++;
				exponentDigitFound = true;
			}
			if (!exponentDigitFound) {
				return false;
			}
		}

		return index == length;
	}

	static String normalizeNumberForComparison(String value, char decimalSeparator) {
		return decimalSeparator == '.' ? value : value.replace(decimalSeparator, '.');
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one plus a given object. For Integers and Doubles,
	 * this is similar to o+1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not contain a numeric prefix, 1 is returned.
	 *
	 * @param o The object to increase.
	 * @return {@code o + 1} if o is numeric or contains a numeric prefix;
	 *         otherwise, {@code 1.0}
	 */
	public static Object inc(Object o) {
		return toDouble(o) + 1;
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one minus a given object. For Integers and Doubles,
	 * this is similar to o-1. For Strings, attempts are
	 * made to convert it to a double first. If the
	 * String does not contain a numeric prefix, -1 is returned.
	 *
	 * @param o The object to increase.
	 * @return {@code o - 1} if o is numeric or contains a numeric prefix;
	 *         otherwise, {@code -1.0}
	 */
	public static Object dec(Object o) {
		return toDouble(o) - 1;
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
		} else if (o instanceof StrNum) {
			StrNum strNum = (StrNum) o;
			val = strNum.isNumber() ? strNum.doubleValue() != 0 : strNum.toString().length() > 0;
		} else if (o instanceof String) {
			val = (o.toString().length() > 0);
		} else if (o instanceof UninitializedObject) {
			val = false;
		} else if (o instanceof Pattern) {
			// match against $0
			Pattern pattern = caseAwarePattern((Pattern) o);
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
		return splitWorker(splitTokenizer(toAwkString(string), fieldSeparator), toArrayMap(array));
	}

	private static Map<Object, Object> toArrayMap(Object array) {
		if (!(array instanceof Map)) {
			throw new IllegalArgumentException("split target must be a Map.");
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> arrayMap = (Map<Object, Object>) array;
		return arrayMap;
	}

	private int splitWorker(Enumeration<Object> e, Map<Object, Object> array) {
		int cnt = 0;
		array.clear();
		while (e.hasMoreElements()) {
			Object value = e.nextElement();
			array.put(Long.valueOf(++cnt), toInputScalar(value));
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
	 * @return the current input line scalar value, or {@code null}
	 */
	public Object getInputLine() {
		if (recordState != null) {
			return recordState.getField(0);
		}
		return inputLine;
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
	 * Sets IGNORECASE, precomputing its truth value so regexp operations can
	 * test a boolean instead of coercing the raw value on every match.
	 *
	 * @param value new IGNORECASE value
	 */
	public void setIGNORECASE(Object value) {
		this.ignorecase = value == null ? Long.valueOf(0L) : value;
		// gawk: IGNORECASE is active when its value is "nonzero or non-null",
		// i.e. regular AWK truthiness (strnum-aware), not numeric coercion
		this.ignoreCase = toBoolean(this.ignorecase);
	}

	/**
	 * Get IGNORECASE from the VariableManager.
	 *
	 * @return IGNORECASE value
	 */
	public Object getIGNORECASEVar() {
		return ignorecase;
	}

	/**
	 * Returns whether IGNORECASE is currently nonzero, making regexp
	 * operations case-insensitive. The truth value is precomputed when
	 * IGNORECASE is assigned.
	 *
	 * @return {@code true} when IGNORECASE is nonzero
	 */
	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	/**
	 * Returns the {@link Pattern} flags implied by the current
	 * {@code IGNORECASE} setting; dynamic regexps should be compiled with
	 * these flags.
	 *
	 * @return {@link Pattern#CASE_INSENSITIVE} when {@code IGNORECASE} is
	 *         truthy, 0 otherwise
	 */
	public int regexpFlags() {
		return ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
	}

	/**
	 * {@code sub()} functionality: replaces the first match of {@code ere} in
	 * {@code orig} with {@code repl}, honoring {@code IGNORECASE}. The
	 * substituted text is available through {@link #getReplaceResult()}.
	 *
	 * @param orig original text
	 * @param repl AWK replacement text
	 * @param ere regular expression
	 * @return number of replacements performed (0 or 1)
	 */
	public int replaceFirst(String orig, String repl, String ere) {
		return replace(orig, repl, ere, false);
	}

	/**
	 * {@code gsub()} functionality: replaces every match of {@code ere} in
	 * {@code orig} with {@code repl}, honoring {@code IGNORECASE}. The
	 * substituted text is available through {@link #getReplaceResult()}.
	 *
	 * @param orig original text
	 * @param repl AWK replacement text
	 * @param ere regular expression
	 * @return number of replacements performed
	 */
	public int replaceAll(String orig, String repl, String ere) {
		return replace(orig, repl, ere, true);
	}

	private int replace(String orig, String repl, String ere, boolean global) {
		replaceResult.setLength(0);
		String preparedReplacement = prepareReplacement(repl, false);
		Matcher matcher = dynamicPattern(ere).matcher(orig);
		int count = 0;
		while (matcher.find()) {
			count++;
			matcher.appendReplacement(replaceResult, preparedReplacement);
			if (!global) {
				break;
			}
		}
		matcher.appendTail(replaceResult);
		return count;
	}

	/**
	 * Returns the text produced by the last {@link #replaceFirst} or
	 * {@link #replaceAll} call.
	 *
	 * @return substituted text
	 */
	public String getReplaceResult() {
		return replaceResult.toString();
	}

	/**
	 * Evaluates the AWK match operator ({@code text ~ regexp}), honoring
	 * {@code IGNORECASE} for both precompiled regexp constants and dynamic
	 * expressions.
	 *
	 * @param text text to match
	 * @param regexp precompiled {@link Pattern} or dynamic regexp text
	 * @return {@code true} when the regexp matches anywhere in the text
	 */
	public boolean matches(String text, Object regexp) {
		if (regexp instanceof Pattern) {
			// find(): AWK's ~ matches anywhere, not the entire string
			return caseAwarePattern((Pattern) regexp).matcher(text).find();
		}
		return dynamicPattern(toAwkString(regexp)).matcher(text).find();
	}

	/**
	 * {@code match()} functionality: locates {@code ere} in {@code s} honoring
	 * {@code IGNORECASE}, updating {@code RSTART} and {@code RLENGTH}.
	 *
	 * @param s text to search
	 * @param ere regular expression
	 * @return the match position ({@code RSTART}), or 0 when there is no match
	 */
	public int matchPosition(String s, String ere) {
		Matcher matcher = dynamicPattern(ere).matcher(s);
		if (matcher.find()) {
			int start = matcher.start() + 1;
			setRSTART(start);
			setRLENGTH(matcher.end() - matcher.start());
			return start;
		}
		setRSTART(0);
		setRLENGTH(-1);
		return 0;
	}

	/**
	 * Builds the tokenizer splitting {@code input} by the given separator,
	 * following AWK field-splitting rules ({@code " "} splits on whitespace
	 * runs, {@code ""} splits into characters, a single character is literal)
	 * and honoring {@code IGNORECASE} for regexp separators. A precompiled
	 * {@link Pattern} separator (a regexp literal) is used directly.
	 *
	 * @param input text to split
	 * @param separator field separator: precompiled pattern or text
	 * @return tokenizer producing the split parts
	 */
	public Enumeration<Object> splitTokenizer(String input, Object separator) {
		if (separator instanceof Pattern) {
			return new RegexTokenizer(input, caseAwarePattern((Pattern) separator));
		}
		String fsString = toAwkString(separator);
		if (fsString.equals(" ")) {
			return new StringTokenizer(input);
		}
		if (fsString.isEmpty()) {
			return new CharacterTokenizer(input);
		}
		if (fsString.length() == 1) {
			char fsChar = fsString.charAt(0);
			if (ignoreCase && Character.isLetter(fsChar)) {
				// a letter is regex-safe, so case-insensitive splitting can
				// go through the regexp path
				return new RegexTokenizer(input, dynamicPattern(fsString));
			}
			return new SingleCharacterTokenizer(input, fsChar);
		}
		return new RegexTokenizer(input, dynamicPattern(fsString));
	}

	/**
	 * Converts an AWK replacement text into a Java {@link Matcher} replacement:
	 * {@code &} becomes the whole match, {@code \&} a literal ampersand, and
	 * {@code $} is escaped.
	 *
	 * @param awkRepl AWK replacement text
	 * @param backreferences whether {@code \N} denotes capture group {@code N},
	 *        as in gawk's {@code gensub()}; when {@code false}, {@code \N} stays
	 *        literal as in {@code sub()} and {@code gsub()}
	 * @return the equivalent Java replacement string
	 */
	public static String prepareReplacement(String awkRepl, boolean backreferences) {
		return prepareReplacement(awkRepl, backreferences ? Integer.MAX_VALUE : -1);
	}

	/**
	 * Converts an AWK replacement text into a Java {@link Matcher} replacement,
	 * resolving gensub-style backreferences against a known number of capture
	 * groups: {@code \N} beyond {@code maxGroup} is replaced by the empty
	 * string, as gawk does, instead of producing a group reference that would
	 * make the matcher throw.
	 *
	 * @param awkRepl AWK replacement text
	 * @param maxGroup highest valid capture group number, or a negative value
	 *        to disable backreferences entirely ({@code sub()}/{@code gsub()}
	 *        semantics)
	 * @return the equivalent Java replacement string
	 */
	public static String prepareReplacement(String awkRepl, int maxGroup) {
		boolean backreferences = maxGroup >= 0;
		if (awkRepl == null) {
			return "";
		}

		if ((awkRepl.indexOf('\\') == -1) && (awkRepl.indexOf('$') == -1) && (awkRepl.indexOf('&') == -1)) {
			return awkRepl;
		}

		StringBuilder javaRepl = new StringBuilder();
		for (int i = 0; i < awkRepl.length(); i++) {
			char c = awkRepl.charAt(i);

			if (c == '\\' && i == awkRepl.length() - 1) {
				// In gensub mode a trailing backslash is a literal backslash;
				// left bare it would make Matcher.appendReplacement throw. The
				// sub()/gsub() mapping keeps its historical bare form.
				javaRepl.append(backreferences ? "\\\\" : "\\");
				continue;
			}

			if (c == '\\') {
				i++;
				c = awkRepl.charAt(i);
				if (c == '&') {
					javaRepl.append('&');
					continue;
				} else if (c == '\\') {
					javaRepl.append("\\\\");
					continue;
				} else if (backreferences && Character.isDigit(c)) {
					if (c - '0' <= maxGroup) {
						javaRepl.append('$').append(c);
					}
					// references beyond the pattern's groups expand to the
					// empty string, as in gawk
					continue;
				}

				javaRepl.append('\\');
			}

			if (c == '$') {
				javaRepl.append("\\$");
			} else if (c == '&') {
				javaRepl.append("$0");
			} else {
				javaRepl.append(c);
			}
		}

		return javaRepl.toString();
	}

	/**
	 * Returns the pattern itself, or its case-insensitive twin when
	 * {@code IGNORECASE} is set. Twins are compiled once and cached here:
	 * the JDK's {@link Pattern#compile(String)} performs no caching of its
	 * own (every call reparses the expression), so dropping this cache would
	 * recompile the regexp on every record matched against a regexp constant.
	 *
	 * @param pattern base pattern
	 * @return pattern honoring the current {@code IGNORECASE} setting
	 */
	public Pattern caseAwarePattern(Pattern pattern) {
		if (!ignoreCase || (pattern.flags() & Pattern.CASE_INSENSITIVE) != 0) {
			return pattern;
		}
		if (caseInsensitivePatterns == null) {
			caseInsensitivePatterns = new IdentityHashMap<Pattern, Pattern>();
		}
		return caseInsensitivePatterns
				.computeIfAbsent(
						pattern,
						base -> Pattern.compile(base.pattern(), base.flags() | Pattern.CASE_INSENSITIVE));
	}

	/**
	 * Compiles a dynamic (string) regexp with the flags implied by the current
	 * {@code IGNORECASE} setting, caching compiled patterns by expression text:
	 * dynamic regexps are typically reused across records (for example a
	 * {@code gsub(dynstr, ...)} loop), and the JDK's
	 * {@link Pattern#compile(String, int)} reparses the expression on every
	 * call. Each {@code IGNORECASE} setting has its own cache; the settings
	 * cannot share one because {@link Pattern#flags()} reflects inline flag
	 * constructs such as {@code (?i)}, so it cannot tell apart a pattern
	 * compiled under the other setting.
	 *
	 * @param ere dynamic regular expression text
	 * @return the compiled pattern honoring the current {@code IGNORECASE}
	 *         setting
	 */
	public Pattern dynamicPattern(String ere) {
		if (dynamicPatterns == null) {
			dynamicPatterns = new HashMap<String, Pattern>();
			dynamicPatternsIgnoreCase = new HashMap<String, Pattern>();
		}
		Map<String, Pattern> cache = ignoreCase ? dynamicPatternsIgnoreCase : dynamicPatterns;
		Pattern pattern = cache.get(ere);
		if (pattern == null) {
			if (cache.size() >= DYNAMIC_PATTERN_CACHE_LIMIT) {
				cache.clear();
			}
			pattern = Pattern.compile(ere, regexpFlags());
			cache.put(ere, pattern);
		}
		return pattern;
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
	public Object getFILENAME() {
		return filename == null ? "" : filename;
	}

	/**
	 * Set FILENAME through VariableManager and update JRT mirror.
	 *
	 * @param name file name to set
	 */
	public void setFILENAMEViaJrt(Object name) {
		this.filename = normalizeRecordValue(name);
	}

	/**
	 * Get ERRNO as tracked by JRT.
	 *
	 * @return current ERRNO (empty string when no input error is pending)
	 */
	public Object getERRNO() {
		return errno == null ? "" : errno;
	}

	/**
	 * Set ERRNO tracked by JRT.
	 *
	 * @param value new ERRNO value
	 */
	public void setERRNO(Object value) {
		this.errno = normalizeRecordValue(value);
	}

	/**
	 * Get ARGIND as tracked by JRT.
	 *
	 * @return ARGV index of the current input file (0 before any file is open)
	 */
	public Object getARGIND() {
		return argind == null ? ZERO : argind;
	}

	/**
	 * Set ARGIND tracked by JRT.
	 *
	 * @param value new ARGIND value
	 */
	public void setARGIND(Object value) {
		this.argind = normalizeRecordValue(value);
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
	 * @param inputLineParam input value
	 */
	public void setInputLine(Object inputLineParam) {
		Object inputValue = normalizeRecordValue(inputLineParam);
		this.inputLine = inputValue;
		recordState = new RecordState(inputValue, null);
	}

	/**
	 * Creates an input-derived AWK scalar value.
	 *
	 * @param value input text
	 * @return input-derived scalar value
	 */
	public Object toInputScalar(Object value) {
		if (value instanceof String) {
			return new StrNum((String) value, decimalSeparator);
		}
		if (value instanceof StrNum) {
			return value;
		}
		if (value == null || value instanceof UninitializedObject) {
			return new StrNum("", decimalSeparator);
		}
		return new StrNum(value.toString(), decimalSeparator);
	}

	private static Object normalizeRecordValue(Object value) {
		if (value == null || value instanceof UninitializedObject) {
			return "";
		}
		return value;
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

		bindConsumedRecord(source);
		return true;
	}

	/**
	 * Attempt to consume one record from the current input file only, without
	 * ever advancing to the next input file. Used by the per-file main input
	 * loop when BEGINFILE/ENDFILE rules or {@code nextfile} are present, so
	 * that the ENDFILE rules can run at each file boundary.
	 * <p>
	 * When the current input file could not be opened (a pending ERRNO set by
	 * {@link #advanceToNextFile(InputSource)} that no {@code nextfile}
	 * consumed), the usual fatal error is raised, mirroring gawk.
	 * </p>
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return {@code true} if a record was consumed; {@code false} at the end
	 *         of the current input file
	 * @throws IOException if the source raises an I/O error
	 */
	public boolean consumeCurrentFileInput(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		if (!(source instanceof StreamInputSource)) {
			// Custom input sources behave as a single unnamed input file.
			return consumeInput(source);
		}
		StreamInputSource streamSource = (StreamInputSource) source;
		throwIfCurrentFileUnopened(streamSource);
		activeSource = source;
		if (!streamSource.nextRecordInCurrentFile()) {
			return false;
		}
		bindConsumedRecord(source);
		return true;
	}

	/**
	 * Attempt to consume one record of the current input file only for
	 * {@code getline target}, returning the input value and leaving the
	 * current input record state untouched. Used instead of
	 * {@link #consumeInputToTarget(InputSource)} while the per-file main
	 * input loop is active, so a {@code getline} in an action never crosses a
	 * file boundary behind the BEGINFILE/ENDFILE rules' back.
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return the consumed input value, or {@code null} at the end of the
	 *         current input file
	 * @throws IOException if the source raises an I/O error
	 */
	public Object consumeCurrentFileInputToTarget(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		if (!(source instanceof StreamInputSource)) {
			// Custom input sources behave as a single unnamed input file.
			return consumeInputToTarget(source);
		}
		StreamInputSource streamSource = (StreamInputSource) source;
		throwIfCurrentFileUnopened(streamSource);
		activeSource = source;
		materializeCurrentRecord();
		if (!streamSource.nextRecordInCurrentFile()) {
			return null;
		}

		RecordState inputState = new RecordState(source);
		this.nr++;
		if (countsTowardFNR(source)) {
			this.fnr++;
		}
		return new StrNum(inputState.getRecordText(), decimalSeparator);
	}

	/**
	 * Raises the gawk-compatible fatal error when the current input file
	 * could not be opened and no BEGINFILE rule bypassed it with
	 * {@code nextfile}.
	 *
	 * @param streamSource the main input source to check
	 */
	private void throwIfCurrentFileUnopened(StreamInputSource streamSource) {
		String openError = streamSource.getCurrentFileOpenError();
		if (openError != null) {
			throw new AwkRuntimeException(
					"cannot open file `" + toAwkString(getFILENAME()) + "' for reading: " + openError);
		}
	}

	/**
	 * Advance the main input to the next input file, applying pending
	 * {@code name=value} command-line assignments along the way. On success,
	 * FILENAME, FNR, ARGIND, and ERRNO are updated and {@code $0} is cleared,
	 * so the BEGINFILE rules observe the new file. A file that cannot be
	 * opened is still reported as available, with ERRNO carrying the error
	 * description (gawk BEGINFILE error handling).
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return {@code true} when a new input file (or the initial stdin
	 *         stream) is available; {@code false} when input is exhausted
	 * @throws IOException if an I/O error occurs while traversing ARGV
	 */
	public boolean advanceToNextFile(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		if (source instanceof StreamInputSource) {
			return ((StreamInputSource) source).advanceToNextFile();
		}
		// Custom input sources behave as a single unnamed input file.
		if (syntheticFilePresented) {
			return false;
		}
		syntheticFilePresented = true;
		return true;
	}

	/**
	 * Returns whether the current input file of the given source failed to
	 * open, leaving a pending error that only a {@code nextfile} statement in
	 * a BEGINFILE rule may bypass.
	 *
	 * @param source source strategy that provides records
	 * @return {@code true} when the current input file could not be opened
	 */
	public boolean hasPendingInputFileError(InputSource source) {
		return source instanceof StreamInputSource
				&& ((StreamInputSource) source).getCurrentFileOpenError() != null;
	}

	/**
	 * Binds the record just consumed from the given source as the current
	 * input record and updates the NR/FNR counters.
	 *
	 * @param source the source a record was just consumed from
	 */
	private void bindConsumedRecord(InputSource source) {
		inputLine = null;
		recordState = new RecordState(source);

		this.nr++;
		if (countsTowardFNR(source)) {
			this.fnr++;
		}
	}

	/**
	 * Returns whether consuming a record from the given source advances FNR,
	 * the per-file record counter. All records of the main command-line input
	 * flow count, including standard input (POSIX defines FNR as the record
	 * number in the <em>current</em> input file, which stdin is). For custom
	 * {@link InputSource} implementations, {@link InputSource#isFromFilenameList()}
	 * keeps controlling FNR, as documented.
	 *
	 * @param source the source a record was just consumed from
	 * @return {@code true} when the record advances FNR
	 */
	private static boolean countsTowardFNR(InputSource source) {
		return source instanceof StreamInputSource || source.isFromFilenameList();
	}

	/**
	 * Attempt to consume one record from a structured input source for
	 * {@code getline target}, returning the input value and leaving the
	 * current input record state untouched.
	 *
	 * @param source source strategy that provides records and optional
	 *        pre-split fields
	 * @return the consumed input value, or {@code null} when the source is
	 *         exhausted
	 * @throws IOException if the source raises an I/O error
	 */
	public Object consumeInputToTarget(final InputSource source) throws IOException {
		Objects.requireNonNull(source, "source");
		activeSource = source;
		materializeCurrentRecord();
		if (!source.nextRecord()) {
			return null;
		}

		RecordState inputState = new RecordState(source);
		this.nr++;
		if (countsTowardFNR(source)) {
			this.fnr++;
		}
		return new StrNum(inputState.getRecordText(), decimalSeparator);
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
		recordState = new RecordState(toInputScalar(record), preFields);
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
		String value = valueObj == null ? "" : valueObj.toString();
		int fieldIndex = (int) fieldNum;
		RecordState state = ensureRecordStateForFieldMutation();
		if (valueObj instanceof UninitializedObject) {
			if (fieldIndex <= state.getNF()) {
				state.setField(fieldIndex - 1, "");
			}
		} else {
			while (state.getNF() < fieldIndex) {
				state.addField(BLANK);
			}
			state.setField(fieldIndex - 1, valueObj);
		}
		state.markRecordTextDirty();
		return value;
	}

	protected void rebuildDollarZeroFromFields() {
		if (recordState != null) {
			recordState.markRecordTextDirty();
			inputLine = recordState.getField(0);
		}
	}

	private void materializeCurrentRecord() {
		if (recordState != null) {
			recordState.materialize();
		}
	}

	private RecordState ensureRecordStateForTextMutation() {
		if (recordState == null) {
			recordState = new RecordState(inputLine, null);
		}
		return recordState;
	}

	private RecordState ensureRecordStateForFieldMutation() {
		RecordState state = ensureRecordStateForTextMutation();
		state.ensureFieldsMaterialized();
		return state;
	}

	private List<Object> sanitizeFields(List<String> rawFields) {
		List<Object> copy = new ArrayList<Object>(rawFields.size());
		for (String field : rawFields) {
			String value = field == null ? "" : field;
			copy.add(new StrNum(value, decimalSeparator));
		}
		return copy;
	}

	private List<Object> splitRecordText(String recordText, String fieldSeparator) {
		List<Object> fields = new ArrayList<Object>();
		if (recordText == null || recordText.isEmpty()) {
			return fields;
		}

		Enumeration<Object> tokenizer = splitTokenizer(recordText, fieldSeparator);

		while (tokenizer.hasMoreElements()) {
			fields.add(new StrNum((String) tokenizer.nextElement(), decimalSeparator));
		}
		return fields;
	}

	private static String joinFieldsWithLiteralSeparator(List<Object> fields, String separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				sb.append(separator);
			}
			Object field = fields.get(i);
			sb.append(field == null ? "" : field.toString());
		}
		return sb.toString();
	}

	private String rebuildRecordTextFromFields(List<Object> fields) {
		return joinFieldsWithLiteralSeparator(fields, ofs);
	}

	private final class RecordState {

		private final String fieldSeparatorAtRead;
		private final InputSource source;
		private String recordText;
		private Object recordScalar;
		private List<Object> fields;
		private boolean recordTextAvailable;
		private boolean fieldsAvailable;
		private boolean recordTextDirty;
		private boolean fieldsDirty;
		private boolean recordTextLoadedFromSource;
		private boolean fieldsLoadedFromSource;

		private RecordState(InputSource source) {
			this(null, null, source);
		}

		private RecordState(Object recordValue, List<String> rawFields) {
			this(recordValue, rawFields, null);
		}

		private RecordState(Object recordValue, List<String> rawFields, InputSource source) {
			this.fieldSeparatorAtRead = fs;
			this.source = source;
			if (recordValue != null) {
				this.recordScalar = normalizeRecordValue(recordValue);
				this.recordText = this.recordScalar.toString();
				this.recordTextAvailable = true;
			} else if (rawFields == null && source == null) {
				this.recordScalar = "";
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
					recordScalar = recordText;
				} else {
					loadRecordTextFromSource();
					if (!recordTextAvailable) {
						loadFieldsFromSource();
						if (!fieldsAvailable) {
							throw new IllegalStateException(
									"InputSource must provide record text, fields, or both after nextRecord()");
						}
						recordText = joinFieldsWithLiteralSeparator(fields, fieldSeparatorAtRead);
						recordScalar = new StrNum(recordText, decimalSeparator);
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
				String value = getRecordText();
				if (recordScalar == null) {
					recordScalar = value;
				}
				return recordScalar;
			}
			ensureFieldsMaterialized();
			int zeroBasedIndex = fieldIndex - 1;
			if (zeroBasedIndex < 0 || zeroBasedIndex >= fields.size()) {
				return BLANK;
			}
			return fields.get(zeroBasedIndex);
		}

		private void setField(int zeroBasedIndex, Object value) {
			ensureFieldsMaterialized();
			fields.set(zeroBasedIndex, normalizeFieldValue(value));
			markRecordTextDirty();
		}

		private void addField(Object value) {
			ensureFieldsMaterialized();
			fields.add(normalizeFieldValue(value));
			markRecordTextDirty();
		}

		private Object normalizeFieldValue(Object value) {
			if (value == null) {
				return "";
			}
			return value;
		}

		private void removeField(int zeroBasedIndex) {
			ensureFieldsMaterialized();
			fields.remove(zeroBasedIndex);
			markRecordTextDirty();
		}

		private void markRecordTextDirty() {
			recordTextDirty = true;
			recordTextAvailable = fieldsAvailable;
			recordScalar = null;
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
			if (recordTextAvailable) {
				recordScalar = new StrNum(recordText, decimalSeparator);
			}
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
		AwkSink sink = getPipeAwkSink(cmd);
		sink.print(ofs, ors, ofmt, values);
		sink.flush();
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
		sink.flush();
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

		String recordText = pr.readRecord();
		if (recordText == null) {
			return false;
		} else {
			jrtInputString = recordText;
			inputLine = toInputScalar(recordText);
			recordState = new RecordState(inputLine, null);
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
		String recordText = commandInput.reader.readRecord();
		if (recordText == null) {
			return false;
		} else {
			jrtInputString = recordText;
			inputLine = toInputScalar(recordText);
			recordState = new RecordState(inputLine, null);
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
