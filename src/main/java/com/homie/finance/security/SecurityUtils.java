package com.homie.finance.security;

import com.homie.finance.entity.Transaction;
import com.homie.finance.entity.User;
import com.homie.finance.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private final UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Tách hàm lấy user hiện tại ra dùng cho tất cả các Service khác
    public User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Homie ơi, phiên đăng nhập đã hết hạn!"));
    }

    // Hàm helper kiểm tra quyền sở hữu chung cho hệ thống
    public void validateTransactionOwner(Transaction transaction, User currentUser) {
        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Hành động bị chặn: Bạn không sở hữu giao dịch này!");
        }
    }
}