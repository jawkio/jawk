package org.metricshub.jawk.jrt;

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
 * Tracks whether we are within a range defined by a pair of condition:
 * <ul>
 * <li>startCondition
 * <li>endCondition
 * </ul>
 */
public class ConditionPair {

	private boolean within = false;

	/**
	 * <p>
	 * Constructor for ConditionPair.
	 * </p>
	 */
	public ConditionPair() {}

	/**
	 * Update the status of the condition pair according to
	 * whether the begin and end conditions match or not
	 *
	 * @param startMatches True if the start condition is true, which means
	 *        we're entering the range
	 * @param endMatches True if the end condition is true, which means
	 *        we're leaving the range
	 * @return wether we're within the range
	 */
	public boolean update(boolean startMatches, boolean endMatches) {
		if (startMatches) {
			within = true;
		}

		boolean result = within;

		if (endMatches) {
			within = false;
		}

		return result;
	}
}
