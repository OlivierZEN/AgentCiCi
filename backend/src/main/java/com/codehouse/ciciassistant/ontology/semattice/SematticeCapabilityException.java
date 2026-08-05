package com.codehouse.ciciassistant.ontology.semattice;

public class SematticeCapabilityException extends RuntimeException {

    private final String code;

    public SematticeCapabilityException(String code, String message) {
        super(message == null || message.isBlank() ? "Semattice capability failed" : message);
        this.code = code == null || code.isBlank() ? "INTERNAL" : code;
    }

    public SematticeCapabilityException(String code, String message, Throwable cause) {
        super(message == null || message.isBlank() ? "Semattice capability failed" : message, cause);
        this.code = code == null || code.isBlank() ? "INTERNAL" : code;
    }

    public String code() {
        return code;
    }
}
