package com.codehouse.ciciassistant.customerinsight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_insight_section")
public class CustomerInsightSectionEntity {

    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "section_code", nullable = false, length = 64)
    private String sectionCode;

    @Column(name = "section_group", nullable = false, length = 64)
    private String sectionGroup;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "output_json", nullable = false, columnDefinition = "TEXT")
    private String outputJson;

    @Column(name = "markdown", columnDefinition = "TEXT")
    private String markdown;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "skill_code", length = 64)
    private String skillCode;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerInsightSectionEntity() {
    }

    public CustomerInsightSectionEntity(Long projectId, String sectionCode, String sectionGroup, String title) {
        this.projectId = projectId;
        this.sectionCode = sectionCode;
        this.sectionGroup = sectionGroup;
        this.title = title;
        this.inputJson = "{}";
        this.outputJson = "{}";
        this.status = STATUS_EMPTY;
        this.aiGenerated = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getSectionCode() { return sectionCode; }
    public String getSectionGroup() { return sectionGroup; }
    public String getTitle() { return title; }
    public String getInputJson() { return inputJson; }
    public String getOutputJson() { return outputJson; }
    public String getMarkdown() { return markdown; }
    public String getStatus() { return status; }
    public boolean isAiGenerated() { return aiGenerated; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
    public String getSkillCode() { return skillCode; }
    public String getTraceId() { return traceId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void saveDraft(String inputJson, String markdown) {
        this.inputJson = inputJson == null || inputJson.isBlank() ? "{}" : inputJson;
        if (markdown != null) {
            this.markdown = markdown.isBlank() ? null : markdown.trim();
        }
        this.status = hasContent(this.markdown) || !"{}".equals(this.inputJson) ? STATUS_DRAFT : STATUS_EMPTY;
        this.aiGenerated = false;
        this.errorMessage = null;
        touch();
    }

    public void markGenerating() {
        this.status = STATUS_GENERATING;
        this.errorMessage = null;
        touch();
    }

    public void markGenerated(String inputJson,
                              String outputJson,
                              String markdown,
                              String modelProvider,
                              String modelName,
                              String skillCode,
                              String traceId) {
        this.inputJson = inputJson == null || inputJson.isBlank() ? "{}" : inputJson;
        this.outputJson = outputJson == null || outputJson.isBlank() ? "{}" : outputJson;
        this.markdown = markdown == null || markdown.isBlank() ? "模型未返回内容。" : markdown.trim();
        this.modelProvider = blankToNull(modelProvider);
        this.modelName = blankToNull(modelName);
        this.skillCode = blankToNull(skillCode);
        this.traceId = blankToNull(traceId);
        this.errorMessage = null;
        this.aiGenerated = true;
        this.status = STATUS_GENERATED;
        touch();
    }

    public void markError(String errorMessage, String traceId) {
        this.status = STATUS_ERROR;
        this.errorMessage = blankToNull(errorMessage);
        this.traceId = blankToNull(traceId);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
