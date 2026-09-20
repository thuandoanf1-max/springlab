package com.hcmute.springlab.dto.graphql;

import com.hcmute.springlab.entity.Category;
import org.springframework.data.domain.Page;

import java.util.List;

public record CategoryPage(
        List<Category> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static CategoryPage from(Page<Category> page) {
        return new CategoryPage(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
