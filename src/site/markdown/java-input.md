keywords: inputsource, structured input, records, fields
description: Feed structured records and fields to Jawk from Java using InputSource.

# Structured Input

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Overview

[`InputSource`](apidocs/io/jawk/jrt/InputSource.html) lets you feed records directly from your own data structures without serializing them to text first. This is the preferred integration point when your application already has rows, columns, or tokenized fields in memory.

You can use an `InputSource` with both:

- `Awk.script(compiled).input(inputSource).execute()`
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

- [Java Quickstart](java.html)
- [Variables and Arguments](java-variables.html)
- [Custom Output](java-output.html)
- [Compile, Eval, and Reuse](java-compile.html)
- [Advanced Runtime](java-advanced.html)
