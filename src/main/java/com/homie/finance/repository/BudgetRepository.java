package com.homie.finance.repository;

import com.homie.finance.entity.Budget;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, String> {
    // Tìm ngân sách của 1 người, 1 danh mục, trong 1 tháng/năm cụ thể
    Optional<Budget> findByUserAndCategoryAndMonthAndYear(User user, Category category, int month, int year);

    boolean existsByCategoryId(String categoryId);
}