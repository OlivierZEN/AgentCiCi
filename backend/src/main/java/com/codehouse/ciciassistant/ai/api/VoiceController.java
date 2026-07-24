package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.AliyunAsrService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/voice")
public class VoiceController {

    private final AliyunAsrService aliyunAsrService;

    public VoiceController(AliyunAsrService aliyunAsrService) {
        this.aliyunAsrService = aliyunAsrService;
    }

    @PostMapping("/asr")
    public ApiResponse<Map<String, Object>> asr(@RequestParam("audio") MultipartFile audio) throws Exception {
        // Keep same auth boundary as chat APIs.
        String companyId = TenantContext.requireCompanyId();
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        String text = aliyunAsrService.transcribe(audio.getBytes(), audio.getContentType());
        return ApiResponse.ok(Map.of(
                "companyId", companyId,
                "text", text == null ? "" : text
        ));
    }
}

