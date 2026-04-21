package com.homie.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final EmailService emailService;

    @Async
    public void sendBudgetAlertEmail(String userEmail, String userName, String categoryName, Double limitAmount) {
        String subject = "CẢNH BÁO: Homie tiêu lố hạn mức rồi!";

        // Nội dung HTML cực xịn cho user sợ chơi
        String body = "<div style='font-family: Arial, sans-serif; border: 1px solid #ffcccc; padding: 20px; border-radius: 10px;'>" +
                "<h2 style='color: #d9534f;'>Cảnh Báo Vượt Hạn Mức!</h2>" +
                "<p>Chào <b>" + userName + "</b>,</p>" +
                "<p>Hệ thống phát hiện bạn vừa tiêu <b>VƯỢT NGÂN SÁCH</b> cho khoản: <span style='color: #d9534f; font-weight: bold;'>" + categoryName + "</span>.</p>" +
                "<p>Hạn mức tháng này của bạn là: <b style='color: #d9534f;'>" + String.format("%,.0f", limitAmount) + " VNĐ</b>.</p>" +
                "<p style='background-color: #f9f9f9; padding: 10px; border-left: 4px solid #d9534f;'>" +
                "Tém tém lại nhé homie ơi, không là cuối tháng ăn mì tôm đấy!</p>" +
                "<hr><small>Tin nhắn tự động từ Homie Finance Assistant</small></div>";

        emailService.sendHtmlEmail(userEmail, subject, body);
        System.out.println("Đã bắn mail cảnh báo bất đồng bộ cho: " + userEmail);
    }
}