keywords: avm, strict numbers, sandbox, jsr223, scriptengine
description: Advanced runtime control, reusable execution, strict numbers, sandboxing, and JSR 223 integration in Jawk.

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

When you want user-defined globals to survive into a later program run on the same runtime, use `executePersistingGlobals(...)` for the run where you want the remapping to happen:

```java
Awk awk = new Awk();
AwkProgram first = awk.compile("BEGIN { total = 5 }");
AwkProgram second = awk.compile("BEGIN { print total }");

try (AVM avm = awk.createAvm()) {
    avm.execute(first, firstSource);
    avm.executePersistingGlobals(second, secondSource);
}
```

`executePersistingGlobals(...)` first imports the user-defined globals currently materialized in that `AVM`, then remaps them onto the next program's compiled global slots. The persistent memory lives only for that `AVM` instance. Built-in runtime variables such as `NR`, `NF`, `FS`, and `RS` still reset between runs.

If you need to carry that retained global bank into another `AVM` instance, serialize `avm.snapshotPersistentMemory()` and later restore it with `restorePersistentMemory(...)` before the next `executePersistingGlobals(...)` call.

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

## Strict Numbers

AWK converts any operand to a number: `""`, `"N/A"`, a field beyond `NF` and a variable never assigned count as `0`, and `"12 W"` counts as `12`. A host that evaluates expressions on the cells of a table gets `0` or `96` for `$2 * 8` where the cell holds no number, and cannot tell that result from a real one. Enable strict numbers to make such arithmetic fail instead:

```java
AwkSettings settings = new AwkSettings();
settings.setStrictNumbers(true);
Awk awk = new Awk(settings);

try {
    awk.eval("$2 * 8", "eth0 N/A");
} catch (AwkNumberFormatException e) {
    e.getMessage(); // "N/A" is not a number
    e.getValue();   // N/A, or null for a value that was never assigned
}
```

The setting is off by default, and an `AVM` reads it when it is created. `eval()` and `execute()` throw the [`AwkNumberFormatException`](apidocs/io/jawk/jrt/AwkNumberFormatException.html) as it is (it is an `AwkRuntimeException`), and the `Awk` or the `AVM` that threw it remains usable.

An operand that is not a number makes these operations fail: `+ - * / % ^`, unary `-` and `+`, the compound assignments (`+=`, `-=`, `*=`, `/=`, `%=`, `^=`), `++`, `--`, and `int()`, `sqrt()`, `exp()`, `log()`, `sin()`, `cos()`, `atan2()` and `srand()`, also when they are called indirectly (`@f(x)`).

| Operand | With strict numbers |
| --- | --- |
| A numeric value, `nan` and `inf` included | A number, as usual |
| A text that is entirely a number once its blanks are trimmed (`"007"`, `" 7 "`, `"1e3"`, `"+inf"`), as a field, a variable or a string constant | A number, as usual |
| Any other text (`""`, `"N/A"`, `"12 W"`, `"0x1A"`) | `"12 W" is not a number` |
| A value never assigned: a variable, an array element, a field beyond `NF` | `An unassigned value is not a number` |

- The target of `++`, `--` and of a compound assignment still starts from zero when it was never assigned: `n++`, `sum += $1` and `count[$1]++` work as usual. A target that holds a text, the empty text included, fails.
- A variable passed with `AwkSettings.putVariable()` or with the per-call overrides of `AVM.eval()` behaves like a field: `"50"` and `" 50 "` are numbers, `"N/A"` and `""` are not, and a variable that is not passed is unassigned.
- A compiled `AwkProgram` or `AwkExpression` does not depend on the setting: the same compiled object runs with and without strict numbers.

Everything else behaves as without the setting:

