package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.OtpTokenRepository;
import com.hcmute.springlab.repository.ProductRepository;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, OtpTokenRepository otpTokenRepository,
                           ProductRepository productRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.otpTokenRepository = otpTokenRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        if (user.getId() == null) {
            normalizeTextFields(user);
            validateRole(user);
            validateNewPassword(user.getPassword());
            ensureUniqueUsernameAndEmail(user);
            if (user.getEnabled() == null) {
                user.setEnabled(true);
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            User existingUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
            // Username is intentionally immutable from the admin edit flow.
            user.setUsername(existingUser.getUsername());
            normalizeTextFields(user);
            validateRole(user);
            validateOptionalPassword(user.getPassword());
            ensureUniqueUsernameAndEmail(user);
            if (user.getEnabled() == null) {
                user.setEnabled(existingUser.getEnabled());
            }
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(existingUser.getPassword());
            } else {
                // Every non-blank form value is raw input and is encoded exactly once here.
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteById(Long id, String currentUsername) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        if (currentUsername != null && target.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        if (otpTokenRepository.existsByUserId(id)) {
            throw new IllegalArgumentException("Cannot delete user because OTP history exists");
        }
        if (productRepository.existsByUserId(id)) {
            throw new IllegalArgumentException("Cannot delete user because they own products");
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return userRepository.findAll(pageable);
        }
        String normalizedKeyword = keyword.trim();
        return userRepository.findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                normalizedKeyword, normalizedKeyword, normalizedKeyword, pageable);
    }

    @Override
    public long count() {
        return userRepository.count();
    }

    private void ensureUniqueUsernameAndEmail(User user) {
        boolean usernameExists = user.getId() == null
                ? userRepository.existsByUsernameIgnoreCase(user.getUsername())
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(user.getUsername(), user.getId());
        if (usernameExists) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            boolean emailExists = user.getId() == null
                    ? userRepository.existsByEmailIgnoreCase(user.getEmail())
                    : userRepository.existsByEmailIgnoreCaseAndIdNot(user.getEmail(), user.getId());
            if (emailExists) {
                throw new IllegalArgumentException("Email already exists");
            }
        }
    }

    private void normalizeTextFields(User user) {
        user.setUsername(trimToNull(user.getUsername()));
        user.setFullname(trimToNull(user.getFullname()));
        user.setEmail(trimToNull(user.getEmail()));
        user.setImage(trimToNull(user.getImage()));
    }

    private void validateRole(User user) {
        String role = trimToNull(user.getRole());
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        role = role.toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new IllegalArgumentException("Role must be ADMIN or USER");
        }
        user.setRole(role);
    }

    private void validateNewPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        validatePasswordLength(password);
    }

    private void validateOptionalPassword(String password) {
        if (password != null && !password.isBlank()) {
            validatePasswordLength(password);
        }
    }

    private void validatePasswordLength(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be between 6 and 100 characters");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
