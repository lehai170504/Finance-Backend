package com.homie.finance.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.homie.finance.dto.*;
import com.homie.finance.entity.BlacklistedToken;
import com.homie.finance.entity.RefreshToken;
import com.homie.finance.entity.User;
import com.homie.finance.repository.BlacklistedTokenRepository;
import com.homie.finance.repository.RefreshTokenRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EmailService emailService;
    @Autowired private BlacklistedTokenRepository blacklistRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private TwoFactorAuthService twoFactorAuthService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private CloudinaryService cloudinaryService;

    @Value("${google.client-id}")
    private String googleClientId;

    // ==============================================================
    // HÀM DÙNG CHUNG (Xử lý trả về Token thật hoặc Token tạm cho 2FA)
    // ==============================================================
    private AuthResponse processUserLogin(User user) {
        if (user.is2faEnabled()) {
            String tempToken = jwtUtil.generateTempToken(user.getId());
            AuthResponse response = new AuthResponse();
            response.set2faRequired(true);
            response.setTempToken(tempToken);
            return response;
        }

        String accessToken = jwtUtil.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", user.getUsername());
    }

    // ==============================================================
    // 1. ĐĂNG KÝ
    // ==============================================================
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

    // ==============================================================
    // 2. ĐĂNG NHẬP BẰNG EMAIL (Tích hợp Cảnh báo IP lạ & 2FA)
    // ==============================================================
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không chính xác!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không chính xác!");
        }

        String currentIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        checkAndAlertUnrecognizedDevice(user, currentIp, userAgent);

        // Gọi hàm dùng chung
        return processUserLogin(user);
    }

    // ==============================================================
    // 3. ĐĂNG NHẬP GOOGLE (Tích hợp Cảnh báo IP lạ & 2FA)
    // ==============================================================
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId)).build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) throw new IllegalArgumentException("Xác thực Google thất bại!");

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

            // Gọi hàm dùng chung
            return processUserLogin(user);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi Google Auth: " + e.getMessage());
        }
    }

    // ==============================================================
    // CÁC HÀM XỬ LÝ BẢO MẬT & THIẾT BỊ
    // ==============================================================
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip.split(",")[0].trim();
    }

    @Async
    public void checkAndAlertUnrecognizedDevice(User user, String currentIp, String userAgent) {
        if (user.getLastLoginIp() != null && !currentIp.equals(user.getLastLoginIp())) {

            String subject = "Cảnh báo đăng nhập từ thiết bị lạ";
            String content = String.format(
                    "Chào %s,\n\n" +
                            "Tài khoản Homie Finance của bạn vừa được đăng nhập từ một thiết bị hoặc vị trí mới.\n\n" +
                            " Địa chỉ IP: %s\n" +
                            " Thiết bị/Trình duyệt: %s\n\n" +
                            "Nếu đây không phải là bạn, hãy đăng nhập và đổi mật khẩu ngay lập tức để bảo vệ tài sản của mình.\n\n" +
                            "Trân trọng,\nĐội ngũ Bảo mật Homie Finance.",
                    user.getUsername(), currentIp, userAgent
            );

            emailService.sendSimpleEmail(user.getEmail(), content, subject);
        }

        user.setLastLoginIp(currentIp);
        userRepository.save(user);
    }

    // ==============================================================
    // CÀI ĐẶT 2FA (BẬT/TẮT BẢO MẬT 2 LỚP)
    // ==============================================================
    @Transactional
    public String setup2FA() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        return twoFactorAuthService.generateSecretKey(user);
    }

    @Transactional
    public void confirmAndEnable2FA(int code) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (twoFactorAuthService.verifyCode(user.getTotpSecret(), code)) {
            user.set2faEnabled(true);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("Mã xác nhận không đúng, vui lòng thử lại!");
        }
    }

    @Transactional
    public void disable2FA(String password) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu không chính xác!");
        }

        user.set2faEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    // ==============================================================
    // THÔNG TIN USER & QUÊN MẬT KHẨU
    // ==============================================================
    public UserResponse getMyInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

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
                .orElseThrow(() -> new IllegalArgumentException("Email này chưa đăng ký homie ơi!"));

        if (user.getOtpExpiry() != null) {
            long secondsSinceLastSend = java.time.Duration.between(
                    user.getOtpExpiry().minusSeconds(300),
                    java.time.Instant.now()
            ).getSeconds();

            if (secondsSinceLastSend < 60) {
                throw new IllegalArgumentException("Vui lòng đợi " + (60 - secondsSinceLastSend) + "s để yêu cầu mã mới!");
            }
        }

        String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
        user.setOtp(otp);
        user.setOtpExpiry(java.time.Instant.now().plusSeconds(300));
        userRepository.save(user);

        emailService.sendSimpleEmail(email, "Mã OTP của bạn là: " + otp, "Mã xác thực đổi mật khẩu");
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại!"));

        if (user.getOtp() == null || !user.getOtp().equals(otp)
                || user.getOtpExpiry().isBefore(java.time.Instant.now())) {
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
        return UserResponse.builder().id(user.getId()).username(user.getUsername()).email(user.getEmail()).build();
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
        String userId = jwtUtil.getUserIdFromTempToken(req.getTempToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Token tạm không hợp lệ hoặc đã hết hạn!"));

        if (!twoFactorAuthService.verifyCode(user.getTotpSecret(), req.getCode())) {
            throw new IllegalArgumentException("Mã 2FA không chính xác!");
        }

        // Nếu đúng mã -> Sinh thẻ thật
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
            // Lấy user đang đăng nhập
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

            // Upload ảnh lên Cloudinary
            String imageUrl = cloudinaryService.uploadImage(file);

            // Lưu link vào DB
            user.setAvatarUrl(imageUrl);
            userRepository.save(user);

            // Trả về thông tin mới (Nhớ map cái avatarUrl vào nhé)
            return UserResponse.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .avatarUrl(user.getAvatarUrl()) // Trả về cho FE
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi upload ảnh: " + e.getMessage());
        }
    }
}