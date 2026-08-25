package com.codehouse.ciciassistant.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SkillPackageServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillPackageService service = new SkillPackageService(
            null, null, null, null, null, null, null, null, objectMapper
    );

    @Test
    void shouldEnforceServerOwnedManifestFieldsAfterModelStandardization() throws Exception {
        SkillDefinitionEntity skill = skill();
        SkillVersionEntity version = version();

        String normalized = service.normalizeModelManifest("""
                {
                  "format": "universal-skill-package@1.0",
                  "formatVersion": "v1",
                  "packageId": "model-overwrite",
                  "name": "模型改名",
                  "sourceSystem": "model",
                  "skill": {
                    "code": "model-overwrite",
                    "versionNo": 999,
                    "publishStatus": "DRAFT"
                  },
                  "exportNotes": "保留非身份扩展字段"
                }
                """, skill, version, "org-admin");

        JsonNode manifest = objectMapper.readTree(normalized);
        assertThat(manifest.path("format").asText()).isEqualTo("universal-skill-package");
        assertThat(manifest.path("formatVersion").asText()).isEqualTo("1.0");
        assertThat(manifest.path("packageId").asText()).isEqualTo("contact-email-generator");
        assertThat(manifest.path("name").asText()).isEqualTo("网络爬取公司联系人邮箱");
        assertThat(manifest.path("sourceSystem").asText()).isEqualTo("cc-cici-assistant");
        assertThat(manifest.path("exportedBy").asText()).isEqualTo("org-admin");
        assertThat(manifest.path("skill").path("code").asText()).isEqualTo("contact-email-generator");
        assertThat(manifest.path("skill").path("versionNo").asInt()).isEqualTo(2);
        assertThat(manifest.path("skill").path("publishStatus").asText()).isEqualTo("PUBLISHED");
        assertThat(manifest.path("exportNotes").asText()).isEqualTo("保留非身份扩展字段");
    }

    @Test
    void shouldRejectNonObjectModelManifest() {
        assertThatThrownBy(() -> service.normalizeModelManifest("[]", skill(), version(), "org-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
    }

    private SkillDefinitionEntity skill() {
        return new SkillDefinitionEntity(
                "org-1",
                "contact-email-generator",
                "网络爬取公司联系人邮箱",
                "用于检索联系人并生成候选邮箱。",
                false,
                true,
                "prompt",
                "spec",
                "",
                "",
                "升级人工",
                "结构化邮箱列表",
                "MEDIUM",
                SkillSourceType.TENANT_CUSTOM,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                null,
                null
        );
    }

    private SkillVersionEntity version() {
        SkillVersionEntity version = new SkillVersionEntity(
                "org-1",
                137L,
                2,
                "spec",
                "DECLARATIVE",
                "PUBLISH",
                "{}",
                "",
                "prompt",
                "{}",
                "",
                "",
                "MEDIUM",
                "compiled",
                "",
                "PUBLISHED"
        );
        version.applyGovernance("发布 v2", "compiled", "PUBLISH", "org-admin", true, "ACTIVE_RECENT", null, null);
        return version;
    }
}
