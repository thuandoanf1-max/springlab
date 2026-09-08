package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> findAll();
    Optional<Category> findById(Long id);
    Category save(Category category);
    void deleteById(Long id);
    List<Category> searchByName(String name);
}

