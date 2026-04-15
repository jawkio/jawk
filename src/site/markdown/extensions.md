keywords: extensions, plugins, functions, loading
description: Load and use Jawk extensions from the CLI or the Java API.

# Using Extensions

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

Jawk extensions let Java code expose additional AWK-callable functions to a script. Extensions are never enabled automatically. The host application or CLI invocation decides exactly which extension instances are available.

> [!IMPORTANT]
> Extensions are opt-in. Sandbox mode blocks dynamic extension loading during script execution, but preloading an extension through the CLI or the Java host remains an explicit host decision.

## How Extensions Are Enabled

There are two supported loading paths:

- CLI: `-l <extension>` or `--load <extension>`
- Java API: pass extension instances to an `Awk` constructor

If you do neither, no extension functions are available.

## List Available Extensions

From the CLI, print the currently registered extension identifiers:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar --list-ext
```

From Java, inspect the registry directly:

```java
Awk.listAvailableExtensions().forEach((name, extension) ->
        System.out.println(name + " -> " + extension.getClass().getName()));
```

The registry may expose multiple identifiers for the same implementation, for example a registered name, a simple class name, and a fully qualified class name.

## Load Extensions from the CLI

Load an extension with any supported identifier:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l stdin -f script.awk
```

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -l io.jawk.ext.StdinExtension -f script.awk
```

If the extension class is not already registered, the CLI can still resolve it by fully qualified class name as long as the class is available on the JVM classpath.

## Load Extensions from Java

Pass extension instances directly to `Awk`:

```java
Awk awk = new Awk(StdinExtension.INSTANCE, new MyExtension());
```

That keeps extension availability explicit and local to the embedding code.

## Built-In Extensions

The current built-in registry includes the stdin extension, which is exposed through identifiers such as:

- `stdin`
- `StdinExtension`
- `io.jawk.ext.StdinExtension`

That extension provides advanced helper functions including `StdinHasInput()`, `StdinGetline()`, and `StdinBlock()`.

## Sandbox Interaction

Sandboxing and extensions are separate concerns:

- sandboxing restricts dangerous AWK runtime features
- extension loading is still an explicit host choice
- scripts do not get to expand their own capabilities automatically

If you need a sandboxed Java embedding, construct `SandboxedAwk` with the extension instances you want to allow. If you need a sandboxed CLI run, combine `-S` with the `-l` options you want to preload.

## See Also

- [Writing Extensions](extensions-writing.html)
- [Java Quickstart](java.html)
- [CLI Basics](cli.html)
