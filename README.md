# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `02-data`

This branch adds persistence on top of `01-web`:

- `src/main/resources/db/schema.sql` — canonical DDL for the two tables
  (`monitor`, `check_result`); used by jOOQ codegen at build time and applied
  at startup (idempotent `CREATE ... IF NOT EXISTS`)
- SQLite via `sqlite-jdbc`, opened in WAL mode with a busy timeout and
  foreign keys enforced (`data/Database.java`)
- jOOQ 3.21.7 with build-time codegen from the DDL (`./gradlew jooqCodegen`,
  runs automatically before compilation) — typed SQL, no ORM
- `data/MonitorRepository` — add / list / delete monitors
- Interim endpoints to exercise persistence from the running app
  (`POST /monitors`, `GET /monitors`, `DELETE /monitors/{id}`, plain text —
  the JSON API with record DTOs arrives in `04-json`)
- The database file defaults to `./pulse.db`; override with the `PULSE_DB`
  environment variable

Try it:

```
./gradlew run
curl -d 'name=Example&url=https://example.org' localhost:7070/monitors
curl localhost:7070/monitors        # survives a restart
curl -X DELETE localhost:7070/monitors/1
```

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (static dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS).

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
