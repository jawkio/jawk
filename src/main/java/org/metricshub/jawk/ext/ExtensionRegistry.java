package org.metricshub.jawk.ext;

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

/**
 * Registry used by extensions and the CLI to expose ready-to-use extension
 * instances.
 */
public final class ExtensionRegistry {

	private static final ConcurrentMap<String, JawkExtension> REGISTERED = new ConcurrentHashMap<String, JawkExtension>();

	static {
		registerBuiltin(
				StdinExtension.INSTANCE,
				StdinExtension.class.getName(),
				StdinExtension.class.getSimpleName(),
				"Stdin Support");
	}

	private ExtensionRegistry() {}

	/**
	 * Registers an extension instance under the supplied name.
	 *
	 * @param name identifying name
	 * @param extension extension instance
	 */
	public static void register(String name, JawkExtension extension) {
		Objects.requireNonNull(name, "Extension name must not be null");
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Extension name must not be empty");
		}
		REGISTERED.put(name, Objects.requireNonNull(extension, "Extension instance must not be null"));
	}

	private static void registerBuiltin(JawkExtension extension, String... identifiers) {
		Objects.requireNonNull(extension, "Extension instance must not be null");
		if (identifiers != null) {
			for (String identifier : identifiers) {
				if (identifier != null && !identifier.isEmpty()) {
					JawkExtension existing = REGISTERED.putIfAbsent(identifier, extension);
					if (existing != null && existing != extension) {
						throw new IllegalStateException(
								"Extension identifier '" + identifier + "' already mapped to "
										+ existing.getClass().getName());
					}
				}
			}
		}
		String name = extension.getExtensionName();
		if (name != null && !name.isEmpty()) {
			JawkExtension existing = REGISTERED.putIfAbsent(name, extension);
			if (existing != null && existing != extension) {
				throw new IllegalStateException(
						"Extension name '" + name + "' already mapped to "
								+ existing.getClass().getName());
			}
		}
	}

	/**
	 * Returns a snapshot of all registered extensions sorted by name.
	 *
	 * @return immutable view of registered extensions
	 */
	public static Map<String, JawkExtension> listExtensions() {
		List<Map.Entry<String, JawkExtension>> entries = new ArrayList<Map.Entry<String, JawkExtension>>(
				REGISTERED.entrySet());
		Collections.sort(entries, Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
		Map<String, JawkExtension> snapshot = new LinkedHashMap<String, JawkExtension>();
		for (Map.Entry<String, JawkExtension> entry : entries) {
			snapshot.put(entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(snapshot);
	}

	/**
	 * Resolves an extension name to the registered instance. The lookup is
	 * case-insensitive and also supports class names. When the extension has not
	 * yet been registered, the method attempts to load the class by name and
	 * instantiate it.
	 *
	 * @param name name or class name of the extension
	 * @return extension instance, or {@code null} when the name cannot be resolved
	 */
	public static JawkExtension resolve(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		JawkExtension extension = REGISTERED.get(name);
		if (extension != null) {
			return extension;
		}
		extension = findCaseInsensitive(name);
		if (extension != null) {
			return extension;
		}
		extension = findByClassName(name);
		if (extension != null) {
			return extension;
		}
		extension = instantiateByClassName(name);
		if (extension != null) {
			return extension;
		}
		return null;
	}

	private static JawkExtension findCaseInsensitive(String name) {
		for (Map.Entry<String, JawkExtension> entry : REGISTERED.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static JawkExtension findByClassName(String name) {
		for (JawkExtension extension : REGISTERED.values()) {
			Class<? extends JawkExtension> type = extension.getClass();
			if (type.getName().equals(name)
					|| type.getSimpleName().equals(name)
					|| type.getName().equalsIgnoreCase(name)
					|| type.getSimpleName().equalsIgnoreCase(name)) {
				return extension;
			}
		}
		return null;
	}

	private static JawkExtension instantiateByClassName(String name) {
		try {
			Class<?> clazz = Class.forName(name);
			if (!JawkExtension.class.isAssignableFrom(clazz)) {
				return null;
			}
			JawkExtension created = clazz.asSubclass(JawkExtension.class).getDeclaredConstructor().newInstance();
			JawkExtension instance = created;
			String simpleName = clazz.getSimpleName();
			if (!simpleName.isEmpty()) {
				JawkExtension existing = REGISTERED.putIfAbsent(simpleName, created);
				if (existing != null) {
					instance = existing;
				}
			}
			String extensionName = instance.getExtensionName();
			if (extensionName != null && !extensionName.isEmpty()) {
				JawkExtension existingByName = REGISTERED.putIfAbsent(extensionName, instance);
				if (existingByName != null) {
					instance = existingByName;
					extensionName = instance.getExtensionName();
				}
			}
			if (!simpleName.isEmpty()) {
				REGISTERED.put(simpleName, instance);
			}
			if (extensionName != null && !extensionName.isEmpty()) {
				REGISTERED.put(extensionName, instance);
			}
			register(name, instance);
			return instance;
		} catch (ClassNotFoundException ex) {
			return null;
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalStateException("Cannot instantiate extension " + name, e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Cannot instantiate extension " + name, cause);
		}
	}

}
