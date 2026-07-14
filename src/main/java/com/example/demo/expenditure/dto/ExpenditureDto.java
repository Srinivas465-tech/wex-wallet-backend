package com.example.demo.expenditure.dto;

import com.example.demo.expenditure.model.Expenditure;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenditureDto() {

    public record ExpenditureRequest(
            Expenditure.Category category,
            @NotNull BigDecimal amount
    ){}

    @Builder
    public record ExpenditureResponse(
            Long id,
            Expenditure.Category category,
            BigDecimal amount,
            BigDecimal remainingAmount,
            LocalDateTime dateTime
    ){}

}
