# AWK in Java

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Getting Started

Add Jawk in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
<dependencies>
  <!-- [...] -->
  <dependency>
    <groupId>org.metricshub</groupId>
    <artifactId>jawk</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>
```

Jawk artifacts are published on Maven Central, so the dependency can be resolved automatically by most build tools.

## Examples

### Evaluate expressions with `Awk.eval()`

```java
Awk awk = new Awk();
Object value = awk.eval("2 + 3");
```

`Awk.eval()` is the simplest entry point when you want the value of an
expression rather than the printed output of a full AWK script. The same
instance can also evaluate against a text record or a structured
`InputSource`:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(":");

Awk awk = new Awk(settings);
Object user = awk.eval("$1", "alice:admin");
Object role = awk.eval("$2", "alice:admin");
```

When you already know you will evaluate the same expression many times,
compile it once and reuse the resulting tuples:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkTuples expression = awk.compileForEval("$2");

for (String line : Arrays.asList("alpha,beta", "left,right", "one,two")) {
    Object value = awk.eval(expression, line);
    System.out.println(value);
}
```

`Awk.eval(...)` automatically chooses the lightweight read-only execution path
when the compiled tuples do not mutate AWK-visible state. No extra API call is
required for this optimization.

### Quick execution with `Awk.run()`

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }", "hello world");
```

### Enforce sandbox restrictions

```java
Awk awk = new SandboxedAwk();
awk.run("{ print \"safe\" }", "input");
```

`SandboxedAwk` uses sandbox-specific tuples and runtime components so
programmatic invocations honor the same restrictions as the CLI `-S`
option.

### Compile and invoke scripts

```java
Awk awk = new Awk();
awk.getSettings().setDefaultRS("\n");
awk.getSettings().setDefaultORS("\n");
ByteArrayOutputStream out = new ByteArrayOutputStream();
awk.getSettings().setOutputStream(new PrintStream(out, false, StandardCharsets.UTF_8.name()));

String script = "{ print $0 }";
AwkTuples tuples = awk.compile(script);
InputStream input = new ByteArrayInputStream("foo\nbar\n".getBytes(StandardCharsets.UTF_8));

awk.invoke(tuples, input, Collections.emptyList());

System.out.println(out.toString(StandardCharsets.UTF_8.name()));
```

### Provide structured input with `InputSource`

Jawk exposes the `org.metricshub.jawk.jrt.InputSource` interface so embedding
applications can push records directly to the runtime without serializing data
to text and reparsing it.

Important: Jawk intentionally ships only the `InputSource` interface - it does
not provide a built-in `ListInputSource` or similar convenience class.
Internally Jawk uses `StreamInputSource` to handle stdin / file-list input, but
that class is an implementation detail and should not be used by embedding code.
Implement `InputSource` directly for your own data structures.

Pass `InputSource` instances directly to the `invoke()` or `eval()` methods.
`AwkSettings` is a purely behavioral configuration (field separator, record
separator, output stream, etc.) and does not carry input or runtime arguments.

#### Contract summary

* `nextRecord()` advances to the next record.
* `getRecordText()` returns `$0` for the current record, or `null` when the
  source does not expose record text.
* `getFields()` returns pre-split fields (`$1`, `$2`, ...), or `null` to let
  Jawk split `$0` using `FS`.
* `isFromFilenameList()` controls whether `FNR` should be incremented like
  file-based input.

Each record may be exposed as:

* text only
* fields only
* text and fields

When both are available, Jawk uses the field list for `$1..$NF` and the
provided record text for the initial `$0` value. When only fields are
available, `$0` is synthesized lazily and only if the script asks for it.

#### Example implementation (`List<List<String>>` table)

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.metricshub.jawk.jrt.InputSource;

public final class TableInputSource implements InputSource {
    private final List<List<String>> rows;
    private int index = -1;
    private List<String> fields;

    public TableInputSource(List<List<String>> rows) {
        this.rows = rows;
    }

