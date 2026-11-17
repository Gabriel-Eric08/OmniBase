package com.secmulher.domain.services;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.DynamicEntityDtos.DynamicEntityRequest;
import com.secmulher.domain.dtos.DynamicEntityDtos.DynamicEntityResponse;
import com.secmulher.domain.dtos.DynamicEntityDtos.DynamicEntitySearchRequest;
import com.secmulher.domain.enums.RecordType;
import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Category;
import com.secmulher.infra.entities.DynamicEntity;
import com.secmulher.infra.entities.enums.ActivityType;
import com.secmulher.infra.repositories.DynamicEntityRepository;
import com.secmulher.infra.security.CurrentUserProvider;

import tools.jackson.databind.json.JsonMapper;

@Service
public class DynamicEntityService {
    final DynamicEntityRepository dynamicEntityRepository;
    final CategoryService categoryService;
    final CategoryFieldService categoryFieldService;
    final ActivityService activityService;
    final FileService fileService;
    final CurrentUserProvider currentUserProvider;
    final JsonMapper jsonMapper;

    public DynamicEntityService(DynamicEntityRepository dynamicEntityRepository, CategoryService categoryService,
            CategoryFieldService categoryFieldService, ActivityService activityService, FileService fileService,
            CurrentUserProvider currentUserProvider, JsonMapper jsonMapper) {
        this.dynamicEntityRepository = dynamicEntityRepository;
        this.categoryService = categoryService;
        this.categoryFieldService = categoryFieldService;
        this.activityService = activityService;
        this.fileService = fileService;
        this.currentUserProvider = currentUserProvider;
        this.jsonMapper = jsonMapper;
    }

    private DynamicEntity getDynamicEntityById(UUID id) {
        return dynamicEntityRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Registro não encontrado: " + id));
    }

    // Só categorias do tipo 'dynamic' guardam registros em dynamic_entities
    private Category getDynamicCategory(UUID categoryId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (!"dynamic".equals(category.getEntityType())) {
            throw new BusinessException("A categoria " + category.getName() + " não é dinâmica");
        }
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new BusinessException("A categoria " + category.getName() + " está inativa");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public List<DynamicEntityResponse> findAll(UUID categoryId) {
        List<DynamicEntity> entities = categoryId != null
            ? dynamicEntityRepository.findByCategoryId(categoryId)
            : dynamicEntityRepository.findAll();
        return entities.stream().map(DynamicEntityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DynamicEntityResponse findById(UUID id) {
        return DynamicEntityResponse.from(getDynamicEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<DynamicEntityResponse> search(DynamicEntitySearchRequest request) {
        categoryService.getCategoryById(request.categoryId());
        Map<String, Object> fields = request.fields() != null ? request.fields() : Map.of();
        return dynamicEntityRepository.searchByCustomFields(request.categoryId(), jsonMapper.writeValueAsString(fields))
            .stream().map(DynamicEntityResponse::from).toList();
    }

    @Transactional
    public DynamicEntityResponse create(DynamicEntityRequest request) {
        Category category = getDynamicCategory(request.categoryId());
        Map<String, Object> customFields = categoryFieldService.validateCustomFields(category.getId(), request.customFields());

        DynamicEntity entity = dynamicEntityRepository.saveAndFlush(DynamicEntity.builder()
            .category(category)
            .customFields(customFields)
            .createdBy(currentUserProvider.getCurrentUser().orElse(null))
            .build());

        activityService.record(RecordType.DYNAMIC_ENTITY, entity.getId(), ActivityType.CREATED,
            "Cadastro criado em " + category.getName(), null);
        return DynamicEntityResponse.from(entity);
    }

    @Transactional
    public DynamicEntityResponse update(UUID id, DynamicEntityRequest request) {
        DynamicEntity entity = getDynamicEntityById(id);
        Map<String, Object> changes = new LinkedHashMap<>();

        if (!entity.getCategory().getId().equals(request.categoryId())) {
            Category newCategory = getDynamicCategory(request.categoryId());
            ActivityService.trackChange(changes, "categoria", entity.getCategory().getName(), newCategory.getName());
            entity.setCategory(newCategory);
        }

        Map<String, Object> newFields = categoryFieldService.validateCustomFields(request.categoryId(), request.customFields());
        Map<String, Object> oldFields = entity.getCustomFields() != null ? entity.getCustomFields() : Map.of();
        Set<String> keys = new HashSet<>(oldFields.keySet());
        keys.addAll(newFields.keySet());
        keys.stream().sorted()
            .forEach(key -> ActivityService.trackChange(changes, key, oldFields.get(key), newFields.get(key)));

        entity.setCustomFields(newFields);

        if (!changes.isEmpty()) {
            activityService.record(RecordType.DYNAMIC_ENTITY, id, ActivityType.UPDATED, "Cadastro atualizado", changes);
        }
        return DynamicEntityResponse.from(dynamicEntityRepository.saveAndFlush(entity));
    }

    // O histórico permanece no ledger mesmo após a exclusão; os documentos são removidos
    @Transactional
    public void delete(UUID id) {
        DynamicEntity entity = getDynamicEntityById(id);
        fileService.deleteAllFor(RecordType.DYNAMIC_ENTITY, id);
        activityService.record(RecordType.DYNAMIC_ENTITY, id, ActivityType.DELETED,
            "Cadastro excluído de " + entity.getCategory().getName(), null);
        dynamicEntityRepository.delete(entity);
    }
}
