package org.metricshub.jawk.util;

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

import java.io.InputStream;
import java.io.PrintStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.metricshub.jawk.jrt.InputSource;

/**
 * A simple container for the parameters of a single AWK invocation.
 * These values have defaults.
 * These defaults may be changed through command line arguments,
 * or when invoking Jawk programmatically, from within Java code.
 *
 * @author Danny Daglas
 */
public class AwkSettings {

	/**
	 * Shared immutable settings instance representing the default configuration.
	 */
	public static final AwkSettings DEFAULT_SETTINGS = new ImmutableAwkSettings();

	/**
	 * Where input is read from.
	 * By default, this is {@link System#in}.
	 */
	private InputStream input = System.in;

	/**
	 * Optional structured input provider. When non-null, this source is consumed
	 * instead of {@link #input}.
	 */
	private InputSource inputSource;

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 */
	private Map<String, Object> variables = new HashMap<String, Object>();

	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 */
	private List<String> nameValueOrFileNames = new ArrayList<String>();

	/**
	 * Initial Field Separator (FS) value.
	 * <code>null</code> means the default FS value.
	 */
	private String fieldSeparator = null;

	/**
	 * Whether to maintain array keys in sorted order;
	 * <code>false</code> by default.
	 */
	private boolean useSortedArrayKeys = false;

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 */
	private boolean catchIllegalFormatExceptions = true;

	/**
	 * Output stream;
	 * <code>System.out</code> by default,
	 * which means we will print to stdout by default
	 */
	private PrintStream outputStream = System.out;

	/**
	 * Locale for the output of numbers
	 * <code>US-English</code> by default.
	 */
	private Locale locale = Locale.US;

	/**
	 * Default value for RS, when not set specifically by the AWK script
	 */
	private String defaultRS = System.getProperty("line.separator", "\n");

	/**
	 * Default value for ORS, when not set specifically by the AWK script
	 */
	private String defaultORS = System.getProperty("line.separator", "\n");

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
		desc.append("inputSource = ").append(getInputSource()).append(newLine);
		desc.append("nameValueOrFileNames = ").append(getNameValueOrFileNames()).append(newLine);
		desc.append("fieldSeparator = ").append(getFieldSeparator()).append(newLine);
		desc.append("useSortedArrayKeys = ").append(isUseSortedArrayKeys()).append(newLine);
		desc.append("catchIllegalFormatExceptions = ").append(isCatchIllegalFormatExceptions()).append(newLine);

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
		if (isCatchIllegalFormatExceptions()) {
			extensions.append(", IllegalFormatExceptions NOT trapped");
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
		assert equalsIdx >= 0;
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
	 * Where input is read from.
	 * By default, this is {@link java.lang.System#in}.
	 *
	 * @return the input
	 */
	public InputStream getInput() {
		return input;
	}

	/**
	 * Where input is read from.
	 * By default, this is {@link java.lang.System#in}.
	 *
	 * @param input the input to set
	 */
	public void setInput(InputStream input) {
		this.input = Objects.requireNonNull(input, "input");
	}

	/**
	 * Returns the optional structured input source consumed by the runtime.
	 * When non-null, this source takes precedence over {@link #getInput()}.
	 *
	 * @return configured {@link InputSource}, or {@code null} when disabled
	 */
	public InputSource getInputSource() {
		return inputSource;
	}

	/**
	 * Sets an optional structured input source for the runtime. When non-null,
	 * this source takes precedence over {@link #setInput(InputStream)}.
	 *
	 * @param inputSource source to consume, or {@code null} to disable
	 */
	public void setInputSource(InputSource inputSource) {
		this.inputSource = inputSource;
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 *
	 * @return the variables
	 */
	public Map<String, Object> getVariables() {
		return new HashMap<String, Object>(variables);
	}

	/**
	 * Contains variable assignments which are applied prior to
	 * executing the script (-v assignments).
	 * The values may be of type <code>Integer</code>,
	 * <code>Double</code> or <code>String</code>.
	 *
	 * @param variables the variables to set
	 */
	public void setVariables(Map<String, Object> variables) {
		this.variables = new HashMap<String, Object>(variables);
	}

	/**
	 * Put or replace a variable entry.
	 *
	 * @param name Variable name
	 * @param value Variable value
	 */
	public void putVariable(String name, Object value) {
		variables.put(name, value);
	}

	/**
	 * Contains name=value or filename entries.
	 * Order is important, which is why name=value and filenames
	 * are listed in the same List container.
	 *
	 * @return the nameValueOrFileNames
	 */
	public List<String> getNameValueOrFileNames() {
		return new ArrayList<String>(nameValueOrFileNames);
	}

	/**
	 * Add a name=value or filename entry.
	 *
	 * @param entry entry to add
	 */
	public void addNameValueOrFileName(String entry) {
		nameValueOrFileNames.add(entry);
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
	}

	/**
	 * Output stream;
	 * <code>System.out</code> by default,
	 * which means we will print to stdout by default
	 *
	 * @return the output stream
	 */
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "OutputStream reference is intentionally shared so callers can control output.")
	public PrintStream getOutputStream() {
		return outputStream;
	}

	/**
	 * Sets the OutputStream to print to (instead of System.out by default)
	 *
	 * @param pOutputStream OutputStream to use for print statements
	 */
	public void setOutputStream(PrintStream pOutputStream) {
		outputStream = Objects.requireNonNull(pOutputStream, "outputStream");
	}

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 *
	 * @return the catchIllegalFormatExceptions
	 */
	public boolean isCatchIllegalFormatExceptions() {
		return catchIllegalFormatExceptions;
	}

	/**
	 * Whether to trap <code>IllegalFormatExceptions</code>
	 * for <code>[s]printf</code>;
	 * <code>true</code> by default.
	 *
	 * @param catchIllegalFormatExceptions the catchIllegalFormatExceptions to set
	 */
	public void setCatchIllegalFormatExceptions(boolean catchIllegalFormatExceptions) {
		this.catchIllegalFormatExceptions = catchIllegalFormatExceptions;
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
	 * Sets the Locale for outputting numbers
	 *
	 * @param pLocale The locale to be used (e.g.: <code>Locale.US</code>)
	 */
	public void setLocale(Locale pLocale) {
		locale = pLocale;
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
	}

	/**
	 * <p>
	 * Getter for the field <code>defaultORS</code>.
	 * </p>
	 *
	 * @return the default ORS, when not set by the AWK script
	 */
	public String getDefaultORS() {
		return defaultORS;
	}

	/**
	 * Sets the default ORS, when not set by the AWK script
	 *
	 * @param ors The string that separates output records (with the print statement)
	 */
	public void setDefaultORS(String ors) {
		defaultORS = Objects.requireNonNull(ors, "defaultORS");
	}

	private static final class ImmutableAwkSettings extends AwkSettings {

		private ImmutableAwkSettings() {
			super();
		}

		@Override
		public void setInput(InputStream input) {
			throw unsupported();
		}

		@Override
		public void setInputSource(InputSource inputSource) {
			throw unsupported();
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
		public void addNameValueOrFileName(String entry) {
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
		public void setOutputStream(PrintStream pOutputStream) {
			throw unsupported();
		}

		@Override
		public void setCatchIllegalFormatExceptions(boolean catchIllegalFormatExceptions) {
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

		@Override
		public void setDefaultORS(String ors) {
			throw unsupported();
		}

		private UnsupportedOperationException unsupported() {
			return new UnsupportedOperationException("DEFAULT_SETTINGS is immutable");
		}
	}
}
