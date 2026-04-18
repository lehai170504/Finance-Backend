package com.homie.finance.controller;

import com.homie.finance.dto.*;

import com.homie.finance.service.OcrService;
import com.homie.finance.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "2. Transaction", description = "Quản lý luồng tiền ra vào (Thêm, Sửa, Xóa, Tìm kiếm, Thùng rác)")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private OcrService ocrService;

    // --- NHÓM 1: LẤY DANH SÁCH & TÌM KIẾM ---

    @GetMapping
    @Operation(summary = "Lấy danh sách giao dịch (Có phân trang)", description = "Hiển thị lịch sử chi tiêu, tự động sắp xếp mới nhất lên đầu.")
    public ApiResponse<PageResponse<TransactionResponse>> getAllTransactions(
            @Parameter(description = "Số trang (Bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số item mỗi trang") @RequestParam(defaultValue = "10") int size) {
        PageResponse<TransactionResponse> data = transactionService.getAllTransactions(page, size);
        return new ApiResponse<>(200, "Lấy danh sách trang " + page + " thành công!", data);
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm theo Ghi chú", description = "Tìm nhanh các khoản chi chứa từ khóa (VD: 'trà sữa'). Hỗ trợ phân trang.")
    public ApiResponse<PageResponse<TransactionResponse>> searchTransactions(
            @Parameter(description = "Từ khóa cần tìm") @RequestParam String keyword,
            @Parameter(description = "Số trang (Bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số item mỗi trang") @RequestParam(defaultValue = "10") int size) {
        PageResponse<TransactionResponse> data = transactionService.searchTransactions(keyword, page, size);
        return new ApiResponse<>(200, "Kết quả tìm kiếm cho: " + keyword, data);
    }

    @GetMapping("/filter")
    @Operation(summary = "Lọc theo Thu / Chi", description = "Chỉ xem tiền vào (INCOME) hoặc tiền ra (EXPENSE).")
    public ApiResponse<List<TransactionResponse>> filterTransactions(
            @Parameter(description = "Loại (INCOME hoặc EXPENSE)", example = "EXPENSE") @RequestParam String type) {
        List<TransactionResponse> data = transactionService.getTransactionsByType(type);
        return new ApiResponse<>(200, "Đã lọc danh sách " + type, data);
    }

    // --- NHÓM 2: TÍNH TỔNG ---

    @GetMapping("/total-income")
    @Operation(summary = "Xem Tổng Thu Nhập", description = "Cộng dồn tất cả các giao dịch mang nhãn INCOME.")
    public ApiResponse<Double> getTotalIncome() {
        return new ApiResponse<>(200, "Tổng thu nhập", transactionService.getTotalByType("INCOME"));
    }

    @GetMapping("/total-expense")
    @Operation(summary = "Xem Tổng Chi Tiêu", description = "Cộng dồn tất cả các giao dịch mang nhãn EXPENSE.")
    public ApiResponse<Double> getTotalExpense() {
        return new ApiResponse<>(200, "Tổng chi tiêu", transactionService.getTotalByType("EXPENSE"));
    }

    // --- NHÓM 3: THÊM / SỬA / XÓA ---

    @PostMapping("/create")
    @Operation(summary = "Thêm giao dịch mới", description = "Ghi chép một khoản thu/chi. Hệ thống tự cập nhật số dư Ví và gắn vào Nhóm (nếu có).")
    public ApiResponse<TransactionResponse> createTransaction(
            @Parameter(description = "ID của Ví (Wallet)") @RequestParam String walletId,

            @Parameter(description = "ID của Danh mục (Category)") @RequestParam String categoryId,

            @Parameter(description = "ID của Nhóm (Nếu có)") @RequestParam(required = false) String groupId,

            @Valid @RequestBody TransactionRequest request) {

        // Đổi Transaction thành TransactionResponse
        TransactionResponse data = transactionService.createTransaction(walletId, categoryId, groupId, request);
        return new ApiResponse<>(201, "Đã ghi chép giao dịch mới!", data);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa giao dịch", description = "Cập nhật lại thông tin giao dịch hoặc đổi sang ví/danh mục khác.")
    public ApiResponse<TransactionResponse> updateTransaction(
            @Parameter(description = "ID của giao dịch cần sửa") @PathVariable String id,
            @Parameter(description = "ID Ví (Wallet) mới") @RequestParam String newWalletId,
            @Parameter(description = "ID Danh mục (Category) mới") @RequestParam String categoryId,
            @Valid @RequestBody TransactionRequest request) {

        // Đổi Transaction thành TransactionResponse
        TransactionResponse data = transactionService.updateTransaction(id, newWalletId, categoryId, request);
        return new ApiResponse<>(200, "Cập nhật thành công!", data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa giao dịch (Vào thùng rác)", description = "Chuyển giao dịch vào thùng rác (Soft Delete) và TỰ ĐỘNG hoàn tiền lại cho Ví.")
    public ApiResponse<String> deleteTransaction(
            @Parameter(description = "ID của giao dịch") @PathVariable String id) {
        transactionService.deleteTransaction(id);
        return new ApiResponse<>(200, "Đã chuyển vào thùng rác và hoàn tiền về ví!", "ID: " + id);
    }

    // =========================================================
    // 🗑️ NHÓM 5: THÙNG RÁC (RECYCLE BIN)
    // =========================================================

    @GetMapping("/trash")
    @Operation(summary = "Xem thùng rác", description = "Lấy danh sách các giao dịch đã bị xóa (chưa xóa vĩnh viễn).")
    public ApiResponse<List<TransactionResponse>> getTrash() {
        return new ApiResponse<>(200, "Danh sách giao dịch trong thùng rác", transactionService.getTrash());
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "Khôi phục giao dịch", description = "Phục hồi giao dịch từ thùng rác và tính lại tiền vào ví.")
    public ApiResponse<TransactionResponse> restoreTransaction(
            @Parameter(description = "ID của giao dịch trong thùng rác") @PathVariable String id) {

        // Đổi Transaction thành TransactionResponse
        TransactionResponse data = transactionService.restoreTransaction(id);
        return new ApiResponse<>(200, "Đã khôi phục thành công!", data);
    }

    @DeleteMapping("/{id}/force")
    @Operation(summary = "Xóa vĩnh viễn", description = "Xóa hẳn giao dịch khỏi Database. Hành động này không thể hoàn tác!")
    public ApiResponse<String> forceDeleteTransaction(
            @Parameter(description = "ID của giao dịch") @PathVariable String id) {
        transactionService.forceDeleteTransaction(id);
        return new ApiResponse<>(200, "Đã dọn dẹp vĩnh viễn khỏi thùng rác!", "ID: " + id);
    }

    // --- NHÓM 4: MEDIA & NHÓM ---

    @PostMapping(value = "/{id}/upload-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Đính kèm ảnh hóa đơn", description = "Upload file ảnh (.jpg, .png) < 5MB lên Cloudinary và gắn link vào giao dịch này.")
    public ApiResponse<TransactionResponse> uploadReceipt(
            @Parameter(description = "ID của giao dịch") @PathVariable String id,
            @Parameter(description = "File ảnh hóa đơn") @RequestPart("file") MultipartFile file) {
        TransactionResponse updatedData = transactionService.uploadReceipt(id, file);
        return new ApiResponse<>(200, "Tải ảnh lên mây thành công!", updatedData);
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Lấy giao dịch của Nhóm", description = "Chỉ thành viên trong nhóm mới được phép xem.")
    public ApiResponse<PageResponse<TransactionResponse>> getGroupTransactions(
            @Parameter(description = "ID của nhóm") @PathVariable String groupId,
            @Parameter(description = "Số trang (Bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số item mỗi trang") @RequestParam(defaultValue = "10") int size) {

        PageResponse<TransactionResponse> data = transactionService.getGroupTransactions(groupId, page, size);
        return new ApiResponse<>(200, "Lịch sử chi tiêu của nhóm", data);
    }

    @GetMapping("/suggest-category")
    @Operation(summary = "Gợi ý danh mục thông minh", description = "Dựa vào ghi chú (note) để trả về ID danh mục phù hợp nhất.")
    public ApiResponse<String> suggestCategory(@RequestParam String note) {
        String suggestedId = transactionService.suggestCategoryId(note);
        return new ApiResponse<>(200, "Gợi ý danh mục", suggestedId);
    }

    @PostMapping(value = "/analyze-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Phân tích hóa đơn (OCR)", description = "Đọc ảnh hóa đơn và trả về số tiền, ghi chú để Frontend tự điền form.")
    public ApiResponse<OcrResponse> analyzeReceipt(
            @Parameter(description = "File ảnh hóa đơn") @RequestPart("file") MultipartFile file) {
        OcrResponse data = ocrService.analyzeReceipt(file);
        return new ApiResponse<>(200, "Phân tích hóa đơn thành công!", data);
    }

}