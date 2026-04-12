package com.homie.finance.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Column(unique = true) // Đảm bảo mỗi người 1 username, không ai trùng ai
    @Schema(description = "Tên tài khoản", example = "homiedev")
    private String username;

    @NotBlank(message = "Mật khẩu là bắt buộc")
    @Schema(description = "Mật khẩu (Sẽ được mã hóa)", example = "123456")
    private String password;

    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email liên hệ", example = "homie@gmail.com")
    private String email;

    private String otp;
    private java.time.Instant otpExpiry;

    @Column(name = "avatar_url")
    @Schema(description = "Link ảnh đại diện")
    private String avatarUrl;

    // ==========================================
    // KHU VỰC BẢO MẬT & 2FA (TRÙM CUỐI)
    // ==========================================

    @Column(name = "totp_secret")
    @Schema(description = "Khóa bí mật cho Google Authenticator (TOTP)")
    private String totpSecret;

    @Column(name = "is_2fa_enabled", nullable = false)
    @Schema(description = "Trạng thái bật/tắt bảo mật 2 lớp", example = "false")
    private boolean is2faEnabled = false; // Mặc định tạo tài khoản là tắt 2FA

    @Column(name = "last_login_ip")
    @Schema(description = "IP của lần đăng nhập gần nhất để cảnh báo bảo mật", example = "192.168.1.1")
    private String lastLoginIp;


    // ==========================================
    // KHU VỰC PHÂN QUYỀN (ROLE)
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Schema(description = "Quyền hạn của tài khoản", example = "USER")
    private Role role = Role.USER; // Mặc định đăng ký là Thường dân

    // Khai báo các loại thẻ (Quyền) ngay trong này
    public enum Role {
        USER,  // Thẻ Thường
        ADMIN  // Thẻ VIP
    }
}