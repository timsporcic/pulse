# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `05-jobs`

This branch makes Pulse actually monitor things, on top of `04-json`:

- `check/Pinger` — a plain JDK `HttpClient` GET with a 5s connect / 10s
  request timeout; < 400 is up, anything else (including timeouts and
  refused connections) is down
- `jobs/CheckDueMonitorsJob` — pings every *due* monitor concurrently on
  virtual threads and records each result in `check_result`; a monitor is
  due when its last check is older than its `interval_secs`
- JobRunr 7.5.1 runs that job every 15 seconds as a recurring background
  job; its state lives in the same SQLite file (`jobrunr_*` tables) — no
  broker, one process, one file
- SQLite connections now use `BEGIN IMMEDIATE` transactions: writers take
  the lock up front and queue on the busy timeout, avoiding
  `SQLITE_BUSY_SNAPSHOT` between JobRunr and the web handlers
- The board now shows real statuses: add a monitor and it flips from
  "waiting for first check" to live latency/uptime within ~15 seconds

Try it: `./gradlew run`, open http://localhost:7070/, add any URL you like —
including a bogus one to see a red "down" row.

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete), `03-presentation`
(JTE board + 5s htmx polling), `04-json` (read-only JSON API with record
DTOs).

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
