package com.codehouse.ciciassistant.tool.codeinterpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
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

class SandboxCodeInterpreterServiceTest {

    private FakeRepository repository;
    private IntegrationAppService integrationAppService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        objectMapper = new ObjectMapper();
        integrationAppService = new IntegrationAppService(
                repository, objectMapper, new NoopObjectProvider(),
                new SecretCipherService(""), new PlatformAccountProperties());
    }

    @Test
    void codeInterpreterConfigIsPlatformManagedEncryptedAndMasked() {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_CODE_INTERPRETER,
                true, "managed sandbox", Map.of(
                        "apiKey", "sk-ws-sensitive",
                        "apiBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "model", "qwen3.5-plus"));

        IntegrationAppEntity entity = repository.findByCompanyIdAndAppCode(
                "demo-org", IntegrationAppService.APP_CODE_CODE_INTERPRETER).orElseThrow();
        assertThat(entity.getConfigJson()).doesNotContain("sk-ws-sensitive")
                .contains("cipher").contains("iv");
        String encryptedConfig = entity.getConfigJson();
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_CODE_INTERPRETER,
                true, "managed sandbox", Map.of(
                        "apiKey", IntegrationAppService.CODE_INTERPRETER_SECRET_MASK,
                        "apiBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "model", "qwen3.5-plus"));
        assertThat(repository.findByCompanyIdAndAppCode(
                "demo-org", IntegrationAppService.APP_CODE_CODE_INTERPRETER).orElseThrow().getConfigJson())
                .isEqualTo(encryptedConfig);
        Map<String, Object> raw = integrationAppService.findRawConfig(
                "any-org", IntegrationAppService.APP_CODE_CODE_INTERPRETER).orElseThrow();
        assertThat(integrationAppService.decryptCodeInterpreterApiKey(raw)).contains("sk-ws-sensitive");

        Map<String, Object> view = integrationAppService.listPlatformManaged().stream()
                .filter(item -> IntegrationAppService.APP_CODE_CODE_INTERPRETER.equals(item.get("appCode")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked") Map<String, Object> config = (Map<String, Object>) view.get("config");
        assertThat(config.get("apiKey")).isEqualTo(IntegrationAppService.CODE_INTERPRETER_SECRET_MASK);
    }

    @Test
    void missingConfigurationFailsClosed() throws Exception {
        SandboxCodeInterpreterService service = new SandboxCodeInterpreterService(
                new SandboxCodeInterpreterClient(objectMapper), integrationAppService, objectMapper);
        JsonNode result = objectMapper.readTree(service.dispatch("org-1", "user-1", "{\"task\":\"12**3\"}"));
        assertThat(result.path("success").asBoolean()).isFalse();
        assertThat(result.path("code").asText()).isEqualTo("CODE_INTERPRETER_NOT_CONFIGURED");
    }

    @Test
    void rejectsNonAliyunAndNonHttpsApiHosts() {
        org.assertj.core.api.ThrowableAssert.ThrowingCallable http = () ->
                SandboxCodeInterpreterService.validateApiBaseUrl("http://dashscope.aliyuncs.com/v1");
        org.assertj.core.api.ThrowableAssert.ThrowingCallable metadata = () ->
                SandboxCodeInterpreterService.validateApiBaseUrl("https://169.254.169.254/latest/meta-data");
        org.assertj.core.api.ThrowableAssert.ThrowingCallable lookalike = () ->
                SandboxCodeInterpreterService.validateApiBaseUrl("https://aliyuncs.com.evil.example/v1");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(http)).isInstanceOf(IllegalArgumentException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(metadata)).isInstanceOf(IllegalArgumentException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(lookalike)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeRepository implements IntegrationAppRepository {
        private final Map<String, IntegrationAppEntity> rows = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public Optional<IntegrationAppEntity> findByCompanyIdAndAppCode(String companyId, String appCode) { return Optional.ofNullable(rows.get(companyId + "|" + appCode)); }
        @Override public List<IntegrationAppEntity> findByCompanyIdOrderByIdAsc(String companyId) { return rows.values().stream().filter(e -> companyId.equals(e.getCompanyId())).toList(); }
        @Override public List<IntegrationAppEntity> findByAppCodeAndEnabledTrueOrderByIdAsc(String appCode) { return rows.values().stream().filter(e -> appCode.equals(e.getAppCode()) && e.isEnabled()).toList(); }
        @Override public <S extends IntegrationAppEntity> S save(S entity) {
            if (entity.getId() == null) set(entity, "id", nextId++);
            if (entity.getCreatedAt() == null) set(entity, "createdAt", Instant.now());
            if (entity.getUpdatedAt() == null) set(entity, "updatedAt", Instant.now());
            rows.put(entity.getCompanyId() + "|" + entity.getAppCode(), entity); return entity;
        }
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
