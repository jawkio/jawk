# Jawk

![GitHub release (with filter)](https://img.shields.io/github/v/release/metricshub/jawk)
![Build](https://img.shields.io/github/actions/workflow/status/metricshub/jawk/deploy.yml)
![Reproducible](https://img.shields.io/badge/build-reproducible-green)
![GitHub top language](https://img.shields.io/github/languages/top/metricshub/jawk)
![License](https://img.shields.io/github/license/metricshub/jawk)

Jawk is a pure Java implementation of [AWK](https://en.wikipedia.org/wiki/AWK). It executes the specified AWK scripts to parse and process text input, and generate a text output. Jawk can be used as a CLI, but more importantly it can be invoked from within your Java project.

This project is forked from the excellent [Jawk project](https://jawk.sourceforge.net/) that was maintained by [hoijui on GitHub](https://github.com/hoijui/Jawk).

Jawk does not rely on any logging framework; errors are reported through standard Java exceptions.

[Project Documentation](https://metricshub.org/Jawk)

## Run Jawk CLI

See [Jawk CLI documentation](https://metricshub.org/Jawk/cli.html)

## Run AWK inside Java

Execute a script in just one line using the convenience methods on `Awk`:

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }", "hello world");
```

See [AWK in Java documentation](https://metricshub.org/Jawk/java.html) for more details and advanced usage.

## Extensions

Jawk supports Java-based extension modules. Extensions can now resolve keywords
to direct Java method references through the `resolve(String)` API, bypassing
string-based dispatch. See the [extensions documentation](https://metricshub.org/Jawk/extensions.html)
for more information.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
