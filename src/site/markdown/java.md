keywords: java, api, embed, sdk
description: Quickstart guide for using Jawk from Java applications.

# Jawk in Java

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

For almost all embedding scenarios, the main entry point is [`Awk`](apidocs/io/jawk/Awk.html). It gives you the safe, high-level APIs for running full AWK programs, evaluating expressions, compiling tuples for reuse, and preparing records for repeated evaluation without forcing you to manage the runtime directly.

> [!WARNING]
> Treat `run()` as the shortest path, not the most configurable one. It is a convenience API for default behavior. If you need custom settings, sandboxing, explicit operands, or advanced runtime reuse, use `invoke()`, `eval()`, `prepareEval()`, or compiled tuples instead.

## The Main Entry Point: Awk

Create an `Awk` instance directly for normal use:

```java
Awk awk = new Awk();
```

Construct it with `AwkSettings` when you need behavioral configuration such as field separators, locale, or output capture:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
```

Construct it with extension instances when you want those functions available to the script:

```java
Awk awk = new Awk(StdinExtension.INSTANCE, new MyExtension());
```

When you write custom extensions, annotate associative array parameters with `@JawkAssocArray` and declare them as `Map` values rather than concrete map implementations. The dedicated [Writing Extensions](extensions-writing.html) guide covers that contract in more detail.

## The Shortest Path: run()

`run()` is the most concise way to execute a full AWK program and collect its printed output as a Java `String`:

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }", "hello world");
// result = "HELLO WORLD\n"
```

Use this when all of the following are true:

- you want default behavior
- you have a script and input already in memory
- you want the printed output as a `String`

If you outgrow any of those assumptions, switch to `invoke()` or `eval()`.

## Full Script Execution: invoke()

Use `invoke()` when you need full control over input, operands, or compiled tuples:

```java
Awk awk = new Awk();
AwkTuples tuples = awk.compile("{ print $1 }");
InputStream input = new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8));

awk.invoke(tuples, input, Collections.<String>emptyList());
```

This is the main API for:

- full AWK programs instead of single expressions
- input streams or structured `InputSource` implementations
- `ARGV` / `ARGC` operands
- per-call variable overrides on the tuple-based overloads

## Capturing Output with AwkSettings

`AwkSettings` controls runtime behavior such as field separators, locale, record separators, and the output stream.

```java
AwkSettings settings = new AwkSettings();
settings.setDefaultRS("\n");
settings.setDefaultORS("\n");

ByteArrayOutputStream out = new ByteArrayOutputStream();
settings.setOutputStream(new PrintStream(out, false, StandardCharsets.UTF_8.name()));

Awk awk = new Awk(settings);
AwkTuples tuples = awk.compile("{ print $0 }");
InputStream input = new ByteArrayInputStream("foo\nbar\n".getBytes(StandardCharsets.UTF_8));

awk.invoke(tuples, input, Collections.<String>emptyList());

String result = out.toString(StandardCharsets.UTF_8.name());
```

Input itself is not stored in `AwkSettings`. Pass input directly to `invoke()` or `eval()`.

## Which API Should I Use?

> [!ACCORDION]
> - `run()`
>
>   Use this for zero-configuration convenience only. It is best for quick string-in, string-out execution with a plain `Awk` instance.
>
> - `invoke()`
>
>   Use this for full AWK programs when you need explicit input, `ARGV` operands, structured `InputSource`, output capture, or tuple reuse.
>
> - `eval()`
>
>   Use this when you want the value of an AWK expression rather than the printed output of a whole program.
>
> - `compile()` and `compileForEval()`
>
>   Use these when the same program or expression will be reused and you want to pay the compile cost once.

## Next Steps

- [Variables, `ARGV`, and structured input](java-input.html)
- [Expression evaluation and tuple reuse](java-compile.html)
- [Prepared eval, raw `AVM`, sandboxing, and JSR 223](java-advanced.html)
