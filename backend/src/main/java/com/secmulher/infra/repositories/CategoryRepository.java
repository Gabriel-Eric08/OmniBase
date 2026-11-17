package com.secmulher.infra.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByName(String name);

    Optional<Category> findFirstByTableReference(String tableReference);
}
