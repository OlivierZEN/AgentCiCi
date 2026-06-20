package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_case")
public class AgentEvalCaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String PRIORITY_P0 = "P0";
    public static final String PRIORITY_SAFETY = "SAFETY";
    public static final String ASSERT_OUTPUT_CONTAINS = "OUTPUT_CONTAINS";
    public static final String ASSERT_OUTPUT_NOT_CONTAINS = "OUTPUT_NOT_CONTAINS";
    public static final String ASSERT_STATUS_EQUALS = "STATUS_EQUALS";
    public static final String ASSERT_TOOL_CALLED = "TOOL_CALLED";
    public static final String ASSERT_TOOL_NOT_CALLED = "TOOL_NOT_CALLED";
    public static final String ASSERT_RAG_USED = "RAG_USED";
    public static final String ASSERT_HANDOFF_REQUESTED = "HANDOFF_REQUESTED";
    public static final String ASSERT_SAFETY_REFUSAL = "SAFETY_REFUSAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "assertion_type", nullable = false, length = 64)
    private String assertionType;

    @Column(name = "expected_text", length = 1000)
    private String expectedText;

    @Column(name = "forbidden_text", length = 1000)
    private String forbiddenText;

    @Column(name = "expected_status", length = 64)
    private String expectedStatus;

    @Column(name = "required_tool_name", length = 128)
    private String requiredToolName;

    @Column(name = "forbidden_tool_name", length = 128)
    private String forbiddenToolName;

    @Column(name = "priority", nullable = false, length = 32)
    private String priority;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEvalCaseEntity() {
    }

    public AgentEvalCaseEntity(String orgId,
                               String agentId,
                               Long suiteId,
                               String name,
                               String inputText,
                               String assertionType,
                               String expectedText,
                               String forbiddenText,
                               String expectedStatus,
                               String requiredToolName,
                               String forbiddenToolName,
                               String priority) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.agentId = agentId;
        this.suiteId = suiteId;
        this.name = name;
        this.inputText = inputText;
        this.assertionType = assertionType;
        this.expectedText = expectedText;
        this.forbiddenText = forbiddenText;
        this.expectedStatus = expectedStatus;
        this.requiredToolName = requiredToolName;
        this.forbiddenToolName = forbiddenToolName;
        this.priority = priority == null || priority.isBlank() ? "P1" : priority;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public String getAgentId() { return agentId; }

    public Long getSuiteId() { return suiteId; }

    public String getName() { return name; }

    public String getInputText() { return inputText; }

    public String getAssertionType() { return assertionType; }

    public String getExpectedText() { return expectedText; }

    public String getForbiddenText() { return forbiddenText; }

    public String getExpectedStatus() { return expectedStatus; }

    public String getRequiredToolName() { return requiredToolName; }

    public String getForbiddenToolName() { return forbiddenToolName; }

    public String getPriority() { return priority; }

    public String getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
