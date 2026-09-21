package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductService {
    List<Product> findAll();
    Optional<Product> findById(Long id);
    Product save(Product product);
    Product createForCurrentUser(Product product);
    void deleteById(Long id);
    List<Product> searchByName(String name);
    List<Product> findAllByPriceAsc();
    List<Product> findByCategoryId(Long categoryId);
    Page<Product> search(String keyword, Pageable pageable);
    Page<Product> search(String keyword, Long categoryId, Long ownerId, Pageable pageable);
    long countByUserId(Long userId);
    Map<Long, Long> countByUserIds(Collection<Long> userIds);
}
