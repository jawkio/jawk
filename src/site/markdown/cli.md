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

## Read Input Files

Input filenames passed after the script become runtime operands and are processed as AWK input files:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -F : '{ print FILENAME ":" FNR ":" $1 }' /etc/passwd /etc/group
```

Jawk follows the usual AWK distinction between the script itself and the remaining operands. Files and `name=value` operands after the script are visible through `ARGV` and `ARGC`.

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

## Enable Sandbox Mode

Use `-S` or `--sandbox` to switch to the sandboxed tuple compiler and runtime:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -S -f script.awk input.txt
```

Sandbox mode disables `system()`, input and output redirection, command pipelines, and related runtime features that are intentionally unsafe in a restricted host environment.
