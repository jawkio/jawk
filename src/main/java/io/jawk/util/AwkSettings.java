package io.jawk.util;

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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import io.jawk.Awk;

/**
 * Reusable behavioral configuration for the Jawk engine.
 * <p>
 * Instances hold settings that control how the AWK interpreter behaves
 * (field separator, locale, sorted keys, initial variables, and default
 * record separator) but do <em>not</em> carry per-execution state such as
 * input sources, filename arguments, or output destinations. This
 * separation allows a single {@code AwkSettings} object to be shared
 * across many invocations of {@link io.jawk.Awk#eval},
 * {@link io.jawk.Awk#script}, or {@link io.jawk.Awk#createAvm()}.
 * </p>
 * <p>
 * Output is configured on the execution builder returned by
 * {@link io.jawk.Awk#script(String)} or {@link io.jawk.Awk#script(io.jawk.AwkProgram)}
 * and is therefore always per-execution.
 * </p>
 *
 * @author Danny Daglas
 */
public class AwkSettings {

	/**
	 * Shared immutable settings instance representing the default configuration.
	 */
	public static final AwkSettings DEFAULT_SETTINGS = new ImmutableAwkSettings();

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code>, <code>String</code>,
	 * {@link io.jawk.jrt.AssocArray} (for array variables),
	 * or any {@link java.util.Map} that Jawk exposes directly to the script.
	 * <p>
	 * When a {@link java.util.Map} is provided, the Jawk runtime may mutate it
	 * during execution. Callers must therefore supply a mutable map
	 * implementation. Numeric indices written by the runtime into such maps use
	 * {@link java.lang.Long} keys (for example, <code>0L</code>,
	 * <code>1L</code>, ...).
	 * </p>
	 */
	private final Map<String, Object> variables = new HashMap<String, Object>();

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 */
	private volatile String fieldSeparator = null;

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 */
	private volatile boolean useSortedArrayKeys = false;

	/**
	 * Locale for the output of numbers
	 * <code>US-English</code> by default.
	 */
	private volatile Locale locale = Locale.US;

	/**
	 * Default value for RS, when not set specifically by the AWK script.
	 * Defaults to {@link Awk#DEFAULT_RS} per POSIX. Platform-specific
	 * end-of-line handling is the responsibility of the input source.
	 */
	private volatile String defaultRS = Awk.DEFAULT_RS;

	/**
	 * Monotonically increasing counter incremented whenever the settings change.
	 * It allows callers that cache derived runtime state to detect when a new
	 * snapshot must be built.
	 */
	private final AtomicLong modificationCount = new AtomicLong();

	/**
	 * <p>
	 * toDescriptionString.
	 * </p>
	 *
	 * @return a human readable representation of the parameters values.
	 */
	public String toDescriptionString() {
		StringBuilder desc = new StringBuilder();

		final char newLine = '\n';

		desc.append("variables = ").append(getVariables()).append(newLine);
		desc.append("fieldSeparator = ").append(getFieldSeparator()).append(newLine);
		desc.append("useSortedArrayKeys = ").append(isUseSortedArrayKeys()).append(newLine);
		return desc.toString();
	}

	/**
	 * Provides a description of extensions that are enabled/disabled.
	 * The default compiler implementation uses this method
	 * to describe extensions which are compiled into the script.
	 * The description is then provided to the user within the usage.
	 *
	 * @return A description of the extensions which are enabled/disabled.
	 */
	public String toExtensionDescription() {
		StringBuilder extensions = new StringBuilder();

		if (isUseSortedArrayKeys()) {
			extensions.append(", associative array keys are sorted");
		}
		if (extensions.length() > 0) {
			return "{extensions: " + extensions.substring(2) + "}";
		} else {
			return "{no compiled extensions utilized}";
		}
	}

