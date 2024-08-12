UPDATE roaconfiguration_prefixes
SET maximum_length = 32 - sb.effective_length FROM (SELECT (log(prefix_end - prefix_start + 1) / log(2)) as effective_length,
                                                                                       asn,
                                                                                       prefix_type_id,
                                                                                       prefix_start,
                                                                                       prefix_end
                                                                                FROM roaconfiguration_prefixes
                                                                                WHERE
                                                                                    maximum_length IS NULL
                                                                                    AND
                                                                                    prefix_type_id = 1
                                                                                ) as sb
WHERE roaconfiguration_prefixes.maximum_length IS NULL
  AND roaconfiguration_prefixes.asn = sb.asn
  AND roaconfiguration_prefixes.prefix_type_id = sb.prefix_type_id
  AND roaconfiguration_prefixes.prefix_start = sb.prefix_start
  AND roaconfiguration_prefixes.prefix_end = sb.prefix_end;

UPDATE roaconfiguration_prefixes
SET maximum_length = 128 - sb.effective_length FROM (SELECT (log(prefix_end - prefix_start + 1) / log(2)) as effective_length,
                                                                                          asn,
                                                                                          prefix_type_id,
                                                                                          prefix_start,
                                                                                          prefix_end
                                                                                   FROM roaconfiguration_prefixes
                                                                                   WHERE
                                                                                       maximum_length IS NULL
                                                                                       AND
                                                                                       prefix_type_id = 2
                                                                                   ) as sb
WHERE roaconfiguration_prefixes.maximum_length IS NULL
  AND roaconfiguration_prefixes.asn = sb.asn
  AND roaconfiguration_prefixes.prefix_type_id = sb.prefix_type_id
  AND roaconfiguration_prefixes.prefix_start = sb.prefix_start
  AND roaconfiguration_prefixes.prefix_end = sb.prefix_end;

ALTER TABLE roaconfiguration_prefixes ALTER COLUMN maximum_length SET NOT NULL;

CREATE INDEX roaconfiguration_prefixes_roaconfiguration_id ON roaconfiguration_prefixes(roaconfiguration_id);

