package io.jawk.jrt;

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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.Test;

/**
 * Verifies that the {@code %f}, {@code %e}, and {@code %g} conversions of
 * {@link AwkPrintf} produce output identical to rounding the exact binary
 * expansion of the double ({@code new BigDecimal(double)}), which is what
 * makes Jawk's rounding match the C library used by gawk.
 * <p>
 * {@link AwkPrintf} internally takes a fast path based on the shortest
 * decimal representation of the double when that provably rounds the same
 * way; this suite compares its output against an exact-expansion reference
 * over random bit patterns, random magnitudes from 1e-300 to 1e300,
 * halfway-adjacent decimal inputs, powers of ten and two and their
 * neighbors, and subnormals.
 * </p>
 */
public class AwkPrintfFloatRoundingTest {

	private static final int[] PRECISIONS = { 0, 1, 2, 3, 5, 6, 9, 12, 15, 16, 17, 20, 25 };

	private final Random random = new Random(20260814L);

	@Test
	public void testRandomBitPatterns() {
		for (int i = 0; i < 5000; i++) {
			double d = Double.longBitsToDouble(random.nextLong());
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				continue;
			}
			assertMatchesExactRounding(Math.abs(d));
		}
	}

	@Test
	public void testRandomMagnitudes() {
		for (int i = 0; i < 5000; i++) {
			int exponent = random.nextInt(601) - 300;
			double d = (1.0 + 9.0 * random.nextDouble()) * Math.pow(10.0, exponent);
			if (d == 0 || Double.isInfinite(d)) {
				continue;
			}
			assertMatchesExactRounding(d);
		}
	}

	@Test
	public void testHalfwayAdjacentDecimals() {
		// Decimal literals ending near a halfway digit parse to doubles just
		// above or below a rounding boundary, the worst case for any shortcut
		// based on the shortest decimal representation.
		String[] tails = { "5", "50", "500", "05", "49", "4999", "5000", "51", "4999999999", "5000000001" };
		for (int i = 0; i < 4000; i++) {
			StringBuilder sb = new StringBuilder();
			int leading = 1 + random.nextInt(12);
			for (int j = 0; j < leading; j++) {
				sb.append((char) ('0' + random.nextInt(10)));
			}
			sb.insert(random.nextInt(sb.length()) + 1, '.');
			sb.append(tails[random.nextInt(tails.length)]);
			sb.append('e').append(random.nextInt(101) - 50);
			double d = Double.parseDouble(sb.toString());
			if (d == 0 || Double.isInfinite(d)) {
				continue;
			}
			assertMatchesExactRounding(d);
		}
	}

	@Test
	public void testPowersAndNeighbors() {
		for (int exponent = -323; exponent <= 308; exponent++) {
			double p = Math.pow(10.0, exponent);
			if (p == 0 || Double.isInfinite(p)) {
				continue;
			}
			assertMatchesExactRounding(p);
			assertMatchesExactRounding(Math.nextUp(p));
			assertMatchesExactRounding(Math.nextDown(p));
		}
		for (int exponent = -1074; exponent <= 1023; exponent += 7) {
			double p = Math.scalb(1.0, exponent);
			assertMatchesExactRounding(p);
			assertMatchesExactRounding(Math.nextUp(p));
			assertMatchesExactRounding(Math.nextDown(p));
		}
	}

	@Test
	public void testSubnormals() {
		for (int i = 0; i < 2000; i++) {
			double d = Double.longBitsToDouble(random.nextLong() & 0x000FFFFFFFFFFFFFL);
			if (d == 0) {
				continue;
			}
			assertMatchesExactRounding(d);
		}
		assertMatchesExactRounding(Double.MIN_VALUE);
		assertMatchesExactRounding(Double.MIN_NORMAL);
		assertMatchesExactRounding(Math.nextDown(Double.MIN_NORMAL));
		assertMatchesExactRounding(Double.MAX_VALUE);
	}

	@Test
	public void testNegativeValuesAndZero() {
		assertEquals("0.000000", AwkPrintf.sprintf("%f", 0.0));
		assertEquals("-0.350000", AwkPrintf.sprintf("%f", -0.35));
		assertEquals("-3.5e-01", AwkPrintf.sprintf("%.1e", -0.35));
		assertEquals("-0.35", AwkPrintf.sprintf("%.6g", -0.35));
	}

	private void assertMatchesExactRounding(double abs) {
		// Exercise a fixed cross-section plus one random precision, so reruns
		// with the fixed seed stay reproducible.
		int[] precisions = {
				PRECISIONS[random.nextInt(PRECISIONS.length)],
				PRECISIONS[random.nextInt(PRECISIONS.length)],
				6 };
		for (int p : precisions) {
			assertEquals("%." + p + "f of " + abs, exactFixed(abs, p), AwkPrintf.sprintf("%." + p + "f", abs));
			assertEquals("%." + p + "e of " + abs, exactScientific(abs, p), AwkPrintf.sprintf("%." + p + "e", abs));
			assertEquals("%." + p + "g of " + abs, exactGeneral(abs, Math.max(p, 1)), AwkPrintf.sprintf("%." + p + "g", abs));
		}
	}

	/** Reference {@code %f} body computed from the exact binary expansion. */
	private static String exactFixed(double abs, int precision) {
		return new BigDecimal(abs).setScale(precision, RoundingMode.HALF_EVEN).toPlainString();
	}

	/** Reference {@code %e} body computed from the exact binary expansion. */
	private static String exactScientific(double abs, int precision) {
		BigDecimal mantissa;
		int exponent;
		if (abs == 0) {
			mantissa = BigDecimal.ZERO.setScale(precision);
			exponent = 0;
		} else {
			BigDecimal rounded = new BigDecimal(abs).round(new MathContext(precision + 1, RoundingMode.HALF_EVEN));
			exponent = rounded.precision() - rounded.scale() - 1;
			mantissa = rounded.movePointLeft(exponent).setScale(precision, RoundingMode.UNNECESSARY);
		}
		return mantissa.toPlainString() + "e" + (exponent < 0 ? "-" : "+") + exponentDigits(Math.abs(exponent));
	}

	/** Reference {@code %g} body computed from the exact binary expansion. */
	private static String exactGeneral(double abs, int precision) {
		if (abs == 0) {
			return "0";
		}
		BigDecimal rounded = new BigDecimal(abs).round(new MathContext(precision, RoundingMode.HALF_EVEN));
		int exponent = rounded.precision() - rounded.scale() - 1;
		if (exponent >= -4 && exponent < precision) {
			return stripTrailingFractionZeros(
					rounded.setScale(precision - 1 - exponent, RoundingMode.UNNECESSARY).toPlainString());
		}
		String mantissa = stripTrailingFractionZeros(
				rounded.movePointLeft(exponent).setScale(precision - 1, RoundingMode.UNNECESSARY).toPlainString());
		return mantissa + "e" + (exponent < 0 ? "-" : "+") + exponentDigits(Math.abs(exponent));
	}

	private static String exponentDigits(int exponent) {
		String digits = Integer.toString(exponent);
		return digits.length() < 2 ? "0" + digits : digits;
	}

	private static String stripTrailingFractionZeros(String s) {
		if (s.indexOf('.') < 0) {
			return s;
		}
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == '0') {
			end--;
		}
		if (end > 0 && s.charAt(end - 1) == '.') {
			end--;
		}
		return s.substring(0, end);
	}
}
