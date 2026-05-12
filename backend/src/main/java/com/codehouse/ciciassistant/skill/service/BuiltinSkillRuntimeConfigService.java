package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BuiltinSkillRuntimeConfigService {

    public static final String CLOUDCC_CUSTOMIZATION_SKILL_CODE = "cloudcc-customization-expert-common";

    private final CloudccAccessTokenService cloudccAccessTokenService;

    public BuiltinSkillRuntimeConfigService(CloudccAccessTokenService cloudccAccessTokenService) {
        this.cloudccAccessTokenService = cloudccAccessTokenService;
    }

    public ResolvedBuiltinSkillRuntimeConfig resolve(SkillResolverService.ResolvedSkillContext context,
                                                     BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs,
                                                     String orgId,
                                                     String userId) {
        if (!shouldResolveCloudccRuntime(context, builtinDocs)) {
            return ResolvedBuiltinSkillRuntimeConfig.empty();
        }
        Optional<CloudccAccessTokenService.CloudccSessionContext> sessionContext =
                cloudccAccessTokenService.getSessionContext(orgId, userId);
        if (sessionContext.isEmpty()) {
            return new ResolvedBuiltinSkillRuntimeConfig(List.of(new RuntimeConfigSection(
                    CLOUDCC_CUSTOMIZATION_SKILL_CODE,
                    "",
                    false,
                    "CloudCC CRM 集成应用或当前用户 CloudCC 账号绑定不可用。"
            )));
        }
        return new ResolvedBuiltinSkillRuntimeConfig(List.of(new RuntimeConfigSection(
                CLOUDCC_CUSTOMIZATION_SKILL_CODE,
                sessionContext.get().setupSvc(),
                true,
                ""
        )));
    }

    private boolean shouldResolveCloudccRuntime(SkillResolverService.ResolvedSkillContext context,
                                                BuiltinSkillDocumentService.ResolvedBuiltinSkillDocs builtinDocs) {
        if (context == null) {
            return false;
        }
        if (CLOUDCC_CUSTOMIZATION_SKILL_CODE.equalsIgnoreCase(context.activeSkillCode())) {
            return true;
        }
        if (builtinDocs != null && builtinDocs.refs() != null
                && builtinDocs.refs().stream().anyMatch(ref ->
                CLOUDCC_CUSTOMIZATION_SKILL_CODE.equalsIgnoreCase(ref.skillCode()))) {
            return true;
        }
        return context.skills().stream()
                .filter(skill -> CLOUDCC_CUSTOMIZATION_SKILL_CODE.equalsIgnoreCase(skill.skillCode()))
                .anyMatch(skill -> {
                    String mode = skill.activationMode();
                    return mode == null || mode.isBlank()
                            || "ALWAYS".equalsIgnoreCase(mode)
                            || "always-on".equalsIgnoreCase(mode);
                });
    }

    public record ResolvedBuiltinSkillRuntimeConfig(List<RuntimeConfigSection> sections) {
        public static ResolvedBuiltinSkillRuntimeConfig empty() {
            return new ResolvedBuiltinSkillRuntimeConfig(List.of());
        }

        public boolean hasContent() {
            return sections != null && !sections.isEmpty();
        }
    }

    public record RuntimeConfigSection(
            String skillCode,
            String setupSvc,
            boolean accessTokenAvailable,
            String warning
    ) {
    }
}
