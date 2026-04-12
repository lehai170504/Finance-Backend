package com.homie.finance.service;

import com.homie.finance.entity.User;
import com.homie.finance.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TwoFactorAuthService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    @Autowired private UserRepository userRepository;

    // 1. Tạo Secret Key khi user bật 2FA
    public String generateSecretKey(User user) {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        user.setTotpSecret(secret);
        userRepository.save(user);

        // Trả về URL để FE vẽ mã QR
        return String.format("otpauth://totp/HomieFinance:%s?secret=%s&issuer=HomieFinance",
                user.getEmail(), secret);
    }

    // 2. Xác thực mã 6 số user nhập vào
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }
}