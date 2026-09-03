# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `06-resilience`

This branch adds down-notifications with hand-rolled resilience, on top of
`05-jobs`:

- `notify/WebhookNotifier` — POSTs a JSON payload
  (`{"monitor","url","status","statusCode","at"}`) to the monitor's
  `notify_url`; three attempts with doubling backoff, then give up and log.
  A plain loop and JDK `HttpClient` timeouts — no resilience library
- UP→DOWN transition detection in the checker: when a monitor that was up
  comes back down *and* has a `notify_url`, a one-off `NotifyDownJob` is
  enqueued in JobRunr (persistent, survives restarts). Staying down does
  not re-notify; a first-ever check that is down does not notify
- The pinger's timeouts (5s connect / 10s request) from `05-jobs` are the
  ping-side resilience: a slow site is simply a DOWN result

Try it: register a monitor with a `notify_url` form field, kill the
monitored site, and the webhook receives one JSON notification.

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete), `03-presentation`
(JTE board + 5s htmx polling), `04-json` (read-only JSON API with record
DTOs), `05-jobs` (JobRunr recurring checker + virtual-thread pinger, all
state in one SQLite file).

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
