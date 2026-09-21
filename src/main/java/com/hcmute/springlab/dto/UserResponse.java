package com.hcmute.springlab.dto;

public record UserResponse(
        Long id,
        String username,
        String fullname,
        String email,
        String image,
        String role,
        Boolean enabled) {
}
