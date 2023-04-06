UPDATE keypair SET status = 'PENDING' WHERE status = 'NEW' AND ca_id IN (SELECT id FROM certificateauthority WHERE type = 'ALL_RESOURCES');
DELETE FROM keypair WHERE status = 'NEW';
