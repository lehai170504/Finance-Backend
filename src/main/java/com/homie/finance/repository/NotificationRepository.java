package com.homie.finance.repository;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    // Lấy thông báo của user, cái nào mới nhất hiện lên đầu
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    // Đếm nhanh số thông báo chưa đọc để hiện Badge đỏ ở FE
    long countByUserAndIsReadFalse(User user);

    // Tìm danh sách thông báo theo list ID và phải thuộc về User đó (để bảo mật)
    List<Notification> findByIdInAndUser(List<String> ids, User user);
}