keywords: output, awksink, print, printf, custom output, appendable
description: Control where Jawk sends output, from simple streams to fully custom sinks.

# Custom Output

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Output is specified per-call on the builder, not in `AwkSettings`. Every `execute(...)` overload lets you direct output to a stream, an `Appendable`, a custom [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html), or back as a `String`.

## Capture as a String

A no-argument `execute()` returns the printed output as a `String`:

```java
Awk awk = new Awk();
String result = awk.script("BEGIN { print \"hello\" }").execute();
// result == "hello\n"
```

## Redirect to a Stream

Pass an `OutputStream` to `execute(...)`:

```java
Awk awk = new Awk();
awk.script("BEGIN { print \"logged\" }").execute(new FileOutputStream("output.txt"));
```

To print to `System.out` explicitly:

```java
awk.script("BEGIN { print \"hello\" }").execute(System.out);
```

## Capture into an Appendable

Pass an `Appendable` to `execute(...)` to collect output in a `StringBuilder`, `StringWriter`, or any `Appendable`:

```java
Awk awk = new Awk();
StringBuilder output = new StringBuilder();
awk.script("BEGIN { print \"captured\" }").execute(output);
// output.toString() == "captured\n"
```

## Per-Call Output

Each builder invocation specifies its own output destination. This lets you direct different executions of the same program to different targets without any shared mutable state:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print $1 }");

StringBuilder first = new StringBuilder();
awk.script(program)
        .input("alpha beta\n")
        .execute(first);

StringBuilder second = new StringBuilder();
awk.script(program)
        .input("gamma delta\n")
        .execute(second);
```

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

> [!TABS]
> * `print(...)`
>
>   | Parameter | AWK Variable | Description |
>   | --- | --- | --- |
>   | `ofs` | `OFS` | Output Field Separator, inserted between values |
>   | `ors` | `ORS` | Output Record Separator, appended after the record |
>   | `ofmt` | `OFMT` | Default numeric output format |
>   | `values` | — | The raw AWK values passed to `print` |
>
> * `printf(...)`
>
>   | Parameter | AWK Variable | Description |
>   | --- | --- | --- |
>   | `ofs` | `OFS` | Output Field Separator, inserted between values |
>   | `ors` | `ORS` | Output Record Separator, appended after the record |
>   | `ofmt` | `OFMT` | Default numeric output format |
>   | `format` | — | The AWK format string |
>   | `values` | — | The AWK values to be formatted |

### getPrintStream

`getPrintStream()` provides the `PrintStream` used by AWK output redirection (`print > "file"`, `print | "cmd"`). Implementations typically return `System.out` or a custom stream. If your sink does not support redirection, return a no-op stream.

### Using a Custom Sink

Pass the sink to `execute(...)` on the builder:

```java
Awk awk = new Awk();
CollectingSink sink = new CollectingSink();

awk.script("{ print $1, $2 }")
        .input("alpha beta\ngamma delta\n")
        .execute(sink);

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
| Capture as `String` | `execute()` | `awk.script(s).execute()` |
| Print to a stream | `execute(OutputStream)` | `awk.script(s).execute(fileOut)` |
| Print to stdout | `execute(System.out)` | `awk.script(s).execute(System.out)` |
| Capture to `Appendable` | `execute(Appendable)` | `awk.script(s).execute(sb)` |
| Structured collection | `execute(AwkSink)` | `awk.script(s).execute(mySink)` |

## See Also

- [Java Quickstart](java.html)
- [Structured Input and Variables](java-input.html)
- [Advanced Runtime](java-advanced.html)
- [AwkSink Javadoc](apidocs/io/jawk/jrt/AwkSink.html)
