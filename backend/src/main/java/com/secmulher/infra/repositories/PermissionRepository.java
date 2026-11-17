package com.secmulher.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findByUserId(UUID userId);

    boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
