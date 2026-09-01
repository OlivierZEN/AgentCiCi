-- The chat microphone remains on Aliyun's realtime protocol.  Move only the
-- previously governed Iflytek adapter to the dedicated AI Minutes ASR scene.
UPDATE company_model_config target
   SET provider = source.provider,
       model_name = source.model_name
  FROM company_model_config source
 WHERE target.company_id = source.company_id
   AND target.scene_code = 'meeting-realtime-asr'
   AND source.scene_code = 'voice-asr'
   AND source.provider = 'iflytek_asr';

INSERT INTO company_model_config (company_id, scene_code, provider, model_name)
SELECT source.company_id, 'meeting-realtime-asr', source.provider, source.model_name
  FROM company_model_config source
 WHERE source.scene_code = 'voice-asr'
   AND source.provider = 'iflytek_asr'
   AND NOT EXISTS (
       SELECT 1
         FROM company_model_config target
        WHERE target.company_id = source.company_id
          AND target.scene_code = 'meeting-realtime-asr'
   );

DELETE FROM company_model_config
 WHERE scene_code = 'voice-asr'
   AND provider = 'iflytek_asr';
