package com.codehouse.ciciassistant.ops.service;

import com.codehouse.ciciassistant.ops.domain.AuditLogEntity;
import com.codehouse.ciciassistant.ops.domain.AuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String orgId, String userId, String eventType, String detail) {
        repository.save(new AuditLogEntity(orgId, userId, eventType, detail));
    }

    public List<AuditLogEntity> latest(String orgId) {
        return repository.findTop50ByOrgIdOrderByIdDesc(orgId);
    }
}
