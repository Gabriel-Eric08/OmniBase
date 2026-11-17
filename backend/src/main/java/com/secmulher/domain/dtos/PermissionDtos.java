package com.secmulher.domain.dtos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.secmulher.infra.entities.Permission;
import com.secmulher.infra.entities.enums.ConfidentialityLevel;

import jakarta.validation.constraints.NotNull;

public final class PermissionDtos {
    private PermissionDtos() {}

    // allowedColumns: {"read": [...], "write": [...]}; sem a chave, todas as colunas são liberadas
    public record PermissionRequest(
        @NotNull UUID userId,
        @NotNull UUID categoryId,
        Boolean canRead,
        Boolean canCreate,
        Boolean canUpdate,
        Boolean canDelete,
        Map<String, Object> allowedColumns,
        ConfidentialityLevel maxConfidentiality
    ) {}

    public record PermissionResponse(
        UUID id,
        UUID userId,
        UUID categoryId,
        Boolean canRead,
        Boolean canCreate,
        Boolean canUpdate,
        Boolean canDelete,
        Map<String, Object> allowedColumns,
        ConfidentialityLevel maxConfidentiality,
        LocalDateTime createdAt
    ) {
        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(
                permission.getId(),
                permission.getUser().getId(),
                permission.getCategory().getId(),
                permission.getCanRead(),
                permission.getCanCreate(),
                permission.getCanUpdate(),
                permission.getCanDelete(),
                permission.getAllowedColumns(),
                permission.getMaxConfidentiality(),
                permission.getCreatedAt()
            );
        }
    }
}
