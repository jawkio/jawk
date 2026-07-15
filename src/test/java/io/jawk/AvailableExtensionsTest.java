package io.jawk;

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

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.Map;
import org.junit.Test;
import io.jawk.ext.ExtensionFunction;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.StdinExtension;

/**
 * Tests discovery of available {@link io.jawk.ext.JawkExtension}s.
 */
public class AvailableExtensionsTest {

	@Test
	public void testListAvailableExtensions() {
		Map<String, JawkExtension> ext = Awk.listAvailableExtensions();
		assertSame(StdinExtension.class, ext.get("stdin").getClass());
	}

	@Test
	public void testExtensionNames() {
		Map<String, JawkExtension> ext = Awk.listAvailableExtensions();
		assertSame(StdinExtension.class, ext.get(StdinExtension.class.getSimpleName()).getClass());
		assertSame(StdinExtension.class, ext.get(StdinExtension.class.getName()).getClass());
		assertSame(StdinExtension.class, ext.get("Stdin Support").getClass());
	}

	@Test
	public void testResolveReturnsFreshInstancesForBuiltins() {
		// Extensions carry per-engine runtime state, so the registry must never
		// hand the same built-in instance to two engines.
		JawkExtension first = io.jawk.ext.ExtensionRegistry.resolve("GawkExtension");
		JawkExtension second = io.jawk.ext.ExtensionRegistry.resolve("GawkExtension");
		assertNotSame(first, second);
	}

	@Test
	public void testExtensionKeywords() {
		Map<String, ExtensionFunction> keywordMap = Awk
				.createExtensionFunctionMap(
						StdinExtension.INSTANCE);
		assertSame(StdinExtension.class, keywordMap.get("StdinHasInput").getDeclaringType());

		JawkExtension customStdin = new StdinExtension();
		Map<String, JawkExtension> instanceMap = Awk.createExtensionInstanceMap(customStdin);
		assertSame(customStdin, instanceMap.get(StdinExtension.class.getName()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDuplicateExtensionInstancesAreRejected() {
		Awk.createExtensionInstanceMap(new StdinExtension(), new StdinExtension());
	}
}
