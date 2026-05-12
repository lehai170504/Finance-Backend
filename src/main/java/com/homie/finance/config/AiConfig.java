package com.homie.finance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${ai.service.url:http://localhost:8000}")
    private String apiUrl;

    @Value("${ai.service.api-key:}")
    private String serviceApiKey;

    @Value("${gemini.api.key:}")
    private String apiKey;

    public String getApiUrl() {
        return apiUrl;
    }

    public String getServiceApiKey() {
        return serviceApiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
}