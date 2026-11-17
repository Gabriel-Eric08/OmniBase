package com.secmulher.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.DynamicEntity;

public interface DynamicEntityRepository extends JpaRepository<DynamicEntity, UUID> {

}
