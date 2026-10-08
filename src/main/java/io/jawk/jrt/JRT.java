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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

	/** gawk special filename designating the standard input of the process. */
	private static final String DEV_STDIN = "/dev/stdin";
	/** gawk special filename designating the standard output of the process. */
	private static final String DEV_STDOUT = "/dev/stdout";
	/** gawk special filename designating the standard error of the process. */
	private static final String DEV_STDERR = "/dev/stderr";
	/** gawk special filename designating file descriptor 0 (standard input). */
	private static final String DEV_FD_0 = "/dev/fd/0";
	/** gawk special filename designating file descriptor 1 (standard output). */
	private static final String DEV_FD_1 = "/dev/fd/1";
	/** gawk special filename designating file descriptor 2 (standard error). */
	private static final String DEV_FD_2 = "/dev/fd/2";
	/** Filename designating the null device on every Unix system. */
	private static final String DEV_NULL = "/dev/null";
	/** Name of the null device on Windows. */
	private static final String WINDOWS_NULL_DEVICE = "NUL";

	private final VariableManager vm;

	private IoState ioState;
	/** Output sink used for plain AWK print/printf output. */
	private AwkSink awkSink;
	/** PrintStream used for command error output */
	private PrintStream error;
	/** PrintStream used for runtime warning messages, stderr by default. */
	private PrintStream warning = System.err;
	/**
	 * Stream backing the {@code /dev/stdin} special filename. It defaults to the
	 * standard input of the JVM and is replaced by the input stream the host
	 * configured for the run, so that {@code getline < "/dev/stdin"} reads the
	 * same data as the main input loop does when no operand is given.
	 */
	private InputStream standardInput = System.in;

	private boolean spawnedProcessesInheritStandardInput;
	/**
	 * Sink writing to the standard error of the process, used by the
	 * {@code /dev/stderr} special filename; created on first use and discarded
	 * whenever the warning stream is replaced.
	 */
	private AwkSink standardErrorSink;
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

	/**
	 * Sink that flushes its stream after every operation, so that partial lines
	 * are visible immediately. Used by the {@code /dev/stderr} special filename,
	 * which gawk keeps unbuffered.
	 */
	private static final class FlushingAwkSink extends OutputStreamAwkSink {

		private FlushingAwkSink(PrintStream printStream, Locale locale) {
			super(printStream, locale);
		}

		@Override
		public void print(String ofs, String ors, String ofmt, Object... values) {
			super.print(ofs, ors, ofmt, values);
			flush();
		}

		@Override
		public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values) {
			super.printf(ofs, ors, ofmt, convfmt, format, values);
			flush();
		}
	}

	private static final class IoState {

		private final Map<String, PartitioningReader> fileReaders = new HashMap<String, PartitioningReader>();
		private final Map<String, CommandInputState> commandInputs = new HashMap<String, CommandInputState>();
		private final Map<String, FileOutputState> fileOutputs = new HashMap<String, FileOutputState>();
		private final Map<String, ProcessOutputState> processOutputs = new HashMap<String, ProcessOutputState>();
		/**
		 * Sink handed out for each standard output special filename a redirection
		 * is currently open on. The sink is retained rather than resolved again on
		 * {@code close()}, so that the redirection is always flushed through the
		 * sink that actually received its writes, even if the runtime's default
		 * output sink has been replaced since.
		 */
		private final Map<String, AwkSink> specialOutputs = new HashMap<String, AwkSink>();
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
		// The /dev/stderr sink wraps the warning stream: drop it so that the
		// next write is routed to the new stream.
		this.standardErrorSink = null;
	}

	/**
	 * Binds the stream that the {@code /dev/stdin} special filename reads from to
	 * the input source of the execution that is starting. A stream-backed source
	 * lends the stream it falls back to when {@code ARGV} holds no filename, which
	 * is what the run treats as its standard input; a source that produces records
	 * some other way has no such stream, so {@code /dev/stdin} designates the
	 * standard input of the JVM.
	 *
	 * @param inputSource input source bound to this execution
	 */
	public void bindStandardInput(InputSource inputSource) {
		this.standardInput = inputSource instanceof StreamInputSource ?
				((StreamInputSource) inputSource).getDefaultInput() : System.in;
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
		return AwkPrintf.toAwkString(o, this.convfmt, this.locale);
	}

	/**
	 * Compares two objects with this runtime's {@code IGNORECASE}, {@code CONVFMT} and locale.
	 * <p>
	 * Prefer this over the static {@code compare2} overloads whenever a runtime is available: it is
	 * the only form that honours a {@code CONVFMT} assigned by the script.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode the comparison mode, as in {@link #compare2(Object, Object, int)}
	 * @return a boolean
	 */
	public boolean compare(Object o1, Object o2, int mode) {
		return compare2(o1, o2, mode, isIgnoreCase(), this.convfmt, this.locale);
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

		// Convert the leading numeric prefix, as AWK does: "25fix" yields 25, and
		// text without a numeric prefix yields 0.
		String s = o.toString();
		int length = s.length();
		int start = 0;
		while (start < length && Character.isWhitespace(s.charAt(start))) {
			start++;
		}
		// AWK accepts an infinity or NaN only as a complete signed token, matched
		// without regard to case: "-inf" and "-INF" are -inf, while "-inform",
		// "-Infinity", and an unsigned "inf" are ordinary text.
		if (start < length) {
			char sign = s.charAt(start);
			if ((sign == '+' || sign == '-') && isBlankToEnd(s, start + 4)) {
				if (s.regionMatches(true, start + 1, "inf", 0, 3)) {
					return sign == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				if (s.regionMatches(true, start + 1, "nan", 0, 3)) {
					return Double.NaN;
				}
			}
		}
		int end = numericPrefixEnd(s, start, '.');
		if (end == start) {
			return 0;
		}
		// Copying is only needed when the number is a strict prefix of the text.
		return Double.parseDouble(start == 0 && end == length ? s : s.substring(start, end));
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

	/** 2^63 as a double: the first value beyond the signed 64-bit range. */
	private static final double TWO_POW_63 = 9.223372036854775808e18;

	/**
	 * Converts a computed double to the canonical AWK scalar: a {@link Long}
	 * when the value is integral and representable as a signed 64-bit integer,
	 * and the {@link Double} itself otherwise. Values beyond the 64-bit range
	 * stay doubles so they are not silently saturated to
	 * {@link Long#MAX_VALUE}.
	 *
	 * @param d the computed value
	 * @return {@code d} as a {@link Long} when exactly representable, or as a
	 *         {@link Double}
	 */
	public static Object toScalarNumber(double d) {
		if (isActuallyLong(d)) {
			double rounded = Math.rint(d);
			if (rounded >= -TWO_POW_63 && rounded < TWO_POW_63) {
				return Long.valueOf((long) rounded);
			}
		}
		return Double.valueOf(d);
	}

	/**
	 * Truncates a double toward zero, as AWK's {@code int()} does, returning a
	 * {@link Long} when the result is representable and a {@link Double}
	 * otherwise.
	 *
	 * @param d the value to truncate
	 * @return the truncated value as a canonical AWK scalar
	 */
	public static Object truncateToScalar(double d) {
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			return Double.valueOf(d);
		}
		return toScalarNumber(d < 0 ? Math.ceil(d) : Math.floor(d));
	}

	/**
	 * Returns whether a scalar is an exact 64-bit integer: a boxed
	 * {@link Long} or {@link Integer}, whose value is known without any
	 * floating-point rounding.
	 *
	 * @param o the scalar to examine
	 * @return {@code true} when {@code o} is a {@code Long} or an
	 *         {@code Integer}
	 */
	private static boolean isExactIntegral(Object o) {
		return o instanceof Long || o instanceof Integer;
	}

	/**
	 * Adds two AWK scalars. When both operands are exact 64-bit integers and
	 * the sum fits in 64 bits, the result stays an exact {@link Long};
	 * otherwise both operands are converted with {@link #toDouble(Object)}
	 * and the result is a {@link Double}.
	 *
	 * @param o1 the left operand
	 * @param o2 the right operand
	 * @return {@code o1 + o2} as a canonical AWK scalar
	 */
	public static Object add(Object o1, Object o2) {
		if (isExactIntegral(o1) && isExactIntegral(o2)) {
			try {
				return Math.addExact(((Number) o1).longValue(), ((Number) o2).longValue());
			} catch (ArithmeticException overflow) {
				return toDouble(o1) + toDouble(o2);
			}
		}
		return toDouble(o1) + toDouble(o2);
	}

	/**
	 * Subtracts two AWK scalars. When both operands are exact 64-bit integers
	 * and the difference fits in 64 bits, the result stays an exact
	 * {@link Long}; otherwise both operands are converted with
	 * {@link #toDouble(Object)} and the result is a {@link Double}.
	 *
	 * @param o1 the left operand
	 * @param o2 the right operand
	 * @return {@code o1 - o2} as a canonical AWK scalar
	 */
	public static Object subtract(Object o1, Object o2) {
		if (isExactIntegral(o1) && isExactIntegral(o2)) {
			try {
				return Math.subtractExact(((Number) o1).longValue(), ((Number) o2).longValue());
			} catch (ArithmeticException overflow) {
				return toDouble(o1) - toDouble(o2);
			}
		}
		return toDouble(o1) - toDouble(o2);
	}

	/**
	 * Multiplies two AWK scalars. When both operands are exact 64-bit
	 * integers and the product fits in 64 bits, the result stays an exact
	 * {@link Long}; otherwise both operands are converted with
	 * {@link #toDouble(Object)} and the result is a {@link Double}.
	 *
	 * @param o1 the left operand
	 * @param o2 the right operand
	 * @return {@code o1 * o2} as a canonical AWK scalar
	 */
	public static Object multiply(Object o1, Object o2) {
		if (isExactIntegral(o1) && isExactIntegral(o2)) {
			try {
				return Math.multiplyExact(((Number) o1).longValue(), ((Number) o2).longValue());
			} catch (ArithmeticException overflow) {
				return toDouble(o1) * toDouble(o2);
			}
		}
		return toDouble(o1) * toDouble(o2);
	}

	/**
	 * Divides two AWK scalars. When both operands are exact 64-bit integers
	 * and the quotient is a 64-bit integer with no remainder, the result
	 * stays an exact {@link Long}; every other case (a fractional quotient,
	 * a zero divisor, or {@code Long.MIN_VALUE / -1}) is computed in
	 * floating point, as before.
	 *
	 * @param o1 the dividend
	 * @param o2 the divisor
	 * @return {@code o1 / o2} as a canonical AWK scalar
	 */
	public static Object divide(Object o1, Object o2) {
		if (isExactIntegral(o1) && isExactIntegral(o2)) {
			long l1 = ((Number) o1).longValue();
			long l2 = ((Number) o2).longValue();
			if (l2 != 0 && l1 % l2 == 0 && (l1 != Long.MIN_VALUE || l2 != -1)) {
				return l1 / l2;
			}
		}
		return toDouble(o1) / toDouble(o2);
	}

	/**
	 * Computes the remainder of two AWK scalars. When both operands are exact
	 * 64-bit integers and the divisor is non-zero, the result stays an exact
	 * {@link Long}; otherwise the remainder is computed in floating point,
	 * so a zero divisor still yields {@code nan}.
	 *
	 * @param o1 the dividend
	 * @param o2 the divisor
	 * @return {@code o1 % o2} as a canonical AWK scalar
	 */
	public static Object mod(Object o1, Object o2) {
		if (isExactIntegral(o1) && isExactIntegral(o2)) {
			long l2 = ((Number) o2).longValue();
			if (l2 != 0) {
				return ((Number) o1).longValue() % l2;
			}
		}
		return toDouble(o1) % toDouble(o2);
	}

	/**
	 * Raises an AWK scalar to a power. Exponentiation is always computed in
	 * floating point, like gawk's {@code ^} operator.
	 *
	 * @param o1 the base
	 * @param o2 the exponent
	 * @return {@code o1 ^ o2} as a {@link Double}
	 */
	public static Object pow(Object o1, Object o2) {
		return Math.pow(toDouble(o1), toDouble(o2));
	}

	/**
	 * Negates an AWK scalar. An exact 64-bit integer stays an exact
	 * {@link Long} (except {@code Long.MIN_VALUE}, whose negation does not
	 * fit); everything else is converted with {@link #toDouble(Object)} and
	 * negated as a {@link Double}.
	 *
	 * @param o the scalar to negate
	 * @return {@code -o} as a canonical AWK scalar
	 */
	public static Object negate(Object o) {
		if (isExactIntegral(o)) {
			long l = ((Number) o).longValue();
			if (l != Long.MIN_VALUE) {
				return -l;
			}
		}
		return -toDouble(o);
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

		// Whole numbers, by far the most common case here, are accumulated
		// directly so that every value a long can hold converts exactly.
		// Anything else -- a fractional part, an exponent, or a value beyond
		// the 64-bit range -- goes through the same conversion as everywhere
		// else and truncates toward zero, so that "1e1" converts to 10 rather
		// than 1. Values beyond the 64-bit range saturate.
		String s = o.toString();
		int length = s.length();
		int index = 0;
		while (index < length && Character.isWhitespace(s.charAt(index))) {
			index++;
		}
		boolean negative = index < length && s.charAt(index) == '-';
		if (index < length && (negative || s.charAt(index) == '+')) {
			index++;
		}
		int firstDigit = index;
		long value = 0;
		while (index < length && isAsciiDigit(s.charAt(index))) {
			int digit = s.charAt(index) - '0';
			if (value > (Long.MAX_VALUE - digit) / 10) {
				// Stop before overflowing: the conversion below then takes over
				// and saturates, since a digit is necessarily left unconsumed.
				break;
			}
			value = value * 10 + digit;
			index++;
		}
		if (index > firstDigit && !continuesNumber(s, index)) {
			return negative ? -value : value;
		}
		return (long) toDouble(o);
	}

	/**
	 * Returns whether the text holds nothing but whitespace from {@code index}
	 * onwards, treating an index past the end as satisfied.
	 */
	private static boolean isBlankToEnd(String value, int index) {
		for (int i = index; i < value.length(); i++) {
			if (!Character.isWhitespace(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns whether the text at {@code index} extends a run of digits into a
	 * number that no longer converts to the same integer. An exponent marker
	 * only does so when digits actually follow it, matching the backtracking in
	 * {@link #numericPrefixEnd(String, int, char)}: the numeric prefix of
	 * {@code "12e"} is just {@code "12"}, which is exactly the integer already
	 * accumulated.
	 */
	private static boolean continuesNumber(String value, int index) {
		if (index >= value.length()) {
			return false;
		}
		char c = value.charAt(index);
		if (isAsciiDigit(c) || c == '.') {
			return true;
		}
		if (c != 'e' && c != 'E') {
			return false;
		}
		int afterExponent = index + 1;
		if (afterExponent < value.length()
				&& (value.charAt(afterExponent) == '+' || value.charAt(afterExponent) == '-')) {
			afterExponent++;
		}
		return afterExponent < value.length() && isAsciiDigit(value.charAt(afterExponent));
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
		// The default CONVFMT is the only one available here. It is also the only correct choice for
		// the caller this form exists for, AwkTuples' constant folding, which runs at compile time
		// when no runtime and therefore no script-assigned CONVFMT exists yet. Anything holding a
		// runtime must call compare(Object, Object, int) instead.
		return compare2(o1, o2, mode, ignoreCase, null, Locale.US);
	}

	/**
	 * Shared implementation, taking every property the comparison depends on explicitly.
	 * <p>
	 * A number compared against a string is a string comparison in AWK, and the number must be
	 * converted with the AWK number-to-string rule: a value exactly equal to an integer renders as
	 * that integer, anything else through {@code CONVFMT}. Using {@link Object#toString()} here would
	 * render {@code 291} as {@code "291.0"} and {@code 1.04152956928E11} as {@code "1.04152956928E11"},
	 * so a computed integer would stop comparing equal to its own digits.
	 * <p>
	 * Deliberately not public: {@link #compare(Object, Object, int)} is the form to use, and it reads
	 * these properties off the runtime. The static entry points remain only for the callers that have
	 * no runtime to read them from.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode the comparison mode, as in {@link #compare2(Object, Object, int)}
	 * @param ignoreCase whether string comparisons ignore case
	 * @param convfmt the {@code CONVFMT} to apply, or {@code null} for the default
	 * @param locale the locale used to format numbers
	 * @return a boolean
	 */
	private static boolean compare2(
			Object o1,
			Object o2,
			int mode,
			boolean ignoreCase,
			String convfmt,
			Locale locale) {
		if (o1 instanceof Number && o2 instanceof Number) {
			if (isExactIntegral(o1) && isExactIntegral(o2)) {
				// Compare exact 64-bit integers without the precision loss a
				// double conversion would introduce beyond 2^53.
				int comparison = Long.compare(((Number) o1).longValue(), ((Number) o2).longValue());
				if (mode == 0) {
					return comparison == 0;
				}
				return mode < 0 ? comparison < 0 : comparison > 0;
			}
			return compareNumbers(((Number) o1).doubleValue(), ((Number) o2).doubleValue(), mode);
		}

		if (o1 instanceof UninitializedObject) {
			if (isBlankOrZero(o2, convfmt, locale)) {
				return mode == 0;
			} else {
				return mode < 0;
			}
		}
		if (o2 instanceof UninitializedObject) {
			if (isBlankOrZero(o1, convfmt, locale)) {
				return mode == 0;
			} else {
				return mode > 0;
			}
		}

		if (isNumericComparisonOperand(o1) && isNumericComparisonOperand(o2)) {
			return compareNumbers(getDoubleForComparison(o1), getDoubleForComparison(o2), mode);
		}

		// Only a genuine string comparison converts, and only here. CONVFMT must not be evaluated for a
		// comparison that turns out to be numeric: besides the discarded string, a format such as
		// "%f%f" would otherwise fail on a comparison that has no string operand at all.
		String o1String = AwkPrintf.toAwkString(o1, convfmt, locale);
		String o2String = AwkPrintf.toAwkString(o2, convfmt, locale);

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

	/**
	 * Whether the value counts as blank or zero when compared against an uninitialized value.
	 * <p>
	 * The string form is produced only for a value that is neither uninitialized nor numeric, so a
	 * numeric operand never evaluates {@code CONVFMT} here.
	 *
	 * @param value the value to test
	 * @param convfmt the {@code CONVFMT} to apply, or {@code null} for the default
	 * @param locale the locale used to format numbers
	 * @return whether the value is blank or zero
	 */
	private static boolean isBlankOrZero(Object value, String convfmt, Locale locale) {
		if (value instanceof UninitializedObject) {
			return true;
		}
		if (value instanceof Number) {
			return ((Number) value).doubleValue() == 0.0D;
		}
		if (value instanceof StrNum && ((StrNum) value).isNumber()) {
			return ((StrNum) value).doubleValue() == 0.0D;
		}
		String stringValue = AwkPrintf.toAwkString(value, convfmt, locale);
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
			return toScalarNumber(((Number) value).doubleValue());
		}
		return value;
	}

	/**
	 * Returns whether the supplied text parses as an AWK number under this
	 * runtime's locale, as used for strnum recognition. As POSIX specifies for
	 * numeric strings, leading and trailing blanks around the number are
	 * ignored, but text that is nothing but blanks does not qualify.
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
		int length = value.length();
		int start = 0;
		while (start < length && Character.isWhitespace(value.charAt(start))) {
			start++;
		}
		int end = numericPrefixEnd(value, start, decimalSeparator);
		return end > start && isBlankToEnd(value, end);
	}

	/**
	 * Strips the leading and trailing whitespace that strnum recognition
	 * ignores, so a recognized numeric string can be handed to the Java
	 * numeric parsers, which do not accept every character
	 * {@link Character#isWhitespace(char)} does.
	 *
	 * @param value text to trim
	 * @return {@code value} without leading and trailing whitespace
	 */
	static String trimWhitespace(String value) {
		int end = value.length();
		int start = 0;
		while (start < end && Character.isWhitespace(value.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
			end--;
		}
		return start == 0 && end == value.length() ? value : value.substring(start, end);
	}

	/**
	 * Scans the AWK numeric constant that starts at {@code start} and returns the
	 * index just past it, or {@code start} when no number starts there. The
	 * grammar is an optional sign, decimal digits with an optional fractional
	 * part, and an optional exponent.
	 * <p>
	 * The scan is greedy but backtracks over an exponent marker that is not
	 * followed by digits, so {@code "1e"} and {@code "1e+"} yield the prefix
	 * {@code "1"} rather than failing, matching how AWK converts a string with a
	 * numeric prefix. The caller is responsible for skipping any leading
	 * whitespace it wants to allow.
	 * </p>
	 *
	 * @param value text to scan
	 * @param start index at which the number is expected to start
	 * @param decimalSeparator character separating the integral and fractional
	 *        parts under the active locale
	 * @return the index just past the numeric prefix, or {@code start} when
	 *         {@code value} has no numeric prefix at {@code start}
	 */
	private static int numericPrefixEnd(String value, int start, char decimalSeparator) {
		int length = value.length();
		int index = start;

		if (index < length && (value.charAt(index) == '+' || value.charAt(index) == '-')) {
			index++;
		}

		boolean digitFound = false;
		while (index < length && isAsciiDigit(value.charAt(index))) {
			index++;
			digitFound = true;
		}

		if (index < length && value.charAt(index) == decimalSeparator) {
			index++;
			while (index < length && isAsciiDigit(value.charAt(index))) {
				index++;
				digitFound = true;
			}
		}

		if (!digitFound) {
			return start;
		}

		if (index < length && (value.charAt(index) == 'e' || value.charAt(index) == 'E')) {
			int afterExponent = index + 1;
			if (afterExponent < length
					&& (value.charAt(afterExponent) == '+' || value.charAt(afterExponent) == '-')) {
				afterExponent++;
			}
			int exponentDigits = afterExponent;
			while (afterExponent < length && isAsciiDigit(value.charAt(afterExponent))) {
				afterExponent++;
			}
			// Keep the exponent only when it has at least one digit: "1e" is the
			// number 1 followed by text, not a malformed number.
			if (afterExponent > exponentDigits) {
				index = afterExponent;
			}
		}

		return index;
	}

	private static boolean isAsciiDigit(char c) {
		return c >= '0' && c <= '9';
	}

	static String normalizeNumberForComparison(String value, char decimalSeparator) {
		return decimalSeparator == '.' ? value : value.replace(decimalSeparator, '.');
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one plus a given object. An exact 64-bit integer stays an exact
	 * {@link Long} (unless the increment overflows). For other numbers and
	 * for Strings, the value is converted to a double first; a String
	 * without a numeric prefix counts as 0, so the result is 1.
	 *
	 * @param o The object to increase.
	 * @return {@code o + 1} if o is numeric or contains a numeric prefix;
	 *         otherwise, {@code 1.0}
	 */
	public static Object inc(Object o) {
		if (isExactIntegral(o)) {
			long l = ((Number) o).longValue();
			if (l != Long.MAX_VALUE) {
				return l + 1;
			}
		}
		return toDouble(o) + 1;
	}

	/**
	 * Return an object which is numerically equivalent to
	 * one minus a given object. An exact 64-bit integer stays an exact
	 * {@link Long} (unless the decrement overflows). For other numbers and
	 * for Strings, the value is converted to a double first; a String
	 * without a numeric prefix counts as 0, so the result is -1.
	 *
	 * @param o The object to increase.
	 * @return {@code o - 1} if o is numeric or contains a numeric prefix;
	 *         otherwise, {@code -1.0}
	 */
	public static Object dec(Object o) {
		if (isExactIntegral(o)) {
			long l = ((Number) o).longValue();
			if (l != Long.MIN_VALUE) {
				return l - 1;
			}
		}
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
	 * {@code &} becomes the whole match, {@code \&} a literal ampersand,
	 * {@code \\} a literal backslash, and {@code $} is escaped.
	 *
	 * @param awkRepl AWK replacement text
	 * @param backreferences whether {@code \N} denotes capture group {@code N}
	 *        and any other {@code \c} a plain {@code c}, as in gawk's
	 *        {@code gensub()}; when {@code false}, {@code \c} stays a literal
	 *        {@code \c} as in {@code sub()} and {@code gsub()}
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

			if (c == '\\') {
				if (i == awkRepl.length() - 1) {
					// a trailing backslash is a literal backslash; left bare
					// it would make Matcher.appendReplacement throw
					javaRepl.append("\\\\");
					continue;
				}
				i++;
				c = awkRepl.charAt(i);
				if (c == '&') {
					javaRepl.append('&');
					continue;
				} else if (c == '\\') {
					javaRepl.append("\\\\");
					continue;
				} else if (backreferences) {
					if (Character.isDigit(c)) {
						if (c - '0' <= maxGroup) {
							javaRepl.append('$').append(c);
						}
						// references beyond the pattern's groups expand to
						// the empty string, as in gawk
						continue;
					}
					// gensub(): any other \c is a plain c, as in gawk
				} else {
					// sub()/gsub(): any other \c stays a literal \c, as in
					// POSIX, gawk and mawk; a bare backslash would be eaten
					// by Matcher.appendReplacement as an escape
					javaRepl.append("\\\\");
				}
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
	 * Reports whether a record is currently loaded, and therefore whether the
	 * input fields hold anything.
	 *
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

	/**
	 * Rebuilds {@code $0} from the current field values, joining them with
	 * {@code OFS}, and caches the result as the current input line.
	 * <p>
	 * Does nothing when no record is loaded. Provided for subclasses that mutate
	 * the fields directly rather than through
	 * {@link #jrtSetInputField(Object, long)}, so that {@code $0} stays
	 * consistent with them.
	 * </p>
	 */
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
		// A field assigned a numeric value retains the number itself;
		// reconstituting $0 converts it with CONVFMT, as POSIX requires and
		// gawk does (a string or input-derived field joins verbatim).
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				sb.append(ofs);
			}
			Object field = fields.get(i);
			sb.append(field == null ? "" : toAwkString(field));
		}
		return sb.toString();
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
	 * Reads one record from a file for a redirected {@code getline},
	 * translating the outcome into the AWK-visible return code.
	 *
	 * @param fileNameParam name of the file to read from
	 * @return {@code 1} when a record was read (available through
	 *         {@link #jrtGetInputString()}), {@code 0} at end of input, and
	 *         {@code -1} when the file cannot be opened or read, in which case
	 *         ERRNO carries the gawk-style error description
	 * @throws AwkRuntimeException when the filename is the empty string, the
	 *         fatal error gawk raises for a null-string redirection
	 */
	public Integer jrtConsumeFileInputForGetline(String fileNameParam) {
		if (fileNameParam.isEmpty()) {
			throw new AwkRuntimeException("expression for `<' redirection has null string value");
		}
		try {
			if (jrtConsumeFileInput(fileNameParam)) {
				return ONE;
			}
			jrtInputString = "";
			return ZERO;
		} catch (IOException ioe) {
			jrtInputString = "";
			setERRNO(describeOpenFailure(fileNameParam, ioe));
			return MINUS_ONE;
		}
	}

	/**
	 * Reads one record from the output of a command for a redirected
	 * {@code getline}, translating the outcome into the AWK-visible return
	 * code.
	 *
	 * @param cmdString the command to execute
	 * @return {@code 1} when a record was read (available through
	 *         {@link #jrtGetInputString()}), {@code 0} at end of input, and
	 *         {@code -1} when the process cannot be spawned, in which case
	 *         ERRNO carries the error description
	 * @throws AwkRuntimeException when the command is the empty string, the
	 *         fatal error gawk raises for a null-string redirection
	 */
	public Integer jrtConsumeCommandInputForGetline(String cmdString) {
		if (cmdString.isEmpty()) {
			throw new AwkRuntimeException("expression for `|' redirection has null string value");
		}
		try {
			if (jrtConsumeCommandInput(cmdString)) {
				return ONE;
			}
			jrtInputString = "";
			return ZERO;
		} catch (IOException ioe) {
			jrtInputString = "";
			setERRNO(describeIoReason(ioe));
			return MINUS_ONE;
		}
	}

	/**
	 * Describes why a file could not be opened for reading, the way gawk
	 * reports it through ERRNO: the strerror-style reason alone, without the
	 * failing path that Java prefixes to its exception messages.
	 *
	 * @param fileNameParam the filename that failed to open
	 * @param ioe the failure raised by the open or read
	 * @return a gawk-style error description
	 */
	private static String describeOpenFailure(String fileNameParam, IOException ioe) {
		if (!isStandardInputName(fileNameParam) && !isNullDeviceName(fileNameParam)) {
			File file = new File(toPlatformFileName(fileNameParam));
			if (file.isDirectory()) {
				return "Is a directory";
			}
			if (!file.exists()) {
				return "No such file or directory";
			}
		}
		return describeIoReason(ioe);
	}

	/**
	 * Extracts the reason from an I/O exception message, the way gawk reports
	 * failures through ERRNO. Java prefixes the failing path to the reason,
	 * as in {@code path (reason)}; only the reason is kept.
	 *
	 * @param ioe the failure to describe
	 * @return the extracted reason, or "Permission denied" when the exception
	 *         carries no message
	 */
	static String describeIoReason(IOException ioe) {
		String message = ioe.getMessage();
		if (message == null || message.isEmpty()) {
			return "Permission denied";
		}
		int open = message.lastIndexOf('(');
		if (open >= 0 && message.endsWith(")")) {
			return message.substring(open + 1, message.length() - 1);
		}
		return message;
	}

	/**
	 * Retrieve the record last consumed by a redirected {@code getline}.
	 *
	 * @return the last record read by
	 *         {@link #jrtConsumeFileInputForGetline(String)} or
	 *         {@link #jrtConsumeCommandInputForGetline(String)}
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
	 * Returns whether the supplied name is the gawk special filename for the
	 * standard input of the process.
	 *
	 * @param fileNameParam name used in a redirection
	 * @return {@code true} for {@code /dev/stdin} and {@code /dev/fd/0}
	 */
	private static boolean isStandardInputName(String fileNameParam) {
		return DEV_STDIN.equals(fileNameParam) || DEV_FD_0.equals(fileNameParam);
	}

	/**
	 * Returns whether the supplied name is the gawk special filename for the
	 * standard output of the process.
	 *
	 * @param fileNameParam name used in a redirection
	 * @return {@code true} for {@code /dev/stdout} and {@code /dev/fd/1}
	 */
	private static boolean isStandardOutputName(String fileNameParam) {
		return DEV_STDOUT.equals(fileNameParam) || DEV_FD_1.equals(fileNameParam);
	}

	/**
	 * Returns whether the supplied name is the gawk special filename for the
	 * standard error of the process.
	 *
	 * @param fileNameParam name used in a redirection
	 * @return {@code true} for {@code /dev/stderr} and {@code /dev/fd/2}
	 */
	private static boolean isStandardErrorName(String fileNameParam) {
		return DEV_STDERR.equals(fileNameParam) || DEV_FD_2.equals(fileNameParam);
	}

	/**
	 * Returns whether the supplied name designates the null device, which reads
	 * as an empty file and discards everything written to it. Both spellings the
	 * platform answers to are recognized: {@code /dev/null} everywhere, and the
	 * native {@code NUL} on Windows, where the file system opens that name as the
	 * device already.
	 * <p>
	 * This is for callers that inspect a filename before opening it, which must
	 * recognize the name instead of relying on the file system, because Windows
	 * does not report its null device as an existing file. Translating a name for
	 * the platform is a narrower question, answered by
	 * {@link #toPlatformFileName(String)}.
	 * </p>
	 *
	 * @param fileNameParam name used in a redirection, in {@code getline} or in
	 *        the {@code ARGV} file list
	 * @return {@code true} when the name designates the null device
	 */
	static boolean isNullDeviceName(String fileNameParam) {
		return DEV_NULL.equals(fileNameParam)
				|| (IS_WINDOWS && WINDOWS_NULL_DEVICE.equalsIgnoreCase(fileNameParam));
	}

	/**
	 * Maps a script-supplied filename to the name the platform opens it under.
	 * Only the null device is translated, and only on Windows: portable AWK
	 * scripts discard output by redirecting to {@code /dev/null}, which is a real
	 * device on every Unix system but a plain relative path on Windows, where
	 * leaving it untranslated creates and truncates a {@code dev\null} file, or
	 * fails outright when no {@code dev} directory exists. gawk's Windows port
	 * performs the same translation, and, as in gawk, the native {@code NUL}
	 * needs none: Windows opens that name as the device itself.
	 * <p>
	 * Redirections stay keyed by the name the script used, so {@code close()}
	 * takes the original spelling.
	 * </p>
	 *
	 * @param fileNameParam name used in a redirection, in {@code getline} or in
	 *        the {@code ARGV} file list
	 * @return the name to open, which differs from the supplied one only for
	 *         {@code /dev/null} on Windows
	 */
	static String toPlatformFileName(String fileNameParam) {
		return IS_WINDOWS && DEV_NULL.equals(fileNameParam) ? WINDOWS_NULL_DEVICE : fileNameParam;
	}

	/**
	 * Returns the sink writing to the standard error of the process, creating it
	 * on first use. Every write is flushed so that the records a script sends to
	 * {@code /dev/stderr} interleave with the diagnostics the runtime itself
	 * writes to the same stream.
	 *
	 * @return the {@code /dev/stderr} sink
	 */
	private AwkSink getStandardErrorSink() {
		if (standardErrorSink == null) {
			standardErrorSink = new FlushingAwkSink(warning, locale);
		}
		return standardErrorSink;
	}

	/**
	 * Resolves the sink used by file redirection. The gawk special filenames
	 * {@code /dev/stdout} and {@code /dev/stderr} (and their {@code /dev/fd/1}
	 * and {@code /dev/fd/2} spellings) are routed to the streams the process
	 * already holds open instead of being opened, and therefore truncated, as
	 * regular files, and {@code /dev/null} designates the platform's null device
	 * on Windows too.
	 *
	 * @param fileNameParam target file name
	 * @param append whether output should be appended
	 * @return the sink that writes to the requested file
	 */
	protected AwkSink getFileAwkSink(String fileNameParam, boolean append) {
		if (isStandardOutputName(fileNameParam)) {
			return openSpecialOutput(fileNameParam, awkSink);
		}
		if (isStandardErrorName(fileNameParam)) {
			return openSpecialOutput(fileNameParam, getStandardErrorSink());
		}
		return getOrCreateFileOutputState(fileNameParam, append).sink;
	}

	/**
	 * Records that a redirection is open on a standard output special filename,
	 * so that {@code close()} has something to report and to flush.
	 *
	 * @param fileNameParam the special filename being redirected to
	 * @param sink the sink that receives the redirected output
	 * @return the supplied sink
	 */
	private AwkSink openSpecialOutput(String fileNameParam, AwkSink sink) {
		getIoState().specialOutputs.put(fileNameParam, sink);
		return sink;
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
		awkSink.printf(ofs, ors, ofmt, convfmt, format, values);
	}

	/**
	 * Formats a string in the same way as AWK's {@code sprintf()} built-in,
	 * through the default output sink and with the current {@code CONVFMT}
	 * value.
	 *
	 * @param format format string passed to {@code sprintf}
	 * @param values arguments supplied after the format string
	 * @return formatted text
	 */
	public String sprintf(String format, Object... values) {
		return awkSink.sprintf(convfmt, format, values);
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
		sink.printf(ofs, ors, ofmt, convfmt, format, values);
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
		sink.printf(ofs, ors, ofmt, convfmt, format, values);
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
	 * Reads one record from a file opened by a redirected {@code getline}.
	 * <p>
	 * The reader is opened on first use and kept until it is explicitly closed
	 * or the VM exits. Unlike the main input loop, this transport leaves the
	 * current record ({@code $0} and its fields), NR, FNR, and FILENAME
	 * untouched: gawk documents {@code getline [var] < file} as setting only
	 * the target of the read. The consumed record is exposed through
	 * {@link #jrtGetInputString()}.
	 * </p>
	 * <p>
	 * The gawk special filename {@code /dev/stdin} (and its {@code /dev/fd/0}
	 * spelling) reads the standard input of the process rather than a file of
	 * that name, and {@code /dev/null} reads the platform's null device, which
	 * reports end of input immediately on Windows too.
	 * </p>
	 *
	 * @param fileNameParam name of the file to read from
	 * @return {@code true} when a record was read; {@code false} at end of
	 *         input
	 * @throws java.io.IOException if the file cannot be opened or read; a
	 *         failed open is not cached, so a later {@code getline} from the
	 *         same name retries it
	 */
	public boolean jrtConsumeFileInput(String fileNameParam) throws IOException {
		Map<String, PartitioningReader> fileReaders = getIoState().fileReaders;
		PartitioningReader pr = fileReaders.get(fileNameParam);
		if (pr == null) {
			InputStream inputStream = isStandardInputName(fileNameParam) ?
					standardInput : new FileInputStream(toPlatformFileName(fileNameParam));
			pr = new PartitioningReader(
					new InputStreamReader(inputStream, StandardCharsets.UTF_8),
					this.rs);
			fileReaders.put(fileNameParam, pr);
		}

		String recordText = pr.readRecord();
		if (recordText == null) {
			return false;
		}
		jrtInputString = recordText;
		return true;
	}

	private static Process spawnProcess(String cmd, boolean inheritStandardInput) throws IOException {
		ProcessBuilder pb = IS_WINDOWS
		// spawn the process using the Windows shell
				? new ProcessBuilder("cmd.exe", "/c", cmd)
				// spawn the process using the default POSIX shell
				: new ProcessBuilder("/bin/sh", "-c", cmd);
		if (inheritStandardInput) {
			pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
		}
		return pb.start();
	}

	/**
	 * Declares whether processes spawned on behalf of the script share the
	 * standard input of this JVM. POSIX gives the children of {@code system()}
	 * and of a command pipe the same standard input as awk itself, which is how
	 * terminal-aware commands like {@code "stty size" | getline} find the
	 * controlling terminal. That is only faithful when Jawk reads the real
	 * standard input of the process, which no capture of {@code System.in} can
	 * establish — an embedder may have replaced the stream with
	 * {@code System.setIn} at any point, including before this class
	 * initializes — so eligibility is asserted explicitly by the one caller
	 * that can vouch for it: the command-line entry point of the process.
	 * Everywhere else the flag stays {@code false} and the child's standard
	 * input is closed, since a Java stream cannot be lent to another OS
	 * process, and exposing the host JVM's real descriptor 0 instead would
	 * leak input the embedder never gave to Jawk.
	 *
	 * @param inherit {@code true} when the standard input this run reads is
	 *        the standard input of the JVM process itself
	 */
	public void setSpawnedProcessesInheritStandardInput(boolean inherit) {
		this.spawnedProcessesInheritStandardInput = inherit;
	}

	/**
	 * Tells whether processes spawned on behalf of the script share the
	 * standard input of this JVM, as declared through
	 * {@link #setSpawnedProcessesInheritStandardInput(boolean)}.
	 *
	 * @return {@code true} when spawned processes inherit the JVM's standard
	 *         input
	 */
	private boolean spawnedProcessInheritsStandardInput() {
		return spawnedProcessesInheritStandardInput;
	}

	/**
	 * Reads one record from the output of a command spawned by a redirected
	 * {@code getline}.
	 * <p>
	 * The process is spawned on first use and kept until the pipe is
	 * explicitly closed or the VM exits. As with file redirection, the current
	 * record ({@code $0} and its fields), NR, FNR, and FILENAME are left
	 * untouched: gawk documents {@code cmd | getline [var]} as setting only
	 * the target of the read. The consumed record is exposed through
	 * {@link #jrtGetInputString()}.
	 * </p>
	 *
	 * @param cmd the command to execute
	 * @return {@code true} when a record was read; {@code false} at end of
	 *         input
	 * @throws java.io.IOException if the process cannot be spawned; a failed
	 *         spawn is not cached, so a later {@code getline} from the same
	 *         command retries it
	 */
	public boolean jrtConsumeCommandInput(String cmd) throws IOException {
		CommandInputState commandInput = getOrCreateCommandInputState(cmd);
		String recordText = commandInput.reader.readRecord();
		if (recordText == null) {
			return false;
		}
		jrtInputString = recordText;
		return true;
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
					new FileOutputStream(toPlatformFileName(fileNameParam), append),
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
		}
		return commandInput;
	}

	private CommandInputState createCommandInputState(String cmd) throws IOException {
		Process process = null;
		Thread errorPump = null;
		try {
			// POSIX: the child shares awk's standard input; when that is not
			// possible (embedded execution on a custom stream) it stays closed
			boolean inheritStandardInput = spawnedProcessInheritsStandardInput();
			process = spawnProcess(cmd, inheritStandardInput);
			if (!inheritStandardInput) {
				process.getOutputStream().close();
			}
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
			// the pipe itself is the child's standard input
			process = spawnProcess(cmd, false);
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
		boolean b5 = jrtCloseSpecialOutput(fileNameParam);
		// either close will do
		return (b1 || b2 || b3 || b4 || b5) ? ZERO : MINUS_ONE;
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
		for (String s : state.specialOutputs.keySet()) {
			set.add(s);
		}
		for (String s : set) {
			jrtClose(s);
		}
	}

	/**
	 * Closes a redirection open on one of the standard output special filenames.
	 * The sink is flushed but the stream it writes to is left open: it belongs to
	 * the process, is shared with the runtime's own diagnostics and with the host
	 * application, and gawk likewise closes only its private duplicate of the
	 * descriptor. Redirecting to the same name again therefore works, exactly as
	 * it does in gawk.
	 *
	 * @param fileNameParam the filename passed to {@code close()}
	 * @return {@code true} when a redirection was open on that name and its sink
	 *         was flushed successfully
	 */
	private boolean jrtCloseSpecialOutput(String fileNameParam) {
		IoState state = ioState;
		if (state == null) {
			return false;
		}
		AwkSink sink = state.specialOutputs.remove(fileNameParam);
		if (sink == null) {
			return false;
		}
		try {
			sink.flush();
			return true;
		} catch (IOException ioe) {
			setERRNO(ioe.toString());
			return false;
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
		if (isStandardInputName(fileNameParam)) {
			// The standard input of the process is shared with the main input
			// loop and with the host application: drop the record reader but
			// never close the stream behind it. A later getline from the same
			// name reads on from whatever the stream still holds, as it does in
			// gawk, which closes only its private duplicate of the descriptor.
			return true;
		}
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
	 * The command inherits the standard input of the JVM when Jawk reads the
	 * real standard input (CLI runs), as POSIX requires of {@code system()};
	 * otherwise its standard input is closed. Threads are created to shuttle
	 * stdout and stderr of the command to stdout/stderr of the calling
	 * process.
	 *
	 * @param cmd The command to execute.
	 * @return Integer(return_code) of the created
	 *         process. Integer(-1) is returned on an IO error.
	 */
	public Integer jrtSystem(String cmd) {
		try {
			PrintStream processOutput = awkSink.getPrintStream();
			// POSIX: the child shares awk's standard input; when that is not
			// possible (embedded execution on a custom stream) it stays closed
			boolean inheritStandardInput = spawnedProcessInheritsStandardInput();
			Process p = spawnProcess(cmd, inheritStandardInput);
			if (!inheritStandardInput) {
				p.getOutputStream().close();
			}
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
