package io.jawk;

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

import java.util.List;

/**
 * Shared model for compatibility cases consumed by {@link GawkCompatibilityIT}.
 * Implementations may come from gawk's generated {@code Maketests} metadata or
 * from a curated manifest of handwritten {@code Makefile.am} rules.
 */
interface GawkCompatibilityCase {

	/**
	 * Returns the stable gawk target name for this compatibility case.
	 *
	 * @return case name used in reporting and expected-output lookup
	 */
	String name();

	/**
	 * Returns the raw command-line arguments that must appear before any
	 * {@code -f} script arguments.
	 *
	 * @return immutable list of CLI arguments
	 */
	List<String> arguments();

	/**
	 * Returns the staged script file names that should be passed through
	 * {@code -f} in the declared order.
	 *
	 * @return immutable list of staged script file names
	 */
	List<String> scriptFileNames();

	/**
	 * Returns the operand list that should be supplied after all script
	 * arguments.
	 *
	 * @return immutable list of AWK operands
	 */
	List<String> operands();

	/**
	 * Returns the staged stdin fixture file name, if the case redirects stdin.
	 *
	 * @return fixture file name or {@code null} when stdin is unused
	 */
	String stdinFileName();

	/**
	 * Returns the staged expected-output fixture file name.
	 *
	 * @return expected-output file name
	 */
	String expectedFileName();

	/**
	 * Indicates whether the compatibility harness requires an explicit skip
	 * manifest entry before the case may appear in the suite.
	 *
	 * @return {@code true} when the case must be listed in {@code skips.properties}
	 */
	boolean requiresExplicitSkip();
}
