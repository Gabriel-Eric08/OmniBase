package com.secmulher.domain.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.secmulher.infra.entities.Client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public final class ClientDtos {
    private ClientDtos() {}

    // createdBy vem no corpo enquanto não há autenticação
    public record ClientRequest(
        @NotBlank @Size(max = 255) String nome,
        @Past LocalDate dataNascimento,
        @Size(max = 50) String telefone,
        Boolean sobRisco,
        UUID createdBy
    ) {}

    public record ClientResponse(
        UUID id,
        String nome,
        LocalDate dataNascimento,
        String telefone,
        Boolean sobRisco,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static ClientResponse from(Client client) {
            return new ClientResponse(
                client.getId(),
                client.getNome(),
                client.getDataNascimento(),
                client.getTelefone(),
                client.getSobRisco(),
                client.getCreatedBy() != null ? client.getCreatedBy().getId() : null,
                client.getCreatedAt(),
                client.getUpdatedAt()
            );
        }
    }
}
