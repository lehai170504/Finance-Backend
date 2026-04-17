package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.dto.AuthResponse;
import com.homie.finance.dto.GoogleLoginRequest;
import com.homie.finance.dto.LoginRequest;
import com.homie.finance.dto.RegisterRequest;
import com.homie.finance.dto.UserResponse;
import com.homie.finance.dto.Verify2FaRequest;
import com.homie.finance.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/auth")
@Tag(name = "1. Authentication")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Dang ky tai khoan")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        return new ApiResponse<>(201, "Thanh cong", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Dang nhap bang email hoac username", description = "Gui loginId va password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse authData = authService.login(request, httpRequest);
        String message = authData.is2faRequired()
                ? "Vui long nhap ma 2FA tu ung dung Google Authenticator!"
                : "Dang nhap thanh cong!";
        return new ApiResponse<>(200, message, authData);
    }

    @PostMapping("/google")
    @Operation(summary = "Dang nhap Google")
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse authData = authService.loginWithGoogle(request, httpRequest);
        String message = authData.is2faRequired()
                ? "Vui long nhap ma 2FA tu ung dung Google Authenticator!"
                : "Google Login thanh cong!";
        return new ApiResponse<>(200, message, authData);
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Xac thuc ma 2 lop (TOTP)")
    public ApiResponse<AuthResponse> verify2fa(@RequestBody Verify2FaRequest req) {
        AuthResponse authData = authService.verify2FA(req);
        return new ApiResponse<>(200, "Xac thuc 2FA thanh cong!", authData);
    }

    @PostMapping("/2fa/setup")
    @Operation(summary = "Lay ma QR de cai dat 2FA", description = "Tra ve chuoi URL de Frontend render thanh ma QR")
    public ApiResponse<String> setup2FA() {
        String qrUrl = authService.setup2FA();
        return new ApiResponse<>(200, "Lay URL ma QR thanh cong!", qrUrl);
    }

    @PostMapping("/2fa/confirm")
    @Operation(summary = "Xac nhan va bat 2FA", description = "Nhap ma 6 so tu Google Auth")
    public ApiResponse<String> confirm2FA(@RequestBody Verify2FaRequest req) {
        authService.confirmAndEnable2FA(req.getCode());
        return new ApiResponse<>(200, "Bao mat 2 lop da duoc bat thanh cong!", null);
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Tat 2FA", description = "Can nhap mat khau hien tai de xac nhan tat 2FA")
    public ApiResponse<String> disable2FA(@RequestBody Map<String, String> body) {
        authService.disable2FA(body.get("password"));
        return new ApiResponse<>(200, "Da tat tinh nang bao mat 2 lop!", null);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Doi the moi (Refresh)")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody Map<String, String> body) {
        AuthResponse authData = authService.refreshToken(body.get("refreshToken"));
        return new ApiResponse<>(200, "Da cap the moi!", authData);
    }

    @GetMapping("/me")
    @Operation(summary = "Lay thong tin ca nhan", description = "Dung Token de xem minh la ai.")
    public ApiResponse<UserResponse> getMe() {
        return new ApiResponse<>(200, "Profile cua homie ne!", authService.getMyInfo());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quen mat khau", description = "Gui ma OTP 6 so ve Email.")
    public ApiResponse<String> forgotPassword(@RequestParam String email) {
        authService.forgotPassword(email);
        return new ApiResponse<>(200, "Da gui ma OTP ve mail, check ngay homie!", null);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Dat lai mat khau", description = "Truyen Email, OTP va Mat khau moi de reset.")
    public ApiResponse<String> resetPassword(
            @RequestParam String email,
            @RequestParam String otp,
            @RequestParam String newPassword) {
        authService.resetPassword(email, otp, newPassword);
        return new ApiResponse<>(200, "Doi mat khau thanh cong! Gio login lai xem nao.", null);
    }

    @PutMapping("/profile")
    @Operation(summary = "Cap nhat Username")
    public ApiResponse<UserResponse> updateProfile(@RequestParam String newUsername) {
        return new ApiResponse<>(200, "Da doi ten!", authService.updateProfile(newUsername));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Doi mat khau")
    public ApiResponse<String> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body.get("oldPass"), body.get("newPass"));
        return new ApiResponse<>(200, "Doi mat khau thanh cong!", null);
    }

    @PostMapping("/logout")
    @Operation(summary = "Dang xuat")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return new ApiResponse<>(200, "Hen gap lai homie!", null);
    }

    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Avatar", description = "Gui file anh (png, jpg) duoi dang form-data voi key la 'file'")
    public ApiResponse<UserResponse> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File anh khong duoc de trong!");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chi cho phep upload file anh (JPG, PNG)!");
        }

        UserResponse updatedUser = authService.uploadAvatar(file);
        return new ApiResponse<>(200, "Cap nhat anh dai dien thanh cong!", updatedUser);
    }
}