	@SuppressWarnings("unused")
	private void addInitialVariable(String keyValue) {
		int equalsIdx = keyValue.indexOf('=');
		String name = keyValue.substring(0, equalsIdx);
		String valueString = keyValue.substring(equalsIdx + 1);
		Object value;
		// deduce type
		try {
			value = Integer.parseInt(valueString);
		} catch (NumberFormatException nfe) {
			try {
				value = Double.parseDouble(valueString);
			} catch (NumberFormatException nfe2) {
				value = valueString;
			}
		}
		// note: can overwrite previously defined variables
		putVariable(name, value);
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code>, <code>String</code>,
	 * {@link io.jawk.jrt.AssocArray} (for array variables),
	 * or any {@link java.util.Map} that Jawk exposes directly to the script.
	 *
	 * @return the variables
	 */
	public Map<String, Object> getVariables() {
		synchronized (variables) {
			return new HashMap<String, Object>(variables);
		}
	}

	/**
	 * Returns the number of explicit mutations applied to this settings
	 * instance.
	 * <p>
	 * The value is intended for cache invalidation only; it has no behavioral
	 * meaning other than changing whenever one of the configuration mutators is
	 * called.
	 * </p>
	 *
	 * @return the current modification counter
	 */
	public long getModificationCount() {
		return modificationCount.get();
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code>, <code>String</code>,
	 * {@link io.jawk.jrt.AssocArray} (for array variables),
	 * or any {@link java.util.Map} that Jawk exposes directly to the script.
	 *
	 * @param variables the variables to set
	 */
	public void setVariables(Map<String, Object> variables) {
		synchronized (this.variables) {
			this.variables.clear();
			this.variables.putAll(variables);
		}
		markModified();
	}

	/**
	 * Put or replace a variable entry.
	 *
	 * @param name Variable name
	 * @param value Variable value
	 */
	public void putVariable(String name, Object value) {
		synchronized (variables) {
			variables.put(name, value);
		}
		markModified();
	}

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 *
	 * @return the fieldSeparator
	 */
	public String getFieldSeparator() {
		return fieldSeparator;
	}

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 *
	 * @param fieldSeparator the fieldSeparator to set
	 */
	public void setFieldSeparator(String fieldSeparator) {
		this.fieldSeparator = fieldSeparator;
		markModified();
	}

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 *
	 * @return the useSortedArrayKeys
	 */
	public boolean isUseSortedArrayKeys() {
		return useSortedArrayKeys;
	}

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 *
	 * @param useSortedArrayKeys the useSortedArrayKeys to set
	 */
	public void setUseSortedArrayKeys(boolean useSortedArrayKeys) {
		this.useSortedArrayKeys = useSortedArrayKeys;
		markModified();
	}

	/**
	 * <p>
	 * Getter for the field <code>locale</code>.
	 * </p>
	 *
	 * @return the Locale that will be used for outputting numbers
	 */
	public Locale getLocale() {
		return locale;
	}

	/**
	 * Sets the Locale for outputting numbers.
	 *
	 * @param pLocale The locale to be used (e.g.: <code>Locale.US</code>)
	 */
	public void setLocale(Locale pLocale) {
		locale = pLocale == null ? Locale.US : pLocale;
		markModified();
	}

	/**
	 * <p>
	 * Getter for the field <code>defaultRS</code>.
	 * </p>
	 *
	 * @return the default RS, when not set by the AWK script
	 */
	public String getDefaultRS() {
		return defaultRS;
	}

	/**
	 * Sets the default RS, when not set by the AWK script
	 *
	 * @param rs The regular expression that separates records
	 */
	public void setDefaultRS(String rs) {
		defaultRS = Objects.requireNonNull(rs, "defaultRS");
		markModified();
	}

	protected final void markModified() {
		modificationCount.incrementAndGet();
	}

	private static final class ImmutableAwkSettings extends AwkSettings {

		private ImmutableAwkSettings() {
			super();
		}

		@Override
		public void setVariables(Map<String, Object> variables) {
			throw unsupported();
		}

		@Override
		public void putVariable(String name, Object value) {
			throw unsupported();
		}

		@Override
		public void setFieldSeparator(String fieldSeparator) {
			throw unsupported();
		}

		@Override
		public void setUseSortedArrayKeys(boolean useSortedArrayKeys) {
			throw unsupported();
		}

		@Override
		public void setLocale(Locale pLocale) {
			throw unsupported();
		}

		@Override
		public void setDefaultRS(String rs) {
			throw unsupported();
		}

		private UnsupportedOperationException unsupported() {
			return new UnsupportedOperationException("DEFAULT_SETTINGS is immutable");
		}
	}
}
