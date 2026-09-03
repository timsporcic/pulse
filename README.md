# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `03-presentation`

This branch makes the board live on top of `02-data`:

- JTE 3.2.4 templates in `src/main/jte/` (`board.jte` page,
  `fragments/rows.jte` board rows), precompiled to Java classes at build time
  by the `gg.jte.gradle` plugin — no runtime template compilation, works
  inside the shadow jar
- The board section polls `GET /board` every 5 seconds via htmx
  (`hx-get` + `hx-trigger="every 5s"`) and swaps in the fresh rows
- Add-monitor form and per-row Remove buttons post through htmx
  (`hx-swap="none"` — the next poll shows the change)
- `MonitorRepository.listViews()` — one query joining each monitor with its
  latest check and uptime %; monitors with no checks yet render as
  "waiting for first check" (real check data arrives in `05-jobs`)
- The static `index.html` is replaced by the server-rendered board at `/`

Try it: `./gradlew run`, open http://localhost:7070/, add a monitor — it
appears within one 5-second poll cycle.

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete).

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
