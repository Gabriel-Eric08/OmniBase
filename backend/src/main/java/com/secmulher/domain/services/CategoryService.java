package com.secmulher.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.CategoryDtos.CategoryRequest;
import com.secmulher.domain.dtos.CategoryDtos.CategoryResponse;
import com.secmulher.domain.exceptions.ConflictException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Category;
import com.secmulher.infra.repositories.CategoryRepository;

@Service
public class CategoryService {
    final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return CategoryResponse.from(getCategoryById(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Já existe uma categoria com o nome: " + request.name());
        }

        Category category = Category.builder()
            .name(request.name())
            .entityType(request.entityType())
            .tableReference(request.tableReference())
            .isActive(Objects.requireNonNullElse(request.isActive(), true))
            .build();

        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = getCategoryById(id);

        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Já existe uma categoria com o nome: " + request.name());
        }

        category.setName(request.name());
        category.setEntityType(request.entityType());
        category.setTableReference(request.tableReference());
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void delete(UUID id) {
        categoryRepository.delete(getCategoryById(id));
    }
}
