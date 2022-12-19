ALTER TABLE commandaudit
        ADD COLUMN ca_name TEXT,
        ADD COLUMN ca_uuid UUID;

UPDATE commandaudit
   SET ca_name = ca.name,
       ca_uuid = ca.uuid
  FROM certificateauthority ca
 WHERE ca_id = ca.id;
