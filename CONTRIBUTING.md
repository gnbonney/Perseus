# Contributing to Perseus

Contributions are welcome! Here are some guidelines to help you get started.

## Getting Started

1. Fork the repository on GitHub.
2. Clone your fork locally.
3. Build the project with `gradle build` to verify your setup.
4. Run the tests with `gradle test` to confirm everything passes.

## Development Workflow

- Use the iterative, test-driven approach described in [docs/Development.md](docs/Development.md).
- Add or update Algol grammar rules in `src/main/antlr/gnb/perseus/compiler/antlr/Perseus.g4`.
- Add corresponding test cases in `src/test/java/` and sample Algol programs in `test/algol/`.
- Run `gradle build` to regenerate the ANTLR parser and compile the project.
- Run `gradle test` to verify all tests pass before submitting a pull request.

## Code Style

- Follow standard Java conventions.
- Keep changes focused and well-commented.
- Use meaningful commit messages.

## Reporting Issues

Please open an issue on GitHub describing the problem or feature request with as much detail as possible.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE.md).

