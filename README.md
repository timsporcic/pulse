# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `04-json`

This branch adds the read-only JSON API on top of `03-presentation`:

- `GET /api/monitors` — all monitors as JSON
- `GET /api/monitors/{id}/checks` — the monitor's checks, newest first
  (capped at 100), 404 for an unknown monitor
- The domain records (`Monitor`, `Check`) serialize directly as DTOs —
  Jackson handles records natively, no annotations
- `data/CheckRepository` — typed jOOQ reads over `check_result`
- The interim plain-text `GET /monitors` is retired; the form endpoints
  (`POST /monitors`, `DELETE /monitors/{id}`) stay, serving the board UI

Try it:

```
./gradlew run
curl -d 'name=Example&url=https://example.org' localhost:7070/monitors
curl localhost:7070/api/monitors
curl localhost:7070/api/monitors/1/checks
```

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete), `03-presentation`
(JTE board + 5s htmx polling).

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
