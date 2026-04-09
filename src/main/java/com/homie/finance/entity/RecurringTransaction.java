package com.homie.finance.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_transactions")
@Data
public class RecurringTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @ManyToOne @JoinColumn(name = "category_id")
    private Category category;

    private Double amount;
    private String note;

    // Tần suất: "MONTHLY" (Hàng tháng) hoặc "WEEKLY" (Hàng tuần)
    private String frequency;

    // Ngày tiếp theo sẽ bị trừ tiền
    private LocalDate nextExecutionDate;

    private boolean isActive = true; // Bật/Tắt lặp lại
}