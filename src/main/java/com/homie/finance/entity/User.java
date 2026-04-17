package com.homie.finance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Column(unique = true, length = 50)
    @Schema(description = "Tên tài khoản", example = "homiedev")
    private String username;

    @NotBlank(message = "Mật khẩu là bắt buộc")
    @Column(nullable = false)
    @JsonIgnore // Bảo mật: Không bao giờ trả về password qua API
    private String password;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Column(unique = true, nullable = false) // Đảm bảo Email không trùng lặp
    @Schema(description = "Email liên hệ", example = "homie@gmail.com")
    private String email;

    @JsonIgnore // OTP cũng là dữ liệu nhạy cảm
    private String otp;

    private Instant otpExpiry;

    @Column(name = "avatar_url")
    @Schema(description = "Link ảnh đại diện")
    private String avatarUrl;

    // ==========================================
    // KHU VỰC BẢO MẬT & 2FA (TRÙM CUỐI)
    // ==========================================

    @Column(name = "totp_secret")
    @JsonIgnore // Bảo mật: Khóa 2FA phải giấu kín, FE chỉ nhận lúc setup qua QR
    private String totpSecret;

    @Column(name = "is_2fa_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean is2faEnabled = false;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    // ==========================================
    // KHU VỰC PHÂN QUYỀN (ROLE)
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role = Role.USER;

    public enum Role {
        USER,
        ADMIN
    }
}
