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
import static org.junit.Assert.assertThrows;
import static io.jawk.jrt.AwkPrintf.sprintf;

import java.util.Locale;
import org.junit.Test;

/**
 * Unit tests for {@link AwkPrintf}.
 * <p>
 * This suite incorporates the complete unit test suite of the former
 * <a href="https://github.com/metricshub/printf4j">Printf4J</a> project,
 * including the tests that were disabled or commented out there. Where
 * Printf4J (which emulated glibc) and AWK semantics differ, the expected
 * values below were verified against gawk 5 and are annotated accordingly.
 * </p>
 */
public class AwkPrintfTest {

	@Test
	public void testPlus() {
		assertEquals("+42", sprintf("%+d", 42));
		assertEquals("-42", sprintf("%+d", -42));
		assertEquals("  +42", sprintf("%+5d", 42));
		assertEquals("  -42", sprintf("%+5d", -42));
		assertEquals("            +42", sprintf("%+15d", 42));
		assertEquals("            -42", sprintf("%+15d", -42));
		assertEquals("Hello testing", sprintf("%+s", "Hello testing"));
		assertEquals("+1024", sprintf("%+d", 1024));
		assertEquals("-1024", sprintf("%+d", -1024));
		assertEquals("+1024", sprintf("%+i", 1024));
		assertEquals("-1024", sprintf("%+i", -1024));
		assertEquals("1024", sprintf("%+u", 1024));
		assertEquals("4294966272", sprintf("%+u", 4294966272L));
		assertEquals("777", sprintf("%+o", 511));
		assertEquals("37777777001", sprintf("%+o", 4294966785L));
		assertEquals("1234abcd", sprintf("%+x", 305441741));
		assertEquals("edcb5433", sprintf("%+x", 3989525555L));
		assertEquals("1234ABCD", sprintf("%+X", 305441741));
		assertEquals("EDCB5433", sprintf("%+X", 3989525555L));
		assertEquals("x", sprintf("%+c", 'x'));
		// Was commented out in Printf4J expecting "0": gawk prints nothing for
		// a zero value with an explicit zero precision, even with sign flags.
		assertEquals("", sprintf("%+.0d", 0));
	}

	@Test
	public void testBlank() {
		assertEquals(" 42", sprintf("% d", 42));
		assertEquals("-42", sprintf("% d", -42));
		assertEquals("   42", sprintf("% 5d", 42));
		assertEquals("  -42", sprintf("% 5d", -42));
		assertEquals("             42", sprintf("% 15d", 42));
		assertEquals("            -42", sprintf("% 15d", -42));
		assertEquals("            -42", sprintf("% 15d", -42));
		assertEquals("        -42.987", sprintf("% 15.3f", -42.987));
		assertEquals("         42.987", sprintf("% 15.3f", 42.987));
		assertEquals("Hello testing", sprintf("% s", "Hello testing"));
		assertEquals(" 1024", sprintf("% d", 1024));
		assertEquals("-1024", sprintf("% d", -1024));
		assertEquals(" 1024", sprintf("% i", 1024));
		assertEquals("-1024", sprintf("% i", -1024));
		assertEquals("1024", sprintf("% u", 1024));
		assertEquals("4294966272", sprintf("% u", 4294966272L));
		assertEquals("777", sprintf("% o", 511));
		assertEquals("37777777001", sprintf("% o", 4294966785L));
		assertEquals("1234abcd", sprintf("% x", 305441741));
		assertEquals("edcb5433", sprintf("% x", 3989525555L));
		assertEquals("1234ABCD", sprintf("% X", 305441741));
		assertEquals("EDCB5433", sprintf("% X", 3989525555L));
		assertEquals("x", sprintf("% c", 'x'));
	}

	@Test
	public void testZero() {
		assertEquals("42", sprintf("%0d", 42));
		assertEquals("42", sprintf("%0ld", 42L));
		assertEquals("-42", sprintf("%0d", -42));
		assertEquals("00042", sprintf("%05d", 42));
		assertEquals("-0042", sprintf("%05d", -42));
		assertEquals("000000000000042", sprintf("%015d", 42));
		assertEquals("-00000000000042", sprintf("%015d", -42));
		assertEquals("000000000042.12", sprintf("%015.2f", 42.1234));
		assertEquals("00000000042.988", sprintf("%015.3f", 42.9876));
		assertEquals("-00000042.98760", sprintf("%015.5f", -42.9876));
	}

	@Test
	public void testMinus() {
		assertEquals("42", sprintf("%-d", 42));
		assertEquals("-42", sprintf("%-d", -42));
		assertEquals("42   ", sprintf("%-5d", 42));
		assertEquals("-42  ", sprintf("%-5d", -42));
		assertEquals("42             ", sprintf("%-15d", 42));
		assertEquals("-42            ", sprintf("%-15d", -42));
		assertEquals("42", sprintf("%-0d", 42));
		assertEquals("-42", sprintf("%-0d", -42));
		assertEquals("42   ", sprintf("%-05d", 42));
		assertEquals("-42  ", sprintf("%-05d", -42));
		assertEquals("42             ", sprintf("%-015d", 42));
		assertEquals("-42            ", sprintf("%-015d", -42));
		assertEquals("42", sprintf("%0-d", 42));
		assertEquals("-42", sprintf("%0-d", -42));
		assertEquals("42   ", sprintf("%0-5d", 42));
		assertEquals("-42  ", sprintf("%0-5d", -42));
		assertEquals("42             ", sprintf("%0-15d", 42));
		assertEquals("-42            ", sprintf("%0-15d", -42));
		assertEquals("-4.200e+01     ", sprintf("%0-15.3e", -42.));
		// Printf4J expected "-42.0 ": AWK's %g removes trailing
		// zeros, so gawk prints "-42 ".
		assertEquals("-42            ", sprintf("%0-15.3g", -42.));
	}

