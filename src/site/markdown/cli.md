keywords: cli, awk, command line, gawk, bwk, mawk
description: Quickstart guide for running Jawk from the command line.

# Jawk CLI Quickstart

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk CLI behaves like AWK, but runs entirely on the JVM. You can pass an inline program, read a script from a file, feed input through standard input or filenames, assign variables, load extensions, precompile a script for later runs, and switch to a sandboxed runtime when needed.

The examples below use the `jawk` command set up by the [one-command install](install.html). Running the standalone jar directly with `java -jar jawk-${project.version}-standalone.jar` accepts exactly the same arguments. The `jawk` launcher locates a Java runtime (Java 8 or later) every time it runs, checking `JAWK_JAVA_HOME`, `JAVA_HOME`, and the `PATH` in that order, so installing or upgrading Java later needs no Jawk reinstall.

> [!WARNING]
> Shell quoting differs by platform. The examples below use separate command forms where quoting is meaningfully different. If a script works in one shell but not another, the quoting is usually the first thing to check.

## Run an Inline Script

> [!TABS]
> - Linux/UNIX
>
>   ```shell-session
>   $ echo "hello world" | jawk '{ print $2 ", " $1 "!" }'
>   world, hello!
>   ```
>
> - Windows
>
>   ```shell-session
>   C:\> echo hello world | jawk "{ print $2 "", "" $1 ""!"" }"
>   world, hello!
>   ```

If you do not pass `-f` or `-L`, Jawk expects the next non-option argument to be the AWK program itself.

## Run a Script File

Store the AWK program in a file:

**`totals.awk`**:

```awk
BEGIN {
  total = 0
}
{
  total += $2
}
END {
  print total
}
```

And point Jawk at it with `-f`:

```shell-session
$ jawk -f totals.awk values.txt
```

You can repeat `-f` to combine multiple script sources before execution.

## Read from stdin

If you do not provide input filenames after the script, Jawk reads from standard input:

```shell-session
$ printf "alpha\nbeta\n" | jawk '{ print NR ":" $0 }'
1:alpha
2:beta
```

You can also request standard input explicitly with the `-` operand, which makes it possible to interleave it with regular input files. `FILENAME` is `-` while standard input is being read:

```shell-session
$ echo "from stdin" | jawk '{ print FILENAME ":" $0 }' before.txt - after.txt
```

