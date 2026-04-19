package com.homie.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class TransactionRequest {

    @Min(value = 1, message = "Số tiền phải lớn hơn 0 chứ!")
    @Schema(description = "Số tiền thu hoặc chi", example = "55000")
    private Double amount;

    @Schema(description = "Ghi chú chi tiết", example = "Mua ly trà sữa trân châu")
    private String note;

    @NotNull(message = "Ngày tháng không được bỏ trống!")
    @Schema(description = "Ngày thực hiện giao dịch", example = "2026-03-18")
    private LocalDate date;

    @Schema(description = "Link ảnh hóa đơn (Nếu có)", example = "http://res.cloudinary.com/...")
    private String receiptUrl;
}