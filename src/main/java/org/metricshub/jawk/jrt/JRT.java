package org.metricshub.jawk.jrt;

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

// There must be NO imports to org.metricshub.jawk.*,
// other than org.metricshub.jawk.jrt which occurs by
// default. We wish to house all
// required runtime classes in jrt.jar,
// not have to refer to jawk.jar!

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import org.metricshub.jawk.intermediate.PositionTracker;
import org.metricshub.jawk.intermediate.UninitializedObject;
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
 * the resultant script class is not in the <code>org.metricshub.jawk.jrt</code> package
 * by default, and the user may reassign the resultant script class
 * to another package. Therefore, all accessed methods are public.
 *
 * @see VariableManager
 * @author Danny Daglas
 */
public class JRT {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").indexOf("Windows") >= 0;

	private VariableManager vm;

	private Map<String, Process> outputProcesses = new HashMap<String, Process>();
	private Map<String, PrintStream> outputStreams = new HashMap<String, PrintStream>();
	/** PrintStream used for command output */
	private PrintStream output = System.out;
	/** PrintStream used for command error output */
	private PrintStream error = System.err;

	// Partitioning reader for stdin.
	private PartitioningReader partitioningReader = null;
	// Current input line ($0).
	private String inputLine = null;
	// Current input fields ($0, $1, $2, ...).
	private List<String> inputFields = new ArrayList<String>(100);
	private AssocArray arglistAa = null;
	private int arglistIdx;
	private int arglistLength;
	private boolean hasFilenames = false;
	private static final UninitializedObject BLANK = new UninitializedObject();

	private static final Integer ONE = Integer.valueOf(1);
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer MINUS_ONE = Integer.valueOf(-1);
	private String jrtInputString;

	private Map<String, PartitioningReader> fileReaders = new HashMap<String, PartitioningReader>();
	private Map<String, PartitioningReader> commandReaders = new HashMap<String, PartitioningReader>();
	private Map<String, Process> commandProcesses = new HashMap<String, Process>();
	private Map<String, Thread> commandErrorPumps = new HashMap<String, Thread>();
	private Map<String, PrintStream> outputFiles = new HashMap<String, PrintStream>();
	private Map<String, Thread> outputStdoutPumps = new HashMap<String, Thread>();
	private Map<String, Thread> outputStderrPumps = new HashMap<String, Thread>();

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
	private Locale locale; // locale for number formatting

	/**
	 * Create a JRT with a VariableManager
	 *
	 * @param vm The VariableManager to use with this JRT.
	 */
	public JRT(VariableManager vm) {
		this(vm, Locale.getDefault());
	}

	/**
	 * Create a JRT with a VariableManager and a Locale
	 *
	 * @param vm The VariableManager to use with this JRT.
	 * @param locale The Locale to use for number formatting.
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "JRT must hold the provided VariableManager for later use")
	public JRT(VariableManager vm, Locale locale) {
		this.vm = vm;
		this.locale = locale;
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
	 * Sets the streams for spawned command output and error.
	 *
	 * @param ps PrintStream to send command output to
	 * @param err PrintStream to send command error output to
	 */
	public void setStreams(PrintStream ps, PrintStream err) {
		output = ps == null ? System.out : ps;
		error = err == null ? System.err : err;
	}