	@Test
	public void testHash() {
		// Printf4J expected "" here, but gawk prints "0" for a zero value
		// with '#' and a zero precision on %x.
		assertEquals("0", sprintf("%#.0x", 0));
		// Printf4J had this assertion commented out as "the real expected
		// behavior, which is wrong IMO" (it returned "0x0" instead): C and
		// gawk agree on "0", which is what AwkPrintf now produces.
		assertEquals("0", sprintf("%#.1x", 0));
		// "%#.0llx" is invalid in gawk: doubled length modifiers make the
		// whole specifier print verbatim, without consuming an argument.
		assertEquals("%#.0llx", sprintf("%#.0llx", 0));
		assertEquals("0x0000614e", sprintf("%#.8x", 0x614e));
		// Was commented out in Printf4J ("binary is not supported for now"):
		// %b is not an AWK conversion, so gawk prints the specifier verbatim.
		assertEquals("%#b", sprintf("%#b", 6));
		// gawk-verified: the '#' prefix depends on the original value, so a
		// nonzero fraction that truncates to zero keeps the prefix.
		assertEquals("0x0", sprintf("%#.0x", 0.1));
		assertEquals("0x0", sprintf("%#x", 0.5));
		// gawk-verified: '#' with %o always adds its leading zero on nonzero
		// values, in addition to any precision padding.
		assertEquals("00", sprintf("%#o", 0.5));
		assertEquals("00", sprintf("%#.0o", 0.2));
		assertEquals("000001", sprintf("%#.5o", 1));
		assertEquals("0010", sprintf("%#.3o", 8));
		assertEquals("010", sprintf("%#o", 8));
	}

	@Test
	public void testSpecifier() {
		assertEquals("Hello testing", sprintf("Hello testing"));
		assertEquals("Hello testing", sprintf("%s", "Hello testing"));
		assertEquals("1024", sprintf("%d", 1024));
		assertEquals("-1024", sprintf("%d", -1024));
		assertEquals("1024", sprintf("%i", 1024));
		assertEquals("-1024", sprintf("%i", -1024));
		assertEquals("1024", sprintf("%u", 1024));
		assertEquals("4294966272", sprintf("%u", 4294966272L));
		assertEquals("777", sprintf("%o", 511));
		assertEquals("37777777001", sprintf("%o", 4294966785L));
		assertEquals("1234abcd", sprintf("%x", 305441741));
		assertEquals("edcb5433", sprintf("%x", 3989525555L));
		assertEquals("1234ABCD", sprintf("%X", 305441741));
		assertEquals("EDCB5433", sprintf("%X", 3989525555L));
		assertEquals("%", sprintf("%%"));
		// gawk-verified: a percent conversion ignores flags, width, and
		// precision.
		assertEquals("%", sprintf("%5%"));
		assertEquals("%", sprintf("%-3%"));
		assertEquals("%", sprintf("%0.2%"));
		// ...but an explicit position still pins the format to positional
		// mode, so mixing with a sequential conversion is fatal, like gawk.
		assertThrows(AwkRuntimeException.class, () -> sprintf("%1$%|%s", "a"));
		// gawk validates the index of a positioned conversion even when it
		// consumes no argument.
		assertThrows(AwkRuntimeException.class, () -> sprintf("%2$%", 1));
		assertThrows(AwkRuntimeException.class, () -> sprintf("%2$q", 1));
		assertThrows(AwkRuntimeException.class, () -> sprintf("%1$%"));
		// gawk-verified: a zero-indexed star operand means the value zero
		// without consuming an argument, while an out-of-range one is fatal.
		assertEquals("7|42", sprintf("%*0$d|%d", 7, 42));
		assertEquals("3|42", sprintf("%.*0$f|%d", 3.14159, 42));
		assertThrows(AwkRuntimeException.class, () -> sprintf("%*5$d|%d", 7, 42));
	}

	@Test
	public void testWidth() {
		assertEquals("Hello testing", sprintf("%1s", "Hello testing"));
		assertEquals("1024", sprintf("%1d", 1024));
		assertEquals("-1024", sprintf("%1d", -1024));
		assertEquals("1024", sprintf("%1i", 1024));
		assertEquals("-1024", sprintf("%1i", -1024));
		assertEquals("1024", sprintf("%1u", 1024));
		assertEquals("4294966272", sprintf("%1u", 4294966272L));
		assertEquals("777", sprintf("%1o", 511));
		assertEquals("37777777001", sprintf("%1o", 4294966785L));
		assertEquals("1234abcd", sprintf("%1x", 305441741));
		assertEquals("edcb5433", sprintf("%1x", 3989525555L));
		assertEquals("1234ABCD", sprintf("%1X", 305441741));
		assertEquals("EDCB5433", sprintf("%1X", 3989525555L));
		assertEquals("x", sprintf("%1c", 'x'));
	}

	@Test
	public void testWidth20() {
		assertEquals("               Hello", sprintf("%20s", "Hello"));
		assertEquals("                1024", sprintf("%20d", 1024));
		assertEquals("               -1024", sprintf("%20d", -1024));
		assertEquals("                1024", sprintf("%20i", 1024));
		assertEquals("               -1024", sprintf("%20i", -1024));
		assertEquals("                1024", sprintf("%20u", 1024));
		assertEquals("          4294966272", sprintf("%20u", 4294966272L));
		assertEquals("                 777", sprintf("%20o", 511));
		assertEquals("         37777777001", sprintf("%20o", 4294966785L));
		assertEquals("            1234abcd", sprintf("%20x", 305441741));
		assertEquals("            edcb5433", sprintf("%20x", 3989525555L));
		assertEquals("            1234ABCD", sprintf("%20X", 305441741));
		assertEquals("            EDCB5433", sprintf("%20X", 3989525555L));
		assertEquals("                   x", sprintf("%20c", 'x'));
	}

	@Test
	public void testWidthStar20() {
		assertEquals("               Hello", sprintf("%*s", 20, "Hello"));
		assertEquals("                1024", sprintf("%*d", 20, 1024));
		assertEquals("               -1024", sprintf("%*d", 20, -1024));
		assertEquals("                1024", sprintf("%*i", 20, 1024));
		assertEquals("               -1024", sprintf("%*i", 20, -1024));
		assertEquals("                1024", sprintf("%*u", 20, 1024));
		assertEquals("          4294966272", sprintf("%*u", 20, 4294966272L));
		assertEquals("                 777", sprintf("%*o", 20, 511));
		assertEquals("         37777777001", sprintf("%*o", 20, 4294966785L));
		assertEquals("            1234abcd", sprintf("%*x", 20, 305441741));
		assertEquals("            edcb5433", sprintf("%*x", 20, 3989525555L));
		assertEquals("            1234ABCD", sprintf("%*X", 20, 305441741));
		assertEquals("            EDCB5433", sprintf("%*X", 20, 3989525555L));
		assertEquals("                   x", sprintf("%*c", 20, 'x'));
	}

