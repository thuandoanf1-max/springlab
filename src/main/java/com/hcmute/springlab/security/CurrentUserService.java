package com.hcmute.springlab.security;

import com.hcmute.springlab.dto.UserResponse;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.mapper.UserMapper;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public CurrentUserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public Optional<UserResponse> getCurrentUser() {
        return getCurrentUserEntity().map(userMapper::toResponse);
    }

    public Optional<User> getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName());
    }

    public User requireCurrentUserEntity() {
        return getCurrentUserEntity()
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist in the database"));
    }
}
