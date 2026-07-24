-- The V1-V93 schema used org/org_id for the top-level tenant.  This is not an
-- organization-tree identifier: it is the cross-product company identity.
-- Keep the identifier values intact and rename only schema metadata.
DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT table_schema, table_name
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND column_name = 'org_id'
         ORDER BY table_name
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I RENAME COLUMN org_id TO company_id',
            item.table_schema,
            item.table_name
        );
    END LOOP;

    IF to_regclass('public.org') IS NOT NULL THEN
        ALTER TABLE public.org RENAME TO company;
    END IF;
    IF to_regclass('public.organization_member') IS NOT NULL THEN
        ALTER TABLE public.organization_member RENAME TO company_member;
    END IF;
    IF to_regclass('public.organization_profile') IS NOT NULL THEN
        ALTER TABLE public.organization_profile RENAME TO company_profile;
    END IF;
    IF to_regclass('public.organization_retention_policy') IS NOT NULL THEN
        ALTER TABLE public.organization_retention_policy RENAME TO company_retention_policy;
    END IF;
    IF to_regclass('public.organization_purge_job') IS NOT NULL THEN
        ALTER TABLE public.organization_purge_job RENAME TO company_purge_job;
    END IF;
    IF to_regclass('public.organization_export_job') IS NOT NULL THEN
        ALTER TABLE public.organization_export_job RENAME TO company_export_job;
    END IF;
    IF to_regclass('public.org_model_config') IS NOT NULL THEN
        ALTER TABLE public.org_model_config RENAME TO company_model_config;
    END IF;
    IF to_regclass('public.org_embed_app_config') IS NOT NULL THEN
        ALTER TABLE public.org_embed_app_config RENAME TO company_embed_app_config;
    END IF;
END $$;

-- ORG denoted the all-members principal of the top-level tenant.  It is not a
-- future organization-tree principal, so make persisted access grants use the
-- same company vocabulary as the surrounding identity contract.
-- The V60 constraint permits the legacy value only, so replace it before
-- rewriting existing production grants.  This must precede the UPDATE: live
-- systems can contain the default COMPANY-wide grant seeded by V60.
ALTER TABLE agent_access_grant DROP CONSTRAINT IF EXISTS ck_agent_access_principal_type;
UPDATE agent_access_grant SET principal_type = 'COMPANY' WHERE principal_type = 'ORG';
ALTER TABLE agent_access_grant
    ADD CONSTRAINT ck_agent_access_principal_type
    CHECK (principal_type IN ('COMPANY', 'USER', 'SYSTEM_ROLE', 'GROUP', 'CUSTOM_ROLE', 'DEPARTMENT'));
UPDATE kb_access_grant SET principal_type = 'COMPANY' WHERE principal_type = 'ORG';

-- PostgreSQL keeps dependent indexes and constraints valid when their table or
-- column is renamed. Rename their identifiers as well so schema inspection no
-- longer presents a competing org-level tenant vocabulary.
DO $$
DECLARE
    item RECORD;
    next_name TEXT;
BEGIN
    FOR item IN
        SELECT conrelid::regclass AS relation_name, conname
          FROM pg_constraint
         WHERE connamespace = 'public'::regnamespace
           AND (conname LIKE '%org%' OR conname LIKE '%organization%')
    LOOP
        next_name := replace(replace(item.conname, 'organization', 'company'), 'org', 'company');
        IF next_name <> item.conname
           AND NOT EXISTS (
               SELECT 1 FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace
                  AND conrelid = item.relation_name::oid
                  AND conname = next_name
           ) THEN
            EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', item.relation_name, item.conname, next_name);
        END IF;
    END LOOP;

    FOR item IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind = 'i'
           AND (c.relname LIKE '%org%' OR c.relname LIKE '%organization%')
    LOOP
        next_name := replace(replace(item.relname, 'organization', 'company'), 'org', 'company');
        IF next_name <> item.relname
           AND to_regclass(format('public.%I', next_name)) IS NULL THEN
            EXECUTE format('ALTER INDEX public.%I RENAME TO %I', item.relname, next_name);
        END IF;
    END LOOP;
END $$;
