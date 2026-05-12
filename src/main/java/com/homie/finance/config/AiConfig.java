package com.homie.finance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${gemini.api.key:}") // Mặc định để trống, homie điền vào application.properties
    private String apiKey;

    @Value("${ai.service.url:http://localhost:8000}")
    private String apiUrl;

    @Value("${ai.service.api-key:}")
    private String serviceApiKey;

    public String getServiceApiKey() {
        return serviceApiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

}
