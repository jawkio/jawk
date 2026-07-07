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

/**
 * Numeric value carrying gawk's boolean type attribute.
 */
public final class GawkBool extends Number {

	private static final long serialVersionUID = 1L;
	private final boolean value;

	/**
	 * Creates a gawk boolean-number value.
	 *
	 * @param valueParam boolean value
	 */
	public GawkBool(boolean valueParam) {
		this.value = valueParam;
	}

	/**
	 * Returns the boolean value.
	 *
	 * @return boolean value
	 */
	public boolean booleanValue() {
		return value;
	}

	/** {@inheritDoc} */
	@Override
	public int intValue() {
		return value ? 1 : 0;
	}

	/** {@inheritDoc} */
	@Override
	public long longValue() {
		return value ? 1L : 0L;
	}

	/** {@inheritDoc} */
	@Override
	public float floatValue() {
		return value ? 1.0F : 0.0F;
	}

	/** {@inheritDoc} */
	@Override
	public double doubleValue() {
		return value ? 1.0D : 0.0D;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return value ? "1" : "0";
	}
}
