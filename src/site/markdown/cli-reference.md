keywords: cli, options, flags, command line
description: Reference for Jawk command-line options and runtime operands.

# CLI Reference

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

This page documents every option accepted by the [`jawk` CLI](apidocs/io/jawk/Cli.html).

> [!CAUTION]
> The precompiled programs written by `-K` and loaded with `-L`, and the state files used by `--persist`, rely on Java serialization and are tied to the Jawk version that produced them. When Jawk reports that such a file is incompatible, recompile or discard it instead of trying to reuse it.

## Invocation Shape

The common command shape is:

```shell
jawk [options] [--] [script] [name=value | input_filename | -]...
```

`jawk` is the launcher set up by the [one-command install](install.html); `java -jar jawk-${project.version}-standalone.jar` accepts exactly the same arguments.

The extension listing and version reporting modes are separate:

```shell
jawk --list-ext
jawk --version
```

## Option Groups

> [!ACCORDION close-others=false]
> - Script selection
>
>   - `script` is the inline AWK program used when you do not pass `-f` or `-L`.
>   - `-f <filename>` reads a script from a file. You can repeat `-f` to combine multiple script sources.
>   - `-L <filename>` loads a program previously compiled with `-K` instead of compiling source now.
>   - `--persist <filename>` loads retained user-defined globals from a state file before execution and writes them back after the run finishes. The `JAWK_PERSISTENT_MEMORY` environment variable can point at the same file; `--persist` wins when both are present.
>   - `-K <filename>` compiles the current script sources to a program file and exits without executing the script.
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
>   - The value of a value-taking short option may be attached to the option letter (POSIX/getopt style): `-fprog.awk`, `-vx=1`, and `-F:` are equivalent to `-f prog.awk`, `-v x=1`, and `-F :`. This applies to `-f`, `-v`, `-F`, `-L`, `-K`, and `-l`.
>
> - Variables and formatting
>
>   - `-v <name=value>` assigns a variable before execution begins.
>   - `-F <fs>` sets the initial field separator.
>   - `--locale <locale>` sets the locale used to format numbers, through `Locale.forLanguageTag(...)`.
>   - `-t` keeps associative array keys sorted.
>   - `--posix` enforces POSIX-oriented compile-time behavior such as disabling gawk-style nested arrays, all gawk `@` forms, and the `BEGINFILE` / `ENDFILE` special patterns, and makes `sub()` / `gsub()` follow the POSIX backslash rules in their replacement text (see [Where Jawk follows gawk](compatibility.html#where-jawk-follows-gawk)).
>
> - Extensions and sandbox
>
>   - `-l <extension>` or `--load <extension>` loads an extension by registered identifier, simple class name, or fully qualified class name; a class that is not registered yet resolves by its fully qualified class name only, and must be on the JVM classpath (set `JAWK_CLASSPATH` when using the `jawk` launcher). Passing `-l` replaces the default extension set (the gawk compatibility extension), so add `-l GawkExtension` when the script still needs the gawk builtins.
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
>   - `-V` and `--version` print the Jawk version and the Java runtime that executes it, then exit. They must be used by themselves.
>   - Usage output names the command Jawk was invoked as: the installed `jawk` launcher passes its invocation name through the `JAWK_PROGRAM_NAME` environment variable, and without that variable the output falls back to the direct `java -jar <jar>` form.
>   - Missing option arguments, invalid `-v` syntax, or missing scripts cause argument parsing to fail. An unknown option is rejected only when no program text has been supplied yet; otherwise it flows to `ARGV` as described above.
>   - A run that fails reports the problem on standard error and exits with a non-zero status.

## Execution Notes

- `--dump-syntax`, `--dump-intermediate`, `-K`, `-h`, `-?`, `-V`, `--version`, and `--list-ext` do not execute the script, and ignore an ambient `JAWK_PERSISTENT_MEMORY`. `--dump-syntax`, `--dump-intermediate`, and `-K` also ignore `--persist`, while the standalone modes (`-h`, `-?`, `-V`, `--version`, `--list-ext`) reject it like any other extra argument. `--profile` does execute the script, and keeps the normal AWK output on stdout.
- `-f` compiles source now, `-L` loads a program compiled earlier. `--posix` is rejected together with `-L`: loading a precompiled program bypasses source compilation, so there is no compile-time behavior left to restrict. A program compiled with `--posix -K` records that mode and keeps the POSIX `sub()` / `gsub()` replacement rules when loaded with `-L`.
- `-S` applies at compile time as well as at run time — a sandboxed run rejects the forbidden constructs while compiling the script.

## See Also

- [CLI Quickstart](cli.html)
- [Java Quickstart](java.html)
- [Compatibility and Compliance](compatibility.html)
