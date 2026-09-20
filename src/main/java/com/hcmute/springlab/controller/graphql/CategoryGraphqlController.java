package com.hcmute.springlab.controller.graphql;

import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.dto.graphql.CategoryInput;
import com.hcmute.springlab.dto.graphql.CategoryPage;
import com.hcmute.springlab.graphql.AdminMutationAuthorizer;
import com.hcmute.springlab.graphql.GraphqlBadRequestException;
import com.hcmute.springlab.graphql.GraphqlEntityValidator;
import com.hcmute.springlab.graphql.GraphqlNotFoundException;
import com.hcmute.springlab.graphql.GraphqlPageRequestFactory;
import com.hcmute.springlab.service.CategoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class CategoryGraphqlController {

    private final CategoryService categoryService;
    private final AdminMutationAuthorizer adminMutationAuthorizer;
    private final GraphqlEntityValidator entityValidator;
    private final GraphqlPageRequestFactory pageRequestFactory;

    public CategoryGraphqlController(CategoryService categoryService,
                                     AdminMutationAuthorizer adminMutationAuthorizer,
                                     GraphqlEntityValidator entityValidator,
                                     GraphqlPageRequestFactory pageRequestFactory) {
        this.categoryService = categoryService;
        this.adminMutationAuthorizer = adminMutationAuthorizer;
        this.entityValidator = entityValidator;
        this.pageRequestFactory = pageRequestFactory;
    }

    @QueryMapping
    public List<Category> categories() {
        return categoryService.findAll();
    }

    @QueryMapping
    public Category categoryById(@Argument Long id) {
        return categoryService.findById(id).orElse(null);
    }

    @QueryMapping
    public CategoryPage searchCategories(@Argument String keyword, @Argument Integer page, @Argument Integer size) {
        return CategoryPage.from(categoryService.search(keyword, pageRequestFactory.create(page, size)));
    }

    @MutationMapping
    public Category createCategory(@Argument CategoryInput input) {
        adminMutationAuthorizer.requireAdmin();
        Category category = new Category();
        applyInput(category, input, true);
        entityValidator.validate(category);
        return categoryService.save(category);
    }

    @MutationMapping
    public Category updateCategory(@Argument Long id, @Argument CategoryInput input) {
        adminMutationAuthorizer.requireAdmin();
        Category category = categoryService.findById(id)
                .orElseThrow(() -> new GraphqlNotFoundException("Category not found"));
        applyInput(category, input, false);
        entityValidator.validate(category);
        return categoryService.save(category);
    }

    @MutationMapping
    public boolean deleteCategory(@Argument Long id) {
        adminMutationAuthorizer.requireAdmin();
        if (categoryService.findById(id).isEmpty()) {
            throw new GraphqlNotFoundException("Category not found");
        }
        try {
            categoryService.deleteById(id);
            return true;
        } catch (DataIntegrityViolationException exception) {
            throw new GraphqlBadRequestException("Cannot delete category because it is used by products");
        }
    }

    private void applyInput(Category category, CategoryInput input, boolean creating) {
        category.setName(input.name() == null ? null : input.name().trim());
        category.setDescription(input.description());
        if (creating || input.image() != null) {
            category.setImage(input.image());
        }
    }
}
