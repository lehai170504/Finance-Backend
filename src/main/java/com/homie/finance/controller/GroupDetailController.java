package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.dto.DebtResponse;
import com.homie.finance.dto.GroupStatsResponse;
import com.homie.finance.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/details")
@Tag(name = "7. Group Space Details", description = "Thống kê chi tiết, quản lý nợ nần và chia tiền trong Nhóm")
public class GroupDetailController {

    @Autowired private TransactionService transactionService;

    @GetMapping("/{groupId}/stats")
    @Operation(
            summary = "Thống kê chi tiêu nhóm theo Tháng",
            description = "Tính tổng chi tiêu, gom nhóm theo Danh mục và thống kê số tiền mỗi thành viên đã chi trong tháng/năm chỉ định để vẽ biểu đồ nhóm."
    )
    public ApiResponse<GroupStatsResponse> getStats(
            @Parameter(description = "ID dạng UUID của Nhóm (GroupSpace)", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String groupId,

            @Parameter(description = "Tháng cần thống kê (1-12)", example = "3")
            @RequestParam int month,

            @Parameter(description = "Năm cần thống kê (yyyy)", example = "2026")
            @RequestParam int year) {

        GroupStatsResponse data = transactionService.getGroupStats(groupId, month, year);
        return new ApiResponse<>(200, "Lấy thống kê nhóm tháng " + month + "/" + year + " thành công", data);
    }

    @GetMapping("/{groupId}/debts")
    @Operation(
            summary = "Lấy danh sách Nợ nần chưa thanh toán",
            description = "Liệt kê tất cả các khoản nợ phát sinh từ việc chia hóa đơn trong nhóm (Ai nợ ai, bao nhiêu tiền) chưa được xác nhận thanh toán."
    )
    public ApiResponse<List<DebtResponse>> getDebts(
            @Parameter(description = "ID dạng UUID của Nhóm cần xem nợ nần", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String groupId) {

        // Gọi qua Service thay vì Repository
        List<DebtResponse> debts = transactionService.getGroupDebts(groupId);
        return new ApiResponse<>(200, "Danh sách nợ nần hiện tại của nhóm", debts);
    }

    @PostMapping("/debts/{debtId}/settle")
    @Operation(
            summary = "Xác nhận trả nợ (Settle Up)",
            description = "Chủ nợ xác nhận đã nhận được tiền thực tế từ con nợ. Khoản nợ sẽ được đánh dấu là đã thanh toán."
    )
    public ApiResponse<?> settleDebt(
            @Parameter(description = "ID của khoản nợ", example = "uuid-debt-123")
            @PathVariable String debtId) {

        transactionService.settleDebt(debtId);
        return new ApiResponse<>(200, "Tuyệt vời! Đã xác nhận xóa nợ thành công.", null);
    }
}