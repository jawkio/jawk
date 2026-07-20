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
java -jar jawk-${project.version}-standalone.jar [options] [--] [script] [name=value | input_filename | -]...
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
>   - `--persist <filename>` loads retained user-defined globals from a serialized state file before execution and writes them back after the run finishes.
>   - `-K <filename>` compiles the current script sources to a tuples file and exits without executing the script.
>
> - Input and `ARGV`
>
>   - `--` marks the end of options (POSIX): every following argument is the script (when `-f` or `-L` was not used) and its operands.
>   - Remaining operands after the script are exposed through `ARGV` and `ARGC`.
>   - An operand without `=` is treated as an input filename.
>   - The `-` operand designates standard input as an input file (POSIX). `FILENAME` is `-` while it is being read.
>   - An operand containing `=` is treated as an AWK-style file-list assignment that applies before the next input file is consumed.
>   - Use `-v name=value` instead when the variable must exist before `BEGIN`.
>   - As in gawk, once the program text has been supplied (with `-f` or `-L`), an unknown option ends option processing and is passed on to the AWK program through `ARGV`, which is useful for `#!` interpreter scripts.
>
> - Variables and formatting
>
>   - `-v <name=value>` assigns a variable before execution begins.
>   - `-F <fs>` sets the initial field separator.
>   - `-r` disables Jawk's default trapping of `IllegalFormatException` for `printf` and `sprintf`.
>   - `--locale <locale>` sets the locale through `Locale.forLanguageTag(...)`.
>   - `-t` keeps associative array keys sorted.
>   - `--posix` enforces POSIX-oriented compile-time behavior such as disabling gawk-style nested arrays, typed regexp literals (`@/re/`), and the `BEGINFILE` / `ENDFILE` special patterns.
>   - `JAWK_PERSISTENT_MEMORY` can also point at the persistent-memory file when you do not want to pass `--persist` explicitly. `--persist` wins when both are present.
>
> - Extensions and sandbox
>
>   - `-l <extension>` or `--load <extension>` loads an extension by registered identifier, simple class name, or fully qualified class name. Passing `-l` replaces the default extension set (the gawk compatibility extension), so add `-l GawkExtension` when the script still needs the gawk builtins.
>   - `--list-ext` prints the identifiers currently registered in `ExtensionRegistry` and exits. It must be used by itself.
>   - `-S` or `--sandbox` compiles and runs the script with sandbox restrictions enabled.
>
> - Inspection and compilation
>
>   - `--dump-syntax` prints the parsed abstract syntax tree and skips execution.
>   - `--dump-intermediate` prints the tuple stream and skips execution.
>   - `-s` or `--no-optimize` disables tuple optimization during compilation.
>   - `--profile` executes the script with runtime profiling enabled and prints tuple and function timing statistics to stderr.
>   - `--profile=<filename>` writes the same profiling report to the specified file instead of stderr.
>
> - Help and errors
>
>   - `-h` and `-?` print usage and exit. They must be used by themselves.
>   - Missing option arguments, invalid `-v` syntax, or missing scripts cause argument parsing to fail. An unknown option is rejected only when no program text has been supplied yet; otherwise it flows to `ARGV` as described above.
>   - Jawk reports runtime problems through exceptions and exits with a non-zero status when execution fails.

## Execution Notes

- `--dump-syntax`, `--dump-intermediate`, `-K`, `-h`, `-?`, and `--list-ext` are non-executing modes.
- `--profile` is an executing mode. It keeps normal AWK output on stdout and writes the profiling report to stderr after execution finishes.
- `--profile=<filename>` keeps normal AWK output on stdout and writes only the profiling report to the file.
- `-S` affects compilation and execution, not just runtime behavior.
- `--posix` currently disables arrays-of-arrays syntax and related subarray-only operands, and stops treating gawk's `BEGINFILE` / `ENDFILE` patterns as special, in order to keep CLI compilation aligned with classic POSIX-style AWK expectations.
- `--posix` is rejected together with `-L`, because loading precompiled tuples bypasses source compilation entirely.
- `-L` lets you skip source compilation, but the loaded tuples must still be compatible with the current runtime.
- `-f` and `-L` are distinct paths: source files compile now, tuple files load now.
- `--persist` and `JAWK_PERSISTENT_MEMORY` affect only real execution. Non-executing modes such as `-K`, `--dump-syntax`, and `--dump-intermediate` ignore persistent memory.

## Tuple Serialization Compatibility

Jawk tuples are reusable, but they should be treated as internal artifacts tied to the Jawk version that produced them.

- `-K` writes tuples to a file
- `-L` reads those tuples back
- version mismatches can cause tuple loading to fail
- the safe fix is to recompile the tuples with the current Jawk version

Persistent memory files use the same Java serialization machinery. They are therefore version-sensitive as well and should be discarded when Jawk reports an incompatibility.

## See Also

- [Quickstart examples](cli.html)
- [Java API equivalent flows](java.html)
- [Compatibility caveats](compatibility.html)
