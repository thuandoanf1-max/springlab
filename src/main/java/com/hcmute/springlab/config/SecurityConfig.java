package com.hcmute.springlab.config;

import com.hcmute.springlab.security.LegacyPasswordAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            LegacyPasswordAuthenticationProvider authenticationProvider) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/register", "/verify-otp", "/register/resend-otp",
                                "/forgot-password", "/reset-password", "/forgot-password/resend-otp",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Queries remain public for the home page; mutations enforce ROLE_ADMIN in their resolver.
                        .requestMatchers("/graphql").permitAll()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                            response.sendRedirect(request.getContextPath() + (isAdmin ? "/admin/categories" : "/"));
                        })
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll())
                // Legacy AJAX and GraphQL clients do not yet send a CSRF token. Keep the exception narrow.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/graphql", "/admin/api/**"));
        return http.build();
    }
}