    @Override
    public boolean nextRecord() throws IOException {
        int next = index + 1;
        if (next >= rows.size()) {
            fields = null;
            return false;
        }
        index = next;
        fields = Collections.unmodifiableList(new ArrayList<>(rows.get(index)));
        return true;
    }

    @Override
    public String getRecordText() {
        return null;
    }

    @Override
    public List<String> getFields() {
        return fields;
    }

    @Override
    public boolean isFromFilenameList() {
        return false;
    }
}
```

This fields-only variant is ideal when your host application already stores
records in structured form and wants to avoid repeatedly joining and splitting
the same values.

#### Using a custom `InputSource`

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

InputSource source = new TableInputSource(Arrays.asList(
        Arrays.asList("Alice", "30", "Engineering"),
        Arrays.asList("Bob", "25", "Marketing")
));

Awk awk = new Awk();
AwkTuples tuples = awk.compile("{ print $1, $3 }");
awk.invoke(tuples, source, Collections.emptyList(), null);
// Alice Engineering
// Bob Marketing
```

For a one-liner, use the convenience overload that compiles and invokes
in one step:

```java
Awk awk = new Awk();
ScriptSource script = new ScriptSource(
        ScriptSource.DESCRIPTION_COMMAND_LINE_SCRIPT,
        new StringReader("{ print $1, $3 }"));
awk.invoke(script, source);
```

#### Evaluating expressions with `InputSource`

`Awk.eval()` accepts an `InputSource` so that structured records can
feed field references like `$1`, `$2`, etc. without going through text
serialization:

```java
InputSource source = new TableInputSource(
        Collections.singletonList(Arrays.asList("Alice", "30", "Engineering")));
Object result = awk.eval("$1 \"-\" $3", source);
// result: "Alice-Engineering"
```

The same approach works with precompiled tuples:

```java
Awk awk = new Awk();
AwkTuples expression = awk.compileForEval("$1 \"-\" $3");

for (List<String> row : rows) {
    Object result = awk.eval(expression, new TableInputSource(Collections.singletonList(row)));
    System.out.println(result);
}
```

To supply custom extensions, create the `Awk` instance with the extension
instances. Built-in extensions expose convenient singletons such as
`CoreExtension.INSTANCE` and `StdinExtension.INSTANCE`:

```java
Awk awk = new Awk(CoreExtension.INSTANCE, new MyExtension());
```

Use `Awk.listAvailableExtensions()` to inspect the extensions discoverable on
the current class path:

```java
Awk.listAvailableExtensions().forEach((name, extension) ->
        System.out.println(name + " - " + extension.getClass().getName()));
```

### Precompile expressions

```java
Awk awk = new Awk();
Object value = awk.eval("$1 - $2", "5 3");
```

You can also precompile the expression with `compileForEval()` and evaluate
it against a structured `InputSource` via `eval()`:

```java
Awk awk = new Awk();
AwkTuples expr = awk.compileForEval("$1 - $2");
InputSource source = new TableInputSource(
        Collections.singletonList(Arrays.asList("5", "3")));
Object value = awk.eval(expr, source);
```

This is the recommended API when you have:

* one expression
* many records
* a tight loop where compile cost must be paid only once

### Advanced runtime control with `AVM`

Most embedders should stay on `Awk`, which compiles tuples, creates a fresh
runtime per invocation, and automatically selects the read-only eval path when
possible.

If you intentionally want to reuse the same interpreter instance, you can work
with `AVM` directly:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk compiler = new Awk(settings);
AwkTuples expr = compiler.compileForEval("NF \":\" $2");

AVM avm = new AVM(settings, Collections.emptyMap());

