package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findById(Long id);
    User save(User user);
    void deleteById(Long id, String currentUsername);
    Page<User> search(String keyword, Pageable pageable);
    long count();
}
