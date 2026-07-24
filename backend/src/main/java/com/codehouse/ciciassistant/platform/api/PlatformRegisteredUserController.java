package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.PlatformRegisteredUserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/platform/registered-users")
@RequirePlatformRole
public class PlatformRegisteredUserController {

    private final PlatformRegisteredUserService registeredUserService;

    public PlatformRegisteredUserController(PlatformRegisteredUserService registeredUserService) {
        this.registeredUserService = registeredUserService;
    }

    @GetMapping
    public ApiResponse<PlatformRegisteredUserService.RegisteredUserPage> listRegisteredUsers(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(registeredUserService.listRegisteredUsers(query, page, pageSize));
    }
}
