package com.hcmute.springlab;

import com.hcmute.springlab.dto.ResetPasswordRequest;
import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.OtpTokenRepository;
import com.hcmute.springlab.repository.UserRepository;
import com.hcmute.springlab.service.EmailService;
import com.hcmute.springlab.service.OtpService;
import com.hcmute.springlab.service.PasswordResetService;
import com.hcmute.springlab.service.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password-reset-tests;DB_CLOSE_DELAY=-1",
        "spring.mail.username=", "spring.mail.password=", "app.admin.password=",
        "app.otp.expiration-minutes=5", "app.otp.resend-cooldown-seconds=60"
})
@AutoConfigureMockMvc
class PasswordResetTests {
    private static final String EMAIL = "reset-user@example.com";
    private static final String OLD_PASSWORD = "old-password";
    private static final String NEW_PASSWORD = "new-password";

    @Autowired MockMvc mvc;
    @Autowired PasswordResetService passwordReset;
    @Autowired RegistrationService registration;
    @Autowired OtpService otpService;
    @Autowired UserRepository users;
    @Autowired OtpTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService mail;
    @MockitoBean JavaMailSender smtp;

    @BeforeEach
    void cleanDatabase() {
        tokens.deleteAll();
        users.deleteAll();
        ReflectionTestUtils.setField(otpService, "secureRandom", new SecureRandom());
        createUser(true);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @AfterEach
    void noRealEmail() {
        verifyNoInteractions(smtp);
        ReflectionTestUtils.setField(otpService, "secureRandom", new SecureRandom());
    }

    @Test
    void forgotAndResetPagesArePublic() throws Exception {
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk()).andExpect(view().name("forgot-password")).andExpect(unauthenticated());
        mvc.perform(get("/reset-password").param("email", EMAIL))
                .andExpect(status().isOk()).andExpect(view().name("reset-password")).andExpect(unauthenticated());
        verifyNoInteractions(mail);
    }

