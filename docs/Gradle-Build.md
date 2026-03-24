# Gradle Build Instructions

This project now uses Gradle for build and dependency management.

## Common Commands

- Build the project:
  ```
  gradle build
  ```
- Run tests:
  ```
  gradle test
  ```
- Clean build outputs:
  ```
  gradle clean
  ```

## Notes
- Jasmin 2.4 is bundled as `jasmin-2.4/jasmin.jar` and referenced as a local file dependency in `build.gradle`. It is self-contained and does not require separate `java_cup` jars.
- Soot is **not** a build dependency. It was removed because it pulls in Jasmin 3.x as a transitive dependency. See [Architecture.md](Architecture.md) if you need the rationale or want to use Soot for bytecode analysis outside the build.
- ANTLR is managed via Gradle (`org.antlr:antlr4:4.13.1`).

---
_Last updated: March 1, 2026_
