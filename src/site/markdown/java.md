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
String script = "{ print $0 }";
AwkTuples tuples = awk.compile(script);

AwkSettings settings = new AwkSettings();
settings.setInput(new ByteArrayInputStream("foo\nbar\n".getBytes(StandardCharsets.UTF_8)));
settings.setDefaultRS("\n");
settings.setDefaultORS("\n");
ByteArrayOutputStream out = new ByteArrayOutputStream();
settings.setOutputStream(new PrintStream(out, false, StandardCharsets.UTF_8.name()));

awk.invoke(tuples, settings);

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

When `AwkSettings\#setInputSource(...)` is set, that source takes precedence
over `AwkSettings\#setInput(...)`.

#### Contract summary

* `nextRecord()` advances to the next record.
* `getRecord()` returns `$0` for the current record.
* `getFields()` returns pre-split fields (`$1`, `$2`, ...), or `null` to let
  Jawk split `$0` using `FS`.
* `isFromFilenameList()` controls whether `FNR` should be incremented like
  file-based input.

#### Example implementation (`List<List<String>>` table)

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.metricshub.jawk.jrt.InputSource;

public final class TableInputSource implements InputSource {
    private final List<List<String>> rows;
    private final String separator;
    private int index = -1;
    private List<String> fields;
    private String record;

    public TableInputSource(List<List<String>> rows) {
        this(rows, " ");
    }

    public TableInputSource(List<List<String>> rows, String separator) {
        this.rows = rows;
        this.separator = separator;
    }

    @Override
    public boolean nextRecord() throws IOException {
        int next = index + 1;
        if (next >= rows.size()) {
            fields = null;
            record = null;
            return false;
        }
        index = next;
        fields = Collections.unmodifiableList(new ArrayList<>(rows.get(index)));
        record = String.join(separator, fields);
        return true;
    }

    @Override
    public String getRecord() {
        return record;
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

#### Using a custom `InputSource`

```java
import java.io.IOException;
import java.util.Arrays;

Awk awk = new Awk();
AwkSettings settings = new AwkSettings();
settings.setInputSource(new TableInputSource(Arrays.asList(
        Arrays.asList("Alice", "30", "Engineering"),
        Arrays.asList("Bob", "25", "Marketing")
)));

awk.invoke("{ print $1, $3 }", settings);
// Alice Engineering
// Bob Marketing
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
AwkTuples expr = awk.compileForEval("$1 - $2");
Object value = awk.eval(expr, "5 3", " ");
```

### Advanced examples

The examples below show how to configure `AwkSettings` directly to customize
input sources, output handling or to register `JawkExtension`s. The
`invoke(ScriptSource, AwkSettings)` helper compiles and runs the script in one step.

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

    AwkSettings settings = new AwkSettings();

    // Set the input files
    for (String name : inputFileList) {
        settings.addNameValueOrFileName(name);
    }

    // Create the OutputStream, to collect the result as a String
    ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    settings.setOutputStream(new PrintStream(resultBytesStream));

    // Execute the awk script against the specified input
    Awk awk = new Awk();
    awk.invoke(new ScriptFileSource(scriptFile.getAbsolutePath()), settings);

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
    AwkTuples tuples = awk.compile(new StringReader(script));

    AwkSettings settings = new AwkSettings();

    // Set the input files
    settings.setInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

    // We force \n as the Record Separator (RS) because even if running on Windows
    // we're passing Java strings, where end of lines are simple \n
    settings.setDefaultRS("\n");

    // Create the OutputStream, to collect the result as a String
    ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
    settings.setOutputStream(new PrintStream(resultBytesStream));

    // Execute the awk script against the specified input
    awk.invoke(tuples, settings);

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
