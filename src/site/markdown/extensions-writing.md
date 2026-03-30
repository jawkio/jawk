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
SampleExtension - com.company.my.SampleExtension
sample - com.company.my.SampleExtension
io.jawk.ext.StdinExtension - io.jawk.ext.StdinExtension
stdin - io.jawk.ext.StdinExtension
Stdin Support - io.jawk.ext.StdinExtension

$ java -cp my-extension.jar -jar jawk-${project.version}-standalone.jar -l sample 'BEGIN { print Repeat(3, "ha") }'
hahaha
```

## See Also

- [Loading extensions](extensions.html)
- [Main Java API](java.html)
