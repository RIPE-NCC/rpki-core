UPDATE manifestentity manifest
SET needs_reissuance=true
WHERE EXISTS (
  SELECT 1 FROM published_object
  WHERE containing_manifest_id = manifest.id AND filename LIKE '%.asa' AND validity_not_after <= '2026-07-01'
);
