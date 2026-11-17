package com.secmulher.infra.repositories;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secmulher.infra.entities.DynamicEntity;

public interface DynamicEntityRepository extends JpaRepository<DynamicEntity, UUID> {
    List<DynamicEntity> findByCategoryId(UUID categoryId);

    List<DynamicEntity> findByCategoryIdIn(Collection<UUID> categoryIds);

    // Busca por conteúdo do JSONB usando o operador @> (aproveita o índice GIN)
    @Query(value = """
        SELECT * FROM dynamic_entities
        WHERE category_id = :categoryId
          AND custom_fields @> CAST(:filter AS jsonb)
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<DynamicEntity> searchByCustomFields(@Param("categoryId") UUID categoryId, @Param("filter") String filterJson);

    // jsonb_exists equivale ao operador ?, que conflita com os placeholders do JDBC
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM dynamic_entities
            WHERE category_id = :categoryId AND jsonb_exists(custom_fields, :fieldKey)
        )
        """, nativeQuery = true)
    boolean existsCustomFieldKey(@Param("categoryId") UUID categoryId, @Param("fieldKey") String fieldKey);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE dynamic_entities
        SET custom_fields = custom_fields - CAST(:fieldKey AS text)
        WHERE category_id = :categoryId
        """, nativeQuery = true)
    int removeCustomFieldKey(@Param("categoryId") UUID categoryId, @Param("fieldKey") String fieldKey);
}
