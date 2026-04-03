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

Construct it with `AwkSettings` when you need engine defaults such as field separators, locale, record separators, or a default output target:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");
settings.setOutputAppendable(new StringBuilder());

Awk awk = new Awk(settings);
```

Construct it with extension instances when you want those functions available to the script:

```java
Awk awk = new Awk(StdinExtension.INSTANCE, new MyExtension());
```

When you write custom extensions, annotate associative array parameters with `@JawkAssocArray` and declare them as `Map` values rather than concrete map implementations. The dedicated [Writing Extensions](extensions-writing.html) guide covers that contract in more detail.

## The Shortest Path: run().capture()

`run().capture()` is the smallest API surface for full AWK programs when you want the printed output back as a Java `String`:

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }").input("hello world").capture();
// result = "HELLO WORLD\n"
```

Use this when:

- you already have the script and input in memory
- you want the rendered AWK output as a `String`
- you do not need explicit `ARGV`, per-execution variables, or runtime reuse

## Compiled Programs and Explicit Output

When the same script will be reused, compile it once and run the compiled program:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print prefix $1 }");

awk.run(program)
        .input(new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8)))
        .sink(new AppendableAwkSink(new StringBuilder(), Locale.US))
        .execute();
```

For full control, use the fluent builder:

```java
awk.run(program)
        .input(myInputSource)
        .arguments("mode=csv")
        .variables(Collections.<String, Object>singletonMap("prefix", "row="))
        .sink(mySink)
        .execute();
```

`AwkSettings` still holds the defaults for the `Awk` instance. Passing a sink via `.sink(...)` overrides that default for one call only.

## Default Output and Per-Call Output

`AwkSettings` holds the default output target for an `Awk` instance:

```java
AwkSettings settings = new AwkSettings();
StringBuilder output = new StringBuilder();
settings.setOutputAppendable(output);

Awk awk = new Awk(settings);
```

Use:

- `setOutputStream(...)` for normal text output to a stream
- `setOutputAppendable(...)` for text capture into a `StringBuilder` or `Writer`
- `setAwkSink(...)` for a custom output strategy

When one execution needs a different destination, pass that `AwkSink` via `.sink(...)` on the run builder instead of mutating shared settings.

## Custom Output with AwkSink

Use [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html) when plain text is not the right abstraction. An `AwkSink` receives raw `print(...)` and `printf(...)` calls together with the current AWK formatting state, so your host application can collect structured AWK output instead of rendered text.

```java
Awk awk = new Awk();
CollectingSink sink = new CollectingSink();

awk.run("{ print $1, $2 }")
        .input("alpha beta\ngamma delta\n")
        .sink(sink)
        .execute();
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

- `run(script).input(text).capture()` for the shortest string-in, string-out path.
- `compile(...)` plus `run(program).execute()` when a whole AWK program is reused.
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
        String result = awk.run(script).input(csv).capture();
        System.out.println(result);
    }
}
```

## See Also

- [Custom Output](java-output.html)
- [Structured Input and Variables](java-input.html)
- [Compile, Eval, and Reuse](java-compile.html)
- [Advanced Runtime](java-advanced.html)
- [Using Extensions](extensions.html)

## Next Steps

- [Structured input and variables](java-input.html)
- [Compile, eval, and reuse](java-compile.html)
- [Advanced runtime, AVM, sandboxing, and JSR 223](java-advanced.html)


