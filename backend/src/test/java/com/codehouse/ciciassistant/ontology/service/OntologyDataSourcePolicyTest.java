package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OntologyDataSourcePolicyTest {

    private OntologyDataSourcePolicy policy;

    @BeforeEach
    void setUp() {
        OntologyDataSourceAdapter connector = mock(OntologyDataSourceAdapter.class);
        when(connector.supports(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    OntologyDataSourceAdapter.DataSourceConfig source = invocation.getArgument(0);
                    return source.type() == OntologyDocument.SourceType.CONNECTOR
                            && "approved-adapter".equals(source.adapterKey());
                });
        when(connector.publicConfigKeys())
                .thenReturn(Set.of("adapterKey", "objectPrefix"));
        policy = new OntologyDataSourcePolicy(new ObjectMapper(), List.of(connector));
    }

    @Test
    void acceptsBoundedInlineSampleStoredSeparatelyFromPublicConfig() {
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                null,
                "delivery-source",
                "交付样例",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                "{}",
                "{\"projects\":[{\"id\":\"p-1\",\"name\":\"交付平台\"}]}" );

        assertThatCode(() -> policy.validate(source)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInlineSampleHiddenInConfigAndMalformedSampleShapes() {
        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "delivery-source", "交付样例", OntologyDocument.SourceType.INLINE_SAMPLE,
                "{\"projects\":[]}", "{\"projects\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATA_SOURCE_CONFIG_INVALID");

        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "delivery-source", "交付样例", OntologyDocument.SourceType.INLINE_SAMPLE,
                "{}", "[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");

        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "delivery-source", "交付样例", OntologyDocument.SourceType.INLINE_SAMPLE,
                "{}", "{\"projects\":[\"not-a-row\"]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INLINE_SAMPLE_ROW_MUST_BE_OBJECT");
    }

    @Test
    void rejectsSecretsUrlsAndUnapprovedConnectorAdapters() {
        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "external-source", "外部数据", OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"approved-adapter\",\"apiToken\":\"secret\"}", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATA_SOURCE_CONFIG_SECRET_FORBIDDEN");

        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "external-source", "外部数据", OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"approved-adapter\",\"endpoint\":\"https://example.invalid\"}", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATA_SOURCE_CONFIG_URL_FORBIDDEN");

        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "external-source", "外部数据", OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"unknown\"}", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATA_SOURCE_ADAPTER_NOT_ALLOWED");

        assertThatThrownBy(() -> policy.validate(new OntologyDocument.DataSource(
                null, "external-source", "外部数据", OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"approved-adapter\",\"auth\":\"Bearer secret\"}", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATA_SOURCE_CONFIG_FIELD_NOT_ALLOWED");

        assertThatCode(() -> policy.validate(new OntologyDocument.DataSource(
                null, "external-source", "外部数据", OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"approved-adapter\",\"objectPrefix\":\"A\"}", null)))
                .doesNotThrowAnyException();
    }
}
