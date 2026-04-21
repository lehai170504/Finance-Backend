package com.homie.finance.repository;

import com.homie.finance.entity.Notification;
import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    // 1. Lấy thông báo của user, cái nào mới nhất hiện lên đầu
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    // 2. Lấy danh sách thông báo CHƯA ĐỌC (Dùng cho hàm Service mới)
    List<Notification> findByUserAndIsReadFalse(User user);

    // 3. Đếm nhanh số thông báo chưa đọc để hiện Badge đỏ ở FE
    long countByUserAndIsReadFalse(User user);

    // 4. Tìm danh sách thông báo theo list ID và phải thuộc về User đó (Bảo mật)
    List<Notification> findByIdInAndUser(List<String> ids, User user);

    // 5. Đánh dấu tất cả đã đọc chỉ với 1 câu Query
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    void markAllAsReadByUser(@Param("user") User user);
}