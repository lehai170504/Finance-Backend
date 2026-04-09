package com.homie.finance.controller;

import com.homie.finance.dto.*;
import com.homie.finance.entity.RefreshToken;
import com.homie.finance.repository.RefreshTokenRepository;
import com.homie.finance.service.AuthService;
import com.homie.finance.service.RefreshTokenService;
import com.homie.finance.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Authentication")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        return new ApiResponse<>(201, "Thành công", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng Email")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. Lấy Access Token (Service đã check bằng Email)
        String accessToken = authService.login(request);

        // 2. Mổ xẻ token lấy Username chuẩn để tạo Refresh Token
        String realUsername = jwtUtil.extractUsername(accessToken);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(realUsername);

        // 3. Trả về: AccessToken, RefreshToken, và Email (request.getUsername())
        return new ApiResponse<>(200, "Đăng nhập thành công!",
                new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", request.getUsername()));
    }

    @PostMapping("/google")
    @Operation(summary = "Đăng nhập Google")
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        String accessToken = authService.loginWithGoogle(request);

        String username = jwtUtil.extractUsername(accessToken);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);

        return new ApiResponse<>(200, "Google Login thành công!",
                new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", username));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Đổi thẻ mới (Refresh)")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody Map<String, String> body) {
        String requestToken = body.get("refreshToken");

        return refreshTokenRepository.findByToken(requestToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String newAccessToken = jwtUtil.generateToken(user.getUsername());
                    // Trả về email để FE dùng
                    return new ApiResponse<>(200, "Đã cấp thẻ mới!",
                            new AuthResponse(newAccessToken, requestToken, "Bearer", user.getEmail()));
                })
                .orElseThrow(() -> new RuntimeException("Refresh Token không hợp lệ!"));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin cá nhân", description = "Dùng Token để xem mình là ai.")
    public ApiResponse<UserResponse> getMe() {
        return new ApiResponse<>(200, "Profile của homie nè!", authService.getMyInfo());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu", description = "Gửi mã OTP 6 số về Email.")
    public ApiResponse<String> forgotPassword(@RequestParam String email) {
        authService.forgotPassword(email);
        return new ApiResponse<>(200, "Đã gửi mã OTP về mail, check ngay homie!", null);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu", description = "Truyền Email, OTP và Mật khẩu mới để reset.")
    public ApiResponse<String> resetPassword(
            @RequestParam String email,
            @RequestParam String otp,
            @RequestParam String newPassword) {
        authService.resetPassword(email, otp, newPassword);
        return new ApiResponse<>(200, "Đổi mật khẩu thành công! Giờ login lại xem nào.", null);
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật Username")
    public ApiResponse<UserResponse> updateProfile(@RequestParam String newUsername) {
        return new ApiResponse<>(200, "Đã đổi tên!", authService.updateProfile(newUsername));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu")
    public ApiResponse<String> changePassword(@RequestParam String oldPass, @RequestParam String newPass) {
        authService.changePassword(oldPass, newPass);
        return new ApiResponse<>(200, "Đổi mật khẩu thành công!", null);
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return new ApiResponse<>(200, "Hẹn gặp lại homie!", null);
    }
}