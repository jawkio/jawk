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
| `setLocale(Locale)` | `Locale.US` | Locale for numeric output formatting |
| `setDefaultRS(String)` | Platform line separator | Default value for `RS`, the record separator  |
| `setUseSortedArrayKeys(boolean)` | `false` | Whether to keep associative array keys in sorted order |
| `setAllowArraysOfArrays(boolean)` | `true` | Whether the compiler accepts gawk-style nested array syntax such as `a[i][j]` |
| `putVariable(String, Object)` | Empty map | Pre-set variables available before `BEGIN` |

Output destination is specified per-call on the builder (`execute()`, `execute(PrintStream)`, `execute(OutputStream)`, `execute(Appendable)`, or `execute(AwkSink)`). See the [Custom Output](java-output.html) guide for details.

For more on passing variables to scripts, see [Variables and Arguments](java-variables.html).

By default, Jawk accepts both classic multi-dimensional array syntax (`a[i, j]`) and gawk-style arrays of arrays (`a[i][j]`). Disable the gawk-style parser mode when you need strict classic AWK parsing:

```java
AwkSettings settings = new AwkSettings();
settings.setAllowArraysOfArrays(false);

Awk awk = new Awk(settings);
```

Construct it with extension instances when you want those functions available to the script:

```java
Awk awk = new Awk(StdinExtension.INSTANCE, new MyExtension());
```

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

Output is specified per-call on the builder:

- `execute()` returns the printed output as a `String`
- `execute(PrintStream)` sends output to a `PrintStream` such as `System.out`
- `execute(OutputStream)` sends output to any `OutputStream`
- `execute(Appendable)` captures text into a `StringBuilder` or `Appendable`
- `execute(AwkSink)` uses a fully custom output strategy

## Custom Output with AwkSink

Use [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html) when plain text is not the right abstraction. An `AwkSink` receives raw `print(...)` and `printf(...)` calls together with the current AWK formatting state, so your host application can collect structured AWK output instead of rendered text.

```java
Awk awk = new Awk();
CollectingSink sink = new CollectingSink();

awk.script("{ print $1, $2 }")
        .input("alpha beta\ngamma delta\n")
        .execute(sink);
```

See the [Custom Output](java-output.html) guide for the full `AwkSink` contract, built-in implementations, and detailed examples.

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

- [Custom Output](java-output.html)
- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Compile, Eval, and Reuse](java-compile.html)
- [Advanced Runtime](java-advanced.html)
- [Using Extensions](extensions.html)

## Next Steps

- [Variables and arguments](java-variables.html)
- [Structured input](java-input.html)
- [Compile, eval, and reuse](java-compile.html)
- [Advanced runtime, AVM, sandboxing, and JSR 223](java-advanced.html)
