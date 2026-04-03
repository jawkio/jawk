keywords: output, awksink, print, printf, custom output, appendable
description: Control where Jawk sends output, from simple streams to fully custom sinks.

# Custom Output

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

By default, Jawk sends `print` and `printf` output to `System.out`. You can redirect it to a stream, an `Appendable`, or a fully custom [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html) that receives raw AWK values instead of rendered text.

## Default: Standard Output

A plain `Awk` instance prints to `System.out`:

```java
Awk awk = new Awk();
awk.run("BEGIN { print \"hello\" }").execute();
// prints "hello" to stdout
```

## Redirect to a Stream

Use `AwkSettings.setOutputStream(...)` to send output to any `PrintStream`:

```java
AwkSettings settings = new AwkSettings();
settings.setOutputStream(new PrintStream(new FileOutputStream("output.txt")));

Awk awk = new Awk(settings);
awk.run("BEGIN { print \"logged\" }").execute();
```

## Capture into an Appendable

Use `AwkSettings.setOutputAppendable(...)` to collect output in a `StringBuilder`, `StringWriter`, or any `Appendable`:

```java
AwkSettings settings = new AwkSettings();
StringBuilder output = new StringBuilder();
settings.setOutputAppendable(output);

Awk awk = new Awk(settings);
awk.run("BEGIN { print \"captured\" }").execute();
// output.toString() == "captured\n"
```

The `run(...).capture()` convenience method uses this mechanism internally.

## Per-Call Output Override

When a single execution needs a different destination, pass an `AwkSink` via `.sink(...)` instead of mutating shared settings:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print $1 }");

StringBuilder first = new StringBuilder();
awk.run(program)
        .input("alpha beta\n")
        .sink(new AppendableAwkSink(first, Locale.US))
        .execute();

StringBuilder second = new StringBuilder();
awk.run(program)
        .input("gamma delta\n")
        .sink(new AppendableAwkSink(second, Locale.US))
        .execute();
```

The `.sink(...)` override applies only to that one execution. The `AwkSettings` default remains unchanged.

## Custom Output with AwkSink

Use [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html) when plain text is not the right abstraction. An `AwkSink` receives raw `print(...)` and `printf(...)` calls together with the current AWK formatting state.

### Implementing an AwkSink

Extend `AwkSink` and override `print(...)`, `printf(...)`, and `getPrintStream()`:

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

    public List<List<Object>> getCollectedPrints() {
        return prints;
    }
}
```

### Sink Parameters

The `print(...)` callback receives:

| Parameter | AWK Variable | Description |
| --- | --- | --- |
| `ofs` | `OFS` | Output Field Separator, inserted between values |
| `ors` | `ORS` | Output Record Separator, appended after the record |
| `ofmt` | `OFMT` | Default numeric output format |
| `values` | `$1..$NF` | The raw AWK values passed to `print` |

The `printf(...)` callback additionally receives:

| Parameter | Description |
| --- | --- |
| `format` | The AWK format string |
| `values` | The AWK values to be formatted |

### getPrintStream

`getPrintStream()` provides the `PrintStream` used by AWK output redirection (`print > "file"`, `print | "cmd"`). Implementations typically return `System.out` or a custom stream. If your sink does not support redirection, return a no-op stream.

### Using a Custom Sink

Pass the sink to `.sink(...)` on the run builder or set it as the default through `AwkSettings.setAwkSink(...)`:

```java
Awk awk = new Awk();
CollectingSink sink = new CollectingSink();

awk.run("{ print $1, $2 }")
        .input("alpha beta\ngamma delta\n")
        .sink(sink)
        .execute();

// sink.getCollectedPrints() contains [[alpha, beta], [gamma, delta]]
```

### Built-In Sink Implementations

Jawk provides two built-in `AwkSink` implementations:

- **`AwkSink.from(PrintStream, Locale)`** creates a sink that renders output to a `PrintStream`. This is the default behavior.
- **`AppendableAwkSink`** renders output to any `Appendable` such as `StringBuilder` or `StringWriter`.

### Locale in AwkSink

The numeric locale is fixed when you construct the sink. It controls how AWK formats numbers in `print` and `printf` output. Pass the locale to the `AwkSink` constructor:

```java
AwkSink frenchSink = AwkSink.from(System.out, Locale.FRANCE);
```

## Choosing the Right Output Strategy

| Goal | API | Example |
| --- | --- | --- |
| Print to stdout | Default | `awk.run(script).execute()` |
| Print to a file | `setOutputStream` | `settings.setOutputStream(new PrintStream(...))` |
| Capture as `String` | `.capture()` | `awk.run(script).input(text).capture()` |
| Capture to `StringBuilder` | `setOutputAppendable` | `settings.setOutputAppendable(sb)` |
| Structured collection | Custom `AwkSink` | `awk.run(script).sink(mySink).execute()` |
| Per-call override | `.sink(...)` | See Per-Call Output Override above |

## See Also

- [Java Quickstart](java.html)
- [Structured Input and Variables](java-input.html)
- [Advanced Runtime](java-advanced.html)
- [AwkSink Javadoc](apidocs/io/jawk/jrt/AwkSink.html)
