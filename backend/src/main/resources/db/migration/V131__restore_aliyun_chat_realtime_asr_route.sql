-- Chat dictation uses the governed Aliyun credential with its fixed realtime
-- WebSocket adapter. AI Minutes remains on the separately governed Iflytek route.
INSERT INTO company_model_config (company_id, scene_code, provider, model_name)
SELECT DISTINCT company_id, 'voice-asr', 'aliyun-bailian', 'paraformer-realtime-v2'
  FROM model_provider_config source
 WHERE provider_code = 'aliyun-bailian'
   AND enabled = TRUE
   AND api_key <> ''
   AND NOT EXISTS (
       SELECT 1
         FROM company_model_config target
        WHERE target.company_id = source.company_id
          AND target.scene_code = 'voice-asr'
   );
