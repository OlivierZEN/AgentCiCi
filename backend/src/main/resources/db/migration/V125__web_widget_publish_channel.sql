UPDATE embed_app_definition
SET supported_sources_json = '["cloudcc","website"]',
    versioned_sdk_url = '/sdk/sisi@1.1.0.js',
    version = '1.1.0',
    updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'sisi';
