package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationRepository;
import com.homie.finance.repository.UserRepository;
import com.homie.finance.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "8. Notifications", description = "Quản lý thông báo người dùng")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
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