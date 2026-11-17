package com.secmulher.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.ActivityLedger;

public interface ActivityLedgerRepository extends JpaRepository<ActivityLedger, UUID> {

}
