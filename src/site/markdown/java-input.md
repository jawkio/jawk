keywords: inputsource, variables, argv, structured input
description: Pass variables, operands, and structured records to Jawk from Java.

# Structured Input and Variables

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk exposes three distinct concepts that are easy to blur together if you come from the CLI first:

- runtime `arguments` are CLI-style operands exposed through `ARGV` and `ARGC`
- `AwkSettings` variables are engine-level defaults
- per-call `variableOverrides` are Java-side overrides on selected APIs

> [!IMPORTANT]
> `AwkSettings` is behavioral configuration, not an input carrier. Put field separators, locale, record separators, output streams, and engine-level variables there. Pass input and operands directly to `invoke()` or `eval()`.

## Runtime Arguments, ARGC, and ARGV

The `arguments` parameter on `Awk.invoke(...)` is the Java equivalent of the CLI operands that appear after the script:

```java
Awk awk = new Awk();
AwkTuples tuples = awk.compile("BEGIN { print ARGC, ARGV[1] }");

InputStream input = new ByteArrayInputStream(new byte[0]);
List<String> arguments = Arrays.asList("mode=csv");

awk.invoke(tuples, input, arguments);
```

Those operands follow AWK rules:

- an operand containing `=` is treated as a variable assignment
- an operand without `=` is treated as an input filename
- `ARGV[0]` is still reserved for the program name

When Jawk exposes AWK arrays back to a Java `Map`, numeric indexes are represented as `Long` keys such as `0L`, `1L`, and `2L`.

## Variables from AwkSettings

Use [`AwkSettings`](apidocs/org/metricshub/jawk/util/AwkSettings.html) for variables that should be present every time the engine runs:

```java
AwkSettings settings = new AwkSettings();
settings.putVariable("threshold", 10);
settings.putVariable("mode", "strict");

Awk awk = new Awk(settings);
Object result = awk.eval("threshold");
```

Values may be scalars such as `Long`, `Double`, and `String`, or associative-array-style values such as mutable `Map` instances and `AssocArray`.

When you pass a plain Java `Map`, Jawk exposes it directly to the script. The runtime may mutate that map, so do not pass immutable map implementations.

## Per-Call Variable Overrides

The tuple-based `Awk.invoke(...)` overloads accept per-call variable overrides on top of the settings-level defaults:

```java
Awk awk = new Awk();
AwkTuples tuples = awk.compile("{ print prefix $0 }");

Map<String, Object> overrides = new HashMap<String, Object>();
overrides.put("prefix", "row=");

InputStream input = new ByteArrayInputStream("alpha\n".getBytes(StandardCharsets.UTF_8));
awk.invoke(tuples, input, Collections.<String>emptyList(), overrides);
```

Use this when the compiled tuples stay the same but one invocation needs a different Java-supplied variable set.

At the high level, `Awk.eval(...)` does not expose per-call overrides. If you need that shape for expression evaluation, the lower-level `AVM` API [exposes it directly](java-advanced.html).

## Structured Input with InputSource

[`InputSource`](apidocs/org/metricshub/jawk/jrt/InputSource.html) lets you feed records directly from your own data structures without serializing them to text first. This is the preferred integration point when your application already has rows, columns, or tokenized fields in memory.

You can pass an `InputSource` to both:

- `Awk.invoke(...)` for full AWK programs
- `Awk.eval(...)` for expression evaluation

## InputSource Contract

An `InputSource` implementation controls four things:

- `nextRecord()` advances to the next record and returns `true` while input remains
- `getRecordText()` provides the current `$0`, or `null` if you only expose fields
- `getFields()` provides `$1..$NF`, or `null` if Jawk should split `$0` using `FS`
- `isFromFilenameList()` tells Jawk whether the current record should behave like file-list input for counters such as `FNR`

When both record text and fields are available, Jawk uses:

- the field list for `$1..$NF`
- the provided text for the initial `$0`

When only fields are available, Jawk synthesizes `$0` lazily if the script asks for it.

## Example: TableInputSource

The example below exposes rows as pre-split fields and leaves `$0` to be synthesized only when necessary:

```java
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.metricshub.jawk.jrt.InputSource;

public final class TableInputSource implements InputSource {
    private final List<List<String>> rows;
    private int index = -1;
    private List<String> currentFields;

    public TableInputSource(List<List<String>> rows) {
        this.rows = rows;
    }

    @Override
    public boolean nextRecord() throws IOException {
        int next = index + 1;
        if (next >= rows.size()) {
            currentFields = null;
            return false;
        }
        index = next;
        currentFields = Collections.unmodifiableList(new ArrayList<String>(rows.get(index)));
        return true;
    }

    @Override
    public String getRecordText() {
        return null;
    }

    @Override
    public List<String> getFields() {
        return currentFields;
    }

    @Override
    public boolean isFromFilenameList() {
        return false;
    }
}
```

Use it directly with Jawk:

```java
InputSource source = new TableInputSource(Arrays.asList(
        Arrays.asList("Alice", "30", "Engineering"),
        Arrays.asList("Bob", "25", "Marketing")));

Awk awk = new Awk();
Object value = awk.eval("$1 \"-\" $3", source);
```

## See Also

- [Java quickstart](java.html)
- [Eval and tuple reuse](java-compile.html)
- [Advanced runtime and `AVM`](java-advanced.html)
