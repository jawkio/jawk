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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.ext.annotations.JawkOptional;
import io.jawk.ext.annotations.JawkRawValue;
import io.jawk.ext.annotations.JawkRegexp;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.IllegalAwkArgumentException;

/**
 * Metadata describing a single annotated extension function.
 */
public final class ExtensionFunction implements Serializable {

	private static final long serialVersionUID = 1L;

	/** AWK-visible keyword that dispatches to the underlying Java method. */
	private final String keyword;

	/** Extension type declaring the Java implementation method. */
	private final Class<? extends AbstractExtension> declaringType;

	/** Name of the Java method used when rehydrating serialized metadata. */
	private final String methodName;

	/** Java parameter types of the extension method. */
	private final Class<?>[] parameterTypes;
	private transient Method method;

	/** Flags describing which parameters must receive associative arrays. */
	private final boolean[] assocArrayParameters;

	/** Whether the underlying Java method accepts varargs. */
	private final boolean varArgs;

	/** Number of non-vararg parameters that must always be present. */
	private final int mandatoryParameterCount;

	/** Whether the vararg component type must be an associative array. */
	private final boolean varArgAssocArray;

	/**
	 * Explicit AWK argument positions that need raw, non-coercing evaluation.
	 * Transient: annotation-derived metadata is recomputed from the resolved
	 * {@link Method} after deserialization so that tuples files written by other
	 * Jawk versions stay loadable.
	 */
	private transient int[] rawValueParameterIndexes;

	/** AWK argument positions where regexp literals keep their precompiled pattern. */
	private transient int[] regexpParameterIndexes;

	ExtensionFunction(String keywordParam, Method methodParam) {
		this.keyword = validateKeyword(keywordParam, methodParam);
		this.declaringType = resolveDeclaringType(methodParam);
		this.methodName = methodParam.getName();
		this.parameterTypes = methodParam.getParameterTypes();
		this.method = prepareMethod(methodParam);
		this.varArgs = methodParam.isVarArgs();
		this.assocArrayParameters = inspectParameters(methodParam, methodParam.getParameters());
		int optionalCount = scanOptionalParameterCount(methodParam, methodParam.getParameters());
		this.mandatoryParameterCount = varArgs ?
				assocArrayParameters.length - 1 : assocArrayParameters.length - optionalCount;
		this.varArgAssocArray = varArgs && assocArrayParameters[assocArrayParameters.length - 1];
		computeAnnotationMetadata(methodParam);
	}

	/**
	 * Derives the argument-position metadata from the method's annotations.
	 * Called from the constructor and again after deserialization, because this
	 * metadata belongs to the class loaded in the current JVM, not to the
	 * serialized stream.
	 */
	private void computeAnnotationMetadata(Method methodParam) {
		this.rawValueParameterIndexes = scanObjectParameterIndexes(
				methodParam,
				methodParam.getParameters(),
				JawkRawValue.class);
		this.regexpParameterIndexes = scanObjectParameterIndexes(
				methodParam,
				methodParam.getParameters(),
				JawkRegexp.class);
	}