	/**
	 * Assign all -v variables.
	 *
	 * @param initialVarMap A map containing all initial variable
	 *        names and their values.
	 */
	public final void assignInitialVariables(Map<String, Object> initialVarMap) {
		assert initialVarMap != null;
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
	 * Called by AVM/compiled modules to assign local
	 * environment variables to an associative array
	 * (in this case, to ENVIRON).
	 *
	 * @param aa The associative array to populate with
	 *        environment variables. The module asserts that
	 *        the associative array is empty prior to population.
	 */
	public static void assignEnvironmentVariables(AssocArray aa) {
		assert aa.keySet().isEmpty();
		Map<String, String> env = System.getenv();
		for (Map.Entry<String, String> var : env.entrySet()) {
			aa.put(var.getKey(), var.getValue());
		}
	}

	/**
	 * Convert Strings, Integers, and Doubles to Strings
	 * based on the CONVFMT variable contents and the stored Locale.
	 *
	 * @param o Object to convert.
	 * @return A String representation of o.
	 */
	public String toAwkString(Object o) {
		return toAwkString(o, this.convfmt, this.locale);
	}

	/**
	 * Convert Strings, Integers, and Doubles to Strings
	 * based on the CONVFMT variable contents.
	 *
	 * @param o Object to convert.
	 * @param convfmt The contents of the CONVFMT variable.
	 * @return A String representation of o.
	 * @param locale a {@link java.util.Locale} object
	 */
	private static String toAwkString(Object o, String convfmt, Locale locale) {
		if (o == null) {
			return "";
		}
		if (o instanceof Number) {
			// It is a number, some processing is required here
			double d = ((Number) o).doubleValue();
			if (isActuallyLong(d)) {
				// If an integer, represent it as an integer (no floating point and decimals)
				return Long.toString((long) Math.rint(d));
			} else {
				// It's not a integer, represent it with the specified format
				try {
					String s = String.format(locale, convfmt, d);
					// Surprisingly, while %.6g is the official representation of numbers in AWK
					// which should include trailing zeroes, AWK seems to trim them. So, we will
					// do the same: trim the trailing zeroes
					if ((s.indexOf('.') > -1 || s.indexOf(',') > -1) && (s.indexOf('e') + s.indexOf('E') == -2)) {
						while (s.endsWith("0")) {
							s = s.substring(0, s.length() - 1);
						}
						if (s.endsWith(".") || s.endsWith(",")) {
							s = s.substring(0, s.length() - 1);
						}
					}
					return s;
				} catch (java.util.UnknownFormatConversionException ufce) {
					// Impossible case
					return "";
				}
			}
		} else {
			// It's not a number, easy
			return o.toString();
		}
	}

	/**
	 * Convert a String, Integer, or Double to String
	 * based on the OFMT variable contents. Jawk will
	 * subsequently use this String for output via print().
	 *
	 * @param o Object to convert.
	 * @return A String representation of o.
	 */
	public String toAwkStringForOutput(Object o) {
		// Even if specified Object o is not officially a number, we try to convert
		// it to a Double. Because if it's a literal representation of a number,
		// we will need to display it as a number ("12.00" --> 12)
		Object val = o;
		if (!(val instanceof Number)) {
			try {
				val = new BigDecimal(val.toString()).doubleValue();
			} catch (NumberFormatException e) {// NOPMD - EmptyCatchBlock: intentionally ignored
			}
		}

		return toAwkString(val, this.ofmt, this.locale);
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
	 * @param position the position tracker pointing at the tuple being
	 *        evaluated, used for error reporting
	 * @return the parsed field number as a long
	 */
	public static long parseFieldNumber(Object obj, PositionTracker position) {
		long num = toLong(obj);
		if (num < 0) {
			throw new AwkRuntimeException(
					position.lineNumber(),
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
		assert o != null;
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
			String s = inputLine == null ? "" : inputLine;
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
		return splitWorker(new StringTokenizer(toAwkString(string)), (AssocArray) array);
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
			return splitWorker(new StringTokenizer(toAwkString(string)), (AssocArray) array);
		} else if (fsString.equals("")) {
			return splitWorker(new CharacterTokenizer(toAwkString(string)), (AssocArray) array);
		} else if (fsString.length() == 1) {
			return splitWorker(
					new SingleCharacterTokenizer(toAwkString(string), fsString.charAt(0)),
					(AssocArray) array);
		} else {
			return splitWorker(new RegexTokenizer(toAwkString(string), fsString), (AssocArray) array);
		}
	}

	private static int splitWorker(Enumeration<Object> e, AssocArray aa) {
		int cnt = 0;
		aa.clear();
		while (e.hasMoreElements()) {
			aa.put(++cnt, e.nextElement());
		}
		aa.put(0L, Integer.valueOf(cnt));
		return cnt;
	}

	/**
	 * <p>
	 * Getter for the field <code>partitioningReader</code>.
	 * </p>
	 *
	 * @return a {@link org.metricshub.jawk.jrt.PartitioningReader} object
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "PartitioningReader is shared across callers")
	public PartitioningReader getPartitioningReader() {
		return partitioningReader;
	}

	/**
	 * <p>
	 * Getter for the field <code>inputLine</code>.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getInputLine() {
		return inputLine;
	}

	/**
	 * Retrieve the current value of NF. When fields are initialized this returns
	 * the number of fields in $0; otherwise 0.
	 *
	 * @return current NF value
	 */
	public Integer getNF() {
		int size = inputFields.size();
		return Integer.valueOf(size == 0 ? 0 : size - 1);
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
		return Long.valueOf(vm.getARGC());
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
	}

	/**
	 * Attempt to consume one line of input. Input may come from standard input or
	 * from files/variable assignments supplied on the command line via
	 * {@code ARGV}. Variable assignment arguments are evaluated lazily when
	 * encountered.
	 *
	 * @param input stream used when consuming from standard input
	 * @param forGetline {@code true} if the call is for {@code getline}; when
	 *        {@code false} the fields of {@code $0} are parsed
	 *        automatically
	 * @param pLocale locale used for string conversion
	 * @return {@code true} if a line was consumed, {@code false} if no more input
	 *         is available
	 * @throws IOException upon an IO error
	 */
	@SuppressWarnings("PMD.UnusedFormalParameter")
	public boolean consumeInput(final InputStream input, boolean forGetline, Locale pLocale) throws IOException {
		initializeArgList();

		while (true) {
			if ((partitioningReader == null || inputLine == null)
					&& !prepareNextReader(input)) {
				return false;
			}

			inputLine = partitioningReader.readRecord();
			if (inputLine == null) {
				continue;
			}

			if (!forGetline) {
				// For getline the caller will re-acquire $0; otherwise parse fields
				jrtParseFields();
			}
			// NR is managed by JRT
			this.nr++;
			if (partitioningReader.fromFilenameList()) {
				// FNR is managed by JRT
				this.fnr++;
			}
			return true; // NOPMD - loop ends when a line is consumed
		}
	}

	/**
	 * Initialize internal state for traversing {@code ARGV}.
	 */
	private void initializeArgList() {
		if (arglistAa != null) {
			return;
		}
		refreshArgListState();
		arglistIdx = 1;
		hasFilenames = detectFilenames();
	}

	private void refreshArgListState() {
		arglistAa = new AssocArray(false);
		String[] argv = vm.getARGV();
		arglistLength = argv.length;
		for (int i = 0; i < argv.length; i++) {
			String value = argv[i];
			if (value != null) {
				arglistAa.put(i, value);
			}
		}
	}

	/**
	 * Determine whether {@code ARGV} contains any filename entries (arguments
	 * without an equals sign).
	 *
	 * @return {@code true} if at least one filename was found
	 */
	private boolean detectFilenames() {
		int traversalArgCount = getTraversalArgCount();
		for (int i = 1; i < traversalArgCount; i++) {
			if (arglistAa.isIn(i)) {
				String arg = toAwkString(arglistAa.get(i));
				if (arg.isEmpty() || arg.indexOf('=') != -1) {
					continue;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieve the number of command-line arguments supplied to the script.
	 *
	 * @return {@code ARGC} converted to an {@code int}
	 */
	private int getArgCount() {
		return vm.getARGC();
	}

	private int getTraversalArgCount() {
		int argCount = getArgCount();
		if (argCount <= 0) {
			return 0;
		}
		return Math.min(argCount, arglistLength);
	}

	/**
	 * Obtain the next valid argument from {@code ARGV}, skipping uninitialized or
	 * empty entries.
	 *
	 * @return the next argument as an AWK string, or {@code null} if none remain
	 */
	private String nextArgument() {
		int traversalArgCount = getTraversalArgCount();
		while (arglistIdx < traversalArgCount) {
			int idx = arglistIdx++;
			if (!arglistAa.isIn(idx)) {
				continue;
			}
			String arg = toAwkString(arglistAa.get(idx));
			if (!arg.isEmpty()) {
				return arg;
			}
		}
		return null;
	}

	/**
	 * Prepare the {@link PartitioningReader} for the next input source. This may
	 * be a filename, a variable assignment, or standard input if no filenames
	 * remain.
	 *
	 * @param input default input stream used when reading from standard input
	 * @return {@code true} if a reader was prepared, {@code false} if no more
	 *         input is available
	 * @throws IOException if an I/O error occurs while opening a file
	 */
	private boolean prepareNextReader(InputStream input) throws IOException {
		boolean ready = false;
		refreshArgListState();
		hasFilenames = detectFilenames();
		while (!ready) {
			String arg = nextArgument();
			if (arg == null) {
				// ARGC/ARGV may have changed while evaluating assignments.
				hasFilenames = detectFilenames();
				if (partitioningReader == null && !hasFilenames) {
					partitioningReader = new PartitioningReader(
							new InputStreamReader(input, StandardCharsets.UTF_8),
							this.rs);
					this.filename = "";
					return true;
				}
				return false;
			}
			if (arg.indexOf('=') != -1) {
				setFilelistVariable(arg);
				// Rebuild from VM so ARGC/ARGV updates are reflected immediately.
				refreshArgListState();
				hasFilenames = detectFilenames();
				if (partitioningReader == null && !hasFilenames) {
					partitioningReader = new PartitioningReader(
							new InputStreamReader(input, StandardCharsets.UTF_8),
							this.rs);
					this.filename = "";
					return true;
				}
				if (partitioningReader != null) {
					this.nr++;
				}
			} else {
				partitioningReader = new PartitioningReader(
						new InputStreamReader(new FileInputStream(arg), StandardCharsets.UTF_8),
						this.rs,
						true);
				this.filename = arg;
				this.fnr = 0L;
				ready = true;
			}
		}
		return true;
	}

	/**
	 * Read input from stdin, only once, and just for simple AWK expression evaluation
	 * <p>
	 *
	 * @param input Stdin
	 * @throws IOException if couldn't read stdin (should never happen, as it's based on a String)
	 */
	public void setInputLineforEval(InputStream input) throws IOException {
		partitioningReader = new PartitioningReader(
				new InputStreamReader(input, StandardCharsets.UTF_8),
				this.rs);
		inputLine = partitioningReader.readRecord();
		if (inputLine != null) {
			jrtParseFields();
			this.nr++;
		}
	}

	/**
	 * Parse a {@code name=value} argument from the command line and assign it to
	 * the corresponding AWK variable.
	 *
	 * @param nameValue argument in the form {@code name=value}
	 */
	private void setFilelistVariable(String nameValue) {
		int eqIdx = nameValue.indexOf('=');
		// variable name should be non-blank
		assert eqIdx >= 0;
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
		vm.assignVariable(name, obj);
	}

	/**
	 * Splits $0 into $1, $2, etc.
	 * Called when an update to $0 has occurred.
	 */
	public void jrtParseFields() {
		String fsString = this.fs;
		assert inputLine != null;

		inputFields.clear();
		inputFields.add(inputLine); // $0

		if (!inputLine.isEmpty()) {
			Enumeration<Object> tokenizer;
			if (fsString.equals(" ")) {
				tokenizer = new StringTokenizer(inputLine);
			} else if (fsString.length() == 1) {
				tokenizer = new SingleCharacterTokenizer(inputLine, fsString.charAt(0));
			} else if (fsString.equals("")) {
				tokenizer = new CharacterTokenizer(inputLine);
			} else {
				tokenizer = new RegexTokenizer(inputLine, fsString);
			}

			while (tokenizer.hasMoreElements()) {
				inputFields.add((String) tokenizer.nextElement());
			}
		}

		// recalc NF
		recalculateNF();
	}

	private void recalculateNF() {
		// NF is managed internally by JRT; parser reads via PUSH_NF
	}

	/**
	 * @return true if at least one input field has been initialized.
	 */
	public boolean hasInputFields() {
		return !inputFields.isEmpty();
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

		int currentNF = inputFields.size() - 1;

		if (nf < currentNF) {
			for (int i = currentNF; i > nf; i--) {
				inputFields.remove(i);
			}
		} else if (nf > currentNF) {
			for (int i = currentNF + 1; i <= nf; i++) {
				inputFields.add("");
			}
		}

		rebuildDollarZeroFromFields();
	}

	/**
	 * Retrieve the contents of a particular input field.
	 *
	 * @param fieldnumObj Object referring to the field number.
	 * @param position the current tuple position, used for error reporting
	 * @return Contents of the field.
	 */
	public Object jrtGetInputField(Object fieldnumObj, PositionTracker position) {
		return jrtGetInputField(parseFieldNumber(fieldnumObj, position), position);
	}

	/**
	 * <p>
	 * jrtGetInputField.
	 * </p>
	 *
	 * @param fieldnum a long
	 * @param position the current tuple position, used for error reporting
	 * @return a {@link java.lang.Object} object
	 */
	public Object jrtGetInputField(long fieldnum, PositionTracker position) {
		if (fieldnum < 0 || fieldnum > Integer.MAX_VALUE) {
			Object descriptor = Long.valueOf(fieldnum);
			String message = "Field $(" + descriptor.toString() + ") is incorrect.";
			if (position == null) {
				throw new RuntimeException(message);
			}
			throw new AwkRuntimeException(position.lineNumber(), message);
		}
		int fieldIndex = (int) fieldnum;
		if (fieldIndex < inputFields.size()) {
			String retval = inputFields.get(fieldIndex);
			assert retval != null;
			return retval;
		}
		return BLANK;
	}

	public Object jrtGetInputField(long fieldnum) {
		return jrtGetInputField(fieldnum, null);
	}

	/**
	 * Stores value_obj into an input field.
	 *
	 * @param valueObj The RHS of the assignment.
	 * @param fieldNum field number to update.
	 * @return A string representation of valueObj.
	 */
	public String jrtSetInputField(Object valueObj, long fieldNum) {
		return jrtSetInputField(valueObj, fieldNum, null);
	}

	public String jrtSetInputField(Object valueObj, long fieldNum, PositionTracker position) {
		assert fieldNum >= 1;
		assert valueObj != null;
		if (fieldNum > Integer.MAX_VALUE) {
			String message = "Field $(" + Long.valueOf(fieldNum) + ") is incorrect.";
			if (position == null) {
				throw new RuntimeException(message);
			}
			throw new AwkRuntimeException(position.lineNumber(), message);
		}
		String value = valueObj.toString();
		int fieldIndex = (int) fieldNum;
// if the value is BLANK
		if (valueObj instanceof UninitializedObject) {
			if (fieldIndex < inputFields.size()) {
				inputFields.set(fieldIndex, "");
			}
		} else {
// append the list to accommodate the new value
			for (int i = inputFields.size() - 1; i < fieldIndex; i++) {
				inputFields.add("");
			}
			inputFields.set(fieldIndex, value);
		}
// rebuild $0
		rebuildDollarZeroFromFields();
		// recalc NF
		recalculateNF();
		return value;
	}

	private void rebuildDollarZeroFromFields() {
		StringBuilder newDollarZeroSb = new StringBuilder();
		String ofsValue = this.ofs;
		for (int i = 1; i < inputFields.size(); i++) {
			if (i > 1) {
				newDollarZeroSb.append(ofsValue);
			}
			newDollarZeroSb.append(inputFields.get(i));
		}
		inputFields.set(0, newDollarZeroSb.toString());
	}

	/**
	 * <p>
	 * jrtConsumeFileInputForGetline.
	 * </p>
	 *
	 * @param filename a {@link java.lang.String} object
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
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Callers modify the map of output files directly")
	public Map<String, PrintStream> getOutputFiles() {
		return outputFiles;
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
		PrintStream ps = outputFiles.get(fileNameParam);
		if (ps == null) {
			try {
				ps = new PrintStream(new FileOutputStream(fileNameParam, append), true, StandardCharsets.UTF_8.name()); // true
				// =
				// autoflush
				outputFiles.put(fileNameParam, ps);
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Cannot open " + fileNameParam + " for writing: " + ioe);
			}
		}
		assert ps != null;
		return ps;
	}

	/**
	 * <p>
	 * jrtConsumeFileInput.
	 * </p>
	 *
	 * @param filename a {@link java.lang.String} object
	 * @return a boolean
	 * @throws java.io.IOException if any.
	 */
	public boolean jrtConsumeFileInput(String fileNameParam) throws IOException {
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
		PartitioningReader pr = commandReaders.get(cmd);
		if (pr == null) {
			try {
				Process p = spawnProcess(cmd);
				// no input to this process!
				p.getOutputStream().close();
				Thread errorPump = DataPump.dumpAndReturnThread(cmd + " stderr", p.getErrorStream(), System.err);
				commandErrorPumps.put(cmd, errorPump);
				commandProcesses.put(cmd, p);
				pr = new PartitioningReader(
						new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8),
						this.rs);
				commandReaders.put(cmd, pr);
				this.filename = "";
			} catch (IOException ioe) {
				commandReaders.remove(cmd);
				Thread errorPump = commandErrorPumps.remove(cmd);
				Process p = commandProcesses.get(cmd);
				commandProcesses.remove(cmd);
				if (p != null) {
					p.destroy();
				}
				joinDataPump(errorPump);
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
		PrintStream ps = outputStreams.get(cmd);
		if (ps == null) {
			Process p;
			try {
				p = spawnProcess(cmd);
				Thread stderrPump = DataPump.dumpAndReturnThread(cmd + " stderr", p.getErrorStream(), error);
				Thread stdoutPump = DataPump.dumpAndReturnThread(cmd + " stdout", p.getInputStream(), output);
				outputStderrPumps.put(cmd, stderrPump);
				outputStdoutPumps.put(cmd, stdoutPump);
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Can't spawn " + cmd + ": " + ioe);
			}
			outputProcesses.put(cmd, p);
			try {
				ps = new PrintStream(p.getOutputStream(), true, StandardCharsets.UTF_8.name()); // true
				// = auto-flush
				outputStreams.put(cmd, ps);
			} catch (java.io.UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		}
		return ps;
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
	 * @param filename The filename/command process to close.
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
		Set<String> set = new HashSet<String>();
		for (String s : fileReaders.keySet()) {
			set.add(s);
		}
		for (String s : commandReaders.keySet()) {
			set.add(s);
		}
		for (String s : outputFiles.keySet()) {
			set.add(s);
		}
		for (String s : outputStreams.keySet()) {
			set.add(s);
		}
		for (String s : set) {
			jrtClose(s);
		}
	}

	private boolean jrtCloseOutputFile(String fileNameParam) {
		PrintStream ps = outputFiles.get(fileNameParam);
		if (ps != null) {
			ps.close();
			outputFiles.remove(fileNameParam);
		}
		return ps != null;
	}

	private boolean jrtCloseOutputStream(String cmd) {
		Process p = outputProcesses.get(cmd);
		PrintStream ps = outputStreams.get(cmd);
		if (ps == null) {
			return false;
		}
		Thread stdoutPump = outputStdoutPumps.get(cmd);
		Thread stderrPump = outputStderrPumps.get(cmd);
		assert p != null;
		outputProcesses.remove(cmd);
		outputStreams.remove(cmd);
		ps.close();
		try {
			// wait for the spawned process to finish to make sure
			// all output has been flushed and captured
			p.waitFor();
			p.exitValue();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			p.destroyForcibly();
			throw new AwkRuntimeException(
					"Caught exception while waiting for process exit: " + ie);
		} finally {
			joinDataPump(stdoutPump);
			joinDataPump(stderrPump);
			outputStdoutPumps.remove(cmd);
			outputStderrPumps.remove(cmd);
			output.flush();
			error.flush();
		}
		return true;
	}

	private boolean jrtCloseFileReader(String fileNameParam) {
		PartitioningReader pr = fileReaders.get(fileNameParam);
		if (pr == null) {
			return false;
		}
		fileReaders.remove(fileNameParam);
		try {
			pr.close();
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	private boolean jrtCloseCommandReader(String cmd) {
		Process p = commandProcesses.get(cmd);
		PartitioningReader pr = commandReaders.get(cmd);
		if (pr == null) {
			return false;
		}
		Thread errorPump = commandErrorPumps.get(cmd);
		assert p != null;
		commandReaders.remove(cmd);
		commandProcesses.remove(cmd);
		try {
			pr.close();
			try {
				// wait for the process to complete so that all
				// data pumped from the command is captured
				p.waitFor();
				p.exitValue();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				p.destroyForcibly();
				throw new AwkRuntimeException(
						"Caught exception while waiting for process exit: " + ie);
			}
			return true;
		} catch (IOException ioe) {
			return false;
		} finally {
			joinDataPump(errorPump);
			commandErrorPumps.remove(cmd);
			output.flush();
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
			Process p = spawnProcess(cmd);
			// no input to this process!
			p.getOutputStream().close();
			Thread errorPump = DataPump.dumpAndReturnThread(cmd + " stderr", p.getErrorStream(), error);
			Thread outputPump = DataPump.dumpAndReturnThread(cmd + " stdout", p.getInputStream(), output);
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
			output.flush();
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
	 * Transform the sub/gsub replacement string from Awk syntax
	 * (with '&amp;') to Java (with '$') so it can be used in Matcher.appendReplacement()
	 * <p>
	 * Awk and Java don't use the same syntax for regex replace:
	 * <ul>
	 * <li>Awk uses &amp; to refer to the matched string
	 * <li>Java uses $0, $g, or ${name} to refer to the corresponding match groups
	 * </ul>
	 *
	 * @param awkRepl the replace string passed in sub() and gsub()
	 * @return a string that can be used in Java's Matcher.appendReplacement()
	 */
	public static String prepareReplacement(String awkRepl) {
		// Null
		if (awkRepl == null) {
			return "";
		}

		// Simple case
		if ((awkRepl.indexOf('\\') == -1) && (awkRepl.indexOf('$') == -1) && (awkRepl.indexOf('&') == -1)) {
			return awkRepl;
		}

		StringBuilder javaRepl = new StringBuilder();
		for (int i = 0; i < awkRepl.length(); i++) {
			char c = awkRepl.charAt(i);

			// Backslash
			if (c == '\\' && i < awkRepl.length() - 1) {
				i++;
				c = awkRepl.charAt(i);
				if (c == '&') {
					javaRepl.append('&');
					continue;
				} else if (c == '\\') {
					javaRepl.append("\\\\");
					continue;
				}

				// For everything else, append the backslash and continue with the logic
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
	 * <p>
	 * replaceFirst.
	 * </p>
	 *
	 * @param origValue a {@link java.lang.String} object
	 * @param repl a {@link java.lang.String} object
	 * @param ere a {@link java.lang.String} object
	 * @param sb a {@link java.lang.StringBuffer} object
	 * @return a {@link java.lang.Integer} object
	 */
	public static Integer replaceFirst(String origValue, String repl, String ere, StringBuffer sb) {
		// remove special meaning for backslash and dollar signs and handle '&'
		repl = prepareReplacement(repl);

		// Reset provided StringBuffer
		sb.setLength(0);

		Pattern p = Pattern.compile(ere);
		Matcher m = p.matcher(origValue);
		int cnt = 0;
		if (m.find()) {
			++cnt;
			m.appendReplacement(sb, repl);
		}
		m.appendTail(sb);
		return Integer.valueOf(cnt);
	}

	/**
	 * Replace all occurrences of the regular expression with specified string
	 *
	 * @param origValue String where replace is done
	 * @param repl Replacement string (with '&amp;' for referring to matching string)
	 * @param ere Regular expression
	 * @param sb StringBuffer we will work on
	 * @return the number of replacements performed
	 */
	public static Integer replaceAll(String origValue, String repl, String ere, StringBuffer sb) {
		// Reset the provided StringBuffer
		sb.setLength(0);

		// remove special meaning for backslash and dollar signs and handle '&'
		repl = prepareReplacement(repl);

		Pattern p = Pattern.compile(ere);
		Matcher m = p.matcher(origValue);
		int cnt = 0;
		while (m.find()) {
			++cnt;
			m.appendReplacement(sb, repl);
		}
		m.appendTail(sb);
		return Integer.valueOf(cnt);
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
		// if (rsObj.toString().equals(BLANK))
		// rs_obj = DEFAULT_RS_REGEX;
		if (partitioningReader != null) {
			partitioningReader.setRecordSeparator(rsObj.toString());
		}
	}
}
