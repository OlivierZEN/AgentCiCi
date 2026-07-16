package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists query audit records independently from a caller's business transaction. */
@Service
public class OntologyQueryAuditWriter {

    private final OntologyTenantPersistence persistence;

    public OntologyQueryAuditWriter(OntologyTenantPersistence persistence) {
        this.persistence = persistence;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OntologyQueryAuditEntity audit) {
        persistence.saveForCurrentOrg(audit);
        persistence.flushForCurrentOrg(audit.getOrgId());
    }
}
