package io.jawk.intermediate;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The standard AWK built-in functions, the single source of truth shared by
 * the parser (recognition and dispatch) and the runtime (the {@code FUNCTAB}
 * listing). Each constant carries the function name as it appears in AWK
 * source code, which may differ from the constant name itself (e.g.
 * {@link #INT} for the <code>int</code> function). {@code print},
 * {@code printf}, and {@code getline} are statements, not functions, so they
 * are not listed, as in gawk.
 */
public enum BuiltinFunction {

	/** The {@code atan2} function. */
	ATAN2("atan2"),
	/** The {@code close} function. */
	CLOSE("close"),
	/** The {@code cos} function. */
	COS("cos"),
	/** The {@code exp} function. */
	EXP("exp"),
	/** The {@code gsub} function. */
	GSUB("gsub"),
	/** The {@code index} function. */
	INDEX("index"),
	/** The {@code int} function. */
	INT("int"),
	/** The {@code length} function. */
	LENGTH("length"),
	/** The {@code log} function. */
	LOG("log"),
	/** The {@code match} function. */
	MATCH("match"),
	/** The {@code rand} function. */
	RAND("rand"),
	/** The {@code sin} function. */
	SIN("sin"),
	/** The {@code split} function. */
	SPLIT("split"),
	/** The {@code sprintf} function. */
	SPRINTF("sprintf"),
	/** The {@code sqrt} function. */
	SQRT("sqrt"),
	/** The {@code srand} function. */
	SRAND("srand"),
	/** The {@code sub} function. */
	SUB("sub"),
	/** The {@code substr} function. */
	SUBSTR("substr"),
	/** The {@code system} function. */
	SYSTEM("system"),
	/** The {@code tolower} function. */
	TOLOWER("tolower"),
	/** The {@code toupper} function. */
	TOUPPER("toupper");

	/**
	 * A mapping of built-in function names to their enum constants, for
	 * name-based lookup.
	 */
	private static final Map<String, BuiltinFunction> BY_NAME = new HashMap<String, BuiltinFunction>();

	/** Names of all built-in functions, in declaration order. */
	private static final Set<String> NAMES;

	static {
		Set<String> names = new LinkedHashSet<String>();
		for (BuiltinFunction function : values()) {
			BY_NAME.put(function.awkName, function);
			names.add(function.awkName);
		}
		NAMES = Collections.unmodifiableSet(names);
	}

	/**
	 * The name of the function as it appears in AWK source code.
	 */
	private final String awkName;

	BuiltinFunction(String awkNameParam) {
		this.awkName = awkNameParam;
	}

	/**
	 * Returns the name of the function as it appears in AWK source code.
	 *
	 * @return the AWK-visible function name
	 */
	public String getAwkName() {
		return awkName;
	}

	/**
	 * Resolves an AWK function name to its enum constant.
	 *
	 * @param name the function name as it appears in AWK source code
	 * @return the matching constant, or <code>null</code> if the name does not
	 *         denote a built-in function
	 */
	public static BuiltinFunction of(String name) {
		return BY_NAME.get(name);
	}

	/**
	 * Returns the names of all standard built-in functions, as they appear in
	 * AWK source code and in gawk's {@code FUNCTAB}.
	 *
	 * @return unmodifiable set of function names, in declaration order
	 */
	public static Set<String> names() {
		return NAMES;
	}
}