for (List<String> row : Arrays.asList(
        Arrays.asList("a", "b", "c"),
        Arrays.asList("left", "right"))) {
    InputSource source = new TableInputSource(Collections.singletonList(row));
    Object value = avm.eval(expr, source);
    System.out.println(value);
}
```

Use this API only when you explicitly want to control the runtime lifecycle.
The reused `AVM` API is sequential, not concurrent: do not call the same AVM
instance from multiple threads at the same time.

`AVM.eval(...)` uses the same tuple metadata as `Awk.eval(...)` and will
automatically choose the read-only eval path when the expression is eligible.

### Advanced examples

The examples below show how to configure `AwkSettings` for behavioral options
(output stream, record separator, etc.) while passing input and arguments
directly to the `invoke()` methods.

#### Invoke AWK script files on input files

```java
/**
 * Executes the specified AWK script
 * <p>
 * @param scriptFile File containing the AWK script to execute
 * @param inputFileList List of files that contain the input to be parsed by the AWK script
 * @return the printed output of the script as a String
 * @throws ExitException when the AWK script forces its exit with a specified code
 * @throws IOException on I/O problems
 */
private String runAwk(File scriptFile, List<String> inputFileList) throws IOException, ExitException {

    // Create the OutputStream, to collect the result as a String
    ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();

    Awk awk = new Awk();
    awk.getSettings().setOutputStream(new PrintStream(resultBytesStream));

    // Compile the script
    ScriptSource scriptSource = new ScriptFileSource(scriptFile.getAbsolutePath());
    AwkTuples tuples = awk.compile(Collections.singletonList(scriptSource));

    // Execute the awk script against the specified input files
    // Input files are passed as arguments (ARGV), not through AwkSettings
    awk.invoke(tuples, System.in, inputFileList);

    // Return the result as a string
    return resultBytesStream.toString(StandardCharsets.UTF_8);

}
```

#### Execute AWK script (as String) on String input

```java
/**
 * Executes the specified script against the specified input
 * <p>
 * @param script AWK script to execute (as a String)
 * @param input Text to process (as a String)
 * @return result as a String
 * @throws ExitException when the AWK script forces its exit with a specified code
 * @throws IOException on I/O problems
 */
private String runAwk(String script, String input) throws IOException, ExitException {

    Awk awk = new Awk();

    // We force \n as the Record Separator (RS) because even if running on Windows
    // we're passing Java strings, where end of lines are simple \n
    awk.getSettings().setDefaultRS("\n");

    // Create the OutputStream, to collect the result as a String
    ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    awk.getSettings().setOutputStream(new PrintStream(resultBytesStream));

    // Compile the script
    AwkTuples tuples = awk.compile(script);

    // Execute the awk script against the specified input
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    awk.invoke(tuples, inputStream, Collections.emptyList());

    // Return the result as a string
    return resultBytesStream.toString(StandardCharsets.UTF_8);

}
```

## Javadoc

* [AwkSettings](apidocs/org/metricshub/jawk/util/AwkSettings.html)
* [Awk](apidocs/org/metricshub/jawk/Awk.html)

## Java Scripting API (JSR 223)

**Jawk** can be invoked via the standard Java scripting framework introduced in JSR&nbsp;223.
The following example loads Jawk through the `ScriptEngineManager` and evaluates
an AWK script from a Java `String`:

```java
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("jawk");

String script = "{ print toupper($0) }";
String input = "hello world";

Bindings bindings = engine.createBindings();
bindings.put("input", new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

StringWriter result = new StringWriter();
engine.getContext().setWriter(new PrintWriter(result));

engine.eval(script, bindings);

System.out.println(result.toString());
```

## Limitations and Differences

When embedding Jawk into an application remember that the interpreter follows
the AWK language closely but not everything from other implementations is
available.  The most notable differences are:

* Regular expressions use Java's implementation and therefore have slightly
  different semantics compared to traditional AWK.
* `printf`/`sprintf` formatting relies on `java.util.Formatter`.  Unexpected
  argument types will raise an exception; Jawk does not provide helper
  keywords for typecasting.
* Extensions must be explicitly enabled.  Only the core extensions bundled with
  Jawk are available by default.

For a more complete list see the [project overview](index.html#features).
