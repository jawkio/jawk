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
 * The AWK Variable Manager.
 * It provides getter/setter methods for global AWK variables.
 * Its purpose is to expose a variable management interface to
 * the JRT, even though the implementation is provided by
 * the AWK script at script compile-time.
 * <p>
 * The getters/setters here do not access <strong>all</strong>
 * special AWK variables, such as <code>RSTART</code>
 * and <code>ENVIRON</code>. That's because these variables
 * are not referred to within the JRT.
 *
 * @see JRT
 * @author Danny Daglas
 */
public interface VariableManager {
	/**
	 * <p>
	 * getARGC.
	 * </p>
	 *
	 * @return the contents of the ARGC variable.
	 */
	Object getARGC();

	/**
	 * <p>
	 * getARGV.
	 * </p>
	 *
	 * @return the contents of the ARGV variable.
	 */
	Object getARGV();

	/**
	 * <p>
	 * getCONVFMT.
	 * </p>
	 *
	 * @return the contents of the CONVFMT variable.
	 */
	Object getCONVFMT();

	/**
	 * <p>
	 * getFS.
	 * </p>
	 *
	 * @return the contents of the FS variable.
	 */
	Object getFS();

	/**
	 * <p>
	 * getRS.
	 * </p>
	 *
	 * @return the contents of the RS variable.
	 */
	Object getRS();

	/**
	 * <p>
	 * getOFS.
	 * </p>
	 *
	 * @return the contents of the OFS variable.
	 */
	Object getOFS();

	/**
	 * <p>
	 * getORS.
	 * </p>
	 *
	 * @return the contents of the ORS variable.
	 */
	Object getORS();

	/**
	 * <p>
	 * getSUBSEP.
	 * </p>
	 *
	 * @return the contents of the SUBSEP variable.
	 */
	Object getSUBSEP();

	/**
	 * Set the contents of the FILENAME variable.
	 *
	 * @param fileName File name
	 */
	void setFILENAME(String fileName);

	/**
	 * Set the contents of the NF variable.
	 *
	 * @param newNf Value for NF
	 */
	void setNF(Integer newNf);

	/**
	 * Increases the NR variable by 1.
	 */
	void incNR();

	/**
	 * Increases the FNR variable by 1.
	 */
	void incFNR();

	/**
	 * Resets the FNR variable to 0.
	 */
	void resetFNR();

	/**
	 * Set the contents of a user-defined AWK
	 * variable. Used when processing
	 * <em>name=value</em> command-line arguments
	 * (either via -v or via ARGV).
	 *
	 * @param name The AWK variable name.
	 * @param value The new contents of the variable.
	 */
	void assignVariable(String name, Object value);
}
