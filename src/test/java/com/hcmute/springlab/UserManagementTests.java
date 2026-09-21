package com.hcmute.springlab;

import com.hcmute.springlab.dto.UserResponse;
import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.mapper.UserMapper;
import com.hcmute.springlab.repository.OtpTokenRepository;
import com.hcmute.springlab.repository.UserRepository;
import com.hcmute.springlab.service.EmailService;
import com.hcmute.springlab.service.UserService;
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

import java.util.Arrays;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-management-tests;DB_CLOSE_DELAY=-1",
        "spring.mail.username=", "spring.mail.password=", "app.admin.password="
})
@AutoConfigureMockMvc
class UserManagementTests {

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        otpTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void noRealMailBoundaryWasUsed() {
        verifyNoInteractions(emailService, mailSender);
    }

    @Test
    void adminCanListUsersAndResponseNeverExposesPassword() throws Exception {
        User stored = saveDirect("listed-user", "Listed Person", "listed@example.com", "USER", true);

        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Listed Person")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(stored.getPassword()))));

        assertTrue(Arrays.stream(UserResponse.class.getRecordComponents())
                .noneMatch(component -> "password".equals(component.getName())));
        assertEquals("listed-user", userMapper.toResponse(stored).username());
    }

    @Test
    void adminCreateValidatesRoleEnabledAndEncodesPassword() throws Exception {
        mockMvc.perform(post("/admin/users/create")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "created-user")
                        .param("password", "secret123")
                        .param("fullname", "Created User")
                        .param("email", "created@example.com")
                        .param("image", "avatar.png")
                        .param("role", "USER")
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        User created = userRepository.findByUsernameIgnoreCase("created-user").orElseThrow();
        assertNotEquals("secret123", created.getPassword());
        assertTrue(passwordEncoder.matches("secret123", created.getPassword()));
        assertEquals("USER", created.getRole());
        assertFalse(created.getEnabled());

        long countBeforeInvalidRole = userRepository.count();
        mockMvc.perform(post("/admin/users/create")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "invalid-role")
                        .param("password", "secret123")
                        .param("fullname", "Invalid Role")
                        .param("email", "invalid-role@example.com")
                        .param("role", "SUPER_ADMIN")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user/form"));
        assertEquals(countBeforeInvalidRole, userRepository.count());
    }

    @Test
    void duplicateUsernameAndCaseInsensitiveEmailAreRejected() throws Exception {
        saveDirect("existing-user", "Existing User", "existing@example.com", "USER", true);

        mockMvc.perform(post("/admin/users/create")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "EXISTING-USER")
                        .param("password", "secret123")
                        .param("fullname", "Duplicate Username")
                        .param("email", "other@example.com")
                        .param("role", "USER").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(model().hasErrors());
        assertEquals(1, userRepository.count());

        mockMvc.perform(post("/admin/users/create")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "other-user")
                        .param("password", "secret123")
                        .param("fullname", "Duplicate Email")
                        .param("email", "EXISTING@EXAMPLE.COM")
                        .param("role", "USER").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(model().hasErrors());
        assertEquals(1, userRepository.count());
    }

