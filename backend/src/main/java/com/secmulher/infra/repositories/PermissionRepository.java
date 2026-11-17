package com.secmulher.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

}
