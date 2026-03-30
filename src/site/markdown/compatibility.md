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
- the Maven coordinates and package root are `org.metricshub`
- gawk and bwk compatibility test suites have been added
- the Maven artifact is published under the LGPL

## Java Regular Expressions

Jawk uses Java regular expressions. That means regex behavior follows the JVM's regex engine rather than every historical AWK implementation exactly.

In practice, this usually means:

- some AWK regex edge cases behave differently
- Java regex features may be available where traditional AWK would differ
- portability between Jawk and non-Java AWKs should be tested when regex behavior is critical

## printf and java.util.Formatter

`printf` and `sprintf` rely on `java.util.Formatter`, not the C formatter used by traditional AWK implementations.

This matters because:

- format handling follows Java rules
- Java does not automatically coerce every value the same way C does
- incompatible format/value combinations can raise `IllegalFormatException`

The CLI `-r` option disables Jawk's default trapping of those format exceptions.

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
