package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OfficialAccessJwksController {

    private final OfficialAccessTokenService officialAccessTokenService;

    public OfficialAccessJwksController(OfficialAccessTokenService officialAccessTokenService) {
        this.officialAccessTokenService = officialAccessTokenService;
    }

    @GetMapping("/.well-known/agentcici-oact-jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(officialAccessTokenService.jwks());
    }
}
