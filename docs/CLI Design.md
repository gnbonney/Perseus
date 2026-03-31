# CLI Design

## Goal

Perseus should be usable as a real command-line compiler without requiring users to invoke `java` directly.

The intended user experience is:

```bash
perseus hello.alg
```

not:

```bash
java -cp ... gnb.perseus.cli.PerseusCLI hello.alg
```

Perseus is implemented on the JVM, but the normal command-line experience should present it as a language tool in its own right rather than as a Java program the user has to manage manually.

---

## Primary Command Shape

The primary command should be:

```bash
perseus [options] <source files...>
```

Examples:

```bash
perseus hello.alg
perseus src\\main.alg src\\util.alg
perseus hello.alg -d out
perseus hello.alg --package mylib.numeric
perseus app.alg --jar app.jar
perseus app.alg -cp libs\\mylib.jar
```

This mirrors the general feel of `javac` while still leaving room for Perseus-specific options.

---

## Default Output Behavior

The default behavior is:

- compile one or more `.alg` files
- emit `.class` files into a sensible output directory
- preserve package layout under that directory

Default:

- if no `-d` is provided, write output to a generated build-oriented directory rather than beside the source files

Option:

```bash
-d <outdir>
```

This should control where generated `.j`, `.class`, and related artifacts go.

Option:

```bash
--package <name>
```

This should control the generated JVM package for the compiled Perseus unit.

This is especially important for:

- separate compilation
- external procedure linkage
- building reusable Perseus libraries with stable class names

---

## Optional JAR Packaging

The CLI supports optional JAR packaging, but JAR creation does not replace ordinary classfile output.

Option:

```bash
--jar <file>
```

Example:

```bash
perseus hello.alg --jar hello.jar
```

This flow:

- compile the program normally
- gather the generated class family
- package the result into a JAR

This is useful for:

- distributing small applications
- testing external procedure libraries
- giving users a familiar JVM artifact when they want one

Ordinary classfile output remains the default because it is simpler and also useful for library-style workflows.

---

## Classpath Handling

The CLI exposes the ordinary JVM classpath model for:

- `external(...)`
- `external java ...`

Options:

```bash
-cp <path>
--classpath <path>
```

These behave like the JVM and `javac`:

- directories and JARs on the classpath are used for external resolution
- Perseus does not invent a separate import or search-path mechanism

This keeps external procedure behavior aligned with the broader JVM ecosystem.

---

## Launcher Strategy

Perseus should ship a real launcher command.

Approach:

- use Gradle application/distribution support
- generate:
  - `perseus`
  - `perseus.bat`

This provides:

- a Windows launcher
- a macOS/Linux launcher
- a stable user-facing entry point

Even though these launch scripts still run on the JVM, users no longer need to type `java` or construct a classpath manually.

---

## Error Reporting and Exit Behavior

The CLI exposes compiler diagnostics in a user-facing way rather than dumping Java stack traces for ordinary compile failures.

Behavior:

- syntax and semantic diagnostics print normally with file, line, column, and code
- internal compiler errors may still print a stack trace
- exit codes should be stable and documented

Exit code categories:

- `0` success
- non-zero for compilation failure

---

## Future Directions

The current CLI is built around the launcher, classfile output, JAR packaging, package selection, and classpath-based external resolution described above.

Later CLI and distribution work may include:

- packaged runtime distribution for easier installation
- optional inclusion of a bundled JVM runtime
- platform-specific installers or app bundles using `jpackage`
- more refined exit-code distinctions between compilation failures and internal compiler/runtime failures

These are follow-on improvements rather than part of the current CLI surface.

---

## Design Principle

Perseus should embrace the JVM as an implementation and ecosystem target, but the CLI should make Perseus feel like its own language tool rather than a thin wrapper around `java`.
