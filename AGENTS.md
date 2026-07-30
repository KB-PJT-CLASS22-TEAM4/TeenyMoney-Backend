# Repository Guidelines

## Project Structure & Module Organization

This is a Java 17, Spring MVC (non-Boot) application packaged as `ROOT.war` for Tomcat 9. Production code lives under `src/main/java/com/teenyfin/teenymoney`. Keep framework configuration in `config`, reusable infrastructure in `global`, and business features in `domain/<feature>`. A feature normally contains `controller`, `dto/request`, `dto/response`, `service`, `mapper`, and `vo` packages.

Resources are under `src/main/resources`. Place MyBatis XML beside the equivalent Java package path, ending in `Mapper.xml`. Update `openapi/teenymoney-api.yaml` whenever an API contract changes. Tests mirror production packages in `src/test/java`. Database schema, ordered migrations, and local seed data belong in `sql/schema`, `sql/migration`, and `sql/seed`; see `sql/README.md` before changing them.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper:

- `.\gradlew.bat test` runs the JUnit 5 suite.
- `.\gradlew.bat war` creates `build/libs/ROOT.war`.
- `.\gradlew.bat clean build` performs the same clean test-and-package flow used by CI.

On macOS/Linux, replace `.\gradlew.bat` with `./gradlew`. Local deployment requires JDK 17 and Tomcat 9; DB-backed checks also require MySQL 8 and the environment variables described in `docs/LOCAL_TEST.md`.

## Coding Style & Naming Conventions

Follow existing Java style: four-space indentation, one public type per file, PascalCase classes, camelCase methods and fields, and lowercase package names. Use suffixes consistently: `Controller`, `Service`, `Mapper`, `DTO`, `VO`, and `*Test`. Keep controllers focused on HTTP concerns, business rules in services, and persistence in MyBatis mappers. Return JSON through `ApiResponse<T>` and represent expected failures with domain `ErrorCode` implementations and `BusinessException`. No formatter or linter is configured, so match nearby code and keep imports organized.

## Testing Guidelines

Write JUnit Jupiter tests that mirror the target package and end filenames with `Test.java`. Use Spring Test/MockMvc for web contracts. Add regression coverage for fixes and verify status codes, response envelopes, validation, and error codes. Some context tests require `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; never hard-code credentials. There is no stated coverage threshold, but all tests must pass before review.

## Commit & Pull Request Guidelines

Use concise conventional prefixes seen in history: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, or `chore:`. Name branches `<issue>-<type>-<initials>-<summary>`, for example `7-chore-psh-github-templates`. PR titles must use `type(scope): summary`. Complete the repository PR template with linked issues, purpose, affected files/APIs, verification steps, impact, and screenshots when useful. Update OpenAPI and SQL documentation in the same PR, and ensure both `repo-policy` and `backend-build` CI checks pass.

## Security & Configuration

Keep secrets, `.env` files, IDE settings, certificates, and local property overrides out of Git. Supply DB and Redis settings through environment variables, and use only synthetic data in seeds and tests.
