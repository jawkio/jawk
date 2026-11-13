keywords: extension, authoring, custom, jawkfunction, jawkassocarray
description: Write custom Jawk extensions with the modern annotation-based API.

# Writing Extensions

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Modern Jawk extensions are small Java classes that expose AWK-callable functions through annotations. In most cases, you should extend `AbstractExtension`, annotate Java methods with `@JawkFunction`, and let Jawk build the function map automatically.

## Prefer AbstractExtension

[`AbstractExtension`](apidocs/io/jawk/ext/AbstractExtension.html) already handles common extension plumbing:

- it stores the `VariableManager`, `JRT`, and `AwkSettings`
- it scans the extension class for annotated functions
- it builds the immutable keyword-to-function map returned to the parser and runtime

That means most extensions do not need to implement `getExtensionFunctions()` themselves.

## Expose Functions with @JawkFunction

Annotate each Java method you want Jawk to expose:

```java
@JawkFunction("Repeat")
public String repeat(Number count, String value) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < count.intValue(); i++) {
        result.append(value);
    }
    return result.toString();
}
```

The annotation value is the AWK function name seen by the script.

## Mark Assoc Array Parameters with @JawkAssocArray

Use `@JawkAssocArray` on parameters that must receive an associative array. Annotated parameters must be declared as `Map`. Do not use concrete map classes such as `HashMap` or `TreeMap`, because Jawk passes its own `Map` implementation:

```java
@JawkFunction("AssocSize")
public int assocSize(@JawkAssocArray Map<Object, Object> array) {
    return array.keySet().size();
}
```

That metadata lets Jawk validate array-vs-scalar usage more accurately.

The extension receives a live view of the AWK array, not a copy:

- Reads (`get()`, `entrySet()`, `values()`, iteration...) return plain Java values: an input-derived numeric string as its `String` text, numbers as is, and a nested array as another such view. The view can be handed to libraries such as Jackson as an ordinary `Map`.
- Writes (`put()`, `remove()`, `clear()`, `Map.Entry.setValue()`...) go straight to the AWK array, so the script sees them after the call.

To receive the `AssocArray` itself, with values exactly as the runtime stores them (including the internal `StrNum` numeric strings), opt in with `@JawkAssocArray(plainValues = false)`. The parameter may then also be declared as `AssocArray`. The built-in gawk extension does this for `asort()`, `asorti()`, `patsplit()` and `typeof()`, which need AWK value types.

## Plain Scalar Values

Scalar arguments are plain Java values too: numbers and strings are passed as is, and input-derived numeric strings (fields, `split()` pieces, `-v` values, strings nested in injected lists and maps) as their `String` text, whatever the declared parameter type.

Annotate a scalar `Object` parameter with `@JawkStrNum` when the function must tell an input-derived numeric string from a string constant, for example to apply AWK truthiness (an input `"0"` is false, the constant `"0"` is true). The parameter then receives the internal `io.jawk.jrt.StrNum` value, which `JRT` methods such as `toBoolean()` understand. The argument is evaluated like any other scalar. The gawk extension uses it for `mkbool()` and the UTC flag of `mktime()` and `strftime()`.

## Declare Optional Arguments with @JawkOptional

Mark trailing parameters with `@JawkOptional` when the AWK caller may omit them; the Java method receives `null` in their place. Optional parameters must be the last declared parameters and cannot be combined with varargs. Other parameter annotations compose naturally, so an optional array argument keeps its `@JawkAssocArray` marker:

```java
@JawkFunction("SortInto")
public long sortInto(
        @JawkAssocArray Map<Object, Object> source,
        @JawkOptional @JawkAssocArray Map<Object, Object> dest) {
    // dest is null when the AWK caller passed a single argument
    ...
}
```

This is how the built-in gawk compatibility extension declares `asort(source [, dest [, how]])`.

## Receive Raw Values with @JawkRawValue

By default, Jawk resolves every scalar argument to an assigned value before calling the extension. Type-introspection functions sometimes need to distinguish untyped variables, uninitialized values, or regexp constants from plain strings. Annotate those parameters with `@JawkRawValue` to receive the runtime object as-is, including numeric strings as `StrNum`. The parameter must be declared as `Object`, because the raw value can be an array, a pattern, or an internal placeholder:

```java
@JawkFunction("IsArray")
public long isArray(@JawkRawValue Object value) {
    return value instanceof Map ? 1L : 0L;
}
```

This is how the built-in gawk compatibility extension implements `typeof()`, `isarray()` and `strtonum()`.

## Keep Regexp Literals with @JawkRegexp

