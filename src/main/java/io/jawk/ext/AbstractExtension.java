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
 * <p>
 * Methods annotated with {@link JawkFunction} receive plain Java values: an
 * input-derived numeric string is passed as its {@code String} text, and an
 * {@link io.jawk.ext.annotations.JawkAssocArray} parameter receives a live
 * {@link Map} view of the AWK array whose reads return plain values. Use
 * {@link io.jawk.ext.annotations.JawkRawValue} to receive the values exactly
 * as the runtime stores them, and
 * {@link io.jawk.ext.annotations.JawkIntrospect} to inspect a value without
 * assigning an untyped variable.
 *
 * @author Danny Daglas
 */
public abstract class AbstractExtension implements JawkExtension {

	private Map<String, ExtensionFunction> annotatedFunctions;
	private List<Method> beforeStartMethods;

	/**
	 * Creates an extension.
	 * <p>
	 * An extension holds no runtime: {@link #getJrt()}, {@link #getVm()},
	 * {@link #getSettings()} and {@link #toAwkString(Object)} resolve the run
	 * executing on the calling thread ({@link AVM#current()}). The same
	 * instance can therefore be registered in several engines and called by
	 * concurrent runs.
	 * </p>
	 */
	protected AbstractExtension() {
		// No per-run state: the runtime is resolved per call.
	}

	/** {@inheritDoc} */
	@Override
	public String getExtensionName() {
		return getClass().getSimpleName();
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
		return getJrt().toAwkString(obj);
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
	 * Returns the runtime of the run calling this extension.
	 *
	 * @return the runtime of the run executing on the calling thread
	 * @throws IllegalStateException when no run is executing on the calling
	 *         thread
	 */
	protected JRT getJrt() {
		return currentAvm().getJrt();
	}

	/**
	 * Returns the variable manager of the run calling this extension.
	 *
	 * @return the variable manager of the run executing on the calling thread
	 * @throws IllegalStateException when no run is executing on the calling
	 *         thread
	 */
	protected VariableManager getVm() {
		return currentAvm();
	}

	/**
	 * Returns the settings of the run calling this extension.
	 *
	 * @return the settings of the run executing on the calling thread
	 * @throws IllegalStateException when no run is executing on the calling
	 *         thread
	 */
	protected AwkSettings getSettings() {
		return currentAvm().getSettings();
	}

	/*
	 * The interpreter calling this extension. Fails when no run is executing on
	 * the calling thread (e.g. in a thread started by the extension).
	 */
	private static AVM currentAvm() {
		AVM avm = AVM.current();
		if (avm == null) {
			throw new IllegalStateException(
					"No AWK program is executing on this thread: the runtime of an extension is only available while a run calls it");
		}
		return avm;
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
						"@" + JawkBeforeStart.class.getSimpleName() + " does not support static methods: "
								+ method.toGenericString());
			}
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (method.getReturnType() != Void.TYPE
					|| parameterTypes.length != 2
					|| parameterTypes[0] != AVM.class
					|| parameterTypes[1] != JRT.class) {
				throw new IllegalStateException(
						"@" + JawkBeforeStart.class.getSimpleName() + " method must declare void method(AVM, JRT): "
								+ method.toGenericString());
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
				throw new IllegalStateException("Extension before-start method failed: " + method.toGenericString(), cause);
			}
		}
	}
}