	@Test
	public void testMinus20() {
		assertEquals("Hello               ", sprintf("%-20s", "Hello"));
		assertEquals("1024                ", sprintf("%-20d", 1024));
		assertEquals("-1024               ", sprintf("%-20d", -1024));
		assertEquals("1024                ", sprintf("%-20i", 1024));
		assertEquals("-1024               ", sprintf("%-20i", -1024));
		assertEquals("1024                ", sprintf("%-20u", 1024));
		assertEquals("1024.1234           ", sprintf("%-20.4f", 1024.1234));
		assertEquals("4294966272          ", sprintf("%-20u", 4294966272L));
		assertEquals("777                 ", sprintf("%-20o", 511));
		assertEquals("37777777001         ", sprintf("%-20o", 4294966785L));
		assertEquals("1234abcd            ", sprintf("%-20x", 305441741));
		assertEquals("edcb5433            ", sprintf("%-20x", 3989525555L));
		assertEquals("1234ABCD            ", sprintf("%-20X", 305441741));
		assertEquals("EDCB5433            ", sprintf("%-20X", 3989525555L));
		assertEquals("x                   ", sprintf("%-20c", 'x'));
		assertEquals("|    9| |9 | |    9|", sprintf("|%5d| |%-2d| |%5d|", 9, 9, 9));
		assertEquals("|   10| |10| |   10|", sprintf("|%5d| |%-2d| |%5d|", 10, 10, 10));
		assertEquals("|    9| |9           | |    9|", sprintf("|%5d| |%-12d| |%5d|", 9, 9, 9));
		assertEquals("|   10| |10          | |   10|", sprintf("|%5d| |%-12d| |%5d|", 10, 10, 10));
	}

	@Test
	public void testZeroMinus20() {
		assertEquals("Hello               ", sprintf("%0-20s", "Hello"));
		assertEquals("1024                ", sprintf("%0-20d", 1024));
		assertEquals("-1024               ", sprintf("%0-20d", -1024));
		assertEquals("1024                ", sprintf("%0-20i", 1024));
		assertEquals("-1024               ", sprintf("%0-20i", -1024));
		assertEquals("1024                ", sprintf("%0-20u", 1024));
		assertEquals("4294966272          ", sprintf("%0-20u", 4294966272L));
		assertEquals("777                 ", sprintf("%0-20o", 511));
		assertEquals("37777777001         ", sprintf("%0-20o", 4294966785L));
		assertEquals("1234abcd            ", sprintf("%0-20x", 305441741));
		assertEquals("edcb5433            ", sprintf("%0-20x", 3989525555L));
		assertEquals("1234ABCD            ", sprintf("%0-20X", 305441741));
		assertEquals("EDCB5433            ", sprintf("%0-20X", 3989525555L));
		assertEquals("x                   ", sprintf("%0-20c", 'x'));
	}

	@Test
	public void testPadding20() {
		assertEquals("00000000000000001024", sprintf("%020d", 1024));
		assertEquals("-0000000000000001024", sprintf("%020d", -1024));
		assertEquals("00000000000000001024", sprintf("%020i", 1024));
		assertEquals("-0000000000000001024", sprintf("%020i", -1024));
		assertEquals("00000000000000001024", sprintf("%020u", 1024));
		assertEquals("00000000004294966272", sprintf("%020u", 4294966272L));
		assertEquals("00000000000000000777", sprintf("%020o", 511));
		assertEquals("00000000037777777001", sprintf("%020o", 4294966785L));
		assertEquals("0000000000001234abcd", sprintf("%020x", 305441741));
		assertEquals("000000000000edcb5433", sprintf("%020x", 3989525555L));
		assertEquals("0000000000001234ABCD", sprintf("%020X", 305441741));
		assertEquals("000000000000EDCB5433", sprintf("%020X", 3989525555L));
	}

	@Test
	public void testPaddingPrecision20() {
		assertEquals("00000000000000001024", sprintf("%.20d", 1024));
		assertEquals("-00000000000000001024", sprintf("%.20d", -1024));
		assertEquals("00000000000000001024", sprintf("%.20i", 1024));
		assertEquals("-00000000000000001024", sprintf("%.20i", -1024));
		assertEquals("00000000000000001024", sprintf("%.20u", 1024));
		assertEquals("00000000004294966272", sprintf("%.20u", 4294966272L));
		assertEquals("00000000000000000777", sprintf("%.20o", 511));
		assertEquals("00000000037777777001", sprintf("%.20o", 4294966785L));
		assertEquals("0000000000001234abcd", sprintf("%.20x", 305441741));
		assertEquals("000000000000edcb5433", sprintf("%.20x", 3989525555L));
		assertEquals("0000000000001234ABCD", sprintf("%.20X", 305441741));
		assertEquals("000000000000EDCB5433", sprintf("%.20X", 3989525555L));
	}

	@Test
	public void testPaddingHashZero20() {
		assertEquals("00000000000000001024", sprintf("%#020d", 1024));
		assertEquals("-0000000000000001024", sprintf("%#020d", -1024));
		assertEquals("00000000000000001024", sprintf("%#020i", 1024));
		assertEquals("-0000000000000001024", sprintf("%#020i", -1024));
		assertEquals("00000000000000001024", sprintf("%#020u", 1024));
		assertEquals("00000000004294966272", sprintf("%#020u", 4294966272L));
		assertEquals("00000000000000000777", sprintf("%#020o", 511));
		assertEquals("00000000037777777001", sprintf("%#020o", 4294966785L));
		assertEquals("0x00000000001234abcd", sprintf("%#020x", 305441741));
		assertEquals("0x0000000000edcb5433", sprintf("%#020x", 3989525555L));
		assertEquals("0X00000000001234ABCD", sprintf("%#020X", 305441741));
		assertEquals("0X0000000000EDCB5433", sprintf("%#020X", 3989525555L));
	}

