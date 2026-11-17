package com.secmulher.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.domain.dtos.UserDtos.UserRequest;
import com.secmulher.domain.dtos.UserDtos.UserResponse;
import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.ConflictException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.User;
import com.secmulher.infra.repositories.UserRepository;

@Service
public class UserService {
    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isEmailRegistered(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.from(getUserById(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("A senha é obrigatória na criação do usuário");
        }
        if (isEmailRegistered(request.email())) {
            throw new ConflictException("E-mail já cadastrado: " + request.email());
        }

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .isGlobalAdmin(Objects.requireNonNullElse(request.isGlobalAdmin(), false))
            .isActive(Objects.requireNonNullElse(request.isActive(), true))
            .build();

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = getUserById(id);

        if (!user.getEmail().equals(request.email()) && isEmailRegistered(request.email())) {
            throw new ConflictException("E-mail já cadastrado: " + request.email());
        }

        user.setName(request.name());
        user.setEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.isGlobalAdmin() != null) {
            user.setIsGlobalAdmin(request.isGlobalAdmin());
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void delete(UUID id) {
        userRepository.delete(getUserById(id));
    }
}
