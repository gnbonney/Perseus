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
- Jasmin is not available on Maven Central; keep using your local jar in `lib/` for now.
- Soot and ANTLR dependencies are managed via Gradle.
- Source code should be moved to `src/main/java` and tests to `src/test/java` for best practices.

---
_Last updated: February 28, 2026_
