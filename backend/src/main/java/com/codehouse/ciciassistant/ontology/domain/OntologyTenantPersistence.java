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
        String currentCompanyId = TenantContext.requireCompanyId();
        if (!currentCompanyId.equals(entity.getCompanyId())) {
            throw new ForbiddenException(
                    "Ontology entity company does not match the current company");
        }

        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    @Transactional
    public void flushForCurrentOrg(String companyId) {
        String currentCompanyId = TenantContext.requireCompanyId();
        if (!currentCompanyId.equals(companyId)) {
            throw new ForbiddenException(
                    "Ontology company does not match the current company");
        }
        entityManager.flush();
    }

    @Transactional
    public void deleteForCurrentOrg(String companyId, Runnable scopedDelete) {
        String currentCompanyId = TenantContext.requireCompanyId();
        if (!currentCompanyId.equals(companyId)) {
            throw new ForbiddenException(
                    "Ontology company does not match the current company");
        }
        Objects.requireNonNull(scopedDelete, "scopedDelete").run();
    }
}
