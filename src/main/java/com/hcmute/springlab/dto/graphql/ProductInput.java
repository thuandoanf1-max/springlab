package com.hcmute.springlab.dto.graphql;

public record ProductInput(String name, Integer quantity, Double price, String image, Long categoryId) {
}
