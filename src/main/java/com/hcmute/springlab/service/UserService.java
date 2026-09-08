package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findById(Long id);
    User save(User user);
    void deleteById(Long id);
    List<User> search(String keyword);
    User login(String username, String password);
}

