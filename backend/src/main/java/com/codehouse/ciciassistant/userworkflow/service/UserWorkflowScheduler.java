package com.codehouse.ciciassistant.userworkflow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserWorkflowScheduler {

    private final UserWorkflowService userWorkflowService;

    public UserWorkflowScheduler(UserWorkflowService userWorkflowService) {
        this.userWorkflowService = userWorkflowService;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 20000)
    public void triggerDueWorkflows() {
        userWorkflowService.triggerDueWorkflows();
    }
}
