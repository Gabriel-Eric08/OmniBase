package com.secmulher.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.ClientDtos.ClientRequest;
import com.secmulher.domain.dtos.ClientDtos.ClientResponse;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Client;
import com.secmulher.infra.repositories.ClientRepository;

@Service
public class ClientService {
    final ClientRepository clientRepository;
    final UserService userService;

    public ClientService(ClientRepository clientRepository, UserService userService) {
        this.clientRepository = clientRepository;
        this.userService = userService;
    }

    private Client getClientById(UUID id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cliente não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(UUID id) {
        return ClientResponse.from(getClientById(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        Client client = Client.builder()
            .nome(request.nome())
            .dataNascimento(request.dataNascimento())
            .telefone(request.telefone())
            .sobRisco(Objects.requireNonNullElse(request.sobRisco(), false))
            .createdBy(request.createdBy() != null ? userService.getUserById(request.createdBy()) : null)
            .build();

        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = getClientById(id);

        client.setNome(request.nome());
        client.setDataNascimento(request.dataNascimento());
        client.setTelefone(request.telefone());
        if (request.sobRisco() != null) {
            client.setSobRisco(request.sobRisco());
        }

        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public void delete(UUID id) {
        clientRepository.delete(getClientById(id));
    }
}
