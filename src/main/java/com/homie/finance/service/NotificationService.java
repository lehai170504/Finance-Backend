package com.homie.finance.service;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationEmitterRepository;
import com.homie.finance.repository.NotificationRepository;
import com.homie.finance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Service
public class NotificationService {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationEmitterRepository emitterRepository;
    @Autowired private UserRepository userRepository;

    // ==============================================================
    // Tự động lấy User đang đăng nhập từ SecurityContext
    // ==============================================================
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    // ==============================================================
    // 1. SETUP SSE REALTIME (Controller sẽ gọi hàm này)
    // ==============================================================
    public SseEmitter createSseConnection() {
        User currentUser = getCurrentUser();
        SseEmitter emitter = new SseEmitter(3600000L); // Timeout 1 tiếng

        emitterRepository.add(currentUser.getId(), emitter);

        emitter.onCompletion(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onTimeout(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onError((e) -> emitterRepository.remove(currentUser.getId()));

        try {
            emitter.send(SseEmitter.event().name("init").data("Homie Realtime Connected!"));
        } catch (IOException e) {
            emitterRepository.remove(currentUser.getId());
        }

        return emitter;
    }

    // ==============================================================
    // 2. LẤY DANH SÁCH & ĐẾM SỐ LƯỢNG
    // ==============================================================
    public List<Notification> getMyNotifications() {
        return notificationRepository.findByUserOrderByCreatedAtDesc(getCurrentUser());
    }

    public long getUnreadCount() {
        return notificationRepository.countByUserAndIsReadFalse(getCurrentUser());
    }

    // ==============================================================
    // 3. TẠO THÔNG BÁO MỚI (Hàm này nhận User từ các Service khác truyền vào)
    // Ví dụ: BudgetService thấy vượt hạn mức -> Gọi hàm này
    // ==============================================================
    @Transactional
    public void createNotification(User targetUser, String message) {
        // --- LƯU VÀO DB ---
        Notification notification = new Notification(targetUser, message);
        Notification savedNote = notificationRepository.save(notification);

        // --- BẮN REALTIME SSE ---
        SseEmitter emitter = emitterRepository.get(targetUser.getId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(savedNote));
            } catch (IOException e) {
                emitterRepository.remove(targetUser.getId());
            }
        }
    }

    // ==============================================================
    // 4. CÁC HÀM CẬP NHẬT TRẠNG THÁI
    // ==============================================================
    @Transactional
    public void markAsRead(List<String> ids) {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);

        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);

        sendUpdateSignal(user.getId(), "UPDATE_READ_STATUS");
    }

    @Transactional
    public void markAllAsRead() {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);

        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);

        sendUpdateSignal(user.getId(), "UPDATE_READ_ALL");
    }

    @Transactional
    public void deleteNotifications(List<String> ids) {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);

        notificationRepository.deleteAll(notifications);

        sendUpdateSignal(user.getId(), "UPDATE_DELETE");
    }

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