package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.AliyunAsrService;
import com.codehouse.ciciassistant.ai.service.AliyunAsrService.FileTranscriptionResult;
import com.codehouse.ciciassistant.ai.service.MeetingMinutesService;
import com.codehouse.ciciassistant.ai.service.MeetingMinutesService.MeetingMinutesResult;
import com.codehouse.ciciassistant.ai.service.MeetingMinutesService.TranscriptSegment;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai/meeting-minutes")
public class MeetingMinutesController {

    private final MeetingMinutesService meetingMinutesService;
    private final AliyunAsrService aliyunAsrService;

    public MeetingMinutesController(MeetingMinutesService meetingMinutesService,
                                    AliyunAsrService aliyunAsrService) {
        this.meetingMinutesService = meetingMinutesService;
        this.aliyunAsrService = aliyunAsrService;
    }

    @PostMapping("/summary")
    public ApiResponse<Map<String, Object>> summarize(@Valid @RequestBody MeetingMinutesRequest request) {
        String orgId = TenantContext.requireOrgId();
        TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        MeetingMinutesResult result = meetingMinutesService.summarize(orgId, request.title(), request.transcript());
        return ApiResponse.ok(Map.of(
                "orgId", orgId,
                "summary", result.summary(),
                "skillCode", result.skillCode(),
                "skillName", result.skillName(),
                "segmentCount", request.transcript().size()
        ));
    }

    @PostMapping(value = "/transcribe-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> transcribeFile(@RequestParam("file") MultipartFile file) throws Exception {
        String orgId = TenantContext.requireOrgId();
        TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        FileTranscriptionResult result = aliyunAsrService.transcribeMeetingFile(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );
        return ApiResponse.ok(Map.of(
                "orgId", orgId,
                "transcript", result.transcript(),
                "segmentCount", result.transcript().size(),
                "file", Map.of(
                        "name", result.filename(),
                        "extension", result.extension(),
                        "contentType", result.contentType(),
                        "size", result.size()
                ),
                "model", Map.of(
                        "provider", "aliyun-bailian",
                        "modelName", result.model(),
                        "taskId", result.taskId()
                )
        ));
    }

    public record MeetingMinutesRequest(
            String title,
            @NotEmpty List<TranscriptSegment> transcript
    ) {
    }
}
