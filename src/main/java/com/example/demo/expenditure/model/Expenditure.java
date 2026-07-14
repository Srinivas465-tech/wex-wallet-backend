package com.example.demo.expenditure.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Table
@Entity(name = "expenditures")
public class Expenditure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Enumerated(EnumType.STRING)
    public Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "remaining_balance")
    private BigDecimal remainingBalance;

    public enum Category {
        FOOD,
        GROCERIES,
        TRANSPORT,
        UTILITIES,
        RENT,
        ENTERTAINMENT,
        HEALTHCARE,
        SHOPPING,
        EDUCATION,
        TRAVEL,
        INSURANCE,
        PERSONAL_CARE,
        SAVINGS,
        OTHER
    }

}
