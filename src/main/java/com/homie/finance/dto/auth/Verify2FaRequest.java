package com.homie.finance.dto.auth;

import lombok.Data;

@Data
public class Verify2FaRequest {
    private String tempToken;
    private int code;
}