keywords: extension, authoring, custom, jawkfunction, jawkassocarray
description: Write custom Jawk extensions with the modern annotation-based API.

# Writing Extensions

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Modern Jawk extensions are small Java classes that expose AWK-callable functions through annotations. In most cases, you should extend `AbstractExtension`, annotate Java methods with `@JawkFunction`, and let Jawk build the function map automatically.

## Prefer AbstractExtension

[`AbstractExtension`](apidocs/io/jawk/ext/AbstractExtension.html) already handles common extension plumbing:

- `getJrt()`, `getVm()`, `getSettings()`, and `toAwkString()` give the runtime, variables, and settings of the run that calls the extension
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

- Reads (`get()`, `entrySet()`, `values()`, iteration...) return plain Java values: an input-derived numeric string as its `String` text, a blank or untyped element as `""`, numbers as is, and a nested array as another such view. The view can be handed to libraries such as Jackson as an ordinary `Map`.
- Reads follow `Map` rules, not AWK rules: `get()` returns `null` for a missing key instead of creating the element, and `containsKey("1")` finds the element `1`, like `"1" in array`.
- The same AWK array always reaches the call as the same view object, so `source == dest` still detects an array passed twice.
- Writes (`put()`, `remove()`, `clear()`, `Map.Entry.setValue()`...) go straight to the AWK array, so the script sees them after the call. A `Boolean` is stored as the number 1 or 0, as gawk's `mkbool()` returns (`typeof()` reports `number|bool`).
- Like an AWK array, a view has no string value: `toString()`, `toAwkString()`, `JRT.toDouble()` or `String.valueOf()` on a view (for example on a nested array read with `get()`) fail with "Attempting to use an array in a scalar context". Check `value instanceof Map` before converting an element that may be a subarray.

To receive the `AssocArray` itself instead, add `@JawkRawValue` (see below).

## Plain Scalar Values

Scalar arguments are plain Java values too: numbers and strings are passed as is, input-derived numeric strings (fields, `split()` pieces, `-v` values, strings nested in injected lists and maps) as their `String` text, and an unassigned variable as `""`, whatever the declared parameter type.

An array passed to a plain scalar parameter (`Object`, `String`, `Object...` without `@JawkRawValue` or `@JawkIntrospect`) fails with "Attempting to use an array in a scalar context", as when the script uses an array as a scalar, and the extension method is not called. Arrays reach `@JawkAssocArray` parameters, and reach `@JawkRawValue` and `@JawkIntrospect` scalars as is, so that functions such as `isarray()` can inspect them.

Use `@JawkRawValue` to receive Jawk's own representation instead.

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

Annotate a parameter with `@JawkRawValue` to receive Jawk's raw runtime values instead of plain Java values. The argument is evaluated like any other argument:

- A scalar parameter, which must be declared as `Object`, receives input-derived numeric strings as `io.jawk.jrt.StrNum` instead of `String`. Use it when the function must tell an input-derived numeric string from a string constant, for example to apply AWK truthiness with `JRT.toBoolean()`: an input `"0"` is false, the constant `"0"` is true.
- An `@JawkAssocArray` parameter receives the `AssocArray` itself, with values exactly as the runtime stores them (including `StrNum` numeric strings and nested `AssocArray` subarrays). The parameter may then also be declared as `AssocArray`.

```java
@JawkFunction("IsTrue")
public long isTrue(@JawkRawValue Object value) {
    return getJrt().toBoolean(value) ? 1L : 0L;
}

@JawkFunction("CountStrNums")
public long countStrNums(@JawkAssocArray @JawkRawValue AssocArray array) {
    return array.values().stream().filter(StrNum.class::isInstance).count();
}
```

The built-in gawk compatibility extension uses it for `asort()`, `asorti()`, `patsplit()`, the metadata array of `typeof()`, `mkbool()`, `strtonum()`, and the UTC flag of `mktime()` and `strftime()`.

## Inspect Values with @JawkIntrospect

By default, Jawk resolves every scalar argument to an assigned value before calling the extension: an untyped variable passed as an argument becomes an unassigned scalar. Type-introspection functions need to see the variable as it is: untyped, uninitialized, an array, or a regexp constant. Annotate those parameters with `@JawkIntrospect` to receive the runtime object as-is, without changing the variable. The parameter must be declared as `Object`, because the value can be an array, a pattern, or an internal placeholder. Like `@JawkRawValue`, it passes numeric strings as `StrNum`:

```java
@JawkFunction("IsArray")
public long isArray(@JawkIntrospect Object value) {
    return value instanceof Map ? 1L : 0L;
}
```

This is how the built-in gawk compatibility extension implements `typeof()` and `isarray()`.

## Return Booleans

An extension function may return a `Boolean` (or `boolean`): the script receives the number 1 or 0, as gawk's `mkbool()` returns, so `if (f())` works and `typeof(f())` reports `number|bool`. A `Boolean` inside a returned `Map`, or in a `Map` or `List` copied with `AssocArray.normalizeValue()`, is converted the same way.

## Return Arrays

An extension function may return a `Map`: Jawk copies it into a new AWK array, as it does for a `Map` injected as a variable, so its keys follow AWK rules. As in AWK, the result cannot be assigned to a variable, but it can be passed to a function that takes an array. To fill an array of the script instead, take it as an `@JawkAssocArray` parameter and write to it, as `split()` does.

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

## Sharing an Extension Instance

An `AbstractExtension` does not store a runtime: `getJrt()`, `getVm()`, `getSettings()`, and `toAwkString()` return those of the run executing on the calling thread. One instance can therefore be registered in several `Awk` engines and called by concurrent runs on different threads: each call converts values with the `CONVFMT` of its own run and reads its own variables. When an extension function starts another run itself, the calling run is current again when that run returns.

Keep the following in mind:

- These methods are available while a run calls the extension (in an extension function, a `@JawkBeforeStart` method, or a hook such as `ForInKeyOrder`). Elsewhere, for example in a thread started by the extension, they throw `IllegalStateException`: read what you need before handing work to another thread.
- Fields of the extension are shared by every run that uses the instance: keep them immutable or thread-safe.
- `@JawkBeforeStart` methods run once per interpreter, so a shared instance runs them once for each engine that executes it.

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
