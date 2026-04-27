package com.codehouse.ciciassistant.tool.tavily;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link TavilyProperties} as a Spring Boot {@code @ConfigurationProperties} bean. */
@Configuration
@EnableConfigurationProperties(TavilyProperties.class)
public class TavilyAutoConfig {
}
