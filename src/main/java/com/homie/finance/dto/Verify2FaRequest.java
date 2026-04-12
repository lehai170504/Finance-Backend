package com.homie.finance.dto;

import lombok.Data;

@Data
public class Verify2FaRequest {
    private String tempToken;
    private int code;
}