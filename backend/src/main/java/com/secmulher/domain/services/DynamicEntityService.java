package com.secmulher.domain.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.DynamicEntityDtos.DynamicEntityRequest;
import com.secmulher.domain.dtos.DynamicEntityDtos.DynamicEntityResponse;
import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Category;
import com.secmulher.infra.entities.DynamicEntity;
import com.secmulher.infra.repositories.DynamicEntityRepository;

@Service
public class DynamicEntityService {
    final DynamicEntityRepository dynamicEntityRepository;
    final CategoryService categoryService;
    final UserService userService;

    public DynamicEntityService(DynamicEntityRepository dynamicEntityRepository, CategoryService categoryService,
            UserService userService) {
        this.dynamicEntityRepository = dynamicEntityRepository;
        this.categoryService = categoryService;
        this.userService = userService;
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

    @Transactional
    public DynamicEntityResponse create(DynamicEntityRequest request) {
        DynamicEntity entity = DynamicEntity.builder()
            .category(getDynamicCategory(request.categoryId()))
            .customFields(request.customFields())
            .createdBy(request.createdBy() != null ? userService.getUserById(request.createdBy()) : null)
            .build();

        return DynamicEntityResponse.from(dynamicEntityRepository.save(entity));
    }

    @Transactional
    public DynamicEntityResponse update(UUID id, DynamicEntityRequest request) {
        DynamicEntity entity = getDynamicEntityById(id);

        if (!entity.getCategory().getId().equals(request.categoryId())) {
            entity.setCategory(getDynamicCategory(request.categoryId()));
        }
        entity.setCustomFields(request.customFields());

        return DynamicEntityResponse.from(dynamicEntityRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        dynamicEntityRepository.delete(getDynamicEntityById(id));
    }
}
