package com.secmulher.domain.services;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.PermissionDtos.PermissionRequest;
import com.secmulher.domain.dtos.PermissionDtos.PermissionResponse;
import com.secmulher.domain.exceptions.ConflictException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Permission;
import com.secmulher.infra.repositories.PermissionRepository;

@Service
public class PermissionService {
    final PermissionRepository permissionRepository;
    final UserService userService;
    final CategoryService categoryService;

    public PermissionService(PermissionRepository permissionRepository, UserService userService,
            CategoryService categoryService) {
        this.permissionRepository = permissionRepository;
        this.userService = userService;
        this.categoryService = categoryService;
    }

    private Permission getPermissionById(UUID id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Permissão não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll(UUID userId) {
        List<Permission> permissions = userId != null
            ? permissionRepository.findByUserId(userId)
            : permissionRepository.findAll();
        return permissions.stream().map(PermissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        return PermissionResponse.from(getPermissionById(id));
    }

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByUserIdAndCategoryId(request.userId(), request.categoryId())) {
            throw new ConflictException("O usuário já possui permissão para esta categoria");
        }

        Permission permission = Permission.builder()
            .user(userService.getUserById(request.userId()))
            .category(categoryService.getCategoryById(request.categoryId()))
            .canRead(Objects.requireNonNullElse(request.canRead(), false))
            .canCreate(Objects.requireNonNullElse(request.canCreate(), false))
            .canUpdate(Objects.requireNonNullElse(request.canUpdate(), false))
            .canDelete(Objects.requireNonNullElse(request.canDelete(), false))
            .allowedColumns(Objects.requireNonNullElse(request.allowedColumns(), new HashMap<>()))
            .build();

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public PermissionResponse update(UUID id, PermissionRequest request) {
        Permission permission = getPermissionById(id);

        boolean targetChanged = !permission.getUser().getId().equals(request.userId())
            || !permission.getCategory().getId().equals(request.categoryId());
        if (targetChanged) {
            if (permissionRepository.existsByUserIdAndCategoryId(request.userId(), request.categoryId())) {
                throw new ConflictException("O usuário já possui permissão para esta categoria");
            }
            permission.setUser(userService.getUserById(request.userId()));
            permission.setCategory(categoryService.getCategoryById(request.categoryId()));
        }

        if (request.canRead() != null) {
            permission.setCanRead(request.canRead());
        }
        if (request.canCreate() != null) {
            permission.setCanCreate(request.canCreate());
        }
        if (request.canUpdate() != null) {
            permission.setCanUpdate(request.canUpdate());
        }
        if (request.canDelete() != null) {
            permission.setCanDelete(request.canDelete());
        }
        if (request.allowedColumns() != null) {
            permission.setAllowedColumns(request.allowedColumns());
        }

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public void delete(UUID id) {
        permissionRepository.delete(getPermissionById(id));
    }
}
