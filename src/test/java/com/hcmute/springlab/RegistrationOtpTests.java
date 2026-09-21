package com.hcmute.springlab;

import com.hcmute.springlab.dto.RegisterRequest;
import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.OtpTokenRepository;
import com.hcmute.springlab.repository.UserRepository;
import com.hcmute.springlab.security.CustomUserDetailsService;
import com.hcmute.springlab.service.EmailService;
import com.hcmute.springlab.service.OtpService;
import com.hcmute.springlab.service.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No @Transactional on this class: each call must commit/roll back its real service transaction.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-otp-tests;DB_CLOSE_DELAY=-1",
        "spring.mail.username=", "spring.mail.password=", "app.admin.password=",
        "app.otp.expiration-minutes=5", "app.otp.resend-cooldown-seconds=60"
})
@AutoConfigureMockMvc
class RegistrationOtpTests {
    private static final String EMAIL = "registrant@example.com";
    private static final String PASSWORD = "secure-password";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired OtpTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired CustomUserDetailsService userDetails;
    @Autowired OtpService otpService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService mail;
    // Second boundary: even an accidental call bypassing EmailService cannot send real SMTP.
    @MockitoBean JavaMailSender smtp;

    @BeforeEach
    void cleanIsolatedDatabase() {
        tokens.deleteAll();
        users.deleteAll();
        ReflectionTestUtils.setField(otpService, "secureRandom", new SecureRandom());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @AfterEach
    void noRealSmtp() {
        verifyNoInteractions(smtp);
        ReflectionTestUtils.setField(otpService, "secureRandom", new SecureRandom());
    }

    @Test
    void registrationAndVerificationPagesArePublic() throws Exception {
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("register"));
        mvc.perform(get("/verify-otp").param("email", EMAIL))
                .andExpect(status().isOk()).andExpect(view().name("verify-otp"));
        verifyNoInteractions(mail);
    }

    @Test
    void validRegistrationPersistsDisabledUserBcryptAndExpiringRegisterOtp() throws Exception {
        mvc.perform(registerPost().param("role", "ADMIN").param("enabled", "true"))
                .andExpect(redirectedUrl("/verify-otp?email=" + EMAIL)).andExpect(unauthenticated());
        User user = user();
        assertEquals(1, users.count());
        assertEquals("registrant", user.getUsername());
        assertEquals(EMAIL, user.getEmail());
        assertEquals("Registration User", user.getFullname());
        assertEquals("USER", user.getRole());
        assertEquals(Boolean.FALSE, user.getEnabled());
        assertNotEquals(PASSWORD, user.getPassword());
        assertTrue(user.getPassword().startsWith("$2"));
        assertTrue(encoder.matches(PASSWORD, user.getPassword()));
        OtpToken otp = current();
        assertEquals(1, tokens.count());
        assertEquals(user.getId(), otp.getUser().getId());
        assertEquals(OtpType.REGISTER, otp.getType());
        assertFalse(otp.isUsed());
        assertTrue(otp.getCode().matches("[0-9]{6}"));
        assertTrue(otp.getExpiresAt().isAfter(otp.getCreatedAt()));
        assertTrue(otp.getExpiresAt().isAfter(otp.getCreatedAt().plusMinutes(4)));
        assertFalse(otp.getExpiresAt().isAfter(otp.getCreatedAt().plusMinutes(5)));
        verify(mail).sendRegistrationOtp(EMAIL, otp.getCode(), 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "email", "uppercase-email"})
    void duplicatesDoNotCreateUsersTokensOrMail(String duplicate) {
        registration.register(request());
        RegisterRequest other = request();
        if (duplicate.equals("username")) other.setEmail("another@example.com");
        else other.setUsername("another-user");
        if (duplicate.equals("uppercase-email")) other.setEmail(EMAIL.toUpperCase(java.util.Locale.ROOT));
        clearInvocations(mail);
        assertThrows(IllegalArgumentException.class, () -> registration.register(other));
        assertEquals(1, users.count());
        assertEquals(1, tokens.count());
        assertFalse(current().isUsed());
        verifyNoInteractions(mail);
    }

    @ParameterizedTest
    @CsvSource(value = {"email|invalid-email", "password|short", "confirmPassword|different-password",
            "username|' '", "fullname|' '", "email|' '", "password|' '", "confirmPassword|' '"}, delimiter = '|')
    void invalidRegistrationIsRejectedBeforePersistence(String field, String value) throws Exception {
        MockHttpServletRequestBuilder post = registerPost();
        // Replace the field rather than adding a second parameter value.
        mvc.perform(post.with(request -> { request.setParameter(field, value); return request; }))
                .andExpect(status().isOk()).andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", field));
        assertEmpty();
        verifyNoInteractions(mail);
    }

