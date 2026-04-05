package com.homie.finance.service;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationEmitterRepository; // 🆕 Import kho chứa emitter
import com.homie.finance.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; // 🆕 Import SseEmitter

import java.io.IOException;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEmitterRepository emitterRepository;

    // 1. Lấy danh sách thông báo (Giữ nguyên)
    public List<Notification> getMyNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    // 2. Tạo thông báo mới (NÂNG CẤP REALTIME 🚀)
    @Transactional
    public void createNotification(User user, String message) {
        // --- BƯỚC 1: LƯU VÀO DB (Để sau này F5 vẫn còn) ---
        Notification notification = new Notification(user, message);
        Notification savedNote = notificationRepository.save(notification);

        // --- BƯỚC 2: BẮN REALTIME (Dành cho những ông đang Online) ---
        SseEmitter emitter = emitterRepository.get(user.getId());
        if (emitter != null) {
            try {
                // Gửi event tên là "notification" kèm dữ liệu là object notification vừa tạo
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(savedNote));
            } catch (IOException e) {
                // Nếu bắn tin lỗi (do user tắt tab, rớt mạng...), dọn dẹp emitter ngay
                emitterRepository.remove(user.getId());
            }
        }
    }

    // 3. Đánh dấu đã đọc (Nâng cấp thêm: Bắn tin để FE tự cập nhật số lượng badge)
    @Transactional
    public void markAsRead(List<String> ids, User user) {
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);

        // 💡 Mẹo: Có thể bắn 1 signal qua SSE ở đây để FE tự biết mà giảm số "Unread count"
        sendUpdateSignal(user.getId(), "UPDATE_READ_STATUS");
    }

    // 4. Đánh dấu TẤT CẢ đã đọc
    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);

        sendUpdateSignal(user.getId(), "UPDATE_READ_ALL");
    }

    // 5. Xóa thông báo
    @Transactional
    public void deleteNotifications(List<String> ids, User user) {
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);
        notificationRepository.deleteAll(notifications);

        sendUpdateSignal(user.getId(), "UPDATE_DELETE");
    }

    // Hàm phụ để gửi tín hiệu cập nhật trạng thái (không kèm data nặng)
    private void sendUpdateSignal(String userId, String action) {
        SseEmitter emitter = emitterRepository.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("update_signal").data(action));
            } catch (IOException e) {
                emitterRepository.remove(userId);
            }
        }
    }
}