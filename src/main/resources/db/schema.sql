-- Canonical DDL: used by jOOQ codegen (DDLDatabase) and applied at startup.
CREATE TABLE IF NOT EXISTS monitor (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT    NOT NULL,
  url           TEXT    NOT NULL,
  interval_secs INTEGER NOT NULL DEFAULT 60,
  enabled       INTEGER NOT NULL DEFAULT 1,
  notify_url    TEXT
);

CREATE TABLE IF NOT EXISTS check_result (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  monitor_id  INTEGER NOT NULL REFERENCES monitor(id),
  checked_at  TEXT    NOT NULL,   -- ISO-8601
  up          INTEGER NOT NULL,
  status_code INTEGER,
  latency_ms  INTEGER
);
-- The index is runtime-only: jOOQ's DDL simulation (H2) cannot index a TEXT
-- column, and no code is generated from indexes anyway.
/* [jooq ignore start] */
CREATE INDEX IF NOT EXISTS idx_check_monitor_time ON check_result(monitor_id, checked_at);
/* [jooq ignore stop] */
