# CLAUDE.md — Pulse

> Project context and working rules for Claude Code. Read this fully before writing any code.

## What we're building

**Pulse** is a lean uptime monitor, built as the running example for a conference talk called _"Lean Java."_ You register URLs; a scheduled job pings each one; results go to SQLite; a live server-rendered dashboard shows status and latency; an external webhook is notified when a site goes down.

The point of Pulse is **not** the feature set — it's to demonstrate a deliberately lean Java stack. Two design goals govern every decision:

1. **Comprehension** — a single developer can hold the whole system in their head.
2. **Sovereignty** — the whole thing is one artifact plus a SQLite file, reconstructable from a git repo and a backup bucket, running on one small box you own.

Keep it small. Smallness is the feature, not a stage to grow out of. The HTML pages should have a pleasing, modern look. It is acceptable to use TailwindCSS for styling.

## Golden rules (hard constraints)

- **No Spring. No Spring Boot.** Ever. Not as a convenience, not for one feature.
- **No ORM.** No Hibernate, no JPA. Data access is typed SQL via jOOQ only.
- **No SPA framework.** No React/Vue/Angular/build pipeline. The UI is server-rendered HTML (JTE) enhanced with HTMX.
- **No external broker.** No Redis/Kafka/RabbitMQ. Background jobs persist in SQLite via JobRunr.
- **Minimize dependencies.** Every dependency is attack surface and cognitive load. Before adding one, ask whether the JDK already does it. Prefer the standard library and virtual threads over a library.
- **Explicit over magic.** No annotation-driven wiring, no reflection-based "magic." If behavior isn't visible by reading the code, reconsider.
- **Records as DTOs.** Immutable record types for data carried across boundaries.
- **Virtual threads.** Plain blocking code on virtual threads (Java 21+ Loom). Do not introduce reactive/async frameworks.

If a requested change violates a golden rule, stop and flag it rather than working around it.

## Toolchain (confirmed current, Aug 2026)

- **Java 26** (26.0.2.1 GA). Configure a Gradle Java **toolchain** with `languageVersion = JavaLanguageVersion.of(26)`. (Java 25 is the LTS; 26 is the current feature release, which is what we want here.)
- **Gradle 9.7.x** via the wrapper (`./gradlew`). Gradle 9.4+ supports running and targeting JDK 26. Use the latest 9.5.x wrapper.
- **Groovy DSL** for all build scripts — `build.gradle`, `settings.gradle` (NOT the Kotlin `.kts` variants).
- **Shadow** for the fat jar: plugin id `com.gradleup.shadow` version `9.6.1`. **Do not** use the old, dead `com.github.johnrengelman.shadow` id.
- Apply the `application` plugin (gives `run`, sets `mainClass`).

## Dependencies

Pin every dependency to the **latest stable** release on Maven Central at build time. The versions below are known-good anchors as of late May 2026 — bump to a newer stable if one exists, but do not downgrade. Confirm each on Maven Central before pinning.

| Concern                       | Coordinates                                                                                                                 | Version                                 |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| Web framework                 | `io.javalin:javalin-bom` (platform), then `io.javalin:javalin`                                                              | **7.2.3** (confirmed)                   |
| JTE rendering for Javalin     | `io.javalin:javalin-rendering` (tracks the BOM)                                                                             | via BOM                                 |
| Micrometer bridge for Javalin | `io.javalin:javalin-micrometer` (tracks the BOM)                                                                            | via BOM                                 |
| SQL / data access             | `org.jooq:jooq` (Open Source Edition)                                                                                       | **3.21.x** (confirmed; free for SQLite) |
| jOOQ codegen (build-time)     | `org.jooq:jooq-codegen` + the official jOOQ codegen Gradle plugin `org.jooq:jooq-codegen-gradle`                            | match jooq version                      |
| SQLite driver                 | `org.xerial:sqlite-jdbc`                                                                                                    | latest 3.x (verify)                     |
| Templates                     | `gg.jte:jte` + the `gg.jte.jte-gradle-plugin` for precompiled templates                                                     | latest 3.x (verify)                     |
| Background jobs               | `org.jobrunr:jobrunr` (SQLite-backed via its SQL storage provider on our DataSource)                                        | **7.5.1** (confirmed; 8.x is still beta — do not use pre-releases) |
| Metrics                       | `io.micrometer:micrometer-registry-prometheus`                                                                              | latest 1.x (verify)                     |
| Logging                       | `org.slf4j:slf4j-api` + `ch.qos.logback:logback-classic`; add `net.logstash.logback:logstash-logback-encoder` for JSON logs | latest (verify)                         |