	@Test
	public void testPaddingHash20() {
		assertEquals("                1024", sprintf("%#20d", 1024));
		assertEquals("               -1024", sprintf("%#20d", -1024));
		assertEquals("                1024", sprintf("%#20i", 1024));
		assertEquals("               -1024", sprintf("%#20i", -1024));
		assertEquals("                1024", sprintf("%#20u", 1024));
		assertEquals("          4294966272", sprintf("%#20u", 4294966272L));
		// The following assertions were commented out in Printf4J; they match
		// C and gawk, and now pass.
		assertEquals("                0777", sprintf("%#20o", 511));
		assertEquals("        037777777001", sprintf("%#20o", 4294966785L));
		assertEquals("          0x1234abcd", sprintf("%#20x", 305441741));
		assertEquals("          0xedcb5433", sprintf("%#20x", 3989525555L));
		assertEquals("          0X1234ABCD", sprintf("%#20X", 305441741));
		assertEquals("          0XEDCB5433", sprintf("%#20X", 3989525555L));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testPadding20Dot5() {
		assertEquals("               01024", sprintf("%20.5d", 1024));
		assertEquals("              -01024", sprintf("%20.5d", -1024));
		assertEquals("               01024", sprintf("%20.5i", 1024));
		assertEquals("              -01024", sprintf("%20.5i", -1024));
		assertEquals("               01024", sprintf("%20.5u", 1024));
		assertEquals("          4294966272", sprintf("%20.5u", 4294966272L));
		assertEquals("               00777", sprintf("%20.5o", 511));
		assertEquals("         37777777001", sprintf("%20.5o", 4294966785L));
		assertEquals("            1234abcd", sprintf("%20.5x", 305441741));
		assertEquals("          00edcb5433", sprintf("%20.10x", 3989525555L));
		assertEquals("            1234ABCD", sprintf("%20.5X", 305441741));
		assertEquals("          00EDCB5433", sprintf("%20.10X", 3989525555L));
	}

	// Was @Disabled in Printf4J; matches C and gawk.
	@Test
	public void testPaddingNegativeNumbers() {
		// space padding
		assertEquals("-5", sprintf("% 1d", -5));
		assertEquals("-5", sprintf("% 2d", -5));
		assertEquals(" -5", sprintf("% 3d", -5));
		assertEquals("  -5", sprintf("% 4d", -5));
		// zero padding
		assertEquals("-5", sprintf("%01d", -5));
		assertEquals("-5", sprintf("%02d", -5));
		assertEquals("-05", sprintf("%03d", -5));
		assertEquals("-005", sprintf("%04d", -5));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testPaddingNegativeFloat() {
		// space padding
		assertEquals("-5.0", sprintf("% 3.1f", -5.));
		assertEquals("-5.0", sprintf("% 4.1f", -5.));
		assertEquals(" -5.0", sprintf("% 5.1f", -5.));
		assertEquals("    -5", sprintf("% 6.1g", -5.));
		assertEquals("-5.0e+00", sprintf("% 6.1e", -5.));
		assertEquals("  -5.0e+00", sprintf("% 10.1e", -5.));
		// zero padding
		assertEquals("-5.0", sprintf("%03.1f", -5.));
		assertEquals("-5.0", sprintf("%04.1f", -5.));
		assertEquals("-05.0", sprintf("%05.1f", -5.));
		// zero padding no decimal point
		assertEquals("-5", sprintf("%01.0f", -5.));
		assertEquals("-5", sprintf("%02.0f", -5.));
		assertEquals("-05", sprintf("%03.0f", -5.));
		assertEquals("-005.0e+00", sprintf("%010.1e", -5.));
		assertEquals("-05E+00", sprintf("%07.0E", -5.));
		assertEquals("-05", sprintf("%03.0g", -5.));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testLength() {
		assertEquals("", sprintf("%.0s", "Hello testing"));
		assertEquals("                    ", sprintf("%20.0s", "Hello testing"));
		assertEquals("", sprintf("%.s", "Hello testing"));
		assertEquals("                    ", sprintf("%20.s", "Hello testing"));
		assertEquals("                1024", sprintf("%20.0d", 1024));
		assertEquals("               -1024", sprintf("%20.0d", -1024));
		assertEquals("                    ", sprintf("%20.d", 0));
		assertEquals("                1024", sprintf("%20.0i", 1024));
		assertEquals("               -1024", sprintf("%20.i", -1024));
		assertEquals("                    ", sprintf("%20.i", 0));
		assertEquals("                1024", sprintf("%20.u", 1024));
		assertEquals("          4294966272", sprintf("%20.0u", 4294966272L));
		assertEquals("                    ", sprintf("%20.u", 0L));
		assertEquals("                 777", sprintf("%20.o", 511));
		assertEquals("         37777777001", sprintf("%20.0o", 4294966785L));
		assertEquals("                    ", sprintf("%20.o", 0L));
		assertEquals("            1234abcd", sprintf("%20.x", 305441741));
		assertEquals("                                          1234abcd", sprintf("%50.x", 305441741));
		assertEquals(
				"                                          1234abcd     12345",
				sprintf("%50.x%10.u", 305441741, 12345));
		assertEquals("            edcb5433", sprintf("%20.0x", 3989525555L));
		assertEquals("                    ", sprintf("%20.x", 0L));
		assertEquals("            1234ABCD", sprintf("%20.X", 305441741));
		assertEquals("            EDCB5433", sprintf("%20.0X", 3989525555L));
		assertEquals("                    ", sprintf("%20.X", 0L));
		assertEquals("  ", sprintf("%02.0u", 0L));
		assertEquals("  ", sprintf("%02.0d", 0));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testFloat() {
		// test special-case floats
		assertEquals("     nan", sprintf("%8f", Float.NaN));
		assertEquals("     inf", sprintf("%8f", Float.POSITIVE_INFINITY));
		assertEquals("-inf    ", sprintf("%-8f", Float.NEGATIVE_INFINITY));
		assertEquals("    +inf", sprintf("%+8e", Float.POSITIVE_INFINITY));
		assertEquals("3.1415", sprintf("%.4f", 3.1415354));
		assertEquals("30343.142", sprintf("%.3f", 30343.1415354));
		assertEquals("34", sprintf("%.0f", 34.1415354));
		assertEquals("1", sprintf("%.0f", 1.3));
		assertEquals("2", sprintf("%.0f", 1.55));
		assertEquals("1.6", sprintf("%.1f", 1.64));
		assertEquals("42.90", sprintf("%.2f", 42.8952));
		assertEquals("42.895200000", sprintf("%.9f", 42.8952));
		assertEquals("42.8952230000", sprintf("%.10f", 42.895223));
		// Printf4J expected "42.895223123000" and "42.895223877000" here
		// because its reference implementation truncated to 9 significant
		// fraction digits; gawk prints the correctly rounded values.
		assertEquals("42.895223123457", sprintf("%.12f", 42.89522312345678));
		assertEquals("42.895223876543", sprintf("%.12f", 42.89522387654321));
		assertEquals(" 42.90", sprintf("%6.2f", 42.8952));
		assertEquals("+42.90", sprintf("%+6.2f", 42.8952));
		assertEquals("+42.9", sprintf("%+5.1f", 42.9252));
		assertEquals("42.500000", sprintf("%f", 42.5));
		assertEquals("42.5", sprintf("%.1f", 42.5));
		assertEquals("42167.000000", sprintf("%f", 42167.0));
		assertEquals("-12345.987654321", sprintf("%.9f", -12345.987654321));
		assertEquals("4.0", sprintf("%.1f", 3.999));
		assertEquals("4", sprintf("%.0f", 3.5));
		assertEquals("4", sprintf("%.0f", 4.5));
		assertEquals("3", sprintf("%.0f", 3.49));
		assertEquals("3.5", sprintf("%.1f", 3.49));
		assertEquals("a0.5  ", sprintf("a%-5.1f", 0.5));
		assertEquals("a0.5  end", sprintf("a%-5.1fend", 0.5));
		assertEquals("12345.7", sprintf("%G", 12345.678));
		assertEquals("12345.68", sprintf("%.7G", 12345.678));
		assertEquals("1.2346E+08", sprintf("%.5G", 123456789.));
		// Printf4J expected "12345.0": AWK's %G removes trailing zeros.
		assertEquals("12345", sprintf("%.6G", 12345.));
		assertEquals("  +1.235e+08", sprintf("%+12.4g", 123456789.));
		assertEquals("0.0012", sprintf("%.2G", 0.001234));
		assertEquals(" +0.001234", sprintf("%+10.4G", 0.001234));
		assertEquals("+001.234e-05", sprintf("%+012.4g", 0.00001234));
		assertEquals("-1.23e-308", sprintf("%.3g", -1.2345e-308));
		assertEquals("+1.230E+308", sprintf("%+.3E", 1.23e+308));
		// Printf4J expected "1.0e+20" (its reference implementation switched
		// to exponential notation out of range); gawk prints the full value.
		assertEquals("100000000000000000000.0", sprintf("%.1f", 1E20));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5,
	// which only accepts a single 'h', 'l', or 'L' length modifier and
	// prints any other modifier combination verbatim.
	@Test
	public void testTypes() {
		assertEquals("0", sprintf("%i", 0));
		assertEquals("1234", sprintf("%i", 1234));
		assertEquals("32767", sprintf("%i", 32767));
		assertEquals("-32767", sprintf("%i", -32767));
		assertEquals("30", sprintf("%li", 30L));
		assertEquals("-2147483647", sprintf("%li", -2147483647L));
		assertEquals("2147483647", sprintf("%li", 2147483647L));
		// Doubled modifiers ("ll", "hh") and the "q" modifier are not valid
		// in gawk: the specifier prints verbatim and consumes no argument.
		assertEquals("%lli", sprintf("%lli", 30L));
		assertEquals("%lli", sprintf("%lli", -9223372036854775807L));
		assertEquals("%lli", sprintf("%lli", 9223372036854775807L));
		assertEquals("100000", sprintf("%lu", 100000L));
		assertEquals("4294967295", sprintf("%lu", 0xFFFFFFFFL));
		assertEquals("%llu", sprintf("%llu", 281474976710656L));
		assertEquals("%llu", sprintf("%llu", Long.parseUnsignedLong("18446744073709551615")));
		// Single j, z, and t modifiers are accepted and ignored, like h, l,
		// and L (gawk 5.2+).
		assertEquals("2147483647", sprintf("%zu", 2147483647L));
		assertEquals("2147483647", sprintf("%zd", 2147483647L));
		assertEquals("-2147483647", sprintf("%zi", -2147483647L));
		assertEquals("5", sprintf("%jd", 5));
		assertEquals("6", sprintf("%td", 6));
		// Distinct modifiers may stack; only repeats are invalid.
		assertEquals("42", sprintf("%lhd", 42));
		// %b is not an AWK conversion: printed verbatim, like gawk.
		assertEquals("%b", sprintf("%b", 60000));
		assertEquals("%lb", sprintf("%lb", 12345678L));
		assertEquals("165140", sprintf("%o", 60000));
		assertEquals("57060516", sprintf("%lo", 12345678L));
		assertEquals("12345678", sprintf("%lx", 0x12345678L));
		assertEquals("%llx", sprintf("%llx", 0x1234567891234567L));
		assertEquals("abcdefab", sprintf("%lx", 0xabcdefabL));
		assertEquals("ABCDEFAB", sprintf("%lX", 0xabcdefabL));
		assertEquals("v", sprintf("%c", 'v'));
		assertEquals("wv", sprintf("%cv", 'w'));
		assertEquals("A Test", sprintf("%s", "A Test"));
		// gawk ignores the single 'h' modifier without truncating the value,
		// and prints the invalid "hh" specifiers verbatim.
		assertEquals("%hhu", sprintf("%hhu", 0xFFFFL));
		assertEquals("13398", sprintf("%hu", 13398));
		assertEquals("1193046", sprintf("%hu", 0x123456L));
		assertEquals("Test%hhi 10000", sprintf("%s%hhi %hu", "Test", 10000, 0xFFFFFFFFL));
	}

	// Was @Disabled in Printf4J, which expected "kmarco": gawk prints the
	// unknown "%k" specifier verbatim.
	@Test
	public void testUnknown() {
		assertEquals("%kmarco", sprintf("%kmarco", 42, 37));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testStringLength() {
		assertEquals("This", sprintf("%.4s", "This is a test"));
		assertEquals("test", sprintf("%.4s", "test"));
		assertEquals("123", sprintf("%.7s", "123"));
		assertEquals("", sprintf("%.7s", ""));
		assertEquals("1234ab", sprintf("%.4s%.2s", "123456", "abcdef"));
		// Printf4J expected ".2s": gawk prints the whole invalid specifier
		// verbatim.
		assertEquals("%.4.2s", sprintf("%.4.2s", "123456"));
		assertEquals("123", sprintf("%.*s", 3, "123456"));
		// The precision counts characters, so it never splits a surrogate
		// pair, like gawk in a multibyte locale.
		assertEquals("😀", sprintf("%.1s", "😀x"));
		assertEquals("😀x", sprintf("%.2s", "😀x"));
		// The field width also counts characters: a supplementary character
		// fills one column (gawk pads %s the same way; its %c padding counts
		// bytes, a C-locale artifact that Jawk does not reproduce).
		assertEquals("  😀", sprintf("%3s", "😀"));
		assertEquals("😀  ", sprintf("%-3s", "😀"));
		assertEquals("  😀", sprintf("%3c", 0x1F600));
	}

	// Was @Disabled in Printf4J; expected values verified against gawk 5.
	@Test
	public void testMisc() {
		assertEquals("53000atest-20 bit", sprintf("%u%u%ctest%d %s", 5, 3000, 'a', -20, "bit"));
		assertEquals("0.33", sprintf("%.*f", 2, 0.33333333));
		assertEquals("1", sprintf("%.*d", -1, 1));
		assertEquals("foo", sprintf("%.3s", "foobar"));
		// Printf4J expected " " (glibc behavior): gawk prints nothing at all
		// for a zero value with zero precision, even with the space flag.
		assertEquals("", sprintf("% .0d", 0));
		assertEquals("     00004", sprintf("%10.5d", 4));
		assertEquals("hi x", sprintf("%*sx", -3, "hi"));
		assertEquals("0.33", sprintf("%.*g", 2, 0.33333333));
		assertEquals("3.33e-01", sprintf("%.*e", 2, 0.33333333));
	}

	@Test
	public void testChar() {
		assertEquals("A", sprintf("%c", 65));
		assertEquals("A", sprintf("%c", 65L));
		assertEquals("A", sprintf("%c", 65.0));
		assertEquals("A", sprintf("%c", 65.1));
		assertEquals("A", sprintf("%c", Integer.valueOf(65)));
		assertEquals("A", sprintf("%c", Long.valueOf(65)));
		assertEquals("A", sprintf("%c", Float.valueOf(65)));
		assertEquals("A", sprintf("%c", Double.valueOf(65)));
		assertEquals("6", sprintf("%c", "65"));
		Object nothing = null;
		assertEquals("\0", sprintf("%c", nothing));
	}

	// Ported from Printf4J's testToChar; AwkPrintf converts values for %c
	// internally, so the equivalent assertions go through sprintf().
	@Test
	public void testToChar() {
		assertEquals("A", sprintf("%c", 65));
		assertEquals("A", sprintf("%c", 65L));
		assertEquals("A", sprintf("%c", 65.0));
		assertEquals("A", sprintf("%c", 65.1));
		assertEquals("A", sprintf("%c", 65.9));
		assertEquals("A", sprintf("%c", Integer.valueOf(65)));
		assertEquals("A", sprintf("%c", Long.valueOf(65)));
		assertEquals("A", sprintf("%c", Float.valueOf(65)));
		assertEquals("A", sprintf("%c", Double.valueOf(65)));
		assertEquals("6", sprintf("%c", "65"));
		assertEquals("\0", sprintf("%c", ""));
		Object nothing = null;
		assertEquals("\0", sprintf("%c", nothing));
	}

	// Ported from Printf4J's testToLong: the same conversion now lives in
	// JRT.toLong (they shared the same original implementation).
	@Test
	public void testToLong() {
		assertEquals(65L, JRT.toLong('A'));
		assertEquals(65L, JRT.toLong(65));
		assertEquals(65L, JRT.toLong(65L));
		assertEquals(65L, JRT.toLong(65.0));
		assertEquals(65L, JRT.toLong(65.1));
		assertEquals(65L, JRT.toLong(65.9));
		assertEquals(65L, JRT.toLong(Integer.valueOf(65)));
		assertEquals(65L, JRT.toLong(Long.valueOf(65)));
		assertEquals(65L, JRT.toLong(Float.valueOf(65)));
		assertEquals(65L, JRT.toLong(Double.valueOf(65)));
		assertEquals(65L, JRT.toLong("65"));
		assertEquals(65L, JRT.toLong("65A"));
		assertEquals(65L, JRT.toLong("65A6666666666666666666666666600000000033333333333999999999999"));
		assertEquals(0L, JRT.toLong(""));
		Object nothing = null;
		assertEquals(0L, JRT.toLong(nothing));
	}

	// Ported from Printf4J's testToDouble: the same conversion now lives in
	// JRT.toDouble (they shared the same original implementation).
	@Test
	public void testToDouble() {
		assertEquals(65.0, JRT.toDouble('A'), 0.0);
		assertEquals(65.0, JRT.toDouble(65), 0.0);
		assertEquals(65.0, JRT.toDouble(65L), 0.0);
		assertEquals(65.0, JRT.toDouble(65.0), 0.0);
		assertEquals(65.1, JRT.toDouble(65.1), 0.0);
		assertEquals(65.9, JRT.toDouble(65.9), 0.0);
		assertEquals(65.0, JRT.toDouble(Integer.valueOf(65)), 0.0);
		assertEquals(65.0, JRT.toDouble(Long.valueOf(65)), 0.0);
		assertEquals(65.0, JRT.toDouble(Float.valueOf(65)), 0.0);
		assertEquals(65.0, JRT.toDouble(Double.valueOf(65)), 0.0);
		assertEquals(65.0, JRT.toDouble("65"), 0.0);
		assertEquals(65.0, JRT.toDouble("65A"), 0.0);
		assertEquals(65.0, JRT.toDouble("65A6666666666666666666666666600000000033333333333999999999999"), 0.0);
		assertEquals(65.0, JRT.toDouble("6.5E+1"), 0.0);
		assertEquals(0.0, JRT.toDouble(""), 0.0);
		Object nothing = null;
		assertEquals(0.0, JRT.toDouble(nothing), 0.0);
	}

	// ------------------------------------------------------------------
	// AWK-specific semantics beyond the original Printf4J suite.
	// ------------------------------------------------------------------

	@Test
	public void testStringConversionUsesAwkNumberToStringRules() {
		// The symptom from issue #528: an integral double prints without a
		// fractional part.
		assertEquals("1", sprintf("%s", 1.0));
		assertEquals("x[1]", sprintf("x[%s]", 1.0));
		// Non-integral values use CONVFMT.
		assertEquals("3.14159", sprintf("%s", 3.14159265));
		assertEquals("3.1", sprintf(Locale.US, "%.2g", "%s", 3.14159265));
		// CONVFMT that is not a %g-style format is honored verbatim.
		assertEquals("3.14", sprintf(Locale.US, "%.2f", "%s", 3.14159265));
		// An explicitly empty CONVFMT converts non-integral numbers to the
		// empty string, like gawk; integral values still print as integers.
		assertEquals("", sprintf(Locale.US, "", "%s", 1.5));
		assertEquals("1", sprintf(Locale.US, "", "%s", 1.0));
		// Integral values beyond the 64-bit range print in full.
		assertEquals("100000000000000000000", sprintf("%s", 1e20));
		// Exact long values are preserved.
		assertEquals("9223372036854775807", sprintf("%s", Long.MAX_VALUE));
	}

	@Test
	public void testCharConversion() {
		// A numeric value selects the corresponding code point.
		assertEquals("é", sprintf("%c", 233));
		// A code point beyond the BMP produces the full character.
		assertEquals(new String(Character.toChars(0x1F600)), sprintf("%c", 0x1F600));
		// A string value uses its first character.
		assertEquals("X", sprintf("%c", "XYZ"));
		// Width applies to %c like any other conversion.
		assertEquals("    A", sprintf("%5c", 65));
		assertEquals("A    ", sprintf("%-5c", 65));
	}

	@Test
	public void testDynamicWidthAndPrecision() {
		assertEquals("    3.14", sprintf("%*.*f", 8, 2, 3.14159));
		assertEquals("  3.14159", sprintf("%9s", 3.14159));
		// A negative dynamic width means left justification.
		assertEquals("42    ", sprintf("%*d", -6, 42));
		// Width and precision arguments are converted like AWK numbers.
		assertEquals("  3.14", sprintf("%*.*f", "6", "2", 3.14159));
	}

	@Test
	public void testPositionalSpecifiers() {
		assertEquals("b a", sprintf("%2$s %1$s", "a", "b"));
		assertEquals("a b a", sprintf("%1$s %2$s %1$s", "a", "b"));
		// Mixing positional and sequential specifiers is fatal, like gawk.
		assertThrows(AwkRuntimeException.class, () -> sprintf("%2$s %s", "a", "b"));
		// A zero positional index is fatal, like gawk.
		assertThrows(AwkRuntimeException.class, () -> sprintf("%0$s", "a"));
		// gawk-verified: an explicitly positioned star operand may accompany
		// sequential conversions.
		assertEquals("    a|5", sprintf("%*2$s|%s", "a", 5));
		assertEquals("a      5", sprintf("%1$s %2$*3$d", "a", 5, 6));
		// gawk-verified: a sequential star operand with a positional
		// conversion is a mixed-mode fatal error...
		assertThrows(AwkRuntimeException.class, () -> sprintf("%2$*d", 5, 12));
		// ...and an explicitly positioned unknown specifier pins the format
		// to positional mode even though it prints verbatim.
		assertThrows(AwkRuntimeException.class, () -> sprintf("%2$q|%d", 5, 12));
	}

	@Test
	public void testOutOfRangeFallbackKeepsSignFlagsAndPrecision() {
		// gawk-verified: the %g fallback for out-of-range %u/%o/%x/%X keeps
		// the sign, the precision, and the zero and '#' flags.
		assertEquals("-1.26765e+30", sprintf("%u", -Math.pow(2, 100)));
		assertEquals("1.2676506e+30", sprintf("%.10x", Math.pow(2, 100)));
		assertEquals("1.27e+30", sprintf("%#.3x", Math.pow(2, 100)));
		assertEquals("0000000001.26765e+30", sprintf("%020u", Math.pow(2, 100)));
	}

	@Test
	public void testAlternateFormKeepsDecimalPoint() {
		// gawk-verified: '#' forces a decimal point even when no fractional
		// digits remain.
		assertEquals("1.", sprintf("%#.1g", 1));
		assertEquals("1.e+04", sprintf("%#.1g", 12345));
		assertEquals("1.2e+04", sprintf("%#.2g", 12345));
		assertEquals("1.e+04", sprintf("%#.0e", 12345));
		assertEquals("1.00000", sprintf("%#g", 1));
	}

	@Test
	public void testZeroPrecisionZeroValue() {
		// gawk-verified: unsigned conversions print "0" when a nonzero value
		// truncates to zero, or when the '#' flag is given; signed %d prints
		// nothing in both zero cases.
		assertEquals("0", sprintf("%.0x", 0.1));
		assertEquals("0", sprintf("%.0u", 0.1));
		assertEquals("0", sprintf("%.0o", 0.1));
		assertEquals("", sprintf("%.0d", 0.1));
		assertEquals("0", sprintf("%#.0u", 0));
		assertEquals("", sprintf("%.0u", 0));
		assertEquals("", sprintf("%.0x", 0));
	}

	@Test
	public void testIntegerTruncationAndConversion() {
		// %d truncates toward zero.
		assertEquals("42", sprintf("%d", 42.7));
		assertEquals("-42", sprintf("%d", -42.7));
		// Strings convert with AWK's number rules (leading/trailing spaces,
		// exponent notation, numeric prefixes).
		assertEquals("1000", sprintf("%d", "1e3"));
		assertEquals("42", sprintf("%d", " 42 "));
		assertEquals("3", sprintf("%d", "+3.9"));
		assertEquals("0", sprintf("%d", "abc"));
		assertEquals("0", sprintf("%x", "abc"));
	}

	@Test
	public void testOutOfRangeIntegerConversions() {
		// Negative values wrap to unsigned 64-bit for %u, %o, %x.
		assertEquals("18446744073709551615", sprintf("%u", -1));
		assertEquals("ffffffffffffffff", sprintf("%x", -1));
		assertEquals("1777777777777777777777", sprintf("%o", -1));
		// 2^63 is out of the signed range but fits unsigned.
		assertEquals("9223372036854775808", sprintf("%d", 9.223372036854775808e18));
		// %d beyond 64 bits prints the full decimal expansion, like gawk.
		assertEquals("1267650600228229401496703205376", sprintf("%d", Math.pow(2, 100)));
		assertEquals("-1267650600228229401496703205376", sprintf("%d", -Math.pow(2, 100)));
		// %u, %o, and %x beyond 64 bits fall back to %g notation, like gawk.
		assertEquals("1.26765e+30", sprintf("%x", Math.pow(2, 100)));
		assertEquals("1.26765e+30", sprintf("%u", Math.pow(2, 100)));
		assertEquals("1.26765e+30", sprintf("%o", Math.pow(2, 100)));
	}

	@Test
	public void testHexFloat() {
		// %a uses Java's hexadecimal float notation (gawk documents %a as
		// C-library dependent); the 0x prefix stays ahead of zero padding.
		assertEquals("0x1.34ap10", sprintf("%a", 1234.5));
		assertEquals("0x00000000001.34ap10", sprintf("%020a", 1234.5));
		assertEquals("-0x1.34ap10", sprintf("%a", -1234.5));
	}

	@Test
	public void testNonFiniteValues() {
		assertEquals("nan", sprintf("%d", Double.NaN));
		assertEquals("inf", sprintf("%d", Double.POSITIVE_INFINITY));
		assertEquals("-inf", sprintf("%f", Double.NEGATIVE_INFINITY));
		assertEquals("INF", sprintf("%E", Double.POSITIVE_INFINITY));
		assertEquals("NAN", sprintf("%G", Double.NaN));
		assertEquals("nan", sprintf("%s", Double.NaN));
		assertEquals("inf", sprintf("%s", Double.POSITIVE_INFINITY));
		assertEquals("-inf", sprintf("%s", Double.NEGATIVE_INFINITY));
	}

	@Test
	public void testUnknownSpecifiersDoNotConsumeArguments() {
		// The unknown %q prints verbatim and its argument feeds %d instead.
		assertEquals("%q1", sprintf("%q%d", 1, 2));
		// %n is not an AWK conversion (Printf4J used to print a newline).
		assertEquals("a%nb", sprintf("a%nb"));
		// A dangling % prints verbatim.
		assertEquals("abc%", sprintf("abc%"));
	}

	@Test
	public void testNotEnoughArgumentsIsFatal() {
		assertThrows(AwkRuntimeException.class, () -> sprintf("%d %s", 1));
		assertThrows(AwkRuntimeException.class, () -> sprintf("%5s"));
		assertThrows(AwkRuntimeException.class, () -> sprintf("%*d", 5));
	}

	@Test
	public void testExtraArgumentsAreIgnored() {
		assertEquals("a b", sprintf("%s %s", "a", "b", "c"));
	}

	@Test
	public void testGroupingFlag() {
		assertEquals("1,234,567", sprintf("%'d", 1234567));
		assertEquals("1,234,567", sprintf("%'u", 1234567));
		assertEquals("1,234,567.89", sprintf("%'.2f", 1234567.891));
		assertEquals("1.234.567", sprintf(Locale.GERMANY, AwkPrintf.DEFAULT_CONVFMT, "%'d", 1234567));
		// gawk-verified: %g groups in fixed notation only, and octal and
		// hexadecimal output is never grouped.
		assertEquals("12,345", sprintf("%'g", 12345));
		assertEquals("1,234,567.25", sprintf("%'.10g", 1234567.25));
		assertEquals("1.234567e+06", sprintf("%'e", 1234567));
		assertEquals("2540be400", sprintf("%'x", 10000000000L));
		assertEquals("1747", sprintf("%'o", 999));
	}

	@Test
	public void testLocaleDecimalSeparator() {
		assertEquals("3,14", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%.2f", 3.14159));
		assertEquals("3,14159", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%g", 3.14159));
		// The '#' decimal point follows the locale as well.
		assertEquals("1,", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%#.0f", 1));
		assertEquals("1,e+04", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%#.0e", 12345));
		assertEquals("1,e+04", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%#.1g", 12345));
		assertEquals("0,00000", sprintf(Locale.FRANCE, AwkPrintf.DEFAULT_CONVFMT, "%#g", 0));
	}

	@Test
	public void testAwkSinkSprintf() {
		// The sink-level sprintf converts %s operands with the supplied
		// CONVFMT, and overriding it customizes formatting.
		AwkSink plainSink = new AwkSink() {

			@Override
			public void print(String ofs, String ors, String ofmt, Object... values) {
				// not needed for this test
			}

			@Override
			public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values) {
				// not needed for this test
			}
		};
		assertEquals("3.1", plainSink.sprintf("%.2g", "%s", 3.14159265));

		AwkSink customSink = new AwkSink() {

			@Override
			public void print(String ofs, String ors, String ofmt, Object... values) {
				// not needed for this test
			}

			@Override
			public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values) {
				// not needed for this test
			}

			@Override
			public String sprintf(String convfmt, String format, Object... values) {
				return "[" + super.sprintf(convfmt, format, values) + "]";
			}
		};
		assertEquals("[3.1]", customSink.sprintf("%.2g", "%s", 3.14159265));
	}

	@Test
	public void testFormatCacheReuseAndEviction() {
		// Repeated use of one format must keep working from the parsed-format
		// cache, and cycling through more distinct formats than the cache
		// bound must keep producing correct results after eviction.
		for (int i = 0; i < 600; i++) {
			String format = "#" + i + ":%0" + (i % 7 + 2) + "d/%s";
			String expected1 = sprintf(format, i, "a");
			String expected2 = sprintf(format, i, "a");
			assertEquals(expected1, expected2);
			assertEquals("#" + i + ":", expected1.substring(0, expected1.indexOf(':') + 1));
		}
		assertEquals("042", sprintf("%03d", 42));
		assertEquals("042", sprintf("%03d", 42));
	}

	@Test
	public void testErrorPrecedenceMatchesSequentialProcessing() {
		// Errors must surface in the order a single left-to-right pass over
		// the format string produces them, even though parsing is cached:
		// a missing argument for an earlier conversion wins over a later
		// invalid specifier, and a width star consumes its argument before
		// an invalid precision star is diagnosed.
		AwkRuntimeException missingFirst = assertThrows(AwkRuntimeException.class, () -> sprintf("%d %*2d"));
		assertEquals("not enough arguments to satisfy format string `%d %*2d'", missingFirst.getMessage());
		AwkRuntimeException invalidStar = assertThrows(AwkRuntimeException.class, () -> sprintf("%d %*2d", 1, 2));
		assertEquals("no `$' supplied for positional field width or precision in `%d %*2d'", invalidStar.getMessage());
		AwkRuntimeException widthBeforeError = assertThrows(AwkRuntimeException.class, () -> sprintf("%*.*2d"));
		assertEquals("not enough arguments to satisfy format string `%*.*2d'", widthBeforeError.getMessage());
		AwkRuntimeException precisionStar = assertThrows(AwkRuntimeException.class, () -> sprintf("%*.*2d", 5, 3));
		assertEquals("no `$' supplied for positional field width or precision in `%*.*2d'", precisionStar.getMessage());
	}

	@Test
	public void testToAwkString() {
		assertEquals("", AwkPrintf.toAwkString(null, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("text", AwkPrintf.toAwkString("text", AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("1", AwkPrintf.toAwkString(1.0, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("0.1", AwkPrintf.toAwkString(0.1, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("3.14159", AwkPrintf.toAwkString(3.14159265, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("100000000000000000000", AwkPrintf.toAwkString(1e20, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("9223372036854775807", AwkPrintf.toAwkString(Long.MAX_VALUE, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("nan", AwkPrintf.toAwkString(Double.NaN, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("inf", AwkPrintf.toAwkString(Double.POSITIVE_INFINITY, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
		assertEquals("-inf", AwkPrintf.toAwkString(Double.NEGATIVE_INFINITY, AwkPrintf.DEFAULT_CONVFMT, Locale.US));
	}
}
