CREATE TABLE device_pairing_codes (
  device_id  TEXT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
  code_hash  TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at    INTEGER
);

CREATE INDEX idx_dpc_expires ON device_pairing_codes(expires_at);

DROP TABLE pairing_codes;
