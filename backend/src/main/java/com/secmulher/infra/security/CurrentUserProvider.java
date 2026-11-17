package com.secmulher.infra.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.User;
import com.secmulher.infra.repositories.UserRepository;

// Identificação PROVISÓRIA do usuário pelo cabeçalho X-User-Id, até a autenticação JWT existir.
// Quando o JWT entrar, basta trocar a implementação para ler do SecurityContext.
@Component
public class CurrentUserProvider {
    public static final String HEADER = "X-User-Id";

    final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> getCurrentUser() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }

        String rawId = attributes.getRequest().getHeader(HEADER);
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }

        UUID userId;
        try {
            userId = UUID.fromString(rawId.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Cabeçalho " + HEADER + " inválido: " + rawId);
        }

        return Optional.of(userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Usuário do cabeçalho " + HEADER + " não encontrado: " + userId)));
    }
}
