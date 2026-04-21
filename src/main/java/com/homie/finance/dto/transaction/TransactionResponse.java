package com.homie.finance.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết giao dịch trả về")
public class TransactionResponse {

    @Schema(description = "Mã định danh bảo mật UUID của giao dịch", example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;

    @Schema(description = "Số tiền thu hoặc chi", example = "55000")
    private Double amount;

    @Schema(description = "Ghi chú chi tiết", example = "Mua ly trà sữa trân châu full topping")
    private String note;

    @Schema(description = "Ngày thực hiện giao dịch", example = "2026-03-18")
    private LocalDate date;

    @Schema(description = "Loại giao dịch: INCOME hoặc EXPENSE", example = "EXPENSE")
    private String type;

    @Schema(description = "Đường dẫn ảnh hóa đơn đính kèm")
    private String receiptUrl;

    @Schema(description = "Tên danh mục", example = "Ăn uống")
    private String categoryName;

    @Schema(description = "Tên ví nguồn", example = "Tiền mặt")
    private String walletName;

    // 🔥 FIX LỖI: Thêm các trường hiển thị Không gian nhóm
    @Schema(description = "ID của không gian nhóm (nếu có)")
    private String groupId;

    @Schema(description = "Tên không gian nhóm (nếu có)", example = "Quỹ phòng trọ")
    private String groupName;

    // 🔥 FIX LỖI: Thêm các trường hiển thị Người tạo (Rất cần cho Giao dịch nhóm)
    @Schema(description = "ID người tạo giao dịch")
    private String userId;

    @Schema(description = "Tên người tạo giao dịch", example = "nguyenvana")
    private String userName;
}