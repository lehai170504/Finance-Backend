package com.homie.finance.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.homie.finance.dto.auth.AuthResponse;
import com.homie.finance.dto.auth.GoogleLoginRequest;
import com.homie.finance.dto.auth.LoginRequest;
import com.homie.finance.dto.auth.RegisterRequest;
import com.homie.finance.dto.auth.UserResponse;
import com.homie.finance.dto.auth.Verify2FaRequest;
import com.homie.finance.entity.BlacklistedToken;
import com.homie.finance.entity.RefreshToken;
import com.homie.finance.entity.User;
import com.homie.finance.repository.BlacklistedTokenRepository;
import com.homie.finance.repository.RefreshTokenRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final BlacklistedTokenRepository blacklistRepository;
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CloudinaryService cloudinaryService;

    @Value("${google.client-id}")
    private String googleClientId;

    private User getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    private User findUserForLogin(String loginId) {
        return userRepository.findByEmail(loginId)
                .or(() -> userRepository.findByUsername(loginId))
                .orElseThrow(() -> new IllegalArgumentException("Email/username hoặc mật khẩu không chính xác!"));
    }

    private AuthResponse processUserLogin(User user) {
        if (user.is2faEnabled()) {
            if (!StringUtils.hasText(user.getTotpSecret())) {
                throw new IllegalStateException(
                        "Tài khoản đang bật 2FA nhưng secret không hợp lệ. Hãy thiết lập lại 2FA.");
            }

            String tempToken = jwtUtil.generateTempToken(user.getId());
            AuthResponse response = new AuthResponse();
            response.set2faRequired(true);
            response.setTempToken(tempToken);
            response.setAccessToken(null);
            response.setRefreshToken(null);
            return response;
        }

        String accessToken = jwtUtil.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", user.getUsername());
    }

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email này đã được sử dụng!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
        return "Đăng ký thành công!";
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = findUserForLogin(request.getLoginId());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email/username hoặc mật khẩu không chính xác!");
        }

        String currentIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        checkAndAlertUnrecognizedDevice(user, currentIp, userAgent);

        return processUserLogin(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                throw new IllegalArgumentException("Xác thực Google thất bại!");
            }

            String email = idToken.getPayload().getEmail();
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setUsername(email);
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                user = userRepository.save(user);
            }

            String currentIp = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            checkAndAlertUnrecognizedDevice(user, currentIp, userAgent);

            return processUserLogin(user);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi Google Auth: " + e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip.split(",")[0].trim();
    }

    @Async
    public void checkAndAlertUnrecognizedDevice(User user, String currentIp, String userAgent) {
        if (user.getLastLoginIp() != null && !currentIp.equals(user.getLastLoginIp())) {
            String subject = "Cảnh báo đăng nhập từ thiết bị lạ";
            String content = String.format(
                    "Chào %s,\n\nTài khoản Homie Finance của bạn vừa được đăng nhập từ một thiết bị hoặc vị trí mới.\n\nIP: %s\nThiết bị/Trình duyệt: %s\n\nNếu đây không phải bạn, hãy đăng nhập và đổi mật khẩu ngay lập tức.\n\nTrân trọng,\nĐội ngũ Bảo mật Homie Finance.",
                    user.getUsername(), currentIp, userAgent);

            emailService.sendSimpleEmail(user.getEmail(), content, subject);
        }

        user.setLastLoginIp(currentIp);
        userRepository.save(user);
    }

    @Transactional
    public String setup2FA() {
        User user = getCurrentAuthenticatedUser();

        if (user.is2faEnabled()) {
            throw new IllegalArgumentException("Tài khoản này đã bật 2FA. Hãy tắt 2FA trước khi thiết lập lại.");
        }

        return twoFactorAuthService.generateSecretKey(user);
    }

    @Transactional
    public void confirmAndEnable2FA(int code) {
        User user = getCurrentAuthenticatedUser();

        if (!StringUtils.hasText(user.getTotpSecret())) {
            throw new IllegalArgumentException("Bạn chưa thiết lập secret 2FA. Hãy gọi /api/auth/2fa/setup trước.");
        }

        if (user.is2faEnabled()) {
            throw new IllegalArgumentException("Tài khoản này đã bật 2FA rồi.");
        }

        if (twoFactorAuthService.verifyCode(user.getTotpSecret(), code)) {
            user.set2faEnabled(true);
            userRepository.save(user);
            return;
        }

        throw new IllegalArgumentException("Mã xác nhận không đúng, vui lòng thử lại!");
    }

    @Transactional
    public void disable2FA(String password) {
        User user = getCurrentAuthenticatedUser();

        if (!user.is2faEnabled()) {
            throw new IllegalArgumentException("Tài khoản này chưa bật 2FA.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu không chính xác!");
        }

        user.set2faEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    public UserResponse getMyInfo() {
        User user = getCurrentAuthenticatedUser();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .is2faEnabled(user.is2faEnabled())
                .build();
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email này chưa đăng ký!"));

        if (user.getOtpExpiry() != null) {
            long secondsSinceLastSend = Duration.between(
                    user.getOtpExpiry().minusSeconds(300),
                    Instant.now()).getSeconds();

            if (secondsSinceLastSend < 60) {
                throw new IllegalArgumentException(
                        "Vui lòng đợi " + (60 - secondsSinceLastSend) + "s để yêu cầu mã mới!");
            }
        }

        String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
        user.setOtp(otp);
        user.setOtpExpiry(Instant.now().plusSeconds(300));
        userRepository.save(user);

        emailService.sendSimpleEmail(email, "Mã OTP của bạn là: " + otp, "Mã xác thực đổi mật khẩu");
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại!"));

        if (user.getOtp() == null || !user.getOtp().equals(otp)
                || user.getOtpExpiry() == null
                || user.getOtpExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Mã OTP sai hoặc đã hết hạn rồi!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse updateProfile(String newUsername) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(currentUsername).orElseThrow();

        if (userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username này đã có người dùng rồi!");
        }

        user.setUsername(newUsername);
        userRepository.save(user);
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(currentUsername).orElseThrow();

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token không hợp lệ!");
        }
        String jwt = token.substring(7);

        BlacklistedToken blacklisted = new BlacklistedToken();
        blacklisted.setToken(jwt);
        blacklisted.setExpiryDate(jwtUtil.extractExpiration(jwt).toInstant());
        blacklistRepository.save(blacklisted);

        String username = jwtUtil.extractUsername(jwt);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        refreshTokenService.deleteByUserId(user.getId());
    }

    @Transactional
    public AuthResponse verify2FA(Verify2FaRequest req) {
        if (!StringUtils.hasText(req.getTempToken())) {
            throw new IllegalArgumentException("Thiếu tempToken để xác thực 2FA.");
        }

        String userId = jwtUtil.getUserIdFromTempToken(req.getTempToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Token tạm không hợp lệ hoặc đã hết hạn!"));

        if (!user.is2faEnabled() || !StringUtils.hasText(user.getTotpSecret())) {
            throw new IllegalArgumentException("Tài khoản này chưa được cấu hình 2FA hợp lệ.");
        }

        if (!twoFactorAuthService.verifyCode(user.getTotpSecret(), req.getCode())) {
            throw new IllegalArgumentException("Mã 2FA không chính xác!");
        }

        String accessToken = jwtUtil.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", user.getUsername());
    }

    @Transactional
    public AuthResponse refreshToken(String requestToken) {
        return refreshTokenRepository.findByToken(requestToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String newAccessToken = jwtUtil.generateToken(user.getUsername());
                    return new AuthResponse(newAccessToken, requestToken, "Bearer", user.getUsername());
                })
                .orElseThrow(() -> new RuntimeException("Refresh Token không hợp lệ hoặc đã hết hạn!"));
    }

    @Transactional
    public UserResponse uploadAvatar(org.springframework.web.multipart.MultipartFile file) {
        try {
            User user = getCurrentAuthenticatedUser();

            String imageUrl = cloudinaryService.uploadImage(file);
            user.setAvatarUrl(imageUrl);
            userRepository.save(user);

            return UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .avatarUrl(user.getAvatarUrl())
                    .is2faEnabled(user.is2faEnabled())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi upload ảnh: " + e.getMessage());
        }
    }
}