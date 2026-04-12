package com.homie.finance.dto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private String role;
    private String avatarUrl;
}