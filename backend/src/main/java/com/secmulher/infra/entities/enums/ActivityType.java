package com.secmulher.infra.entities.enums;

public enum ActivityType {
    // Registradas manualmente pelos operadores
    TRIAGE(true),
    SERVICE(true),
    FOLLOW_UP(true),
    NOTE(true),

    // Geradas automaticamente pelo sistema (auditoria)
    CREATED(false),
    UPDATED(false),
    DELETED(false),
    FILE_UPLOADED(false),
    FILE_UPDATED(false),
    FILE_REMOVED(false),
    FILE_DOWNLOADED(false);

    private final boolean manual;

    ActivityType(boolean manual) {
        this.manual = manual;
    }

    public boolean isManual() {
        return manual;
    }
}
