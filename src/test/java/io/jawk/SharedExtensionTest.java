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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.util.AwkSettings;

/**
 * Tests that an extension instance shared by several engines, threads and
 * nested runs always uses the runtime of the run that calls it.
 */
public class SharedExtensionTest {

	/** Extension converting values with the calling run's {@code CONVFMT}. */
	public static class ConvertingExtension extends AbstractExtension {

		/**
		 * Converts a value with {@code CONVFMT}.
		 *
		 * @param value value to convert
		 * @return AWK string value
		 */
		@JawkFunction("text")
		public String text(Object value) {
			return toAwkString(value);
		}

		/**
		 * Runs another script with this extension, then converts a value.
		 *
		 * @param value value to convert after the nested run
		 * @return the nested run's output and the converted value
		 * @throws Exception when the nested run fails
		 */
		@JawkFunction("nestedThenText")
		public String nestedThenText(Object value) throws Exception {
			Awk inner = new Awk(Collections.singletonList(this), new AwkSettings());
			String innerOutput = inner.script(inner.compile("BEGIN { printf \"%s\", text(1/3) }")).execute();
			return innerOutput + "|" + toAwkString(value);
		}
	}

	@Test
	public void concurrentRunsUseTheirOwnRuntime() throws Exception {
		ConvertingExtension shared = new ConvertingExtension();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> runs = new ArrayList<Future<?>>();
			for (int run = 0; run < 32; run++) {
				int digits = 2 + run % 8;
				runs.add(pool.submit(() -> {
					AwkTestSupport.awkTest("a shared extension uses CONVFMT %." + digits + "g of its run")
							.script(
									"BEGIN { CONVFMT = \"%." + digits + "g\"; "
											+ "for (i = 0; i < 2000; i++) if (text(1/3) != (1/3 \"\")) bad++; print bad + 0 }")
							.withExtensions(shared)
							.expectLines("0")
							.runAndAssert();
					return null;
				}));
			}
			for (Future<?> run : runs) {
				run.get();
			}
		} finally {
			pool.shutdown();
		}
	}

	@Test
	public void nestedRunRestoresTheCallingRuntime() throws Exception {
		AwkTestSupport.awkTest("an extension starting another run keeps its caller's runtime afterwards")
				.script("BEGIN { CONVFMT = \"%.2g\"; print nestedThenText(1/3), text(1/3) }")
				.withExtensions(new ConvertingExtension())
				.expectLines("0.333333|0.33 0.33")
				.runAndAssert();
	}

	@Test
	public void runtimeIsUnavailableOutsideARun() {
		ConvertingExtension extension = new ConvertingExtension();
		try {
			extension.getExtensionFunctions().get("text").invoke(extension, new Object[] { "x" });
		} catch (IllegalStateException expected) {
			assertEquals(true, expected.getMessage().startsWith("No AWK program is executing on this thread"));
			return;
		}
		throw new AssertionError("toAwkString() outside a run must fail");
	}
}
