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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves vendored integration-test resources directly from the repository
 * checkout instead of relying on the Maven test classpath layout.
 */
final class CompatibilityTestResources {

	private CompatibilityTestResources() {}

	static Path projectDirectory(Class<?> anchor) {
		try {
			Path testClassesDirectory = Paths
					.get(anchor.getProtectionDomain().getCodeSource().getLocation().toURI())
					.toAbsolutePath()
					.normalize();
			Path targetDirectory = testClassesDirectory.getParent();
			if (targetDirectory == null) {
				throw new IllegalStateException("Cannot determine Maven target directory from " + testClassesDirectory);
			}
			Path projectDirectory = targetDirectory.getParent();
			if (projectDirectory == null) {
				throw new IllegalStateException("Cannot determine project directory from " + targetDirectory);
			}
			return projectDirectory;
		} catch (URISyntaxException ex) {
			throw new IllegalStateException("Cannot resolve project directory", ex);
		}
	}

	static Path resourceDirectory(Class<?> anchor, String firstSegment, String... additionalSegments) {
		Path resourceDirectory = projectDirectory(anchor).resolve(Paths.get("src", "it", "resources", firstSegment));
		for (String segment : additionalSegments) {
			resourceDirectory = resourceDirectory.resolve(segment);
		}
		return resourceDirectory;
	}
}
