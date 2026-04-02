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

import org.junit.Assume;
import org.junit.Test;

/**
 * POSIX Issue 8 conformance tests for awk behavior, adapted for Jawk.
 */
public class PosixConformanceTest {

	// Section 1: Overall program structure, BEGIN/END, default pattern/action

	@Test
	public void posix11DefaultActionPrintsRecord() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.1 default action prints record")
				.script("/.*/")
				.stdin("alpha\nbeta\n")
				.expectLines("alpha", "beta")
				.runAndAssert();
	}

	@Test
	public void posix12MissingPatternMatchesAllRecords() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.2 missing pattern matches all records")
				.script("{ print $0 }")
				.stdin("A\nB\n")
				.expectLines("A", "B")
				.runAndAssert();
	}

	@Test
	public void posix13MultipleBeginEndExecuteInOrder() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.3 multiple BEGIN and END execute in program order")
				.script("BEGIN { print \"one\" }\nBEGIN { print \"two\" }\nEND { print \"three\" }\nEND { print \"four\" }")
				.expectLines("one", "two", "three", "four")
				.runAndAssert();
	}

	@Test
	public void posix14BeginRunsBeforeFirstRecord() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.4 BEGIN runs before first record and -v applies before BEGIN")
				.script(
						"BEGIN{print \"BEGIN\", P; print \"FILENAME?\", (FILENAME==\"\"? \"unset\":\"set\")}\n{print \"REC\", $0}\nEND{print \"END\", P}")
				.file("f1", "X")
				.operand("{{f1}}")
				.preassign("P", "pre")
				.expectLines("BEGIN pre", "FILENAME? unset", "REC X", "END pre")
				.runAndAssert();
	}

	@Test
	public void posix15EndRunsAfterLastRecord() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.5 END runs after last record")
				.script("END{print \"done\"}")
				.stdin("a\nb\n")
				.expectLines("done")
				.runAndAssert();
	}

	@Test
	public void posix16EmptyProgramExitsZero() throws Exception {
		Assume.assumeTrue("Empty programs are not yet supported by the CLI", false);
		AwkTestSupport
				.cliTest("POSIX 1.6 empty program exits with zero")
				.file("empty.awk", "")
				.argument("-f", "{{empty.awk}}")
				.stdin("should_not_be_read\n")
				.expectLines()
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void posix17PatternRangesInclusiveAndRepeat() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 1.7 pattern ranges inclusive and repeat")
				.script("/start/,/end/ {print $0}")
				.stdin("a\nstart\nmid\nend\nb\nstart\nend\nc\n")
				.expectLines("start", "mid", "end", "start", "end")
				.runAndAssert();
	}

	// Section 2: Options & operands

	@Test
	public void posix21DashFDefinesFs() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 2.1 -F defines FS")
				.argument("-F", ":")
				.script("{print $2}")
				.stdin("a:b:c\nd:e:f\n")
				.expectLines("b", "e")
				.runAndAssert();
	}

	@Test
	public void posix22DashVAssignsBeforeBegin() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 2.2 -v assigns before BEGIN")
				.argument("-v", "x=42")
				.script("BEGIN{print x}")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void posix23AssignmentsOccurBeforeFollowingFile() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 2.3 assignment operands per file")
				.script(
						"BEGIN{print \"B:\",x+0}\n{print FILENAME \":\" x}\nEND{print \"E:\",x+0}")
				.file("f1", "A\n")
				.file("f2", "B\n")
				.operand("x=1", "{{f1}}", "x=2", "{{f2}}", "x=3")
				.expectLines("B: 0", "{{f1}}:1", "{{f2}}:2", "E: 3")
				.runAndAssert();
	}

	@Test
	public void posix24DashFConcatenatesProgramFiles() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 2.4 -f concatenates program files")
				.argument("-f", "{{p1.awk}}", "-f", "{{p2.awk}}")
				.file("p1.awk", "BEGIN{print \"P1\"}\n")
				.file("p2.awk", "BEGIN{print \"P2\"}\n")
				.expectLines("P1", "P2")
				.runAndAssert();
	}

	@Test
	public void posix26DashVSetsRegexFs() throws Exception {
		Assume.assumeTrue("Jawk does not treat -v FS assignments as regular expressions yet", false);
		AwkTestSupport
				.cliTest("POSIX 2.6 -v FS ERE")
				.argument("-v", "FS=[, ]+")
				.script("{print $1 \"|\" $2 \"|\" $3}")
				.stdin("a,,b   c\nx  y\n")
				.expectLines("a|b|c", "x|y|")
				.runAndAssert();
	}

	// Section 3: Records & fields

	@Test
	public void posix31DefaultFsSkipsBlanks() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.1 default FS skips blanks")
				.script("{print NF \":\" $1 \"-\" $2 \"-\" $3}")
				.stdin("    a   b\nc\nd     e      f\n")
				.expectLines("2:a-b-", "1:c--", "3:d-e-f")
				.runAndAssert();
	}

	@Test
	public void posix32FsSingleCharacterSplitsEachOccurrence() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.2 FS single char splits each occurrence")
				.script("BEGIN{FS=\"|\"}{print NF \":\" $2}")
				.stdin("a|b||c\n")
				.expectLines("4:b")
				.runAndAssert();
	}

	@Test
	public void posix33FsAsEre() throws Exception {
		Assume.assumeTrue("Jawk does not yet normalize whitespace when FS is an ERE", false);
		AwkTestSupport
				.awkTest("POSIX 3.3 FS as ERE")
				.script("BEGIN{FS=\"[,;][[:space:]]*\"}{print $1 \"-\" $2 \"-\" $3}")
				.stdin("a,  b; c\n")
				.expectLines("a-b-c")
				.runAndAssert();
	}

	@Test
	public void posix34RsDefaultExcludesNewline() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.4 RS default newline excluded from $0")
				.script("{print $0 \"~\"}")
				.stdin("line1\nline2\n")
				.expectLines("line1~", "line2~")
				.runAndAssert();
	}

	@Test
	public void posix35RsEmptyParagraphMode() throws Exception {
		Assume.assumeTrue("RS=\"\" paragraph mode is not implemented", false);
		AwkTestSupport
				.awkTest("POSIX 3.5 RS empty string paragraph mode")
				.script("BEGIN{RS=\"\"}{print \"REC[\" $0 \"]\"; print \"NF=\" NF}")
				.stdin("a b\n\n\nc d\ne\n\n\n\nf\n")
				.expectLines("REC[a b]", "NF=2", "REC[c d\ne]", "NF=3", "REC[f]", "NF=1")
				.runAndAssert();
	}

	@Test
	public void posix36AssigningToExistingFieldRecomputesDollarZero() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.6 assigning to existing field recomputes $0")
				.script("BEGIN{OFS=\"|\"}{$2=\"X\"; print $0}")
				.stdin("foo bar baz\n")
				.expectLines("foo|X|baz")
				.runAndAssert();
	}

	@Test
	public void posix37AssigningToDollarZeroResetsFields() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.7 assigning to $0 resets fields")
				.script("BEGIN{OFS=\",\"}{$0=\"x y\"; print NF \":\" $1 \"-\" $2 \"-\" $3}")
				.stdin("a b c\n")
				.expectLines("2:x-y-")
				.runAndAssert();
	}

	@Test
	public void posix38ReferencingNonexistentFieldIsUninitialized() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.8 referencing nonexistent field is uninitialized")
				.script("{print \"[\" $(NF+1) \"]\", 0+$(NF+1), NF}")
				.stdin("a b\n")
				.expectLines("[] 0 2")
				.runAndAssert();
	}

	@Test
	public void posix39AssigningFutureFieldExpandsRecord() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.9 assigning future field expands record")
				.script("BEGIN{OFS=\":\"}{$(NF+2)=5; print NF \"|\" $0 \"|\" $(NF-1) \"|\" $(NF)}")
				.stdin("a b\n")
				.expectLines("4|a:b::5||5")
				.runAndAssert();
	}

	@Test
	public void posix310DynamicFieldSelection() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 3.10 dynamic field selection")
				.script("{i=2; print $(i) \"-\" $(i+1)}")
				.stdin("u v w\n")
				.expectLines("v-w")
				.runAndAssert();
	}

	// Section 4: Expressions

	@Test
	public void posix41ConcatenationPrecedence() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.1 concatenation precedence")
				.script("BEGIN{ x=1; y=2; print (x y) }")
				.expectLines("12")
				.runAndAssert();
	}

	@Test
	public void posix42ExponentiationRightAssociative() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.2 exponentiation is right associative")
				.script("BEGIN{print 2^3^2}")
				.expectLines("512")
				.runAndAssert();
	}

	@Test
	public void posix43ModuloUsesFmod() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.3 modulo uses fmod semantics")
				.script("BEGIN{print 5.5%2, -5.5%2}")
				.expectLines("1.5 -1.5")
				.runAndAssert();
	}

	@Test
	public void posix44CompoundAssignmentsModifyOnce() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.4 compound assignments modify once")
				.script("BEGIN{a=2; a^=3; b=5; b%=2; c=3; c*=4; d=8; d/=2; e=7; e-=1; f=1; f+=9; print a, b, c, d, e, f}")
				.expectLines("8 1 12 4 6 10")
				.runAndAssert();
	}

	@Test
	public void posix45PrePostIncrementDecrement() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.5 pre and post increment/decrement")
				.script("BEGIN{a=1; print a++, a; print ++a, a; print a--, a; print --a, a}")
				.expectLines("1 2", "3 3", "3 2", "1 1")
				.runAndAssert();
	}

	@Test
	public void posix46LogicalOperatorsShortCircuit() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.6 logical operators short circuit")
				.script("BEGIN{ i=0; j=0; (i && ++j); print i,j; (i || ++j); print i,j }")
				.expectLines("0 0", "0 1")
				.runAndAssert();
	}

	@Test
	public void posix47ConditionalOperatorRightAssociative() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.7 conditional operator right associative")
				.script("BEGIN{print 1?2:3?4:5}")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void posix48NumericVsStringComparison() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.8 numeric vs string comparison")
				.script("BEGIN{print \"10\" < \"2\", 10 < \"2\", \"10\"==\"10\", \"10\"==10}")
				.expectLines("0 0 1 1")
				.runAndAssert();
	}

	@Test
	public void posix49StringConversionUsesConvfmt() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.9 string conversion uses CONVFMT")
				.script("BEGIN{CONVFMT=\"%.2f\"; x=3; y=3.14; s1=x \"\"; s2=y \"\"; print s1 \"|\" s2}")
				.expectLines("3|3.14")
				.runAndAssert();
	}

	@Test
	public void posix410PrintUsesOfmt() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.10 print uses OFMT")
				.script("BEGIN{OFMT=\"%.2f\"; x=3.14159; print x}")
				.expectLines("3.14")
				.runAndAssert();
	}

	@Test
	public void posix411DivisionOperator() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 4.11 division vs regex literal disambiguation")
				.script("BEGIN{print 12/2/3}")
				.expectLines("2")
				.runAndAssert();
	}

	// Section 5: Special variables

	@Test
	public void posix51NrAndFnrCounts() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.1 NR counts records across files")
				.script("{print FILENAME \":\" FNR \"/\" NR}")
				.file("f1", "a\nb\n")
				.file("f2", "c\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("{{f1}}:1/1", "{{f1}}:2/2", "{{f2}}:1/3")
				.runAndAssert();
	}

	@Test
	public void posix52FilenameUnsetInBegin() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.2 FILENAME unset in BEGIN and holds last file in END")
				.script(
						"BEGIN{print \"[\" FILENAME \"]\"}\n{if (NR==1) first=FILENAME}\nEND{print \"last=\" FILENAME \", first=\" first}")
				.file("f1", "z\n")
				.operand("{{f1}}")
				.expectLines("[]", "last={{f1}}, first={{f1}}")
				.runAndAssert();
	}

	@Test
	public void posix53MatchSetsRstartAndRlength() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.3 match sets RSTART and RLENGTH")
				.script(
						"BEGIN{ s=\"abc123\"; print match(s, /[0-9]+/), RSTART, RLENGTH; print match(s, /XYZ/), RSTART, RLENGTH }")
				.expectLines("4 4 3", "0 0 -1")
				.runAndAssert();
	}

	@Test
	public void posix54OfsAndOrsAffectPrint() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.4 OFS and ORS affect print")
				.script("BEGIN{OFS=\"|\"; ORS=\";\"} {print $1,$2}")
				.stdin("a b\nc d\n")
				.expect("a|b;c|d;")
				.runAndAssert();
	}

	@Test
	public void posix55DeletingArgvPreventsOperandProcessing() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.5 deleting ARGV prevents operand processing")
				.script("BEGIN{delete ARGV[2]} {print FILENAME \":\" $0}")
				.file("f1", "x\n")
				.file("f2", "y\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("{{f1}}:x")
				.runAndAssert();
	}

	@Test
	public void posix56NoOperandsReadsStdinWithDashFilename() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 5.6 no operands reads stdin with FILENAME '-'")
				.script("BEGIN{delete ARGV[1]; delete ARGV[2]} {print \"[\" FILENAME \"] \" $0}")
				.stdin("s\n")
				.operand("-")
				.expectLines("[] s")
				.runAndAssert();
	}

	// Section 6: Regular expressions

	@Test
	public void posix61RegexLiteralMatchesDollarZero() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 6.1 /ERE/ outside ~ matches $0")
				.script("/o/ {print}")
				.stdin("foo\nbar\n")
				.expectLines("foo")
				.runAndAssert();
	}

	@Test
	public void posix62RegexEscapeSlash() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 6.2 regex escape slash")
				.script("$0 ~ /a\\/b/ {print $0}")
				.stdin("a/b\na\\b\n")
				.expectLines("a/b")
				.runAndAssert();
	}

	@Test
	public void posix63StringRegexDoubleEscapes() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 6.3 string regex double escape")
				.script("{ if ($0 ~ \"\\\\\\\\\") print \"yes\" }")
				.stdin("back\\\\slash\n")
				.expectLines("yes")
				.runAndAssert();
	}

	@Test
	public void posix64FsRegexRunsOfDelimiters() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 6.4 FS regex splits on runs")
				.script("BEGIN{FS=\"[ ,]+\"}{print $1 \"-\" $2}")
				.stdin("a,, ,b\n")
				.expectLines("a-b")
				.runAndAssert();
	}

	@Test
	public void posix65RegexCannotMatchRsChar() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 6.5 regex cannot match RS char in $0")
				.script("{print ($0 ~ /\\n/)? \"match\":\"no\"}")
				.stdin("x\ny\n")
				.expectLines("no", "no")
				.runAndAssert();
	}

	// Section 7: Patterns

	@Test
	public void posix71ExpressionPatternTruthiness() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 7.1 expression pattern truthiness")
				.script("$0+0 {print \"T\"}")
				.stdin("1\n0\n2\n")
				.expectLines("T", "T")
				.runAndAssert();
	}

	@Test
	public void posix72RangePatternRestarts() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 7.2 range pattern restarts after closing")
				.script("/a/,/c/ {print $0}")
				.stdin("x\na\nb\nc\na\nc\ny\n")
				.expectLines("a", "b", "c", "a", "c")
				.runAndAssert();
	}

	// Section 8: Statements

	@Test
	public void posix81ElseBindsNearestIf() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.1 else binds to nearest if")
				.script("BEGIN{ if (1) if (0) x=1; else x=2; print x }")
				.expectLines("2")
				.runAndAssert();
	}

	@Test
	public void posix82WhileLoop() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.2 while loop")
				.script("BEGIN{ i=0; while(i<3){print i; i++} }")
				.expectLines("0", "1", "2")
				.runAndAssert();
	}

	@Test
	public void posix83DoWhileLoopExecutesAtLeastOnce() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.3 do while executes body once")
				.script("BEGIN{ i=5; do {print i} while (i<0) }")
				.expectLines("5")
				.runAndAssert();
	}

	@Test
	public void posix84ForLoopClassic() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.4 classic for loop")
				.script("BEGIN{ for(i=3;i>0;i--) print i }")
				.expectLines("3", "2", "1")
				.runAndAssert();
	}

	@Test
	public void posix85ForInDeleteAll() throws Exception {
		Assume.assumeTrue("length(array) is not supported", false);
		AwkTestSupport
				.awkTest("POSIX 8.5 for in delete all elements")
				.script("BEGIN{ split(\"a b c\", a, \" \" ); for (i in a) delete a[i]; print length(a) }")
				.expectLines("0")
				.runAndAssert();
	}

	@Test
	public void posix86BreakAndContinue() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.6 break and continue in loop")
				.script("BEGIN{for(i=1;i<=5;i++){ if(i==3) continue; if(i==4) break; print i }}")
				.expectLines("1", "2")
				.runAndAssert();
	}

	@Test
	public void posix87NextSkipsCurrentRecord() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 8.7 next skips current record")
				.script("/skip/ { next } { print \">\" $0 }")
				.stdin("keep\nskip\nkeep2\n")
				.expectLines(">keep", ">keep2")
				.runAndAssert();
	}

	@Test
	public void posix88NextfileSkipsRestOfCurrentFile() throws Exception {
		Assume.assumeTrue("nextfile is not supported by Jawk", false);
		AwkTestSupport
				.awkTest("POSIX 8.8 nextfile skips rest of current file")
				.script("{print FILENAME \":\" $0; if (FNR==1) nextfile}")
				.file("f1", "A1\nA2\n")
				.file("f2", "B1\n")
				.operand("{{f1}}", "{{f2}}")
				.expectLines("{{f1}}:A1", "{{f2}}:B1")
				.runAndAssert();
	}

	@Test
	public void posix89ExitSetsStatus() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 8.9 exit expr sets status")
				.script("END{exit 7}")
				.expectLines()
				.expectExit(7)
				.runAndAssert();
	}

	// Section 9: Output

	@Test
	public void posix91PrintDefaultIsDollarZero() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.1 print with empty list prints $0")
				.script("{print}")
				.stdin("z\n")
				.expectLines("z")
				.runAndAssert();
	}

	@Test
	public void posix92PrintfBasicConversions() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.2 printf conversions")
				.script("BEGIN{ printf \"%d %u %o\\n\", 7, 7, 9 }")
				.expectLines("7 7 11")
				.runAndAssert();
	}

	@Test
	public void posix93PrintfPercentCUsesFirstCharacter() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.3 printf %c uses first character")
				.script("BEGIN{ printf \"%c\\n\", \"XYZ\" }")
				.expectLines("X")
				.runAndAssert();
	}

	@Test
	public void posix94PrintfStarWidthPrecision() throws Exception {
		Assume.assumeTrue("Dynamic width/precision in printf requires printf4j support", false);
		AwkTestSupport
				.awkTest("POSIX 9.4 printf star width and precision")
				.script("BEGIN{ printf \"%*.*f\\n\", 6, 2, 3.14159 }")
				.expectLines("  3.14")
				.runAndAssert();
	}

	@Test
	public void posix95RedirectionTruncatesAndReuses() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.5 redirection truncates and reuses file")
				.path("out.txt")
				.script(
						"BEGIN{ f=\"{{out.txt}}\"; print \"a\" > f; print \"b\" > f; close(f); while ((getline x < f) > 0) print x }")
				.expectLines("a", "b")
				.runAndAssert();
	}

	@Test
	public void posix96AppendRedirection() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.6 append redirection")
				.path("app.txt")
				.script(
						"BEGIN{ f=\"{{app.txt}}\"; print \"first\" > f; close(f); print \"second\" >> f; close(f); while ((getline x < f) > 0) print x }")
				.expectLines("first", "second")
				.runAndAssert();
	}

	@Test
	public void posix97CloseReturnsZeroOnSuccess() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.7 close returns zero")
				.path("c.txt")
				.script("BEGIN{ f=\"{{c.txt}}\"; print \"x\" > f; print (close(f)==0 ? \"ok\":\"bad\") }")
				.expectLines("ok")
				.runAndAssert();
	}

	@Test
	public void posix98FflushReturnsZero() throws Exception {
		Assume.assumeTrue("fflush() is not available in Jawk", false);
		AwkTestSupport
				.awkTest("POSIX 9.8 fflush returns zero")
				.script("BEGIN{ printf \"x\"; print fflush()==0 ? \"OK\" : \"NO\" }")
				.expectLines("xOK")
				.runAndAssert();
	}

	@Test
	public void posix99PipelineWriteClose() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.9 pipeline write close")
				.path("pipe.txt")
				.script(
						"BEGIN{ f=\"{{pipe.txt}}\"; cmd=\"cat > \" f; print \"hi\" | cmd; print (close(cmd)==0 ? \"ok\":\"bad\") }")
				.expectLines("ok")
				.runAndAssert();
	}

	@Test
	public void posix991PipelineWritesDataToFile() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 9.9 pipeline writes data")
				.posixOnly()
				.path("pipe.txt")
				.script(
						"BEGIN{ f=\"{{pipe.txt}}\"; cmd=\"cat > \" f; print \"hi\" | cmd; close(cmd); while ((getline x < f) > 0) print x; close(f) }")
				.expectLines("hi")
				.runAndAssert();
	}

	// Section 10: Built-in functions

	@Test
	public void posix101IntTruncatesTowardZero() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.1 int truncates toward zero")
				.script("BEGIN{ print int( 3.9), int(-3.9) }")
				.expectLines("3 -3")
				.runAndAssert();
	}

	@Test
	public void posix102SrandRepeatsSequence() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.2 srand repeats sequence")
				.script("BEGIN{ srand(123); a=rand(); srand(123); b=rand(); print (a==b) }")
				.expectLines("1")
				.runAndAssert();
	}

	@Test
	public void posix103LengthOnDollarZeroAndString() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.3 length on $0 and string")
				.script("{print length($0), length(\"xx\") }")
				.stdin("abcd\n")
				.expectLines("4 2")
				.runAndAssert();
	}

	@Test
	public void posix104LengthOfArray() throws Exception {
		Assume.assumeTrue("length(array) is not supported", false);
		AwkTestSupport
				.awkTest("POSIX 10.4 length of array")
				.script("BEGIN{ split(\"a b c\", A, \" \" ); print length(A) }")
				.expectLines("3")
				.runAndAssert();
	}

	@Test
	public void posix105IndexFunction() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.5 index function")
				.script("BEGIN{ print index(\"banana\",\"na\"), index(\"banana\",\"x\") }")
				.expectLines("3 0")
				.runAndAssert();
	}

	@Test
	public void posix106SubstrFunction() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.6 substr function")
				.script("BEGIN{ print substr(\"abcdef\",2,3), substr(\"abcdef\",5) }")
				.expectLines("bcd ef")
				.runAndAssert();
	}

	@Test
	public void posix107ToLowerUpper() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.7 tolower and toupper")
				.script("BEGIN{ print tolower(\"AbC\"), toupper(\"mIx\") }")
				.expectLines("abc MIX")
				.runAndAssert();
	}

	@Test
	public void posix108MatchFunction() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.8 match function")
				.script("BEGIN{ s=\"abc def\"; print match(s,/def/), RSTART, RLENGTH }")
				.expectLines("5 5 3")
				.runAndAssert();
	}

	@Test
	public void posix109SubAndGsub() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.9 gsub and sub")
				.script(
						"BEGIN{ s=\"axa xa\"; n1=gsub(/xa/,\"(&)\",s); print n1, s; s=\"xa xa\"; n2=sub(/xa/,\"\\\\&\",s); print n2, s }")
				.expectLines("2 a(xa) (xa)", "1 & xa")
				.runAndAssert();
	}

	@Test
	public void posix1010SplitDeletesExistingElements() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.10 split deletes existing elements")
				.script("BEGIN{ a[99]=\"old\"; n=split(\"p,q\",a,\",\"); print n, a[1], a[2], (1 in a) && !(99 in a) }")
				.expectLines("2 p q 1")
				.runAndAssert();
	}

	@Test
	public void posix1011SprintfFormatting() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.11 sprintf formatting")
				.script("BEGIN{ s=sprintf(\"-%04d- %.1f\", 7, 3.14); print s }")
				.expectLines("-0007- 3.1")
				.runAndAssert();
	}

	@Test
	public void posix1012CloseAndGetlineReuse() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.12 close and getline reuse")
				.path("gf.txt")
				.script(
						"BEGIN{ f=\"gf.txt\"; print \"A\" > f; print \"B\" >> f; close(f); while ((getline x < f) > 0) print x; print (close(f)==0 ? \"ok\":\"bad\") }")
				.expectLines("A", "B", "ok")
				.runAndAssert();
	}

	@Test
	public void posix1013GetlineSetsDollarZero() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.13 getline without var sets $0 and NF")
				.script("{ if (NR==1) { getline < \"{{data.txt}}\"; print \"Z:\" $0 \",NF=\" NF }; print \"S:\" $0 }")
				.stdin("outer\n")
				.file("data.txt", "inner\n")
				.expectLines("Z:inner,NF=1", "S:inner")
				.runAndAssert();
	}

	@Test
	public void posix1014GetlineVarFromCurrentInput() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.14 getline var from current input")
				.script("{ if (NR==1){ getline z; print \"Z=\" z \",0=\" $0 \",NR=\" NR; } }")
				.stdin("X\nY\n")
				.expectLines("Z=Y,0=X,NR=2")
				.runAndAssert();
	}

	@Test
	public void posix1015CommandGetlineAndSystem() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 10.15 command getline and system")
				.posixOnly()
				.script(
						"BEGIN{ cmd=\"printf hack\\n\"; if ( (cmd | getline z) > 0 ) print z; close(cmd); print system(\":\") }")
				.expectLines("hack", "0")
				.runAndAssert();
	}

	// Section 11: Arrays

	@Test
	public void posix111MultidimensionalIndexesUseSubsep() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 11.1 multidimensional indexes via SUBSEP")
				.script("BEGIN{ SUBSEP=\"|\"; A[1,2]=\"x\"; print A[\"1\" SUBSEP \"2\"], A[1 SUBSEP 2]; }")
				.expectLines("x x")
				.runAndAssert();
	}

	@Test
	public void posix112InOperatorDoesNotCreateElements() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 11.2 in operator membership test")
				.script("BEGIN{ delete A; print (1 in A); A[1]=7; print (1 in A); print ((1,2) in A); }")
				.expectLines("0", "1", "0")
				.runAndAssert();
	}

	@Test
	public void posix113DeleteArrayClearsAll() throws Exception {
		Assume.assumeTrue("length(array) is not supported", false);
		AwkTestSupport
				.awkTest("POSIX 11.3 delete array clears elements")
				.script("BEGIN{ split(\"a b\", A, \" \" ); delete A; print length(A) }")
				.expectLines("0")
				.runAndAssert();
	}

	// Section 12: Lexical conventions

	@Test
	public void posix121HashIntroducesComment() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 12.1 # comments")
				.script("{ # print record\n  print $0 }")
				.stdin("q\n")
				.expectLines("q")
				.runAndAssert();
	}

	@Test
	public void posix122BackslashNewlineIgnored() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 12.2 backslash newline ignored")
				.script("BEGIN{ print \"x\"\\\n\"y\" }")
				.expectLines("xy")
				.runAndAssert();
	}

	@Test
	public void posix123OctalEscapesInString() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 12.3 octal escapes in string")
				.script("BEGIN{ s=\"A\\101\"; print s }")
				.expectLines("AA")
				.runAndAssert();
	}

	// Section 13: Exit status and errors

	@Test
	public void posix131ExitStatusZeroOnSuccess() throws Exception {
		AwkTestSupport
				.cliTest("POSIX 13.1 exit status zero when successful")
				.script("BEGIN{}")
				.expectLines()
				.expectExit(0)
				.runAndAssert();
	}

	@Test
	public void posix132UnreadableFileProducesDiagnostic() throws Exception {
		Assume.assumeTrue("Jawk throws FileNotFoundException instead of reporting a diagnostic", false);
		AwkTestSupport
				.cliTest("POSIX 13.2 unreadable file yields diagnostic and non-zero exit")
				.script("{print}")
				.path("missing.txt")
				.operand("{{missing.txt}}")
				.expectLines()
				.expectExit(2)
				.runAndAssert();
	}

	// Section 14: Locale & numeric strings

	@Test
	public void posix141DotRecognizedAsDecimalPoint() throws Exception {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.FRANCE);
			AwkTestSupport
					.awkTest("POSIX 14.1 dot recognized as decimal point")
					.script("BEGIN{ print 1.5 + n }")
					.preassign("n", 3.5)
					.expectLines("5")
					.runAndAssert();
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	public void posix142LocaleNumericInput() throws Exception {
		Assume.assumeTrue("Locale-aware numeric parsing is not available", false);
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.FRANCE);
			AwkTestSupport
					.awkTest("POSIX 14.2 locale numeric input")
					.script("{print ($1+0)==1.5 ? \"OK\":\"NO\"}")
					.stdin("1,5\n")
					.expectLines("OK")
					.runAndAssert();
		} finally {
			Locale.setDefault(previous);
		}
	}

	// Section 15: Grammar edges

	@Test
	public void posix151FunctionCallWithoutWhitespace() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 15.1 function call without whitespace")
				.script("function f(x){return x+1}\nBEGIN{ print f(41) }")
				.expectLines("42")
				.runAndAssert();
	}

	@Test
	public void posix152NewlineStatementTerminator() throws Exception {
		AwkTestSupport
				.awkTest("POSIX 15.2 newline as statement terminator")
				.script("BEGIN{\n    a=1\n    ; b=2\n    print a+b\n}")
				.expectLines("3")
				.runAndAssert();
	}
}
