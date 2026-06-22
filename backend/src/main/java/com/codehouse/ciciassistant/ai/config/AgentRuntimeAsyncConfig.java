package com.codehouse.ciciassistant.ai.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AgentRuntimeAsyncConfig {

    @Bean(name = "agentRuntimeExecutor")
    public Executor agentRuntimeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("agent-runtime-");
        executor.initialize();
        return executor;
    }
}

