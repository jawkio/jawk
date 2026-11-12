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
    public void printf(String ofs, String ors, String ofmt, String convfmt, String format, Object... values) {
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
>   | `convfmt` | `CONVFMT` | Number-to-string conversion format used by `%s` |
>   | `format` | — | The AWK format string |
>   | `values` | — | The AWK values to be formatted |

### Bare `print` and printRecord

A `print` without arguments to the default output, and a rule without an action (a pattern
alone), call `printRecord(ofs, ors, ofmt, fields, record)`. Its default implementation calls
`print(ofs, ors, ofmt, record)`, so a sink that does not override it receives `$0` as before.
Override it to receive the current fields `$1` to `$NF` as the script left them (assigned fields
and a changed `NF` included), for instance to turn each record into one row of values:

```java
@Override
public void printRecord(String ofs, String ors, String ofmt, List<Object> fields, Object record) {
    List<String> row = new ArrayList<String>();
    for (Object field : fields) {
        row.add(formatPrintArgument(field, ofmt)); // the text "print $i" would write
    }
    rows.add(row);
}
```

`fields` is a read-only view of the current record, valid only during the call; the record is split
into fields only when the sink reads it. `print $0`, `print` with arguments, and a redirected
`print` (`> file`, `| cmd`, and `> "/dev/stdout"`) still call `print(...)`.

### getPrintStream

`getPrintStream()` provides the `PrintStream` that receives the stdout of the processes a script
spawns with `system("...")` or an output pipe (`print ... | "cmd"`). The default implementation
discards it silently, so override this method — typically returning `System.out` or a stream of your
own — in sinks that must capture subprocess output; see [Subprocess Output](#subprocess-output).
File redirection (`print > "file"`) does _not_ use this stream: it creates its own file-backed sink
internally.

### Special Filenames

The gawk [special filenames](compatibility.html#special-filenames) are routed to the streams the run is configured with rather than to files of those names:

- `print > "/dev/stdout"` (and `/dev/fd/1`) writes to the sink like an unredirected `print`, so a script that writes there is captured by the host like any other output. A bare `print > "/dev/stdout"` calls `print(...)` with `$0`, not [`printRecord`](#bare-print-and-printrecord).
- `print > "/dev/stderr"` (and `/dev/fd/2`) writes to the stream passed to `errorStream(PrintStream)`, which defaults to `System.err`.
- `getline < "/dev/stdin"` (and `/dev/fd/0`) reads the stream passed to `input(...)` — not the JVM's standard input, unless that is what the host supplied. When the run is fed with a structured `InputSource` instead of a stream, `/dev/stdin` falls back to `System.in`.

`close()` on these names never closes the host's streams.

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

Jawk provides three built-in `AwkSink` implementations:

- **`OutputStreamAwkSink`** renders output to an `OutputStream` or a `PrintStream`. This is the default behavior, and what `execute(OutputStream)` and `execute(PrintStream)` use.
- **`AppendableAwkSink`** renders output to any `Appendable`, such as a `StringBuilder` or a `StringWriter`.
- **`JavaStringFormatAwkSink`** renders `printf`/`sprintf` with Java's standard
  `String.format(...)` instead of AWK's formatting rules, giving scripts access to Java-only
  conversions (`%,d` grouping, `%(d` negative parentheses, `%tY` date/time, etc.) and faster
  formatting. Conversions must match the value's Java type: AWK integral numbers arrive as
  `Long`, other numbers as `Double`, and text as `String`, so `%d` requires an integral value
  and `%f` a floating-point one.

  ```java
  awk.script("BEGIN { printf \"%,d\\n\", 1234567 }")
          .execute(new JavaStringFormatAwkSink(System.out));
  // prints: 1,234,567
  ```

The first two are also reachable through the `AwkSink.from(...)` factories, which accept an
`OutputStream`, a `PrintStream`, or an `Appendable`. Every constructor and factory takes an optional
`Locale`, and defaults to `Locale.US` without one.

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

## Subprocess Output

When AWK runs an external command via `system("...")` or an output pipe (`print ... | "cmd"`),
the command's **stdout** is pumped into the `PrintStream` returned by the sink's
[`getPrintStream()`](#getprintstream). The built-in sinks override that method, so subprocess stdout
is captured alongside normal output; a custom sink that does not override it discards that output.

Subprocess **stderr** defaults to the sink's `PrintStream` as well. To redirect it to
a separate stream, use `errorStream(PrintStream)` (this only affects **stderr**, not
stdout):

```java
awk.script("BEGIN { system(\"mycommand\") }")
        .errorStream(System.err)
        .execute(System.out);
```

The CLI uses `.errorStream(System.err)` so that command errors appear on the
console rather than mixing with normal output.

Subprocess **stdin** follows POSIX in CLI runs: the children of `system("...")` and of a
command input pipe (`"cmd" | getline`) inherit the standard input of the JVM, so stdin
filters and terminal-aware commands (`"stty size" | getline`) work as they do under gawk.
In embedded runs — any execution not started through the `jawk` command line — the
child's standard input is closed instead: a Java stream cannot be lent to another OS
process, and the host's real standard input is never handed to the script's children.
The child of an output pipe (`print ... | "cmd"`) always reads the pipe itself as its
standard input.

## See Also

- [Java Quickstart](java.html)
- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Advanced Runtime](java-advanced.html)
- [AwkSink Javadoc](apidocs/io/jawk/jrt/AwkSink.html)
