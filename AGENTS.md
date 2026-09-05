# Repository Guidelines

## Project structure and architecture

Pulse is an uptime monitor built with Java 26, Javalin, jOOQ, JTE, and SQLite.

- `src/main/java/org/sporcic/pulse/`: application code. `App.java` wires the web app; `web/` contains routes, `data/` repositories, `domain/` records, and `jobs/` background work. HTTP checks, notifications, and metrics have separate packages.
- `src/main/jte/`: page templates and htmx fragments.
- `src/main/resources/`: canonical DDL in `db/schema.sql`, static assets in `public/`, and logging configuration.
- `src/test/java/`: tests mirroring the application packages.
- `infra/` and `ops/`: OpenTofu and deployment configuration. `presentation/` contains the conference deck.

Keep wiring explicit and use blocking code on virtual threads. Preserve the constraints in `CLAUDE.md`: no Spring, ORM, SPA framework, or external message broker. Prefer JDK APIs before adding dependencies.

## Build, test, and development commands

Use JDK 26 and the committed Gradle wrapper:

- `./gradlew run`: start the app at `http://localhost:7070/`.
- `./gradlew build`: compile, test, and assemble.
- `./gradlew test`: run the full test suite.
- `./gradlew test --tests '*MonitorRepositoryTest'`: run one test class.
- `./gradlew shadowJar`: create `build/libs/pulse-all.jar`; run it with `java -jar build/libs/pulse-all.jar`.
- `./gradlew jooqCodegen`: regenerate typed SQL classes from the DDL. Compilation also triggers this task.
- `bash tools/build-css.sh`: rebuild Tailwind CSS after class changes; commit the resulting CSS. First use downloads the standalone CLI.

## Coding style and naming

Follow existing four-space indentation and same-line braces. Use `UpperCamelCase` classes, `lowerCamelCase` methods and fields, and lowercase packages. Prefer imports over inline fully qualified names and records for DTOs. Keep Gradle scripts in Groovy. Compilation enables `-Xlint:all`; no formatter is configured. Never edit generated sources under `build/`.

## Testing guidelines

Use JUnit Jupiter and Javalin test tools. Name classes `*Test` and methods after observable behavior, such as `deleteRemovesMonitorWithCheckHistory`. Use `@TempDir` for isolated SQLite files. Add tests for changed behavior and run `./gradlew build` before submitting. No coverage threshold is configured.

## Commit and pull request guidelines

History uses short descriptive subjects, often imperative, with optional prefixes such as `README:` or `build.gradle:`. Keep commits focused. Explain each new dependency in the commit message. In pull requests, describe behavior changes, report validation, link relevant issues, and include screenshots for UI changes. Update `README.md` when usage changes.

## Local configuration

Set `PULSE_DB` to override `./pulse.db`. Keep database files, credentials, OpenTofu state, and `.tfvars` files out of commits.
