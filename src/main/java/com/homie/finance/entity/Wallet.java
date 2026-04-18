package com.homie.finance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "wallets")
@Data
@org.hibernate.annotations.SQLRestriction("is_deleted = false")
public class Wallet implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name; // Ví dụ: Tiền mặt, Techcombank, MoMo

    private Double balance = 0.0; // Số dư hiện tại trong ví

    private String color; // Mã màu để hiển thị trên App Android cho đẹp (VD: #FF0000)

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // Ví này thuộc về ai
    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;
}