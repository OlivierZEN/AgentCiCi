package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.SystemApiCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/system-apis")
@RequirePlatformRole
public class PlatformSystemApiController {

    private final SystemApiCatalogService catalogService;

    public PlatformSystemApiController(SystemApiCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<SystemApiCatalogService.CatalogView> catalog() {
        return ApiResponse.ok(catalogService.catalog());
    }
}
