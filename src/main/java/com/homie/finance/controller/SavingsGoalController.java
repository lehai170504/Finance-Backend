package com.homie.finance.controller;

import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.dto.goal.SavingsGoalRequest;
import com.homie.finance.dto.goal.SavingsGoalResponse;
import com.homie.finance.service.SavingsGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/savings-goals")
@RequiredArgsConstructor
@Tag(name = "8. Savings Goals", description = "Quản lý Lợn Đất - Đặt mục tiêu và theo dõi tiến độ tiết kiệm")
public class SavingsGoalController {

        private final SavingsGoalService savingsGoalService;

        @GetMapping
        @Operation(summary = "Lấy danh sách mục tiêu tiết kiệm", description = "Trả về tất cả các lợn đất của bạn, sắp xếp: chưa đạt lên trên, đã hoàn thành xuống dưới.")
        public ApiResponse<List<SavingsGoalResponse>> getMyGoals() {
                return new ApiResponse<>(200, "Danh sách mục tiêu tiết kiệm", savingsGoalService.getMyGoals());
        }

        @PostMapping
        @Operation(summary = "Tạo mục tiêu tiết kiệm mới", description = "Tạo một 'Lợn Đất' mới. Ví dụ: Mua xe 50 triệu, Du lịch Nhật 30 triệu.")
        public ApiResponse<SavingsGoalResponse> createGoal(@RequestBody SavingsGoalRequest request) {
                return new ApiResponse<>(201, "Đã tạo mục tiêu tiết kiệm!", savingsGoalService.createGoal(request));
        }

        @PostMapping("/{goalId}/deposit")
        @Operation(summary = "Bỏ tiền vào Lợn Đất", description = "Trích một khoản tiền từ Ví sang Lợn Đất. Hệ thống tự động kiểm tra số dư ví và thông báo khi đạt mục tiêu.")
        public ApiResponse<SavingsGoalResponse> deposit(
                        @Parameter(description = "ID của mục tiêu tiết kiệm") @PathVariable String goalId,
                        @Parameter(description = "ID của ví muốn trích tiền từ đó") @RequestParam String walletId,
                        @Parameter(description = "Số tiền muốn bỏ vào lợn (VD: 500000)") @RequestParam Double amount) {
                return new ApiResponse<>(200, "Đã nạp tiền vào lợn đất thành công!",
                                savingsGoalService.depositToGoal(goalId, walletId, amount));
        }

        @PostMapping("/{goalId}/withdraw")
        @Operation(summary = "Rút tiền ra khỏi Lợn Đất", description = "Lấy tiền từ Lợn Đất trả về Ví. Cẩn thận - hành động này sẽ làm giảm % tiến độ mục tiêu!")
        public ApiResponse<SavingsGoalResponse> withdraw(
                        @Parameter(description = "ID của mục tiêu tiết kiệm") @PathVariable String goalId,
                        @Parameter(description = "ID của ví muốn hoàn tiền về") @RequestParam String walletId,
                        @Parameter(description = "Số tiền muốn rút ra") @RequestParam Double amount) {
                return new ApiResponse<>(200, "Đã rút tiền ra khỏi lợn đất!",
                                savingsGoalService.withdrawFromGoal(goalId, walletId, amount));
        }

        @DeleteMapping("/{goalId}")
        @Operation(summary = "Xóa mục tiêu tiết kiệm", description = "Xóa lợn đất. Nếu còn tiền bên trong, cần cung cấp walletId để hệ thống tự động hoàn tiền về ví đó. Nếu không có walletId, tiền sẽ bị mất!")
        public ApiResponse<String> deleteGoal(
                        @Parameter(description = "ID của mục tiêu cần xóa") @PathVariable String goalId,
                        @Parameter(description = "ID ví để nhận lại tiền (nếu còn trong lợn)") @RequestParam(required = false) String walletId) {
                savingsGoalService.deleteGoal(goalId, walletId);
                return new ApiResponse<>(200, "Đã xóa mục tiêu tiết kiệm!", "ID: " + goalId);
        }
}
