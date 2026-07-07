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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jawk.backend.AVM;
import io.jawk.ext.annotations.JawkBeforeStart;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.IllegalAwkArgumentException;
import io.jawk.jrt.JRT;
import io.jawk.jrt.VariableManager;
import io.jawk.util.AwkSettings;

/**
 * Base class of various extensions.
 * <p>
 * Provides functionality common to most extensions,
 * such as VM and JRT variable management, and convenience
 * methods such as checkNumArgs() and toAwkString().
 *
 * @author Danny Daglas
 */
public abstract class AbstractExtension implements JawkExtension {

	private JRT jrt;
	private VariableManager vm;
	private AwkSettings settings;
	private Map<String, ExtensionFunction> annotatedFunctions;
	private List<Method> beforeStartMethods;

	/** {@inheritDoc} */
	@Override
	public String getExtensionName() {
		return getClass().getSimpleName();
	}

	/** {@inheritDoc} */
	@Override
	@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Extension needs direct access to runtime, VM and settings")
	public void init(VariableManager vmParam, JRT runtime, final AwkSettings conf) {
		this.vm = vmParam;
		this.jrt = runtime;
		this.settings = conf;
	}

	/**
	 * Convert a Jawk variable to a Jawk string
	 * based on the value of the CONVFMT variable.
	 *
	 * @param obj The Jawk variable to convert to a Jawk string.
	 * @return A string representation of obj after CONVFMT
	 *         has been applied.
	 */
	protected final String toAwkString(Object obj) {
		return jrt.toAwkString(obj);
	}

	/**
	 * Verifies that an exact number of arguments
	 * has been passed in by checking the length
	 * of the argument array.
	 *
	 * @param arr The arguments to check.
	 * @param expectedNum The expected number of arguments.
	 */
	protected static void checkNumArgs(Object[] arr, int expectedNum) {
		// some sanity checks on the arguments
		// (made into assertions so that
		// production code does not perform
		// these checks)

		if (arr.length != expectedNum) {
			throw new IllegalAwkArgumentException("Expecting " + expectedNum + " arg(s), got " + arr.length);
		}
	}

	/**
	 * <p>
	 * Getter for the field <code>jrt</code>.
	 * </p>
	 *
	 * @return the Runtime
	 */
	protected JRT getJrt() {
		return jrt;
	}

	/**
	 * <p>
	 * Getter for the field <code>vm</code>.
	 * </p>
	 *
	 * @return the Variable Manager
	 */
	protected VariableManager getVm() {
		return vm;
	}

	/**
	 * <p>
	 * Getter for the field <code>settings</code>.
	 * </p>
	 *
	 * @return the Settings
	 */
	protected AwkSettings getSettings() {
		return settings;
	}

	private Map<String, ExtensionFunction> getAnnotatedFunctions() {
		if (annotatedFunctions == null) {
			annotatedFunctions = Collections.unmodifiableMap(scanAnnotatedFunctions());
		}
		return annotatedFunctions;
	}

	private List<Method> getBeforeStartMethods() {
		if (beforeStartMethods == null) {
			beforeStartMethods = Collections.unmodifiableList(scanBeforeStartMethods());
		}
		return beforeStartMethods;
	}

	private Map<String, ExtensionFunction> scanAnnotatedFunctions() {
		Map<String, ExtensionFunction> discovered = new LinkedHashMap<String, ExtensionFunction>();
		Class<? extends AbstractExtension> type = getClass();
		for (Method method : type.getMethods()) {
			JawkFunction function = method.getAnnotation(JawkFunction.class);
			if (function == null) {
				continue;
			}
			String keyword = function.value();
			ExtensionFunction existing = discovered.put(keyword, new ExtensionFunction(keyword, method));
			if (existing != null) {
				throw new IllegalStateException(
						"Duplicate @JawkFunction mapping for keyword '" + keyword + "' in " + type.getName());
			}
		}
		return discovered;
	}

	private List<Method> scanBeforeStartMethods() {
		List<Method> discovered = new ArrayList<Method>();
		Class<? extends AbstractExtension> type = getClass();
		for (Method method : type.getMethods()) {
			if (!method.isAnnotationPresent(JawkBeforeStart.class)) {
				continue;
			}
			if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
				throw new IllegalStateException(
						"@" + JawkBeforeStart.class.getSimpleName()
								+ " does not support static methods: " + method.toGenericString());
			}
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (method.getReturnType() != Void.TYPE
					|| parameterTypes.length != 2
					|| parameterTypes[0] != AVM.class
					|| parameterTypes[1] != JRT.class) {
				throw new IllegalStateException(
						"@" + JawkBeforeStart.class.getSimpleName()
								+ " method must declare void method(AVM, JRT): " + method.toGenericString());
			}
			method.setAccessible(true);
			discovered.add(method);
		}
		return discovered;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, ExtensionFunction> getExtensionFunctions() {
		return getAnnotatedFunctions();
	}

	/** {@inheritDoc} */
	@Override
	public void beforeStart(AVM avm, JRT runtime) {
		for (Method method : getBeforeStartMethods()) {
			try {
				method.invoke(this, avm, runtime);
			} catch (IllegalAccessException ex) {
				throw new IllegalStateException(
						"Unable to access extension before-start method " + method.toGenericString(),
						ex);
			} catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException) {
					throw (RuntimeException) cause;
				}
				if (cause instanceof Error) {
					throw (Error) cause;
				}
				throw new IllegalStateException(
						"Extension before-start method failed: " + method.toGenericString(),
						cause);
			}
		}
	}
}
