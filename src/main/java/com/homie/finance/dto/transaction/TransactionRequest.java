package com.homie.finance.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Yêu cầu thêm mới/cập nhật giao dịch")
public class TransactionRequest {

    @NotNull(message = "Số tiền không được bỏ trống!")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0 chứ!")
    @Schema(description = "Số tiền thu hoặc chi (phải > 0)", example = "55000")
    private Double amount;

    @Schema(description = "Ghi chú chi tiết", example = "Mua ly trà sữa trân châu full topping")
    private String note;

    @NotNull(message = "Ngày tháng không được bỏ trống!")
    @Schema(description = "Ngày thực hiện giao dịch", example = "2026-03-18")
    private LocalDate date;

    @NotNull(message = "Phải chọn danh mục!")
    @Schema(description = "ID của danh mục", example = "cat-123")
    private String categoryId;

    @Schema(description = "ID của ví (Nếu là giao dịch cá nhân)", example = "wallet-456")
    private String walletId;

    @Schema(description = "ID của nhóm (Nếu là giao dịch nhóm)", example = "group-789")
    private String groupSpaceId;

    @Schema(description = "Đường dẫn ảnh hóa đơn đính kèm (nếu có)")
    private String receiptUrl;
}