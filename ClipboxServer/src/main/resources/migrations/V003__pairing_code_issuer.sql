CREATE TABLE device_pairing_codes_new (
  issuer_id  TEXT PRIMARY KEY,
  code_hash  TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at    INTEGER
);

INSERT INTO device_pairing_codes_new (issuer_id, code_hash, created_at, expires_at, used_at)
  SELECT device_id, code_hash, created_at, expires_at, used_at FROM device_pairing_codes;

DROP TABLE device_pairing_codes;

ALTER TABLE device_pairing_codes_new RENAME TO device_pairing_codes;

CREATE INDEX idx_dpc_expires ON device_pairing_codes(expires_at);
