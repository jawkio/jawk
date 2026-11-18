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

### Skip the Evaluation of Trivial Expressions

A host that evaluates many small expressions on every record can ask a compiled expression, once, whether it needs the runtime at all:

```java
AwkExpression constant = awk.compileExpression("\"enclosure\"");
constant.isConstant();       // true
constant.getConstantValue(); // "enclosure", as eval() returns it

AwkExpression field = awk.compileExpression("$3");
field.getFieldIndex();       // 3: the host can read the third field itself
```

| Method | Result |
| --- | --- |
| `isConstant()` | `true` when the value depends neither on the input nor on the variables: a string or number literal, or what constant folding reduces to one (`2 * 3`, `"a" "b"`) |
| `getConstantValue()` | The value `eval()` returns for a constant expression (`String`, `Long` or `Double`). Throws `IllegalStateException` when the expression is not constant |
| `getFieldIndex()` | `n` when the expression is exactly `$n` with a constant `n` (`0` for `$0`), `-1` otherwise |

- The constant value is the evaluated one, not the source text: `0.10` is `0.1`, `010` is `10`, and `1e3` is `1000`.
- An expression that reads a field or a variable, or calls a function, is never constant: `NF`, `length`, `x`, `toupper("a")`.
- `$NF`, `$(i)` and `$1 $2` have no field index.
- The answers come from the optimized code. When the optimizer does not reduce an expression to one value or one field (or when the expression is compiled with optimization disabled), it is reported as neither, and evaluating it remains correct.

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

Compilation settings matter here. For example, gawk-style arrays of arrays (`a[i][j]`) are accepted by default, but you can disable that compile-time mode before compiling. When disabled, Jawk also rejects subarray operands in array-only positions such as `split(..., a[i])` or `for (k in a[i])`:

```java
AwkSettings settings = new AwkSettings();
settings.setPosix(true);

Awk awk = new Awk(settings);
AwkProgram program = awk.compile("{ print a[1,2] }");
```

To keep one runtime alive across several of these calls instead of one artifact, see the
[Advanced Runtime](java-advanced.html) guide.

## See Also

- [Java Quickstart](java.html)
- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Custom Output](java-output.html)
- [Advanced Runtime](java-advanced.html)

