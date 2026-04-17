package com.homie.finance.service;

import com.homie.finance.entity.User;
import com.homie.finance.repository.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
public class TwoFactorAuthService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    @Autowired
    private UserRepository userRepository;

    public String generateSecretKey(User user) {
        String secret = user.getTotpSecret();
        if (secret == null || secret.isBlank()) {
            GoogleAuthenticatorKey key = gAuth.createCredentials();
            secret = key.getKey();
            user.setTotpSecret(secret);
            userRepository.save(user);
        }

        String encodedIssuer = UriUtils.encode("HomieFinance", StandardCharsets.UTF_8);
        String encodedAccount = UriUtils.encode(user.getEmail(), StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedAccount, secret, encodedIssuer);
    }

    public boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        return gAuth.authorize(secret, code);
    }
}
