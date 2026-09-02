-- The chat dictation WebSocket implements the Paraformer realtime protocol.
-- Do not retain a generic realtime-audio model name in this protocol-specific route.
UPDATE company_model_config route
   SET provider = 'aliyun-bailian',
       model_name = 'paraformer-realtime-v2'
  FROM model_provider_config provider
 WHERE route.company_id = provider.company_id
   AND route.scene_code = 'voice-asr'
   AND provider.provider_code = 'aliyun-bailian'
   AND provider.enabled = TRUE
   AND provider.api_key <> ''
   AND (route.provider <> 'aliyun-bailian'
        OR route.model_name <> 'paraformer-realtime-v2');
