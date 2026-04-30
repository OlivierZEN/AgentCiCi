package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogEntity;
import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuditService {

    private final PlatformAuditLogRepository repository;

    public PlatformAuditService(PlatformAuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String orgId,
                    String userId,
                    String roleCode,
                    String eventType,
                    String resourceType,
                    String resourceKey,
                    String detail) {
        repository.save(new PlatformAuditLogEntity(orgId, userId, roleCode, eventType, resourceType, resourceKey, detail));
    }

    public List<PlatformAuditLogEntity> latest(String orgId) {
        return repository.findTop100ByOrgIdOrderByIdDesc(orgId);
    }
}
