package com.secmulher.domain.dtos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.secmulher.infra.entities.Permission;

import jakarta.validation.constraints.NotNull;

public final class PermissionDtos {
    private PermissionDtos() {}

    public record PermissionRequest(
        @NotNull UUID userId,
        @NotNull UUID categoryId,
        Boolean canRead,
        Boolean canCreate,
        Boolean canUpdate,
        Boolean canDelete,
        Map<String, Object> allowedColumns
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
                permission.getCreatedAt()
            );
        }
    }
}
