keywords: eval, compile, tuples, expression, evaluation, prepare
description: Evaluate expressions, compile programs, and reuse tuples efficiently in Jawk.

# Compile, Eval, and Reuse

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk separates compilation from execution. That gives you a simple API for one-off work and a reusable tuple API for hot paths.

| Situation | Recommended API | Why |
| --- | --- | --- |
| One expression, one call | `eval(String...)` | Smallest API surface |
| One expression, many records | `compileForEval(...)` + `eval(AwkTuples, ...)` | Pay the compile cost once |
| One full script, many runs | `compile(...)` + `invoke(...)` | Reuse the same tuple stream |
| One record, many expressions | `prepareEval(...)` | Bind the record once, then reuse it |

## Simple Expression Evaluation

`eval()` is the direct path when you want the value of an expression rather than the printed output of a full AWK program.

> [!TABS]
> - String input
>
>   ```java
>   Awk awk = new Awk();
>   Object value = awk.eval("$2", "alpha beta");
>   // value = "beta"
>   ```
>
> - Structured input
>
>   ```java
>   Awk awk = new Awk();
>   InputSource source = new TableInputSource(
>           Collections.singletonList(Arrays.asList("alpha", "beta")));
>   Object value = awk.eval("$2", source);
>   // value = "beta"
>   ```

Each high-level `Awk.eval(...)` call creates, prepares, uses, and closes a fresh runtime, so evaluations stay isolated from one another.

## Precompile an Expression

When the same expression runs many times, compile it once and reuse the tuples:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkTuples expression = awk.compileForEval("$2");

Object first = awk.eval(expression, "alpha,beta");
Object second = awk.eval(expression, "left,right");
```

This is the normal high-performance path for one expression applied to many records.

## Compile a Full Script

For full AWK programs, compile to `AwkTuples` and then invoke the tuples with the input and operands you want:

```java
Awk awk = new Awk();
AwkTuples tuples = awk.compile("{ print $1 }");

InputStream input = new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8));
awk.invoke(tuples, input, Collections.<String>emptyList());
```

This keeps compilation and execution separate, which is useful when the same AWK program is reused across multiple inputs.

## Reuse Compiled Tuples

Compiled tuples are the reusable intermediate form used by both the Java API and the CLI:

- `compile(...)` produces tuples for full AWK programs
- `compileForEval(...)` produces tuples for expressions
- `invoke(...)` executes program tuples
- `eval(...)` executes expression tuples

The CLI equivalents are `-K` for tuple generation and `-L` for later loading. See [CLI Reference](cli-reference.html) for the command-line flow.

## Choosing the Right Reuse Strategy

- Use `eval(String...)` when the expression is cheap and called only occasionally.
- Use `compileForEval(...)` plus `eval(AwkTuples, ...)` for tight loops over many records.
- Use `compile(...)` plus `invoke(...)` when a whole AWK program is reused.
- Use `prepareEval(...)` when the shape is inverted and you have one record with many expressions. That workflow is covered in [Java Advanced](java-advanced.html).
