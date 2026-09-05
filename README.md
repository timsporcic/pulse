# Pulse

## The Pitch

Your last "hello world" service pulled two hundred dependencies, and nobody
on your team can explain what half of them do. We reached for heavy
frameworks because Java needed them: thread pools, async plumbing, container
magic to hide the pain. That Java is gone. Virtual threads, records, and a
grown-up JDK have quietly erased the reasons heavy frameworks exist, and most teams
haven't noticed yet.

This talk shows the alternative, built on two principles: comprehension and
sovereignty. We'll walk a real production app built on Javalin, jOOQ, JTE
with htmx, and JobRunr for background jobs, with all application state in a
single SQLite file. Every claim comes with a number measured on the demo
repo: half the jars, half the memory, a 47-test suite that runs in under
two seconds. Then the demo: deploying the app live to a $6 box with
automatic HTTPS, continuous backup, and Prometheus metrics, provisioned by
one OpenTofu file, and watching it catch a site going down in real time.

You leave with two questions to ask of every dependency you add from now
on: can I understand it, and do I own it? In 2026 those questions pay
twice, because a stack small enough to hold in your head is small enough
for your AI pair to hold in context. 

## The Project

A lean uptime monitor — the running example for the _"Lean Java"_ conference
talk. Register URLs; a scheduled job pings each one; results go to SQLite; a
live server-rendered dashboard shows status and latency; a webhook fires when
a site goes down.

The point is not the feature set. Pulse demonstrates a deliberately lean Java
stack governed by two ideas: **comprehension** (one developer can hold the
whole system in their head) and **sovereignty** (one artifact plus a SQLite
file, reconstructable from this repo and a backup bucket, on one small box
you own).

## The stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 26, virtual threads, plain blocking code |
| Build | Gradle 9.7 (Groovy DSL), Shadow fat jar |
| Web | Javalin 7 (routing in config, virtual-thread executor) |
| Templates | JTE, precompiled to classes at build time |
| Interactivity | htmx 4 (vendored single file), polling fragments |
| Styling | Tailwind CSS, built once at dev time and committed |
| Data | SQLite (WAL, `BEGIN IMMEDIATE`) + jOOQ typed SQL, codegen from DDL |
| Background jobs | JobRunr, state in the same SQLite file |
| Resilience | Hand-rolled: JDK `HttpClient` timeouts + a retry loop |
| JSON | Jackson (records serialize as DTOs, no annotations) |
| Metrics | Micrometer → Prometheus at `/metrics` |
| Logging | Logback; human-readable in dev, Logstash JSON in prod |
| Edge | Caddy (automatic HTTPS) |
| Durability | Litestream replication to object storage |
| Infrastructure | OpenTofu: droplet + firewall + volume + Spaces bucket |

Notably absent, on purpose: Spring, any ORM, any SPA framework or JS build
pipeline, any message broker, Kubernetes.

## Run it locally

```
./gradlew run          # http://localhost:7070/
./gradlew test
./gradlew shadowJar && java -jar build/libs/pulse-all.jar
```

Add a monitor on the dashboard (try a bogus URL to see a red "down" row —
checks start within ~15 seconds). JSON API at `/api/monitors` and
`/api/monitors/{id}/checks`; Prometheus metrics at `/metrics`. The database
defaults to `./pulse.db` (override with `PULSE_DB`). The application binds to
`127.0.0.1:7070`; Caddy is the public entry point. Monitor and webhook URLs must
use HTTP or HTTPS, without embedded credentials, and check intervals must be positive.

## Deploy it

```
./gradlew shadowJar
cd infra
tofu init && tofu apply   # needs do_token, spaces keys, domain, demo_password_hash
# point your domain's A record at the droplet_ip output
```

Generate a bcrypt hash with `caddy hash-password` and set `demo_password_hash`
in an ignored `infra/*.tfvars` file. The default login name is `demo`; override
it with `demo_username`. Caddy requires authentication for the dashboard and API.
The hash travels through cloud-init into `/etc/caddy/pulse-users`, never the
public artifact bucket. This deployment is for a trusted operator who controls
outbound monitor destinations. The JSON API omits webhook URLs.

`tofu apply` uploads the jar and configs to a Spaces bucket and boots a
droplet whose cloud-init installs JDK 26, Caddy, and Litestream, mounts the
data volume, restores the SQLite file from a replica if one exists, and
starts everything. Destroy the droplet and apply again: the box rebuilds
itself and the data comes back. `pulse-restore.service` reads the backup
credentials from `/etc/litestream.env` and restores only when the database is
absent. Pulse and replication require a successful restore and a mounted data
volume. An existing database is preserved. Test backup recovery on an empty
volume to distinguish it from reattaching the existing data.

For manual installation, install all three units in `ops/`: `pulse.service`,
`pulse-restore.service`, and `litestream.service`. Create `/etc/caddy/pulse-users`
with one `username bcrypt-hash` line, owned by `root:caddy` with mode `0640`.
Store the Litestream environment file as root with mode `0600`.

Before upgrading an existing instance, let pending outage notifications finish.
New notification jobs store both the monitor ID and failing check ID; jobs from
the previous one-argument signature cannot be replayed by this version.

Run `python3 tools/verify-deployment.py` with OpenTofu and Caddy installed to
check rendered cloud-init, startup dependencies, and actual authenticated HTTP
requests through both Caddy configurations. This check does not provision a
server or verify access to a backup bucket.

## Follow the build, branch by branch

Each stage is a branch that compiles, runs, and is tested; each builds on
the previous one:

| Branch | Adds |
| --- | --- |
| `00-skeleton` | Gradle + JDK 26 toolchain, Javalin hello page, shadow jar |
| `01-web` | Dashboard shell, vendored htmx + prebuilt Tailwind CSS |
| `02-data` | `schema.sql`, SQLite + WAL, jOOQ codegen, persisted monitors |
| `03-presentation` | JTE board + htmx fragment polling every 5s |
| `04-json` | JSON API with record DTOs |
| `05-jobs` | JobRunr recurring checker, virtual-thread pinger |
| `06-resilience` | UP→DOWN detection, webhook notify with hand-rolled retry |
| `07-observability` | Prometheus `/metrics`, check timer, up/down gauge, JSON logs |
| `08-packaging` | Caddyfile, litestream.yml, systemd unit, cloud-init |
| `09-iac` | OpenTofu: droplet, firewall, bucket, volume |

Project layout, golden rules, and design notes live in `CLAUDE.md`; the
conference deck is in `presentation/`.
