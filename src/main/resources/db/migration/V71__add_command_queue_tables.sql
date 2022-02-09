BEGIN;

DROP TABLE IF EXISTS queue_command_errors CASCADE;
DROP TABLE IF EXISTS queue_ca_commands CASCADE;
DROP TABLE IF EXISTS queue_commands CASCADE;
DROP TABLE IF EXISTS command_archive CASCADE;

CREATE TABLE queue_ca_commands (
  id              BIGSERIAL PRIMARY KEY,
  ca_id           BIGINT NOT NULL,
  command_type    TEXT NOT NULL,
  command         TEXT NOT NULL,
  admin           BOOLEAN NOT NULL DEFAULT FALSE,
  locking_policy  TEXT NOT NULL DEFAULT 'THIS_CA_ONLY',

  -- commands with the same 'non_conflicting_group' are considered non-conflicting
  -- commands with non_conflicting_group = NULL are in conflict with any other command
  non_conflicting_group TEXT,

  -- version of the CA at the moment of command submission
  ca_version      BIGINT NOT NULL,
  archive         BOOLEAN NOT NULL DEFAULT false,
  scope           BIGINT,
  inserted_at     TIMESTAMP NOT NULL DEFAULT NOW()

  CHECK (locking_policy IN ('THIS_CA_ONLY', 'THIS_AND_PARENT'))
);

-- commands that are not related to a specific CA
CREATE TABLE queue_commands (
  id             BIGSERIAL PRIMARY KEY,
  command_type   TEXT NOT NULL,
  command        TEXT NOT NULL,
  locking_policy TEXT NOT NULL,
  admin          BOOLEAN NOT NULL DEFAULT FALSE,
  archive        BOOLEAN NOT NULL DEFAULT FALSE,
  inserted_at    TIMESTAMP NOT NULL DEFAULT NOW(),

  CHECK (locking_policy IN ('EXCLUSIVE', 'LOCKABLE', 'UNLOCKABLE'))
);

CREATE TABLE queue_command_errors (
  scope        BIGINT NOT NULL PRIMARY KEY,
  error_count  INT NOT NULL DEFAULT 1
);


CREATE TABLE command_archive (
  id            BIGSERIAL PRIMARY KEY,
  ca_id         BIGINT,
  command_type  TEXT NOT NULL,
  command       TEXT NOT NULL,
  scope         BIGINT,
  sync          BOOLEAN NOT NULL DEFAULT FALSE,
  inserted_at   TIMESTAMP NOT NULL,
  executiontime TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE queue_ca_commands ADD FOREIGN KEY (ca_id)
    REFERENCES certificateauthority(id) ON DELETE CASCADE;

CREATE INDEX idx_queue_commands_inserted_at_id ON queue_commands(inserted_at, id);
CREATE INDEX idx_queue_ca_commands_inserted_at_id ON queue_ca_commands(inserted_at, id);

CREATE INDEX idx_queue_ca_commands_ca_id ON queue_ca_commands(ca_id);

COMMIT;
