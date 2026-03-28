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
perseus app.alg --jar app.jar
perseus app.alg -cp libs\\mylib.jar
```

This mirrors the general feel of `javac` while still leaving room for Perseus-specific options.

---

## Default Output Behavior

The default behavior should be:

- compile one or more `.alg` files
- emit `.class` files into a sensible output directory
- preserve package layout under that directory

Recommended default:

- if no `-d` is provided, write output to a generated build-oriented directory rather than beside the source files

Recommended option:

```bash
-d <outdir>
```

This should control where generated `.j`, `.class`, and related artifacts go.

---

## Optional JAR Packaging

The CLI should support optional JAR packaging, but JAR creation should not replace ordinary classfile output.

Recommended option:

```bash
--jar <file>
```

Example:

```bash
perseus hello.alg --jar hello.jar
```

This should:

- compile the program normally
- gather the generated class family
- package the result into a JAR

This is useful for:

- distributing small applications
- testing external procedure libraries
- giving users a familiar JVM artifact when they want one

Ordinary classfile output should remain the default because it is simpler and also useful for library-style workflows.

---

## Classpath Handling

The CLI should expose the ordinary JVM classpath model for:

- `external algol(...)`
- `external java ...`

Recommended options:

```bash
-cp <path>
--classpath <path>
```

These should behave like the JVM and `javac`:

- directories and JARs on the classpath are used for external resolution
- Perseus does not invent a separate import or search-path mechanism

This keeps external procedure behavior aligned with the broader JVM ecosystem.

---

## Launcher Strategy

The first milestone should hide Java by shipping a real launcher command.

Recommended initial approach:

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

## Installation and Distribution

Perseus should eventually be installable as a normal command-line tool on Windows and macOS.

Recommended phased approach:

### Phase 1

- distributable launcher scripts
- zipped application bundle from Gradle

### Phase 2

- packaged runtime distribution for easier installation
- optional inclusion of a bundled JVM runtime if desired

### Phase 3

- platform-specific installers or app bundles using `jpackage`

This would allow:

- Windows installer or app image
- macOS application bundle or installer

The goal is for users to install and run `perseus` as a normal tool, without being constantly reminded that the implementation happens to target the JVM.

---

## Error Reporting and Exit Behavior

The CLI should expose compiler diagnostics in a user-facing way rather than dumping Java stack traces for ordinary compile failures.

Recommended behavior:

- syntax and semantic diagnostics print normally with file, line, column, and code
- internal compiler errors may still print a stack trace
- exit codes should be stable and documented

Suggested categories:

- `0` success
- non-zero for compilation failure
- distinct non-zero code for internal compiler/runtime failure if practical

---

## Artifact Policy

The CLI should decide intentionally which files it keeps by default:

- `.class`
- `.jar` when requested
- optional `.j` Jasmin output

One reasonable policy is:

- keep `.class` output by default
- emit `.j` only with an explicit option such as `--emit-jasmin`

That would make the normal user-facing experience feel like a compiler, while still preserving the JVM/Jasmin transparency that is valuable during development.

---

## Staged Milestone 31 Plan

### First Slice

- `perseus` launcher script via Gradle application packaging
- compile one or more `.alg` files
- `-d <outdir>`
- `-cp` / `--classpath`
- improved user-facing diagnostics and exit codes

### Second Slice

- `--jar <file>`
- optional `--emit-jasmin`
- cleaner output layout

### Later Polishing

- installable platform packages
- `jpackage`
- richer CLI subcommands like `check` or `emit-jasmin`

---

## Design Principle

Perseus should embrace the JVM as an implementation and ecosystem target, but the CLI should make Perseus feel like its own language tool rather than a thin wrapper around `java`.
