# Jawk

![GitHub release (with filter)](https://img.shields.io/github/v/release/jawkio/jawk)
![Build](https://img.shields.io/github/actions/workflow/status/jawkio/jawk/deploy.yml)
![Reproducible](https://img.shields.io/badge/build-reproducible-green)
![GitHub top language](https://img.shields.io/github/languages/top/jawkio/jawk)
![License](https://img.shields.io/github/license/jawkio/jawk)

Jawk is a pure Java implementation of [AWK](https://en.wikipedia.org/wiki/AWK). You can run it as a CLI, embed it directly in Java applications, compile scripts to reusable tuples, evaluate AWK expressions, feed it structured input, load extensions explicitly, and enable a sandboxed runtime when you need tighter execution constraints.

## Support for POSIX AWK and Gawk

Jawk fully implements POSIX AWK, and adds support for the most commonly used gawk-specific features:

- Builtins, available by default through the built-in GNU Awk compatibility extension: `asort()`, `asorti()`, `typeof()`, `isarray()`, `mkbool()`, `gensub()`, `patsplit()`, `strtonum()`, `systime()`, `mktime()`, `strftime()`, `bindtextdomain()`, `dcgettext()`, `dcngettext()`, and `PROCINFO["sorted_in"]`-controlled array traversal
- Arrays of arrays (`a[i][j]`) and typed regexp literals (`@/re/`)
- `BEGINFILE` / `ENDFILE` special patterns, with the `ERRNO` and `ARGIND` special variables, so a script can hook into the command-line file processing loop and skip unreadable files without a fatal error
- The `nextfile` statement
- The `IGNORECASE`, `SYMTAB`, and `FUNCTAB` special variables

As in gawk, the gawk-specific syntax is not special in `--posix` mode.

## CLI Example

```shell
echo "hello world" | java -jar jawk-${project.version}-standalone.jar '{ print $2 ", " $1 "!" }'
```

The CLI follows the POSIX argument conventions: `--` marks the end of options, and the `-` operand designates standard input as an input file (with `FILENAME` set to `-`). As in gawk, once the program text has been supplied (with `-f` or `-L`), an unknown option ends option processing and is passed on to the AWK script through `ARGV`, which makes `#!` interpreter scripts work. See the [CLI documentation](https://jawk.io/cli.html) for details.

## Java Example

```java
Awk awk = new Awk();
String result = awk.script("{ print toupper($0) }").input("hello world").execute();
```

When writing custom extensions, annotate associative array parameters with `@JawkAssocArray` and declare them as `Map` values rather than concrete map implementations.

Java variables passed to embedded Jawk scripts may include `Map` and `List` values, including nested JSON-like object trees. Lists are materialized as AWK arrays with zero-based numeric indexes.

## Documentation

- Overview: https://jawk.io/index.html
- CLI: https://jawk.io/cli.html
- Java: https://jawk.io/java.html
- Extensions: https://jawk.io/extensions.html
- Writing Extensions: https://jawk.io/extensions-writing.html

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
