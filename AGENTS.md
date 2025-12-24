# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/sh/apptrail/controlplane/` contains application source code.
- `src/main/resources/application.yml` holds Spring Boot configuration (YAML).
- `src/main/resources/db/migration/` is the default Flyway migrations location.
- `src/test/kotlin/` contains test sources (JUnit 5 + Spring Boot test).
- `build.gradle` and `settings.gradle` define the Gradle build and module metadata.
- `compose.yaml` supports local dependencies via Spring Boot Docker Compose.

## Build, Test, and Development Commands
- `./gradlew bootRun` runs the Spring Boot app locally.
- `./gradlew test` executes the JUnit 5 test suite.
- `./gradlew build` compiles, tests, and assembles the project.
- `./gradlew clean` removes build outputs to reset the workspace.

## Coding Style & Naming Conventions
- Kotlin uses 2-space indentation; keep lines compact and readable.
- Package names are lower-case (e.g., `sh.apptrail.controlplane`).
- Class names use PascalCase; functions and properties use camelCase.
- No formatter or linter is configured; use IntelliJ IDEA’s Kotlin defaults.

## Testing Guidelines
- Frameworks: Spring Boot Test + JUnit 5 (`spring-boot-starter-test`).
- Place tests in `src/test/kotlin` and mirror package structure.
- Naming: `*Tests.kt` (see `ControlplaneApplicationTests.kt`).
- Run unit/integration tests with `./gradlew test`.

## Database Migrations (Flyway)
- Flyway is enabled via Gradle dependencies; prefer SQL migrations.
- Default location: `src/main/resources/db/migration/`.
- Naming pattern: `V<timestamp>__<short_description>.sql` (e.g., `V202502141030__add_users_table.sql`).

## Commit & Pull Request Guidelines
- Prefer Conventional Commits (e.g., `feat: add health endpoint`).
- PRs should include: a clear summary, test results, and any config changes.
- If a change impacts APIs or behavior, note it explicitly in the PR description.

## Configuration & Local Services
- Application config lives in `src/main/resources/application.yml`.
- Use `compose.yaml` for local services (e.g., databases) when needed.
- Java toolchain is set to 21; ensure your local JDK matches.
