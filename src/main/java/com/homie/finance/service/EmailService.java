package com.homie.finance.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:hoanghaile175@gmail.com}")
    private String fromEmail;

    @Async
    public void sendWelcomeEmail(String toEmail, String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Chào mừng homie gia nhập Homie Finance!");
            message.setText("Chào " + username + ",\n\n" +
                    "Chúc mừng homie đã đăng ký thành công tài khoản trên hệ thống Homie Finance!\n" +
                    "Thân mến,\n" + "Đội ngũ Homie Dev.");
            mailSender.send(message);
            log.info("Đã gửi email chào mừng thành công tới: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi gửi mail chào mừng tới {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendSimpleEmail(String to, String content, String subject) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail); // Nên set luôn From cho chuẩn xác
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Đã gửi email thông báo thành công tới: {}", to);
        } catch (Exception e) {
            log.error("Lỗi gửi email thông báo tới {}: {}", to, e.getMessage());
        }
    }

    /**
     * Gửi Email định dạng HTML (Dùng cho Báo cáo và Cảnh báo đẹp)
     */
    @Async // 🔥 Chạy ngầm
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail); // Khai báo From cho email HTML
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Đã gửi email HTML thành công tới: {}", to);
        } catch (Exception e) {
            log.error("Lỗi gửi HTML email tới {}: {}", to, e.getMessage(), e);
        }
    }
}