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

/**
 * Public view of a compiled AWK program.
 * <p>
 * Programs are compiled into immutable tuple streams that can be reused across
 * multiple executions. Instances are produced by {@link Awk#compile(String)}.
 * </p>
 */
public class AwkProgram extends io.jawk.intermediate.AwkTuples {

	private static final long serialVersionUID = 2L;

	/** Whether the program was compiled in POSIX mode. */
	private boolean posix;

	AwkProgram() {
		super();
	}

	/**
	 * Returns whether the program was compiled in POSIX mode, in which case
	 * {@code sub()} and {@code gsub()} follow the POSIX backslash rules in
	 * their replacement text at run time, even when the program is loaded
	 * from a file.
	 *
	 * @return {@code true} when the program was compiled in POSIX mode
	 */
	public boolean isPosix() {
		return posix;
	}

	void setPosix(boolean posix) {
		this.posix = posix;
	}
}
