package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.MeetingMinutesService;
import com.codehouse.ciciassistant.ai.service.MeetingMinutesService.MeetingMinutesResult;
import com.codehouse.ciciassistant.ai.service.MeetingMinutesService.TranscriptSegment;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/meeting-minutes")
public class MeetingMinutesController {

    private final MeetingMinutesService meetingMinutesService;

    public MeetingMinutesController(MeetingMinutesService meetingMinutesService) {
        this.meetingMinutesService = meetingMinutesService;
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

    public record MeetingMinutesRequest(
            String title,
            @NotEmpty List<TranscriptSegment> transcript
    ) {
    }
}
