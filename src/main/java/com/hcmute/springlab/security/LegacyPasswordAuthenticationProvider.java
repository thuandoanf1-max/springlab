package com.hcmute.springlab.security;

import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LegacyPasswordAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public LegacyPasswordAuthenticationProvider(UserRepository userRepository,
                                                CustomUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String identifier = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        User user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new DisabledException("User account is disabled");
        }

        String storedPassword = user.getPassword();
        boolean passwordMatches;
        if (isBcrypt(storedPassword)) {
            passwordMatches = passwordEncoder.matches(rawPassword, storedPassword);
        } else {
            passwordMatches = storedPassword != null && storedPassword.equals(rawPassword);
            if (passwordMatches) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userRepository.save(user);
            }
        }

        if (!passwordMatches) {
            throw new BadCredentialsException("Invalid username/email or password");
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                userDetailsService.toUserDetails(user), null, userDetailsService.toUserDetails(user).getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$")
                || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
