# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `09-iac`

This branch provisions the whole thing with OpenTofu, on top of
`08-packaging`:

- `infra/` — one Spaces bucket (deploy artifacts under `/deploy`,
  Litestream replica at `/pulse.db`), a volume for the SQLite file that
  outlives the droplet, the droplet itself, and a firewall (22/80/443 in)
- `tofu apply` uploads the built jar + templated Caddyfile/litestream.yml
  to the bucket, then boots the droplet with `ops/cloud-init.yml` as
  templated user-data — the box installs JDK 26, Caddy, and Litestream,
  restores the database from a replica if one exists, and starts everything
- Kill the droplet, `tofu apply` again: infra rebuilds, cloud-init
  re-bootstraps, Litestream restores the data. That's the sovereignty story

Deploy:

```
./gradlew shadowJar
cd infra
tofu init
tofu apply    # needs do_token, spaces keys, domain (see variables.tf)
# point the domain's A record at the droplet_ip output
```

## Previous stage: `08-packaging`

This branch readies Pulse for a real box, on top of `07-observability`:

- `shadowJar` finalized with `mergeServiceFiles()` (service-loader
  registrations from the JDBC driver, logback, and jackson merge instead
  of colliding)
- `ops/Caddyfile` — reverse proxy with automatic HTTPS; `/metrics` is
  blocked at the edge (Prometheus scrapes localhost directly)
- `ops/litestream.yml` — continuous SQLite replication to S3-compatible
  object storage; credentials via environment
- `ops/pulse.service` — hardened systemd unit (`ProtectSystem=strict`,
  dedicated `pulse` user, JSON logging config, restart on failure)
- `ops/cloud-init.yml` — bootstraps a fresh Ubuntu host: Temurin JDK 26,
  Caddy, Litestream (with its own unit), fetches the jar and configs from
  the deploy bucket, restores the database from a replica if one exists,
  starts everything. Bucket URL and credentials are templated in by
  `09-iac`

The deployable artifact remains a single `pulse-all.jar` plus the SQLite
file; everything in `ops/` is plain text you can read in one sitting.

Earlier stages: `00-skeleton` (Gradle 9.7, JDK 26 toolchain, Javalin 7.2.3 on
virtual threads, Shadow fat jar), `01-web` (dashboard shell, vendored
htmx 4.0.0 + prebuilt Tailwind CSS), `02-data` (SQLite + WAL, jOOQ codegen
from `schema.sql`, persisted monitor add/list/delete), `03-presentation`
(JTE board + 5s htmx polling), `04-json` (read-only JSON API with record
DTOs), `05-jobs` (JobRunr recurring checker + virtual-thread pinger, all
state in one SQLite file), `06-resilience` (UP→DOWN webhook notifications
with hand-rolled retry), `07-observability` (Prometheus `/metrics`, check
timer + up/down gauge, JSON logging).

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
