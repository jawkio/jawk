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
for command-line options, including `-K <filename>` to compile a script
to a tuples file and `-L <filename>` to load previously compiled tuples.
Use `--list-ext` to display available extensions (with their human readable
names) and `-l <extension>` or `--load <extension>` to load them by extension
name, simple class name, or fully qualified class name.

Enable sandbox mode with `-S` or `--sandbox` to disable the `system()`
function, all input/output redirection, command pipelines, and loading
dynamic extensions during script execution.

When embedding Jawk, instantiate `SandboxedAwk` to apply the same sandbox
restrictions programmatically.

## Run AWK inside Java

Execute a script in just one line using the convenience methods on `Awk`:

```java
Awk awk = new Awk();
String result = awk.run("{ print toupper($0) }", "hello world");
```

Evaluate expressions the same way:

```java
Awk awk = new Awk();
Object value = awk.eval("2 + 3");
```

For repeated evaluations, compile the expression once and reuse the tuples:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
AwkTuples expression = awk.compileForEval("$2");

Object first = awk.eval(expression, "alpha,beta");
Object second = awk.eval(expression, "left,right");
```

When you need to evaluate several expressions against the same record, prepare
that record once and reuse the prepared session:

```java
AwkSettings settings = new AwkSettings();
settings.setFieldSeparator(",");

Awk awk = new Awk(settings);
Awk.PreparedEval prepared = awk.prepareEval("alpha,beta,gamma");

Object second = prepared.eval("$2");
Object summary = prepared.eval("NF \":\" $NF");
```

For the hottest path, combine both techniques: prepare the record once and pass
precompiled tuples to `PreparedEval.eval(AwkTuples)`.

Prepared sessions intentionally reuse the same mutable AVM state across calls.
That means globals, `RSTART`, `RLENGTH`, and any other AWK-visible state can
leak from one expression to the next. Use `Awk.eval(...)` when you need an
isolated evaluation instead.

`Awk.eval(...)` always creates and prepares a fresh runtime for isolated
evaluation. Use direct `AVM` access only when you explicitly want to manage
runtime reuse yourself. `Awk.prepareEval(...)` is the high-level convenience
API; `AVM.prepareForEval(...)` is the low-level expert equivalent.

When your application already has structured rows, implement
`org.metricshub.jawk.jrt.InputSource` and feed fields directly to
`Awk.eval(...)` or `Awk.invoke(...)` without serializing them back to text.

See [AWK in Java documentation](https://metricshub.org/Jawk/java.html) for more details and advanced usage.

## Writing tests with `AwkTestSupport`

Jawk ships with the `org.metricshub.jawk.AwkTestSupport` utility to make unit
tests expressive and repeatable. The helper provides fluent builders for both
`Awk` and CLI driven tests, letting you define scripts, inputs, operands,
pre-assigned variables, expected outputs, and even required operating-system
capabilities in a single, readable block. Each builder automatically creates a
temporary execution environment, resolves placeholder paths inside scripts and
input data, executes the script, and performs assertions on the captured
results. The helper also exposes assertions such as `runAndAssert()` and
`assertExpected()` so the intent of the test remains clear.

### Example: testing the Java API

```java
import static org.metricshub.jawk.AwkTestSupport.awkTest;

@Test
public void uppercasesAllInput() throws Exception {
        awkTest("uppercase conversion")
                .script("{ print toupper($0) }")
                .input("hello", "world")
                .expectedOutput("HELLO\nWORLD\n")
                .runAndAssert();
}
```

### Example: testing the CLI entry point

```java
import static org.metricshub.jawk.AwkTestSupport.cliTest;

@Test
public void reportsUsageOnMissingScript() throws Exception {
        cliTest("missing script argument")
                .args("-f")
                .expectedExitCode(2)
                .expectedOutput("usage: jawk ...\n")
                .runAndAssert();
}
```

Always lean on `AwkTestSupport` when adding new tests. Jawk spans a broad
surface area—parsing, evaluation, file-system interaction, and extension
loading—which makes correctness regressions easy to introduce. Consistently
exercising features with unit tests gives us quick feedback when refactoring,
ensures behaviour remains consistent across supported platforms, and protects
our compatibility guarantees with the wider AWK ecosystem. By funneling all
tests through the shared helper we avoid duplicated boilerplate and ensure that
every test follows the same execution semantics, leaving more time to reason
about the scenarios being validated instead of the mechanics of running them.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
