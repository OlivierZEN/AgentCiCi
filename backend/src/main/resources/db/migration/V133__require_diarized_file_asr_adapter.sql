-- AI Minutes uploaded recordings require speaker diarization. Reuse the
-- governed Aliyun credential, but bind the route to the asynchronous Filetrans
-- adapter instead of the single-speaker synchronous Flash model.
UPDATE company_model_config route
   SET provider = 'aliyun-bailian',
       model_name = 'qwen-audio-3.0-asr-flash-filetrans'
  FROM model_provider_config provider
 WHERE route.company_id = provider.company_id
   AND route.scene_code = 'file-asr'
   AND provider.provider_code = 'aliyun-bailian'
   AND provider.enabled = TRUE
   AND provider.api_key <> ''
   AND (route.provider <> 'aliyun-bailian'
        OR route.model_name <> 'qwen-audio-3.0-asr-flash-filetrans');

INSERT INTO company_model_config (company_id, scene_code, provider, model_name)
SELECT DISTINCT company_id,
       'file-asr',
       'aliyun-bailian',
       'qwen-audio-3.0-asr-flash-filetrans'
  FROM model_provider_config source
 WHERE provider_code = 'aliyun-bailian'
   AND enabled = TRUE
   AND api_key <> ''
   AND NOT EXISTS (
       SELECT 1
         FROM company_model_config target
        WHERE target.company_id = source.company_id
          AND target.scene_code = 'file-asr'
   );
