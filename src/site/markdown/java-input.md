keywords: inputsource, variables, argv, structured input
description: Pass variables, operands, and structured records to Jawk from Java.

# Structured Input and Variables

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk exposes three distinct concepts that are easy to blur together if you come from the CLI first:

- runtime `arguments` are CLI-style operands exposed through `ARGV` and `ARGC`
- `AwkSettings` variables are engine-level defaults
- `run(...)` and `AVM.interpret(...)` accept per-call variable overrides

> [!IMPORTANT]
> `AwkSettings` is behavioral configuration, not an input carrier. Put field separators, locale, record separators, default output targets, and engine-level variables there. Pass input directly through `run(...)`, `AVM.interpret(...)`, or `eval(...)`.

## Runtime Arguments, ARGC, and ARGV

The `arguments` passed to `run(...)` or `AVM.interpret(...)` are the Java equivalent of the CLI operands that appear after the script:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("BEGIN { print ARGC, ARGV[1] }");

awk.run(
        program,
        myInputSource,
        Arrays.asList("mode=csv"),
        null,
        null);
```

Those operands follow AWK rules:

- an operand containing `=` is treated as a variable assignment
- an operand without `=` is treated as an input filename
- `ARGV[0]` is still reserved for the program name

When Jawk exposes AWK arrays back to a Java `Map`, numeric indexes are represented as `Long` keys such as `0L`, `1L`, and `2L`.

## Variables from AwkSettings

Use [`AwkSettings`](apidocs/io/jawk/util/AwkSettings.html) for variables that should be present every time the engine runs:

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

Use the explicit `variableOverrides` parameter when the compiled program stays the same but one execution needs a different Java-supplied variable set:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print prefix $0 }");

awk.run(
        program,
        myInputSource,
        Collections.<String>emptyList(),
        Collections.<String, Object>singletonMap("prefix", "row="),
        null);
```

The same idea is available on the reusable runtime API through `AVM.interpret(...)` and `AVM.prepareForEval(...)`.

## Structured Input with InputSource

[`InputSource`](apidocs/io/jawk/jrt/InputSource.html) lets you feed records directly from your own data structures without serializing them to text first. This is the preferred integration point when your application already has rows, columns, or tokenized fields in memory.

You can use an `InputSource` with both:

- `Awk.run(program, inputSource, arguments, variableOverrides, sink)`
- `Awk.eval(expression, source)` for one-off expression evaluation

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
import io.jawk.jrt.InputSource;

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

- [Jawk in Java](java.html)
- [Compile, eval, and reuse](java-compile.html)
- [Advanced runtime](java-advanced.html)
