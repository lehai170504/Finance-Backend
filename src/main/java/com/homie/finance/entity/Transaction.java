package com.homie.finance.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Đổi sang sinh UUID tự động
    @Schema(description = "Mã định danh bảo mật UUID của giao dịch", example = "123e4567-e89b-12d3-a456-426614174000", accessMode = Schema.AccessMode.READ_ONLY)
    private String id;

    @Min(value = 1, message = "Số tiền phải lớn hơn 0 chứ!")
    @Schema(description = "Số tiền thu hoặc chi (phải > 0)", example = "55000")
    private Double amount;

    @Schema(description = "Ghi chú chi tiết", example = "Mua ly trà sữa trân châu full topping")
    private String note;

    @NotNull(message = "Ngày tháng không được bỏ trống!")
    @Schema(description = "Ngày thực hiện giao dịch", example = "2026-03-18")
    private LocalDate date;

    // Mối quan hệ: Nhiều Giao dịch (Many) thuộc về 1 Danh mục (One)
    @ManyToOne
    @JoinColumn(name = "category_id")
    @Schema(description = "Thông tin danh mục chứa giao dịch này")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Schema(hidden = true)
    private User user;

    @Schema(description = "Đường dẫn ảnh hóa đơn đính kèm")
    private String receiptUrl;

    @ManyToOne
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @ManyToOne
    @JoinColumn(name = "group_space_id")
    private GroupSpace groupSpace;


    @Schema(description = "Loại giao dịch: INCOME hoặc EXPENSE", example = "EXPENSE")
    private String type;

    @Column(name = "is_deleted")
    @Schema(hidden = true)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Schema(hidden = true)
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.type == null) {
            if (this.category != null) {
                this.type = this.category.getType();
            } else {
                this.type = "EXPENSE";
            }
        }
    }

    @PostLoad
    public void postLoad() {
        if (this.type == null && this.category != null) {
            this.type = this.category.getType();
        }
    }

}