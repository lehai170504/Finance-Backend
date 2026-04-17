package com.homie.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Chua nhap email hoac username")
    @Schema(description = "Dinh danh dang nhap: email hoac ten dang nhap", example = "homiedev")
    private String loginId;

    @NotBlank(message = "Chua nhap password")
    @Schema(description = "Mat khau", example = "123456")
    private String password;
}
