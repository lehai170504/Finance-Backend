package com.homie.finance.service;

import com.homie.finance.entity.User;
import com.homie.finance.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final UserRepository userRepository;

    /**
     * Tạo mã Secret Key và trả về chuỗi URI để Frontend vẽ QR Code
     */
    public String generateSecretKey(User user) {
        String secret = user.getTotpSecret();

        // Nếu chưa có secret thì tạo mới và lưu lại
        if (secret == null || secret.isBlank()) {
            GoogleAuthenticatorKey key = gAuth.createCredentials();
            secret = key.getKey();
            user.setTotpSecret(secret);
            userRepository.save(user);
            log.info("Đã tạo Secret Key 2FA mới cho User: {}", user.getUsername());
        }

        // Encode các thông tin để tạo URI chuẩn cho Google Authenticator / Microsoft Authenticator
        String encodedIssuer = UriUtils.encode("HomieFinance", StandardCharsets.UTF_8);
        String encodedAccount = UriUtils.encode(user.getEmail(), StandardCharsets.UTF_8);

        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedAccount, secret, encodedIssuer);
    }

    /**
     * Xác thực mã code 6 số từ ứng dụng của người dùng
     */
    public boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isBlank()) {
            log.warn("Cố gắng xác thực 2FA nhưng Secret bị trống!");
            return false;
        }

        boolean isAuthorized = gAuth.authorize(secret, code);
        if (isAuthorized) {
            log.info("Xác thực 2FA thành công.");
        } else {
            log.warn("❌ Xác thực 2FA thất bại (Mã code sai hoặc hết hạn).");
        }

        return isAuthorized;
    }
}