package com.homie.finance.controller;

import com.homie.finance.dto.ApiResponse;
import com.homie.finance.dto.StatisticResponse;
import com.homie.finance.dto.TransactionResponse;
import com.homie.finance.service.ExcelService;
import com.homie.finance.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "5. Report & Statistics", description = "Trích xuất số liệu vẽ biểu đồ và Tải báo cáo")
public class ReportController {

        @Autowired
        private TransactionService transactionService;
        @Autowired
        private ExcelService excelService;

        @GetMapping("/categories")
        @Operation(summary = "Thống kê cho Biểu đồ Tròn (Pie Chart)", description = "Gom nhóm và tính tổng tiền theo từng Danh mục (VD: Ăn uống: 5tr, Xăng: 1tr). Nếu không nhập ngày, mặc định lấy tháng hiện tại.")
        public ApiResponse<List<StatisticResponse>> getCategoryStats(
                        @Parameter(description = "Ngày bắt đầu (Định dạng: yyyy-MM-dd)", example = "2026-03-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @Parameter(description = "Ngày kết thúc (Định dạng: yyyy-MM-dd)", example = "2026-03-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

                List<StatisticResponse> data = transactionService.getCategoryStatistics(startDate, endDate);
                String message = (startDate == null) ? "Báo cáo tháng hiện tại"
                                : "Báo cáo từ " + startDate + " đến " + endDate;
                return new ApiResponse<>(200, message, data);
        }

        @GetMapping("/cash-flow")
        @Operation(summary = "Phân tích dòng tiền (Cash Flow Line Chart)", description = "Lấy dữ liệu Thu/Chi theo từng ngày để vẽ biểu đồ đường (Line Chart) hoặc cột (Bar Chart).")
        public ApiResponse<List<com.homie.finance.dto.CashFlowResponse>> getCashFlowStats(
                        @Parameter(description = "Ngày bắt đầu", example = "2026-03-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @Parameter(description = "Ngày kết thúc", example = "2026-03-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

                List<com.homie.finance.dto.CashFlowResponse> data = transactionService.getCashFlowStatistics(startDate,
                                endDate);
                String message = (startDate == null) ? "Dòng tiền tháng hiện tại"
                                : "Dòng tiền từ " + startDate + " đến " + endDate;
                return new ApiResponse<>(200, message, data);
        }

        @GetMapping("/download-excel")
        @Operation(summary = "Tải File Excel (.xlsx)", description = "Đóng gói toàn bộ lịch sử chi tiêu của người dùng thành file Excel tiêu chuẩn để lưu trữ hoặc xem offline.")
        public ResponseEntity<InputStreamResource> downloadExcel() {
                List<TransactionResponse> data = transactionService.getAllTransactionsForExport();
                ByteArrayInputStream in = excelService.exportToExcel(data);

                HttpHeaders headers = new HttpHeaders();
                headers.add("Content-Disposition", "attachment; filename=Homie_Finance_Report.xlsx");

                return ResponseEntity.ok()
                                .headers(headers)
                                .contentType(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(new InputStreamResource(in));
        }
}