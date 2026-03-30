# Contributing

## How to build

Requirements:

* [Java 11](https://adoptium.net/)
* [Maven 3.x](https://maven.apache.org/)

As usual with Maven, you compile, build and test with:

```bash
mvn verify
```

2 artifacts are built:

* `jawk-<VERSION>.jar`, to be used in downstream projects requiring Jawk
* `jawk-<VERSION>-standalone.jar`, an executable JAR that behaves like `awk` or `gawk`

The [documentation](https://jawk.io) is generated with Maven too:

```bash
mvn site
```

For more information about Maven-generated documentation, visit [Maven Site plugin](https://maven.apache.org/plugins/maven-site-plugin/) and [Sentry Maven Skin](https://sentrysoftware.github.io/sentry-maven-skin/).

## Development workflows

Please follow this workflow to contribute to this project:

* Create an issue describing either the problem you're trying to fix, or the feature you would like to add.
* Wait for a feedback from the team, to validate your suggested change will be approved by the maintainer.
* Fork the repository.
* Create a `feature/issue-<NUMBER>-short-description` from the `main` branch.
* **Make sure the project builds with your changes and passes the unit tests!**
* Commit with a clear and concise messages: `Fix issue #NUMBER: ...`.
* Create Pull Request from your branch to the main branch of this repository.
* Wait for the feedback and review!

In this repository, we prefer **merging** over **rebasing**, and **tabs** over **spaces**. Don't fight against it.

## Release

The release to [Maven Central](https://central.sonatype.com/) must be performed using the *Release to Maven Central* GitHub Action, which relies on the shared [Maven Central Release](https://github.com/metricshub/workflows/blob/main/README.md#maven-central-release).

## Licenses

The original code from Danny Daglas was under the Boost Software License. The current code is now mainly under LGPL, which means it's actually GPLv3, but can be used in non-GPL projects.

**Important:**

* The `Jawk.jar` executable artifact is released under strict GPLv3.
* The `jawk` Maven artifact is released under LGPL and can be used in non-GPL projects.

Each file includes a license header. Make sure to always include this header.

To update all Java files to include the header, simply use the command below:

```bash
mvn license:update-file-header
```
