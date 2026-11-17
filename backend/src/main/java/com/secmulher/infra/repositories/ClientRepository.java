package com.secmulher.infra.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.infra.entities.Client;

public interface ClientRepository extends JpaRepository<Client, UUID> {

}
