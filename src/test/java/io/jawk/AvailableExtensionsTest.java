package io.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2025 MetricsHub
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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

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
		assertSame(StdinExtension.INSTANCE, ext.get("stdin"));
	}

	@Test
	public void testExtensionNames() {
		Map<String, JawkExtension> ext = Awk.listAvailableExtensions();
		assertSame(StdinExtension.INSTANCE, ext.get(StdinExtension.class.getSimpleName()));
		assertSame(StdinExtension.INSTANCE, ext.get(StdinExtension.class.getName()));
		assertSame(StdinExtension.INSTANCE, ext.get("Stdin Support"));
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
