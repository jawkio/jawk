keywords: output, awksink, print, printf, custom output, appendable
description: Control where Jawk sends output, from simple streams to fully custom sinks.

# Custom Output

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Output is specified per-call on the builder, to capture `print()` and `printf(...)` output in a stream, an `Appendable`, a custom [`AwkSink`](apidocs/io/jawk/jrt/AwkSink.html), or simply as a `String`.

## Capture as a String

A no-argument `execute()` returns the printed output as a `String`:

```java
Awk awk = new Awk();
String result = awk.script("BEGIN { print \"hello\" }").execute();
// result == "hello\n"
```

## Redirect to a Stream

Pass an `OutputStream` or `PrintStream` to `execute(...)`:

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

### Sink `print` and `printf` Parameters

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

`getPrintStream()` provides the `PrintStream` used for pumping the stdout of spawned processes (`system("...")` and pipe output from `print ... | "cmd"`) back into the host output. File redirection (`print > "file"`) does _not_ use this stream — it creates its own file-backed sink internally.

The default implementation returns a no-op stream that silently discards output. Override this method in sinks that need to capture subprocess output. Implementations typically return `System.out` or a custom stream.

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

- **`AwkSink.from(PrintStream)`** / **`AwkSink.from(PrintStream, Locale)`** creates a sink that renders output to a `PrintStream`. This is the default behavior.
- **`AwkSink.from(Appendable)`** / **`AwkSink.from(Appendable, Locale)`** renders output to any `Appendable` such as `StringBuilder` or `StringWriter`.

The overloads without a `Locale` parameter default to `Locale.US`.

## Numeric Locale

The locale controls how AWK formats numbers in `print` and `printf` output (e.g. decimal separator). The default is `Locale.US`.

### With the Builder

When you use the `AwkRunBuilder` methods (`execute()`, `execute(OutputStream)`, `execute(Appendable)`),
the locale is taken automatically from `AwkSettings`. Set it once on the `Awk` instance:

```java
Awk awk = new Awk();
awk.getSettings().setLocale(Locale.FRANCE);

// All execute() variants use the French locale for number formatting
String result = awk.script("BEGIN { print 3.14 }").execute();
// result == "3,14\n"
```

### With a Custom AwkSink

When you pass an `AwkSink` to `execute(AwkSink)`, the sink carries its own locale — `AwkSettings` is not involved.
Specify the locale when constructing the sink:

```java
AwkSink frenchSink = AwkSink.from(System.out, Locale.FRANCE);
awk.script("BEGIN { print 3.14 }").execute(frenchSink);
```

When extending `AwkSink` directly, pass the locale to the `super(...)` constructor:

```java
public class MySink extends AwkSink {
    public MySink(Locale locale) {
        super(locale);
    }
    // ...
}
```

## Choosing the Right Output Strategy

| Goal | API | Example |
| --- | --- | --- |
| Capture as `String` | `execute()` | `awk.script(s).execute()` |
| Print to a `PrintStream` | `execute(PrintStream)` | `awk.script(s).execute(System.out)` |
| Print to an `OutputStream` | `execute(OutputStream)` | `awk.script(s).execute(fileOut)` |
| Capture to `Appendable` | `execute(Appendable)` | `awk.script(s).execute(sb)` |
| Structured collection | `execute(AwkSink)` | `awk.script(s).execute(mySink)` |

## Subprocess Output

When AWK runs an external command via `system("...")` or a pipe (`print ... | "cmd"`),
the command's **stdout** is pumped into the sink's `PrintStream` returned by
`getPrintStream()`. The built-in `OutputStreamAwkSink` and `AppendableAwkSink` both
override this method, so subprocess stdout is captured alongside normal output. For a
custom `AwkSink` that does _not_ override `getPrintStream()`, subprocess stdout is
silently discarded (the default no-op stream); override `getPrintStream()` or call
`errorStream(...)` / provide a sink that returns a real stream to capture it.

Subprocess **stderr** defaults to the sink's `PrintStream` as well. To redirect it to
a separate stream, use `errorStream(PrintStream)`:

```java
awk.script("BEGIN { system(\"mycommand\") }")
        .errorStream(System.err)
        .execute(System.out);
```

The CLI uses `.errorStream(System.err)` so that command errors appear on the
console rather than mixing with normal output.

## See Also

- [Java Quickstart](java.html)
- [Structured Input and Variables](java-input.html)
- [Advanced Runtime](java-advanced.html)
- [AwkSink Javadoc](apidocs/io/jawk/jrt/AwkSink.html)
