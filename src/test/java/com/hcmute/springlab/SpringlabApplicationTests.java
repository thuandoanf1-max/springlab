package com.hcmute.springlab;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.dto.UserResponse;
import com.hcmute.springlab.mapper.UserMapper;
import com.hcmute.springlab.repository.UserRepository;
import com.hcmute.springlab.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest(properties = {"spring.mail.username=", "spring.mail.password=", "app.admin.password="})
@AutoConfigureMockMvc
class SpringlabApplicationTests {

	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private com.hcmute.springlab.service.EmailService emailService;

	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private org.springframework.mail.javamail.JavaMailSender mailSender;

	@org.junit.jupiter.api.AfterEach
	void noSmtpCalls() {
		org.mockito.Mockito.verifyNoInteractions(emailService, mailSender);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private UserService userService;

	@Test
	void contextLoads() {
	}

	@Test
	void thymeleafItem5PagesUseTheGraphqlAjaxScripts() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/js/graphql-client.js")))
				.andExpect(content().string(containsString("/js/graphql-home.js")));

		MockHttpSession adminSession = adminSession();
		mockMvc.perform(get("/admin/products").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/js/graphql-product.js")))
				.andExpect(content().string(not(containsString("/js/product.js"))));
		mockMvc.perform(get("/admin/categories").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/js/graphql-category.js")))
				.andExpect(content().string(not(containsString("/js/category.js"))));
	}

	@Test
	void securitySupportsBcryptUsernameEmailLegacyMigrationAndLogout() throws Exception {
		User admin = new User(null, "phase1-admin", passwordEncoder.encode("secret123"), "Phase One Admin",
				"phase1-admin@example.com", null, true, "ADMIN");
		userRepository.save(admin);
		User legacyUser = new User(null, "phase1-legacy", "legacy123", "Legacy User",
				"phase1-legacy@example.com", null, true, "USER");
		userRepository.save(legacyUser);

		mockMvc.perform(get("/admin/categories"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/categories").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
				.andExpect(status().isForbidden());

		MvcResult adminLogin = mockMvc.perform(post("/login").with(csrf())
				.param("username", "phase1-admin")
				.param("password", "secret123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/categories"))
				.andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withUsername("phase1-admin").withRoles("ADMIN"))
				.andReturn();
		assertTrue(passwordEncoder.matches("secret123", userRepository.findById(admin.getId()).orElseThrow().getPassword()));

		mockMvc.perform(post("/login").with(csrf())
				.param("username", "phase1-admin@example.com")
				.param("password", "secret123"))
				.andExpect(redirectedUrl("/admin/categories"))
				.andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated().withUsername("phase1-admin").withRoles("ADMIN"));
		mockMvc.perform(post("/login").with(csrf())
				.param("username", "phase1-legacy")
				.param("password", "legacy123"))
				.andExpect(status().is3xxRedirection());
		assertTrue(passwordEncoder.matches("legacy123", userRepository.findById(legacyUser.getId()).orElseThrow().getPassword()));

		MockHttpSession session = (MockHttpSession) adminLogin.getRequest().getSession(false);
		mockMvc.perform(get("/admin/users").session(session)).andExpect(status().isOk());
		mockMvc.perform(post("/logout").session(session).with(csrf()))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void userDtoMapperHeaderAndPasswordEditAreSafe() throws Exception {
		User user = new User(null, "phase2-user", passwordEncoder.encode("old-password"), "Phase Two User",
				"phase2-user@example.com", null, true, "ADMIN");
		userRepository.save(user);

		UserResponse response = userMapper.toResponse(user);
		assertEquals("Phase Two User", response.fullname());
		assertTrue(Arrays.stream(UserResponse.class.getRecordComponents())
				.noneMatch(component -> component.getName().equals("password")));

		String originalPassword = user.getPassword();
		User blankPasswordUpdate = new User(user.getId(), user.getUsername(), "", user.getFullname(), user.getEmail(),
				null, true, user.getRole());
		userService.save(blankPasswordUpdate);
		assertEquals(originalPassword, userRepository.findById(user.getId()).orElseThrow().getPassword());

		User newPasswordUpdate = new User(user.getId(), user.getUsername(), "new-password", user.getFullname(), user.getEmail(),
				null, true, user.getRole());
		userService.save(newPasswordUpdate);
		assertTrue(passwordEncoder.matches("new-password", userRepository.findById(user.getId()).orElseThrow().getPassword()));

		MvcResult login = mockMvc.perform(post("/login").with(csrf())
				.param("username", user.getUsername())
				.param("password", "new-password"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		mockMvc.perform(get("/").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Phase Two User")))
				.andExpect(content().string(containsString("/images/avatar-placeholder.png")));
	}

	@Test
	void disabledUserCannotLogin() throws Exception {
		User disabledUser = new User(null, "phase2-disabled", passwordEncoder.encode("disabled-password"), "Disabled User",
				"phase2-disabled@example.com", null, false, "USER");
		userRepository.save(disabledUser);
		mockMvc.perform(post("/login").with(csrf())
				.param("username", "phase2-disabled")
				.param("password", "disabled-password"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void graphqlCrudRequiresAdminAndKeepsQueriesPublic() throws Exception {
		JsonNode publicQuery = execute("query { categories { id name } }");
		assertFalse(publicQuery.has("errors"));

		JsonNode unauthorizedMutation = execute("mutation { createCategory(input: { name: \"Unauthenticated category\" }) { id } }");
		assertTrue(unauthorizedMutation.has("errors"));
		assertEquals("Administrator authentication is required for mutations",
				unauthorizedMutation.path("errors").get(0).path("message").asText());

		MockHttpSession adminSession = adminSession();
		JsonNode invalidCategory = execute("mutation { createCategory(input: { name: \"   \" }) { id } }", adminSession);
		assertTrue(invalidCategory.has("errors"));
		assertEquals("Category name is required", invalidCategory.path("errors").get(0).path("message").asText());

		JsonNode invalidProductCategory = execute("mutation { createProduct(input: { name: \"Invalid Product\", quantity: 1, price: 1.0, categoryId: 999999 }) { id } }", adminSession);
		assertTrue(invalidProductCategory.has("errors"));
		assertEquals("Category does not exist", invalidProductCategory.path("errors").get(0).path("message").asText());

		JsonNode createdCategory = execute("mutation { createCategory(input: { name: \"Phase 3 Category\", description: \"Initial\", image: \"category.png\" }) { id name description image } }", adminSession);
		long categoryId = createdCategory.path("data").path("createCategory").path("id").asLong();
		assertTrue(categoryId > 0);

		JsonNode updatedCategory = execute("mutation($id: ID!) { updateCategory(id: $id, input: { name: \"Phase 3 Category Updated\", description: \"Updated\" }) { name description image } }", Map.of("id", categoryId), adminSession);
		assertEquals("category.png", updatedCategory.path("data").path("updateCategory").path("image").asText());
		JsonNode secondCategory = execute("mutation { createCategory(input: { name: \"Phase 4 Category\" }) { id } }", adminSession);
		long secondCategoryId = secondCategory.path("data").path("createCategory").path("id").asLong();

		long firstProductId = createProduct("Phase 3 Product Low", 10.0, categoryId, adminSession);
		long secondProductId = createProduct("Phase 3 Product High", 20.0, categoryId, adminSession);

		JsonNode productPage = execute("query { searchProducts(keyword: \"\", page: 0, size: 1) { content { id name } page size totalElements totalPages } }");
		JsonNode productPageData = productPage.path("data").path("searchProducts");
		assertEquals(1, productPageData.path("content").size());
		assertEquals(0, productPageData.path("page").asInt());
		assertEquals(1, productPageData.path("size").asInt());
		assertEquals(2, productPageData.path("totalElements").asInt());
		assertEquals(2, productPageData.path("totalPages").asInt());
		JsonNode productNextPage = execute("query { searchProducts(keyword: \"\", page: 1, size: 1) { content { id } page } }");
		assertEquals(1, productNextPage.path("data").path("searchProducts").path("page").asInt());
		assertEquals(1, productNextPage.path("data").path("searchProducts").path("content").size());
		JsonNode productSearch = execute("query { searchProducts(keyword: \"Low\", page: 0, size: 5) { content { name } totalElements totalPages } }");
		assertEquals("Phase 3 Product Low", productSearch.path("data").path("searchProducts").path("content").get(0).path("name").asText());
		assertEquals(1, productSearch.path("data").path("searchProducts").path("totalElements").asInt());

		JsonNode categoryPage = execute("query { searchCategories(keyword: \"\", page: 0, size: 1) { content { id name } page size totalElements totalPages } }");
		JsonNode categoryPageData = categoryPage.path("data").path("searchCategories");
		assertEquals(1, categoryPageData.path("content").size());
		assertEquals(0, categoryPageData.path("page").asInt());
		assertEquals(1, categoryPageData.path("size").asInt());
		assertEquals(2, categoryPageData.path("totalElements").asInt());
		assertEquals(2, categoryPageData.path("totalPages").asInt());
		JsonNode categoryNextPage = execute("query { searchCategories(keyword: \"\", page: 1, size: 1) { content { id } page } }");
		assertEquals(1, categoryNextPage.path("data").path("searchCategories").path("page").asInt());
		assertEquals(1, categoryNextPage.path("data").path("searchCategories").path("content").size());
		JsonNode categorySearch = execute("query { searchCategories(keyword: \"Updated\", page: 0, size: 5) { content { name } totalElements totalPages } }");
		assertEquals("Phase 3 Category Updated", categorySearch.path("data").path("searchCategories").path("content").get(0).path("name").asText());
		assertEquals(1, categorySearch.path("data").path("searchCategories").path("totalElements").asInt());

		JsonNode deleteInUseCategory = execute("mutation($id: ID!) { deleteCategory(id: $id) }", Map.of("id", categoryId), adminSession);
		assertTrue(deleteInUseCategory.has("errors"));
		assertEquals("Cannot delete category because it is used by products",
				deleteInUseCategory.path("errors").get(0).path("message").asText());

		JsonNode priceAscending = execute("query { productsByPriceAsc { id price category { id name } } }");
		JsonNode products = priceAscending.path("data").path("productsByPriceAsc");
		assertTrue(products.get(0).path("price").asDouble() <= products.get(1).path("price").asDouble());
		assertEquals(categoryId, products.get(0).path("category").path("id").asLong());

		JsonNode byCategory = execute("query($categoryId: ID!) { productsByCategory(categoryId: $categoryId) { id category { id name } } }", Map.of("categoryId", categoryId), null);
		assertEquals(2, byCategory.path("data").path("productsByCategory").size());
		for (JsonNode product : byCategory.path("data").path("productsByCategory")) {
			assertEquals(categoryId, product.path("category").path("id").asLong());
		}

		JsonNode updatedProduct = execute("mutation($id: ID!, $categoryId: ID!) { updateProduct(id: $id, input: { name: \"Phase 3 Product Low Updated\", quantity: 2, price: 5.0, categoryId: $categoryId }) { name quantity price image } }", Map.of("id", firstProductId, "categoryId", categoryId), adminSession);
		assertEquals("product.png", updatedProduct.path("data").path("updateProduct").path("image").asText());

		assertTrue(execute("mutation($id: ID!) { deleteProduct(id: $id) }", Map.of("id", firstProductId), adminSession).path("data").path("deleteProduct").asBoolean());
		assertTrue(execute("mutation($id: ID!) { deleteProduct(id: $id) }", Map.of("id", secondProductId), adminSession).path("data").path("deleteProduct").asBoolean());
		assertTrue(execute("mutation($id: ID!) { deleteCategory(id: $id) }", Map.of("id", categoryId), adminSession).path("data").path("deleteCategory").asBoolean());
		assertTrue(execute("mutation($id: ID!) { deleteCategory(id: $id) }", Map.of("id", secondCategoryId), adminSession).path("data").path("deleteCategory").asBoolean());
	}

	private long createProduct(String name, double price, long categoryId, MockHttpSession session) throws Exception {
		JsonNode response = execute("mutation($categoryId: ID!) { createProduct(input: { name: \"%s\", quantity: 1, price: %s, image: \"product.png\", categoryId: $categoryId }) { id category { id name } } }".formatted(name, price), Map.of("categoryId", categoryId), session);
		assertEquals(categoryId, response.path("data").path("createProduct").path("category").path("id").asLong());
		return response.path("data").path("createProduct").path("id").asLong();
	}

	private MockHttpSession adminSession() {
		if (userRepository.findByUsernameIgnoreCase("admin").isEmpty()) {
			userRepository.save(new User(null, "admin", passwordEncoder.encode("test-admin-password"),
					"Test Administrator", "test-admin@example.com", null, true, "ADMIN"));
		}
		MockHttpSession session = new MockHttpSession();
		SecurityContextImpl context = new SecurityContextImpl();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
		return session;
	}

	private JsonNode execute(String query) throws Exception {
		return execute(query, Map.of(), null);
	}

	private JsonNode execute(String query, MockHttpSession session) throws Exception {
		return execute(query, Map.of(), session);
	}

	private JsonNode execute(String query, Map<String, Object> variables, MockHttpSession session) throws Exception {
		MockHttpServletRequestBuilder request = post("/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("query", query, "variables", variables)));
		if (session != null) {
			request.session(session);
		}
		MvcResult result = mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
