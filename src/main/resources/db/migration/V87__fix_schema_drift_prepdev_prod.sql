-- Prepdev/prod only index, drop it. The `published_object_issuing_key_pair_id` index is probably good enough as a replacement.
DROP INDEX IF EXISTS idx_part_published_object;

-- These objects are only in the production database
DROP VIEW IF EXISTS public.affected_cas;
DROP TABLE IF EXISTS public.deleted_roa_prefixes_incident_2020_12_16;
DROP TABLE IF EXISTS public.resource_cache_backup;
DROP TABLE IF EXISTS public.roaconfiguration_prefixes_backup;

-- These objects exist on the current prepdev and production databases. I can't find any reference in the migrations
-- about when they were created.
DROP FUNCTION IF EXISTS public.acquire_ca_lock_for_execution(ca_id_ bigint);
DROP FUNCTION IF EXISTS public.acquire_ca_lock_for_submit(ca_id_ bigint);
DROP FUNCTION IF EXISTS public.acquire_exclusive_global_lock();
DROP FUNCTION IF EXISTS public.acquire_shared_global_lock();
DROP FUNCTION IF EXISTS public.archive_sync_ca_command(ca_id_ bigint, scope_ bigint, command_type_ text, command_ text);
DROP FUNCTION IF EXISTS public.archive_sync_command(command_type_ text, command_ text);
DROP FUNCTION IF EXISTS public.cleaup_error_counts();
DROP FUNCTION IF EXISTS public.command_by_type(command_type_ text);
DROP FUNCTION IF EXISTS public.dequeue_ca_command(id_ bigint);
DROP FUNCTION IF EXISTS public.dequeue_ca_command(id_ bigint, historic_ca_id bigint);
DROP FUNCTION IF EXISTS public.dequeue_command(id_ bigint);
DROP FUNCTION IF EXISTS public.drop_if_exists(function_name text);
DROP FUNCTION IF EXISTS public.get_command_counts();
DROP FUNCTION IF EXISTS public.get_command_counts_fast();
DROP FUNCTION IF EXISTS public.get_or_create_roa_configuration(ca_id_ bigint);
DROP FUNCTION IF EXISTS public.increment_error_count(scope_ bigint);
DROP FUNCTION IF EXISTS public.lock_ca(ca_id_ bigint);
DROP FUNCTION IF EXISTS public.lock_exclusively();
DROP FUNCTION IF EXISTS public.next_ca_command;
DROP FUNCTION IF EXISTS public.next_exclusive_command();
DROP FUNCTION IF EXISTS public.next_lockable_command();
DROP FUNCTION IF EXISTS public.next_unlockable_command();
DROP FUNCTION IF EXISTS public.release_ca_lock_for_submit(ca_id_ bigint);
DROP FUNCTION IF EXISTS public.remove_scope(scope_ bigint);
DROP FUNCTION IF EXISTS public.submit_ca_command(ca_id_ bigint, command_type_ text, command_ text, locking_policy_ text, non_conflicting_group_ text, scope_ bigint, archive_ boolean, admin_ boolean);
DROP FUNCTION IF EXISTS public.submit_command(command_type_ text, command_ text, locking_policy_ text, is_archive_ boolean, admin_ boolean);
DROP FUNCTION IF EXISTS public.submit_command_unique_by_type(command_type_ text, command_ text, locking_policy_ text, is_archive_ boolean, admin_ boolean);

DROP TABLE IF EXISTS public.next_ca_return_type;
DROP TABLE IF EXISTS public.published_object_backup;

-- Some more obsolete tables (they are empty in prod and not referenced from the code).
DROP TABLE IF EXISTS queue_ca_commands;
DROP TABLE IF EXISTS queue_commands;
DROP TABLE IF EXISTS queue_command_errors;
