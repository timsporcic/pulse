# Pulse

A lean uptime monitor — the running example for the _"Lean Java"_ conference talk.
One process, one SQLite file, server-rendered HTML. Smallness is the feature.

## Current stage: `01-web`

This branch adds the static dashboard shell on top of `00-skeleton`:

- Javalin serves static files from the classpath (`src/main/resources/public/`)
- `index.html` — the dashboard shell: monitor rows with status edge, latency, and uptime (static placeholder content for now)
- `htmx.min.js` 4.0.0 vendored — no CDN at runtime
- `tailwind.css` prebuilt and committed; regenerate with `tools/build-css.sh` when
  Tailwind classes change (the script downloads the standalone CLI on first use —
  a dev-time tool only, nothing runs at build or runtime)
- Tests assert the shell and both vendored assets are served

Earlier stage `00-skeleton` established: Gradle 9.7 (Groovy DSL, JDK 26 toolchain),
`application` + Shadow plugins, Javalin 7.2.3 on virtual threads, Logback logging,
and the first boot-the-app test.

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
