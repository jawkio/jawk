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

awk.run(program)
        .input(new ByteArrayInputStream("safe\n".getBytes(StandardCharsets.UTF_8)))
        .execute();
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

