package com.codehouse.ciciassistant.kb.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KbAsyncConfig {

    public static final String KB_INDEX_QUEUE = "kb.index.queue";

    @Bean
    public Queue kbIndexQueue() {
        return new Queue(KB_INDEX_QUEUE, true);
    }
}
