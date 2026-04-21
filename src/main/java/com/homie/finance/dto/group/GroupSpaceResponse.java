package com.homie.finance.dto.group;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GroupSpaceResponse {
    private String id;
    private String name;
    private String inviteCode;
    private LocalDateTime createdAt;
    private UserSummaryDto owner;
    private List<UserSummaryDto> members;
}