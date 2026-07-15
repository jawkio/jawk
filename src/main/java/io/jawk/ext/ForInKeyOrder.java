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

import java.util.Collection;
import java.util.Map;

/**
 * Supplies the key traversal order used by {@code for (index in array)}
 * statements.
 * <p>
 * The interpreter itself has no opinion about iteration order: it snapshots
 * whatever collection this hook returns. Extensions that implement ordered
 * traversal (such as the gawk compatibility extension honoring
 * {@code PROCINFO["sorted_in"]}) register an instance from their
 * {@code beforeStart} hook. Implementations that have no ordering to apply
 * should return {@code array.keySet()} directly to avoid any extra copy.
 * </p>
 */
@FunctionalInterface
public interface ForInKeyOrder {

	/**
	 * Returns the keys of {@code array} in the order {@code for (index in array)}
	 * should traverse them.
	 *
	 * @param array associative array about to be iterated
	 * @return the keys in traversal order; the interpreter copies the returned
	 *         collection before iterating
	 */
	Collection<Object> order(Map<Object, Object> array);
}
