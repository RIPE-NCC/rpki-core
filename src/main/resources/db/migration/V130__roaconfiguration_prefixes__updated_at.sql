ALTER TABLE roaconfiguration_prefixes ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT now();
--- Old records do not have a updated_at value
UPDATE roaconfiguration_prefixes SET updated_at = NULL;