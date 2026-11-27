keywords: java, api, embed, sdk
description: Quickstart guide for using Jawk from Java applications.

# Jawk in Java

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

For most Java applications, start with [`Awk`](apidocs/io/jawk/Awk.html). It gives you:

- short convenience methods for string-in, string-out use cases
- compiled `AwkProgram` and `AwkExpression` artifacts for reuse
- direct access to [`AVM`](apidocs/io/jawk/backend/AVM.html) when you want one reusable runtime

## Start with Awk

Create an `Awk` instance directly for normal use:

```java
Awk awk = new Awk();
```

Construct it with `AwkSettings` when you need engine defaults such as field separators, locale, or record separators:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
```

### AwkSettings Reference

| Setter | Default | Description |
| --- | --- | --- |
| `setFieldSeparator(String)` | `null` (default AWK FS) | The initial value of `FS`, the field separator |
| `setLocale(Locale)` | `Locale.US` | Locale for numeric output formatting and for `tolower()` / `toupper()` |
| `setDefaultRS(String)` | `"\n"` | Default value for `RS`, the record separator  |
| `setUseSortedArrayKeys(boolean)` | `false` | Whether to keep associative array keys in sorted order |
| `setPosix(boolean)` | `false` | Enforce POSIX compile-time behavior, rejecting gawk syntax such as `a[i][j]`, `split(..., a[i])`, all `@` forms, and the `BEGINFILE` / `ENDFILE` special patterns |
| `setStrictNumbers(boolean)` | `false` | Make arithmetic fail with an `AwkNumberFormatException` on a value that is not a number (`""`, `"N/A"`, `"12 W"`, a field beyond `NF`, a variable never assigned) instead of counting it as zero or as its numeric prefix. See [Strict Numbers](java-advanced.html#strict-numbers) |
| `putVariable(String, Object)` | Empty map | Pre-set variables available before `BEGIN` |

The output destination is not a setting: it is chosen per call, when you execute the script. See
[Custom Output](java-output.html). For more on passing variables to scripts, see
[Variables and Arguments](java-variables.html).

By default, Jawk accepts classic AWK syntax and the [gawk extensions it implements](compatibility.html).
Enable POSIX mode when you need strict classic parsing:

```java
AwkSettings settings = new AwkSettings();
settings.setPosix(true);

Awk awk = new Awk(settings);
```

Construct it with extension instances when you want those functions available to the script:

```java
Awk awk = new Awk(new GawkExtension(), new MyExtension());
```

A plain `new Awk()` enables the built-in [GNU Awk compatibility extension](extensions.html#gawk) (`asort()`, `typeof()`, and friends). Passing explicit extension instances replaces that default set, so include a `new GawkExtension()` in the list when the script still needs the gawk builtins.

The dedicated [Writing Extensions](extensions-writing.html) guide covers how to write your own extensions to expose new functions, written in Java, to your AWK scripts.

## The Shortest Path: `script().execute()`

`script().execute()` is the smallest API surface for full AWK programs when you want the printed output back as a Java `String`:

```java
Awk awk = new Awk();
String result = awk.script("{ print toupper($0) }").input("hello world").execute();
// result = "HELLO WORLD\n"
```

Use this when:

- you already have the script and input in memory
- you want the rendered AWK output as a `String`
- you do not need explicit `ARGV`, per-execution variables, or runtime reuse

## Compiled Programs

When the same script will be reused, compile it once and run the compiled program:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print prefix $1 }");

awk.script(program)
        .input("alpha beta\n")
        .execute();
```

## Output Destination

Where the script's output goes is decided by the `execute(...)` overload you call:

- `execute()` returns the printed output as a `String`
- `execute(PrintStream)` sends output to a `PrintStream` such as `System.out`
- `execute(OutputStream)` sends output to any `OutputStream`
- `execute(Appendable)` captures text into a `StringBuilder` or `Appendable`
- `execute(AwkSink)` hands raw `print`/`printf` calls to your own [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html), so the host can collect structured values instead of rendered text

See the [Custom Output](java-output.html) guide for the full `AwkSink` contract, the built-in implementations, and locale handling.

## Reusable Runtime: AVM

When you want to keep the same runtime alive across several calls, create an `AVM`:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("BEGIN { print \"value\" }");

try (AVM avm = awk.createAvm()) {
    avm.setAwkSink(mySink);
    avm.execute(program, myInputSource, Collections.<String>emptyList(), null);
    avm.execute(program, myOtherInputSource);
}
```

`AVM` is sequential-only and intentionally stateful. Use it when performance matters and you want one reusable runtime for repeated program runs or repeated expression evaluation.

## Which API Should I Use?

- `script(text).input(text).execute()` for the shortest string-in, string-out path.
- `compile(...)` plus `script(compiled).execute(out)` when a whole AWK program is reused.
- `compileExpression(...)` plus `eval(...)` when one expression is reused.
- `createAvm()` when you want one reusable runtime across several calls.

## Complete Example

The example below reads CSV input, sums the second column per category in the first column, and captures the result:

```java
import io.jawk.Awk;
import io.jawk.util.AwkSettings;

public class JawkDemo {
    public static void main(String[] args) throws Exception {
        // Configure the engine for CSV input
        AwkSettings settings = new AwkSettings();
        settings.setFieldSeparator(",");

        Awk awk = new Awk(settings);

        // AWK script: accumulate totals by category, print sorted results
        String script = "{ totals[$1] += $2 } END { for (k in totals) print k, totals[k] }";

        // Input data
        String csv = "fruit,10\nvegetable,20\nfruit,15\nvegetable,5\n";

        // Execute and capture the printed output
        String result = awk.script(script).input(csv).execute();
        System.out.println(result);
    }
}
```

## See Also

- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Custom Output](java-output.html)
- [Compile, Eval, and Reuse](java-compile.html)
- [Advanced Runtime](java-advanced.html) — AVM reuse, sandboxing, JSR 223, and thread safety
- [Using Extensions](extensions.html)
