package com.codehouse.ciciassistant.wecom.api;

import com.codehouse.ciciassistant.wecom.service.WecomKfCallbackService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wecom/kf")
public class WecomKfCallbackController {

    private final WecomKfCallbackService callbackService;

    public WecomKfCallbackController(WecomKfCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyUrl(@RequestParam("msg_signature") String msgSignature,
                                            @RequestParam("timestamp") String timestamp,
                                            @RequestParam("nonce") String nonce,
                                            @RequestParam("echostr") String echostr,
                                            @RequestParam(value = "companyId", required = false) String companyId,
                                            @RequestParam(value = "openKfId", required = false) String openKfId) {
        return ResponseEntity.ok(callbackService.verifyUrl(msgSignature, timestamp, nonce, echostr, companyId, openKfId));
    }

    @PostMapping(value = "/callback", consumes = { MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_PLAIN_VALUE },
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> acceptCallback(@RequestParam("msg_signature") String msgSignature,
                                                 @RequestParam("timestamp") String timestamp,
                                                 @RequestParam("nonce") String nonce,
                                                 @RequestParam(value = "companyId", required = false) String companyId,
                                                 @RequestParam(value = "openKfId", required = false) String openKfId,
                                                 @RequestBody String body) {
        callbackService.acceptCallback(msgSignature, timestamp, nonce, body, companyId, openKfId);
        return ResponseEntity.ok("success");
    }
}
