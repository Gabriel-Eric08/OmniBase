package com.secmulher.domain.services;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.access.AccessControlService;
import com.secmulher.domain.dtos.PermissionDtos.PermissionRequest;
import com.secmulher.domain.dtos.PermissionDtos.PermissionResponse;
import com.secmulher.domain.exceptions.ConflictException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Permission;
import com.secmulher.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.infra.repositories.PermissionRepository;

// A matriz de permissões é gerida exclusivamente por administradores
@Service
public class PermissionService {
    final PermissionRepository permissionRepository;
    final UserService userService;
    final CategoryService categoryService;
    final AccessControlService accessControl;

    public PermissionService(PermissionRepository permissionRepository, UserService userService,
            CategoryService categoryService, AccessControlService accessControl) {
        this.permissionRepository = permissionRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.accessControl = accessControl;
    }

    private Permission getPermissionById(UUID id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Permissão não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll(UUID userId) {
        accessControl.requireAdmin();
        List<Permission> permissions = userId != null
            ? permissionRepository.findByUserId(userId)
            : permissionRepository.findAll();
        return permissions.stream().map(PermissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        accessControl.requireAdmin();
        return PermissionResponse.from(getPermissionById(id));
    }

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        accessControl.requireAdmin();
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
            .maxConfidentiality(Objects.requireNonNullElse(request.maxConfidentiality(), ConfidentialityLevel.INTERNAL))
            .build();

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public PermissionResponse update(UUID id, PermissionRequest request) {
        accessControl.requireAdmin();
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
        if (request.maxConfidentiality() != null) {
            permission.setMaxConfidentiality(request.maxConfidentiality());
        }

        return PermissionResponse.from(permissionRepository.saveAndFlush(permission));
    }

    @Transactional
    public void delete(UUID id) {
        accessControl.requireAdmin();
        permissionRepository.delete(getPermissionById(id));
    }
}