    @Test
    void validForgotRequestCreatesExpiringResetOtpAndSendsResetMail() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", EMAIL))
                .andExpect(redirectedUrl("/reset-password?email=reset-user%40example.com"))
                .andExpect(flash().attribute("message", "If the email exists, a verification code has been sent."));
        OtpToken otp = currentResetOtp();
        assertEquals(OtpType.PASSWORD_RESET, otp.getType());
        assertFalse(otp.isUsed());
        assertTrue(otp.getExpiresAt().isAfter(otp.getCreatedAt()));
        assertTrue(otp.getExpiresAt().isAfter(otp.getCreatedAt().plusMinutes(4)));
        assertTrue(encoder.matches(OLD_PASSWORD, user().getPassword()));
        verify(mail).sendPasswordResetOtp(EMAIL, otp.getCode(), 5);
        verify(mail, never()).sendRegistrationOtp(anyString(), anyString(), anyLong());
    }

    @Test
    void unknownEmailGetsSamePublicResponseWithoutOtpOrMail() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "missing@example.com"))
                .andExpect(redirectedUrl("/reset-password?email=missing%40example.com"))
                .andExpect(flash().attribute("message", "If the email exists, a verification code has been sent."));
        assertEquals(0, tokens.count());
        verifyNoInteractions(mail);
    }

    @Test
    void wrongResetOtpDoesNotChangePasswordOrConsumeOtp() {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        ResetPasswordRequest request = resetRequest(otp.getCode().equals("000000") ? "000001" : "000000");
        assertThrows(IllegalArgumentException.class, () -> passwordReset.resetPassword(request));
        assertOldPasswordAndUnused(otp);
    }

    @Test
    void expiredResetOtpFailsWithoutChangingPassword() {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        otp.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        tokens.saveAndFlush(otp);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> passwordReset.resetPassword(resetRequest(otp.getCode())));
        assertTrue(failure.getMessage().contains("expired"));
        assertOldPasswordAndUnused(otp);
    }

    @Test
    void usedResetOtpCannotBeReused() {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        passwordReset.resetPassword(resetRequest(otp.getCode()));
        assertThrows(IllegalArgumentException.class, () -> passwordReset.resetPassword(resetRequest(otp.getCode())));
        assertTrue(tokens.findById(otp.getId()).orElseThrow().isUsed());
        assertTrue(encoder.matches(NEW_PASSWORD, user().getPassword()));
    }

    @Test
    void registerOtpCannotResetPassword() {
        OtpToken registerOtp = token(OtpType.REGISTER, "123456");
        assertThrows(IllegalArgumentException.class,
                () -> passwordReset.resetPassword(resetRequest(registerOtp.getCode())));
        assertOldPasswordAndUnused(registerOtp);
    }

    @Test
    void passwordResetOtpCannotVerifyRegistrationOrEnableAccount() {
        User disabled = user();
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        OtpToken resetOtp = token(OtpType.PASSWORD_RESET, "234567");
        assertThrows(IllegalArgumentException.class, () -> registration.verify(EMAIL, resetOtp.getCode()));
        assertEquals(Boolean.FALSE, user().getEnabled());
        assertFalse(tokens.findById(resetOtp.getId()).orElseThrow().isUsed());
    }

    @Test
    void correctOtpResetsToBcryptAndOldLoginFailsWhileNewLoginSucceeds() throws Exception {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        mvc.perform(post("/reset-password").with(csrf()).param("email", EMAIL).param("otp", otp.getCode())
                        .param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login?resetSuccess")).andExpect(unauthenticated());
        String stored = user().getPassword();
        assertNotEquals(NEW_PASSWORD, stored);
        assertTrue(stored.startsWith("$2"));
        assertTrue(encoder.matches(NEW_PASSWORD, stored));
        assertFalse(encoder.matches(OLD_PASSWORD, stored));
        assertTrue(tokens.findById(otp.getId()).orElseThrow().isUsed());

        mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        var login = mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", NEW_PASSWORD))
                .andExpect(redirectedUrl("/")).andExpect(authenticated().withUsername("reset-user").withRoles("USER"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void resetDoesNotEnableDisabledAccount() {
        User user = user();
        user.setEnabled(false);
        users.saveAndFlush(user);
        passwordReset.requestOtp(EMAIL);
        passwordReset.resetPassword(resetRequest(currentResetOtp().getCode()));
        assertEquals(Boolean.FALSE, user().getEnabled());
        assertTrue(encoder.matches(NEW_PASSWORD, user().getPassword()));
    }

    @Test
    void mismatchedOrShortPasswordIsRejectedWithoutConsumingOtp() throws Exception {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        mvc.perform(post("/reset-password").with(csrf()).param("email", EMAIL).param("otp", otp.getCode())
                        .param("password", NEW_PASSWORD).param("confirmPassword", "different"))
                .andExpect(status().isOk()).andExpect(view().name("reset-password"))
                .andExpect(model().attributeHasFieldErrors("resetPasswordRequest", "confirmPassword"));
        mvc.perform(post("/reset-password").with(csrf()).param("email", EMAIL).param("otp", otp.getCode())
                        .param("password", "123").param("confirmPassword", "123"))
                .andExpect(status().isOk()).andExpect(view().name("reset-password"))
                .andExpect(model().attributeHasFieldErrors("resetPasswordRequest", "password"));
        assertOldPasswordAndUnused(otp);
    }

    @Test
    void resendInvalidatesOldOtpAndOnlyNewOtpCanResetPassword() {
        deterministicCodes(123456, 654321);
        passwordReset.requestOtp(EMAIL);
        OtpToken old = currentResetOtp();
        allowResend(old);
        passwordReset.resendOtp(EMAIL);
        OtpToken fresh = currentResetOtp();
        assertNotEquals(old.getId(), fresh.getId());
        assertNotEquals(old.getCode(), fresh.getCode());
        assertTrue(tokens.findById(old.getId()).orElseThrow().isUsed());
        assertThrows(IllegalArgumentException.class,
                () -> passwordReset.resetPassword(resetRequest(old.getCode())));
        passwordReset.resetPassword(resetRequest(fresh.getCode()));
        assertTrue(encoder.matches(NEW_PASSWORD, user().getPassword()));
        assertTrue(tokens.findById(fresh.getId()).orElseThrow().isUsed());
        verify(mail).sendPasswordResetOtp(EMAIL, old.getCode(), 5);
        verify(mail).sendPasswordResetOtp(EMAIL, fresh.getCode(), 5);
    }

    @Test
    void resetResendCooldownPreservesExistingOtpAndDoesNotSendMail() {
        passwordReset.requestOtp(EMAIL);
        OtpToken otp = currentResetOtp();
        clearInvocations(mail);
        assertThrows(IllegalStateException.class, () -> passwordReset.resendOtp(EMAIL));
        assertEquals(1, tokens.count());
        assertEquals(otp.getCode(), currentResetOtp().getCode());
        assertFalse(tokens.findById(otp.getId()).orElseThrow().isUsed());
        verifyNoInteractions(mail);
    }

    @Test
    void initialResetMailFailureRollsBackOtpAndKeepsPassword() {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tokens.flush();
            assertEquals(1, tokens.count());
            throw new MailSendException("Simulated reset email failure");
        }).when(mail).sendPasswordResetOtp(anyString(), anyString(), anyLong());
        assertThrows(MailSendException.class, () -> passwordReset.requestOtp(EMAIL));
        assertEquals(0, tokens.count());
        assertTrue(encoder.matches(OLD_PASSWORD, user().getPassword()));
    }

    @Test
    void resendMailFailureRollsBackNewOtpAndRestoresOldOtp() {
        passwordReset.requestOtp(EMAIL);
        OtpToken old = currentResetOtp();
        allowResend(old);
        clearInvocations(mail);
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tokens.flush();
            assertEquals(2, tokens.count());
            assertTrue(tokens.findById(old.getId()).orElseThrow().isUsed());
            throw new MailSendException("Simulated reset email failure");
        }).when(mail).sendPasswordResetOtp(anyString(), anyString(), anyLong());
        assertThrows(MailSendException.class, () -> passwordReset.resendOtp(EMAIL));
        assertEquals(1, tokens.count());
        assertEquals(old.getId(), currentResetOtp().getId());
        assertFalse(tokens.findById(old.getId()).orElseThrow().isUsed());
        assertTrue(encoder.matches(OLD_PASSWORD, user().getPassword()));
    }

    @Test
    void registerAndResetOtpRemainIndependentDuringResend() {
        OtpToken register = token(OtpType.REGISTER, "345678");
        passwordReset.requestOtp(EMAIL);
        OtpToken reset = currentResetOtp();
        allowResend(reset);
        passwordReset.resendOtp(EMAIL);
        assertFalse(tokens.findById(register.getId()).orElseThrow().isUsed());
        assertTrue(tokens.findById(reset.getId()).orElseThrow().isUsed());
    }

    private User createUser(boolean enabled) {
        User user = new User();
        user.setUsername("reset-user");
        user.setFullname("Reset User");
        user.setEmail(EMAIL);
        user.setPassword(encoder.encode(OLD_PASSWORD));
        user.setRole("USER");
        user.setEnabled(enabled);
        return users.saveAndFlush(user);
    }

    private User user() { return users.findByUsername("reset-user").orElseThrow(); }

    private OtpToken token(OtpType type, String code) {
        OtpToken otp = new OtpToken();
        otp.setUser(user());
        otp.setType(type);
        otp.setCode(code);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setUsed(false);
        return tokens.saveAndFlush(otp);
    }

    private OtpToken currentResetOtp() {
        return tokens.findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user(), OtpType.PASSWORD_RESET).orElseThrow();
    }

    private ResetPasswordRequest resetRequest(String code) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(EMAIL);
        request.setOtp(code);
        request.setPassword(NEW_PASSWORD);
        request.setConfirmPassword(NEW_PASSWORD);
        return request;
    }

    private void assertOldPasswordAndUnused(OtpToken otp) {
        assertTrue(encoder.matches(OLD_PASSWORD, user().getPassword()));
        assertFalse(tokens.findById(otp.getId()).orElseThrow().isUsed());
    }

    private void allowResend(OtpToken otp) {
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
