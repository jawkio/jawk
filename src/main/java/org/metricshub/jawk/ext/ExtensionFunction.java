package org.metricshub.jawk.ext;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright 2006 - 2026 MetricsHub
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.metricshub.jawk.ext.annotations.JawkAssocArray;
import org.metricshub.jawk.ext.annotations.JawkFunction;
import org.metricshub.jawk.jrt.AssocArray;
import org.metricshub.jawk.jrt.IllegalAwkArgumentException;

/**
 * Metadata describing a single annotated extension function.
 */
public final class ExtensionFunction implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String keyword;
	private final Class<? extends AbstractExtension> declaringType;
	private final String methodName;
	private final Class<?>[] parameterTypes;
	private transient Method method;
	private final boolean[] assocArrayParameters;
	private final boolean varArgs;
	private final int mandatoryParameterCount;
	private final boolean varArgAssocArray;

	ExtensionFunction(String keywordParam, Method methodParam) {
		this.keyword = validateKeyword(keywordParam, methodParam);
		this.declaringType = resolveDeclaringType(methodParam);
		this.methodName = methodParam.getName();
		this.parameterTypes = methodParam.getParameterTypes();
		this.method = prepareMethod(methodParam);
		this.varArgs = methodParam.isVarArgs();
		this.assocArrayParameters = inspectParameters(methodParam, methodParam.getParameters());
		this.mandatoryParameterCount = varArgs ? assocArrayParameters.length - 1 : assocArrayParameters.length;
		this.varArgAssocArray = varArgs && assocArrayParameters[assocArrayParameters.length - 1];
	}

	private static String validateKeyword(String keyword, Method method) {
		Objects.requireNonNull(method, "method");
		if (keyword == null || keyword.trim().isEmpty()) {
			throw new IllegalStateException(
					"@" + JawkFunction.class.getSimpleName()
							+ " on " + method + " must declare a non-empty name");
		}
		return keyword;
	}

	private static Class<? extends AbstractExtension> resolveDeclaringType(Method method) {
		Class<?> declaringClass = method.getDeclaringClass();
		if (!AbstractExtension.class.isAssignableFrom(declaringClass)) {
			throw new IllegalStateException(
					"@" + JawkFunction.class.getSimpleName()
							+ " must be declared on a subclass of " + AbstractExtension.class.getName()
							+ ": " + method);
		}
		@SuppressWarnings("unchecked")
		Class<? extends AbstractExtension> type = (Class<? extends AbstractExtension>) declaringClass;
		return type;
	}

	private static Method prepareMethod(Method method) {
		if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
			throw new IllegalStateException(
					"@" + JawkFunction.class.getSimpleName()
							+ " does not support static methods: " + method.toGenericString());
		}
		method.setAccessible(true);
		return method;
	}

	private boolean[] inspectParameters(Method methodParam, Parameter[] parameters) {
		boolean[] assoc = new boolean[parameters.length];
		for (int idx = 0; idx < parameters.length; idx++) {
			Parameter parameter = parameters[idx];
			if (parameter.isAnnotationPresent(JawkAssocArray.class)) {
				Class<?> parameterType = parameter.getType();
				if (parameter.isVarArgs()) {
					parameterType = parameterType.getComponentType();
				}
				if (!AssocArray.class.isAssignableFrom(parameterType)) {
					throw new IllegalStateException(
							"Parameter " + idx + " of " + methodParam
									+ " annotated with @" + JawkAssocArray.class.getSimpleName()
									+ " must accept " + AssocArray.class.getName());
				}
				assoc[idx] = true;
			}
		}
		return assoc;
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		try {
			Method resolved = declaringType.getDeclaredMethod(methodName, parameterTypes);
			this.method = prepareMethod(resolved);
		} catch (NoSuchMethodException ex) {
			throw new IllegalStateException(
					"Unable to rehydrate extension method '" + methodName
							+ "' on type " + declaringType.getName(),
					ex);
		}
	}

	/**
	 * Returns the Awk keyword mapped to this extension function.
	 *
	 * @return the keyword exposed by the annotated method
	 */
	public String getKeyword() {
		return keyword;
	}

	/**
	 * Returns the extension type that declares the underlying Java method.
	 *
	 * @return declaring {@link AbstractExtension} subtype
	 */
	public Class<? extends AbstractExtension> getDeclaringType() {
		return declaringType;
	}

	/**
	 * Returns the fully-qualified class name of the declaring extension.
	 *
	 * @return extension class name
	 */
	public String getExtensionClassName() {
		return declaringType.getName();
	}

	/**
	 * Returns the minimum number of arguments required to invoke the function.
	 *
	 * @return required argument count before considering varargs
	 */
	public int getArity() {
		return mandatoryParameterCount;
	}

	/**
	 * Indicates whether the parameter at the supplied index must be an associative
	 * array.
	 *
	 * @param index zero-based parameter index
	 * @return {@code true} when the parameter must be an associative array
	 * @throws IndexOutOfBoundsException when the index exceeds the parameter count
	 */
	public boolean expectsAssocArray(int index) {
		if (index < 0 || index >= assocArrayParameters.length) {
			throw new IndexOutOfBoundsException("Parameter index out of range: " + index);
		}
		return assocArrayParameters[index];
	}

	/**
	 * Collects the indexes of arguments that must be associative arrays for a call
	 * with the supplied argument count. Vararg positions are included when the
	 * vararg parameter requires associative arrays.
	 *
	 * @param argCount number of arguments supplied by the caller
	 * @return indexes of arguments that must be associative arrays
	 */
	public int[] collectAssocArrayIndexes(int argCount) {
		verifyArgCount(argCount);
		List<Integer> indexes = new ArrayList<Integer>();
		int upperBound = Math.min(argCount, mandatoryParameterCount);
		for (int idx = 0; idx < upperBound; idx++) {
			if (assocArrayParameters[idx]) {
				indexes.add(Integer.valueOf(idx));
			}
		}
		if (varArgs && varArgAssocArray) {
			for (int idx = mandatoryParameterCount; idx < argCount; idx++) {
				indexes.add(Integer.valueOf(idx));
			}
		}
		int[] result = new int[indexes.size()];
		for (int idx = 0; idx < indexes.size(); idx++) {
			result[idx] = indexes.get(idx).intValue();
		}
		return result;
	}

	/**
	 * Invokes the underlying Java method on the supplied target instance.
	 *
	 * @param target extension instance to receive the call
	 * @param args arguments evaluated by the interpreter
	 * @return result of the Java invocation
	 * @throws IllegalAwkArgumentException when the arguments violate the metadata
	 * @throws IllegalStateException when reflection cannot invoke the method
	 */
	public Object invoke(AbstractExtension target, Object[] args) {
		Objects.requireNonNull(target, "target");
		if (!declaringType.isInstance(target)) {
			throw new IllegalArgumentException(
					"Extension instance " + target.getClass().getName()
							+ " is not compatible with " + declaringType.getName());
		}
		int argCount = args == null ? 0 : args.length;
		verifyArgCount(argCount);
		enforceAssocArrayParameters(args);
		Object[] invocationArgs = prepareArguments(args);
		try {
			return method.invoke(target, invocationArgs);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(
					"Unable to access extension function method for keyword '" + keyword + "'",
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
					"Invocation of extension function '" + keyword + "' failed",
					cause);
		}
	}

	private Object[] prepareArguments(Object[] args) {
		int argCount = args == null ? 0 : args.length;
		if (argCount == 0) {
			return varArgs ?
					new Object[]
					{ Array.newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), 0) } : new Object[0];
		}
		if (!varArgs) {
			Object[] invocationArgs = new Object[argCount];
			System.arraycopy(args, 0, invocationArgs, 0, argCount);
			return invocationArgs;
		}
		Object[] invocationArgs = new Object[mandatoryParameterCount + 1];
		for (int idx = 0; idx < mandatoryParameterCount; idx++) {
			invocationArgs[idx] = args[idx];
		}
		int varArgCount = argCount - mandatoryParameterCount;
		Class<?> componentType = parameterTypes[parameterTypes.length - 1].getComponentType();
		Object varArgArray = Array.newInstance(componentType, varArgCount);
		for (int idx = 0; idx < varArgCount; idx++) {
			Array.set(varArgArray, idx, args[mandatoryParameterCount + idx]);
		}
		invocationArgs[mandatoryParameterCount] = varArgArray;
		return invocationArgs;
	}

	/**
	 * Verifies that the provided argument count satisfies the arity constraints
	 * encoded in the metadata.
	 *
	 * @param argCount number of arguments the caller supplied
	 * @throws IllegalAwkArgumentException when the count violates the signature
	 */
	public void verifyArgCount(int argCount) {
		if (!varArgs) {
			if (argCount != mandatoryParameterCount) {
				throw new IllegalAwkArgumentException(
						"Extension function '" + keyword + "' expects " + mandatoryParameterCount
								+ " argument(s), not " + argCount);
			}
			return;
		}
		if (argCount < mandatoryParameterCount) {
			throw new IllegalAwkArgumentException(
					"Extension function '" + keyword + "' expects " + getArity()
							+ " argument(s), not " + argCount);
		}
	}

	private void enforceAssocArrayParameters(Object[] args) {
		if (args == null) {
			return;
		}
		int argCount = args.length;
		int upperBound = Math.min(argCount, mandatoryParameterCount);
		for (int idx = 0; idx < upperBound; idx++) {
			if (!assocArrayParameters[idx]) {
				continue;
			}
			Object argument = args[idx];
			if (!(argument instanceof AssocArray)) {
				throw new IllegalAwkArgumentException(
						"Argument " + idx + " passed to extension function '" + keyword
								+ "' must be an associative array");
			}
		}
		if (varArgs && varArgAssocArray) {
			for (int idx = mandatoryParameterCount; idx < argCount; idx++) {
				Object argument = args[idx];
				if (!(argument instanceof AssocArray)) {
					throw new IllegalAwkArgumentException(
							"Argument " + idx + " passed to extension function '" + keyword
									+ "' must be an associative array");
				}
			}
		}
	}
}