    @Test
    void editBlankPasswordKeepsHashAndNewPasswordIsBcryptOnce() throws Exception {
        User existing = saveDirect("edit-user", "Before Edit", "edit@example.com", "USER", true);
        String originalHash = existing.getPassword();

        mockMvc.perform(post("/admin/users/edit/{id}", existing.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "attempted-rename")
                        .param("password", "")
                        .param("fullname", "After Blank Password Edit")
                        .param("email", "edited@example.com")
                        .param("image", "edited.png")
                        .param("role", "ADMIN").param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        User afterBlank = userRepository.findById(existing.getId()).orElseThrow();
        assertEquals("edit-user", afterBlank.getUsername());
        assertEquals(originalHash, afterBlank.getPassword());
        assertEquals("ADMIN", afterBlank.getRole());
        assertFalse(afterBlank.getEnabled());

        mockMvc.perform(post("/admin/users/edit/{id}", existing.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "edit-user")
                        .param("password", "new-secret")
                        .param("fullname", "After Password Edit")
                        .param("email", "edited@example.com")
                        .param("role", "USER").param("enabled", "true"))
                .andExpect(status().is3xxRedirection());

        User afterNewPassword = userRepository.findById(existing.getId()).orElseThrow();
        assertNotEquals("new-secret", afterNewPassword.getPassword());
        assertNotEquals(originalHash, afterNewPassword.getPassword());
        assertTrue(passwordEncoder.matches("new-secret", afterNewPassword.getPassword()));
    }

    @Test
    void searchRunsCaseInsensitiveInDatabaseForUsernameFullnameAndEmail() {
        saveDirect("alpha-login", "First Person", "one@example.com", "USER", true);
        saveDirect("beta-login", "Special Full Name", "two@example.com", "USER", true);
        saveDirect("gamma-login", "Third Person", "unique-mail@example.com", "ADMIN", true);

        assertEquals("alpha-login", firstSearchResult("ALPHA").getUsername());
        assertEquals("beta-login", firstSearchResult("special full").getUsername());
        assertEquals("gamma-login", firstSearchResult("UNIQUE-MAIL@").getUsername());
    }

    @Test
    void listUsesServerPaginationPreservesKeywordAndReportsTotalCount() throws Exception {
        for (int i = 0; i < 12; i++) {
            saveDirect("page-user-" + i, "Paging Person " + i, "page" + i + "@example.com", "USER", true);
        }

        MvcResult result = mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "1").param("size", "5").param("keyword", "page"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "page"))
                .andExpect(model().attribute("totalUsers", 12L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("keyword=page")))
                .andReturn();

        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("userPage");
        assertEquals(1, page.getNumber());
        assertEquals(5, page.getSize());
        assertEquals(12, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(12, userService.count());
    }

    @Test
    void deleteWorksButCurrentAdminAndUsersWithOtpHistoryAreProtected() throws Exception {
        User deletable = saveDirect("delete-me", "Delete Me", "delete@example.com", "USER", true);
        mockMvc.perform(post("/admin/users/delete/{id}", deletable.getId())
                        .with(user("current-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "User deleted successfully"));
        assertFalse(userRepository.existsById(deletable.getId()));

        User currentAdmin = saveDirect("current-admin", "Current Admin", "admin@example.com", "ADMIN", true);
        mockMvc.perform(post("/admin/users/delete/{id}", currentAdmin.getId())
                        .with(user("current-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "You cannot delete your own account"));
        assertTrue(userRepository.existsById(currentAdmin.getId()));

        User withOtpHistory = saveDirect("otp-owner", "OTP Owner", "otp-owner@example.com", "USER", true);
        OtpToken token = new OtpToken();
        token.setUser(withOtpHistory);
        token.setCode("123456");
        token.setType(OtpType.REGISTER);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setUsed(true);
        otpTokenRepository.save(token);
        mockMvc.perform(post("/admin/users/delete/{id}", withOtpHistory.getId())
                        .with(user("current-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Cannot delete user because OTP history exists"));
        assertTrue(userRepository.existsById(withOtpHistory.getId()));
    }

    @Test
    void onlyAdminCanAccessUserManagementAndDeleteRequiresCsrf() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("regular").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        User target = saveDirect("csrf-target", "CSRF Target", "csrf@example.com", "USER", true);
        mockMvc.perform(post("/admin/users/delete/{id}", target.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.existsById(target.getId()));
    }

    private User firstSearchResult(String keyword) {
        Page<User> result = userService.search(keyword, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        return result.getContent().getFirst();
    }

    private User saveDirect(String username, String fullname, String email, String role, boolean enabled) {
        return userRepository.save(new User(null, username, passwordEncoder.encode("initial-password"),
                fullname, email, null, enabled, role));
    }
}
