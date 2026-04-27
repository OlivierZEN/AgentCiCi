package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.kb.config.KbAsyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kb.indexing.mode", havingValue = "mq")
public class KbIndexWorker {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public KbIndexWorker(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = KbAsyncConfig.KB_INDEX_QUEUE)
    public void consume(String payload) throws Exception {
        KbIndexTask task = objectMapper.readValue(payload, KbIndexTask.class);
        knowledgeBaseService.indexDocument(task.orgId(), task.documentId());
    }
}
