package com.hcmute.springlab.controller.graphql;

import com.hcmute.springlab.dto.graphql.ProductInput;
import com.hcmute.springlab.dto.graphql.ProductPage;
import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.entity.Product;
import com.hcmute.springlab.graphql.AdminMutationAuthorizer;
import com.hcmute.springlab.graphql.GraphqlBadRequestException;
import com.hcmute.springlab.graphql.GraphqlEntityValidator;
import com.hcmute.springlab.graphql.GraphqlNotFoundException;
import com.hcmute.springlab.graphql.GraphqlPageRequestFactory;
import com.hcmute.springlab.service.CategoryService;
import com.hcmute.springlab.service.ProductService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class ProductGraphqlController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final GraphqlEntityValidator entityValidator;
    private final GraphqlPageRequestFactory pageRequestFactory;

    public ProductGraphqlController(ProductService productService,
                                    CategoryService categoryService,
                                    AdminMutationAuthorizer adminMutationAuthorizer,
                                    GraphqlEntityValidator entityValidator,
                                    GraphqlPageRequestFactory pageRequestFactory) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.adminMutationAuthorizer = adminMutationAuthorizer;
        this.entityValidator = entityValidator;
        this.pageRequestFactory = pageRequestFactory;
    }

    @QueryMapping
    public List<Product> products() {
        return productService.findAll();
    }

    @QueryMapping
    public Product productById(@Argument Long id) {
        return productService.findById(id).orElse(null);
    }

    @QueryMapping
    public List<Product> productsByPriceAsc() {
        return productService.findAllByPriceAsc();
    }

    @QueryMapping
    public List<Product> productsByCategory(@Argument Long categoryId) {
        return productService.findByCategoryId(categoryId);
    }

    @QueryMapping
    public ProductPage searchProducts(@Argument String keyword, @Argument Integer page, @Argument Integer size) {
        return ProductPage.from(productService.search(keyword, pageRequestFactory.create(page, size)));
    }

    @MutationMapping
    public Product createProduct(@Argument ProductInput input) {
        adminMutationAuthorizer.requireAdmin();
        Product product = new Product();
        applyInput(product, input, true);
        entityValidator.validate(product);
        return productService.save(product);
    }

    @MutationMapping
    public Product updateProduct(@Argument Long id, @Argument ProductInput input) {
        adminMutationAuthorizer.requireAdmin();
        Product product = productService.findById(id)
                .orElseThrow(() -> new GraphqlNotFoundException("Product not found"));
        applyInput(product, input, false);
        entityValidator.validate(product);
        return productService.save(product);
    }

    @MutationMapping
    public boolean deleteProduct(@Argument Long id) {
        adminMutationAuthorizer.requireAdmin();
        if (productService.findById(id).isEmpty()) {
            throw new GraphqlNotFoundException("Product not found");
        }
        productService.deleteById(id);
        return true;
    }

    private void applyInput(Product product, ProductInput input, boolean creating) {
        Category category = categoryService.findById(input.categoryId())
                .orElseThrow(() -> new GraphqlBadRequestException("Category does not exist"));
        product.setName(input.name() == null ? null : input.name().trim());
        product.setQuantity(input.quantity());
        product.setPrice(input.price());
        product.setCategory(category);
        if (creating || input.image() != null) {
            product.setImage(input.image());
        }
    }
}
