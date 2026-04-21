package com.homie.finance.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank(message = "Thiếu Token của Google rồi homie!")
    @Schema(description = "ID Token nhận được từ Google SDK trên Web/App")
    private String idToken;
}