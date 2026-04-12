package com.homie.finance.repository;

import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Lấy thông tin User để Login hoặc Quên mật khẩu
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    // Dùng cái này lúc Đăng ký để check trùng lặp (Nhanh hơn findBy... rất nhiều)
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}