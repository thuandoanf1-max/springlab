package com.hcmute.springlab;

import com.hcmute.springlab.dto.ProductResponse;
import com.hcmute.springlab.entity.Category;
import com.hcmute.springlab.entity.Product;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.CategoryRepository;
import com.hcmute.springlab.repository.OtpTokenRepository;
import com.hcmute.springlab.repository.ProductRepository;
import com.hcmute.springlab.repository.UserRepository;
import com.hcmute.springlab.service.EmailService;
import com.hcmute.springlab.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-ownership-tests;DB_CLOSE_DELAY=-1",
        "spring.mail.username=", "spring.mail.password=", "app.admin.password="
})
@AutoConfigureMockMvc
class ProductOwnershipTests {

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        otpTokenRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void noMailWasSent() {
        verifyNoInteractions(emailService, mailSender);
    }

    @Test
    void restCrudAssignsAuthenticatedOwnerPreservesCategoryAndNeverExposesPassword() throws Exception {
        User admin = saveUser("product-admin", "Product Administrator", "ADMIN");
        Category firstCategory = saveCategory("REST Category");
        Category secondCategory = saveCategory("Updated REST Category");

        MvcResult createdResult = mockMvc.perform(multipart("/admin/api/products")
                        .with(user(admin.getUsername()).roles("ADMIN")).with(csrf())
                        .param("name", "REST Owned Product")
                        .param("quantity", "4")
                        .param("price", "15.5")
                        .param("categoryId", firstCategory.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(firstCategory.getId()))
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.userFullname").value(admin.getFullname()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        long productId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).path("id").asLong();
        Product created = productRepository.findById(productId).orElseThrow();
        assertEquals(firstCategory.getId(), created.getCategory().getId());
        assertEquals(admin.getId(), created.getUser().getId());

        mockMvc.perform(multipart("/admin/api/products/{id}", productId)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(user(admin.getUsername()).roles("ADMIN")).with(csrf())
                        .param("name", "REST Product Updated")
                        .param("quantity", "8")
                        .param("price", "20")
                        .param("categoryId", secondCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(secondCategory.getId()))
                .andExpect(jsonPath("$.userId").value(admin.getId()));

        Product updated = productRepository.findById(productId).orElseThrow();
        assertEquals(secondCategory.getId(), updated.getCategory().getId());
        assertEquals(admin.getId(), updated.getUser().getId());

        mockMvc.perform(delete("/admin/api/products/{id}", productId)
                        .with(user(admin.getUsername()).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertFalse(productRepository.existsById(productId));
    }

    @Test
    void legacyProductWithNullOwnerRemainsReadableInRestAndGraphql() throws Exception {
        Category category = saveCategory("Legacy Category");
        Product legacy = saveProduct("Legacy Product", category, null);

        ProductResponse response = ProductResponse.from(legacy);
        assertNull(response.userId());
        assertNull(response.username());

        mockMvc.perform(get("/admin/api/products/{id}", legacy.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        JsonNode graphql = executeGraphql(
                "query($id: ID!) { productById(id: $id) { id name category { id name } owner { id username fullname } } }",
                Map.of("id", legacy.getId()), null);
        assertFalse(graphql.has("errors"));
        assertTrue(graphql.path("data").path("productById").path("owner").isNull());
        assertEquals(category.getId(), graphql.path("data").path("productById").path("category").path("id").asLong());
    }

    @Test
    void graphqlCreateAssignsAdminOwnerWithoutAcceptingOwnerInput() throws Exception {
        User admin = saveUser("graphql-owner", "GraphQL Owner", "ADMIN");
        Category category = saveCategory("GraphQL Ownership Category");
        JsonNode result = executeGraphql("""
                mutation($categoryId: ID!) {
                  createProduct(input: {name: "GraphQL Owned", quantity: 2, price: 9.5, categoryId: $categoryId}) {
                    id name category { id } owner { id username fullname }
                  }
                }
                """, Map.of("categoryId", category.getId()), admin);

        assertFalse(result.has("errors"), result.toString());
        JsonNode product = result.path("data").path("createProduct");
        assertEquals(category.getId(), product.path("category").path("id").asLong());
        assertEquals(admin.getId(), product.path("owner").path("id").asLong());
        assertEquals(admin.getUsername(), product.path("owner").path("username").asText());
        assertFalse(result.toString().contains(admin.getPassword()));
    }

    @Test
    void searchPaginationCategoryOwnerFiltersAndGroupedCountsWork() {
        User firstOwner = saveUser("first-owner", "First Owner", "ADMIN");
        User secondOwner = saveUser("second-owner", "Second Owner", "USER");
        Category firstCategory = saveCategory("First Category");
        Category secondCategory = saveCategory("Second Category");
        for (int i = 0; i < 6; i++) {
            saveProduct("Filter Widget " + i, firstCategory, firstOwner);
        }
        saveProduct("Filter Widget Other Owner", firstCategory, secondOwner);
        saveProduct("Filter Widget Other Category", secondCategory, firstOwner);

        Page<Product> firstPage = productService.search("WIDGET", firstCategory.getId(), firstOwner.getId(),
                PageRequest.of(0, 2));
        Page<Product> secondPage = productService.search("widget", firstCategory.getId(), firstOwner.getId(),
                PageRequest.of(1, 2));
        assertEquals(6, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(1, secondPage.getNumber());
        assertTrue(firstPage.getContent().stream().allMatch(product ->
                product.getCategory().getId().equals(firstCategory.getId())
                        && product.getUser().getId().equals(firstOwner.getId())));

        assertEquals(7, productService.countByUserId(firstOwner.getId()));
        Map<Long, Long> counts = productService.countByUserIds(
                java.util.List.of(firstOwner.getId(), secondOwner.getId()));
        assertEquals(7L, counts.get(firstOwner.getId()));
        assertEquals(1L, counts.get(secondOwner.getId()));
    }

    @Test
    void userListDisplaysGroupedProductCountAndDeleteOwnerIsBlocked() throws Exception {
        User admin = saveUser("management-admin", "Management Admin", "ADMIN");
        User owner = saveUser("managed-owner", "Managed Owner", "USER");
        Category category = saveCategory("Managed Category");
        saveProduct("Owned One", category, owner);
        saveProduct("Owned Two", category, owner);

        MvcResult listResult = mockMvc.perform(get("/admin/users")
                        .with(user(admin.getUsername()).roles("ADMIN"))
                        .param("keyword", owner.getUsername()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Managed Owner")))
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<Long, Long> productCounts = (Map<Long, Long>) listResult.getModelAndView().getModel().get("productCounts");
        assertEquals(2L, productCounts.get(owner.getId()));

        mockMvc.perform(post("/admin/users/delete/{id}", owner.getId())
                        .with(user(admin.getUsername()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Cannot delete user because they own products"));
        assertTrue(userRepository.existsById(owner.getId()));
        assertEquals(2, productRepository.countByUserId(owner.getId()));
    }

    @Test
    void nonAdminCannotCreateProductThroughRestOrGraphql() throws Exception {
        User regularUser = saveUser("regular-product-user", "Regular Product User", "USER");
        Category category = saveCategory("Protected Category");

        mockMvc.perform(multipart("/admin/api/products")
                        .with(user(regularUser.getUsername()).roles("USER")).with(csrf())
                        .param("name", "Forbidden REST Product")
                        .param("quantity", "1").param("price", "1")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isForbidden());

        JsonNode graphql = executeGraphql("""
                mutation($categoryId: ID!) {
                  createProduct(input: {name: "Forbidden GraphQL Product", quantity: 1, price: 1, categoryId: $categoryId}) { id }
                }
                """, Map.of("categoryId", category.getId()), regularUser);
        assertTrue(graphql.has("errors"));
        assertEquals("Administrator authentication is required for mutations",
                graphql.path("errors").get(0).path("message").asText());
        assertEquals(0, productRepository.count());
    }

    private JsonNode executeGraphql(String query, Map<String, Object> variables, User authenticatedUser) throws Exception {
        var request = post("/graphql")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("query", query, "variables", variables)));
        if (authenticatedUser != null) {
            request.with(user(authenticatedUser.getUsername()).roles(authenticatedUser.getRole()));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User saveUser(String username, String fullname, String role) {
        return userRepository.save(new User(null, username, passwordEncoder.encode("password123"), fullname,
                username + "@example.com", null, true, role));
    }

    private Category saveCategory(String name) {
        return categoryRepository.save(new Category(null, name, null, null));
    }

    private Product saveProduct(String name, Category category, User owner) {
        return productRepository.save(new Product(null, name, 1, 10.0, null, category, owner));
    }
}
