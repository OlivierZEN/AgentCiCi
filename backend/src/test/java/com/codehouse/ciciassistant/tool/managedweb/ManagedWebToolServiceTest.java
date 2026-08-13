package com.codehouse.ciciassistant.tool.managedweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.ai.service.ModelInvocationResolver;
import static org.mockito.Mockito.mock;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.feishu.service.FeishuBotClientManager;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ManagedWebToolServiceTest {

    private FakeRepository repository;
    private IntegrationAppService integrationAppService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        objectMapper = new ObjectMapper();
        integrationAppService = new IntegrationAppService(repository, objectMapper, new NoopObjectProvider(),
                new SecretCipherService(""), new PlatformAccountProperties());
    }

    @Test
    void createsTwoDisabledCardsWithoutIndependentSecrets() {
        List<Map<String, Object>> initial = integrationAppService.listPlatformManaged();
        assertThat(initial).filteredOn(item -> List.of(
                        IntegrationAppService.APP_CODE_MANAGED_WEB_SEARCH,
                        IntegrationAppService.APP_CODE_MANAGED_WEB_EXTRACTOR).contains(item.get("appCode")))
                .hasSize(2).allSatisfy(item -> assertThat(item.get("enabled")).isEqualTo(false));

        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_MANAGED_WEB_SEARCH,
                true, "search", config());
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_MANAGED_WEB_EXTRACTOR,
                true, "extract", config());

        IntegrationAppEntity search = repository.findByCompanyIdAndAppCode(
                "demo-org", IntegrationAppService.APP_CODE_MANAGED_WEB_SEARCH).orElseThrow();
        IntegrationAppEntity extract = repository.findByCompanyIdAndAppCode(
                "demo-org", IntegrationAppService.APP_CODE_MANAGED_WEB_EXTRACTOR).orElseThrow();
        assertThat(search.getConfigJson()).doesNotContain("apiKey").doesNotContain("apiBaseUrl").doesNotContain("model");
        assertThat(extract.getConfigJson()).doesNotContain("apiKey").doesNotContain("apiBaseUrl").doesNotContain("model");
    }

    @Test
    void missingConfigurationFailsClosedAndCatalogHasBothSchemas() throws Exception {
        ManagedWebToolService service = new ManagedWebToolService(
                new ManagedWebToolClient(objectMapper), integrationAppService, mock(ModelInvocationResolver.class), objectMapper);
        JsonNode result = objectMapper.readTree(service.dispatch(
                "org", "user", ManagedWebToolService.TOOL_SEARCH, "{\"query\":\"latest\"}"));
        assertThat(result.path("success").asBoolean()).isFalse();
        assertThat(result.path("code").asText()).isEqualTo("MANAGED_WEB_NOT_CONFIGURED");
        assertThat(service.toolDefinition(ManagedWebToolService.TOOL_SEARCH).toString()).contains("query");
        assertThat(service.toolDefinition(ManagedWebToolService.TOOL_EXTRACT).toString()).contains("url");
    }

    @Test
    void rejectsUnsafeApiHostsAndPrivateExtractionTargets() {
        assertThat(catchThrowable(() -> ManagedWebToolService.validateApiBaseUrl(
                "http://workspace.cn-beijing.maas.aliyuncs.com/v1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> ManagedWebToolService.validateApiBaseUrl(
                "https://dashscope.aliyuncs.com/compatible-mode/v1"))).isInstanceOf(IllegalArgumentException.class);
        ManagedWebToolService.validateApiBaseUrl(
                "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");

        for (String value : List.of("http://127.0.0.1/admin", "http://10.0.0.8/", "http://169.254.169.254/",
                "http://service.local/", "https://user:pass@example.com/", "https://example.com:8443/")) {
            assertThat(catchThrowable(() -> ManagedWebToolService.validatePublicWebUrl(value)))
                    .as(value).isInstanceOf(IllegalArgumentException.class);
        }
        ManagedWebToolService.validatePublicWebUrl("https://help.aliyun.com/zh/model-studio/web-extractor");
    }

    private Map<String, Object> config() {
        return Map.of("timeoutMs", "120000", "maxInputChars", "12000");
    }

    private static final class FakeRepository implements IntegrationAppRepository {
        private final Map<String, IntegrationAppEntity> rows = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public Optional<IntegrationAppEntity> findByCompanyIdAndAppCode(String companyId, String appCode) { return Optional.ofNullable(rows.get(companyId + "|" + appCode)); }
        @Override public List<IntegrationAppEntity> findByCompanyIdOrderByIdAsc(String companyId) { return rows.values().stream().filter(e -> companyId.equals(e.getCompanyId())).toList(); }
        @Override public List<IntegrationAppEntity> findByAppCodeAndEnabledTrueOrderByIdAsc(String appCode) { return rows.values().stream().filter(e -> appCode.equals(e.getAppCode()) && e.isEnabled()).toList(); }
        @Override public <S extends IntegrationAppEntity> S save(S entity) { if (entity.getId() == null) set(entity, "id", nextId++); if (entity.getCreatedAt() == null) set(entity, "createdAt", Instant.now()); if (entity.getUpdatedAt() == null) set(entity, "updatedAt", Instant.now()); rows.put(entity.getCompanyId() + "|" + entity.getAppCode(), entity); return entity; }
        private void set(Object target, String field, Object value) { try { var f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value); } catch (Exception e) { throw new RuntimeException(e); } }
        @Override public void flush() { }
        @Override public <S extends IntegrationAppEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends IntegrationAppEntity> List<S> saveAll(Iterable<S> es) { List<S> out=new ArrayList<>(); es.forEach(e->out.add(save(e))); return out; }
        @Override public <S extends IntegrationAppEntity> List<S> saveAllAndFlush(Iterable<S> es) { return saveAll(es); }
        @Override public long count() { return rows.size(); }
        @Override public void deleteAllInBatch(Iterable<IntegrationAppEntity> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> List<S> findAll(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> List<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public Optional<IntegrationAppEntity> findById(Long id) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public List<IntegrationAppEntity> findAll() { return List.copyOf(rows.values()); }
        @Override public List<IntegrationAppEntity> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(IntegrationAppEntity e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends IntegrationAppEntity> e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { rows.clear(); }
        @Override public List<IntegrationAppEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<IntegrationAppEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> long count(org.springframework.data.domain.Example<S> e) { return 0; }
        @Override public <S extends IntegrationAppEntity> boolean exists(org.springframework.data.domain.Example<S> e) { return false; }
        @Override public <S extends IntegrationAppEntity, R> R findBy(org.springframework.data.domain.Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> q) { throw new UnsupportedOperationException(); }
    }

    private static final class NoopObjectProvider implements ObjectProvider<FeishuBotClientManager> {
        @Override public FeishuBotClientManager getObject(Object... args) { return null; }
        @Override public FeishuBotClientManager getIfAvailable() { return null; }
        @Override public FeishuBotClientManager getIfUnique() { return null; }
        @Override public FeishuBotClientManager getObject() { return null; }
        @Override public java.util.Iterator<FeishuBotClientManager> iterator() { return List.<FeishuBotClientManager>of().iterator(); }
    }
}
