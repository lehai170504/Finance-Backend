package com.homie.finance.repository;

import com.homie.finance.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

    // Tìm danh mục theo tên (Ví dụ: "Ăn uống", "Di chuyển")
    Optional<Category> findByNameIgnoreCase(String name);
}