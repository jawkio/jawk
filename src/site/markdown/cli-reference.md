keywords: cli, options, flags, command line
description: Reference for Jawk command-line options and runtime operands.

# CLI Reference

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

This page documents the CLI surface implemented by [`Cli`](apidocs/io/jawk/Cli.html). It focuses on the actual parser-backed options in the current codebase rather than historical flags from older Jawk documentation.

> [!CAUTION]
> Precompiled tuples loaded with `-L` use Java serialization and are version-sensitive. If Jawk reports that a tuples file is incompatible, recompile it with the current Jawk version instead of trying to reuse the old file.

## Invocation Shape

The common command shape is:

```shell
java -jar jawk-${project.version}-standalone.jar [options] [script] [name=value | input_filename]...
```

The extension listing mode is separate:

```shell
java -jar jawk-${project.version}-standalone.jar --list-ext
```

## Option Groups

> [!ACCORDION close-others=false]
> - Script selection
>
>   - `script` is the inline AWK program used when you do not pass `-f` or `-L`.
>   - `-f <filename>` reads a script from a file. You can repeat `-f` to combine multiple script sources.
>   - `-L <filename>` loads previously serialized `AwkTuples` instead of compiling source now.
>   - `-K <filename>` compiles the current script sources to a tuples file and exits without executing the script.
>
> - Input and `ARGV`
>
>   - Remaining operands after the script are exposed through `ARGV` and `ARGC`.
>   - An operand without `=` is treated as an input filename.
>   - An operand containing `=` is treated as an AWK-style file-list assignment that applies before the next input file is consumed.
>   - Use `-v name=value` instead when the variable must exist before `BEGIN`.
>
> - Variables and formatting
>
>   - `-v <name=value>` assigns a variable before execution begins.
>   - `-F <fs>` sets the initial field separator.
>   - `-r` disables Jawk's default trapping of `IllegalFormatException` for `printf` and `sprintf`.
>   - `--locale <locale>` sets the locale through `Locale.forLanguageTag(...)`.
>   - `-t` keeps associative array keys sorted.
>
> - Extensions and sandbox
>
>   - `-l <extension>` or `--load <extension>` loads an extension by registered identifier, simple class name, or fully qualified class name.
>   - `--list-ext` prints the identifiers currently registered in `ExtensionRegistry` and exits. It must be used by itself.
>   - `-S` or `--sandbox` compiles and runs the script with sandbox restrictions enabled.
>
> - Inspection and compilation
>
>   - `--dump-syntax` prints the parsed abstract syntax tree and skips execution.
>   - `--dump-intermediate` prints the tuple stream and skips execution.
>   - `-s` or `--no-optimize` disables tuple optimization during compilation.
>
> - Help and errors
>
>   - `-h` and `-?` print usage and exit. They must be used by themselves.
>   - Missing option arguments, unknown parameters, invalid `-v` syntax, or missing scripts cause argument parsing to fail.
>   - Jawk reports runtime problems through exceptions and exits with a non-zero status when execution fails.

## Execution Notes

- `--dump-syntax`, `--dump-intermediate`, `-K`, `-h`, `-?`, and `--list-ext` are non-executing modes.
- `-S` affects compilation and execution, not just runtime behavior.
- `-L` lets you skip source compilation, but the loaded tuples must still be compatible with the current runtime.
- `-f` and `-L` are distinct paths: source files compile now, tuple files load now.

## See Also

- [Quickstart examples](cli.html)
- [Java API equivalent flows](java.html)
- [Compatibility caveats](compatibility.html)
