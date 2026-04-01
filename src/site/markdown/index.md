keywords: jawk, awk, java, cli, text processing
description: Pure Java AWK for command-line use and Java embedding.

# Jawk: AWK for Java

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk is a pure Java implementation of [AWK](https://en.wikipedia.org/wiki/AWK). You can run AWK programs from the command line, embed them in JVM applications, compile them to reusable tuples, evaluate expressions, provide structured input without converting it back to text, and opt into extensions or sandboxing when your host application needs them.

> [!TABS]
> - CLI
>
>   ```shell-session
>   $ echo "hello world" | java -jar jawk-${project.version}-standalone.jar '{ print $2 ", " $1 "!" }'
>   world, hello!
>   ```
>
> - Java
>
>   ```java
>   Awk awk = new Awk();
>   String result = awk.run("{ print toupper($0) }", "hello world");
>   // result = "HELLO WORLD\n"
>   ```

## What Jawk Is

Jawk parses AWK source, performs semantic analysis, compiles the result to an intermediate tuple representation, and executes those tuples in a Java runtime. The same core engine powers both the CLI and the Java API, which means the command-line workflow and the embedding workflow share the same parser, tuple compiler, runtime, extensions, and sandbox rules.

This project is a maintained fork of the original [Jawk project](https://jawk.sourceforge.net/). In this fork, errors are reported through Java exceptions rather than a logging framework, the public Java API has been expanded substantially, and the documentation focuses on practical embedding as much as on CLI use.

## What It Is Good For

Jawk fits well when you want AWK's text-processing model but need it to live inside a JVM process instead of a separate native toolchain.

- Running AWK one-liners or scripts from the CLI
- Embedding AWK rules inside Java applications
- Reusing compiled expressions or full scripts in hot paths
- Feeding existing rows or records through `InputSource` without serializing them back to text
- Enabling only the extensions your application wants to expose
- Locking execution down with sandboxed tuples and runtime components

## Choose Your Path

- If you want to run Jawk from the shell, start with the [CLI quickstart](cli.html).
- If you want to add Jawk to a JVM application, start with the [Java quickstart](java.html).
- If you need dependencies or the standalone jar, go to the [installation guide](install.html).
- If you want extension loading or authoring, use [loading extensions](extensions.html) and [writing extensions](extensions-writing.html).
- If you need tuple reuse, expression evaluation, or structured input, continue with [structured input and variables](java-input.html), [compile, eval, and reuse](java-compile.html), and [advanced runtime](java-advanced.html).
- If you are comparing Jawk with other AWKs, see [compatibility and differences](compatibility.html).

## Key Capabilities

- CLI-compatible AWK execution with inline scripts, script files, operands, and input files
- Java entry points for script execution, expression evaluation, tuple compilation, and repeated reuse
- Structured input through `InputSource` for row-oriented or already-tokenized data
- Explicit extension loading through the Java API or the CLI
- Tuple serialization for CLI precompilation and later loading
- Sandbox-specific tuples and runtime components through `SandboxedAwk`

> [!IMPORTANT]
> Extensions are always opt-in. Sandbox mode exists for both the CLI and the Java API. Raw `AVM` access is intentionally advanced and stateful, and should only be used when you explicitly want to manage runtime reuse yourself.

## Safety and Advanced Topics

For most Java applications, `Awk` is the right abstraction. The convenience methods on `Awk` create, use, and close a fresh runtime for each isolated operation. When you move to raw `AVM`, you are choosing performance and lifecycle control over isolation.

Likewise, `SandboxedAwk` and the CLI `-S` option deliberately restrict dangerous AWK features such as `system()`, redirections, and command pipelines. Use the sandbox when scripts come from untrusted or tightly controlled sources, and use the advanced runtime APIs only when you are comfortable owning the tradeoffs.

## Next Steps

- [Install Jawk](install.html)
- [Learn the CLI](cli.html)
- [Embed Jawk in Java](java.html)
- [Work with structured input and variables](java-input.html)
- [Compile, evaluate, and reuse tuples](java-compile.html)
- [Load or write extensions](extensions.html)