An AWK regexp literal used as an ordinary expression evaluates to the boolean `$0 ~ /re/`. Functions that genuinely take a regular expression as an argument — like gawk's `gensub()` — should annotate that parameter with `@JawkRegexp`, so a literal `/re/` reaches the extension as a precompiled pattern instead:

```java
@JawkFunction("Highlight")
public String highlight(@JawkRegexp Object regexp, String text) {
    Pattern pattern = regexp instanceof Pattern ?
            (Pattern) regexp : Pattern.compile(toAwkString(regexp));
    ...
}
```

Declare the parameter as `Object` and fall back to compiling the string form, because callers may also pass dynamic strings.

## Run Setup Code with @JawkBeforeStart

Annotate an instance method with `@JawkBeforeStart` to run initialization after globals are allocated but before the script starts executing. The method runs **once per interpreter instance**, not once per execution: initialization may be heavy, and the `AVM`/`JRT` pair it receives stays the same for the lifetime of the engine. Use it to register runtime hooks and build one-time state; per-run globals are cleared between executions, so seed those from the extension functions themselves. The method must return `void` and accept `(AVM, JRT)`:

```java
@JawkBeforeStart
public void initialize(AVM avm, JRT jrt) {
    // bind to the interpreter, register hooks, build one-time state
}
```

This is also where an extension can register runtime hooks. For example, the gawk compatibility extension installs a `ForInKeyOrder` so `for (index in array)` follows `PROCINFO["sorted_in"]`:

```java
@JawkBeforeStart
public void initialize(AVM avm, JRT jrt) {
    avm.setForInKeyOrder(this::orderKeys);
}

private Collection<Object> orderKeys(Map<Object, Object> array) {
    // return array.keySet() when no ordering applies — the interpreter
    // copies the returned collection, so this path costs nothing extra
    return sortIfNeeded(array);
}
```

## Making the Extension Available

There are two distinct paths for making an extension available:

- Java embedding: pass the extension instance directly to `new Awk(...)`; the registry is not involved.
- CLI: name the extension with `-l`/`--load`. A custom extension is resolved by its **fully qualified class name**, provided the class is on the JVM class path and has a public no-argument constructor: `ExtensionRegistry` loads the class, instantiates it, and registers it — under its class name, its simple name, and its `getExtensionName()` — for the rest of the JVM.

Here is a complete sample extension:

```java
package com.company.my;

public final class SampleExtension extends AbstractExtension {

    @Override
    public String getExtensionName() {
        return "sample";
    }

    @JawkFunction("Repeat")
    public String repeat(Number count, String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count.intValue(); i++) {
            result.append(value);
        }
        return result.toString();
    }

    @JawkFunction("AssocSize")
    public int assocSize(@JawkAssocArray Map<Object, Object> array) {
        return array.keySet().size();
    }
}
```

Explicit `ExtensionRegistry.register("sample", SampleExtension::new)` calls are only useful in Java hosts that later resolve extensions by short name through the registry. In particular, a `static { }` registration block inside the extension class cannot make a CLI invocation aware of the extension: the block only runs once the class is loaded, and the CLI only loads extension classes named on `-l`.

> [!NOTE]
> Your extension will not show up in `--list-ext`, which only lists what is already registered in the JVM. Load it with `-l` and its fully qualified class name.

## When You Still Need Custom Dispatch

The annotation path is not mandatory. You may still implement `JawkExtension` directly, or override the default function-map behavior, when:

- function names are determined dynamically
- the extension wants to expose different function sets at runtime
- you need a highly customized mapping layer

That is the exception, not the default.

## Minimal End-to-End Example

Use the extension directly from Java:

```java
Awk awk = new Awk(new SampleExtension());
Object value = awk.eval("Repeat(3, \"ha\")");
// value = "hahaha"
```

Or run it from the CLI. Note that `java -jar` ignores `-cp`, `-classpath`, and the `CLASSPATH` environment variable entirely, so the extension jar and the Jawk standalone jar must both go on the class path, with the main class `io.jawk.Cli` named explicitly:

```shell-session
$ java -cp "my-extension.jar:jawk-${project.version}-standalone.jar" io.jawk.Cli -l com.company.my.SampleExtension 'BEGIN { print Repeat(3, "ha") }'
hahaha
```

On Windows, the class path separator is `;` instead of `:`.

With the `jawk` launcher installed by the [one-command installer](install.html), set `JAWK_CLASSPATH` instead — the launcher takes care of the Jawk jar, the main class, and the platform-specific separator:

```shell-session
$ JAWK_CLASSPATH=my-extension.jar jawk -l com.company.my.SampleExtension 'BEGIN { print Repeat(3, "ha") }'
hahaha
```

## See Also

- [Using Extensions](extensions.html)
- [Java Quickstart](java.html)
