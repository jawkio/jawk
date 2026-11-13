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

/**
 * Marks an extension function parameter that receives Jawk's raw runtime
 * values instead of plain Java values.
 * <p>
 * By default, extension functions receive plain Java values: an
 * input-derived numeric string is passed as its {@code String} text, and an
 * array as a {@link java.util.Map} view whose reads return plain values. With
 * this marker:
 * </p>
 * <ul>
 * <li>a scalar parameter, which must be declared as {@link Object}, receives
 * input-derived numeric strings as {@link io.jawk.jrt.StrNum} instances, so
 * that, for example, {@link io.jawk.jrt.JRT#toBoolean(Object)} can tell an
 * input {@code "0"} (false) from the string constant {@code "0"} (true)</li>
 * <li>a {@link JawkAssocArray} parameter receives the
 * {@link io.jawk.jrt.AssocArray} itself, with its values exactly as stored,
 * and may be declared as {@link io.jawk.jrt.AssocArray}</li>
 * </ul>
 * <p>
 * The argument is evaluated like any other argument. To inspect untyped
 * variables without assigning them, use {@link JawkIntrospect}.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface JawkRawValue {}
