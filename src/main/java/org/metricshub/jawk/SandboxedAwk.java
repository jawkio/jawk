package org.metricshub.jawk;

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

import java.util.Collection;
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.backend.SandboxedAVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.intermediate.SandboxedAwkTuples;
import org.metricshub.jawk.util.AwkSettings;

/**
 * {@link Awk} variant that enforces sandbox restrictions by delegating to the
 * sandbox-specific tuple and runtime implementations.
 */
public final class SandboxedAwk extends Awk {

	public SandboxedAwk() {
		super();
	}

	/**
	 * Creates a sandboxed AWK instance with the specified settings.
	 *
	 * @param settings behavioral configuration for this engine
	 */
	public SandboxedAwk(AwkSettings settings) {
		super(settings);
	}

	public SandboxedAwk(Collection<? extends JawkExtension> extensions) {
		super(extensions);
	}

	/**
	 * Creates a sandboxed AWK instance with extensions and settings.
	 *
	 * @param extensions extension instances
	 * @param settings behavioral configuration for this engine
	 */
	public SandboxedAwk(Collection<? extends JawkExtension> extensions, AwkSettings settings) {
		super(extensions, settings);
	}

	@SafeVarargs
	public SandboxedAwk(JawkExtension... extensions) {
		super(extensions);
	}

	@Override
	protected AwkTuples createTuples() {
		return new SandboxedAwkTuples();
	}

	@Override
	protected AVM createAvm() {
		return createAvm(getSettings());
	}

	@Override
	protected AVM createAvm(AwkSettings settingsParam) {
		return new SandboxedAVM(settingsParam, getExtensionInstances());
	}
}
