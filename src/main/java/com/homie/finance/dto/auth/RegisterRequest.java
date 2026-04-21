package com.homie.finance.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Nhập tên tài khoản đi homie!")
    @Schema(description = "Tên đăng nhập", example = "homiedev")
    private String username;

    @NotBlank(message = "Quên nhập mật khẩu kìa!")
    @Schema(description = "Mật khẩu", example = "123456")
    private String password;

    @Email(message = "Email sai định dạng rồi")
    @Schema(description = "Email liên hệ", example = "homie@gmail.com")
    private String email;
}