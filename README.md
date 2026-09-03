# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `07-observability`

This branch makes Pulse observable, on top of `06-resilience`:

- `GET /metrics` — Prometheus text format from a
  `PrometheusMeterRegistry` (`metrics/Metrics`), with JVM memory/GC/CPU/
  uptime binders
- `metrics/CheckMetrics` — a `pulse_check_seconds` timer and a
  `pulse_monitor_up` 0/1 gauge, both tagged per monitor; recorded by the
  checker job on every check
- Javalin HTTP metrics (`http_server_requests_seconds` per route/status)
  via the `javalin-micrometer` plugin
- Structured JSON logging for deployment: run with
  `-Dlogback.configurationFile=logback-json.xml` (logstash encoder to
  stdout); the default `logback.xml` stays human-readable for dev

Try it: `./gradlew run`, add a monitor, then `curl localhost:7070/metrics | grep pulse_`.

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete), `03-presentation`
(JTE board + 5s htmx polling), `04-json` (read-only JSON API with record
DTOs), `05-jobs` (JobRunr recurring checker + virtual-thread pinger, all
state in one SQLite file), `06-resilience` (UP→DOWN webhook notifications
with hand-rolled retry).

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
