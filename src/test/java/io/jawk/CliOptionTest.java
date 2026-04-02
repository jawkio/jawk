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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class CliOptionTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void shortNoOptimizeOptionDisablesOptimization() {
		Cli cli = new Cli();
		cli.parse(new String[] { "-s", "{ print 1 }" });

		assertTrue(cli.isDisableOptimize());
	}

	@Test
	public void longNoOptimizeOptionDisablesOptimization() {
		Cli cli = new Cli();
		cli.parse(new String[] { "--no-optimize", "{ print 1 }" });

		assertTrue(cli.isDisableOptimize());
	}

	@Test
	public void loadOptionWithWrongSerializedTypeThrowsFriendlyError() throws Exception {
		File bad = tempFolder.newFile("wrong-type.ser");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(bad))) {
			oos.writeObject("not an AwkProgram");
		}

		Cli cli = new Cli();
		IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class,
				() -> cli.parse(new String[]
				{ "-L", bad.getAbsolutePath(), "{ print }" }));
		assertTrue(ex.getMessage().contains("does not contain a valid precompiled AwkProgram"));
		assertTrue(ex.getCause() instanceof ClassCastException);
	}
}
