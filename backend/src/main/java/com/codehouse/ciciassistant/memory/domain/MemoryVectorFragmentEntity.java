package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memory_vector_fragment")
public class MemoryVectorFragmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "org_id", nullable = false) private String orgId;
    @Column(name = "memory_record_id", nullable = false, unique = true) private Long memoryRecordId;
    @Column(name = "vector_id", nullable = false) private String vectorId;
    @Column(name = "redacted_text", nullable = false, columnDefinition = "TEXT") private String redactedText;
    @Column(nullable = false) private String status;
    @Column(name = "indexed_at", nullable = false) private Instant indexedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    protected MemoryVectorFragmentEntity() {}
    public MemoryVectorFragmentEntity(String orgId, Long memoryRecordId, String vectorId, String redactedText) { this.orgId=orgId; this.memoryRecordId=memoryRecordId; this.vectorId=vectorId; this.redactedText=redactedText; this.status="ACTIVE"; this.indexedAt=Instant.now(); }
    public Long getMemoryRecordId(){return memoryRecordId;}
}
