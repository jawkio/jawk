keywords: install, maven, standalone, dependency, cli
description: Install Jawk from Maven Central or run the standalone jar.

# Installation

Jawk can be added as a normal Maven dependency or used as a standalone jar for CLI execution.

## Maven Dependency

Add Jawk to your project:

```xml
<dependency>
  <groupId>jawk.io</groupId>
  <artifactId>jawk</artifactId>
  <version>${project.version}</version>
</dependency>
```

Jawk artifacts are published on Maven Central, so standard Maven and Gradle builds can resolve them automatically.

## Standalone Jar

Download [jawk-${project.version}-standalone.jar](https://github.com/jawkio/jawk/releases/download/v${project.version}/jawk-${project.version}-standalone.jar) from the [latest release](https://github.com/jawkio/jawk/releases), then run it with Java:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -?
```

## Java Prerequisite

Jawk targets Java 8 and later.

## Where to Go Next

- [Command-line usage](cli.html)
- [Java embedding](java.html)
- [Extensions](extensions.html)
