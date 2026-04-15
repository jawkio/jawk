keywords: variables, argv, argc, settings, overrides
description: Pass variables, arguments, and runtime settings to Jawk from Java.

# Variables and Arguments

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk exposes three distinct concepts that are easy to blur together if you come from the CLI first:

- runtime `arguments` are CLI-style operands exposed through `ARGV` and `ARGC`
- `AwkSettings` variables are engine-level defaults
- `script(...)`, `program(...)`, and `AVM.execute(...)` accept per-call variable overrides

> [!IMPORTANT]
> `AwkSettings` is behavioral configuration, not an input carrier. Put field separators, locale, record separators, and engine-level variables there. Pass input and output directly through `script(...)`, `program(...)`, `AVM.execute(...)`, or `eval(...)`.

## Runtime Arguments, ARGC, and ARGV

The `arguments` passed to `arguments(...)` or `AVM.execute(...)` are the Java equivalent of the CLI operands that appear after the script. The below Java code:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("BEGIN { print ARGC, ARGV[1] }");

awk.script(program)
        .input(myInputSource)
        .arguments("mode=csv")
        .execute();
```

is equivalent to:

```sh
awk 'BEGIN { print ARGC, ARGV[1] }' mode=csv
```

Those operands follow AWK rules:

- an operand containing `=` is treated as a variable assignment
- an operand without `=` is treated as an input filename
- `ARGV[0]` is still reserved for the program name

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

When you pass a plain Java `Map`, Jawk exposes it directly to the script. The runtime may mutate that map, so do not pass immutable map implementations. Numeric AWK array indexes are represented as `Long` keys (e.g. `0L`, `1L`, `2L`).

## Per-Call Variable Overrides

Use the `variable(name, value)` method when the compiled program stays the same but one execution needs a different Java-supplied variable. The below Java code:

```java
Awk awk = new Awk();
AwkProgram program = awk.compile("{ print prefix $0 }");

awk.script(program)
        .input(myInputSource)
        .variable("prefix", "row=")
        .execute();
```

is equivalent to:

```sh
echo "hello" | awk -v prefix="row=" '{ print prefix $0 }'
```

To set several variables at once, pass a `Map<String, Object>` with `variables(map)`.

The same idea is available on the reusable runtime API through `AVM.execute(...)` and `AVM.prepareForEval(...)`.

## See Also

- [Java Quickstart](java.html)
- [Structured Input](java-input.html)
- [Custom Output](java-output.html)
- [Compile, Eval, and Reuse](java-compile.html)
- [Advanced Runtime](java-advanced.html)
