package com.codehouse.ciciassistant.tool.tavily;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.feishu.service.FeishuBotClientManager;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.tool.tavily.dto.TavilyExtractRequest;
import com.codehouse.ciciassistant.tool.tavily.dto.TavilySearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Hand-rolled fakes — no Mockito — so the test works on recent JDKs where inline mock
 * instrumentation can fail. Covers parameter normalization, error mapping, and the
 * "api key not configured" path for {@link TavilyToolService}.
 */
class TavilyToolServiceTest {

    private FakeTavilyClient fakeClient;
    private FakeIntegrationAppRepository fakeRepo;
    private IntegrationAppService integrationAppService;
    private TavilyToolService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        fakeClient = new FakeTavilyClient();
        fakeRepo = new FakeIntegrationAppRepository();
        mapper = new ObjectMapper();
        SecretCipherService cipher = new SecretCipherService("");
        integrationAppService = new IntegrationAppService(
                fakeRepo, mapper, new NoopObjectProvider(), cipher, new PlatformAccountProperties());
        TavilyProperties props = new TavilyProperties(
                "https://api.tavily.com", 5, "basic", "general",
                "none", "markdown", 20_000, 10, Duration.ofSeconds(10));
        service = new TavilyToolService(fakeClient, props, integrationAppService, mapper);
    }

    @Test
    void toolDefinitionsListsSearchAndExtract() {
        List<Map<String, Object>> defs = service.toolDefinitions();
        assertThat(defs).hasSize(2);
        assertThat(nameOf(defs.get(0))).isEqualTo("tavily_search");
        assertThat(nameOf(defs.get(1))).isEqualTo("tavily_extract");
    }

    @Test
    void searchReturnsNotConfiguredWhenIntegrationMissing() throws Exception {
        String out = service.dispatch("org-1", "user-1", "tavily_search", "{\"query\":\"hello\"}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("code").asText()).isEqualTo("TAVILY_NOT_CONFIGURED");
    }

    @Test
    void extractReturnsNotConfiguredWhenIntegrationMissing() throws Exception {
        String out = service.dispatch("org-1", "user-1", "tavily_extract",
                "{\"urls\":[\"https://example.com\"]}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("code").asText()).isEqualTo("TAVILY_NOT_CONFIGURED");
    }

    @Test
    void searchClampsMaxResultsAndNormalizesEnums() throws Exception {
        primeApiKey("tvly-test");
        Map<String, Object> fake = new LinkedHashMap<>();
        fake.put("answer", "hello");
        fake.put("results", List.of(Map.of("title", "t", "url", "https://a", "content", "c", "score", 0.9)));
        fakeClient.nextSearch = TavilyClient.TavilyCallResult.ok(fake, 200, 42L);
        String out = service.dispatch("org-1", "user-1", "tavily_search",
                "{\"query\":\"q\",\"max_results\":999,\"search_depth\":\"nope\",\"topic\":\"news\"}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isTrue();
        assertThat(node.path("resultCount").asInt()).isEqualTo(1);
        TavilySearchRequest sent = fakeClient.lastSearch.get();
        assertThat(sent).isNotNull();
        assertThat(sent.maxResults()).isEqualTo(20);
        assertThat(sent.searchDepth()).isEqualTo("basic");
        assertThat(sent.topic()).isEqualTo("news");
    }

    @Test
    void searchPropagatesUpstreamError() throws Exception {
        primeApiKey("tvly-test");
        fakeClient.nextSearch = new TavilyClient.TavilyCallResult<>(
                false, null, 503, 70L, "TAVILY_UPSTREAM_ERROR", "upstream down");
        String out = service.dispatch("org-1", "user-1", "tavily_search", "{\"query\":\"q\"}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("code").asText()).isEqualTo("TAVILY_UPSTREAM_ERROR");
    }

    @Test
    void extractTrimsTooManyUrls() throws Exception {
        primeApiKey("tvly-test");
        fakeClient.nextExtract = TavilyClient.TavilyCallResult.ok(Map.of("results", List.of()), 200, 10L);
        StringBuilder urls = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) urls.append(',');
            urls.append("\"https://example.com/").append(i).append("\"");
        }
        urls.append(']');
        String out = service.dispatch("org-1", "user-1", "tavily_extract",
                "{\"urls\":" + urls + "}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isTrue();
        TavilyExtractRequest sent = fakeClient.lastExtract.get();
        assertThat(sent).isNotNull();
        assertThat(sent.urls()).hasSize(20);
    }

    @Test
    void extractRejectsEmptyUrlArray() throws Exception {
        primeApiKey("tvly-test");
        String out = service.dispatch("org-1", "user-1", "tavily_extract", "{\"urls\":[]}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isFalse();
        assertThat(node.path("code").asText()).isEqualTo("TAVILY_BAD_REQUEST");
    }

    @Test
    void searchTruncatesQueryOver400Chars() throws Exception {
        primeApiKey("tvly-test");
        fakeClient.nextSearch = TavilyClient.TavilyCallResult.ok(Map.of("results", List.of()), 200, 5L);
        String longQuery = "q".repeat(600);
        String out = service.dispatch("org-1", "user-1", "tavily_search",
                "{\"query\":\"" + longQuery + "\"}");
        JsonNode node = mapper.readTree(out);
        assertThat(node.path("success").asBoolean()).isTrue();
        assertThat(fakeClient.lastSearch.get().query().length()).isEqualTo(400);
    }

    @Test
    void apiKeyIsEncryptedAtRestAndMaskedOnRead() {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_TAVILY,
                true, "tavily", Map.of("apiKey", "tvly-real-key"));
        // Ciphertext envelope persisted in the row, not plaintext
        IntegrationAppEntity row = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_TAVILY).orElseThrow();
        assertThat(row.getConfigJson()).doesNotContain("tvly-real-key");
        assertThat(row.getConfigJson()).contains("cipher").contains("iv");
        // Runtime resolve returns the plaintext back
        String resolved = service.resolveApiKey("org-1");
        assertThat(resolved).isEqualTo("tvly-real-key");
        // Public view masks the key
        Map<String, Object> view = integrationAppService.listPlatformManaged().stream()
                .filter(v -> "tavily".equals(v.get("appCode")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) view.get("config");
        assertThat(config.get("apiKey")).isEqualTo(IntegrationAppService.API_KEY_MASK);
    }

    @Test
    void updatingWithMaskPreservesStoredCiphertext() {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_TAVILY,
                true, "tavily", Map.of("apiKey", "tvly-real-key"));
        String firstConfig = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_TAVILY).orElseThrow().getConfigJson();
        // Subsequent save that passes the mask back should NOT overwrite the ciphertext.
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_TAVILY,
                true, "tavily", Map.of("apiKey", IntegrationAppService.API_KEY_MASK));
        String secondConfig = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_TAVILY).orElseThrow().getConfigJson();
        assertThat(secondConfig).contains("cipher").contains("iv");
        assertThat(secondConfig).doesNotContain("tvly-****");
        // Plaintext is still resolvable.
        assertThat(service.resolveApiKey("org-1")).isEqualTo("tvly-real-key");
    }

    @Test
    void iflytekAsrSecretIsEncryptedMaskedAndDecryptable() {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_IFLYTEK_ASR,
                true, "iflytek", Map.of(
                        "appId", "iflytek-app",
                        "accessKeyId", "iflytek-access-key",
                        "accessKeySecret", "iflytek-secret"
                ));

        IntegrationAppEntity row = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_IFLYTEK_ASR).orElseThrow();
        assertThat(row.getConfigJson()).doesNotContain("iflytek-secret");
        assertThat(row.getConfigJson()).contains("cipher").contains("iv");

        Map<String, Object> raw = integrationAppService.findRawConfig("org-1",
                IntegrationAppService.APP_CODE_IFLYTEK_ASR).orElseThrow();
        assertThat(integrationAppService.decryptIflytekAccessKeySecret(raw)).contains("iflytek-secret");

        Map<String, Object> view = integrationAppService.listPlatformManaged().stream()
                .filter(v -> IntegrationAppService.APP_CODE_IFLYTEK_ASR.equals(v.get("appCode")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) view.get("config");
        assertThat(config.get("accessKeySecret")).isEqualTo(IntegrationAppService.IFLYTEK_SECRET_MASK);
        assertThat(config.get("realtimeUrl")).isEqualTo("wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1");
        assertThat(config.get("lang")).isEqualTo("autodialect");
        assertThat(config.get("domain")).isEqualTo("com");
    }

    @Test
    void iflytekAsrMaskPreservesStoredCiphertext() {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_IFLYTEK_ASR,
                true, "iflytek", Map.of(
                        "appId", "iflytek-app",
                        "accessKeyId", "iflytek-access-key",
                        "accessKeySecret", "iflytek-secret"
                ));
        String firstConfig = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_IFLYTEK_ASR).orElseThrow().getConfigJson();

        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_IFLYTEK_ASR,
                true, "iflytek", Map.of(
                        "appId", "iflytek-app",
                        "accessKeyId", "iflytek-access-key",
                        "accessKeySecret", IntegrationAppService.IFLYTEK_SECRET_MASK
                ));
        String secondConfig = fakeRepo.findByOrgIdAndAppCode("demo-org",
                IntegrationAppService.APP_CODE_IFLYTEK_ASR).orElseThrow().getConfigJson();

        assertThat(secondConfig).isEqualTo(firstConfig);
        Map<String, Object> raw = integrationAppService.findRawConfig("org-1",
                IntegrationAppService.APP_CODE_IFLYTEK_ASR).orElseThrow();
        assertThat(integrationAppService.decryptIflytekAccessKeySecret(raw)).contains("iflytek-secret");
    }

    // ---------------------------------------------------------------------------------------------

    private String nameOf(Map<String, Object> toolDef) {
        Map<?, ?> function = (Map<?, ?>) toolDef.get("function");
        return String.valueOf(function.get("name"));
    }

    private void primeApiKey(String plain) {
        integrationAppService.updatePlatformManaged(IntegrationAppService.APP_CODE_TAVILY,
                true, "tavily", Map.of("apiKey", plain));
    }

    /**
     * Minimal in-memory fake for {@link IntegrationAppRepository}. Only the methods actually
     * used by {@link IntegrationAppService} are implemented; all other JpaRepository defaults
     * throw {@link UnsupportedOperationException}.
     */
    private static final class FakeIntegrationAppRepository implements IntegrationAppRepository {
        private final Map<String, IntegrationAppEntity> byKey = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public Optional<IntegrationAppEntity> findByOrgIdAndAppCode(String orgId, String appCode) {
            return Optional.ofNullable(byKey.get(orgId + "|" + appCode));
        }

        @Override
        public List<IntegrationAppEntity> findByOrgIdOrderByIdAsc(String orgId) {
            List<IntegrationAppEntity> out = new ArrayList<>();
            for (IntegrationAppEntity e : byKey.values()) {
                if (orgId.equals(e.getOrgId())) out.add(e);
            }
            return out;
        }

        @Override
        public List<IntegrationAppEntity> findByAppCodeAndEnabledTrueOrderByIdAsc(String appCode) {
            List<IntegrationAppEntity> out = new ArrayList<>();
            for (IntegrationAppEntity e : byKey.values()) {
                if (appCode.equals(e.getAppCode()) && e.isEnabled()) out.add(e);
            }
            return out;
        }

        @Override
        public <S extends IntegrationAppEntity> S save(S entity) {
            if (entity.getId() == null) {
                try {
                    java.lang.reflect.Field idField = IntegrationAppEntity.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(entity, nextId++);
                    java.lang.reflect.Field createdAt = IntegrationAppEntity.class.getDeclaredField("createdAt");
                    createdAt.setAccessible(true);
                    if (createdAt.get(entity) == null) createdAt.set(entity, Instant.now());
                    java.lang.reflect.Field updatedAt = IntegrationAppEntity.class.getDeclaredField("updatedAt");
                    updatedAt.setAccessible(true);
                    if (updatedAt.get(entity) == null) updatedAt.set(entity, Instant.now());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            byKey.put(entity.getOrgId() + "|" + entity.getAppCode(), entity);
            return entity;
        }

        // ---- unused JpaRepository defaults below ----
        @Override public void flush() { }
        @Override public <S extends IntegrationAppEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends IntegrationAppEntity> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<IntegrationAppEntity> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getOne(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public IntegrationAppEntity getReferenceById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> List<S> saveAll(Iterable<S> entities) { List<S> out = new ArrayList<>(); for (S e : entities) out.add(save(e)); return out; }
        @Override public Optional<IntegrationAppEntity> findById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public List<IntegrationAppEntity> findAll() { throw new UnsupportedOperationException(); }
        @Override public List<IntegrationAppEntity> findAllById(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
        @Override public long count() { return byKey.size(); }
        @Override public void deleteById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public void delete(IntegrationAppEntity entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends IntegrationAppEntity> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<IntegrationAppEntity> findAll(org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<IntegrationAppEntity> findAll(org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> long count(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity> boolean exists(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IntegrationAppEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
    }

    /** Simple extensible fake that records the last call and returns a pre-set result. */
    private static class FakeTavilyClient extends TavilyClient {
        TavilyCallResult<Map<String, Object>> nextSearch = TavilyCallResult.ok(Map.of(), 200, 0L);
        TavilyCallResult<Map<String, Object>> nextExtract = TavilyCallResult.ok(Map.of(), 200, 0L);
        final AtomicReference<TavilySearchRequest> lastSearch = new AtomicReference<>();
        final AtomicReference<TavilyExtractRequest> lastExtract = new AtomicReference<>();

        FakeTavilyClient() {
            super(new TavilyProperties(
                    "https://api.tavily.com", 5, "basic", "general",
                    "none", "markdown", 20_000, 10, Duration.ofSeconds(10)),
                    new ObjectMapper());
        }

        @Override
        public TavilyCallResult<Map<String, Object>> search(String apiKey, TavilySearchRequest body) {
            lastSearch.set(body);
            return nextSearch;
        }

        @Override
        public TavilyCallResult<Map<String, Object>> extract(String apiKey, TavilyExtractRequest body) {
            lastExtract.set(body);
            return nextExtract;
        }
    }

    private static final class NoopObjectProvider implements ObjectProvider<FeishuBotClientManager> {
        @Override public FeishuBotClientManager getObject(Object... args) { return null; }
        @Override public FeishuBotClientManager getObject() { return null; }
        @Override public FeishuBotClientManager getIfAvailable() { return null; }
        @Override public FeishuBotClientManager getIfUnique() { return null; }
    }
}
