keywords: cli, awk, command line, gawk, bwk, mawk
description: Quickstart guide for running Jawk from the command line.

# Jawk CLI Quickstart

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk CLI behaves like AWK, but runs entirely on the JVM. You can pass an inline program, read a script from a file, feed input through standard input or filenames, assign variables, load explicit extensions, dump syntax or tuples, precompile tuples, and switch to a sandboxed runtime when needed.

> [!WARNING]
> Shell quoting differs by platform. The examples below use separate command forms where quoting is meaningfully different. If a script works in one shell but not another, the quoting is usually the first thing to check.

## Run an Inline Script

> [!TABS]
> - Linux/UNIX
>
>   ```shell-session
>   $ echo "hello world" | java -jar jawk-${project.version}-standalone.jar '{ print $2 ", " $1 "!" }'
>   world, hello!
>   ```
>
> - Windows
>
>   ```shell-session
>   C:\> echo hello world | java -jar jawk-${project.version}-standalone.jar "{ print $2 "", "" $1 ""!"" }"
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
$ java -jar jawk-${project.version}-standalone.jar -f totals.awk values.txt
```

You can repeat `-f` to combine multiple script sources before execution.

## Read from stdin

If you do not provide input filenames after the script, Jawk reads from standard input:

```shell-session
$ printf "alpha\nbeta\n" | java -jar jawk-${project.version}-standalone.jar '{ print NR ":" $0 }'
1:alpha
2:beta
```

You can also request standard input explicitly with the `-` operand, which makes it possible to interleave it with regular input files. `FILENAME` is `-` while standard input is being read:

```shell-session
$ echo "from stdin" | java -jar jawk-${project.version}-standalone.jar '{ print FILENAME ":" $0 }' before.txt - after.txt
```

## Read Input Files

Input filenames passed after the script become runtime operands and are processed as AWK input files:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -F : '{ print FILENAME ":" FNR ":" $1 }' /etc/passwd /etc/group
```

Jawk follows the usual AWK distinction between the script itself and the remaining operands. Files and `name=value` operands after the script are visible through `ARGV` and `ARGC`.

Use `--` to mark the end of options when the script or the first operand could otherwise be mistaken for an option:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -f totals.awk -- values.txt
```

As in gawk, once the program text has been supplied with `-f` or `-L`, an unknown option also ends option processing and is passed on to the AWK program through `ARGV`, which is useful for `#!` interpreter scripts.

### BEGINFILE and ENDFILE Rules

Jawk supports gawk's `BEGINFILE` and `ENDFILE` special patterns, which hook into the command-line file processing loop. `BEGINFILE` rules run just before the first record of each input file is read, with `FILENAME` set, `FNR` at 0, `$0` cleared, and `ARGIND` designating the `ARGV` entry being processed. `ENDFILE` rules run when the last record of a file has been consumed — even for empty files — and before the `END` rules for the last file.

When a `BEGINFILE` rule is present, a file that cannot be opened is no longer an immediate fatal error: `ERRNO` carries the error description (for example `No such file or directory` or `Is a directory`), and the script can skip the file with `nextfile`. If the `BEGINFILE` rule does not skip it, the usual fatal error is raised:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar '
    BEGINFILE { if (ERRNO != "") { printf "skipping %s (%s)\n", FILENAME, ERRNO; nextfile } }
    { print FILENAME ":" FNR ":" $0 }' good.txt missing.txt
good.txt:1:hello
skipping missing.txt (No such file or directory)
```

The `nextfile` statement is also available in ordinary rules — including from user-defined functions — and abandons the rest of the current input file after running the `ENDFILE` rules. `next` is rejected inside `BEGINFILE`/`ENDFILE` rules, `nextfile` is rejected inside `ENDFILE`, `BEGIN`, and `END` rules, and only redirected forms of `getline` (such as `getline line < "file"`) may be used inside `BEGINFILE`/`ENDFILE`, all matching gawk's restrictions.

When `BEGINFILE`/`ENDFILE` rules are present, a non-redirected `getline` in an ordinary rule never crosses a file boundary: it reports end-of-input at the end of the current file, and the main loop then runs the `ENDFILE` and `BEGINFILE` rules before the next file's records are processed. (gawk instead lets such a `getline` pull in the next file's first record, firing the hooks mid-statement.) Without `BEGINFILE`/`ENDFILE` rules, `getline` keeps the classic AWK behavior of streaming across input files.

As in gawk, `BEGINFILE` and `ENDFILE` are gawk extensions: with `--posix` they are not special and parse as ordinary identifiers.

## Pass Variables

Use `-v` for variables that must exist before `BEGIN` runs:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -v prefix=hello 'BEGIN { print prefix }'
hello
```

Use `name=value` operands after the script for AWK-style file-list assignments that take effect before the next input file is consumed:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar '{ print mode ":" $0 }' mode=csv data.txt
```

Set the field separator with `-F` when you want Jawk to split text records differently:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -F : '{ print $1 "," $6 }' /etc/passwd
```

Use `--posix` when you want POSIX-oriented compile-time behavior from the CLI. In particular, it disables gawk-style nested arrays, so classic multi-dimensional subscripts such as `a[i, j]` remain valid while `a[i][j]` is rejected:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar --posix 'BEGIN { a[1,2] = 42; print a[1,2] }'
42
```

Because `-L` loads an already compiled tuples file, Jawk rejects `--posix` together with `-L` instead of pretending that it can re-apply compile-time restrictions after the fact.

## Load Extensions

List the currently registered extension identifiers:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar --list-ext
```

Then load one explicitly:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l stdin -f script.awk
```

Jawk accepts the registered extension name, the simple class name, or the fully qualified class name. Additional extension classes can be placed on the JVM classpath before launching Jawk.

When no `-l` option is given, Jawk enables the built-in [GNU Awk compatibility extension](extensions.html#gawk), which provides `asort()`, `typeof()`, and the other gawk builtins. Passing `-l` replaces that default set, so add `-l GawkExtension` when the script still needs the gawk functions:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l GawkExtension -l stdin -f script.awk
```

## Enable Sandbox Mode

Use `-S` or `--sandbox` to switch to the sandboxed tuple compiler and runtime:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -S -f script.awk input.txt
```

Sandbox mode disables `system()`, input and output redirection, command pipelines, and related runtime features that are intentionally unsafe in a restricted host environment.

## See Also

- [CLI Reference](cli-reference.html) for the full list of options and flags
- [Java Quickstart](java.html) if you want to embed AWK in a JVM application
- [Using Extensions](extensions.html)
- [Compatibility and Differences](compatibility.html)
