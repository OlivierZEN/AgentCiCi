-- Credentials and model selection for managed tools and ASR are now resolved only
-- from published scene model routes. Remove retired per-tool secret/model fields.
UPDATE integration_app
SET config_json = (COALESCE(NULLIF(config_json, ''), '{}')::jsonb
        - 'apiKey' - 'apiBaseUrl' - 'model'
    )::text,
    updated_at = NOW()
WHERE app_code IN ('code_interpreter', 'managed_web_search', 'managed_web_extractor');

UPDATE integration_app
SET config_json = '{}'::text,
    updated_at = NOW()
WHERE app_code = 'iflytek_asr';
