package com.secmulher.domain.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.ClientDtos.ClientRequest;
import com.secmulher.domain.dtos.ClientDtos.ClientResponse;
import com.secmulher.domain.enums.RecordType;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.Client;
import com.secmulher.infra.entities.enums.ActivityType;
import com.secmulher.infra.repositories.ClientRepository;
import com.secmulher.infra.security.CurrentUserProvider;

@Service
public class ClientService {
    final ClientRepository clientRepository;
    final ActivityService activityService;
    final FileService fileService;
    final CurrentUserProvider currentUserProvider;

    public ClientService(ClientRepository clientRepository, ActivityService activityService, FileService fileService,
            CurrentUserProvider currentUserProvider) {
        this.clientRepository = clientRepository;
        this.activityService = activityService;
        this.fileService = fileService;
        this.currentUserProvider = currentUserProvider;
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
        Client client = clientRepository.saveAndFlush(Client.builder()
            .nome(request.nome())
            .dataNascimento(request.dataNascimento())
            .telefone(request.telefone())
            .sobRisco(Objects.requireNonNullElse(request.sobRisco(), false))
            .createdBy(currentUserProvider.getCurrentUser().orElse(null))
            .build());

        activityService.record(RecordType.CLIENT, client.getId(), ActivityType.CREATED, "Cadastro criado", null);
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(UUID id, ClientRequest request) {
        Client client = getClientById(id);
        Boolean sobRisco = Objects.requireNonNullElse(request.sobRisco(), client.getSobRisco());

        Map<String, Object> changes = new LinkedHashMap<>();
        ActivityService.trackChange(changes, "nome", client.getNome(), request.nome());
        ActivityService.trackChange(changes, "data_nascimento", client.getDataNascimento(), request.dataNascimento());
        ActivityService.trackChange(changes, "telefone", client.getTelefone(), request.telefone());
        ActivityService.trackChange(changes, "sob_risco", client.getSobRisco(), sobRisco);

        client.setNome(request.nome());
        client.setDataNascimento(request.dataNascimento());
        client.setTelefone(request.telefone());
        client.setSobRisco(sobRisco);

        if (!changes.isEmpty()) {
            activityService.record(RecordType.CLIENT, id, ActivityType.UPDATED, "Cadastro atualizado", changes);
        }
        return ClientResponse.from(clientRepository.saveAndFlush(client));
    }

    // O histórico permanece no ledger mesmo após a exclusão; os documentos são removidos
    @Transactional
    public void delete(UUID id) {
        Client client = getClientById(id);
        fileService.deleteAllFor(RecordType.CLIENT, id);
        activityService.record(RecordType.CLIENT, id, ActivityType.DELETED, "Cadastro excluído: " + client.getNome(), null);
        clientRepository.delete(client);
    }
}
