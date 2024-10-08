CREATE OR REPLACE FUNCTION force_roa_prefix_deleted_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.deleted_at := now();
    RETURN NEW;
END
$$;

CREATE OR REPLACE TRIGGER delete_roa_prefix_before_insert
    BEFORE INSERT ON deleted_roaconfiguration_prefixes
    FOR EACH ROW
    WHEN (NEW.deleted_at IS NULL)
EXECUTE FUNCTION force_roa_prefix_deleted_at();

DROP TRIGGER IF EXISTS delete_roa_prefix_before_insert ON deleted_roaconfiguration_prefixes;
DROP FUNCTION IF EXISTS force_roa_prefix_deleted_at();

-- TODO This migration is to be deleted after next deployment dropping the trigger workaround

