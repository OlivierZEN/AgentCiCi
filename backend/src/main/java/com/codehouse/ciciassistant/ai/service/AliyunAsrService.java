package com.codehouse.ciciassistant.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AliyunAsrService {

    static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of(
            "aac", "amr", "avi", "flac", "flv", "m4a", "mkv", "mov", "mp3", "mp4",
            "mpeg", "ogg", "opus", "wav", "webm", "wma", "wmv"
    );

    private final RestClient compatibleRestClient;
    private final RestClient dashscopeRestClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String asrModel;
    private final String fileAsrModel;
    private final int fileAsrPollAttempts;
    private final long fileAsrPollIntervalMillis;
    private static final Duration OSS_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration OSS_READ_TIMEOUT = Duration.ofSeconds(120);

    public AliyunAsrService(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            @Value("${app.model.aliyun.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
                            @Value("${app.voice.aliyun.dashscope-url:https://dashscope.aliyuncs.com}") String dashscopeUrl,
                            @Value("${app.model.aliyun.api-key:}") String apiKey,
                            @Value("${app.voice.aliyun.asr-model:qwen3-asr-flash}") String asrModel,
                            @Value("${app.voice.aliyun.file-asr-model:fun-asr}") String fileAsrModel,
                            @Value("${app.voice.aliyun.file-asr-poll-attempts:60}") int fileAsrPollAttempts,
                            @Value("${app.voice.aliyun.file-asr-poll-interval-ms:2000}") long fileAsrPollIntervalMillis) {
        this.compatibleRestClient = restClientBuilder.baseUrl(baseUrl).build();
        this.dashscopeRestClient = restClientBuilder.baseUrl(dashscopeUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.asrModel = asrModel;
        this.fileAsrModel = fileAsrModel;
        this.fileAsrPollAttempts = Math.max(1, fileAsrPollAttempts);
        this.fileAsrPollIntervalMillis = Math.max(200, fileAsrPollIntervalMillis);
    }

    public String transcribe(byte[] audioBytes, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Aliyun ASR API key is not configured");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio bytes are empty");
        }

        String mime = normalizeMime(contentType);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", asrModel);
        payload.put("temperature", 0);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are an ASR engine. Return transcript text only."),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "input_audio", "input_audio", Map.of("data", dataUrl)),
                        Map.of("type", "text", "text", "请将音频转写为文字，仅返回识别文本。")
                ))
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = compatibleRestClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalArgumentException("Aliyun ASR returned empty response");
        }
        return parseTranscript(response);
    }

    public FileTranscriptionResult transcribeMeetingFile(byte[] fileBytes, String originalFilename, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Aliyun ASR API key is not configured");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        FileType fileType = validateSupportedFile(originalFilename, contentType);
        String safeFilename = safeFilename(originalFilename, fileType.extension());
        String temporaryUrl = uploadToDashscopeTemporaryStorage(fileBytes, safeFilename, fileType.contentType());
        String taskId = submitFileTranscriptionTask(temporaryUrl);
        JsonNode taskOutput = waitForFileTranscription(taskId);
        JsonNode resultNode = downloadTranscriptionResult(taskOutput);
        List<MeetingFileTranscriptSegment> segments = parseFileTranscript(resultNode);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("百炼语音识别未返回可用转写内容");
        }
        return new FileTranscriptionResult(
                fileAsrModel,
                taskId,
                safeFilename,
                fileType.extension(),
                fileType.contentType(),
                fileBytes.length,
                segments
        );
    }

    static FileType validateSupportedFile(String originalFilename, String contentType) {
        String extension = extensionOf(originalFilename);
        if (extension.isBlank() || !SUPPORTED_FILE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持以下音视频格式：" + supportedFileExtensionsText());
        }
        return new FileType(extension, normalizeUploadedMime(contentType, extension));
    }

    static List<MeetingFileTranscriptSegment> parseFileTranscript(JsonNode resultNode) {
        List<MeetingFileTranscriptSegment> segments = new ArrayList<>();
        JsonNode transcripts = resultNode.path("transcripts");
        if (transcripts.isArray()) {
            for (JsonNode transcript : transcripts) {
                JsonNode sentences = transcript.path("sentences");
                if (sentences.isArray()) {
                    for (JsonNode sentence : sentences) {
                        String text = sentence.path("text").asText("").trim();
                        if (text.isBlank()) {
                            continue;
                        }
                        String speakerId = normalizeSpeakerId(sentence.path("speaker_id"));
                        segments.add(new MeetingFileTranscriptSegment(
                                speakerId,
                                speakerName(speakerId),
                                text,
                                longOrNull(sentence.path("begin_time")),
                                longOrNull(sentence.path("end_time"))
                        ));
                    }
                }
            }
        }
        if (!segments.isEmpty()) {
            return mergeAdjacentSegments(segments);
        }
        String text = resultNode.path("text").asText("").trim();
        if (text.isBlank() && transcripts.isArray() && !transcripts.isEmpty()) {
            text = transcripts.get(0).path("text").asText("").trim();
        }
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new MeetingFileTranscriptSegment("1", "发言人 1", text, null, null));
    }

    private String uploadToDashscopeTemporaryStorage(byte[] fileBytes, String filename, String contentType) {
        Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadPolicyResponse = dashscopeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/uploads")
                            .queryParam("action", "getPolicy")
                            .queryParam("model", fileAsrModel)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            response = uploadPolicyResponse;
        } catch (RestClientException e) {
            throw new IllegalArgumentException("百炼文件上传凭证获取失败：" + e.getMessage(), e);
        }
        JsonNode data = objectMapper.valueToTree(response).path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("百炼文件上传凭证获取失败");
        }

        String key = data.path("upload_dir").asText("") + "/" + UUID.randomUUID() + "-" + filename;
        Map<String, String> fields = new HashMap<>();
        fields.put("OSSAccessKeyId", data.path("oss_access_key_id").asText(""));
        fields.put("Signature", data.path("signature").asText(""));
        fields.put("policy", data.path("policy").asText(""));
        fields.put("x-oss-object-acl", data.path("x_oss_object_acl").asText("private"));
        fields.put("x-oss-forbid-overwrite", data.path("x_oss_forbid_overwrite").asText("true"));
        fields.put("key", key);
        fields.put("success_action_status", "200");
        postMultipartToOss(data.path("upload_host").asText(""), fields, fileBytes, filename, contentType);
        return "oss://" + key;
    }

    private static void postMultipartToOss(String uploadHost,
                                           Map<String, String> fields,
                                           byte[] fileBytes,
                                           String filename,
                                           String contentType) {
        if (uploadHost == null || uploadHost.isBlank()) {
            throw new IllegalArgumentException("百炼文件上传地址为空");
        }
        String boundary = "----CiCiDashscopeUpload" + UUID.randomUUID();
        byte[] fieldParts = buildFieldParts(boundary, fields);
        byte[] fileHeader = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] closing = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        long contentLength = (long) fieldParts.length + fileHeader.length + fileBytes.length + closing.length;

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(uploadHost).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout((int) OSS_CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) OSS_READ_TIMEOUT.toMillis());
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
            connection.setFixedLengthStreamingMode(contentLength);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(fieldParts);
                output.write(fileHeader);
                output.write(fileBytes);
                output.write(closing);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("百炼文件上传失败，HTTP " + status + ": " + readResponseBody(connection));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("百炼文件上传失败：" + e.getMessage(), e);
        }
    }

    private static byte[] buildFieldParts(String boundary, Map<String, String> fields) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                output.write(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                        + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("百炼文件上传表单构造失败", e);
        }
    }

    private static String readResponseBody(HttpURLConnection connection) {
        try (var input = connection.getErrorStream() == null ? connection.getInputStream() : connection.getErrorStream()) {
            if (input == null) {
                return "";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private String submitFileTranscriptionTask(String temporaryUrl) {
        Map<String, Object> payload = Map.of(
                "model", fileAsrModel,
                "input", Map.of("file_urls", List.of(temporaryUrl)),
                "parameters", Map.of(
                        "channel_id", List.of(0),
                        "diarization_enabled", true
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> response = dashscopeRestClient.post()
                .uri("/api/v1/services/audio/asr/transcription")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
                .header("X-DashScope-OssResourceResolve", "enable")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
        String taskId = objectMapper.valueToTree(response).path("output").path("task_id").asText("");
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("百炼语音识别任务提交失败");
        }
        return taskId;
    }

    private JsonNode waitForFileTranscription(String taskId) {
        for (int attempt = 0; attempt < fileAsrPollAttempts; attempt++) {
            if (attempt > 0) {
                sleepBeforePolling();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = dashscopeRestClient.get()
                    .uri("/api/v1/tasks/{taskId}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            JsonNode output = objectMapper.valueToTree(response).path("output");
            String status = output.path("task_status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                return output;
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                throw new IllegalArgumentException("百炼语音识别任务失败：" + firstFailureMessage(output));
            }
        }
        throw new IllegalArgumentException("百炼语音识别任务超时，请稍后重试");
    }

    private JsonNode downloadTranscriptionResult(JsonNode taskOutput) {
        JsonNode results = taskOutput.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalArgumentException("百炼语音识别没有返回结果文件");
        }
        JsonNode result = results.get(0);
        if (!"SUCCEEDED".equalsIgnoreCase(result.path("subtask_status").asText(""))) {
            throw new IllegalArgumentException("百炼语音识别文件解析失败：" + result.path("message").asText("未知错误"));
        }
        String resultUrl = result.path("transcription_url").asText("");
        if (resultUrl.isBlank()) {
            throw new IllegalArgumentException("百炼语音识别没有返回转写结果地址");
        }
        String body = downloadUrlBody(resultUrl);
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalArgumentException("百炼语音识别结果解析失败", e);
        }
    }

    private static String downloadUrlBody(String resultUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(resultUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) OSS_CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) OSS_READ_TIMEOUT.toMillis());
            int status = connection.getResponseCode();
            String body = readResponseBody(connection);
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("百炼语音识别结果下载失败，HTTP " + status + ": " + body);
            }
            return body;
        } catch (IOException e) {
            throw new IllegalArgumentException("百炼语音识别结果下载失败：" + e.getMessage(), e);
        }
    }

    private String parseTranscript(Map<String, Object> response) {
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalArgumentException("Aliyun ASR returned no choices");
        }
        JsonNode msg = choices.get(0).path("message");
        JsonNode content = msg.path("content");
        if (content.isTextual()) {
            return content.asText("").trim();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText())) {
                    sb.append(part.path("text").asText(""));
                } else if (part.has("text")) {
                    sb.append(part.path("text").asText(""));
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "audio/webm";
        }
        String v = contentType.trim().toLowerCase();
        if (!v.startsWith("audio/")) {
            return "audio/webm";
        }
        return v;
    }

    private static String normalizeUploadedMime(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType.trim().toLowerCase();
        }
        return switch (extension) {
            case "mp3", "mpeg" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "webm" -> "audio/webm";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            default -> "audio/" + extension;
        };
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String name = originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static String safeFilename(String originalFilename, String extension) {
        String fallback = "meeting-audio." + extension;
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String name = originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isBlank() ? fallback : name;
    }

    private static String normalizeSpeakerId(JsonNode speakerNode) {
        if (speakerNode == null || speakerNode.isMissingNode() || speakerNode.isNull()) {
            return "1";
        }
        String raw = speakerNode.asText("").trim();
        if (raw.isBlank()) {
            return "1";
        }
        if (raw.matches("\\d+")) {
            int value = Integer.parseInt(raw);
            return String.valueOf(value + 1);
        }
        return raw;
    }

    private static String speakerName(String speakerId) {
        return "发言人 " + (speakerId == null || speakerId.isBlank() ? "1" : speakerId);
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.canConvertToLong() ? node.asLong() : null;
    }

    private static List<MeetingFileTranscriptSegment> mergeAdjacentSegments(List<MeetingFileTranscriptSegment> segments) {
        List<MeetingFileTranscriptSegment> merged = new ArrayList<>();
        for (MeetingFileTranscriptSegment segment : segments) {
            if (merged.isEmpty()) {
                merged.add(segment);
                continue;
            }
            MeetingFileTranscriptSegment previous = merged.get(merged.size() - 1);
            if (!previous.speakerId().equals(segment.speakerId())) {
                merged.add(segment);
                continue;
            }
            merged.set(merged.size() - 1, new MeetingFileTranscriptSegment(
                    previous.speakerId(),
                    previous.speakerName(),
                    previous.text() + (shouldJoinWithoutSpace(previous.text(), segment.text()) ? "" : " ") + segment.text(),
                    previous.startMs(),
                    segment.endMs() == null ? previous.endMs() : segment.endMs()
            ));
        }
        return merged;
    }

    private static boolean shouldJoinWithoutSpace(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        String leftTail = left.substring(left.length() - 1);
        String rightHead = right.substring(0, 1);
        return leftTail.matches("[\\u4e00-\\u9fff]") || rightHead.matches("[\\u4e00-\\u9fff]");
    }

    private String firstFailureMessage(JsonNode output) {
        JsonNode results = output.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                String message = result.path("message").asText("");
                if (!message.isBlank()) {
                    return message;
                }
            }
        }
        return output.path("message").asText("未知错误");
    }

    private void sleepBeforePolling() {
        try {
            Thread.sleep(fileAsrPollIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("百炼语音识别任务被中断", e);
        }
    }

    public static String supportedFileExtensionsText() {
        return String.join(", ", SUPPORTED_FILE_EXTENSIONS.stream().sorted().toList());
    }

    public record FileType(String extension, String contentType) {
    }

    public record MeetingFileTranscriptSegment(String speakerId, String speakerName, String text, Long startMs, Long endMs) {
    }

    public record FileTranscriptionResult(
            String model,
            String taskId,
            String filename,
            String extension,
            String contentType,
            long size,
            List<MeetingFileTranscriptSegment> transcript
    ) {
        public List<MeetingFileTranscriptSegment> transcript() {
            return transcript == null ? List.of() : transcript;
        }
    }
}
