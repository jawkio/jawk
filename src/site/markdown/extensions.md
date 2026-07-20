keywords: extensions, plugins, functions, loading
description: Load and use Jawk extensions from the CLI or the Java API.

# Using Extensions

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk extensions let Java code expose additional AWK-callable functions to a script. By default, Jawk enables the built-in [GNU Awk compatibility extension](#gawk), so gawk functions such as `asort()` and `typeof()` work out of the box. Every other extension is opt-in: the host application or CLI invocation decides exactly which extension instances are available.

> [!IMPORTANT]
> Apart from the default gawk compatibility extension, extensions are opt-in. Sandbox mode blocks dynamic extension loading during script execution, but preloading an extension through the CLI or the Java host remains an explicit host decision.

## How Extensions Are Enabled

There are two supported loading paths:

- CLI: `-l <extension>` or `--load <extension>`
- Java API: pass extension instances to an `Awk` constructor

If you do neither, the default extension set applies: the gawk compatibility extension and nothing else. Specifying an explicit extension list — through `-l` on the CLI or an `Awk` constructor — replaces the default set, so add `GawkExtension` to the list when the script still needs the gawk functions.

## List Available Extensions

From the CLI, print the currently registered extension identifiers:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar --list-ext
```

From Java, inspect the registry directly:

```java
Awk.listAvailableExtensions().forEach((name, extension) ->
        System.out.println(name + " -> " + extension.getClass().getName()));
```

The registry may expose multiple identifiers for the same implementation, for example a registered name, a simple class name, and a fully qualified class name.

## Load Extensions from the CLI

Load an extension with any supported identifier:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l stdin -f script.awk
```

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l io.jawk.ext.StdinExtension -f script.awk
```

If the extension class is not already registered, the CLI can still resolve it by fully qualified class name as long as the class is available on the JVM classpath.

## Load Extensions from Java

Pass extension instances directly to `Awk`:

```java
Awk awk = new Awk(StdinExtension.INSTANCE, new MyExtension());
```

That keeps extension availability explicit and local to the embedding code.

## Built-In Extensions

<a id="gawk"></a>
### GNU Awk Compatibility (Enabled by Default)

`GawkExtension` implements gawk-specific builtins and belongs to the default extension set, so its functions are available whenever no explicit extension list is supplied:

- `asort(source [, dest [, how]])` sorts an array by value and renumbers the result with integer indices starting at 1
- `asorti(source [, dest [, how]])` sorts an array by index instead of by value
- `typeof(x)` returns the gawk type category of a value: `"number"`, `"string"`, `"strnum"`, `"array"`, `"regexp"`, `"number|bool"`, `"unassigned"`, or `"untyped"`
- `isarray(x)` returns 1 when the value is an array, 0 otherwise
- `mkbool(expression)` creates a gawk-style boolean-typed number
- `gensub(regexp, replacement, how [, target])` returns the substituted text without modifying the target

`asort()`, `asorti()`, and the `for (index in array)` statement honor `PROCINFO["sorted_in"]` with gawk's predefined comparison modes: `@unsorted`, `@ind_str_asc`, `@ind_num_asc`, `@val_str_asc`, `@val_num_asc`, `@val_type_asc`, and their `_desc` counterparts. String comparisons ignore case when `IGNORECASE` is non-zero.

Beyond the extension functions, the interpreter itself implements gawk's `BEGINFILE` / `ENDFILE` special patterns, the `nextfile` statement, and the `ERRNO` and `ARGIND` special variables (see the [CLI guide](cli.html#BEGINFILE_and_ENDFILE_Rules)). Like the other gawk-specific syntax, `BEGINFILE` and `ENDFILE` are not special in POSIX mode.

Scripts that reference `SYMTAB` or `FUNCTAB` get honest, Jawk-shaped content, populated by the runtime itself (outside POSIX mode): `SYMTAB` holds the names of the program's globals, Jawk's special variables, and `-v`/host-supplied variables; `FUNCTAB` holds the names of the program's user-defined functions plus the loaded extensions' function keywords. Command-line `name=value` operand assignments update `SYMTAB` live, as in gawk; ordinary in-script assignments are not reflected (the array is a startup snapshot, not gawk's live view). As in gawk, assigning a scalar to `SYMTAB` or `FUNCTAB` is a runtime error.

> [!NOTE]
> Because these functions are registered by default, `gensub`, `typeof`, `isarray`, `asort`, `asorti`, and `mkbool` become reserved function names. A script that uses them as variable or function identifiers must be run with an explicit extension list that omits `GawkExtension`.

The registry exposes the extension through identifiers such as:

- `GawkExtension`
- `io.jawk.ext.GawkExtension`
- `GNU Awk Compatibility`

### Stdin Support

The built-in registry also includes the stdin extension, which is exposed through identifiers such as:

- `stdin`
- `StdinExtension`
- `io.jawk.ext.StdinExtension`

That extension provides advanced helper functions including `StdinHasInput()`, `StdinGetline()`, and `StdinBlock()`.

## Sandbox Interaction

Sandboxing and extensions are separate concerns:

- sandboxing restricts dangerous AWK runtime features
- extension loading is still an explicit host choice
- scripts do not get to expand their own capabilities automatically

If you need a sandboxed Java embedding, construct `SandboxedAwk` with the extension instances you want to allow. If you need a sandboxed CLI run, combine `-S` with the `-l` options you want to preload.

## See Also

- [Writing Extensions](extensions-writing.html)
- [Java Quickstart](java.html)
- [CLI Basics](cli.html)