Inside a script, `getline < "/dev/stdin"` reads the same standard input, and `print > "/dev/stdout"` and `print > "/dev/stderr"` write to the standard output and standard error of the process, as they do in gawk. `print > "/dev/null"` discards its output on every platform, Windows included, and `/dev/null` may be passed as an operand to read an empty input file. See [special filenames](compatibility.html#special-filenames) for details.

## Read Input Files

Input filenames passed after the script become runtime operands and are processed as AWK input files:

```shell-session
$ jawk -F : '{ print FILENAME ":" FNR ":" $1 }' /etc/passwd /etc/group
```

Jawk follows the usual AWK distinction between the script itself and the remaining operands. Files and `name=value` operands after the script are visible through `ARGV` and `ARGC`.

Use `--` to mark the end of options when the script or the first operand could otherwise be mistaken for an option:

```shell-session
$ jawk -f totals.awk -- values.txt
```

As in gawk, once the program text has been supplied with `-f` or `-L`, an unknown option also ends option processing and is passed on to the AWK program through `ARGV`, which is useful for `#!` interpreter scripts.

### BEGINFILE and ENDFILE Rules

Jawk supports gawk's `BEGINFILE` and `ENDFILE` special patterns, which hook into the command-line file processing loop. `BEGINFILE` rules run just before the first record of each input file is read; `ENDFILE` rules run once its last record has been consumed.

When a `BEGINFILE` rule is present, a file that cannot be opened is no longer an immediate fatal error: `ERRNO` carries the error description (for example `No such file or directory` or `Is a directory`), and the script can skip the file with `nextfile`. If the `BEGINFILE` rule does not skip it, the usual fatal error is raised:

```shell-session
$ jawk '
    BEGINFILE { if (ERRNO != "") { printf "skipping %s (%s)\n", FILENAME, ERRNO; nextfile } }
    { print FILENAME ":" FNR ":" $0 }' good.txt missing.txt
good.txt:1:hello
skipping missing.txt (No such file or directory)
```

The `nextfile` statement is also available in ordinary rules — including inside user-defined functions — and abandons the rest of the current input file after running the `ENDFILE` rules. See [BEGINFILE and ENDFILE](compatibility.html#beginfile-and-endfile) for the complete rules: what `next`, `nextfile`, and `getline` may do in and around these patterns, and how `--posix` turns them back into ordinary identifiers.

## Pass Variables

Use `-v` for variables that must exist before `BEGIN` runs:

```shell-session
$ jawk -v prefix=hello 'BEGIN { print prefix }'
hello
```

Use `name=value` operands after the script for AWK-style file-list assignments that take effect before the next input file is consumed:

```shell-session
$ jawk '{ print mode ":" $0 }' mode=csv data.txt
```

Set the field separator with `-F` when you want Jawk to split text records differently:

```shell-session
$ jawk -F : '{ print $1 "," $6 }' /etc/passwd
```

Use `--posix` when you want POSIX-oriented compile-time behavior from the CLI. In particular, it disables gawk-style nested arrays, so classic multi-dimensional subscripts such as `a[i, j]` remain valid while `a[i][j]` is rejected:

```shell-session
$ jawk --posix 'BEGIN { a[1,2] = 42; print a[1,2] }'
42
```

Because `-L` loads an already compiled program, Jawk rejects `--posix` together with `-L` instead of pretending that it can re-apply compile-time restrictions after the fact.

## Load Extensions

List the currently registered extension identifiers:

```shell-session
$ jawk --list-ext
```

Then load one explicitly:

```shell-session
$ jawk -l GawkExtension -f script.awk
```

Jawk accepts the registered extension name, the simple class name, or the fully qualified class name; an extension class that is not registered yet resolves by its fully qualified class name only. Additional extension classes can be placed on the JVM classpath before launching Jawk: set `JAWK_CLASSPATH` when using the `jawk` launcher, or use `java -cp` with the `io.jawk.Cli` main class when running the standalone jar (see [Using Extensions](extensions.html)).

When no `-l` option is given, Jawk enables the built-in [GNU Awk compatibility extension](extensions.html#gawk), which provides `asort()`, `typeof()`, and the other gawk builtins. Passing `-l` replaces that default set, so add `-l GawkExtension` when the script still needs the gawk functions:

```shell-session
$ jawk -l GawkExtension -l com.company.my.SampleExtension -f script.awk
```

## Enable Sandbox Mode

Use `-S` or `--sandbox` to compile and run the script with sandbox restrictions:

```shell-session
$ jawk -S -f script.awk input.txt
```

Sandbox mode disables `system()`, input and output redirection, command pipelines, `@include` source inclusion, and related features that are intentionally unsafe in a restricted host environment. Every redirection is rejected, whatever the target: the `/dev/stdin`, `/dev/stdout`, and `/dev/stderr` [special filenames](compatibility.html#special-filenames) are no more reachable from a sandboxed script than a regular file is.

`ENVIRON` is empty in sandbox mode: a sandboxed script cannot read the environment variables of the process. As a consequence, `strftime()` and `mktime()` use the default time zone of the JVM, since no `TZ` is set in `ENVIRON`, unless the script assigns `ENVIRON["TZ"]` itself.

## See Also

- [CLI Reference](cli-reference.html) for the full list of options and flags
- [Java Quickstart](java.html) if you want to embed AWK in a JVM application
- [Using Extensions](extensions.html)
- [Compatibility and Compliance](compatibility.html)
