package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.codehouse.ciciassistant.kb.service.*;
import com.codehouse.ciciassistant.memory.domain.*;
import com.codehouse.ciciassistant.memory.service.*;
import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class MemorySemanticRetrievalServiceTest {
    @Test void returnsOnlyRecordsAlreadyAuthorizedByTheRelationalContext() throws Exception {
        EmbeddingService embeddings=mock(EmbeddingService.class); VectorStoreClient vectors=mock(VectorStoreClient.class); MemoryRecordRepository records=mock(MemoryRecordRepository.class); ExternalMemoryContextService contexts=mock(ExternalMemoryContextService.class);
        MemoryRecordEntity allowed=record(7L), denied=record(8L);
        when(contexts.loadContext(any(), any(), any(), any())).thenReturn(new ExternalMemoryContextService.MemoryContext(1L, "", List.of(allowed)));
        when(embeddings.embedForScene("org", "memory-embedding", null, "query")).thenReturn(List.of(1f));
        when(vectors.search(any())).thenReturn(List.of(new VectorSearchHit("a", "agent-memory", 7L, 7L, 0, "x", .9), new VectorSearchHit("b", "agent-memory", 8L, 8L, 0, "y", .8)));
        when(records.findById(7L)).thenReturn(Optional.of(allowed)); when(records.findById(8L)).thenReturn(Optional.of(denied));
        MemorySemanticRetrievalService service=new MemorySemanticRetrievalService(embeddings, vectors, mock(MemoryVectorFragmentRepository.class), records, contexts, new SecurityRedactionService());
        List<MemoryRecordEntity> result=service.retrieve(new ExternalMemoryContextService.ExternalMemoryContext("org", "app", "c", "s", "EXTERNAL_USER", "VERIFIED"), "agent", Set.of(), "query", 5);
        assertThat(result).containsExactly(allowed); verify(records, never()).findById(8L);
    }
    @Test void redactsBeforeEmbeddingAndPropagatesRouteOrIndexFailure() throws Exception {
        EmbeddingService embeddings=mock(EmbeddingService.class); VectorStoreClient vectors=mock(VectorStoreClient.class); MemoryVectorFragmentRepository fragments=mock(MemoryVectorFragmentRepository.class);
        MemoryRecordEntity record=record(7L); Field content=MemoryRecordEntity.class.getDeclaredField("content"); content.setAccessible(true); content.set(record, "email a@b.com token=secret");
        when(embeddings.embedForScene(eq("org"), eq("memory-embedding"), isNull(), contains("[email]"))).thenReturn(List.of(1f)); when(vectors.upsert(any())).thenThrow(new IllegalStateException("offline"));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new MemorySemanticRetrievalService(embeddings, vectors, fragments, mock(MemoryRecordRepository.class), mock(ExternalMemoryContextService.class), new SecurityRedactionService()).index(record)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("offline");
        verify(embeddings).embedForScene(eq("org"), eq("memory-embedding"), isNull(), contains("[email]")); verify(fragments, never()).save(any());
    }
    private static MemoryRecordEntity record(Long id) throws Exception { MemoryRecordEntity value=new MemoryRecordEntity("org", 1L, "SUBJECT_SHARED", null, "PREFERENCE", "text", "ACTIVE", "NORMAL", BigDecimal.ONE, Instant.now(), null, "HUMAN", "[]"); Field field=MemoryRecordEntity.class.getDeclaredField("id"); field.setAccessible(true); field.set(value,id); return value; }
}
