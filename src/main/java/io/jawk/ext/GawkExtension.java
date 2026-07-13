package io.jawk.ext;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.backend.AVM;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkBeforeStart;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.ext.annotations.JawkOptional;
import io.jawk.ext.annotations.JawkRawValue;
import io.jawk.ext.annotations.JawkRegexp;
import io.jawk.intermediate.UninitializedObject;
import io.jawk.intermediate.UntypedObject;
import io.jawk.jrt.IllegalAwkArgumentException;
import io.jawk.jrt.JRT;
import io.jawk.jrt.StrNum;

/**
 * GNU awk compatibility extension for array sorting and type introspection.
 */
public class GawkExtension extends AbstractExtension implements JawkExtension {

	private static final String VAL_TYPE_ASC = "@val_type_asc";

	private static final class SortEntry {
		private final Object index;
		private final Object value;

		private SortEntry(Object indexParam, Object valueParam) {
			this.index = indexParam;
			this.value = valueParam;
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getExtensionName() {
		return "GawkExtension";
	}

	/** Interpreter this per-engine extension instance is bound to. */
	private AVM avm;

	/**
	 * Installs the {@code PROCINFO["sorted_in"]} traversal order for
	 * {@code for-in} loops and binds this per-engine extension instance to its
	 * interpreter. SYMTAB and FUNCTAB are populated by the interpreter itself.
	 *
	 * @param avmParam interpreter about to execute
	 * @param jrt runtime associated with {@code avmParam}
	 */
	@JawkBeforeStart
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The extension is a per-engine instance deliberately bound to its interpreter")
	public void initializeGawkVariables(AVM avmParam, JRT jrt) {
		this.avm = avmParam;
		avm.setForInKeyOrder(this::orderForInKeys);
	}

	/**
	 * Returns the {@code for (index in array)} traversal order mandated by
	 * {@code PROCINFO["sorted_in"]}, or the array's natural key order when no
	 * sort mode is in effect.
	 */
	private Collection<Object> orderForInKeys(Map<Object, Object> map) {
		String mode = currentSortedIn();
		if (mode == null || mode.isEmpty() || "@unsorted".equals(mode)) {
			return map.keySet();
		}
		return sortedKeys(map, effectiveSortMode(mode, VAL_TYPE_ASC), getJrt(), currentIgnoreCase());
	}

	/*
	 * Gawk also accepts the name of a user-defined comparison function, which
	 * Jawk does not support: those fall back to the default ordering with a
	 * one-time warning. Unknown @-modes are typos and stay fatal, as in gawk.
	 */
	private String effectiveSortMode(String mode, String defaultMode) {
		if (mode.isEmpty()) {
			// gawk treats an empty mode like an omitted one
			return defaultMode;
		}
		if (mode.charAt(0) != '@') {
			warnUnsupportedComparator(mode);
			return defaultMode;
		}
		return mode;
	}

	private void warnUnsupportedComparator(String name) {
		if (warnedComparators == null) {
			warnedComparators = new HashSet<String>();
		}
		if (warnedComparators.add(name)) {
			warnAtCurrentLine("sort comparison function `%s' is not supported; using default ordering", name);
		}
	}

	/** Comparison-function names already warned about; created on first use. */
	private Set<String> warnedComparators;

	private String currentSortedIn() {
		Object procinfo = getVm().getVariable("PROCINFO");
		if (!(procinfo instanceof Map)) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<Object, Object> procinfoMap = (Map<Object, Object>) procinfo;
		if (!JRT.containsAwkKey(procinfoMap, "sorted_in")) {
			return null;
		}
		return getJrt().toAwkString(JRT.getAssocArrayValue(procinfoMap, "sorted_in"));
	}

	/**
	 * Sorts an array by value, optionally writing the result to another array.
	 *
	 * @param source source array
	 * @param dest destination array, or {@code null} to sort in place
	 * @param how predefined sorting mode, or {@code null} for the default
	 * @return number of sorted elements
	 */
	@JawkFunction("asort")
	public Long asort(
			@JawkAssocArray Map<Object, Object> source,
			@JawkOptional @JawkAssocArray Map<Object, Object> dest,
			@JawkOptional Object how) {
		return sort(source, dest, how, false);
	}

	/**
	 * Sorts an array by index, optionally writing the result to another array.
	 *
	 * @param source source array
	 * @param dest destination array, or {@code null} to sort in place
	 * @param how predefined sorting mode, or {@code null} for the default
	 * @return number of sorted elements
	 */
	@JawkFunction("asorti")
	public Long asorti(
			@JawkAssocArray Map<Object, Object> source,
			@JawkOptional @JawkAssocArray Map<Object, Object> dest,
			@JawkOptional Object how) {
		return sort(source, dest, how, true);
	}

	/**
	 * Returns the gawk type category for a value.
	 *
	 * @param value value to inspect
	 * @param meta optional metadata destination array
	 * @return gawk type name
	 */
	@JawkFunction("typeof")
	public String typeof(@JawkRawValue Object value, @JawkOptional @JawkAssocArray Map<Object, Object> meta) {
		if (meta != null) {
			meta.clear();
			if (value instanceof Map) {
				meta.put("array_type", arrayType((Map<?, ?>) value));
			}
		}
		return typeOf(value);
	}

	/**
	 * Returns whether the supplied value is an array.
	 *
	 * @param value value to inspect
	 * @return 1 for arrays, 0 otherwise
	 */
	@JawkFunction("isarray")
	public Long isarray(@JawkRawValue Object value) {
		return value instanceof Map ? Long.valueOf(1L) : Long.valueOf(0L);
	}

	/**
	 * Creates a boolean-typed numeric value used by gawk's test suite.
	 *
	 * @param value truth value
	 * @return boolean numeric value
	 */
	@JawkFunction("mkbool")
	public GawkBool mkbool(Object value) {
		// gawk applies ordinary AWK truthiness: a non-empty non-numeric
		// string like "abc" is true, not numeric-coerced to 0
		return new GawkBool(getJrt().toBoolean(value));
	}

	/**
	 * Performs a small gawk-compatible {@code gensub()} substitution.
	 *
	 * @param regexp regular expression
	 * @param replacement replacement text
	 * @param how occurrence selector or {@code g}
	 * @param target target text, or {@code null} to default to {@code $0}
	 * @return substituted text
	 */
	@JawkFunction("gensub")
	public String gensub(@JawkRegexp Object regexp, Object replacement, Object how, @JawkOptional Object target) {
		Pattern pattern = regexp instanceof Pattern ?
				(Pattern) regexp : Pattern.compile(toAwkString(regexp));
		// gawk: a truthy IGNORECASE makes all regexp operations case-insensitive
		pattern = getJrt().caseAwarePattern(pattern);
		Object targetValue = target == null ? getJrt().getInputLine() : target;
		Matcher matcher = pattern.matcher(toAwkString(targetValue));
		String repl = JRT.prepareReplacement(toAwkString(replacement), pattern.matcher("").groupCount());
		String selector = toAwkString(how);
		// gawk: any string beginning with 'g' or 'G' selects a global replacement
		if (!selector.isEmpty() && (selector.charAt(0) == 'g' || selector.charAt(0) == 'G')) {
			return matcher.replaceAll(repl);
		}
		// gawk coerces the selector with AWK numeric conversion (so " 2" and
		// "1e1" are the occurrences 2 and 10) and warns only when the result,
		// truncated, is below 1
		double selected = JRT.toDouble(how);
		if (selected < 1.0D) {
			warnAtCurrentLine("gensub: third argument `%s' treated as 1", selector);
			selected = 1.0D;
		}
		int occurrence = (int) selected;
		if (occurrence == 1) {
			return matcher.replaceFirst(repl);
		}
		StringBuffer result = new StringBuffer();
		int seen = 0;
		while (matcher.find()) {
			seen++;
			if (seen == occurrence) {
				matcher.appendReplacement(result, repl);
				break;
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * Prints a gawk-style diagnostic located at the extension call currently
	 * being dispatched, e.g. {@code gawk: script.awk:4: warning: ...}.
	 */
	private void warnAtCurrentLine(String format, Object... args) {
		String source = avm == null ? null : avm.getSourceDescription();
		String basename = source == null ? "" : new File(source).getName();
		getJrt()
				.printWarning(
						String
								.format(
										"gawk: %s:%d: warning: %s",
										basename,
										avm == null ? 0 : avm.getCurrentLineNumber(),
										String.format(format, args)));
	}

	/**
	 * Sorts and returns map keys according to a gawk predefined sort mode.
	 *
	 * @param map map whose keys should be sorted
	 * @param mode predefined sort mode
	 * @param jrt runtime used for AWK string conversion
	 * @param ignoreCase whether string comparisons ignore case
	 * @return sorted keys
	 */
	private static List<Object> sortedKeys(Map<Object, Object> map, String mode, JRT jrt, boolean ignoreCase) {
		List<SortEntry> entries = entries(map);
		Collections.sort(entries, comparator(mode, jrt, ignoreCase));
		List<Object> keys = new ArrayList<Object>();
		for (SortEntry entry : entries) {
			keys.add(entry.index);
		}
		return keys;
	}

	private Long sort(Map<Object, Object> source, Map<Object, Object> dest, Object how, boolean indicesAsValues) {
		Map<Object, Object> destination = dest == null ? source : dest;
		// gawk's defaults: value-type order for asort(), string index order
		// for asorti() (indexes are strings, so no type ranking applies)
		String defaultMode = indicesAsValues ? "@ind_str_asc" : VAL_TYPE_ASC;
		String mode = how == null ? defaultMode : effectiveSortMode(toAwkString(how), defaultMode);
		List<SortEntry> entries = entries(source);
		// @unsorted keeps the natural traversal order: no sorting at all
		if (!"@unsorted".equals(mode)) {
			Collections.sort(entries, comparator(mode, getJrt(), currentIgnoreCase()));
		}
		destination.clear();
		long idx = 1L;
		for (SortEntry entry : entries) {
			// asorti() writes indices as string values, as in gawk: the
			// internal key object may be a Long for numeric-looking indexes
			Object value = indicesAsValues ? getJrt().toAwkString(entry.index) : entry.value;
			destination.put(Long.valueOf(idx++), value);
		}
		return Long.valueOf(entries.size());
	}

	private boolean currentIgnoreCase() {
		return getJrt().isIgnoreCase();
	}

	private static List<SortEntry> entries(Map<Object, Object> map) {
		List<SortEntry> entries = new ArrayList<SortEntry>();
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			entries.add(new SortEntry(entry.getKey(), entry.getValue()));
		}
		return entries;
	}

	/*
	 * Callers handle @unsorted before reaching this point (no sort at all),
	 * both for asort()/asorti() and for the for-in traversal hook.
	 */
	private static Comparator<SortEntry> comparator(String effectiveMode, JRT jrt, boolean ignoreCase) {
		boolean desc = effectiveMode.endsWith("_desc");
		Comparator<SortEntry> comparator;
		/*
		 * Gawk's predefined orderings first choose whether indexes or values are
		 * compared, then choose numeric, string, or type-aware comparison. Arrays
		 * are kept in a separate group because gawk does not stringify subarrays
		 * during type-aware sorting. Unknown modes are fatal, as in gawk;
		 * function-name comparators are not supported.
		 */
		switch (effectiveMode) {
		case "@ind_num_asc":
		case "@ind_num_desc":
			comparator = (left, right) -> compareNumericThenText(left.index, right.index, jrt, ignoreCase);
			break;
		case "@ind_str_asc":
		case "@ind_str_desc":
			comparator = (left, right) -> compareStrings(left.index, right.index, jrt, ignoreCase);
			break;
		case "@ind_type_asc":
		case "@ind_type_desc":
			comparator = (left, right) -> compareByTypeThenValue(left.index, right.index, jrt, ignoreCase);
			break;
		case "@val_num_asc":
		case "@val_num_desc":
			comparator = (left, right) -> compareNumericThenText(left.value, right.value, jrt, ignoreCase);
			break;
		case "@val_str_asc":
		case "@val_str_desc":
			comparator = (left, right) -> compareStrings(left.value, right.value, jrt, ignoreCase);
			break;
		case "@val_type_asc":
		case "@val_type_desc":
			comparator = (left, right) -> compareByTypeThenValue(left.value, right.value, jrt, ignoreCase);
			break;
		default:
			throw new IllegalAwkArgumentException("Invalid sort comparison mode '" + effectiveMode + "'");
		}
		return desc ? comparator.reversed() : comparator;
	}

	/*
	 * The comparators below implement gawk's predefined sort orderings
	 * (@ind_num_*, @val_str_*, @val_type_*, ...). They cannot reuse
	 * JRT.compare2(): that method implements AWK's relational operators
	 * (boolean outcome, strnum coercion rules), while these need a three-way
	 * ordering that first RANKS values by gawk type (numbers < strings <
	 * subarrays, in mode-specific order) and then compares within the rank
	 * using a comparison FORCED by the mode (numeric or string), honoring
	 * IGNORECASE for strings.
	 */

	/**
	 * Type-aware ordering used by the {@code @..._type_...} modes and as gawk's
	 * default: numbers and strnums first (compared numerically), then strings
	 * (compared as text), then subarrays (mutually unordered).
	 */
	private static int compareByTypeThenValue(Object left, Object right, JRT jrt, boolean ignoreCase) {
		int leftRank = typeRank(left);
		int rightRank = typeRank(right);
		if (leftRank != rightRank) {
			return Integer.compare(leftRank, rightRank);
		}
		if (left instanceof Map && right instanceof Map) {
			return 0;
		}
		if (leftRank == 0) {
			return compareNumbers(left, right);
		}
		return compareStrings(left, right, jrt, ignoreCase);
	}

	/** Rank for type-aware ordering: 0 = number/strnum, 1 = string, 2 = subarray. */
	private static int typeRank(Object value) {
		if (value instanceof Number || isStrnum(value)) {
			return 0;
		}
		if (value instanceof Map) {
			return 2;
		}
		return 1;
	}

	/** Numeric three-way comparison; subarrays sort as 0 so ranking decides first. */
	private static int compareNumbers(Object left, Object right) {
		return Double.compare(numericSortValue(left), numericSortValue(right));
	}

	private static double numericSortValue(Object value) {
		return value instanceof Map ? 0.0D : JRT.toDouble(value);
	}

	/**
	 * Ordering for the {@code @..._num_...} modes: gawk coerces every scalar to
	 * a number (non-numeric strings count as 0), breaks numeric ties with a
	 * string comparison, and sorts subarrays last.
	 */
	private static int compareNumericThenText(Object left, Object right, JRT jrt, boolean ignoreCase) {
		if (left instanceof Map || right instanceof Map) {
			if (left instanceof Map && right instanceof Map) {
				return 0;
			}
			return left instanceof Map ? 1 : -1;
		}
		int numeric = compareNumbers(left, right);
		if (numeric != 0) {
			return numeric;
		}
		return compareStrings(left, right, jrt, ignoreCase);
	}

	/**
	 * Text three-way comparison through {@code toAwkString} (CONVFMT/locale),
	 * folding case when {@code IGNORECASE} is set; subarrays sort last.
	 */
	private static int compareStrings(Object left, Object right, JRT jrt, boolean ignoreCase) {
		if (left instanceof Map || right instanceof Map) {
			if (left instanceof Map && right instanceof Map) {
				return 0;
			}
			return left instanceof Map ? 1 : -1;
		}
		String leftString = jrt.toAwkString(left);
		String rightString = jrt.toAwkString(right);
		if (ignoreCase) {
			leftString = leftString.toLowerCase(java.util.Locale.ROOT);
			rightString = rightString.toLowerCase(java.util.Locale.ROOT);
		}
		return leftString.compareTo(rightString);
	}

	private static String typeOf(Object value) {
		if (value == null || value instanceof UntypedObject) {
			return "untyped";
		}
		if (value instanceof Map) {
			return "array";
		}
		if (value instanceof GawkBool) {
			return "number|bool";
		}
		if (value instanceof Number) {
			return "number";
		}
		if (value instanceof Pattern) {
			return "regexp";
		}
		if (value instanceof UninitializedObject) {
			return "unassigned";
		}
		return isStrnum(value) ? "strnum" : "string";
	}

	private static boolean isStrnum(Object value) {
		return value instanceof StrNum && ((StrNum) value).isNumber();
	}

	private static String arrayType(Map<?, ?> map) {
		if (map.isEmpty()) {
			return "null";
		}
		boolean allNonNegativeIntegral = true;
		for (Object key : map.keySet()) {
			if (!(key instanceof Number)) {
				return "str";
			}
			long longValue = ((Number) key).longValue();
			double doubleValue = ((Number) key).doubleValue();
			if (Double.compare(doubleValue, (double) longValue) != 0) {
				return "str";
			}
			if (longValue < 0) {
				allNonNegativeIntegral = false;
			}
		}
		return allNonNegativeIntegral ? "cint" : "int";
	}
}
