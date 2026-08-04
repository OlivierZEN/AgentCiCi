package com.codehouse.ciciassistant.skill.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillResolverServiceTest {

    @Test
    void productManagerDeliveryCapabilityIsAFormalBuiltinSkillAndDefaultBinding() throws Exception {
        Field builtinField = SkillDefinitionService.class.getDeclaredField("BUILTIN_SKILLS");
        builtinField.setAccessible(true);
        List<?> builtinSkills = (List<?>) builtinField.get(null);
        String builtinDefinitions = builtinSkills.toString();

        assertThat(builtinDefinitions).contains("semattice-project-delivery-management");
        assertThat(builtinDefinitions).contains("Semattice 研发交付管理");
        assertThat(builtinDefinitions).contains("semattice_project_delivery_query");
        assertThat(builtinDefinitions).contains("semattice_project_delivery_create");
        assertThat(builtinDefinitions).contains("semattice_project_delivery_review");
        assertThat(builtinDefinitions).contains("SERVICE Principal");

        Field bindingsField = SkillDefinitionService.class.getDeclaredField("DEFAULT_AGENT_SKILLS");
        bindingsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> defaultBindings = (Map<Object, Object>) bindingsField.get(null);

        assertThat(defaultBindings).containsKey("dev-autopilot-pm");
        assertThat(defaultBindings.get("dev-autopilot-pm").toString())
                .contains("semattice-project-delivery-management")
                .contains("always-on");
    }
}
