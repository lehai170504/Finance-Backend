package com.homie.finance.service;

import com.homie.finance.entity.Category;
import com.homie.finance.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private com.homie.finance.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.homie.finance.repository.BudgetRepository budgetRepository;

    // 1. Lấy tất cả danh mục
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    // 2. Tạo danh mục mới
    @Transactional
    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    // 3. Cập nhật danh mục
    @Transactional
    public Category updateCategory(String id, Category request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại!"));

        existing.setName(request.getName());
        existing.setType(request.getType());
        existing.setIcon(request.getIcon()); // 💡 Cập nhật cả icon mới

        return categoryRepository.save(existing);
    }

    // 4. Xóa danh mục
    @Transactional
    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy danh mục để xóa!");
        }

        if (transactionRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException("Danh mục này đã có giao dịch, không thể xóa!");
        }

        if (budgetRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException("Danh mục này đang được dùng để cài đặt hạn mức, không thể xóa!");
        }

        categoryRepository.deleteById(id);
    }
}