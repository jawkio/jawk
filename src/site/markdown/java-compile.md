keywords: eval, compile, runtime, reuse, expression, program
description: Compile programs and expressions once, then reuse them efficiently in Jawk.

# Compile, Eval, and Reuse

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk separates compilation from execution. That gives you a small convenience API for one-off work and reusable compiled artifacts for hot paths.

| Situation | Recommended API | Why |
| --- | --- | --- |
| One expression, one call | `eval(String...)` | Smallest API surface |
| One expression, many records | `AwkExpression` + `eval(...)` | Pay the compile cost once |
| One full script, many runs | `AwkProgram` + `script(program).execute(...)` | Reuse the compiled program |
| One record, many expressions | `AVM.prepareForEval(...)` + `eval(...)` | Bind the record once |

## Simple Expression Evaluation

`eval()` is the direct path when you want the value of an expression rather than the printed output of a full AWK program.

```java
Awk awk = new Awk();
Object value = awk.eval("$2", "alpha beta");
// value = "beta"
```

Structured input works the same way:

```java
Awk awk = new Awk();
InputSource source = new TableInputSource(
        Collections.singletonList(Arrays.asList("alpha", "beta")));
Object value = awk.eval("$2", source);
// value = "beta"
```

Each high-level `Awk.eval(...)` call creates, prepares, uses, and closes a fresh runtime, so evaluations stay isolated from one another.

## Precompile an Expression

When the same expression runs many times, compile it once and reuse it:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkExpression expression = awk.compileExpression("$2");

Object first = awk.eval(expression, "alpha,beta");
Object second = awk.eval(expression, "left,right");
```

## Compile a Full Program

For full AWK programs, compile to `AwkProgram` and then run that compiled program with the input and output you want:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print $1 }");

awk.script(program)
        .input(new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8)))
        .execute(System.out);
```

This keeps compilation and execution separate, which is useful when the same AWK program is reused across multiple inputs.

Compilation settings matter here. For example, gawk-style arrays of arrays (`a[i][j]`) are accepted by default, but you can disable that syntax before compiling:

```java
AwkSettings settings = new AwkSettings();
settings.setAllowArraysOfArrays(false);

Awk awk = new Awk(settings);
AwkProgram program = awk.compile("{ print a[1,2] }");
```

## Choosing the Right Reuse Strategy

- Use `eval(String...)` when the expression is cheap and called only occasionally.
- Use `AwkExpression` plus `eval(...)` when one expression is reused across many records.
- Use `AwkProgram` plus `script(program).execute(...)` when a whole AWK program is reused.
- Use `AVM` when you want to keep one runtime alive across several calls. See the [Advanced Runtime](java-advanced.html) guide for AVM-level reuse patterns.

## See Also

- [Java Quickstart](java.html)
- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Custom Output](java-output.html)
- [Advanced Runtime](java-advanced.html)

