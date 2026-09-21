package com.hcmute.springlab.mapper;

import com.hcmute.springlab.dto.UserFormRequest;
import com.hcmute.springlab.dto.UserResponse;
import com.hcmute.springlab.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);

    List<UserResponse> toResponses(List<User> users);

    @Mapping(target = "enabled", expression = "java(user.getEnabled() == null ? Boolean.TRUE : user.getEnabled())")
    UserFormRequest toFormRequest(User user);

    User toEntity(UserFormRequest request);
}
