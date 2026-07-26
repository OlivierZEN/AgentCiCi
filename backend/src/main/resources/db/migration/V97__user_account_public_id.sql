ALTER TABLE user_account
    ADD COLUMN public_id VARCHAR(13);

ALTER TABLE user_account
    ADD CONSTRAINT chk_user_account_public_id_format
    CHECK (public_id IS NULL OR public_id ~ '^U[0-9]{4}[A-Z0-9]{8}$');

CREATE OR REPLACE FUNCTION generate_user_account_public_id(account_created_at TIMESTAMP)
RETURNS VARCHAR(13)
LANGUAGE plpgsql
AS $$
DECLARE
    alphabet CONSTANT TEXT := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    prefix TEXT := 'U' || to_char(COALESCE(account_created_at, CURRENT_TIMESTAMP), 'YYYY');
    suffix TEXT;
    candidate VARCHAR(13);
    attempt INTEGER;
    position INTEGER;
BEGIN
    -- Serialize allocation for one creation year so the NOT EXISTS check and
    -- the final unique constraint provide deterministic collision handling.
    PERFORM pg_advisory_xact_lock(hashtext(prefix));

    FOR attempt IN 1..100 LOOP
        suffix := '';
        FOR position IN 1..8 LOOP
            suffix := suffix || substr(alphabet, floor(random() * length(alphabet))::INTEGER + 1, 1);
        END LOOP;
        candidate := prefix || suffix;

        IF NOT EXISTS (SELECT 1 FROM user_account WHERE public_id = candidate) THEN
            RETURN candidate;
        END IF;
    END LOOP;

    RAISE EXCEPTION 'could not allocate a unique public user id for % after 100 attempts', prefix;
END;
$$;

UPDATE user_account
SET public_id = generate_user_account_public_id(created_at)
WHERE public_id IS NULL;

ALTER TABLE user_account
    ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE user_account
    ADD CONSTRAINT uk_user_account_public_id UNIQUE (public_id);

CREATE OR REPLACE FUNCTION assign_user_account_public_id()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.public_id IS NULL THEN
        NEW.public_id := generate_user_account_public_id(NEW.created_at);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_account_assign_public_id
BEFORE INSERT ON user_account
FOR EACH ROW
EXECUTE FUNCTION assign_user_account_public_id();

CREATE OR REPLACE FUNCTION prevent_user_account_public_id_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.public_id IS DISTINCT FROM OLD.public_id THEN
        RAISE EXCEPTION 'user_account.public_id is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_account_public_id_immutable
BEFORE UPDATE OF public_id ON user_account
FOR EACH ROW
EXECUTE FUNCTION prevent_user_account_public_id_change();
