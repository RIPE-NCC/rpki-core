CREATE TABLE provisioning_stat
(
    id                      bigint                    NOT NULL PRIMARY KEY,
    version                 bigint                    NOT NULL,
    created_at              timestamp with time zone  NOT NULL,
    updated_at              timestamp with time zone  NOT NULL,
    non_hosted_ca_uuid      uuid                      NOT NULL,
    last_success            timestamp with time zone,
    last_failure            timestamp with time zone,
    last_failure_reason     text
);

CREATE UNIQUE INDEX provisioning_stat_non_hosted_ca_uuid_idx ON provisioning_stat(non_hosted_ca_uuid);

INSERT INTO provisioning_stat
WITH last_failure AS (
    SELECT DISTINCT ON (non_hosted_ca_uuid)
        non_hosted_ca_uuid,
        executiontime,
        summary
    FROM provisioning_audit_log
    WHERE request_message_type = 'error_response'
    ORDER BY non_hosted_ca_uuid, executiontime DESC
),
last_success AS (
    SELECT DISTINCT ON (non_hosted_ca_uuid)
        non_hosted_ca_uuid,
        executiontime
    FROM provisioning_audit_log
    WHERE request_message_type = 'issue_response'
    ORDER BY non_hosted_ca_uuid, executiontime DESC
)
SELECT
    nextval('seq_all'),
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    ca.uuid,
    t.executiontime,
    e.executiontime,
    e.summary
FROM certificateauthority ca
    LEFT JOIN last_failure e ON e.non_hosted_ca_uuid = ca.uuid
    LEFT JOIN last_success t ON t.non_hosted_ca_uuid = ca.uuid
WHERE ca.type = 'NONHOSTED';
