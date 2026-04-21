package com.homie.finance.controller;

import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.dto.transaction.SetBudgetRequest;
import com.homie.finance.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/budgets")
@Tag(name = "4. Budget", description = "Cài đặt hạn mức chi tiêu (Cảnh báo vượt ngân sách)")
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping("/set")
    @Operation(summary = "Đặt Hạn mức (Ngân sách)", description = "Khóa van chi tiêu cho một danh mục cụ thể trong tháng. Nếu tiêu lố số tiền này, hệ thống sẽ báo lỗi.")
    public ApiResponse<Object> setBudget(@Valid @RequestBody SetBudgetRequest request) {

        String categoryName = budgetService.setBudget(request);

        return new ApiResponse<>(200,
                String.format("Đã set ngân sách thành công mức %,.0fđ cho danh mục %s!", request.getLimitAmount(), categoryName),
                null);
    }
}