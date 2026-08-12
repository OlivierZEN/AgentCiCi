package com.codehouse.ciciassistant.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillDefinitionServicePlatformSnapshotTest {

    @Test
    void republishesAChangedBuiltinInsteadOfKeepingTheLegacySnapshot() {
        String companyId = "org00000000000000001";
        String skillCode = "semattice-project-delivery-management";
        SkillDefinitionRepository definitions = mock(SkillDefinitionRepository.class);
        SkillVersionRepository versions = mock(SkillVersionRepository.class);
        SkillApiToolService apiTools = mock(SkillApiToolService.class);
        SkillDefinitionEntity legacy = new SkillDefinitionEntity(
                companyId, skillCode, "旧研发交付技能", "旧描述", true, true,
                "旧提示：要求用户填写严重度、优先级和复现步骤。", "旧规格",
                "semattice_project_delivery_query", null, null, null, "HIGH",
                SkillSourceType.PLATFORM_STANDARD, SkillVisibility.VISIBLE, SkillEditPolicy.CONFIGURABLE,
                SkillBindingPolicy.OPTIONAL, SkillUpdatePolicy.AUTO, skillCode, null);
        ReflectionTestUtils.setField(legacy, "id", 31L);
        legacy.markPublished(41L, "system");
        SkillVersionEntity oldVersion = new SkillVersionEntity(
                companyId, 31L, 1, "旧规格", "policy", "manual", null, null,
                "旧提示：要求用户填写严重度、优先级和复现步骤。", "{}",
                "semattice_project_delivery_query", null, "HIGH", "", "", "PUBLISHED");
        ReflectionTestUtils.setField(oldVersion, "id", 41L);

        when(definitions.findByCompanyIdAndSkillCode(companyId, skillCode)).thenReturn(Optional.of(legacy));
        when(definitions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findByIdAndCompanyId(41L, companyId)).thenReturn(Optional.of(oldVersion));
        when(versions.findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(companyId, 31L)).thenReturn(Optional.of(oldVersion));
        when(versions.findByCompanyIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(companyId, 31L))
                .thenReturn(List.of());
        AtomicLong nextId = new AtomicLong(42L);
        when(versions.save(any())).thenAnswer(invocation -> {
            SkillVersionEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", nextId.getAndIncrement());
            }
            return saved;
        });

        SkillDefinitionService service = spy(new SkillDefinitionService(
                definitions,
                mock(AgentSkillBindingRepository.class),
                mock(SkillPromptAssembler.class),
                versions,
                new SpecCompilerService(),
                new ObjectMapper(),
                mock(PlatformSkillTemplateRepository.class),
                mock(AgentWorkflowSkillRefRepository.class),
                apiTools,
                mock(FileBackedBuiltinSkillSyncService.class)
        ));
        doNothing().when(service).ensurePhaseOneDefaults(companyId);

        Long publishedId = service.ensurePublishedPlatformSkillVersion(companyId, skillCode);

        assertThat(publishedId).isEqualTo(42L);
        assertThat(legacy.getPromptFragment())
                .contains("不得把工程调查表转嫁给普通用户")
                .doesNotContain("旧提示");
        assertThat(legacy.getCurrentPublishedVersionId()).isEqualTo(42L);
        verify(apiTools).publishApisForVersion(
                eq(companyId), eq(legacy), any(SkillVersionEntity.class), isNull());
    }
}
