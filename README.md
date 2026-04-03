# Jawk

![GitHub release (with filter)](https://img.shields.io/github/v/release/jawkio/jawk)
![Build](https://img.shields.io/github/actions/workflow/status/jawkio/jawk/deploy.yml)
![Reproducible](https://img.shields.io/badge/build-reproducible-green)
![GitHub top language](https://img.shields.io/github/languages/top/jawkio/jawk)
![License](https://img.shields.io/github/license/jawkio/jawk)

Jawk is a pure Java implementation of [AWK](https://en.wikipedia.org/wiki/AWK). You can run it as a CLI, embed it directly in Java applications, compile scripts to reusable tuples, evaluate AWK expressions, feed it structured input, load extensions explicitly, and enable a sandboxed runtime when you need tighter execution constraints.

## CLI Example

```shell
echo "hello world" | java -jar jawk-${project.version}-standalone.jar '{ print $2 ", " $1 "!" }'
```

## Java Example

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }").input("hello world").capture();
```

When writing custom extensions, annotate associative array parameters with `@JawkAssocArray` and declare them as `Map` values rather than concrete map implementations.

## Documentation

- Overview: https://jawk.io/index.html
- CLI: https://jawk.io/cli.html
- Java: https://jawk.io/java.html
- Extensions: https://jawk.io/extensions.html
- Writing Extensions: https://jawk.io/extensions-writing.html

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
