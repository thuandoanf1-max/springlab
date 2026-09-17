package com.hcmute.springlab.dto;

import com.hcmute.springlab.entity.Product;

public record ProductResponse(
        Long id,
        String name,
        Integer quantity,
        Double price,
        String image,
        Long categoryId,
        String categoryName) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getQuantity(),
                product.getPrice(),
                product.getImage(),
                product.getCategory().getId(),
                product.getCategory().getName());
    }
}
