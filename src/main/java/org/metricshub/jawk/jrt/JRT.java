package org.metricshub.jawk.jrt;

import java.io.FileInputStream;
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
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.metricshub.jawk.intermediate.UninitializedObject;
import org.metricshub.jawk.util.AwkLogger;
import org.slf4j.Logger;

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
 *
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
 *
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

	private static final Logger LOG = AwkLogger.getLogger(JRT.class);

	private static final boolean IS_WINDOWS = System.getProperty("os.name").indexOf("Windows") >= 0;

	private VariableManager vm;

	private Map<String, Process> output_processes = new HashMap<String, Process>();
	private Map<String, PrintStream> output_streams = new HashMap<String, PrintStream>();

	// Partitioning reader for stdin.
	private PartitioningReader partitioningReader = null;
	// Current input line ($0).
	private String inputLine = null;
	// Current input fields ($0, $1, $2, ...).
	private List<String> input_fields = new ArrayList<String>(100);
	private AssocArray arglist_aa = null;
	private int arglist_idx;
	private boolean has_filenames = false;
	private static final UninitializedObject BLANK = new UninitializedObject();

	private static final Integer ONE = Integer.valueOf(1);
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer MINUS_ONE = Integer.valueOf(-1);
	private static final double EPSILON = 1e-9;
	private String jrt_input_string;

	private Map<String, PartitioningReader> file_readers = new HashMap<String, PartitioningReader>();
	private Map<String, PartitioningReader> command_readers = new HashMap<String, PartitioningReader>();
	private Map<String, Process> command_processes = new HashMap<String, Process>();
	private Map<String, PrintStream> outputFiles = new HashMap<String, PrintStream>();

	/**
	 * Create a JRT with a VariableManager
	 *
	 * @param vm The VariableManager to use with this JRT.
	 */
	public JRT(VariableManager vm) {
		if (vm == null) {
			throw new IllegalArgumentException("vm cannot be null");
		}
		this.vm = vm;
	}

	/**
	 * Assign all -v variables.
	 *
	 * @param initial_var_map A map containing all initial variable
	 *   names and their values.
	 */
	public final void assignInitialVariables(Map<String, Object> initial_var_map) {
		assert initial_var_map != null;
		for (Map.Entry<String, Object> var : initial_var_map.entrySet()) {
			vm.assignVariable(var.getKey(), var.getValue());
		}
	}

	/**
	 * Called by AVM/compiled modules to assign local
	 * environment variables to an associative array
	 * (in this case, to ENVIRON).
	 *
	 * @param aa The associative array to populate with
	 *   environment variables. The module asserts that
	 *   the associative array is empty prior to population.
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
	 * based on the CONVFMT variable contents.
	 *
	 * @param o Object to convert.
	 * @param convfmt The contents of the CONVFMT variable.
	 * @return A String representation of o.
	 * @param locale a {@link java.util.Locale} object
	 */
	public static String toAwkString(Object o, String convfmt, Locale locale) {
		if (o instanceof Number) {
			// It is a number, some processing is required here
			double d = ((Number) o).doubleValue();
			if (Math.abs(d - (long) d) < EPSILON) {
				// If an integer, represent it as an integer (no floating point and decimals)
				return Long.toString((long) d);
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

	// not static to use CONVFMT (& possibly OFMT later)
	/**
	 * Convert a String, Integer, or Double to String
	 * based on the OFMT variable contents. Jawk will
	 * subsequently use this String for output via print().
	 *
	 * @param o Object to convert.
	 * @param ofmt The contents of the OFMT variable.
	 * @return A String representation of o.
	 * @param locale a {@link java.util.Locale} object
	 */
	public static String toAwkStringForOutput(Object o, String ofmt, Locale locale) {
		// Even if specified Object o is not officially a number, we try to convert
		// it to a Double. Because if it's a literal representation of a number,
		// we will need to display it as a number ("12.00" --> 12)
		if (!(o instanceof Number)) {
			try {
				o = Double.parseDouble(o.toString());
			} catch (NumberFormatException e) {
				LOG.debug("Failed to parse number", e);
			}
		}

		return toAwkString(o, ofmt, locale);
	}

	/**
	 * Convert a String, Integer, or Double to Double.
	 *
	 * @param o Object to convert.
	 *
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
	 * Convert a String, Long, or Double to Long.
	 *
	 * @param o Object to convert.
	 *
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
	 * Compares two objects. Whether to employ less-than, equals, or
	 * greater-than checks depends on the mode chosen by the callee.
	 * It handles Awk variable rules and type conversion semantics.
	 *
	 * @param o1 The 1st object.
	 * @param o2 the 2nd object.
	 * @param mode <ul>
	 *   <li>&lt; 0 - Return true if o1 &lt; o2.
	 *   <li>0 - Return true if o1 == o2.
	 *   <li>&gt; 0 - Return true if o1 &gt; o2.
	 *   </ul>
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

		if (!(o1 instanceof Number) && !o1String.isEmpty()) {
			char o1FirstChar = o1String.charAt(0);
			if (o1FirstChar >= '0' && o1FirstChar <= '9') {
				try {
					o1 = Double.parseDouble(o1String);
				} catch (NumberFormatException nfe) {
					LOG.debug("Invalid number", nfe);
				}
			}
		}
		if (!(o2 instanceof Number) && !o2String.isEmpty()) {
			char o2FirstChar = o2String.charAt(0);
			if (o2FirstChar >= '0' && o2FirstChar <= '9') {
				try {
					o2 = Double.parseDouble(o2String);
				} catch (NumberFormatException nfe) {
					LOG.debug("Invalid number", nfe);
				}
			}
		}

		if ((o1 instanceof Number) && (o2 instanceof Number)) {
			if (mode < 0) {
				return ((Number) o1).doubleValue() < ((Number) o2).doubleValue();
			} else if (mode == 0) {
				return Math.abs(((Number) o1).doubleValue() - ((Number) o2).doubleValue()) <= EPSILON;
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
	 *   if o is a String object and represents a double.
	 *   Otherwise, 1 is returned. If the return value
	 *   is an integer, an Integer object is returned.
	 *   Otherwise, a Double object is returned.
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
		if (ans == (long) ans) {
			return (long) ans;
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
	 *   if o is a String object and represents a double.
	 *   Otherwise, -1 is returned. If the return value
	 *   is an integer, an Integer object is returned.
	 *   Otherwise, a Double object is returned.
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
		if (ans == (long) ans) {
			return (long) ans;
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
	 * 	<ul>
	 * 	<li><strong>Integer</strong> - o.intValue() != 0
	 * 	<li><strong>Long</strong> - o.longValue() != 0
	 * 	<li><strong>Double</strong> - o.doubleValue() != 0
	 * 	<li><strong>String</strong> - o.length() &gt; 0
	 * 	<li><strong>UninitializedObject</strong> - false
	 * 	<li><strong>Pattern</strong> - $0 ~ o
	 * 	</ul>
	 * 	If o is none of these types, an error is thrown.
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
	 * @param convfmt Contents of the CONVFMT variable.
	 * @return The number of parts resulting from this split operation.
	 * @param locale a {@link java.util.Locale} object
	 */
	public static int split(Object array, Object string, String convfmt, Locale locale) {
		return splitWorker(new StringTokenizer(toAwkString(string, convfmt, locale)), (AssocArray) array);
	}

	/**
	 * Splits the string into parts separated the regular expression fs.
	 * This conforms to the 3-argument version of AWK's split function.
	 * <p>
	 * If fs is blank, it behaves similar to the 2-arg version of
	 * AWK's split function.
	 *
	 * @param fs Field separator regular expression.
	 * @param array The array to populate.
	 * @param string The string to split.
	 * @param convfmt Contents of the CONVFMT variable.
	 * @return The number of parts resulting from this split operation.
	 * @param locale a {@link java.util.Locale} object
	 */
	public static int split(Object fs, Object array, Object string, String convfmt, Locale locale) {
		String fs_string = toAwkString(fs, convfmt, locale);
		if (fs_string.equals(" ")) {
			return splitWorker(new StringTokenizer(toAwkString(string, convfmt, locale)), (AssocArray) array);
		} else if (fs_string.equals("")) {
			return splitWorker(new CharacterTokenizer(toAwkString(string, convfmt, locale)), (AssocArray) array);
		} else if (fs_string.length() == 1) {
			return splitWorker(new SingleCharacterTokenizer(toAwkString(string, convfmt, locale), fs_string.charAt(0)), (AssocArray) array);
		} else {
			return splitWorker(new RegexTokenizer(toAwkString(string, convfmt, locale), fs_string), (AssocArray) array);
		}
	}

	private static int splitWorker(Enumeration<Object> e, AssocArray aa) {
		int cnt = 0;
		aa.clear();
		while (e.hasMoreElements()) {
			aa.put(++cnt, e.nextElement());
		}
		return cnt;
	}

	/**
	 * <p>Getter for the field <code>partitioningReader</code>.</p>
	 *
	 * @return a {@link org.metricshub.jawk.jrt.PartitioningReader} object
	 */
	public PartitioningReader getPartitioningReader() {
		if (partitioningReader == null) {
			return null;
		}
		return partitioningReader.copy();
	}

	/**
	 * <p>Getter for the field <code>inputLine</code>.</p>
	 *
	 * @return a {@link java.lang.String} object
	 */
	public String getInputLine() {
		return inputLine;
	}

	/**
	 * <p>Setter for the field <code>inputLine</code>.</p>
	 *
	 * @param inputLine a {@link java.lang.String} object
	 */
	public void setInputLine(String inputLine) {
		this.inputLine = inputLine;
	}

	/**
	 * Attempt to consume one line of input, either from stdin
	 * or from filenames passed in to ARGC/ARGV via
	 * the command-line.
	 *
	 * @param for_getline true if call is for getline, false otherwise.
	 * @return true if line is consumed, false otherwise.
	 * @throws java.io.IOException upon an IO error.
	 * @param input a {@link java.io.InputStream} object
	 * @param locale a {@link java.util.Locale} object
	 */
	public boolean jrtConsumeInput(final InputStream input, boolean for_getline, Locale locale) throws IOException {
		// first time!
		if (arglist_aa == null) {
			Object arglist_obj = vm.getARGV(); // vm.getVariable("argv_field", true);
			arglist_aa = (AssocArray) arglist_obj;
			arglist_idx = 1;

			// calculate has_filenames

			int argc = (int) toDouble(vm.getARGC()); //(vm.getVariable("argc_field", true));
			// 1 .. argc doesn't make sense
			// 1 .. argc-1 does since arguments of:
			// a b c
			// result in:
			// ARGC=4
			// ARGV[0]="java Awk"
			// ARGV[1]="a"
			// ARGV[2]="b"
			// ARGV[3]="c"
			for (long i = 1; i < argc; i++) {
				if (arglist_aa.isIn(i)) {
					Object namevalue_or_filename_object = arglist_aa.get(i);
					String namevalue_or_filename = toAwkString(namevalue_or_filename_object, vm.getCONVFMT().toString(), locale);
					if (namevalue_or_filename.indexOf('=') == -1) {
						// filename!
						has_filenames = true;
						break;
					}
				}
			}
		}

		// initial: pr == null
		// subsequent: pr != null, but eof

		while (true) {
			try {
				if (partitioningReader == null) {
					int argc = (int) toDouble(vm.getARGC()); // (vm.getVariable("argc_field", true));
					Object o = BLANK;
					while (arglist_idx <= argc) {
						o = arglist_aa.get(arglist_idx);
						++arglist_idx;
						if (!(o instanceof UninitializedObject || o.toString().isEmpty())) {
							break;
						}
					}
					if (!(o instanceof UninitializedObject || o.toString().isEmpty())) {
						String name_value_or_filename = toAwkString(o, vm.getCONVFMT().toString(), locale);
						if (name_value_or_filename.indexOf('=') == -1) {
							partitioningReader =
								new PartitioningReader(new InputStreamReader(new FileInputStream(name_value_or_filename), StandardCharsets.UTF_8), vm.getRS().toString(), true);
							vm.setFILENAME(name_value_or_filename);
							vm.resetFNR();
						} else {
							setFilelistVariable(name_value_or_filename);
							if (!has_filenames) {
								// stdin with a variable!
								partitioningReader = new PartitioningReader(new InputStreamReader(input, StandardCharsets.UTF_8), vm.getRS().toString());
								vm.setFILENAME("");
							} else {
								continue;
							}
						}
					} else if (!has_filenames) {
						partitioningReader = new PartitioningReader(new InputStreamReader(input, StandardCharsets.UTF_8), vm.getRS().toString());
						vm.setFILENAME("");
					} else {
						return false;
					}
				} else if (inputLine == null) {
					if (has_filenames) {
						int argc = (int) toDouble(vm.getARGC());
						Object o = BLANK;
						while (arglist_idx <= argc) {
							o = arglist_aa.get(arglist_idx);
							++arglist_idx;
							if (!(o instanceof UninitializedObject || o.toString().isEmpty())) {
								break;
							}
						}
						if (!(o instanceof UninitializedObject || o.toString().isEmpty())) {
							String name_value_or_filename = toAwkString(o, vm.getCONVFMT().toString(), locale);
							if (name_value_or_filename.indexOf('=') == -1) {
								// true = from filename list
								partitioningReader =
									new PartitioningReader(
										new InputStreamReader(new FileInputStream(name_value_or_filename), StandardCharsets.UTF_8),
										vm.getRS().toString(),
										true
									);
								vm.setFILENAME(name_value_or_filename);
								vm.resetFNR();
							} else {
								setFilelistVariable(name_value_or_filename);
								vm.incNR();
								continue;
							}
						} else {
							return false;
						}
					} else {
						return false;
					}
				}

				// when active_input == false, usually means
				// to instantiate "pr" (PartitioningReader for $0, etc)
				// for Jawk extensions
				//if (!active_input)
				//	return false;

				inputLine = partitioningReader.readRecord();
				if (inputLine == null) {
					continue;
				} else {
					if (!for_getline) {
						// For getline the caller will re-acquire $0; otherwise parse fields
						jrtParseFields();
					}
					vm.incNR();
					if (partitioningReader.fromFilenameList()) {
						vm.incFNR();
					}
					return true;
				}
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				continue;
			}
		}
	}

	private void setFilelistVariable(String name_value) {
		int eq_idx = name_value.indexOf('=');
		// variable name should be non-blank
		assert eq_idx >= 0;
		if (eq_idx == 0) {
			throw new IllegalArgumentException("Must have a non-blank variable name in a name=value variable assignment argument.");
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
		vm.assignVariable(name, obj);
	}

	/**
	 * Splits $0 into $1, $2, etc.
	 * Called when an update to $0 has occurred.
	 */
	public void jrtParseFields() {
		String fs_string = vm.getFS().toString();
		Enumeration<Object> tokenizer;
		if (fs_string.equals(" ")) {
			tokenizer = new StringTokenizer(inputLine);
		} else if (fs_string.length() == 1) {
			tokenizer = new SingleCharacterTokenizer(inputLine, fs_string.charAt(0));
		} else if (fs_string.equals("")) {
			tokenizer = new CharacterTokenizer(inputLine);
		} else {
			tokenizer = new RegexTokenizer(inputLine, fs_string);
		}

		assert inputLine != null;
		input_fields.clear();
		input_fields.add(inputLine); // $0
		while (tokenizer.hasMoreElements()) {
			input_fields.add((String) tokenizer.nextElement());
		}
		// recalc NF
		recalculateNF();
	}

	private void recalculateNF() {
		vm.setNF(Integer.valueOf(input_fields.size() - 1));
	}

	private static int toFieldNumber(Object o) {
		int fieldnum;
		if (o instanceof Number) {
			fieldnum = ((Number) o).intValue();
		} else {
			try {
				fieldnum = (int) Double.parseDouble(o.toString());
			} catch (NumberFormatException nfe) {
				throw new RuntimeException("Field $(" + o.toString() + ") is incorrect.");
			}
		}
		return fieldnum;
	}

	/**
	 * Retrieve the contents of a particular input field.
	 *
	 * @param fieldnum_obj Object referring to the field number.
	 * @return Contents of the field.
	 */
	public Object jrtGetInputField(Object fieldnum_obj) {
		return jrtGetInputField(toFieldNumber(fieldnum_obj));
	}

	/**
	 * <p>jrtGetInputField.</p>
	 *
	 * @param fieldnum a int
	 * @return a {@link java.lang.Object} object
	 */
	public Object jrtGetInputField(int fieldnum) {
		if (fieldnum < input_fields.size()) {
			String retval = input_fields.get(fieldnum);
			assert retval != null;
			return retval;
		} else {
			return BLANK;
		}
	}

	/**
	 * Stores value_obj into an input field.
	 *
	 * @param value_obj The RHS of the assignment.
	 * @param field_num Object referring to the field number.
	 * @return A string representation of value_obj.
	 */
	public String jrtSetInputField(Object value_obj, int field_num) {
		assert field_num >= 1;
		assert value_obj != null;
		String value = value_obj.toString();
		// if the value is BLANK
		if (value_obj instanceof UninitializedObject) {
			if (field_num < input_fields.size()) {
				input_fields.set(field_num, "");
			}
		} else {
			// append the list to accommodate the new value
			for (int i = input_fields.size() - 1; i < field_num; i++) {
				input_fields.add("");
			}
			input_fields.set(field_num, value);
		}
		// rebuild $0
		rebuildDollarZeroFromFields();
		// recalc NF
		recalculateNF();
		return value;
	}

	private void rebuildDollarZeroFromFields() {
		StringBuilder new_dollar_zero_sb = new StringBuilder();
		String ofs = vm.getOFS().toString();
		for (int i = 1; i < input_fields.size(); i++) {
			if (i > 1) {
				new_dollar_zero_sb.append(ofs);
			}
			new_dollar_zero_sb.append(input_fields.get(i));
		}
		input_fields.set(0, new_dollar_zero_sb.toString());
	}

	/**
	 * <p>jrtConsumeFileInputForGetline.</p>
	 *
	 * @param filename a {@link java.lang.String} object
	 * @return a {@link java.lang.Integer} object
	 */
	public Integer jrtConsumeFileInputForGetline(String filename) {
		try {
			if (jrtConsumeFileInput(filename)) {
				return ONE;
			} else {
				jrt_input_string = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrt_input_string = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve the next line of output from a command, executing
	 * the command if necessary and store it to $0.
	 *
	 * @param cmd_string The command to execute.
	 * @return Integer(1) if successful, Integer(0) if no more
	 * 	input is available, Integer(-1) upon an IO error.
	 */
	public Integer jrtConsumeCommandInputForGetline(String cmd_string) {
		try {
			if (jrtConsumeCommandInput(cmd_string)) {
				return ONE;
			} else {
				jrt_input_string = "";
				return ZERO;
			}
		} catch (IOException ioe) {
			jrt_input_string = "";
			return MINUS_ONE;
		}
	}

	/**
	 * Retrieve $0.
	 *
	 * @return The contents of the $0 input field.
	 */
	public String jrtGetInputString() {
		return jrt_input_string;
	}

	/**
	 * <p>Getter for the field <code>outputFiles</code>.</p>
	 *
	 * @return a {@link java.util.Map} object
	 */
	public Map<String, PrintStream> getOutputFiles() {
		return new HashMap<String, PrintStream>(outputFiles);
	}

	/**
	 * Retrieve the PrintStream which writes to a particular file,
	 * creating the PrintStream if necessary.
	 *
	 * @param filename The file which to write the contents of the PrintStream.
	 * @param append true to append to the file, false to overwrite the file.
	 * @return a {@link java.io.PrintStream} object
	 */
	public final PrintStream jrtGetPrintStream(String filename, boolean append) {
		PrintStream ps = outputFiles.get(filename);
		if (ps == null) {
			try {
				outputFiles.put(filename, ps = new PrintStream(new FileOutputStream(filename, append), true)); // true = autoflush
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Cannot open " + filename + " for writing: " + ioe);
			}
		}
		assert ps != null;
		return ps;
	}

	/**
	 * <p>jrtConsumeFileInput.</p>
	 *
	 * @param filename a {@link java.lang.String} object
	 * @return a boolean
	 * @throws java.io.IOException if any.
	 */
	public boolean jrtConsumeFileInput(String filename) throws IOException {
		PartitioningReader pr = file_readers.get(filename);
		if (pr == null) {
			try {
				file_readers.put(
					filename,
					pr = new PartitioningReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8), vm.getRS().toString())
				);
				vm.setFILENAME(filename);
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				file_readers.remove(filename);
				throw ioe;
			}
		}

		inputLine = pr.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrt_input_string = inputLine;
			vm.incNR();
			return true;
		}
	}

	private static Process spawnProcess(String cmd) throws IOException {
		Process p;

		if (IS_WINDOWS) {
			// spawn the process!
			ProcessBuilder pb = new ProcessBuilder(("cmd.exe /c " + cmd).split("[ \t]+"));
			p = pb.start();
		} else {
			// spawn the process!
			ProcessBuilder pb = new ProcessBuilder(cmd.split("[ \t]+"));
			p = pb.start();
		}

		return p;
	}

	/**
	 * <p>jrtConsumeCommandInput.</p>
	 *
	 * @param cmd a {@link java.lang.String} object
	 * @return a boolean
	 * @throws java.io.IOException if any.
	 */
	public boolean jrtConsumeCommandInput(String cmd) throws IOException {
		PartitioningReader pr = command_readers.get(cmd);
		if (pr == null) {
			try {
				Process p = spawnProcess(cmd);
				// no input to this process!
				p.getOutputStream().close();
				DataPump.dump(cmd, p.getErrorStream(), System.err);
				command_processes.put(cmd, p);
				command_readers.put(cmd, pr = new PartitioningReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), vm.getRS().toString()));
				vm.setFILENAME("");
			} catch (IOException ioe) {
				LOG.warn("IO Exception", ioe);
				command_readers.remove(cmd);
				Process p = command_processes.get(cmd);
				command_processes.remove(cmd);
				if (p != null) {
					p.destroy();
				}
				throw ioe;
			}
		}

		inputLine = pr.readRecord();
		if (inputLine == null) {
			return false;
		} else {
			jrt_input_string = inputLine;
			vm.incNR();
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
	 *   input data to the process.
	 */
	public PrintStream jrtSpawnForOutput(String cmd) {
		PrintStream ps = output_streams.get(cmd);
		if (ps == null) {
			Process p;
			try {
				p = spawnProcess(cmd);
				DataPump.dump(cmd, p.getErrorStream(), System.err);
				DataPump.dump(cmd, p.getInputStream(), System.out);
			} catch (IOException ioe) {
				throw new AwkRuntimeException("Can't spawn " + cmd + ": " + ioe);
			}
			output_processes.put(cmd, p);
			output_streams.put(cmd, ps = new PrintStream(p.getOutputStream(), true)); // true = auto-flush
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
	 *   otherwise.
	 */
	public Integer jrtClose(String filename) {
		boolean b1 = jrtCloseFileReader(filename);
		boolean b2 = jrtCloseCommandReader(filename);
		boolean b3 = jrtCloseOutputFile(filename);
		boolean b4 = jrtCloseOutputStream(filename);
		// either close will do
		return (b1 || b2 || b3 || b4) ? ZERO : MINUS_ONE;
	}

	/**
	 * <p>jrtCloseAll.</p>
	 */
	public void jrtCloseAll() {
		Set<String> set = new HashSet<String>();
		for (String s : file_readers.keySet()) {
			set.add(s);
		}
		for (String s : command_readers.keySet()) {
			set.add(s);
		}
		for (String s : outputFiles.keySet()) {
			set.add(s);
		}
		for (String s : output_streams.keySet()) {
			set.add(s);
		}
		for (String s : set) {
			jrtClose(s);
		}
	}

	private boolean jrtCloseOutputFile(String filename) {
		PrintStream ps = outputFiles.get(filename);
		if (ps != null) {
			ps.close();
			outputFiles.remove(filename);
		}
		return ps != null;
	}

	private boolean jrtCloseOutputStream(String cmd) {
		Process p = output_processes.get(cmd);
		PrintStream ps = output_streams.get(cmd);
		if (ps == null) {
			return false;
		}
		assert p != null;
		output_processes.remove(cmd);
		output_streams.remove(cmd);
		ps.close();
		// if windows, let the process kill itself eventually
		if (!IS_WINDOWS) {
			try {
				// causes a hard exit ?!
				p.waitFor();
				p.exitValue();
			} catch (InterruptedException ie) {
				throw new AwkRuntimeException("Caught exception while waiting for process exit: " + ie);
			}
		}
		return true;
	}

	private boolean jrtCloseFileReader(String filename) {
		PartitioningReader pr = file_readers.get(filename);
		if (pr == null) {
			return false;
		}
		file_readers.remove(filename);
		try {
			pr.close();
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	private boolean jrtCloseCommandReader(String cmd) {
		Process p = command_processes.get(cmd);
		PartitioningReader pr = command_readers.get(cmd);
		if (pr == null) {
			return false;
		}
		assert p != null;
		command_readers.remove(cmd);
		command_processes.remove(cmd);
		try {
			pr.close();
			// if windows, let the process kill itself eventually
			if (!IS_WINDOWS) {
				try {
					// causes a hard die ?!
					p.waitFor();
					p.exitValue();
				} catch (InterruptedException ie) {
					throw new AwkRuntimeException("Caught exception while waiting for process exit: " + ie);
				}
			}
			return true;
		} catch (IOException ioe) {
			return false;
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
	 *   process. Integer(-1) is returned on an IO error.
	 */
	public static Integer jrtSystem(String cmd) {
		try {
			Process p = spawnProcess(cmd);
			// no input to this process!
			p.getOutputStream().close();
			DataPump.dump(cmd, p.getErrorStream(), System.err);
			DataPump.dump(cmd, p.getInputStream(), System.out);
			try {
				int retcode = p.waitFor();
				return Integer.valueOf(retcode);
			} catch (InterruptedException ie) {
				return Integer.valueOf(p.exitValue());
			}
		} catch (IOException ioe) {
			LOG.warn("IO Exception", ioe);
			return MINUS_ONE;
		}
	}

	/**
	 * <p>sprintfFunctionNoCatch.</p>
	 *
	 * @param locale a {@link java.util.Locale} object
	 * @param fmt_arg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 * @return a {@link java.lang.String} object
	 * @throws java.util.IllegalFormatException if any.
	 */
	public static String sprintfNoCatch(Locale locale, String fmt_arg, Object... arr) throws IllegalFormatException {
		return String.format(locale, fmt_arg, arr);
	}

	/**
	 * <p>printfFunctionNoCatch.</p>
	 *
	 * @param locale a {@link java.util.Locale} object
	 * @param fmt_arg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 */
	public static void printfNoCatch(Locale locale, String fmt_arg, Object... arr) {
		System.out.print(sprintfNoCatch(locale, fmt_arg, arr));
	}

	/**
	 * <p>printfFunctionNoCatch.</p>
	 *
	 * @param ps a {@link java.io.PrintStream} object
	 * @param locale a {@link java.util.Locale} object
	 * @param fmt_arg a {@link java.lang.String} object
	 * @param arr an array of {@link java.lang.Object} objects
	 */
	public static void printfNoCatch(PrintStream ps, Locale locale, String fmt_arg, Object... arr) {
		ps.print(sprintfNoCatch(locale, fmt_arg, arr));
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
	 * <p>replaceFirst.</p>
	 *
	 * @param orig_value a {@link java.lang.String} object
	 * @param repl a {@link java.lang.String} object
	 * @param ere a {@link java.lang.String} object
	 * @param sb a {@link java.lang.StringBuffer} object
	 * @return a {@link java.lang.Integer} object
	 */
	public static Integer replaceFirst(String orig_value, String repl, String ere, StringBuffer sb) {
		// remove special meaning for backslash and dollar signs and handle '&'
		repl = prepareReplacement(repl);

		// Reset provided StringBuffer
		sb.setLength(0);

		Pattern p = Pattern.compile(ere);
		Matcher m = p.matcher(orig_value);
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
	 * @param orig_value String where replace is done
	 * @param repl Replacement string (with '&amp;' for referring to matching string)
	 * @param ere Regular expression
	 * @param sb StringBuffer we will work on
	 * @return the number of replacements performed
	 */
	public static Integer replaceAll(String orig_value, String repl, String ere, StringBuffer sb) {
		// Reset the provided StringBuffer
		sb.setLength(0);

		// remove special meaning for backslash and dollar signs and handle '&'
		repl = prepareReplacement(repl);

		Pattern p = Pattern.compile(ere);
		Matcher m = p.matcher(orig_value);
		int cnt = 0;
		while (m.find()) {
			++cnt;
			m.appendReplacement(sb, repl);
		}
		m.appendTail(sb);
		return Integer.valueOf(cnt);
	}

	/**
	 * <p>substr.</p>
	 *
	 * @param startpos_obj a {@link java.lang.Object} object
	 * @param str a {@link java.lang.String} object
	 * @return a {@link java.lang.String} object
	 */
	public static String substr(Object startpos_obj, String str) {
		int startpos = (int) toDouble(startpos_obj);
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
	 * <p>substr.</p>
	 *
	 * @param size_obj a {@link java.lang.Object} object
	 * @param startpos_obj a {@link java.lang.Object} object
	 * @param str a {@link java.lang.String} object
	 * @return a {@link java.lang.String} object
	 */
	public static String substr(Object size_obj, Object startpos_obj, String str) {
		int startpos = (int) toDouble(startpos_obj);
		if (startpos <= 0) {
			throw new AwkRuntimeException("2nd arg to substr must be a positive integer");
		}
		if (startpos > str.length()) {
			return "";
		}
		int size = (int) toDouble(size_obj);
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
	 * <p>timeSeed.</p>
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
	 * <p>newRandom.</p>
	 *
	 * @param seed a int
	 * @return a {@link java.util.Random} object
	 */
	public static Random newRandom(int seed) {
		return new Random(seed);
	}

	/**
	 * <p>applyRS.</p>
	 *
	 * @param rs_obj a {@link java.lang.Object} object
	 */
	public void applyRS(Object rs_obj) {
		//	if (rs_obj.toString().equals(BLANK))
		//		rs_obj = DEFAULT_RS_REGEX;
		if (partitioningReader != null) {
			partitioningReader.setRecordSeparator(rs_obj.toString());
		}
	}
}
