package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.Product;
import com.hcmute.springlab.repository.ProductRepository;
import com.hcmute.springlab.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;

    public ProductServiceImpl(ProductRepository productRepository, CurrentUserService currentUserService) {
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product createForCurrentUser(Product product) {
        if (product.getId() != null) {
            throw new IllegalArgumentException("A new product must not already have an ID");
        }
        product.setUser(currentUserService.requireCurrentUserEntity());
        return productRepository.save(product);
    }

    @Override
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public List<Product> searchByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return findAll();
        }
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public List<Product> findAllByPriceAsc() {
        return productRepository.findAllByOrderByPriceAsc();
    }

    @Override
    public List<Product> findByCategoryId(Long categoryId) {
        return productRepository.findByCategory_Id(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> search(String keyword, Pageable pageable) {
        return search(keyword, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> search(String keyword, Long categoryId, Long ownerId, Pageable pageable) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return productRepository.search(normalizedKeyword, categoryId, ownerId, pageable);
    }

    @Override
    public long countByUserId(Long userId) {
        return productRepository.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.countProductsByUserIds(userIds).stream()
                .collect(Collectors.toMap(ProductRepository.UserProductCount::getUserId,
                        ProductRepository.UserProductCount::getProductCount));
    }
}