- Comparisons, concatenation, boolean tests and string functions: `$2 > 5` on `N/A` remains a string comparison, and `$2 == "" ? 0 : $2 * 8` is how an expression accepts an empty cell.
- `printf` and `sprintf()`: `%d` of `"N/A"` remains `0`. `sprintf("%d", $2 + 0)` fails, on the addition.
- `strtonum()`, the explicit lenient conversion: `strtonum($2) * 8` is `0` for `N/A` and `96` for `12 W`.
- Positions and counts (`substr()`, `$(i)`, `exit`), and division by zero.
- The arguments of extension functions, which may [check them themselves](extensions-writing.html#refuse-arguments-that-are-not-numbers).

## Sandbox Mode in Java

Use `SandboxedAwk` when you want the same restrictions as the CLI `-S` mode:

```java
Awk awk = new SandboxedAwk();
AwkProgram program = awk.compile("{ print $0 }");

awk.script(program)
        .input(new ByteArrayInputStream("safe\n".getBytes(StandardCharsets.UTF_8)))
        .execute(System.out);
```

Sandboxed compilation rejects `@include` so an untrusted script cannot select and execute source files from the host filesystem. Pass every trusted source explicitly to the host API instead.

`ENVIRON` is empty in a `SandboxedAwk`, so that a script or an expression cannot read the environment variables of the host process. To give a sandboxed script some environment variables, `TZ` for `strftime()` and `mktime()` for example, pass your own `ENVIRON`: the script sees exactly that map.

```java
awk.script("BEGIN { print strftime(\"%H:%M\") }")
        .variable("ENVIRON", Map.of("TZ", "UTC"))
        .execute(System.out);
```

`AwkSettings.putVariable("ENVIRON", map)` does the same for every execution and for `eval()`.

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

### Engine Metadata

The factory reports what Jawk actually is, which is what a host logs or version-gates on:

```java
ScriptEngineFactory factory = engine.getFactory();

factory.getEngineName();      // "Jawk"
factory.getEngineVersion();   // e.g. "7.2.00", the version "jawk -V" prints
factory.getLanguageName();    // "awk"
factory.getLanguageVersion(); // "POSIX"
```

`getEngineVersion()`, and `getParameter(ScriptEngine.ENGINE_VERSION)` with it, resolves the
version from the jar metadata, the same way the `-V` CLI option does: Jawk's Maven descriptor
first, since it names the artifact it belongs to, then the enclosing manifest's
`Implementation-Version`. A Jawk loaded from a plain class directory (an IDE run, a test
classpath) has neither and reports `unknown`.

Embedders that do not go through JSR 223 read the same value from
[`JawkVersion.getVersion()`](apidocs/io/jawk/util/JawkVersion.html).

## Thread Safety

Jawk's classes are designed for single-threaded use within each instance. The key rules:

- **`Awk` instances are not thread-safe.** Do not call `script(...)`, `eval(...)`, or `compile(...)` on the same `Awk` instance concurrently from multiple threads.
- **`AVM` is sequential-only.** A single `AVM` must not be shared across threads. It is intentionally mutable and stateful.
- **`AwkProgram` and `AwkExpression` are immutable.** Compiled artifacts can be safely shared across threads and reused by different `Awk` or `AVM` instances.
- **`AwkSettings` should not be mutated during execution.** Configure settings before creating an `Awk` instance or before calling `script(...)`.
- **`AwkSink` instances should not be shared** across concurrent executions unless the implementation is explicitly thread-safe.
- **Extension instances can be shared** by several `Awk` instances and threads: an `AbstractExtension` resolves the runtime of the run that calls it (`getJrt()`, `getVm()`, `getSettings()`, `toAwkString()`), so each call uses the `CONVFMT`, variables and settings of its own run. State the extension keeps in its own fields is shared by all its callers, and must be thread-safe. See [Sharing an Extension Instance](extensions-writing.html#sharing-an-extension-instance).

For concurrent AWK processing, create a separate `Awk` instance per thread:

```java
// Thread-safe: each thread gets its own Awk instance
ExecutorService pool = Executors.newFixedThreadPool(4);
AwkProgram program = new Awk().compile("{ print toupper($0) }");

for (String input : inputs) {
    pool.submit(() -> {
        Awk awk = new Awk();
        return awk.script(program).input(input).execute();
    });
}
```

The compiled `AwkProgram` is shared safely because it is immutable; each thread creates its own `Awk` to execute it.

## See Also

- [Java Quickstart](java.html)
- [Custom Output](java-output.html)
- [Variables and Arguments](java-variables.html)
- [Structured Input](java-input.html)
- [Compile, Eval, and Reuse](java-compile.html)

