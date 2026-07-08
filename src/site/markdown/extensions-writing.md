keywords: extension, authoring, custom, jawkfunction, jawkassocarray
description: Write custom Jawk extensions with the modern annotation-based API.

# Writing Extensions

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Modern Jawk extensions are small Java classes that expose AWK-callable functions through annotations. In most cases, you should extend `AbstractExtension`, annotate Java methods with `@JawkFunction`, and let Jawk build the function map automatically.

> [!TIP]
> Prefer annotations and `AbstractExtension` by default. Drop to custom dispatch only when you genuinely need dynamic keyword handling or a non-standard function map.

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

Use `@JawkAssocArray` on parameters that must receive an associative array. Annotated parameters should be declared as `Map`, which keeps the extension API decoupled from the concrete `AssocArray` implementation Jawk provides at runtime. Do not use concrete map classes such as `HashMap` or `TreeMap`, because Jawk passes `AssocArray` instances:

```java
@JawkFunction("AssocSize")
public int assocSize(@JawkAssocArray Map<Object, Object> array) {
    return array.keySet().size();
}
```

That metadata lets Jawk validate array-vs-scalar usage more accurately.

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

By default, Jawk resolves every scalar argument to an assigned value before calling the extension. Type-introspection functions sometimes need to distinguish untyped variables, uninitialized values, or regexp constants from plain strings. Annotate those parameters with `@JawkRawValue` to receive the runtime object as-is. The parameter must be declared as `Object`, because the raw value can be an array, a pattern, or an internal placeholder:

```java
@JawkFunction("IsArray")
public long isArray(@JawkRawValue Object value) {
    return value instanceof Map ? 1L : 0L;
}
```

This is how the built-in gawk compatibility extension implements `typeof()` and `isarray()`.

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

Annotate an instance method with `@JawkBeforeStart` to run initialization after globals are allocated but before the script starts executing. The method must return `void` and accept `(AVM, JRT)`:

```java
@JawkBeforeStart
public void initialize(AVM avm, JRT jrt) {
    // seed global variables, allocate arrays, etc.
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

## Registering Extensions

There are two distinct registration paths:

- Java embedding: pass the extension instance directly to `new Awk(...)`
- CLI usage and `--list-ext`: register an instance with `ExtensionRegistry`

If you want the extension to show up through the registry, register it explicitly:

```java
public final class SampleExtension extends AbstractExtension {

    static {
        ExtensionRegistry.register("sample", new SampleExtension());
    }

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

Or expose it to the CLI after placing the class on the JVM classpath and registering it:

```shell-session
$ java -cp my-extension.jar -jar jawk-${project.version}-standalone.jar --list-ext
GawkExtension - io.jawk.ext.GawkExtension
GNU Awk Compatibility - io.jawk.ext.GawkExtension
io.jawk.ext.GawkExtension - io.jawk.ext.GawkExtension
io.jawk.ext.StdinExtension - io.jawk.ext.StdinExtension
sample - com.company.my.SampleExtension
SampleExtension - com.company.my.SampleExtension
stdin - io.jawk.ext.StdinExtension
Stdin Support - io.jawk.ext.StdinExtension

$ java -cp my-extension.jar -jar jawk-${project.version}-standalone.jar -l sample 'BEGIN { print Repeat(3, "ha") }'
hahaha
```

## See Also

- [Loading extensions](extensions.html)
- [Main Java API](java.html)
