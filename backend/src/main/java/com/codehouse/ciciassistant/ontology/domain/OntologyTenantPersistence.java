package com.codehouse.ciciassistant.ontology.domain;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OntologyTenantPersistence {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public <T extends OntologyTenantEntity> T saveForCurrentOrg(T entity) {
        Objects.requireNonNull(entity, "entity");
        String currentOrgId = TenantContext.requireOrgId();
        if (!currentOrgId.equals(entity.getOrgId())) {
            throw new ForbiddenException(
                    "Ontology entity organization does not match the current organization");
        }

        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    @Transactional
    public void flushForCurrentOrg(String orgId) {
        String currentOrgId = TenantContext.requireOrgId();
        if (!currentOrgId.equals(orgId)) {
            throw new ForbiddenException(
                    "Ontology organization does not match the current organization");
        }
        entityManager.flush();
    }

    @Transactional
    public void deleteForCurrentOrg(String orgId, Runnable scopedDelete) {
        String currentOrgId = TenantContext.requireOrgId();
        if (!currentOrgId.equals(orgId)) {
            throw new ForbiddenException(
                    "Ontology organization does not match the current organization");
        }
        Objects.requireNonNull(scopedDelete, "scopedDelete").run();
    }
}
