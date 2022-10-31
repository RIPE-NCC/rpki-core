ALTER TABLE aspaconfiguration
       DROP CONSTRAINT aspaconfiguration_unique_per_ca,
        ADD CONSTRAINT aspaconfiguration_unique_customer_asn_per_ca EXCLUDE (certificateauthority_id with =, customer_asn with =)
            DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE aspaconfiguration_provider_asns RENAME TO aspaconfiguration_providers;

ALTER TABLE aspaconfiguration_providers RENAME COLUMN asn TO provider_asn;
ALTER TABLE aspaconfiguration_providers
       DROP COLUMN prefix_type_id,
        ADD COLUMN afi_limit TEXT NOT NULL CHECK (afi_limit IN ('ANY', 'IPv4', 'IPv6'));
ALTER TABLE aspaconfiguration_providers
        ADD CONSTRAINT aspaconfiguration_provider_asns_pkey PRIMARY KEY (aspaconfiguration_id, provider_asn);
