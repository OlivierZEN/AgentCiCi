-- V56 used organization_size.  V94 renamed the table and primary identity
-- column, and the application contract now consistently exposes companySize.
-- Rename the persisted field as well so JPA validation matches upgraded
-- production databases without rewriting any existing values.
DO $$
BEGIN
    IF to_regclass('public.company_profile') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'company_profile'
             AND column_name = 'organization_size'
       ) THEN
        ALTER TABLE public.company_profile RENAME COLUMN organization_size TO company_size;
    END IF;
END $$;
