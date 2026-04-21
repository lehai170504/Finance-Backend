package com.homie.finance.service;

import com.homie.finance.dto.transaction.SetBudgetRequest;
import com.homie.finance.entity.Budget;
import com.homie.finance.entity.Category;
import com.homie.finance.entity.User;
import com.homie.finance.repository.BudgetRepository;
import com.homie.finance.repository.CategoryRepository;
import com.homie.finance.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public String setBudget(SetBudgetRequest request) {
        User currentUser = securityUtils.getCurrentUser();

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục!"));

        if (!"EXPENSE".equalsIgnoreCase(category.getType())) {
            throw new IllegalArgumentException("Chỉ được cài đặt hạn mức cho danh mục Chi Tiêu (EXPENSE)!");
        }

        Budget budget = budgetRepository.findByUserAndCategoryAndMonthAndYear(
                currentUser, category, request.getMonth(), request.getYear()
        ).orElse(new Budget());

        budget.setUser(currentUser);
        budget.setCategory(category);
        budget.setMonth(request.getMonth());
        budget.setYear(request.getYear());
        budget.setLimitAmount(request.getLimitAmount());

        budgetRepository.save(budget);

        return category.getName();
    }
}