package com.homie.finance.controller;

import com.homie.finance.dto.format.ApiResponse;
import com.homie.finance.entity.TransactionLog;
import com.homie.finance.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "10. Audit Logs", description = "Quản lý nhật ký hoạt động (Dành cho cá nhân và Trưởng nhóm)")
public class LogController {

    private final LogService logService;

    // --- LOG CỦA 1 GIAO DỊCH (Chuyển từ TransactionController qua) ---
    @GetMapping("/transaction/{id}")
    @Operation(summary = "Xem lịch sử của 1 giao dịch", description = "Xem chi tiết ai đã sửa số tiền, ghi chú của giao dịch này.")
    public ApiResponse<List<TransactionLog>> getTransactionLogs(@PathVariable String id) {
        List<TransactionLog> logs = logService.getLogsForTransaction(id);
        return new ApiResponse<>(200, "Lấy lịch sử giao dịch thành công!", logs);
    }

    // --- LOG CỦA CẢ NHÓM (Dành cho Trưởng nhóm) ---
    @GetMapping("/group/{groupId}")
    @Operation(summary = "Xem nhật ký hoạt động nhóm", description = "Chỉ Trưởng nhóm (Owner) mới xem được tất cả biến động trong nhóm.")
    public ApiResponse<List<TransactionLog>> getGroupLogs(@PathVariable String groupId) {
        List<TransactionLog> logs = logService.getGroupActivityLogs(groupId);
        return new ApiResponse<>(200, "Truy xuất nhật ký nhóm thành công!", logs);
    }
}