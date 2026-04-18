package com.homie.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Debt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "creditor_id")
    private User creditor; // Người cho vay (người trả tiền)

    @ManyToOne
    @JoinColumn(name = "debtor_id")
    private User debtor; // Người nợ

    private Double amount;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private GroupSpace group;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    private boolean isSettled = false; // Đã trả nợ chưa
}
