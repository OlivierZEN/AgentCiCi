package com.codehouse.ciciassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CiciAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(CiciAssistantApplication.class, args);
    }
}