	/*
	 * Optional parameters let a fixed-signature method accept a shorter AWK
	 * argument list; missing trailing arguments are passed as null. They must be
	 * trailing and cannot be combined with varargs.
	 */
	private static int scanOptionalParameterCount(Method methodParam, Parameter[] parameters) {
		int optionalCount = 0;
		for (Parameter parameter : parameters) {
			if (parameter.isAnnotationPresent(JawkOptional.class)) {
				if (methodParam.isVarArgs()) {
					throw new IllegalStateException(
							"@" + JawkOptional.class.getSimpleName()
									+ " cannot be combined with varargs: " + methodParam.toGenericString());
				}
				optionalCount++;
			} else if (optionalCount > 0) {
				throw new IllegalStateException(
						"@" + JawkOptional.class.getSimpleName()
								+ " parameters must be trailing: " + methodParam.toGenericString());
			}
		}
		return optionalCount;
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

	/*
	 * Raw-value and regexp markers change how the compiler evaluates the matching
	 * AWK argument, so the annotated Java parameter must be able to receive any
	 * runtime value (Pattern, Map, untyped placeholders, ...): plain Object.
	 */
	private static int[] scanObjectParameterIndexes(
			Method methodParam,
			Parameter[] parameters,
			Class<? extends Annotation> annotationType) {
		int count = 0;
		int[] indexes = new int[parameters.length];
		for (int idx = 0; idx < parameters.length; idx++) {
			Parameter parameter = parameters[idx];
			if (!parameter.isAnnotationPresent(annotationType)) {
				continue;
			}
			if (parameter.isVarArgs() || parameter.getType() != Object.class) {
				throw new IllegalStateException(
						"Parameter " + idx + " of " + methodParam
								+ " annotated with @" + annotationType.getSimpleName()
								+ " must be a non-vararg " + Object.class.getName());
			}
			indexes[count++] = idx;
		}
		return Arrays.copyOf(indexes, count);
	}

	/**
	 * Inspects the declared Java parameters and records which ones are annotated
	 * with {@link JawkAssocArray}.
	 * <p>
	 * The validation is intentionally stricter than checking
	 * {@code Map.class.isAssignableFrom(parameterType)} alone. Jawk passes runtime
	 * associative arrays as {@link AssocArray} instances, so the declared parameter
	 * type must satisfy two constraints:
	 * </p>
	 * <ul>
	 * <li>it must be a {@link Map} type, because {@code @JawkAssocArray} is a
	 * map-shaped contract for extension authors</li>
	 * <li>it must also be able to receive an {@link AssocArray} instance at
	 * invocation time</li>
	 * </ul>
	 * <p>
	 * That second constraint rejects concrete map implementations such as
	 * {@link java.util.HashMap}. A declaration like
	 * {@code @JawkAssocArray HashMap<Object, Object>} is map-shaped, but it is not
	 * compatible with the {@link AssocArray} values that Jawk actually passes, so
	 * letting it register here would only defer the failure until reflective
	 * invocation.
	 * </p>
	 *
	 * @param methodParam method whose parameters are being inspected
	 * @param parameters declared parameters of {@code methodParam}
	 * @return flags indicating which parameter positions require associative arrays
	 * @throws IllegalStateException when an annotated parameter cannot receive the
	 *         runtime {@link AssocArray} values provided by Jawk
	 */
	private boolean[] inspectParameters(Method methodParam, Parameter[] parameters) {
		boolean[] assoc = new boolean[parameters.length];
		for (int idx = 0; idx < parameters.length; idx++) {
			Parameter parameter = parameters[idx];
			if (parameter.isAnnotationPresent(JawkAssocArray.class)) {
				Class<?> parameterType = parameter.getType();
				if (parameter.isVarArgs()) {
					parameterType = parameterType.getComponentType();
				}
				if (!Map.class.isAssignableFrom(parameterType)
						|| !parameterType.isAssignableFrom(AssocArray.class)) {
					throw new IllegalStateException(
							"Parameter " + idx + " of " + methodParam
									+ " annotated with @" + JawkAssocArray.class.getSimpleName()
									+ " must accept " + AssocArray.class.getName()
									+ " instances via " + Map.class.getName());
				}
				assoc[idx] = true;
			}
		}
		return assoc;
	}

	/**
	 * Restores the reflective {@link Method} handle after Java deserialization.
	 *
	 * @param in Object stream containing the serialized metadata
	 * @throws IOException If the stream cannot be read
	 * @throws ClassNotFoundException If a serialized dependency cannot be resolved
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		try {
			Method resolved = declaringType.getDeclaredMethod(methodName, parameterTypes);
			this.method = prepareMethod(resolved);
			computeAnnotationMetadata(resolved);
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
		int upperBound = Math.min(argCount, declaredParameterUpperBound());
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

	/** Highest exclusive index of the declared (non-vararg) parameters. */
	private int declaredParameterUpperBound() {
		return varArgs ? mandatoryParameterCount : parameterTypes.length;
	}

	/**
	 * Collects the indexes of arguments that should be evaluated without
	 * autoconverting untyped values to assigned scalar blanks.
	 *
	 * @param argCount number of arguments supplied by the caller
	 * @return indexes requiring raw value evaluation
	 */
	public int[] collectRawValueIndexes(int argCount) {
		verifyArgCount(argCount);
		return Arrays.stream(rawValueParameterIndexes).filter(idx -> idx < argCount).toArray();
	}

	/**
	 * Collects the indexes of arguments where a regexp literal keeps its
	 * precompiled pattern instead of being evaluated as {@code $0 ~ /re/}.
	 *
	 * @param argCount number of arguments supplied by the caller
	 * @return indexes keeping regexp literals raw
	 */
	public int[] collectRegexpIndexes(int argCount) {
		verifyArgCount(argCount);
		return Arrays.stream(regexpParameterIndexes).filter(idx -> idx < argCount).toArray();
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
		if (!varArgs) {
			// Omitted optional trailing arguments are passed as null
			Object[] invocationArgs = new Object[parameterTypes.length];
			if (argCount > 0) {
				System.arraycopy(args, 0, invocationArgs, 0, argCount);
			}
			return invocationArgs;
		}
		if (argCount == 0) {
			return new Object[] { Array.newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), 0) };
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
			int maxCount = parameterTypes.length;
			if (argCount < mandatoryParameterCount || argCount > maxCount) {
				String expected = mandatoryParameterCount == maxCount ?
						String.valueOf(maxCount) : mandatoryParameterCount + " to " + maxCount;
				throw new IllegalAwkArgumentException(
						"Extension function '" + keyword + "' expects " + expected
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
		int upperBound = Math.min(argCount, declaredParameterUpperBound());
		for (int idx = 0; idx < upperBound; idx++) {
			if (assocArrayParameters[idx]) {
				requireAssocArray(idx, args[idx]);
			}
		}
		if (varArgs && varArgAssocArray) {
			for (int idx = mandatoryParameterCount; idx < argCount; idx++) {
				requireAssocArray(idx, args[idx]);
			}
		}
	}

	private void requireAssocArray(int idx, Object arg) {
		if (!(arg instanceof Map)) {
			throw new IllegalAwkArgumentException(
					"Argument " + idx + " passed to extension function '" + keyword
							+ "' must be an associative array");
		}
	}
}
