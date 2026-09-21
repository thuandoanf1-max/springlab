package com.hcmute.springlab.dto.graphql;

import com.hcmute.springlab.entity.User;

public record ProductOwnerResponse(Long id, String username, String fullname) {

    public static ProductOwnerResponse from(User user) {
        return user == null ? null : new ProductOwnerResponse(user.getId(), user.getUsername(), user.getFullname());
    }
}
