package com.codehouse.ciciassistant.ontology.api;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.service.SemanticQueryService;
import com.codehouse.ciciassistant.ontology.service.SemanticQueryService.SemanticQuery;
import com.codehouse.ciciassistant.ontology.service.SemanticQueryService.QueryPlan;
import com.codehouse.ciciassistant.ontology.service.SemanticQueryService.QueryResult;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/semantic-query")
public class SemanticQueryController {

    private final SemanticQueryService semanticQueries;

    public SemanticQueryController(SemanticQueryService semanticQueries) {
        this.semanticQueries = semanticQueries;
    }

    @PostMapping("/explain")
    public ApiResponse<QueryPlan> explain(@RequestBody SemanticQuery query) {
        Context context = requireOrganizationMember();
        requireContract(query);
        return ApiResponse.ok(semanticQueries.explain(context.orgId(), context.userId(), query));
    }

    @PostMapping("/execute")
    public ApiResponse<QueryResult> execute(@RequestBody SemanticQuery query) {
        Context context = requireOrganizationMember();
        requireContract(query);
        return ApiResponse.ok(semanticQueries.execute(context.orgId(), context.userId(), query));
    }

    private Context requireOrganizationMember() {
        boolean organizationRole = TenantContext.getRoles().stream().anyMatch(role ->
                RoleCodes.OWNER.equals(role)
                        || RoleCodes.ORG_ADMIN.equals(role)
                        || RoleCodes.ORG_USER.equals(role));
        String orgId = TenantContext.getOrgId().orElse(null);
        String userId = TenantContext.getUserId().orElse(null);
        if (!organizationRole
                || orgId == null
                || orgId.isBlank()
                || userId == null
                || userId.isBlank()
                || TenantContext.getTokenType().filter("platform"::equals).isPresent()) {
            throw new ForbiddenException("ORGANIZATION_MEMBER_REQUIRED");
        }
        return new Context(orgId, userId);
    }

    private void requireContract(SemanticQuery query) {
        if (query == null
                || query.ontologyKey() == null
                || query.version() == null
                || query.concept() == null) {
            throw new IllegalArgumentException("QUERY_CONTRACT_INVALID");
        }
    }

    private record Context(String orgId, String userId) {
    }
}
