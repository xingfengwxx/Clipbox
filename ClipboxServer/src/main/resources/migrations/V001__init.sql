CREATE TABLE schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at INTEGER NOT NULL
);

CREATE TABLE devices (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  token_hash   TEXT NOT NULL UNIQUE,
  created_at   INTEGER NOT NULL,
  last_seen_at INTEGER
);

CREATE TABLE clips (
  id               TEXT PRIMARY KEY,
  content          TEXT NOT NULL,
  source_device_id TEXT,
  pinned           INTEGER NOT NULL DEFAULT 0 CHECK (pinned IN (0, 1)),
  created_at       INTEGER NOT NULL,
  deleted_at       INTEGER,
  server_seq       INTEGER NOT NULL,
  FOREIGN KEY (source_device_id) REFERENCES devices(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_clips_server_seq ON clips(server_seq);
CREATE INDEX idx_clips_created_at ON clips(created_at);
CREATE INDEX idx_clips_retention ON clips(pinned, deleted_at, created_at);

CREATE TABLE pairing_codes (
  code       TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at    INTEGER
);

CREATE INDEX idx_pairing_expires ON pairing_codes(expires_at);

CREATE TABLE shares (
  token_hash TEXT PRIMARY KEY,
  clip_id    TEXT NOT NULL,
  content    TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  one_time   INTEGER NOT NULL DEFAULT 0 CHECK (one_time IN (0, 1)),
  opened_at  INTEGER
);

CREATE INDEX idx_shares_expires ON shares(expires_at);

CREATE TABLE meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

INSERT INTO meta(key, value) VALUES ('server_seq', '0');
INSERT INTO meta(key, value) VALUES ('sync_floor', '0');
