package com.secmulher.domain.services;

import org.springframework.stereotype.Service;

import com.secmulher.infra.entities.User;
import com.secmulher.infra.repositories.UserRepository;

@Service 
public class UserService {
    final UserRepository userRepository; 

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isEmailRegistered(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
