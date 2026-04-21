package com.homie.finance.dto.group;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryDto {
    private String id;
    private String username;
    private String email;
    private String avatarUrl;
}