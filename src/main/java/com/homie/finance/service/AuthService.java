package com.homie.finance.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.homie.finance.dto.GoogleLoginRequest;
import com.homie.finance.dto.LoginRequest;
import com.homie.finance.dto.RegisterRequest;
import com.homie.finance.dto.UserResponse;
import com.homie.finance.entity.BlacklistedToken;
import com.homie.finance.entity.User;
import com.homie.finance.repository.BlacklistedTokenRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EmailService emailService;
    @Autowired private BlacklistedTokenRepository blacklistRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${google.client-id}")
    private String googleClientId;

    // 1. ĐĂNG KÝ (Check trùng cả Username lẫn Email)
    public String register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
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

    // 2. ĐĂNG NHẬP BẰNG EMAIL
    public String login(LoginRequest request) {
        // Tìm User theo Email (request.getUsername() chứa email từ FE)
        User user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không chính xác!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không chính xác!");
        }

        // Quan trọng: In thẻ Token dựa trên Username chuẩn trong DB
        return jwtUtil.generateToken(user.getUsername());
    }

    // 3. ĐĂNG NHẬP GOOGLE
    public String loginWithGoogle(GoogleLoginRequest request) {
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
                user.setUsername(email); // User Google dùng email làm username luôn
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                user = userRepository.save(user);
            }
            return jwtUtil.generateToken(user.getUsername());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi Google Auth: " + e.getMessage());
        }
    }

    //4. LẤY THÔNG TIN NGƯỜI DÙNG
    public UserResponse getMyInfo() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // 5. Yêu cầu cấp OTP
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email này chưa đăng ký homie ơi!"));

        //CHỐNG SPAM: Chỉ cho phép gửi lại mã sau 60 giây
        if (user.getOtpExpiry() != null) {
            // Thời điểm gửi = Thời điểm hết hạn - 5 phút (300s)
            long secondsSinceLastSend = java.time.Duration.between(
                    user.getOtpExpiry().minusSeconds(300),
                    java.time.Instant.now()
            ).getSeconds();

            if (secondsSinceLastSend < 60) {
                throw new IllegalArgumentException("Vui lòng đợi " + (60 - secondsSinceLastSend) + "s để yêu cầu mã mới!");
            }
        }

        // Tạo mã 6 số ngẫu nhiên
        String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
        user.setOtp(otp);
        user.setOtpExpiry(java.time.Instant.now().plusSeconds(300)); // Hết hạn sau 5 phút
        userRepository.save(user);

        // Gửi mail (Tận dụng EmailService cũ)
        emailService.sendSimpleEmail(email, "Mã OTP của bạn là: " + otp, "Mã xác thực đổi mật khẩu");
    }

    // 6. Đổi mật khẩu mới bằng OTP
    public void resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại!"));

        if (user.getOtp() == null || !user.getOtp().equals(otp)
                || user.getOtpExpiry().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("Mã OTP sai hoặc đã hết hạn rồi!");
        }

        // OTP đúng -> Băm mật khẩu mới và xóa OTP cũ
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }

    // Đổi Username hoặc các thông tin cơ bản
    public UserResponse updateProfile(String newUsername) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(currentUsername).orElseThrow();

        if (userRepository.findByUsername(newUsername).isPresent()) {
            throw new IllegalArgumentException("Username này đã có người dùng rồi!");
        }

        user.setUsername(newUsername);
        userRepository.save(user);
        return UserResponse.builder().id(user.getId()).username(user.getUsername()).email(user.getEmail()).build();
    }

    // Đổi mật khẩu (Cần nhập mật khẩu cũ để xác nhận)
    public void changePassword(String oldPassword, String newPassword) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(currentUsername).orElseThrow();

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void logout(String token) {
        // 1. Cắt bỏ chữ "Bearer " (nhớ check null để tránh lỗi substring)
        if (token == null || !token.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token không hợp lệ!");
        }
        String jwt = token.substring(7);

        // 2. Lưu vào Blacklist
        BlacklistedToken blacklisted = new BlacklistedToken();
        blacklisted.setToken(jwt);

        // FIX LỖI 1: Chuyển từ Date sang Instant chuẩn xác
        blacklisted.setExpiryDate(jwtUtil.extractExpiration(jwt).toInstant());

        blacklistRepository.save(blacklisted);

        // 3. Xóa Refresh Token
        String username = jwtUtil.extractUsername(jwt);

        // FIX LỖI 2: Dùng orElseThrow để an toàn và code sạch hơn
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        refreshTokenService.deleteByUserId(user.getId());
    }
}