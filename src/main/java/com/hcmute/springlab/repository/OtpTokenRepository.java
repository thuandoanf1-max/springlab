package com.hcmute.springlab.repository;

import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    boolean existsByUserId(Long userId);

    boolean existsByUserAndTypeAndCode(User user, OtpType type, String code);

    Optional<OtpToken> findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(User user, OtpType type);
}
