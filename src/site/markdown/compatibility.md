keywords: compatibility, differences, awk, bwk, mawk, gawk, jawk
description: Compatibility notes and important differences between Jawk and other AWK implementations.

# Compatibility and Differences

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk aims to be a practical AWK implementation for JVM environments, but it is not a byte-for-byte clone of every historical AWK behavior. Some differences are deliberate and come from the way Jawk integrates with Java.

## Differences from Traditional AWK

Jawk keeps the AWK language model while adding JVM-oriented capabilities:

- it runs entirely in Java and can be embedded directly in applications
- it exposes a serializable tuple representation for compilation and reuse
- it can maintain associative array keys in sorted order
- it supports gawk-style arrays of arrays (`a[i][j]`) in addition to classic AWK multi-dimensional subscripts (`a[i, j]`)
  This compile-time feature can be disabled, in which case Jawk also rejects subarray operands in array-only positions such as `split(..., a[i])` or `for (k in a[i])`. From the CLI, use `--posix` to disable it.
- it supports explicit extensions
- it offers a sandboxed compiler and runtime

At the same time, some semantics are intentionally Java-driven rather than C-driven.

## Differences from Original Jawk

This maintained fork diverges from the original project in several practical ways:

- Jawk now reports errors through Java exceptions instead of a logging framework
- the AWK-to-JVM bytecode compiler has been removed
- the Socket extension has been removed
- parsing and output performance have been improved
- Jawk supports long integers
- Jawk supports octal and hexadecimal notation in strings
- the Maven coordinates are `io.jawk:jawk` and the package root is `io.jawk`
- BWK, POSIX, and gawk compatibility test suites have been added
- the Maven artifact is published under the LGPL
- operator precedence is fixed and follows POSIX specifications

## Java Regular Expressions

Jawk uses Java regular expressions. That means regex behavior follows the JVM's regex engine rather than every historical AWK implementation exactly.

In practice, this usually means:

- some AWK regex edge cases behave differently
- Java regex features may be available where traditional AWK would differ
- portability between Jawk and non-Java AWKs should be tested when regex behavior is critical

## Compile-Time Function Resolution

Jawk resolves user-defined function calls during compilation. It does not defer all of that work to runtime.

As a result:

- missing user-defined functions are rejected earlier
- parameter and array/scalar checks happen during compilation
- the tuple stream is built with resolved control flow

This is one of the major behavioral differences between Jawk and AWK implementations that defer more validation until runtime.

## Tuple Serialization Compatibility

Jawk tuples are reusable, but they should be treated as internal artifacts tied to the Jawk version that produced them.

- CLI `-K` writes tuples to a file
- CLI `-L` reads those tuples back
- version mismatches can cause tuple loading to fail
- the safe fix is to recompile the tuples with the current Jawk version

## See Also

- [CLI options and tuple workflows](cli-reference.html)
- [Java compilation and reuse APIs](java-compile.html)

## Compatibility Test Suites

Jawk maintains compatibility tests derived from:

- BWK / One True Awk: <https://github.com/onetrueawk/awk>
- POSIX awk utility specification: <https://pubs.opengroup.org/onlinepubs/9699919799/utilities/awk.html>
- GNU Awk: `git://git.savannah.gnu.org/gawk.git`

These run automatically as integration tests during `mvn verify`.

Compatibility suites now live under `src/it/java`, with their vendored inputs under `src/it/resources`.
The compatibility suites read those vendored files directly from the repository checkout instead of relying on the Maven test classpath layout.
They are also grouped by upstream family in separate Java packages so the Failsafe reports aggregate BWK, POSIX, and gawk results independently.
The gawk coverage is split into a small set of explicit Java integration suites built on `AwkTestSupport`, following the broad test families declared in gawk's vendored `Makefile.am`. The vendored gawk files remain in the repository to make future refreshes and diffs straightforward, but the runtime source of truth is the Java test code.

| Suite | Coverage |
| --- | --- |
| **BwkPIT** | Pattern matching and basic AWK operations from the BWK test collection |
| **BwkTIT** | Text processing, field splitting, built-in functions, and output formatting |
| **BwkMiscIT** | Miscellaneous BWK compatibility edge cases |
| **PosixIT** | Explicit POSIX AWK specification behaviors transcribed into integration tests |
| **GawkIT** | Core gawk compatibility mirrored from the vendored basic and Unix test groups |
| **GawkExtensionIT** | gawk extension-oriented compatibility mirrored from the vendored extension-style test groups |
| **GawkLocaleIT** | Locale- and charset-sensitive gawk compatibility mirrored from the vendored locale test groups |
| **GawkOptionalFeatureIT** | Optional-feature and environment-specific gawk compatibility mirrored from the vendored optional test groups |

Not all gawk compatibility cases pass, primarily because Jawk uses Java regular expressions and `java.util.Formatter` rather than their C equivalents. Linux CI is the authoritative environment for the full compatibility pass rate. Windows can still run the explicit Java gawk suites without requiring Unix tooling.

Results are stored in `target/failsafe-reports/` and summarized on the [Failsafe Report](failsafe-report.html) page of the generated site.