Notes:

- **HTMX** is a single JS file, not a Gradle dependency. Vendor `htmx.min.js` into `src/main/resources/public/` and serve it as a static file (no CDN dependency at runtime — sovereignty).
- **jOOQ Open Source Edition** (`org.jooq`, Apache-2.0) is the one on Maven Central and is free for SQLite and Postgres. Do not pull a commercial `org.jooq.pro*` coordinate.

## Build commands

```
./gradlew run            # run locally (application plugin)
./gradlew shadowJar      # build the fat jar -> build/libs/pulse-all.jar
./gradlew build          # compile + test + assemble
./gradlew test           # tests
./gradlew jooqCodegen    # generate jOOQ classes from schema (task name per plugin)
```

The shadow jar must be runnable with a plain `java -jar build/libs/pulse-all.jar`.

## Project layout

Base package: `org.sporcic.pulse`.

```
pulse/
  build.gradle
  settings.gradle
  gradle/wrapper/...
  src/main/java/org/sporcic/pulse/
    App.java                  # main(): wire config, start Javalin, start JobRunr
    web/                      # Javalin route config + handlers (controllers)
    web/api/                  # JSON endpoints (record DTOs)
    data/                     # jOOQ-based repositories; DataSource setup
    domain/                   # records: Monitor, Check, MonitorView, ...
    jobs/                     # JobRunr job definitions (the checker, the notifier)
    check/                    # the pinger (outbound HTTP + timeout)
    notify/                   # webhook dispatch (JDK HttpClient + hand-rolled retry)
    metrics/                  # Micrometer registry + Prometheus endpoint
  src/main/jte/               # JTE templates (board.jte, fragments)
  src/main/resources/
    db/schema.sql             # canonical DDL (source of truth for codegen + runtime)
    public/                   # htmx.min.js, a little CSS
    logback.xml
  build/generated-src/jooq/   # generated jOOQ classes (git-ignored)
```

## Domain model

Two tables. Keep it this simple.

```sql
-- src/main/resources/db/schema.sql
CREATE TABLE monitor (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT    NOT NULL,
  url           TEXT    NOT NULL,
  interval_secs INTEGER NOT NULL DEFAULT 60,
  enabled       INTEGER NOT NULL DEFAULT 1,
  notify_url    TEXT
);

CREATE TABLE check_result (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  monitor_id  INTEGER NOT NULL REFERENCES monitor(id),
  checked_at  TEXT    NOT NULL,   -- ISO-8601
  up          INTEGER NOT NULL,
  status_code INTEGER,
  latency_ms  INTEGER
);
CREATE INDEX idx_check_monitor_time ON check_result(monitor_id, checked_at);
```

Flow: register a monitor → a recurring job pings every enabled monitor on schedule → each result is written to `check_result` → the dashboard reads recent status + uptime % → when a monitor transitions UP→DOWN, enqueue a one-off notification to its `notify_url`.

## jOOQ codegen approach

Generate from the DDL files, not a live database — keeps codegen reproducible and offline. Use jOOQ's `DDLDatabase` pointing at `src/main/resources/db/schema.sql`, wired through the official `jooq-codegen-gradle` plugin. The same `schema.sql` is applied at runtime on startup (a tiny "create tables if absent" step — no Flyway needed for two tables; if migrations grow, revisit).

## Implementation notes & gotchas

