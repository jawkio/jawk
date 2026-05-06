package io.jawk;

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

import java.util.Collection;
import java.util.List;
import io.jawk.backend.AVM;
import io.jawk.backend.ProfilingAVM;
import io.jawk.backend.ProfilingSandboxedAVM;
import io.jawk.ext.JawkExtension;
import io.jawk.util.AwkSettings;
import io.jawk.util.ScriptSource;

/**
 * {@link Awk} variant that creates {@link ProfilingAVM} runtimes.
 */
public final class ProfilingAwk extends Awk {

	/**
	 * Creates a profiling AWK instance with default settings and no extensions.
	 */
	public ProfilingAwk() {
		super();
	}

	/**
	 * Creates a profiling AWK instance with the specified settings.
	 *
	 * @param settings behavioral configuration for this engine
	 */
	public ProfilingAwk(AwkSettings settings) {
		super(settings);
	}

	/**
	 * Creates a profiling AWK instance with the specified extension instances.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 */
	public ProfilingAwk(Collection<? extends JawkExtension> extensions) {
		super(extensions);
	}

	/**
	 * Creates a profiling AWK instance with the specified extension instances and
	 * settings.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 * @param settings behavioral configuration for this engine
	 */
	public ProfilingAwk(Collection<? extends JawkExtension> extensions, AwkSettings settings) {
		super(extensions, settings);
	}

	/**
	 * Creates a profiling AWK instance with the supplied extensions.
	 *
	 * @param extensions extension instances implementing {@link JawkExtension}
	 */
	@SafeVarargs
	public ProfilingAwk(JawkExtension... extensions) {
		super(extensions);
	}

	@Override
	public AVM createAvm() {
		return createAvm(getSettings());
	}

	@Override
	protected AVM createAvm(AwkSettings settingsParam) {
		return new ProfilingAVM(settingsParam, getExtensionInstances());
	}
}

final class ProfilingSandboxedAwk extends Awk {

	ProfilingSandboxedAwk(Collection<? extends JawkExtension> extensions, AwkSettings settings) {
		super(extensions, settings);
	}

	@Override
	public AwkProgram compile(List<ScriptSource> scripts, boolean disableOptimizeParam) throws java.io.IOException {
		return compileProgram(scripts, disableOptimizeParam, new SandboxedCompiledAwkProgram());
	}

	@Override
	public AwkExpression compileExpression(String expression, boolean disableOptimizeParam) throws java.io.IOException {
		return compileExpression(expression, disableOptimizeParam, new SandboxedCompiledAwkExpression());
	}

	@Override
	public AVM createAvm() {
		return createAvm(getSettings());
	}

	@Override
	protected AVM createAvm(AwkSettings settingsParam) {
		return new ProfilingSandboxedAVM(settingsParam, getExtensionInstances());
	}
}
