package io.jawk.ext.annotations;

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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Marks an extension function parameter that must be evaluated and passed as
 * an associative array backed by a {@link Map}. Combine with
 * {@link JawkOptional} when the AWK caller may omit the array.
 * <p>
 * The parameter must be declared as {@link Map} and receives a live view of
 * the AWK array: reads return plain Java values (an input-derived numeric
 * string as its {@code String} text, numbers as is, a nested array as another
 * such view), while writes ({@code put}, {@code remove}, {@code clear}, ...)
 * go straight to the AWK array, so the script sees them after the call.
 * Combine with {@link JawkRawValue} to receive the
 * {@link io.jawk.jrt.AssocArray} itself instead.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface JawkAssocArray {}
