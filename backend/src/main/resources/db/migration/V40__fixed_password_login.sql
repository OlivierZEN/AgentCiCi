CREATE TABLE IF NOT EXISTS auth_password (
    id VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(256) NOT NULL,
    salt VARCHAR(128) NOT NULL,
    iterations INTEGER NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO auth_password (id, password_hash, salt, iterations, algorithm, updated_at)
SELECT
    'default',
    'AX8IB2gWmlHU6hpMFmauK33K9PiSxe5bQqDzcb9Wdsc=',
    'cici-fixed-login-v1',
    120000,
    'PBKDF2WithHmacSHA256',
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM auth_password WHERE id = 'default'
);
