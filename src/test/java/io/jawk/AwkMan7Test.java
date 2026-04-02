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

import java.util.Locale;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AwkMan7Test {

	private static Locale defaultLocale;

	@BeforeClass
	public static void captureLocale() {
		defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.US);
	}

	@AfterClass
	public static void resetLocale() {
		if (defaultLocale != null) {
			Locale.setDefault(defaultLocale);
		}
	}

	@Test
	public void spec01VisibleInBegin() throws Exception {
		AwkTestSupport
				.awkTest("1. -v visible in BEGIN")
				.script("BEGIN{print X}")
				.preassign("X", "42")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void spec02OperandAssignmentAfterBegin() throws Exception {
		AwkTestSupport
				.awkTest("2. Operand assignment after BEGIN")
				.script("BEGIN{print (X==\"\"?\"unset\":\"set\")} {print X \":\" $0}")
				.file("a.txt", "one\n")
				.operand("X=abc", "{{a.txt}}")
				.expectLines("unset", "abc:one")
				.runAndAssert();
	}

	@Test
	public void spec03OperandAssignmentPerFile() throws Exception {
		AwkTestSupport
				.awkTest("3. Operand assignment per file")
				.script("{print FILENAME \":\" X \":\" $0}")
				.file("A", "a1\na2\n")
				.file("B", "b1\nb2\n")
				.operand("X=AA", "{{A}}", "X=BB", "{{B}}")
				.expectLines("{{A}}:AA:a1", "{{A}}:AA:a2", "{{B}}:BB:b1", "{{B}}:BB:b2")
				.runAndAssert();
	}

	@Test
	public void spec04BeginOnlyDoesNotReadFiles() throws Exception {
		AwkTestSupport
				.awkTest("4. BEGIN-only does not read files")
				.script("BEGIN{print \"hi\"}")
				.file("a.txt", "anything\n")
				.operand("{{a.txt}}")
				.expectLines("hi")
				.runAndAssert();
	}

	@Test
	public void spec05EndReadsInput() throws Exception {
		AwkTestSupport
				.awkTest("5. END reads input")
				.script("END{print NR}")
				.file("a.txt", "x\ny\n")
				.operand("{{a.txt}}")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void spec06DefaultRsIsNewline() throws Exception {
		AwkTestSupport
				.awkTest("6. Default RS is newline")
				.script("{print $0}")
				.stdin("a\nb\nc\n")
				.expectLines("a", "b", "c")
				.runAndAssert();
	}

	@Test
	public void spec07RsSingleCharacter() throws Exception {
		AwkTestSupport
				.awkTest("7. RS single character")
				.script("BEGIN{RS=\",\"}{print $0}")
				.stdin("a,b,c")
				.expectLines("a", "b", "c")
				.runAndAssert();
	}

	@Test
	public void spec08RsEmptyParagraphMode() throws Exception {
		String escapedEOL = System.lineSeparator().replace("\n", "\\\\n").replace("\r", "\\\\r");
		AwkTestSupport
				.awkTest("8. RS empty paragraph mode")
				.script(
						"BEGIN{RS=\"\"}{n=split($0, lines, \"" + escapedEOL
								+ "\"); count=0; for(i=1;i<=n;i++){if(lines[i]!=\"\"){count++}}; print \"REC:\" NR \":\" count \"lines\"}")
				.stdin("p1-line1\n\n\np2-line1\np2-line2\n\np3\n")
				.expectLines("REC:1:4lines")
				.runAndAssert();
	}

	@Test
	public void spec09DefaultFsCollapsesBlanks() throws Exception {
		AwkTestSupport
				.awkTest("9. Default FS collapses blanks")
				.script("{print NF \":\" $1 \":\" $2 \":\" $3}")
				.stdin(" a\tb   c")
				.expectLines("3:a:b:c")
				.runAndAssert();
	}

	@Test
	public void spec10FsCommaPreservesEmpties() throws Exception {
		AwkTestSupport
				.awkTest("10. FS comma preserves empties")
				.script("BEGIN{FS=\",\"}{print NF \":\" $1 \":\" $2 \":\" $3 \":\" $4}")
				.stdin("a,,b,c,")
				.expectLines("5:a::b:c")
				.runAndAssert();
	}

	@Test
	public void spec11FsAsEre() throws Exception {
		AwkTestSupport
				.awkTest("11. FS as ERE")
				.script("BEGIN{FS=\"[ ,:]+\"}{print NF \":\" $1 \":\" $2 \":\" $3}")
				.stdin("a,, b:::c")
				.expectLines("3:a:b:c")
				.runAndAssert();
	}

	@Test
	public void spec12FsChangeAffectsFutureRecords() throws Exception {
		AwkTestSupport
				.awkTest("12. FS change affects future records")
				.script("NR==1{print NF \":\" $1 \":\" $2; FS=\",\"} NR==2{print NF \":\" $1 \":\" $2}")
				.stdin("a b\nc,d\n")
				.expectLines("2:a:b", "2:c:d")
				.runAndAssert();
	}

	@Test
	public void spec13AssigningToZeroRecomputesFields() throws Exception {
		AwkTestSupport
				.awkTest("13. Assigning to $0 recomputes fields")
				.script("{$0=\"p q r\"; print NF \":\" $2}")
				.stdin("x y\n")
				.expectLines("3:q")
				.runAndAssert();
	}

	@Test
	public void spec14AssigningToFieldRebuildsZero() throws Exception {
		AwkTestSupport
				.awkTest("14. Assigning to field rebuilds $0")
				.script("{$2=\"X\"; print $0}")
				.stdin("a b c\n")
				.expectLines("a X c")
				.runAndAssert();
	}

	@Test
	public void spec15ReferencingFieldPastNf() throws Exception {
		AwkTestSupport
				.awkTest("15. Referencing field past NF")
				.script("{print \"(\" $(NF+1) \"):\" NF}")
				.stdin("a b\n")
				.expectLines("():2")
				.runAndAssert();
	}

	@Test
	public void spec16AssigningBeyondNfGrowsRecord() throws Exception {
		AwkTestSupport
				.awkTest("16. Assigning beyond NF grows record")
				.script("BEGIN{OFS=\",\"} {$(NF+2)=5; print NF \":\" $0}")
				.stdin("a b\n")
				.expectLines("4:a,b,,5")
				.runAndAssert();
	}

	@Test
	public void spec17MissingActionPrintsRecord() throws Exception {
		AwkTestSupport
				.awkTest("17. Missing action prints record")
				.script("1")
				.stdin("x\n")
				.expectLines("x")
				.runAndAssert();
	}

	@Test
	public void spec18RegexPatternMatchesRecords() throws Exception {
		AwkTestSupport
				.awkTest("18. Regex pattern matches records")
				.script("/ar/")
				.stdin("foo\nbar\n")
				.expectLines("bar")
				.runAndAssert();
	}

	@Test
	public void spec19PatternOrderEvaluation() throws Exception {
		AwkTestSupport
				.awkTest("19. Pattern order evaluation")
				.script("{flag=1} flag==1 {print \"seen\"}")
				.stdin("x\n")
				.expectLines("seen")
				.runAndAssert();
	}

	@Test
	public void spec20RangePatternWithinFile() throws Exception {
		AwkTestSupport
				.awkTest("20. Range pattern within file")
				.script("/b/,/d/")
				.stdin("a\nb\nc\nd\ne\n")
				.expectLines("b", "c", "d")
				.runAndAssert();
	}

	@Test
	public void spec21RangePatternRepeats() throws Exception {
		AwkTestSupport
				.awkTest("21. Range pattern repeats")
				.script("/start/,/end/")
				.stdin("start\nx\nend\nx\nend\n")
				.expectLines("start", "x", "end")
				.runAndAssert();
	}

	@Test
	public void spec22UnanchoredRegexMatchesSubstring() throws Exception {
		AwkTestSupport
				.awkTest("22. Unanchored regex matches substring")
				.script("$0 ~ /b/")
				.stdin("abc\n")
				.expectLines("abc")
				.runAndAssert();
	}

	@Test
	public void spec23AnchoredRegex() throws Exception {
		AwkTestSupport
				.awkTest("23. Anchored regex")
				.script("$0 ~ /^abc$/")
				.stdin("abc\nabcx\n")
				.expectLines("abc")
				.runAndAssert();
	}

	@Test
	public void spec24LiteralSlashInRegex() throws Exception {
		AwkTestSupport
				.awkTest("24. Literal slash in regex")
				.script("$0 ~ /a\\/b/")
				.stdin("a/b\n")
				.expectLines("a/b")
				.runAndAssert();
	}

	@Test
	public void spec25RegexFromVariable() throws Exception {
		AwkTestSupport
				.awkTest("25. Regex from variable")
				.script("BEGIN{re=\"a\\/b\"} $0 ~ re {print $0}")
				.stdin("path a/b\n")
				.expectLines("path a/b")
				.runAndAssert();
	}

	@Test
	public void spec26RegexStandalonePattern() throws Exception {
		AwkTestSupport
				.awkTest("26. Regex standalone pattern")
				.script("/A/")
				.stdin("xxAyy\n")
				.expectLines("xxAyy")
				.runAndAssert();
	}

	@Test
	public void spec27MultipleBeginOrder() throws Exception {
		AwkTestSupport
				.awkTest("27. Multiple BEGIN order")
				.script("BEGIN{print \"1\"} BEGIN{print \"2\"}")
				.expectLines("1", "2")
				.runAndAssert();
	}

	@Test
	public void spec28MultipleEndOrder() throws Exception {
		AwkTestSupport
				.awkTest("28. Multiple END order")
				.script("END{print \"E1\"} END{print \"E2\"}")
				.stdin("x\ny\n")
				.expectLines("E1", "E2")
				.runAndAssert();
	}

	@Test
	public void spec29GetlineInBeginConsumesRecord() throws Exception {
		AwkTestSupport
				.awkTest("29. getline in BEGIN consumes record")
				.script("BEGIN{getline; print \"got:\" $0} {print \"line:\" $0}")
				.stdin("L1\nL2\n")
				.expectLines("got:L1", "line:L2")
				.runAndAssert();
	}

	@Test
	public void spec30FilenameVisibility() throws Exception {
		AwkTestSupport
				.awkTest("30. FILENAME visibility")
				.script("BEGIN{print (FILENAME==\"\"?\"undef\":\"bad\")} {last=FILENAME} END{print last}")
				.file("A", "a\n")
				.file("B", "b\n")
				.operand("{{A}}", "{{B}}")
				.expectLines("undef", "{{B}}")
				.runAndAssert();
	}

	@Test
	public void spec31NrVsFnr() throws Exception {
		AwkTestSupport
				.awkTest("31. NR vs FNR")
				.script("{print FILENAME \":\" FNR \":\" NR}")
				.file("A", "x\ny\n")
				.file("B", "z\n")
				.operand("{{A}}", "{{B}}")
				.expectLines("{{A}}:1:1", "{{A}}:2:2", "{{B}}:1:3")
				.runAndAssert();
	}

	@Test
	public void spec32ArgvManipulationSkipsFile() throws Exception {
		AwkTestSupport
				.awkTest("32. ARGV manipulation skips file")
				.script("BEGIN{ARGV[1]=\"\"} {print FILENAME \":\" $0}")
				.file("A", "a\n")
				.file("B", "b\n")
				.operand("{{A}}", "{{B}}")
				.expectLines("{{B}}:b")
				.runAndAssert();
	}

	@Test
	public void spec33OfmtVsConvfmt() throws Exception {
		AwkTestSupport
				.awkTest("33. OFMT vs CONVFMT")
				.script("BEGIN{OFMT=\"%.2f\"; CONVFMT=\"%.3f\"; x=1.2345; print x; s=x \"\"; print s}")
				.expectLines("1.23", "1.24")
				.runAndAssert();
	}

	@Test
	public void spec34IntegersStringifyWithPercentD() throws Exception {
		AwkTestSupport
				.awkTest("34. Integers stringify with %d")
				.script("BEGIN{CONVFMT=\"%.3f\"; x=12; s=x \"\"; print s}")
				.expectLines("12")
				.runAndAssert();
	}

	@Test
	public void spec35NumericVsStringComparison() throws Exception {
		AwkTestSupport
				.awkTest("35. Numeric vs string comparison")
				.script("{print ($0<10)?\"Y\":\"N\"}")
				.stdin("2\n2a\n")
				.expectLines("Y", "N")
				.runAndAssert();
	}

	@Test
	public void spec36ExponentiationRightAssociative() throws Exception {
		AwkTestSupport
				.awkTest("36. Exponentiation is right-associative")
				.script("BEGIN{print 2^3^2}")
				.expectLines("512")
				.runAndAssert();
	}

	@Test
	public void spec37ModulusUsesFmodSemantics() throws Exception {
		AwkTestSupport
				.awkTest("37. Modulus uses fmod semantics")
				.script("BEGIN{print (-5)%2}")
				.expectLines("-1")
				.runAndAssert();
	}

	@Test
	public void spec38ConcatenationPrecedence() throws Exception {
		AwkTestSupport
				.awkTest("38. Concatenation precedence")
				.script("BEGIN{print 1 2+3}")
				.expectLines("15")
				.runAndAssert();
	}

	@Test
	public void spec39TernaryRightAssociativity() throws Exception {
		AwkTestSupport
				.awkTest("39. Ternary right associativity")
				.script("BEGIN{print (0?1:0?2:3)}")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void spec40PreVsPostIncrement() throws Exception {
		AwkTestSupport
				.awkTest("40. Pre vs post increment")
				.script("BEGIN{i=0; print i++; print i; j=0; print ++j; print j}")
				.expectLines("0", "1", "1", "1")
				.runAndAssert();
	}

	@Test
	public void spec41FieldExpressionIndex() throws Exception {
		AwkTestSupport
				.awkTest("41. Field expression index")
				.script("{i=2; print $(i)}")
				.stdin("a b c\n")
				.expectLines("b")
				.runAndAssert();
	}

	@Test
	public void spec42PrintWithEmptyExprList() throws Exception {
		AwkTestSupport
				.awkTest("42. Print with empty expr list")
				.script("{print; print $0}")
				.stdin("hello\n")
				.expectLines("hello", "hello")
				.runAndAssert();
	}

	@Test
	public void spec43OfsAndOrs() throws Exception {
		AwkTestSupport
				.awkTest("43. OFS and ORS")
				.script("BEGIN{OFS=\",\"; ORS=\"|\"} {print $1,$2; print $2,$1}")
				.stdin("a b\n")
				.expect("a,b|b,a|")
				.runAndAssert();
	}

	@Test
	public void spec44PrintfWidthAndPrecision() throws Exception {
		AwkTestSupport
				.awkTest("44. printf width and precision")
				.script("BEGIN{printf \"%.3f\\n\", 1.23456; printf \"%5s\\n\", \"x\"}")
				.expectLines("1.235", "    x")
				.runAndAssert();
	}

	@Test
	public void spec45PrintfDoesNotUnescapeVariableFormat() throws Exception {
		AwkTestSupport
				.awkTest("45. printf does not unescape variable format")
				.script("BEGIN{fmt=\"\\\\n\"; printf fmt; printf \"\\n\"}")
				.expect("\\n\n")
				.runAndAssert();
	}

	@Test
	public void spec46WriteToFileAndClose() throws Exception {
		AwkTestSupport
				.awkTest("46. Write to file and close")
				.path("tOut")
				.script(
						"BEGIN{f=\"{{tOut}}\"; print \"X\" > f; rc=close(f); print rc; while ((getline line < f)>0) print \"line: \" line \".\"; close(f)}")
				// .expectLines("0", "line: X")
				.expect("0\nline: X.\n")
				.runAndAssert();
	}

	@Test
	public void spec47AppendWithRedirect() throws Exception {
		AwkTestSupport
				.awkTest("47. Append with redirect")
				.path("tAppend")
				.script(
						"BEGIN{f=\"{{tAppend}}\"; print \"A\" >> f; print \"B\" >> f; close(f); while ((getline line < f)>0) print line; close(f)}")
				.expectLines("A", "B")
				.runAndAssert();
	}

	@Test
	public void spec48PipeToCommandProducesOutput() throws Exception {
		AwkTestSupport
				.awkTest("48. Pipe to command produces output")
				.script("{print $0 | \"sed s/a/A/\"} END{close(\"sed s/a/A/\")}")
				.stdin("a\nb\n")
				.posixOnly()
				.expectLines("A", "b")
				.runAndAssert();
	}

	@Test
	public void spec49CommandPipeGetline() throws Exception {
		AwkTestSupport
				.awkTest("49. Command pipe getline")
				.script("BEGIN{cmd=\"printf abc\\n\"; n=(cmd | getline x); print n \":\" x; close(cmd)}")
				.posixOnly()
				.expectLines("1:abc")
				.runAndAssert();
	}

	@Test
	public void spec50GetlineFromFileReturnsCounts() throws Exception {
		AwkTestSupport
				.awkTest("50. getline from file returns counts")
				.file("fileX", "L1\nL2\n")
				.script("BEGIN{f=\"{{fileX}}\"; n=0; while ((rc=(getline ln < f))>0){n++} print n \":\" rc; close(f)}")
				.expectLines("2:0")
				.runAndAssert();
	}

	@Test
	public void spec51SystemExitStatus() throws Exception {
		AwkTestSupport
				.awkTest("51. system() exit status")
				.script("BEGIN{print system(\"sh -c true\"); print (system(\"sh -c false\")!=0?\"NZ\":\"Z\")}")
				.posixOnly()
				.expectLines("0", "NZ")
				.runAndAssert();
	}

	@Test
	public void spec52NextSkipsRemainingRules() throws Exception {
		AwkTestSupport
				.awkTest("52. next skips remaining rules")
				.script("{print \"A:\" $0; next; print \"B:\" $0}")
				.stdin("a\nb\n")
				.expectLines("A:a", "A:b")
				.runAndAssert();
	}

	@Test
	public void spec53EmulateNextfileSkipsToNextFile() throws Exception {
		AwkTestSupport
				.awkTest("53. emulate nextfile skips to next file")
				.script(
						"FNR==1{print $0; fname=FILENAME; while (getline > 0) { if (FILENAME!=fname) {print $0; break} } next} {print \"NEVER\"}")
				.file("A", "a1\na2\n")
				.file("B", "b1\n")
				.operand("{{A}}", "{{B}}")
				.expectLines("a1", "b1")
				.runAndAssert();
	}

	@Test
	public void spec54ExitStillRunsEnd() throws Exception {
		AwkTestSupport
				.awkTest("54. exit still runs END")
				.script("{print; exit} END{print \"E\"}")
				.stdin("x\ny\n")
				.expectLines("x", "E")
				.runAndAssert();
	}

	@Test
	public void spec55BeginExitCode() throws Exception {
		AwkTestSupport
				.awkTest("55. BEGIN exit code")
				.script("BEGIN{exit 3}")
				.expect("")
				.expectExit(3)
				.runAndAssert();
	}

	@Test
	public void spec56LengthDefaultArgument() throws Exception {
		AwkTestSupport
				.awkTest("56. length() default argument")
				.script("{print length()}")
				.stdin("abcd\n")
				.expectLines("4")
				.runAndAssert();
	}

	@Test
	public void spec57IndexFunction() throws Exception {
		AwkTestSupport
				.awkTest("57. index function")
				.script("BEGIN{print index(\"banana\",\"na\"); print index(\"banana\",\"x\")}")
				.expectLines("3", "0")
				.runAndAssert();
	}

	@Test
	public void spec58SubstrVariations() throws Exception {
		AwkTestSupport
				.awkTest("58. substr variations")
				.script("BEGIN{print substr(\"hello\",2,3); print substr(\"hello\",4)}")
				.expectLines("ell", "lo")
				.runAndAssert();
	}

	@Test
	public void spec59MatchUpdatesRstartAndRlength() throws Exception {
		AwkTestSupport
				.awkTest("59. match updates RSTART and RLENGTH")
				.script("BEGIN{print match(\"abc\",\"abc\"), RSTART, RLENGTH; print match(\"xyz\",\"a\"), RSTART, RLENGTH}")
				.expectLines("1 1 3", "0 0 -1")
				.runAndAssert();
	}

	@Test
	public void spec60SubReplacesFirstOccurrence() throws Exception {
		AwkTestSupport
				.awkTest("60. sub replaces first occurrence")
				.script("BEGIN{s=\"foo\"; n=sub(/f/, \"X\", s); print n \":\" s}")
				.expectLines("1:Xoo")
				.runAndAssert();
	}

	@Test
	public void spec61GsubEscapesAmpersand() throws Exception {
		AwkTestSupport
				.awkTest("61. gsub escapes ampersand")
				.script("BEGIN{s=\"aba\"; n=gsub(/a/,\"\\\\&X\",s); print n \":\" s}")
				.expectLines("2:&Xb&X")
				.runAndAssert();
	}

	@Test
	public void spec62SplitClearsArrayAndCounts() throws Exception {
		AwkTestSupport
				.awkTest("62. split clears array and counts")
				.script("BEGIN{delete a; n=split(\"a::b:c\", a, \"[:]+\"); print n \":\" a[1] \":\" a[2] \":\" a[3]}")
				.expectLines("3:a:b:c")
				.runAndAssert();
	}

	@Test
	public void spec63SprintfFormatting() throws Exception {
		AwkTestSupport
				.awkTest("63. sprintf formatting")
				.script("BEGIN{print sprintf(\"<%6.2f>\", 1.234)}")
				.expectLines("<  1.23>")
				.runAndAssert();
	}

	@Test
	public void spec64IntTruncatesTowardZero() throws Exception {
		AwkTestSupport
				.awkTest("64. int truncates toward zero")
				.script("BEGIN{print int(-1.7)}")
				.expectLines("-1")
				.runAndAssert();
	}

	@Test
	public void spec65SrandProducesRepeatableSequence() throws Exception {
		AwkTestSupport
				.awkTest("65. srand produces repeatable sequence")
				.script("BEGIN{srand(1); r1=rand(); srand(1); r2=rand(); print (r1==r2)?1:0}")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void spec66SqrtPositive() throws Exception {
		AwkTestSupport
				.awkTest("66. sqrt positive")
				.script("BEGIN{printf \"%.5f\\n\", sqrt(9)}")
				.expectLines("3.00000")
				.runAndAssert();
	}

	@Test
	public void spec67InOperatorForArrays() throws Exception {
		AwkTestSupport
				.awkTest("67. in operator for arrays")
				.script("BEGIN{print ((1 in a)?1:0); a[1]=0; print ((1 in a)?1:0)}")
				.expectLines("0", "1")
				.runAndAssert();
	}

	@Test
	public void spec68MultidimensionalArraysViaSubsep() throws Exception {
		AwkTestSupport
				.awkTest("68. Multidimensional arrays via SUBSEP")
				.script("BEGIN{a[1,2]=42; print a[1 SUBSEP 2]}")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void spec69DeleteElementAndArray() throws Exception {
		AwkTestSupport
				.awkTest("69. delete element and array")
				.script(
						"BEGIN{a[1]=10; a[2]=20; delete a[1]; c=0; for(i in a)c++; print c; delete a; c=0; for(i in a)c++; print c}")
				.expectLines("1", "0")
				.runAndAssert();
	}

	@Test
	public void spec70SplitYieldsNumericStrings() throws Exception {
		AwkTestSupport
				.awkTest("70. split yields numeric strings")
				.script("BEGIN{n=split(\"10 20\",a,\" \" ); print (a[1]+0)+(a[2]+0)}")
				.expectLines("30")
				.runAndAssert();
	}

	@Test
	public void spec71ScalarsPassedByValue() throws Exception {
		AwkTestSupport
				.awkTest("71. Scalars passed by value")
				.script("function f(x){x=5} BEGIN{y=3; f(y); print y}")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void spec72ArraysPassedByReference() throws Exception {
		AwkTestSupport
				.awkTest("72. Arrays passed by reference")
				.script("function g(arr){arr[1]=\"X\"} BEGIN{a[1]=\"A\"; g(a); print a[1]}")
				.expectLines("X")
				.runAndAssert();
	}

	@Test
	public void spec73MissingActualArgsDefaultToEmpty() throws Exception {
		AwkTestSupport
				.awkTest("73. Missing actual args default to empty")
				.script("function f(u,v){print (u==\"\"?\"E\":u) \":\" (v==\"\"?\"E\":v)} BEGIN{f(1)}")
				.expectLines("1:E")
				.runAndAssert();
	}

	@Test
	public void spec74FunctionCallRequiresNoWhitespace() throws Exception {
		AwkTestSupport
				.awkTest("74. Function call requires no whitespace")
				.script("function f(x){return x} BEGIN{print f(7)}")
				.expectLines("7")
				.runAndAssert();
	}

	@Test
	public void spec75RecursiveFunction() throws Exception {
		AwkTestSupport
				.awkTest("75. Recursive function")
				.script("function fact(n){return n? n*fact(n-1):1} BEGIN{print fact(5)}")
				.expectLines("120")
				.runAndAssert();
	}

	@Test
	public void spec76BareGetlineUpdatesRecordState() throws Exception {
		AwkTestSupport
				.awkTest("76. Bare getline updates record state")
				.file("A", "a\nb\n")
				.script("BEGIN{print NR \":\" FNR} {if (NR==1){getline; print $0 \":\" NF \":\" NR \":\" FNR; exit}}")
				.operand("{{A}}")
				.expectLines("0:0", "b:1:2:2")
				.runAndAssert();
	}

	@Test
	public void spec77GetlineVarLeavesZeroUnchanged() throws Exception {
		AwkTestSupport
				.awkTest("77. getline var leaves $0 unchanged")
				.file("A", "a\nb\n")
				.script("{ if (FNR==1) { getline line; print line \":\" $0 \":\" NF \":\" NR \":\" FNR; exit } }")
				.operand("{{A}}")
				.expectLines("b:a:1:2:2")
				.runAndAssert();
	}

	@Test
	public void spec78GetlineFromNamedFileAndClose() throws Exception {
		AwkTestSupport
				.awkTest("78. getline from named file and close")
				.file("f1", "L1\nL2\n")
				.script("BEGIN{f=\"{{f1}}\"; getline x < f; getline y < f; print x \"-\" y; print (close(f)==0)}")
				.expectLines("L1-L2", "1")
				.runAndAssert();
	}

	@Test
	public void spec79BeginNrFnrZeroAndGetlineSetsNf() throws Exception {
		AwkTestSupport
				.awkTest("79. BEGIN NR/FNR zero and getline sets NF")
				.script("BEGIN{print NR \":\" FNR; getline; print NF \":\" $0; exit}")
				.stdin("x\n")
				.expectLines("0:0", "1:x")
				.runAndAssert();
	}

	@Test
	public void spec80EndSeesTotalNr() throws Exception {
		AwkTestSupport
				.awkTest("80. END sees total NR")
				.script("END{print NR}")
				.stdin("a\nb\nc\n")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void spec81StringComparisonInCLocale() throws Exception {
		AwkTestSupport
				.awkTest("81. String comparison in C locale")
				.script("BEGIN{print (\"abc\"<\"abd\")?\"Y\":\"N\"}")
				.expectLines("Y")
				.runAndAssert();
	}

	@Test
	public void spec82NumericComparisonWithNumericStrings() throws Exception {
		AwkTestSupport
				.awkTest("82. Numeric comparison with numeric strings")
				.script("BEGIN{print (10<\"2\")?\"Y\":\"N\"; print (10<(\" 2\"))?\"Y\":\"N\"}")
				.expectLines("N", "N")
				.runAndAssert();
	}

	@Test
	public void spec83AssignmentsInArgv() throws Exception {
		AwkTestSupport
				.awkTest("83. Assignments in ARGV")
				.script("{print X \":\" $0}")
				.file("A", "a\n")
				.operand("X=Q", "{{A}}")
				.expectLines("Q:a")
				.runAndAssert();
	}

	@Test
	public void spec84AppendFileViaArgvInBegin() throws Exception {
		AwkTestSupport
				.awkTest("84. Append file via ARGV in BEGIN")
				.script("BEGIN{ARGC=ARGC+1; ARGV[ARGC-1]=\"{{A}}\"} {print $0}")
				.file("A", "a\n")
				.operand("{{A}}")
				.expectLines("a", "a")
				.runAndAssert();
	}

	@Test
	public void spec85SemicolonStatementSeparators() throws Exception {
		AwkTestSupport
				.awkTest("85. Semicolon statement separators")
				.script("{a=1; b=2; print a+b}")
				.stdin("x\n")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void spec86ForInIterationCount() throws Exception {
		AwkTestSupport
				.awkTest("86. for-in iteration count")
				.script("BEGIN{a[\"x\"]=1; a[\"y\"]=2; c=0; for (i in a) c++; print c}")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void spec87RebuildZeroWithCustomOfs() throws Exception {
		AwkTestSupport
				.awkTest("87. Rebuild $0 with custom OFS")
				.script("BEGIN{OFS=\"|\"} {$2=\"\"; print $0 \":\" NF}")
				.stdin("a b\n")
				.expectLines("a|:2")
				.runAndAssert();
	}

	@Test
	public void spec88SplitWithLiteralSpaceSeparator() throws Exception {
		AwkTestSupport
				.awkTest("88. split with literal space separator")
				.script("BEGIN{n=split(\" a\\t b  c \", a, \" \" ); print n \":\" a[1] \":\" a[2] \":\" a[3]}")
				.expectLines("3:a:b:c")
				.runAndAssert();
	}

	@Test
	public void spec89FsAsClassRepetition() throws Exception {
		AwkTestSupport
				.awkTest("89. FS as class repetition")
				.script("BEGIN{FS=\"[ ,:]+\"}{print NF \":\" $1 \":\" $2}")
				.stdin("a  , , : :b\n")
				.expectLines("2:a:b")
				.runAndAssert();
	}

	@Test
	public void spec90RsEmptyIgnoresLeadingBlanks() throws Exception {
		String escapedEOL = System.lineSeparator().replace("\n", "\\\\n").replace("\r", "\\\\r");
		AwkTestSupport
				.awkTest("90. RS empty ignores leading blanks")
				.script(
						"BEGIN{RS=\"\"}{gsub(\"(" + escapedEOL
								+ ")+\",\" \",$0); sub(\"^ +\",\"\",$0); sub(\" +$\",\"\",$0); print \"REC-\" NR \":\" $0}")
				.stdin("\n\npara1\n\n\npara2\n\n")
				.expectLines("REC-1:para1 para2")
				.runAndAssert();
	}

	@Test
	public void spec91RangeResetsPerFile() throws Exception {
		AwkTestSupport
				.awkTest("91. Range resets per file")
				.script("/1/,/2/ {print FILENAME \":\" $0}")
				.file("A", "1\nX\n2\n")
				.file("B", "1\n2\n")
				.operand("{{A}}", "{{B}}")
				.expectLines("{{A}}:1", "{{A}}:X", "{{A}}:2", "{{B}}:1", "{{B}}:2")
				.runAndAssert();
	}

	@Test
	public void spec92EnvironExposesEnvironmentVariable() throws Exception {
		AwkTestSupport
				.awkTest("92. ENVIRON exposes environment variable")
				.script("BEGIN{print ENVIRON[\"AWK_TEST\"]}")
				.expectLines(System.getenv().getOrDefault("AWK_TEST", ""))
				.runAndAssert();
	}

	@Test
	public void spec93MatchUsesRegexVariable() throws Exception {
		AwkTestSupport
				.awkTest("93. match uses regex variable")
				.script("BEGIN{re=\"fo+\"; print match(\"foo\",re)}")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void spec94ActionOnlyPrintsRecords() throws Exception {
		AwkTestSupport
				.awkTest("94. Action-only prints records")
				.script("{print NR \":\" $0}")
				.stdin("x\ny\n")
				.expectLines("1:x", "2:y")
				.runAndAssert();
	}

	@Test
	public void spec95PatternOnlyExpression() throws Exception {
		AwkTestSupport
				.awkTest("95. Pattern-only expression")
				.script("($0+0) {print NR \":T\"}")
				.stdin("1\n0\n")
				.expectLines("1:T")
				.runAndAssert();
	}
}
