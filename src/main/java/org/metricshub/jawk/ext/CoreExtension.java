package org.metricshub.jawk.ext;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import org.metricshub.jawk.jrt.AssocArray;
import org.metricshub.jawk.jrt.AwkRuntimeException;
import org.metricshub.jawk.jrt.BlockObject;
import org.metricshub.jawk.jrt.IllegalAwkArgumentException;
import org.metricshub.jawk.jrt.JRT;
import org.metricshub.jawk.jrt.VariableManager;
import org.metricshub.jawk.ext.annotations.JawkAssocArray;
import org.metricshub.jawk.ext.annotations.JawkFunction;

/**
 * Extensions which make developing in Jawk and
 * interfacing other extensions with Jawk
 * much easier.
 * <p>
 * The extension functions which are available are as follows:
 * <ul>
 * <li><strong>Array</strong> - <code>Array(array,1,3,5,7,9)</code><br>
 * Inserts elements into an associative array whose keys
 * are ordered non-negative integers, and the values
 * are the arguments themselves. The first argument is
 * the associative array itself.
 * <li><strong>Map/HashMap/TreeMap</strong> - <code>Map(map,k1,v1,k2,v2,...,kN,vN)</code>,
 * or <code>Map(k1,v1,k2,v2,...,kN,vN)</code>.<br>
 * Build an associative array with its keys/values as
 * parameters. The odd parameter count version takes
 * the map name as the first parameter, while the even
 * parameter count version returns an anonymous associative
 * array for the purposes of providing a map by function
 * call parameter.<br>
 * Map/HashMap configures the associative array as a hash map,
 * and TreeMap as an ordered map.
 * <li><strong>MapUnion</strong> - <code>MapUnion(map,k1,v1,k2,v2,...,kN,vN)</code><br>
 * Similar to Map, except that map is not cleared prior
 * to populating it with key/value pairs from the
 * parameter list.
 * <li><strong>MapCopy</strong> - <code>cnt = MapCopy(aaTarget, aaSource)</code><br>
 * Clears the target associative array and copies the
 * contents of the source associative array to the
 * target associative array.
 * <li><strong>TypeOf</strong> - <code>typestring = TypeOf(item)</code><br>
 * Returns one of the following depending on the argument:
 * <ul>
 * <li>"String"
 * <li>"Integer"
 * <li>"AssocArray"
 * <li>"Reference" (see below)
 * </ul>
 * <li><strong>String</strong> - <code>str = String(3)</code><br>
 * Converts its argument to a String for completeness/normalization.
 * <li><strong>Double</strong> - <code>dbl = Double(3)</code><br>
 * Converts its argument to a Double for completeness/normalization.
 * <li><strong>Halt</strong> - <code>Halt()</code><br>
 * Similar to exit(), except that END blocks are
 * not executed if Halt() called before END
 * block processing.
 * <li><strong>Timeout</strong> - <code>r = Timeout(300)</code><br>
 * A blocking function which waits N milliseconds
 * before unblocking (continuing). This is useful in scripts which
 * employ blocking, but occasionally needs to break out
 * of the block to perform some calculation, polling, etc.
 * <li><strong>Throw</strong> - <code>Throw("this is an awkruntimeexception")</code><br>
 * Throws an AwkRuntimeException from within the script.
 * <li><strong>Version</strong> - <code>print Version(aa)</code><br>
 * Prints the version of the Java class which represents the parameter.
 * <li><strong>Date</strong> - <code>str = Date()</code><br>
 * Similar to the Java equivalent : str = new Date().toString();
 * <li><strong>FileExists</strong> - <code>b = FileExists("/a/b/c")</code><br>
 * Returns 0 if the file doesn't exist, 1 otherwise.
 * <li><strong>NewRef[erence]/Dereference/DeRef/Unreference/UnRef/etc.</strong> -
 * Reference Management Functions. These are described in detail below.
 * </ul>
 * <h2>Reference Management</h2>
 * AWK's memory model provides only 4 types of variables
 * for use within AWK scripts:
 * <ul>
 * <li>Integer
 * <li>Double
 * <li>String
 * <li>Associative Array
 * </ul>
 * Variables can hold any of these types. However, unlike
 * for scalar types (integer/double/string), AWK applies
 * the following restrictions with regard to associative
 * arrays:
 * <ul>
 * <li>Associative array assignments (i.e., assocarray1 = associarray2)
 * are prohibited.
 * <li>Functions cannot return associative arrays.
 * </ul>
 * These restrictions, while sufficient for AWK, are detrimental
 * to extensions because associative arrays are excellent vehicles
 * for configuration and return values for user extensions.
 * Plus, associative arrays can be overriden, which can be used
 * to enforce type safety within user extensions. Unfortunately, the
 * memory model restrictions make using associative arrays in this
 * capacity very difficult.
 * <p>
 * We attempt to alleviate these difficulties by adding references
 * to Jawk via the CoreExtension module.
 * References convert associative arrays into
 * unique strings called <strong>reference handles</strong>.
 * Since reference handles are strings, they can be
 * assigned and returned via AWK functions without restriction.
 * And, reference handles are then used by other reference extension
 * functions to perform common associative array operations, such as
 * associative array cell lookup and assignment, key existence
 * check, and key iteration.
 * <p>
 * The reference model functions are explained below:
 * <ul>
 * <li><strong>NewRef / NewReference</strong> - <code>handle = NewRef(assocarray)</code><br>
 * Store map into reference cache. Return the unique string handle
 * for this associative array.
 * <li><strong>DeRef / Dereference</strong> - <code>val = DeRef(handle, key)</code><br>
 * Return the cell value of the associative array referenced by the key.
 * In other words:
 * <blockquote>
 *
 * <pre>
 * return assocarray[key]
 * </pre>
 *
 * </blockquote>
 * <li><strong>UnRef / Unreference</strong> - <code>UnRef(handle)</code><br>
 * Eliminate the reference occupied by the reference cache.
 * <li><strong>InRef</strong> - <code>while(key = InRef(handle)) ...</code><br>
 * Iterate through the key-set of the associative array
 * referred to by handle in the reference cache.
 * This is similar to:
 * <blockquote>
 *
 * <pre>
 * for (key in assocarray)
 * 	...
 * </pre>
 *
 * </blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <br>
 * <strong>Warning:</strong> unlike the IN keyword, InRef
 * will maintain state regardless of scope. That is,
 * if one were to break; out of the while loop above,
 * the next call to InRef() will be the next anticipated
 * element of the <code>assoc</code> array.
 * <li><strong>IsInRef</strong> - <code>b = IsInRef(handle, key)</code><br>
 * Checks whether the associative array in the reference cache
 * contains the key. This is similar to:
 * <blockquote>
 *
 * <pre>
 * if (key in assocarray)
 *	...
 * </pre>
 *
 * </blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <li><strong>DumpRefs</strong> - <code>DumpRefs()</code><br>
 * Dumps the reference cache to stdout.
 * </ul>
 *
 * @author Danny Daglas
 */
