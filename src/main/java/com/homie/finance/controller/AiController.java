package com.homie.finance.controller;

import com.homie.finance.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @GetMapping("/financial-advice")
    public ResponseEntity<Map<String, String>> getFinancialAdvice() {
        String advice = aiService.getFinancialAdvice();
        return ResponseEntity.ok(Map.of("advice", advice));
    }

    @org.springframework.web.bind.annotation.PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        String response = aiService.chatWithAi(message, history);
        return ResponseEntity.ok(Map.of("response", response));
    }
}
