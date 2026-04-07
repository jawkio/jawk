package io.jawk.backend;

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

import java.util.Map;
import io.jawk.ext.JawkExtension;
import io.jawk.jrt.AwkSink;
import io.jawk.jrt.JRT;
import io.jawk.jrt.SandboxedJRT;
import io.jawk.util.AwkSettings;

/**
 * {@link AVM} variant enforcing sandbox restrictions at runtime.
 */
public class SandboxedAVM extends AVM {

	/**
	 * Creates a sandboxed AVM with the provided settings and extension instances.
	 *
	 * @param parameters Runtime settings to honor
	 * @param extensionInstances Available extension implementations
	 */
	public SandboxedAVM(AwkSettings parameters,
			Map<String, JawkExtension> extensionInstances) {
		super(parameters, extensionInstances);
	}

	@Override
	protected JRT createJrt() {
		AwkSettings s = getSettings();
		return new SandboxedJRT(this, s.getLocale(), AwkSink.NOP_SINK, null);
	}

}
