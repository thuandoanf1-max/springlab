package com.hcmute.springlab.config;

import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final String adminUsername;
    private final String adminPassword;

    public AdminInitializer(UserRepository userRepository,
                            @Value("${app.admin.username:admin}") String adminUsername,
                            @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByRole("ADMIN")) {
            if (!StringUtils.hasText(adminPassword)) {
                logger.warn("No ADMIN user was created because ADMIN_PASSWORD is not configured.");
                return;
            }

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(adminPassword);
            admin.setFullname("System Administrator");
            admin.setEmail("admin@localhost.com");
            admin.setRole("ADMIN");
            userRepository.save(admin);
            logger.info("Default ADMIN user created: {}", adminUsername);
        }
    }
}