- **Javalin 7 routing lives in config.** v7 moved routing into the config block — use the current `Javalin.create(config -> { config.router... })` style, not the old chained `.get().start()` from the slides. Verify against the 7.2.x docs.
- **SQLite: enable WAL mode** (`PRAGMA journal_mode=WAL;`) and `busy_timeout`. Use a small connection pool; SQLite is single-writer, so serialize writes. One process only.
- **Metrics:** expose Prometheus at `/metrics` (or `/q/metrics`). Register a check-duration timer and an up/down gauge per monitor.
- **Virtual threads:** ensure Javalin/Jetty handles requests on virtual threads and that the checker uses virtual threads for concurrent pings. No thread pools tuned for blocking I/O.
- **Logging:** structured JSON to stdout in the deployable config; human-readable in dev.
- **Resilience is hand-rolled on purpose.** No Resilience4j or similar: timeouts come from `java.net.http.HttpClient`, retry is a small explicit loop with backoff, all on virtual threads. Talk point: "the JDK already does it."

## Git branch plan

Build Pulse incrementally, **one branch per step**, each branching from the previous so a follower can `git checkout` any stage and run it. Every branch must **compile and run** and leave the app working at that stage. Commit with clear messages; update `README.md` at each branch describing what that step added.

| Branch             | Adds                                                                                                               | Runnable result                                                    |
| ------------------ | ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| `00-skeleton`      | Gradle (Groovy DSL, JDK 26 toolchain, application + shadow plugins), `App.main`, Javalin serving a "hello" page    | `./gradlew run` serves a page; `shadowJar` produces a runnable jar |
| `01-web`           | Javalin route config, a static dashboard shell, htmx.min.js vendored, prebuilt `tailwind.css` vendored (built once with the Tailwind standalone CLI, committed — no CDN at runtime) | dashboard renders (static)                                         |
| `02-data`          | `schema.sql`, SQLite + WAL, jOOQ codegen, repositories, add/list/delete monitors persisted                         | monitors persist across restarts                                   |
| `03-presentation`  | JTE board template + an HTMX fragment that polls (`hx-get` every 5s)                                               | live-updating board                                                |
| `04-json`          | JSON API (`/api/monitors`, `/api/monitors/{id}/checks`) using record DTOs                                          | JSON endpoints return data                                         |
| `05-jobs`          | JobRunr (SQLite-backed storage) + recurring checker job + the `check/` pinger; results written to `check_result`; dashboard shows real data | monitors get pinged on schedule automatically                      |
| `06-resilience`    | hand-rolled resilience (no Resilience4j): connect/request timeouts on the pinger, bounded retry with backoff on the notify webhook, UP→DOWN transition detection enqueuing a one-off JobRunr notification job | down-transitions dispatch notifications, retried safely            |
| `07-observability` | Micrometer + `/metrics`, check-duration timer, up/down gauge, structured logging                                   | Prometheus can scrape metrics                                      |
| `08-packaging`     | finalize `shadowJar`; `Caddyfile`; `litestream.yml`; `pulse.service` (systemd); cloud-init snippet                 | jar + ops files ready to deploy                                    |
| `09-iac`           | `infra/` with OpenTofu (droplet, firewall, Spaces bucket, volume) + cloud-init that installs and starts everything | `tofu apply` provisions; box self-bootstraps                       |
| `main`             | merge of the complete, deployable project + full README                                                            | end-to-end deployable                                              |

## Definition of done (every branch)

1. `./gradlew build` passes.
2. `./gradlew run` starts the app and it works at that step's scope.
3. The shadow jar runs via `java -jar`.
4. No new dependency added without a one-line justification in the commit message.
5. `README.md` updated for the branch.
6. JUnit tests created for the incremental code created and the tests pass

## Anti-goals (do not add)

Spring, Hibernate/JPA, a SPA framework or JS build step, Redis/Kafka/RabbitMQ, an application server, Kubernetes manifests, a SaaS APM agent, or any dependency that exists only to save a few lines the JDK already covers. When unsure, choose fewer moving parts.
