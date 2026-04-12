package com.homie.finance.service;

import com.homie.finance.dto.SetBudgetRequest;
import com.homie.finance.entity.Budget;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.User;
import com.homie.finance.repository.BudgetRepository;
import com.homie.finance.repository.CategoryRepository;
import com.homie.finance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    @Autowired private BudgetRepository budgetRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    public String setBudget(SetBudgetRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        if (!"EXPENSE".equalsIgnoreCase(category.getType())) {
            throw new IllegalArgumentException("Chỉ được cài đặt hạn mức cho danh mục Chi Tiêu (EXPENSE)!");
        }

        Budget budget = budgetRepository.findByUserAndCategoryAndMonthAndYear(
                user, category, request.getMonth(), request.getYear()
        ).orElse(new Budget());

        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());
        budget.setLimitAmount(request.getLimitAmount());

        budgetRepository.save(budget);

        return category.getName();
    }
}