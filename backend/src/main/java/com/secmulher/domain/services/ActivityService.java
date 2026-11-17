package com.secmulher.domain.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.ActivityDtos.ActivityResponse;
import com.secmulher.domain.dtos.ActivityDtos.ManualActivityRequest;
import com.secmulher.domain.enums.RecordType;
import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.ActivityLedger;
import com.secmulher.infra.entities.enums.ActivityType;
import com.secmulher.infra.repositories.ActivityLedgerRepository;
import com.secmulher.infra.repositories.UserRepository;
import com.secmulher.infra.security.CurrentUserProvider;

@Service
public class ActivityService {
    final ActivityLedgerRepository activityLedgerRepository;
    final UserRepository userRepository;
    final RecordService recordService;
    final CurrentUserProvider currentUserProvider;

    public ActivityService(ActivityLedgerRepository activityLedgerRepository, UserRepository userRepository,
            RecordService recordService, CurrentUserProvider currentUserProvider) {
        this.activityLedgerRepository = activityLedgerRepository;
        this.userRepository = userRepository;
        this.recordService = recordService;
        this.currentUserProvider = currentUserProvider;
    }

    // Adiciona {"campo": {"antigo": ..., "novo": ...}} ao mapa de mudanças, se o valor mudou
    public static void trackChange(Map<String, Object> changes, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("antigo", before != null ? String.valueOf(before) : null);
        change.put("novo", after != null ? String.valueOf(after) : null);
        changes.put(field, change);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getTimeline(RecordType type, UUID recordId) {
        recordService.ensureExists(type, recordId);
        return activityLedgerRepository
            .findByEntityTypeAndEntityIdOrderByOccurredAtDescCreatedAtDesc(type.getTableName(), recordId)
            .stream().map(ActivityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getByUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Usuário não encontrado: " + userId);
        }
        return activityLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(ActivityResponse::from).toList();
    }

    @Transactional
    public ActivityResponse createManual(RecordType type, UUID recordId, ManualActivityRequest request) {
        if (!request.actionType().isManual()) {
            throw new BusinessException("O tipo " + request.actionType() + " é reservado para registros automáticos do sistema");
        }
        recordService.ensureExists(type, recordId);

        ActivityLedger activity = ActivityLedger.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .user(currentUserProvider.getCurrentUser().orElse(null))
            .actionType(request.actionType())
            .description(request.description())
            .responsibleEmployee(request.responsibleEmployee())
            .occurredAt(Objects.requireNonNullElseGet(request.occurredAt(), LocalDateTime::now))
            .build();

        return ActivityResponse.from(activityLedgerRepository.saveAndFlush(activity));
    }

    // Registro automático de auditoria; participa da transação de quem chamou
    @Transactional
    public void record(RecordType type, UUID recordId, ActivityType actionType, String description,
            Map<String, Object> changes) {
        ActivityLedger activity = ActivityLedger.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .user(currentUserProvider.getCurrentUser().orElse(null))
            .actionType(actionType)
            .description(description)
            .changes(changes == null || changes.isEmpty() ? null : changes)
            .occurredAt(LocalDateTime.now())
            .build();

        activityLedgerRepository.save(activity);
    }
}
