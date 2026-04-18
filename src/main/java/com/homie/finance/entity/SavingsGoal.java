package com.homie.finance.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity
@Table(name = "savings_goals")
@Data
public class SavingsGoal implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name; // Ví dụ: "Mua xe máy", "Du lịch Đà Lạt"

    @Column(nullable = false)
    private Double targetAmount; // Số tiền cần đạt (VD: 50_000_000)

    @Column(nullable = false)
    private Double savedAmount = 0.0; // Số tiền đã bỏ heo vào

    private String icon; // Ví dụ: "directions_car", "flight"

    private String color; // Mã màu hiển thị trên App

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_completed", nullable = false, columnDefinition = "boolean default false")
    private boolean completed = false;
}
