keywords: avm, sandbox, jsr223, scriptengine
description: Advanced runtime control, reusable execution, sandboxing, and JSR 223 integration in Jawk.

# Advanced Runtime

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

> [!WARNING]
> [`AVM`](apidocs/io/jawk/backend/AVM.html) is expert-only. It is mutable, sequential-only, and caller-managed.

## Reusing One Runtime Across Several Evaluations

Use one `AVM` when you want to bind one record once and evaluate several expressions against it:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkExpression secondField = awk.compileExpression("$2");
AwkExpression summary = awk.compileExpression("NF \":\" $NF");

try (AVM avm = awk.createAvm()) {
    avm.prepareForEval("alpha,beta,gamma");

    Object second = avm.eval(secondField);
    Object info = avm.eval(summary);
}
```

This keeps the same runtime alive on purpose, which means state from the first evaluation can still be visible to the second one.

## Reusing One Runtime Across Several Program Runs

The same `AVM` can also execute several full AWK programs or rerun the same compiled program several times:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print $1 }");

try (AVM avm = awk.createAvm()) {
    avm.setAwkSink(new AppendableAwkSink(new StringBuilder(), Locale.US));
    avm.execute(
            program,
            firstSource,
            Collections.<String>emptyList(),
            null);

    avm.execute(program, secondSource);
}
```

Each `execute(...)` resets the AWK execution state before the program starts again, but it still reuses the same interpreter instance and runtime infrastructure.

## Why Stateful Eval Is Powerful and Dangerous

Raw repeated eval against one runtime is intentionally stateful:

```java
Awk awk = new Awk();
AwkExpression matcher = awk.compileExpression("match($0, /alpha/)");
AwkExpression state = awk.compileExpression("RSTART \":\" RLENGTH");

try (AVM avm = awk.prepareEval("alpha beta")) {
    avm.eval(matcher);
    Object leaked = avm.eval(state);
    // leaked = "1:5"
}
```

That second evaluation can see `RSTART` and `RLENGTH` produced by the first one because both expressions run inside the same mutable runtime.

## Sandbox Mode in Java

Use `SandboxedAwk` when you want the same restrictions as the CLI `-S` mode:

```java
Awk awk = new SandboxedAwk();
AwkProgram program = awk.compile("{ print $0 }");

awk.program(program)
        .input(new ByteArrayInputStream("safe\n".getBytes(StandardCharsets.UTF_8)))
        .execute();
```

## JSR 223 ScriptEngine

Jawk also exposes a JSR 223 `ScriptEngine`:

```java
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("jawk");

String script = "{ print toupper($0) }";
Bindings bindings = engine.createBindings();
bindings.put("input", new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)));

StringWriter result = new StringWriter();
engine.getContext().setWriter(new PrintWriter(result));

engine.eval(script, bindings);
```

The `input` binding may be either:

- an `InputStream`
- a `String`

If no explicit input binding is present, the script engine falls back to `System.in`.

## Thread Safety

Jawk's classes are designed for single-threaded use within each instance. The key rules:

- **`Awk` instances are not thread-safe.** Do not call `script(...)`, `program(...)`, `eval(...)`, or `compile(...)` on the same `Awk` instance concurrently from multiple threads.
- **`AVM` is sequential-only.** A single `AVM` must not be shared across threads. It is intentionally mutable and stateful.
- **`AwkProgram` and `AwkExpression` are immutable.** Compiled artifacts can be safely shared across threads and reused by different `Awk` or `AVM` instances.
- **`AwkSettings` should not be mutated during execution.** Configure settings before creating an `Awk` instance or before calling `script(...)` or `program(...)`.
- **`AwkSink` instances should not be shared** across concurrent executions unless the implementation is explicitly thread-safe.

For concurrent AWK processing, create a separate `Awk` instance per thread:

```java
// Thread-safe: each thread gets its own Awk instance
ExecutorService pool = Executors.newFixedThreadPool(4);
AwkProgram program = new Awk().compile("{ print toupper($0) }");

for (String input : inputs) {
    pool.submit(() -> {
        Awk awk = new Awk();
        return awk.program(program).input(input).capture();
    });
}
```

The compiled `AwkProgram` is shared safely because it is immutable; each thread creates its own `Awk` to execute it.

## See Also

- [Java Quickstart](java.html)
- [Custom Output](java-output.html)
- [Structured Input and Variables](java-input.html)
- [Compile, Eval, and Reuse](java-compile.html)

