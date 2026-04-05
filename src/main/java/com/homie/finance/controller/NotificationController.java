package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationEmitterRepository; // 🆕 Import kho chứa RAM
import com.homie.finance.repository.NotificationRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "8. Notifications", description = "Quản lý thông báo người dùng")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationEmitterRepository emitterRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Đăng ký nhận thông báo Realtime (Dành cho FE EventSource)")
    public SseEmitter subscribe() {
        User currentUser = getCurrentUser();

        // Tạo emitter với timeout 1 tiếng (3600000ms)
        SseEmitter emitter = new SseEmitter(3600000L);

        // Lưu vào kho để Service có thể tìm thấy và bắn tin
        emitterRepository.add(currentUser.getId(), emitter);

        // Các callback để dọn dẹp RAM khi kết nối bị ngắt
        emitter.onCompletion(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onTimeout(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onError((e) -> emitterRepository.remove(currentUser.getId()));

        // Bắn một tin nhắn chào mừng để giữ kết nối không bị timeout ngay lập tức
        try {
            emitter.send(SseEmitter.event().name("init").data("Homie Realtime Connected!"));
        } catch (IOException e) {
            emitterRepository.remove(currentUser.getId());
        }

        return emitter;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách thông báo của tôi")
    public ApiResponse<List<Notification>> getAll() {
        return new ApiResponse<>(200, "Thành công", notificationService.getMyNotifications(getCurrentUser()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm số thông báo chưa đọc")
    public ApiResponse<Long> getUnreadCount() {
        long count = notificationRepository.countByUserAndIsReadFalse(getCurrentUser());
        return new ApiResponse<>(200, "Thành công", count);
    }

    @PutMapping("/read")
    @Operation(summary = "Đánh dấu đã đọc (Truyền list mảng ID ['id1', 'id2'])")
    public ApiResponse<String> markRead(@RequestBody List<String> ids) {
        notificationService.markAsRead(ids, getCurrentUser());
        return new ApiResponse<>(200, "Đã cập nhật trạng thái đã đọc", null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    public ApiResponse<String> markReadAll() {
        notificationService.markAllAsRead(getCurrentUser());
        return new ApiResponse<>(200, "Đã đọc tất cả thông báo", null);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Xóa thông báo (Truyền list mảng ID ['id1', 'id2'])")
    public ApiResponse<String> delete(@RequestBody List<String> ids) {
        notificationService.deleteNotifications(ids, getCurrentUser());
        return new ApiResponse<>(200, "Đã xóa thông báo thành công", null);
    }
}