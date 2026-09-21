package com.hcmute.springlab.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    Cloudinary cloudinary(@Value("${cloudinary.cloud-name:}") String cloudName,
                          @Value("${cloudinary.api-key:}") String apiKey,
                          @Value("${cloudinary.api-secret:}") String apiSecret) {
        Map<String, Object> config = new HashMap<>();
        config.put("secure", true);
        putIfPresent(config, "cloud_name", cloudName);
        putIfPresent(config, "api_key", apiKey);
        putIfPresent(config, "api_secret", apiSecret);
        return new Cloudinary(config);
    }

    private void putIfPresent(Map<String, Object> config, String key, String value) {
        if (value != null && !value.isBlank()) {
            config.put(key, value.trim());
        }
    }
}
