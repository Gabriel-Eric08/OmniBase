package com.secmulher.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.DynamicEntity;

public interface DynamicEntityRepository extends JpaRepository<DynamicEntity, UUID> {
    List<DynamicEntity> findByCategoryId(UUID categoryId);
}
