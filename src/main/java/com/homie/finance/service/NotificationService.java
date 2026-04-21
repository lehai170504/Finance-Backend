package com.homie.finance.service;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationEmitterRepository;
import com.homie.finance.repository.NotificationRepository;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEmitterRepository emitterRepository;
    private final SecurityUtils securityUtils;

    // ==============================================================
    // 1. SETUP SSE REALTIME
    // ==============================================================
    public SseEmitter createSseConnection() {
        User currentUser = securityUtils.getCurrentUser();
        SseEmitter emitter = new SseEmitter(3600000L); // Timeout 1 tiếng

        emitterRepository.add(currentUser.getId(), emitter);

        emitter.onCompletion(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onTimeout(() -> emitterRepository.remove(currentUser.getId()));
        emitter.onError((e) -> emitterRepository.remove(currentUser.getId()));

        try {
            emitter.send(SseEmitter.event().name("init").data("Homie Realtime Connected!"));
        } catch (IOException e) {
            log.error("Lỗi kết nối SSE cho User: {}", currentUser.getUsername());
            emitterRepository.remove(currentUser.getId());
        }

        return emitter;
    }

    // ==============================================================
    // 2. LẤY DANH SÁCH & ĐẾM SỐ LƯỢNG
    // ==============================================================
    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications() {
        return notificationRepository.findByUserOrderByCreatedAtDesc(securityUtils.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByUserAndIsReadFalse(securityUtils.getCurrentUser());
    }

    // ==============================================================
    // 3. TẠO THÔNG BÁO MỚI
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
                // Bắn data real-time qua cho FE
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(savedNote));
            } catch (IOException e) {
                log.warn("Emitter hỏng cho User: {}, tiến hành dọn dẹp.", targetUser.getUsername());
                emitterRepository.remove(targetUser.getId());
            }
        }
    }

    // ==============================================================
    // 4. CÁC HÀM CẬP NHẬT TRẠNG THÁI
    // ==============================================================
    @Transactional
    public void markAsRead(List<String> ids) {
        User user = securityUtils.getCurrentUser();
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);

        if (!notifications.isEmpty()) {
            notifications.forEach(n -> n.setRead(true));
            notificationRepository.saveAll(notifications);
            sendUpdateSignal(user.getId(), "UPDATE_READ_STATUS");
        }
    }

    @Transactional
    public void markAllAsRead() {
        User user = securityUtils.getCurrentUser();
        notificationRepository.markAllAsReadByUser(user);

        sendUpdateSignal(user.getId(), "UPDATE_READ_ALL");
        log.info("User {} đã đánh dấu đọc tất cả thông báo.", user.getUsername());
    }

    @Transactional
    public void deleteNotifications(List<String> ids) {
        User user = securityUtils.getCurrentUser();
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);

        if (!notifications.isEmpty()) {
            notificationRepository.deleteAll(notifications);
            sendUpdateSignal(user.getId(), "UPDATE_DELETE");
        }
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