    @Test
    void missingRequiredFieldsReturnsValidationErrorsNotServerError() throws Exception {
        mvc.perform(post("/register").with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "password", "email", "username"));
        assertEmpty();
        verifyNoInteractions(mail);
    }

    @Test
    void serviceRejectsPasswordMismatchWithoutSideEffects() {
        RegisterRequest request = request();
        request.setConfirmPassword("different");
        assertThrows(IllegalArgumentException.class, () -> registration.register(request));
        assertEmpty();
        verifyNoInteractions(mail);
    }

    @Test
    void bcryptLookingInputIsStillEncodedAsNewRegistrationPassword() {
        RegisterRequest request = request();
        request.setPassword("$2a$literal-password");
        request.setConfirmPassword(request.getPassword());
        registration.register(request);
        assertNotEquals(request.getPassword(), user().getPassword());
        assertTrue(encoder.matches(request.getPassword(), user().getPassword()));
    }

    @Test
    void correctOtpEnablesAccountAndCannotBeReused() throws Exception {
        registration.register(request());
        OtpToken otp = current();
        mvc.perform(post("/verify-otp").with(csrf()).param("email", EMAIL).param("code", otp.getCode()))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attributeExists("success"))
                .andExpect(unauthenticated());
        assertEquals(Boolean.TRUE, user().getEnabled());
        assertTrue(tokens.findById(otp.getId()).orElseThrow().isUsed());
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, otp.getCode()));
        assertTrue(tokens.findById(otp.getId()).orElseThrow().isUsed());
    }

    @Test
    void wrongOtpDoesNotConsumeTokenOrEnableUser() {
        registration.register(request());
        OtpToken otp = current();
        String wrong = otp.getCode().equals("000000") ? "000001" : "000000";
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, wrong));
        assertDisabledAndUnused(otp);
    }

    @Test
    void expiredOtpIsRejectedWithoutEnablingUser() {
        registration.register(request());
        OtpToken otp = current();
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokens.saveAndFlush(otp);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> registration.verify(EMAIL, otp.getCode()));
        assertTrue(failure.getMessage().contains("expired"));
        assertDisabledAndUnused(otp);
    }

    @Test
    void alreadyUsedTokenCannotEnableDisabledUser() {
        registration.register(request());
        OtpToken otp = current();
        otp.setUsed(true);
        tokens.saveAndFlush(otp);
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, otp.getCode()));
        assertEquals(Boolean.FALSE, user().getEnabled());
        assertTrue(tokens.findById(otp.getId()).orElseThrow().isUsed());
    }

    @Test
    void resendInvalidatesOldCodeAndNewCodeCanVerify() throws Exception {
        deterministicCodes(123456, 654321);
        registration.register(request());
        OtpToken old = current();
        allowResend(old);
        mvc.perform(post("/register/resend-otp").with(csrf()).param("email", EMAIL))
                .andExpect(redirectedUrl("/verify-otp?email=" + EMAIL))
                .andExpect(flash().attributeExists("message"));
        OtpToken fresh = current();
        assertNotEquals(old.getId(), fresh.getId());
        assertNotEquals(old.getCode(), fresh.getCode());
        assertEquals(2, tokens.count());
        assertTrue(tokens.findById(old.getId()).orElseThrow().isUsed());
        assertFalse(fresh.isUsed());
        assertEquals(Boolean.FALSE, user().getEnabled());
        verify(mail).sendRegistrationOtp(EMAIL, old.getCode(), 5);
        verify(mail).sendRegistrationOtp(EMAIL, fresh.getCode(), 5);
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, old.getCode()));
        assertDisabledAndUnused(fresh);
        registration.verify(EMAIL, fresh.getCode());
        assertEquals(Boolean.TRUE, user().getEnabled());
        assertTrue(tokens.findById(fresh.getId()).orElseThrow().isUsed());
    }

    @Test
    void resendMustNotReactivateOldCodeWhenRandomGeneratorRepeats() {
        deterministicCodes(123456, 123456, 654321);
        registration.register(request());
        OtpToken old = current();
        allowResend(old);
        registration.resend(EMAIL);
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, old.getCode()));
        assertEquals(Boolean.FALSE, user().getEnabled());
        registration.verify(EMAIL, current().getCode());
        assertEquals(Boolean.TRUE, user().getEnabled());
    }

    @Test
    void cooldownDoesNotAlterTokenOrSendAnotherEmail() {
        registration.register(request());
        OtpToken otp = current();
        clearInvocations(mail);
        assertThrows(IllegalStateException.class, () -> registration.resend(EMAIL));
        assertEquals(1, tokens.count());
        assertEquals(otp.getCode(), current().getCode());
        assertDisabledAndUnused(otp);
        verifyNoInteractions(mail);
    }

    @Test
    void verifiedAccountCannotResendRegistrationOtp() {
        registration.register(request());
        registration.verify(EMAIL, current().getCode());
        clearInvocations(mail);
        assertThrows(IllegalArgumentException.class, () -> registration.resend(EMAIL));
        assertEquals(1, tokens.count());
        assertTrue(tokens.findAll().getFirst().isUsed());
        assertEquals(Boolean.TRUE, user().getEnabled());
        verifyNoInteractions(mail);
    }

    @Test
    void mailFailureRollsBackNewUserAndOtpAndAllowsRetry() {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tokens.flush();
            assertEquals(1, users.count());
            assertEquals(1, tokens.count());
            throw new MailSendException("Simulated SMTP failure");
        }).when(mail).sendRegistrationOtp(anyString(), anyString(), anyLong());
        assertThrows(MailSendException.class, () -> registration.register(request()));
        assertEmpty();
        verify(mail).sendRegistrationOtp(eq(EMAIL), anyString(), eq(5L));
        doNothing().when(mail).sendRegistrationOtp(anyString(), anyString(), anyLong());
        registration.register(request());
        assertEquals(1, users.count());
        assertEquals(1, tokens.count());
    }

    @Test
    void resendMailFailureRollsBackInvalidationAndNewToken() {
        registration.register(request());
        OtpToken old = current();
        allowResend(old);
        clearInvocations(mail);
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tokens.flush();
            assertEquals(2, tokens.count());
            assertTrue(tokens.findById(old.getId()).orElseThrow().isUsed());
            throw new MailSendException("Simulated SMTP failure");
        }).when(mail).sendRegistrationOtp(anyString(), anyString(), anyLong());
        assertThrows(MailSendException.class, () -> registration.resend(EMAIL));
        assertEquals(1, users.count());
        assertEquals(1, tokens.count());
        assertEquals(old.getId(), current().getId());
        assertEquals(old.getCode(), current().getCode());
        assertDisabledAndUnused(old);
        verify(mail).sendRegistrationOtp(eq(EMAIL), anyString(), eq(5L));
        registration.verify(EMAIL, old.getCode());
        assertEquals(Boolean.TRUE, user().getEnabled());
    }

    @Test
    void loginUsesRealAuthenticationBeforeAndAfterVerification() throws Exception {
        registration.register(request());
        assertFalse(userDetails.loadUserByUsername("registrant").isEnabled());
        mvc.perform(post("/login").with(csrf()).param("username", "registrant").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        registration.verify(EMAIL, current().getCode());
        assertTrue(userDetails.loadUserByUsername(EMAIL).isEnabled());
        for (String identifier : new String[]{"registrant", EMAIL}) {
            var result = mvc.perform(post("/login").with(csrf()).param("username", identifier).param("password", PASSWORD))
                    .andExpect(redirectedUrl("/")).andExpect(authenticated().withUsername("registrant").withRoles("USER"))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            mvc.perform(get("/admin/users").session(session)).andExpect(status().isForbidden());
        }
    }

    @Test
    void resetOnlyTokenCannotEnableRegistration() {
        registration.register(request());
        OtpToken reset = current();
        reset.setType(OtpType.PASSWORD_RESET);
        tokens.saveAndFlush(reset);
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, reset.getCode()));
        assertDisabledAndUnused(reset);
    }

    @Test
    void registrationVerificationIgnoresResetTokenEvenWhenItIsNewer() {
        registration.register(request());
        OtpToken register = current();
        OtpToken reset = new OtpToken();
        reset.setUser(user());
        reset.setType(OtpType.PASSWORD_RESET);
        reset.setCode(register.getCode().equals("000000") ? "000001" : "000000");
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        tokens.saveAndFlush(reset);
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, reset.getCode()));
        assertDisabledAndUnused(register);
        registration.verify(EMAIL, register.getCode());
        assertEquals(Boolean.TRUE, user().getEnabled());
        assertFalse(tokens.findById(reset.getId()).orElseThrow().isUsed());
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("registrant");
        request.setFullname("Registration User");
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        request.setConfirmPassword(PASSWORD);
        return request;
    }

    private MockHttpServletRequestBuilder registerPost() {
        return post("/register").with(csrf()).param("username", "registrant")
                .param("fullname", "Registration User").param("email", EMAIL)
                .param("password", PASSWORD).param("confirmPassword", PASSWORD);
    }

    private User user() { return users.findByUsername("registrant").orElseThrow(); }
    private OtpToken current() {
        return tokens.findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user(), OtpType.REGISTER).orElseThrow();
    }
    private void assertEmpty() { assertEquals(0, users.count()); assertEquals(0, tokens.count()); }
    private void assertDisabledAndUnused(OtpToken otp) {
        assertEquals(Boolean.FALSE, user().getEnabled());
        assertFalse(tokens.findById(otp.getId()).orElseThrow().isUsed());
    }
    private void allowResend(OtpToken otp) {
        // createdAt is updatable=false in JPA. Change this fixture's timestamp directly in isolated H2.
        jdbc.update("update otp_tokens set created_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(120)), otp.getId());
    }
    private void deterministicCodes(int first, int... remaining) {
        SecureRandom random = mock(SecureRandom.class);
        Integer[] rest = java.util.Arrays.stream(remaining).boxed().toArray(Integer[]::new);
        when(random.nextInt(1_000_000)).thenReturn(first, rest);
        ReflectionTestUtils.setField(otpService, "secureRandom", random);
    }
}
