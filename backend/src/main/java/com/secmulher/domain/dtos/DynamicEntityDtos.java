package com.secmulher.domain.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.secmulher.domain.access.CategoryAccess;
import com.secmulher.infra.entities.DynamicEntity;

import jakarta.validation.constraints.NotNull;

public final class DynamicEntityDtos {
    private DynamicEntityDtos() {}

    public record DynamicEntityRequest(
        @NotNull UUID categoryId,
        @NotNull Map<String, Object> customFields
    ) {}

    // Filtra registros cujo custom_fields contenha todos os pares informados (igualdade exata)
    public record DynamicEntitySearchRequest(
        @NotNull UUID categoryId,
        Map<String, Object> fields
    ) {}

    // Campos sem permissão de leitura são removidos de customFields e listados em hiddenFields
    public record DynamicEntityResponse(
        UUID id,
        UUID categoryId,
        Map<String, Object> customFields,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> hiddenFields
    ) {
        public static DynamicEntityResponse from(DynamicEntity entity, CategoryAccess access) {
            Map<String, Object> fields = entity.getCustomFields() != null ? entity.getCustomFields() : Map.of();
            return new DynamicEntityResponse(
                entity.getId(),
                entity.getCategory().getId(),
                access.filterReadable(fields),
                entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                access.hiddenColumns(fields.keySet())
            );
        }
    }
}
