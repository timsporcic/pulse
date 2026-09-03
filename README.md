# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `00-skeleton`

This branch establishes the build and a running web server:

- Gradle 9.7 (Groovy DSL) with a JDK 26 toolchain
- `application` plugin (`./gradlew run`) and Shadow 9.6.1 for the fat jar
- Javalin 7.2.3 serving a hello page at `/` on port 7070, handling requests on virtual threads
- Logback console logging (human-readable dev config)
- JUnit test that boots the app and asserts the page responds

## Run it

```
./gradlew run                          # serves http://localhost:7070/
./gradlew test                         # tests
./gradlew shadowJar                    # build fat jar
java -jar build/libs/pulse-all.jar     # run the fat jar
```

Requires JDK 26 (the Gradle toolchain will locate or provision it).

## Roadmap

Each stage lives on its own branch, building on the previous one — check out any
branch and it compiles and runs. See `CLAUDE.md` for the full plan:
`00-skeleton` → `01-web` → `02-data` → `03-presentation` → `04-json` →
`05-jobs` → `06-resilience` → `07-observability` → `08-packaging` → `09-iac` → `main`.
