package com.homie.finance.controller;

import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.dto.auth.AuthResponse;
import com.homie.finance.dto.auth.GoogleLoginRequest;
import com.homie.finance.dto.auth.LoginRequest;
import com.homie.finance.dto.auth.RegisterRequest;
import com.homie.finance.dto.auth.UserResponse;
import com.homie.finance.dto.auth.Verify2FaRequest;
import com.homie.finance.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "1. Authentication")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        return new ApiResponse<>(201, "Thanh cong", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng email hoặc username", description = "Gửi loginId và password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse authData = authService.login(request, httpRequest);
        String message = authData.is2faRequired()
                ? "Vui lòng nhập mã 2FA từ ứng dụng Google Authenticator!"
                : "Đăng nhập thành công!";
        return new ApiResponse<>(200, message, authData);
    }

    @PostMapping("/google")
    @Operation(summary = "Đăng nhập Google")
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse authData = authService.loginWithGoogle(request, httpRequest);
        String message = authData.is2faRequired()
                ? "Vui lòng nhập mã 2FA từ ứng dụng Google Authenticator!"
                : "Google Login thành công!";
        return new ApiResponse<>(200, message, authData);
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Xác thực mã 2 lớp (TOTP)")
    public ApiResponse<AuthResponse> verify2fa(@RequestBody Verify2FaRequest req) {
        AuthResponse authData = authService.verify2FA(req);
        return new ApiResponse<>(200, "Xác thực 2FA thành công!", authData);
    }

    @PostMapping("/2fa/setup")
    @Operation(summary = "Lấy mã QR để cài đặt 2FA", description = "Trả về chuỗi URL để Frontend render thành mã QR")
    public ApiResponse<String> setup2FA() {
        String qrUrl = authService.setup2FA();
        return new ApiResponse<>(200, "Lấy URL mã QR thành công!", qrUrl);
    }

    @PostMapping("/2fa/confirm")
    @Operation(summary = "Xác nhận và bật 2FA", description = "Nhập mã 6 số từ Google Auth")
    public ApiResponse<String> confirm2FA(@RequestBody Verify2FaRequest req) {
        authService.confirmAndEnable2FA(req.getCode());
        return new ApiResponse<>(200, "Bảo mật 2 lớp đã được bật thành công!", null);
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Tắt 2FA", description = "Cần nhập mật khẩu hiện tại để xác nhận tắt 2FA")
    public ApiResponse<String> disable2FA(@RequestBody Map<String, String> body) {
        authService.disable2FA(body.get("password"));
        return new ApiResponse<>(200, "Đã tắt tính năng bảo mật 2 lớp!", null);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Đổi thẻ mới (Refresh)")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody Map<String, String> body) {
        AuthResponse authData = authService.refreshToken(body.get("refreshToken"));
        return new ApiResponse<>(200, "Đã cấp thẻ mới!", authData);
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
    public ApiResponse<String> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body.get("oldPass"), body.get("newPass"));
        return new ApiResponse<>(200, "Đổi mật khẩu thành công!", null);
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return new ApiResponse<>(200, "Hẹn gặp lại homie!", null);
    }

    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Avatar", description = "Gửi file ảnh (png, jpg) dưới dạng form-data với key là 'file'")
    public ApiResponse<UserResponse> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không được để trống!");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chỉ cho phép upload file ảnh (JPG, PNG)!");
        }

        UserResponse updatedUser = authService.uploadAvatar(file);
        return new ApiResponse<>(200, "Cập nhật ảnh đại diện thành công!", updatedUser);
    }
}
