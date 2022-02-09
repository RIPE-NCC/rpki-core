CREATE UNIQUE INDEX outgoing_resourcecertificate_unique_serial_idx
    ON resourcecertificate (signing_keypair_id, serial_number)
 WHERE type = 'OUTGOING';
