CREATE TABLE IF NOT EXISTS active_installs (
    day TEXT NOT NULL,
    install_hash TEXT NOT NULL,
    mod_version TEXT NOT NULL,
    minecraft_version TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (day, install_hash)
);

CREATE INDEX IF NOT EXISTS active_installs_day_idx ON active_installs (day);
