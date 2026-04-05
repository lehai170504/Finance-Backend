package com.homie.finance.service;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import com.homie.finance.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    // 1. Lấy danh sách thông báo
    public List<Notification> getMyNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    // 2. Tạo thông báo mới (Dùng Constructor của homie)
    @Transactional
    public void createNotification(User user, String message) {
        Notification notification = new Notification(user, message);
        notificationRepository.save(notification);
    }

    // 3. Đánh dấu đã đọc (1 hoặc nhiều)
    @Transactional
    public void markAsRead(List<String> ids, User user) {
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    // 4. Đánh dấu TẤT CẢ đã đọc
    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    // 5. Xóa thông báo (1 hoặc nhiều)
    @Transactional
    public void deleteNotifications(List<String> ids, User user) {
        List<Notification> notifications = notificationRepository.findByIdInAndUser(ids, user);
        notificationRepository.deleteAll(notifications);
    }
}