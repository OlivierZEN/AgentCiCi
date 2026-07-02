package com.codehouse.ciciassistant.kb.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QdrantVectorStoreClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExtractUnnamedCollectionVectorSize() throws Exception {
        assertThat(QdrantVectorStoreClient.extractCollectionVectorSize(objectMapper.readTree("""
                {
                  "result": {
                    "config": {
                      "params": {
                        "vectors": { "size": 1024, "distance": "Cosine" }
                      }
                    }
                  }
                }
                """))).contains(1024);
    }

    @Test
    void shouldExtractNamedCollectionVectorSizeWhenAllNamedVectorsMatch() throws Exception {
        assertThat(QdrantVectorStoreClient.extractCollectionVectorSize(objectMapper.readTree("""
                {
                  "result": {
                    "config": {
                      "params": {
                        "vectors": {
                          "content": { "size": 1024, "distance": "Cosine" },
                          "summary": { "size": 1024, "distance": "Cosine" }
                        }
                      }
                    }
                  }
                }
                """))).contains(1024);
    }

    @Test
    void shouldReturnEmptyWhenCollectionVectorSizeIsAmbiguous() throws Exception {
        assertThat(QdrantVectorStoreClient.extractCollectionVectorSize(objectMapper.readTree("""
                {
                  "result": {
                    "config": {
                      "params": {
                        "vectors": {
                          "content": { "size": 1024, "distance": "Cosine" },
                          "summary": { "size": 768, "distance": "Cosine" }
                        }
                      }
                    }
                  }
                }
                """))).isEmpty();
    }
}