public class CoreExtension extends AbstractExtension implements JawkExtension {

	private static CoreExtension instance = null;
	private static final Object INSTANCE_LOCK = new Object();

	public static final CoreExtension INSTANCE = new CoreExtension();
	private int refMapIdx = 0;
	private Map<String, Object> referenceMap = new HashMap<String, Object>();
	private Map<AssocArray, Iterator<?>> iterators = new HashMap<AssocArray, Iterator<?>>();
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);
	private volatile int waitInt = 0;

	// single threaded, so one Date object (unsynchronized) will do
	private final Date dateObj = new Date();
	private final SimpleDateFormat dateFormat = new SimpleDateFormat();

	private final BlockObject timeoutBlocker = new BlockObject() {
		@Override
		public String getNotifierTag() {
			return "Timeout";
		}

		@Override
		public void block() throws InterruptedException {
			synchronized (timeoutBlocker) {
				timeoutBlocker.wait(waitInt);
			}
		}
	};

	/**
	 * <p>
	 * Constructor for CoreExtension.
	 * </p>
	 */
	public CoreExtension() {
		synchronized (INSTANCE_LOCK) {
			if (instance == null) {
				instance = this;
			}
		}
	}

	@Override
	public String getExtensionName() {
		return "core";
	}

	@JawkFunction("Map")
	public Object mapFunction(Object... args) {
		return map("Map", args, AssocArray::createHash);
	}

	@JawkFunction("HashMap")
	public Object hashMapFunction(Object... args) {
		return map("HashMap", args, AssocArray::createHash);
	}

	@JawkFunction("TreeMap")
	public Object treeMapFunction(Object... args) {
		return map("TreeMap", args, AssocArray::createSorted);
	}

	@JawkFunction("MapUnion")
	public Object mapUnionFunction(Object... args) {
		return mapUnion("MapUnion", args);
	}

	@JawkFunction("MapCopy")
	public int mapCopyFunction(@JawkAssocArray AssocArray target, @JawkAssocArray AssocArray source) {
		return mapCopy(new Object[] { target, source });
	}

	@JawkFunction("Array")
	public int arrayFunction(@JawkAssocArray AssocArray target, Object... values) {
		Object[] args = new Object[values.length + 1];
		args[0] = target;
		System.arraycopy(values, 0, args, 1, values.length);
		return array("Array", args, getVm());
	}

	@JawkFunction("TypeOf")
	public String typeOfFunction(Object value) {
		return typeOf(value);
	}

	@JawkFunction("String")
	public String stringFunction(Object value) {
		return toString(value);
	}

	@JawkFunction("Double")
	public Object doubleFunction(Object value) {
		return toDouble(value);
	}

	@JawkFunction("Halt")
	public Object haltFunction(Object... args) {
		if (args.length == 0) {
			Runtime.getRuntime().halt(0);
		} else if (args.length == 1) {
			Runtime.getRuntime().halt((int) JRT.toDouble(args[0]));
		} else {
			throw new IllegalAwkArgumentException("Halt requires 0 or 1 argument, not " + args.length);
		}
		return null;
	}

	@JawkFunction("NewReference")
	public Object newReferenceFunction(Object... args) {
		return handleNewReference("NewReference", args);
	}

	@JawkFunction("NewRef")
	public Object newRefFunction(Object... args) {
		return handleNewReference("NewRef", args);
	}

	@JawkFunction("Dereference")
	public Object dereferenceFunction(Object... args) {
		return handleDereference("Dereference", args);
	}

	@JawkFunction("DeRef")
	public Object deRefFunction(Object... args) {
		return handleDereference("DeRef", args);
	}

	@JawkFunction("Unreference")
	public int unreferenceFunction(Object handle) {
		return unreference(handle);
	}

	@JawkFunction("UnRef")
	public int unRefFunction(Object handle) {
		return unreference(handle);
	}

	@JawkFunction("InRef")
	public Object inRefFunction(Object handle) {
		return inref(handle);
	}

	@JawkFunction("IsInRef")
	public int isInRefFunction(Object handle, Object key) {
		return isInRef(handle, key);
	}

	@JawkFunction("DumpRefs")
	public void dumpRefsFunction() {
		dumpRefs();
	}

	@JawkFunction("Timeout")
	public Object timeoutFunction(Object millis) {
		return timeout((int) JRT.toDouble(millis));
	}

	@JawkFunction("Throw")
	public Object throwFunction(Object... args) {
		throw new AwkRuntimeException(Arrays.toString(args));
	}

	@JawkFunction("Version")
	public String versionFunction(Object value) {
		return version(value);
	}

	@JawkFunction("Date")
	public String dateFunction(Object... args) {
		if (args.length == 0) {
			return date();
		}
		if (args.length == 1) {
			return date(toAwkString(args[0]));
		}
		throw new IllegalAwkArgumentException("Date expects 0 or 1 argument, not " + args.length);
	}

	@JawkFunction("FileExists")
	public int fileExistsFunction(Object path) {
		return fileExists(toAwkString(path));
	}

	private Object handleNewReference(String keyword, Object... args) {
		if (args.length == 1) {
			return newReference(args[0]);
		}
		if (args.length == 3) {
			return newReference(toAwkString(args[0]), args[1], args[2]);
		}
		throw new IllegalAwkArgumentException(keyword + " requires 1 or 3 arguments, not " + args.length);
	}

	private Object handleDereference(String keyword, Object... args) {
		if (args.length == 1) {
			return resolve(dereference(args[0]));
		}
		if (args.length == 2) {
			return resolve(dereference(toAwkString(args[0]), args[1]));
		}
		throw new IllegalAwkArgumentException(keyword + " requires 1 or 2 arguments, not " + args.length);
	}

	private Object resolve(Object arg) {
		Object obj = arg;
		while (true) {
			if (obj instanceof AssocArray) {
				return obj;
			}
			String argCheck = toAwkString(obj);
			if (referenceMap.get(argCheck) != null) {
				obj = referenceMap.get(argCheck);
			} else {
				return obj;
			}
		}
	}

	static String newReference(Object arg) {
		// argument must be an associative array
		if (!(arg instanceof AssocArray)) {
			throw new IllegalAwkArgumentException("NewRef[erence] requires an assoc array, not " + arg.getClass().getName());
		}

		// otherwise, set the reference and return the new key

		// get next refmapIdx
		int refIdx = instance.refMapIdx++;
		// inspect the argument
		String argStr = arg.getClass().getName();
		if (argStr.length() > 63) {
			argStr = argStr.substring(0, 60) + "...";
		}
		// build Reference (scalar) string to this argument
		String retval = "@REFERENCE@ " + refIdx + " <" + argStr + ">";
		instance.referenceMap.put(retval, arg);
		return retval;
	}

	// this version assigns an assoc array a key/value pair
	static Object newReference(String refstring, Object key, Object value) {
		AssocArray aa = (AssocArray) instance.referenceMap.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		return aa.put(key, value);
	}

	// this version assigns an object to a reference
	private Object dereference(Object arg) {
		// return the reference if the arg is a reference key
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("an assoc array cannot be a reference handle");
		} else {
			String argCheck = toAwkString(arg);
			return dereference(argCheck);
		}
	}

	// split this out for static access by other extensions
	static Object dereference(String argCheck) {
		if (instance.referenceMap.get(argCheck) != null) {
			return instance.referenceMap.get(argCheck);
		} else {
			throw new IllegalAwkArgumentException(argCheck + " not a valid reference");
		}
	}

	// this version assumes an assoc array is stored as a reference,
	// and to retrieve the stored value
	static Object dereference(String refstring, Object key) {
		AssocArray aa = (AssocArray) instance.referenceMap.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		if (!(key instanceof AssocArray)) {
			// check if key is a reference string!
			String keyCheck = instance.toAwkString(key);
			if (instance.referenceMap.get(keyCheck) != null) {
				// assume it is a reference rather than an assoc array key itself
				key = instance.referenceMap.get(keyCheck);
			}
		}
		return aa.get(key);
	}

	static int unreference(Object arg) {
		String argCheck = instance.toAwkString(arg);
		if (instance.referenceMap.get(argCheck) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + argCheck);
		}

		instance.referenceMap.remove(argCheck);
		return 1;
	}

	private Object inref(Object arg) {
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("InRef requires a Reference (string) argument, not an assoc array");
		}
		String argCheck = toAwkString(arg);
		if (referenceMap.get(argCheck) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + argCheck);
		}
		Object o = referenceMap.get(argCheck);
		if (!(o instanceof AssocArray)) {
			throw new IllegalAwkArgumentException("Reference not an assoc array. ref.class = " + o.getClass().getName());
		}

		AssocArray aa = (AssocArray) o;

		// use an inMap to keep track of existing iterators

		// Iterator<Object> iter = iterators.get(aa);
		Iterator<?> iter = iterators.get(aa);
		if (iter == null) { // assoc array during iteration causes a ConcurrentModificationException // without a new
												// Collection, modification to the //iterators.put(aa, iter = aa.keySet().iterator());
			iter = new ArrayList<Object>(aa.keySet()).iterator();
			iterators.put(aa, iter);
		}

		Object retval = null;

		if (iter.hasNext()) {
			retval = iter.next();
			if (retval instanceof String && retval.toString().equals("")) {
				throw new AwkRuntimeException("Assoc array key contains a blank string ?!");
			}
		}

		if (retval == null) {
			iterators.remove(aa);
			retval = "";
		}

		if (retval instanceof AssocArray) {
			// search if item is referred to already
			for (Map.Entry<String, Object> e : referenceMap.entrySet()) {
				if (e.getValue() == retval) {
					return e.getKey();
				}
			}
			// otherwise, return new reference to this item
			// return newReference(argCheck, retval);
			return newReference(retval);
		} else {
			return retval;
		}
	}

	private int isInRef(Object ref, Object key) {
		if (ref instanceof AssocArray) {
			throw new IllegalAwkArgumentException("Expecting a reference string for the 1st argument, not an assoc array.");
		}
		String refstring = toAwkString(ref);
		return isInRef(refstring, key);
	}

	static int isInRef(String refstring, Object key) {
		Object o = instance.referenceMap.get(refstring);
		if (o == null) {
			throw new IllegalAwkArgumentException("Invalid refstring : " + refstring);
		}
		AssocArray aa = (AssocArray) o;
		return aa.isIn(key) ? ONE : ZERO;
	}

	private void dumpRefs() {
		for (Map.Entry<String, Object> entry : referenceMap.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof AssocArray) {
				value = ((AssocArray) value).mapString();
			}
			System.out.println("REF : " + entry.getKey() + " = " + value);
		}
	}

	static String typeOf(Object arg) {
		if (arg instanceof AssocArray) {
			return "AssocArray";
		} else if (arg instanceof Integer) {
			return "Integer";
		} else if (arg instanceof Double) {
			return "Double";
		} else {
			String stringRep = instance.toAwkString(arg);
			if (instance.referenceMap.get(stringRep) != null) {
				return "Reference";
			} else {
				return "String";
			}
		}
	}

	@SuppressWarnings("unused")
	private int get(AssocArray retval, AssocArray map, Object key) {
		retval.clear();
		retval.put(0, map.get(key));
		return 1;
	}

	@SuppressWarnings("unused")
	private Object toScalar(AssocArray aa) {
		return aa.get(0);
	}

	private Object map(String keyword, Object[] args, Supplier<AssocArray> factory) {
		if (args.length % 2 == 0) {
			return subMap(args, factory);
		}
		return topLevelMap(keyword, args, false); // false = map assignment
	}

	private Object mapUnion(String keyword, Object[] args) {
		return topLevelMap(keyword, args, true); // true = map union
	}

	private int topLevelMap(String keyword, Object[] args, boolean mapUnion) {
		if (args.length == 0) {
			throw new IllegalAwkArgumentException(keyword + " requires at least one argument.");
		}
		if (!(args[0] instanceof AssocArray)) {
			throw new IllegalAwkArgumentException(
					keyword + " requires the first argument to be an associative array when assigning to it.");
		}
		AssocArray aa = (AssocArray) args[0];
		if (!mapUnion) {
			aa.clear();
		}
		int cnt = 0;
		for (int i = 1; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newReference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newReference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);

			++cnt;
		}
		return cnt;
	}

	private AssocArray subMap(Object[] args, Supplier<AssocArray> factory) {
		AssocArray aa = factory.get();
		for (int i = 0; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newReference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newReference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);
		}
		return aa;
	}

	private int array(String keyword, Object[] args, VariableManager vm) {
		if (args.length == 0 || !(args[0] instanceof AssocArray)) {
			throw new IllegalAwkArgumentException(
					keyword + " requires the first argument to be an associative array.");
		}
		AssocArray aa = (AssocArray) args[0];
		aa.clear();
		String subsep = toAwkString(vm.getSUBSEP());
		int cnt = 0;
		for (int i = 1; i < args.length; ++i) {
			Object o = args[i];
			if (o instanceof AssocArray) {
				AssocArray arr = (AssocArray) o;
				for (Map.Entry<Object, Object> entry : arr.entrySet()) {
					aa.put("" + i + subsep + entry.getKey(), entry.getValue());
				}
			} else {
				aa.put("" + i, o);
			}
			++cnt;
		}
		return cnt;
	}

	/*
	 * private AssocArray subarray(Object[] args, VariableManager vm) {
	 * AssocArray aa = new AssocArray(false);
	 * aa.clear();
	 * //aa.useLinkedHashMap();
	 * aa.useMapType(AssocArray.MT_TREE);
	 * String subsep = toAwkString(vm.getSUBSEP());
	 * int cnt = 0;
	 * for (int i = 1; i <= args.length; ++i) {
	 * Object o = args[i - 1];
	 * if (o instanceof AssocArray) {
	 * AssocArray arr = (AssocArray) o;
	 * for (Object key : arr.keySet()) {
	 * aa.put("" + i + subsep + key, arr.get(key));
	 * }
	 * } else {
	 * aa.put("" + i, o);
	 * }
	 * //aa.put(args[i], args[i+1]);
	 * ++cnt;
	 * }
	 * return aa;
	 * }
	 */

	private int mapCopy(Object[] args) {
		AssocArray aaTarget = (AssocArray) args[0];
		AssocArray aaSource = (AssocArray) args[1];
		aaTarget.clear();
		int cnt = 0;
		for (Map.Entry<Object, Object> entry : aaSource.entrySet()) {
			aaTarget.put(entry.getKey(), entry.getValue());
			++cnt;
		}
		return cnt;
	}

	private Object toDouble(Object arg) {
		if (arg instanceof AssocArray) {
			throw new IllegalArgumentException("Cannot deduce double value from an associative array.");
		}
		if (arg instanceof Number) {
			return ((Number) arg).doubleValue();
		}

		// otherwise, a string

		try {
			String str = toAwkString(arg);
			double d = Double.parseDouble(str);
			return d;
		} catch (NumberFormatException nfe) {
			return "";
		}
	}

	private static String toString(Object arg) {
		if (arg instanceof AssocArray) {
			return ((AssocArray) arg).mapString();
		} else {
			return instance.toAwkString(arg);
		}
	}

	private Object timeout(int ms) {
		if (ms <= 0) {
			throw new IllegalAwkArgumentException("Timeout requires a positive # argument, not " + ms + ".");
		}
		waitInt = ms;
		return timeoutBlocker;
	}

	private String version(Object obj) {
		if (obj instanceof AssocArray) {
			return ((AssocArray) obj).getMapVersion();
		} else {
			Class<?> cls = (Class<?>) obj.getClass();
			return cls.getPackage().getSpecificationVersion();
		}
	}

	private String date() {
		dateObj.setTime(System.currentTimeMillis());
		return dateObj.toString();
	}

	private String date(String formatString) {
		dateObj.setTime(System.currentTimeMillis());
		dateFormat.applyPattern(formatString);
		return dateFormat.format(dateObj);
	}

	private int fileExists(String path) {
		if (new File(path).exists()) {
			return ONE;
		} else {
			return ZERO;
		}
	}
}
