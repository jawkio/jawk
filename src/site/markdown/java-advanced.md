keywords: avm, prepareeval, sandbox, jsr223, scriptengine
description: Advanced runtime control, prepared evaluation, sandboxing, and JSR 223 integration in Jawk.

# Advanced Runtime

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

> [!WARNING]
> Raw `AVM` access is expert-only. It is mutable, sequential-only, and caller-managed. If you want high performance without owning the runtime lifecycle yourself, prefer `Awk.prepareEval(...)`.

## Fast Prepared Eval with Awk.prepareEval()

`Awk.prepareEval(...)` is the recommended high-performance API when you want to evaluate several expressions against the same record:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkTuples secondField = awk.compileForEval("$2");
AwkTuples summary = awk.compileForEval("NF \":\" $NF");

try (AVM prepared = awk.prepareEval("alpha,beta,gamma")) {
    Object second = prepared.eval(secondField);
    Object info = prepared.eval(summary);
}
```

This API binds the record once, reuses the same runtime state on purpose, and still keeps the creation and cleanup logic in one high-level place.

## Raw AVM Control

Use [`AVM`](apidocs/org/metricshub/jawk/backend/AVM.html) directly only when you explicitly want to manage the runtime yourself:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk compiler = new Awk(settings);
AwkTuples expr = compiler.compileForEval("$2");

try (AVM avm = new AVM(settings, Collections.<String, JawkExtension>emptyMap())) {
    avm.prepareForEval("alpha,beta,gamma");
    Object value = avm.eval(expr);
}
```

The main reasons to do this are:

- you want direct lifecycle control
- you want the lower-level preparation overloads
- you are integrating Jawk into an existing runtime-management abstraction

> [!COLLAPSIBLE]
> Owning an `AVM` directly
>
> When you create `AVM` yourself, you are responsible for:
>
> - preparing input with `prepareForEval(...)` or calling `interpret(...)` at the right times
> - understanding when state is reset and when it is intentionally reused
> - passing extensions explicitly if the runtime should know about them
> - closing the `AVM` when you are done so that bound input and runtime I/O resources are released

## Why AVM.eval(AwkTuples) Is Dangerous

Raw `AVM.eval(AwkTuples)` executes against the runtime exactly as it currently stands. It does not reset variables, operand stacks, random state, or AWK-visible specials for you.

This state leakage is intentional and can be useful, but it is also the main footgun:

```java
Awk awk = new Awk();
AwkTuples matcher = awk.compileForEval("match($0, /alpha/)");
AwkTuples state = awk.compileForEval("RSTART \":\" RLENGTH");

try (AVM prepared = awk.prepareEval("alpha beta")) {
    prepared.eval(matcher);
    Object leaked = prepared.eval(state);
    // leaked = "1:5"
}
```

That second evaluation can see `RSTART` and `RLENGTH` produced by the first one because both expressions run inside the same mutable runtime.

## Sandbox Mode in Java

Use `SandboxedAwk` when you want the same restrictions as the CLI `-S` mode:

```java
Awk awk = new SandboxedAwk();
AwkTuples tuples = awk.compile("{ print $0 }");

InputStream input = new ByteArrayInputStream("safe\n".getBytes(StandardCharsets.UTF_8));
awk.invoke(tuples, input, Collections.<String>emptyList());
```

Scripts that rely on `system()`, redirections, or command pipelines will fail under the sandboxed compiler or runtime. That is the intended behavior.

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
