---
kind: test-plan
feature_id: FEAT-054
title: Bailian real audio transcription smoke test plan
status: active
owner_role: fullstack-agent
task_ids: TASK-134
updated_at: 2026-05-25T01:50:00Z
updated_by: MANAGER-001
---

# FEAT-054 - Bailian Real Audio Transcription Smoke Test Plan

## Purpose

Validate AI 听记 local audio upload against 百炼 Fun-ASR with speaker diarization using the real local file:

- `/Users/owenmacbook/Downloads/中粮高层访谈.m4a`
- Observed locally: `m4a`, AAC, 1 channel, 48000 Hz, about `842.15s`, about `7.1MB`

Do not commit test audio, response JSON, access tokens, API keys, or generated chunks.

## Current Findings

- Local login with `POST /auth/password/login` succeeded with HTTP 200.
- The original 7.1MB file passed the application multipart size limit after raising Spring upload caps to 256MB.
- Full-file upload currently fails on this network before ASR task submission: `百炼文件上传失败：Connection reset`.
- A generated 1-second WAV reached 百炼 ASR and returned `ASR_RESPONSE_HAVE_NO_WORDS`, confirming small-file OSS upload and task submission work.
- A generated 7MB WAV also resets during 百炼 temporary OSS upload, and direct `curl` upload to the returned OSS host resets around 30 seconds. This points to network/temporary OSS upload path instability for larger uploads.
- `ffmpeg` was installed locally with Homebrew and successfully split the real audio into 60-second chunks of about 480KB each.

## Prerequisites

- Local backend dependencies are running: Postgres, Redis, RabbitMQ.
- `backend/src/main/resources/application-local.yml` has a valid 百炼 API key configured locally.
- `ffmpeg` is available. If missing on macOS:

```bash
/opt/homebrew/bin/brew install ffmpeg
```

## Start Backend

Use a longer poll window for provider ASR tasks:

```bash
cd /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/backend
mvn -Dmaven.repo.local=../.m2 spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--app.voice.aliyun.file-asr-poll-attempts=300
```

Stop it after testing:

```bash
kill <spring_boot_pid>
```

## Login

```bash
curl -sS -o /tmp/cici_login.json -w "%{http_code}\n" \
  -X POST http://localhost:8080/auth/password/login \
  -H 'Content-Type: application/json' \
  -d '{"orgId":"demo-org","mobile":"18611892001","password":"szyd1234"}'
```

Expected: `200`.

Extract token without printing it:

```bash
TOKEN=$(node -e "const fs=require('fs'); const j=JSON.parse(fs.readFileSync('/tmp/cici_login.json','utf8')); process.stdout.write(j.data.token)")
```

## Whole-File Test

Run this first on the new network:

```bash
curl -sS -o /tmp/cici_real_transcribe_result.json -w "%{http_code}\n" \
  -X POST http://localhost:8080/ai/meeting-minutes/transcribe-file \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@/Users/owenmacbook/Downloads/中粮高层访谈.m4a
```

Success criteria:

- HTTP `200`
- JSON `success: true`
- `data.segmentCount > 0`
- `data.model.modelName` is `fun-asr`
- `data.transcript[]` includes speaker fields such as `speakerId` / `speakerName`

Summary check without dumping the transcript:

```bash
node - <<'NODE'
const fs = require('fs');
const j = JSON.parse(fs.readFileSync('/tmp/cici_real_transcribe_result.json', 'utf8'));
const transcript = j.data?.transcript || [];
const speakers = [...new Set(transcript.map((x) => x.speakerName || x.speakerId).filter(Boolean))];
console.log(JSON.stringify({
  success: j.success,
  message: j.message,
  segmentCount: j.data?.segmentCount,
  speakers,
  file: j.data?.file,
  model: j.data?.model
}, null, 2));
NODE
```

If it returns `百炼文件上传失败：Connection reset`, continue with chunked testing.

## Chunked Fallback Test

Create 60-second chunks:

```bash
CHUNK_DIR=/tmp/cici-real-audio-ffmpeg-chunks-$(date +%s)
mkdir -p "$CHUNK_DIR"
/opt/homebrew/bin/ffmpeg -hide_banner -loglevel error \
  -i /Users/owenmacbook/Downloads/中粮高层访谈.m4a \
  -f segment -segment_time 60 -reset_timestamps 1 -c copy \
  "$CHUNK_DIR/chunk-%03d.m4a"
ls -lh "$CHUNK_DIR"
```

Expected: about 15 chunks; most chunks around 480KB.

Upload chunks sequentially:

```bash
RESULT_DIR=/tmp/cici-bailian-real-test-results-$(date +%s)
mkdir -p "$RESULT_DIR"

for f in "$CHUNK_DIR"/chunk-*.m4a; do
  name=$(basename "$f" .m4a)
  echo "uploading $name"
  curl -sS -o "$RESULT_DIR/$name.json" -w "$name %{http_code}\n" \
    -X POST http://localhost:8080/ai/meeting-minutes/transcribe-file \
    -H "Authorization: Bearer $TOKEN" \
    -F file=@"$f"
done
```

Summarize all chunk results without dumping transcripts:

```bash
RESULT_DIR="$RESULT_DIR" node - <<'NODE'
const fs = require('fs');
const dir = process.env.RESULT_DIR;
const rows = fs.readdirSync(dir)
  .filter((name) => name.endsWith('.json'))
  .sort()
  .map((name) => {
    const j = JSON.parse(fs.readFileSync(`${dir}/${name}`, 'utf8'));
    const transcript = j.data?.transcript || [];
    const speakers = [...new Set(transcript.map((x) => x.speakerName || x.speakerId).filter(Boolean))];
    return {
      chunk: name,
      success: j.success,
      message: j.message,
      segmentCount: j.data?.segmentCount || 0,
      speakers
    };
  });
console.log(JSON.stringify(rows, null, 2));
console.log('passedChunks=', rows.filter((x) => x.success).length, 'totalChunks=', rows.length);
NODE
```

Chunked success criteria:

- Most or all chunks return HTTP `200` and `success: true`.
- Chunks with silence can return `ASR_RESPONSE_HAVE_NO_WORDS`; note them separately.
- Successful chunks include transcript segments with speaker metadata.

## UI Smoke

If the whole-file backend test passes on the new network, verify the product surface:

1. Start frontend with `npm run dev` in `frontend/`.
2. Login with the same local account.
3. Open AI 应用 / AI 听记.
4. Click `导入录音`.
5. Select `/Users/owenmacbook/Downloads/中粮高层访谈.m4a`.
6. Confirm transcript is populated and `生成纪要` is enabled.

The UI currently uploads whole files. It does not automatically chunk and merge.

## Known Limits

- App upload cap: 256MB.
- 百炼 Fun-ASR supports long files, but diarization is recommended for recordings no longer than 2 hours.
- This implementation does not yet auto-split files. Chunked testing is a manual validation workaround.
- Production-grade auto-splitting requires media probing, chunk creation, chunk task orchestration, timestamp offset merging, and cross-chunk speaker reconciliation.

## Evidence To Record

Record only summaries in `.claw/test-report.md`:

- Network used.
- Whole-file HTTP status and summary.
- If chunked: chunk count, passed chunk count, failed chunk messages, speaker count summary.
- Whether UI whole-file upload passed.

Do not paste API keys, bearer tokens, raw transcript dumps, or private interview content into Git-tracked files.
