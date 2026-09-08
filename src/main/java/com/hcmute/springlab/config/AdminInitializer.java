package com.hcmute.springlab.config;

import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByRole("ADMIN")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("admin");
            admin.setFullname("System Administrator");
            admin.setEmail("admin@localhost.com");
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println("Default admin user created: admin/admin");
        }
    }
}

