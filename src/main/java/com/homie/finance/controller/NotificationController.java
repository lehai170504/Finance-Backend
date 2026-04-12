package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.entity.Notification;
import com.homie.finance.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "8. Notifications", description = "Quản lý thông báo người dùng")
public class NotificationController {

    // CHỈ CẦN GỌI ĐÚNG 1 THẰNG SERVICE NÀY THÔI
    @Autowired
    private NotificationService notificationService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Đăng ký nhận thông báo Realtime (Dành cho FE EventSource)")
    public SseEmitter subscribe() {
        return notificationService.createSseConnection();
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách thông báo của tôi")
    public ApiResponse<List<Notification>> getAll() {
        return new ApiResponse<>(200, "Thành công", notificationService.getMyNotifications());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm số thông báo chưa đọc")
    public ApiResponse<Long> getUnreadCount() {
        return new ApiResponse<>(200, "Thành công", notificationService.getUnreadCount());
    }

    @PutMapping("/read")
    @Operation(summary = "Đánh dấu đã đọc (Truyền list mảng ID ['id1', 'id2'])")
    public ApiResponse<String> markRead(@RequestBody List<String> ids) {
        notificationService.markAsRead(ids);
        return new ApiResponse<>(200, "Đã cập nhật trạng thái đã đọc", null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    public ApiResponse<String> markReadAll() {
        notificationService.markAllAsRead();
        return new ApiResponse<>(200, "Đã đọc tất cả thông báo", null);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Xóa thông báo (Truyền list mảng ID ['id1', 'id2'])")
    public ApiResponse<String> delete(@RequestBody List<String> ids) {
        notificationService.deleteNotifications(ids);
        return new ApiResponse<>(200, "Đã xóa thông báo thành công", null);
    }
}