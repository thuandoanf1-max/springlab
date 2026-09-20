package com.hcmute.springlab.dto.graphql;

import com.hcmute.springlab.entity.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPage(
        List<Product> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ProductPage from(Page<Product> page) {
        return new ProductPage(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
