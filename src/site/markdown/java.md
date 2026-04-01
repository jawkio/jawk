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

## The Shortest Path: run()

`run()` is the smallest API surface for full AWK programs when you want the printed output back as a Java `String`:

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }", "hello world");
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

awk.run(
        program,
        new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8)),
        new AppendableAwkSink(new StringBuilder(), Locale.US));
```

For full control, use the explicit advanced overload:

```java
awk.run(
        program,
        myInputSource,
        Arrays.asList("mode=csv"),
        Collections.<String, Object>singletonMap("prefix", "row="),
        mySink);
```

`AwkSettings` still holds the defaults for the `Awk` instance. Passing a sink directly to `run(...)` overrides that default for one call only.

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

When one execution needs a different destination, pass that `AwkSink` directly to `run(...)` instead of mutating shared settings.

## Custom Output with AwkSink

Use [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html) when plain text is not the right abstraction.

An `AwkSink` receives raw `print(...)` and `printf(...)` calls together with the current AWK formatting state. The sink's numeric locale is fixed when you construct it:

```java
public final class CollectingSink extends AwkSink {
    private final List<List<Object>> prints = new ArrayList<List<Object>>();
    private final PrintStream processOutput = System.out;

    public CollectingSink() {
        super(Locale.US);
    }

    @Override
    public void print(String ofs, String ors, String ofmt, Object... values) {
        prints.add(Arrays.asList(Arrays.copyOf(values, values.length)));
    }

    @Override
    public void printf(String ofs, String ors, String ofmt, String format, Object... values) {
        // store format + values however your application wants
    }

    @Override
    public PrintStream getPrintStream() {
        return processOutput;
    }
}
```

This is the extension point to use when your host application wants structured AWK output instead of rendered text.

## Reusable Runtime: AVM

When you want to keep the same runtime alive across several calls, create an `AVM`:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("BEGIN { print \"value\" }");

try (AVM avm = awk.createAvm()) {
    avm.setAwkSink(mySink);
    avm.interpret(program, myInputSource, Collections.<String>emptyList(), null);
    avm.interpret(program, myOtherInputSource);
}
```

`AVM` is sequential-only and intentionally stateful. Use it when performance matters and you want one reusable runtime for repeated program runs or repeated expression evaluation.

## Which API Should I Use?

- `run(String, String)` or `run(String, InputStream)` for the shortest string-in, string-out path.
- `compile(...)` plus `run(...)` when a whole AWK program is reused.
- `compileExpression(...)` plus `eval(...)` when one expression is reused.
- `createAvm()` when you want one reusable runtime across several calls.

## Next Steps

- [Structured input and variables](java-input.html)
- [Compile, eval, and reuse](java-compile.html)
- [Advanced runtime, AVM, sandboxing, and JSR 223](java-advanced.html)


