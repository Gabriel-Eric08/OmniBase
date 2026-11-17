package com.secmulher.domain.dtos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.secmulher.infra.entities.ActivityLedger;
import com.secmulher.infra.entities.enums.ActivityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class ActivityDtos {
    private ActivityDtos() {}

    // Registro manual de triagem, atendimento, acompanhamento ou observação
    public record ManualActivityRequest(
        @NotNull ActivityType actionType,
        @NotBlank String description,
        @Size(max = 255) String responsibleEmployee,
        @PastOrPresent LocalDateTime occurredAt
    ) {}

    public record ActivityResponse(
        UUID id,
        String entityType,
        UUID entityId,
        UUID userId,
        String userName,
        ActivityType actionType,
        String description,
        Map<String, Object> changes,
        String responsibleEmployee,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        boolean redacted
    ) {
        public static ActivityResponse from(ActivityLedger activity) {
            return new ActivityResponse(
                activity.getId(),
                activity.getEntityType(),
                activity.getEntityId(),
                activity.getUser() != null ? activity.getUser().getId() : null,
                activity.getUser() != null ? activity.getUser().getName() : null,
                activity.getActionType(),
                activity.getDescription(),
                activity.getChanges(),
                activity.getResponsibleEmployee(),
                activity.getOccurredAt(),
                activity.getCreatedAt(),
                false
            );
        }

        // Cópia com conteúdo ocultado para quem não tem acesso a parte das informações
        public ActivityResponse redact(String newDescription, Map<String, Object> newChanges) {
            return new ActivityResponse(id, entityType, entityId, userId, userName, actionType,
                newDescription, newChanges, responsibleEmployee, occurredAt, createdAt, true);
        }
    }
}
