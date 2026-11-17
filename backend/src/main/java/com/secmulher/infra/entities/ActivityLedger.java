package com.secmulher.infra.entities;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "activities_ledger", indexes = {
    @Index(name = "idx_ledger_polymorphic", columnList = "entity_type, entity_id"),
    @Index(name = "idx_ledger_user", columnList = "user_id")
})
@Entity
public class ActivityLedger {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    // Polimorfismo: tabela de origem (ex: 'clients' ou 'dynamic_entities')
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    // Polimorfismo: registro específico dentro da tabela
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Ex: 'MANUAL_ENTRY' ou 'AUTO_UPDATE'
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // Ex: {"campo": "telefone", "antigo": "1111", "novo": "2222"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private Map<String, Object> changes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
