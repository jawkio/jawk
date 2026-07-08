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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Registry used by extensions and the CLI to expose ready-to-use extension
 * factories.
 * <p>
 * Extensions carry per-engine runtime state (interpreter, JRT), so
 * {@link #resolve(String)} returns a fresh instance for factory-registered
 * extensions — never a shared one. Instances registered through
 * {@link #register(String, JawkExtension)} are returned as-is; sharing them
 * across engines is the caller's responsibility.
 * </p>
 */
public final class ExtensionRegistry {

	/** A registered extension: the factory producing instances plus its type for class-name lookups. */
	private static final class Registration {

		private final Supplier<JawkExtension> factory;
		private final Class<? extends JawkExtension> type;

		private Registration(Supplier<JawkExtension> factoryParam, Class<? extends JawkExtension> typeParam) {
			this.factory = factoryParam;
			this.type = typeParam;
		}
	}

	private static final ConcurrentMap<String, Registration> REGISTERED = new ConcurrentHashMap<String, Registration>();

	static {
		registerBuiltin(
				GawkExtension::new,
				"GNU Awk Compatibility");
		registerBuiltin(
				StdinExtension::new,
				"Stdin Support");
	}

	private ExtensionRegistry() {}

	/**
	 * Registers an extension instance under the supplied name. The same
	 * instance is returned by every {@link #resolve(String)} call; prefer
	 * {@link #register(String, Supplier)} for stateful extensions that must not
	 * be shared across engines.
	 *
	 * @param name identifying name
	 * @param extension extension instance
	 */
	public static void register(String name, JawkExtension extension) {
		Objects.requireNonNull(extension, "Extension instance must not be null");
		requireName(name);
		REGISTERED.put(name, new Registration(() -> extension, extension.getClass()));
	}

	/**
	 * Registers an extension factory under the supplied name. Every
	 * {@link #resolve(String)} call returns a freshly created instance, so
	 * per-engine extension state is never shared.
	 *
	 * @param name identifying name
	 * @param factory factory producing extension instances
	 */
	public static void register(String name, Supplier<JawkExtension> factory) {
		Objects.requireNonNull(factory, "Extension factory must not be null");
		requireName(name);
		JawkExtension probe = Objects.requireNonNull(factory.get(), "Extension factory must not produce null");
		REGISTERED.put(name, new Registration(factory, probe.getClass()));
	}

	private static void requireName(String name) {
		Objects.requireNonNull(name, "Extension name must not be null");
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Extension name must not be empty");
		}
	}

	private static void registerBuiltin(Supplier<JawkExtension> factory, String... extraIdentifiers) {
		JawkExtension probe = factory.get();
		Registration registration = new Registration(factory, probe.getClass());
		putIfAbsentOrFail(probe.getClass().getName(), registration);
		putIfAbsentOrFail(probe.getClass().getSimpleName(), registration);
		putIfAbsentOrFail(probe.getExtensionName(), registration);
		for (String identifier : extraIdentifiers) {
			putIfAbsentOrFail(identifier, registration);
		}
	}

	private static void putIfAbsentOrFail(String identifier, Registration registration) {
		if (identifier == null || identifier.isEmpty()) {
			return;
		}
		Registration existing = REGISTERED.putIfAbsent(identifier, registration);
		if (existing != null && existing.type != registration.type) {
			throw new IllegalStateException(
					"Extension identifier '" + identifier + "' already mapped to " + existing.type.getName());
		}
	}

	/**
	 * Returns a snapshot of all registered extensions sorted by name. Each
	 * factory-registered entry maps to a freshly created instance.
	 *
	 * @return immutable view of registered extensions
	 */
	public static Map<String, JawkExtension> listExtensions() {
		List<Map.Entry<String, Registration>> entries = new ArrayList<Map.Entry<String, Registration>>(
				REGISTERED.entrySet());
		Collections.sort(entries, Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
		Map<String, JawkExtension> snapshot = new LinkedHashMap<String, JawkExtension>();
		for (Map.Entry<String, Registration> entry : entries) {
			snapshot.put(entry.getKey(), entry.getValue().factory.get());
		}
		return Collections.unmodifiableMap(snapshot);
	}

	/**
	 * Resolves an extension name to an extension instance. The lookup is
	 * case-insensitive and also supports class names. When the extension has not
	 * yet been registered, the method attempts to load the class by name and
	 * instantiate it.
	 *
	 * @param name name or class name of the extension
	 * @return extension instance (freshly created for factory-registered
	 *         extensions), or {@code null} when the name cannot be resolved
	 */
	public static JawkExtension resolve(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		Registration registration = REGISTERED.get(name);
		if (registration == null) {
			registration = findCaseInsensitive(name);
		}
		if (registration == null) {
			registration = findByClassName(name);
		}
		if (registration == null) {
			registration = registerByClassName(name);
		}
		return registration == null ? null : registration.factory.get();
	}

	private static Registration findCaseInsensitive(String name) {
		for (Map.Entry<String, Registration> entry : REGISTERED.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static Registration findByClassName(String name) {
		for (Registration registration : REGISTERED.values()) {
			Class<? extends JawkExtension> type = registration.type;
			if (type.getName().equals(name)
					|| type.getSimpleName().equals(name)
					|| type.getName().equalsIgnoreCase(name)
					|| type.getSimpleName().equalsIgnoreCase(name)) {
				return registration;
			}
		}
		return null;
	}

	private static Registration registerByClassName(String name) {
		Class<? extends JawkExtension> type;
		try {
			Class<?> clazz = Class.forName(name);
			if (!JawkExtension.class.isAssignableFrom(clazz)) {
				return null;
			}
			type = clazz.asSubclass(JawkExtension.class);
		} catch (ClassNotFoundException ex) {
			return null;
		}
		Supplier<JawkExtension> factory = () -> instantiate(type);
		// probe once to validate instantiation and learn the extension name
		JawkExtension probe = factory.get();
		Registration registration = new Registration(factory, type);
		putIfAbsentOrFail(type.getSimpleName(), registration);
		putIfAbsentOrFail(probe.getExtensionName(), registration);
		putIfAbsentOrFail(name, registration);
		return registration;
	}

	private static JawkExtension instantiate(Class<? extends JawkExtension> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalStateException("Cannot instantiate extension " + type.getName(), e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Cannot instantiate extension " + type.getName(), cause);
		}
	}
}
