package com.homie.finance.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
@Data
public class TransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String transactionId;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;
}