package com.hcmute.springlab.repository;

import com.hcmute.springlab.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Override
    @EntityGraph(attributePaths = {"category", "user"})
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = {"category", "user"})
    Optional<Product> findById(Long id);

    List<Product> findByNameContainingIgnoreCase(String name);

    @EntityGraph(attributePaths = {"category", "user"})
    List<Product> findAllByOrderByPriceAsc();

    @EntityGraph(attributePaths = {"category", "user"})
    List<Product> findByCategory_Id(Long categoryId);

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            select p from Product p
            where (:keyword is null or lower(p.name) like lower(concat('%', :keyword, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:ownerId is null or p.user.id = :ownerId)
            """)
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         @Param("ownerId") Long ownerId,
                         Pageable pageable);

    long countByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
            select p.user.id as userId, count(p) as productCount
            from Product p
            where p.user.id in :userIds
            group by p.user.id
            """)
    List<UserProductCount> countProductsByUserIds(@Param("userIds") Collection<Long> userIds);

    interface UserProductCount {
        Long getUserId();
        Long getProductCount();
    }
}
