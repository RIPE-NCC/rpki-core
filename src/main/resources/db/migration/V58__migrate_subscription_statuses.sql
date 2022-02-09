BEGIN;

UPDATE roa_alert_configuration
SET route_validity_states = regexp_replace(route_validity_states, 'INVALID', 'INVALID_ASN,INVALID_LENGTH')
WHERE route_validity_states LIKE '%INVALID%';

COMMIT;
